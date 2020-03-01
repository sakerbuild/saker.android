package saker.android.impl.aidl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.android.api.aidl.AidlTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.aidl.AidlTaskFactory;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.utils.dependencies.RecursiveIgnoreCaseExtensionFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;

public class AidlWorkerTaskFactory implements TaskFactory<AidlTaskOutput>, Task<AidlTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<SakerPath> sourceDirectories;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public AidlWorkerTaskFactory() {
	}

	public AidlWorkerTaskFactory(NavigableSet<SakerPath> sourceDirectories) {
		this.sourceDirectories = sourceDirectories;
	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidBuildToolsSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not specified.");
		}
		remoteDispatchableEnvironmentSelector = SDKSupportUtils
				.getSDKBasedClusterExecutionEnvironmentSelector(sdkdescriptions.values());
	}

	@Override
	public Set<String> getCapabilities() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}
		return TaskFactory.super.getCapabilities();
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return remoteDispatchableEnvironmentSelector;
		}
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@Override
	public int getRequestedComputationTokenCount() {
		return 1;
	}

	@Override
	public AidlTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}
		AidlWorkerTaskIdentifier taskid = (AidlWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		taskcontext.setStandardOutDisplayIdentifier(AidlTaskFactory.TASK_NAME + ":" + compilationid);

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}

		SDKReference buildtoolssdk = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		SakerPath exepath = buildtoolssdk.getPath(AndroidBuildToolsSDKReference.PATH_AIDL_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aidl executable not found in SDK: " + buildtoolssdk);
		}

		SakerPath fwaidlpath = getFrameworkAidlPath(sdkrefs);

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AidlTaskFactory.TASK_NAME + "/" + compilationid));

		NavigableMap<SakerPath, InputFileInfo> relativeinputfiles = new TreeMap<>();
		for (SakerPath srcdir : sourceDirectories) {
			NavigableMap<SakerPath, SakerFile> inputfiles = taskcontext.getTaskUtilities()
					.collectFilesReportInputFileAndAdditionDependency(null,
							RecursiveIgnoreCaseExtensionFileCollectionStrategy.create(srcdir, ".aidl"));

			for (Entry<SakerPath, SakerFile> entry : inputfiles.entrySet()) {
				SakerPath dirrelative = srcdir.relativize(entry.getKey());
				InputFileInfo prev = relativeinputfiles.put(dirrelative,
						new InputFileInfo(entry.getKey(), dirrelative, entry.getValue()));
				if (prev != null) {
					throw new IllegalArgumentException(
							"Multiple input aidl files found with source directory relative path: " + dirrelative
									+ " as " + prev.absolutePath + " and " + entry.getKey());
				}
			}
		}

		SakerDirectory javaoutdir = outputdir.getDirectoryCreate("java");
		//TODO make incremental, don't clear everything
		javaoutdir.clear();

		SakerPath javasourcedirectory = javaoutdir.getSakerPath();

		Path javaoutdirlocalpath = taskcontext.mirror(javaoutdir);
		NavigableMap<SakerPath, ContentDescriptor> outputdependencies = new ConcurrentSkipListMap<>();

		ThreadUtils.runParallelItems(relativeinputfiles.values(), in -> {
			List<String> cmd = new ArrayList<>();
			cmd.add(exepath.toString());
			cmd.add(taskcontext.mirror(in.file).toString());
			if (fwaidlpath != null) {
				cmd.add("-p");
				cmd.add(fwaidlpath.toString());
			}
			cmd.add("-o");
			cmd.add(javaoutdirlocalpath.toString());

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			IOUtils.close(proc.getOutputStream());

			UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
			try {
				StreamUtils.copyStream(proc.getInputStream(), procout);
				int res = proc.waitFor();
				if (res != 0) {
					throw new IOException("aidl failed: " + res);
				}
			} finally {
				if (!procout.isEmpty()) {
					procout.writeTo(taskcontext.getStandardOut());
				}
			}

			SakerPath outputfilelocalpath = SakerPath.valueOf(javaoutdirlocalpath)
					.resolve(in.sourceDirectoryRelativePath)
					.resolveSibling(FileUtils.changeExtension(in.sourceDirectoryRelativePath.getFileName(), "java"));
			ProviderHolderPathKey outputfilepathkey = LocalFileProvider.getInstance().getPathKey(outputfilelocalpath);
			ContentDescriptor outputfilecd = taskcontext.invalidateGetContentDescriptor(outputfilepathkey);
			SakerFile outputfile = taskcontext.getTaskUtilities()
					.createProviderPathFile(outputfilelocalpath.getFileName(), outputfilepathkey);
			taskcontext.getTaskUtilities()
					.resolveDirectoryAtRelativePathCreate(javaoutdir, in.sourceDirectoryRelativePath.getParent())
					.add(outputfile);

			outputdependencies.put(outputfile.getSakerPath(), outputfilecd);
		});

		javaoutdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputdependencies);

		return new AidlTaskOutputImpl(javasourcedirectory);
	}

	private static SakerPath getFrameworkAidlPath(NavigableMap<String, SDKReference> sdkrefs) throws Exception {
		SDKReference platformssdk = sdkrefs.get(AndroidPlatformSDKReference.SDK_NAME);
		SakerPath fwaidlpath;
		if (platformssdk != null) {
			fwaidlpath = platformssdk.getPath(AndroidPlatformSDKReference.PATH_FRAMEWORK_AIDL);
		} else {
			fwaidlpath = null;
		}
		return fwaidlpath;
	}

	@Override
	public Task<? extends AidlTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, sourceDirectories);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);

	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sourceDirectories = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((sdkDescriptions == null) ? 0 : sdkDescriptions.hashCode());
		result = prime * result + ((sourceDirectories == null) ? 0 : sourceDirectories.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AidlWorkerTaskFactory other = (AidlWorkerTaskFactory) obj;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		if (sourceDirectories == null) {
			if (other.sourceDirectories != null)
				return false;
		} else if (!sourceDirectories.equals(other.sourceDirectories))
			return false;
		return true;
	}

	private static class InputFileInfo {
		protected SakerPath absolutePath;
		protected SakerPath sourceDirectoryRelativePath;
		protected SakerFile file;

		public InputFileInfo(SakerPath absolutePath, SakerPath sourceDirectoryRelativePath, SakerFile file) {
			this.absolutePath = absolutePath;
			this.sourceDirectoryRelativePath = sourceDirectoryRelativePath;
			this.file = file;
		}
	}

	private static final class AidlTaskOutputImpl implements AidlTaskOutput, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath javaSourceDirectory;

		/**
		 * For {@link Externalizable}.
		 */
		public AidlTaskOutputImpl() {
		}

		private AidlTaskOutputImpl(SakerPath javasourcedirectory) {
			this.javaSourceDirectory = javasourcedirectory;
		}

		@Override
		public SakerPath getJavaSourceDirectory() {
			return javaSourceDirectory;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(javaSourceDirectory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			javaSourceDirectory = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((javaSourceDirectory == null) ? 0 : javaSourceDirectory.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AidlTaskOutputImpl other = (AidlTaskOutputImpl) obj;
			if (javaSourceDirectory == null) {
				if (other.javaSourceDirectory != null)
					return false;
			} else if (!javaSourceDirectory.equals(other.javaSourceDirectory))
				return false;
			return true;
		}

	}
}
