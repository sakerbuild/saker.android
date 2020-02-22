package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.main.aapt2.AAPT2CompileTaskFactory;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.IndeterminateSDKDescription;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;

public class AAPT2CompileWorkerTaskFactory
		implements TaskFactory<AAPT2CompileTaskOutput>, Task<AAPT2CompileTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Deprecated
	private SakerPath resourceDirectory;
	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2CompileWorkerTaskFactory() {
	}

	@Deprecated
	public SakerPath getResourceDirectory() {
		return resourceDirectory;
	}

	@Deprecated
	public void setResourceDirectory(SakerPath resourceDirectory) {
		this.resourceDirectory = resourceDirectory;
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
	public Task<? extends AAPT2CompileTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2CompileTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		AAPT2CompileWorkerTaskIdentifier taskid = (AAPT2CompileWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		taskcontext.setStandardOutDisplayIdentifier(AAPT2CompileTaskFactory.TASK_NAME + ":" + compilationid);

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AAPT2CompileTaskFactory.TASK_NAME + "/" + compilationid));

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext.getExecutionContext().getEnvironment(),
					this.sdkDescriptions);
		}
		SDKReference buildtoolssdkref = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		if (buildtoolssdkref == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not found.");
		}
		SakerPath exepath = buildtoolssdkref.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdkref);
		}

		NavigableMap<SakerPath, SakerFile> resfiles = taskcontext.getTaskUtilities()
				.collectFilesReportInputFileAndAdditionDependency(null,
						new AndroidResourcesFileCollectionStrategy(resourceDirectory));

		System.out.println("AAPT2CompileWorkerTaskFactory.run() " + exepath);

		outputdir.clear();

		Map<String, Map<String, InputFileConfig>> resdirinputfiles = new TreeMap<>();

		Collection<InputFileConfig> inputs = new ArrayList<>();

		for (Entry<SakerPath, SakerFile> entry : resfiles.entrySet()) {
			SakerPath respath = entry.getKey();
			String parentfname = respath.getParent().getFileName();
			Map<String, InputFileConfig> filenameconfigs = resdirinputfiles.computeIfAbsent(parentfname,
					Functionals.treeMapComputer());

			InputFileConfig fileconfig = new InputFileConfig(respath, parentfname, entry.getValue());
			InputFileConfig prev = filenameconfigs.put(respath.getFileName(), fileconfig);
			if (prev != null) {
				throw new IllegalArgumentException(
						"Name conflict: " + respath.getFileName() + " with " + respath + " and " + prev.resourcePath);
			}
			inputs.add(fileconfig);
		}

		LocalFileProvider fp = LocalFileProvider.getInstance();

		NavigableMap<SakerPath, ContentDescriptor> outputfilecontents = new ConcurrentSkipListMap<>();

		//XXX make more build cluster RMI performant
		ThreadUtils.runParallelItems(inputs, in -> {
			SakerFile file = in.file;

			SakerDirectory outdir = outputdir.getDirectoryCreate(in.parentDirectoryName)
					.getDirectoryCreate(file.getName());
			outdir.clear();
			Path outputdirlocalpath = taskcontext.mirror(outdir,
					new OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate());

			System.out.println("AAPT2CompileWorkerTaskFactory.run() " + in.resourcePath);
			ProcessBuilder pb = new ProcessBuilder(exepath.toString(), "compile", "-o", outputdirlocalpath.toString(),
					taskcontext.mirror(file).toString());
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
			int res;
			try {
				StreamUtils.copyStream(proc.getInputStream(), procout);
				res = proc.waitFor();
			} finally {
				if (!procout.isEmpty()) {
					procout.writeTo(taskcontext.getStandardOut());
				}
			}
			if (res != 0) {
				throw new IOException("aapt2 compilation failed: " + pb.command());
			}
			for (String fname : fp.getDirectoryEntries(outputdirlocalpath).keySet()) {
				ProviderHolderPathKey outpathkey = fp.getPathKey(outputdirlocalpath.resolve(fname));

				ContentDescriptor outputfilecontent = taskcontext.invalidateGetContentDescriptor(outpathkey);
				SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outpathkey);
				outdir.add(outfile);
				SakerPath outfilepath = outfile.getSakerPath();

				outputfilecontents.put(outfilepath, outputfilecontent);

			}
		});
		outputdir.synchronize();

		System.out.println("Output:");
		outputfilecontents.keySet().forEach(System.out::println);

		taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputfilecontents);

		NavigableMap<String, SDKDescription> pinnedsdks = new TreeMap<>(SDKSupportUtils.getSDKNameComparator());
		for (Entry<String, SDKReference> entry : sdkrefs.entrySet()) {
			String sdkname = entry.getKey();
			SDKDescription desc = sdkDescriptions.get(sdkname);
			if (desc instanceof IndeterminateSDKDescription) {
				desc = ((IndeterminateSDKDescription) desc).pinSDKDescription(entry.getValue());
			}
			pinnedsdks.put(sdkname, desc);
		}

		return new AAPT2CompileTaskOutputImpl(outputdir.getSakerPath(),
				ImmutableUtils.makeImmutableNavigableSet(outputfilecontents.navigableKeySet()), compilationid,
				pinnedsdks);
	}

	private final class OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate implements DirectoryVisitPredicate {
		@Override
		public boolean visitFile(String name, SakerFile file) {
			return false;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}

		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return null;
		}

		@Override
		public NavigableSet<String> getSynchronizeFilesToKeep() {
			return null;
		}
	}

	private static class InputFileConfig {

		protected SakerPath resourcePath;
		protected String parentDirectoryName;
		protected SakerFile file;

		public InputFileConfig(SakerPath resourcePath, String parentDirectoryName, SakerFile file) {
			this.resourcePath = resourcePath;
			this.parentDirectoryName = parentDirectoryName;
			this.file = file;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);

		out.writeObject(resourceDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();

		resourceDirectory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((resourceDirectory == null) ? 0 : resourceDirectory.hashCode());
		result = prime * result + ((sdkDescriptions == null) ? 0 : sdkDescriptions.hashCode());
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
		AAPT2CompileWorkerTaskFactory other = (AAPT2CompileWorkerTaskFactory) obj;
		if (resourceDirectory == null) {
			if (other.resourceDirectory != null)
				return false;
		} else if (!resourceDirectory.equals(other.resourceDirectory))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		return true;
	}

}
