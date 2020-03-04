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
import java.util.Objects;
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
import saker.build.task.delta.DeltaType;
import saker.build.task.utils.TaskUtils;
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
		SDKReference platformssdk = sdkrefs.get(AndroidPlatformSDKReference.SDK_NAME);
		SakerPath fwaidlpath = getFrameworkAidlPath(platformssdk);

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AidlTaskFactory.TASK_NAME + "/" + compilationid));

		CompilerState prevstate = taskcontext.getPreviousTaskOutput(CompilerState.class, CompilerState.class);

		NavigableMap<SakerPath, InputFileInfo> collectedabsoluteinputfiles = new TreeMap<>();
		NavigableMap<SakerPath, InputFileInfo> collectedrelativeinputfiles = new TreeMap<>();
		for (SakerPath srcdir : sourceDirectories) {
			NavigableMap<SakerPath, SakerFile> inputfiles = taskcontext.getTaskUtilities()
					.collectFilesReportInputFileAndAdditionDependency(AidlTaskTags.INPUT_AIDL,
							RecursiveIgnoreCaseExtensionFileCollectionStrategy.create(srcdir, ".aidl"));

			for (Entry<SakerPath, SakerFile> entry : inputfiles.entrySet()) {
				SakerPath dirrelative = srcdir.relativize(entry.getKey());
				InputFileInfo infileinfo = new InputFileInfo(entry.getKey(), dirrelative, entry.getValue());
				InputFileInfo prev = collectedrelativeinputfiles.put(dirrelative, infileinfo);
				if (prev != null) {
					throw new IllegalArgumentException(
							"Multiple input aidl files found with source directory relative path: " + dirrelative
									+ " as " + prev.absolutePath + " and " + entry.getKey());
				}
				collectedabsoluteinputfiles.put(entry.getKey(), infileinfo);
			}
		}

		if (prevstate != null) {
			if (!Objects.equals(prevstate.buildToolsSDK, buildtoolssdk)
					|| !Objects.equals(prevstate.platformsSDK, platformssdk)) {
				//clean
				prevstate = null;
			}
		}

		NavigableMap<SakerPath, ContentDescriptor> outputdependencies = new ConcurrentSkipListMap<>();

		NavigableMap<SakerPath, InputFileInfo> inputfiles;

		CompilerState nstate;
		if (prevstate != null) {
			nstate = new CompilerState(prevstate);

			NavigableMap<SakerPath, SakerFile> changedinputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.INPUT_FILE_CHANGE), AidlTaskTags.INPUT_AIDL);
			NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), AidlTaskTags.OUTPUT_FILE);

			inputfiles = new TreeMap<>();

			ObjectUtils.iterateSortedMapEntries(prevstate.pathOutputs, changedoutputfiles,
					(filepath, prevoutput, file) -> {
						InputFileState instate = removeOutputsForInputPath(taskcontext, nstate, prevoutput.inputPath);
						InputFileInfo ininfo = collectedabsoluteinputfiles.get(instate.path);
						if (ininfo != null) {
							inputfiles.put(ininfo.sourceDirectoryRelativePath, ininfo);
						}
					});

			ObjectUtils.iterateSortedMapEntries(prevstate.pathInputs, collectedabsoluteinputfiles,
					(filepath, previnput, infile) -> {
						if (infile != null) {
							if (previnput == null) {
								//an input file was added
							} else if (changedinputfiles.containsKey(filepath)) {
								//an input file was changed
								InputFileState instate = removeOutputsForInputPath(taskcontext, nstate, previnput.path);
								if (instate != null) {
									InputFileInfo ininfo = collectedabsoluteinputfiles.get(instate.path);
									if (ininfo != null) {
										inputfiles.put(ininfo.sourceDirectoryRelativePath, ininfo);
									}
								}
								return;
							} else {
								//unchanged input
								return;
							}
						} else {
							//an input file was removed
							removeOutputsForInputPath(taskcontext, nstate, previnput.path);
							return;
						}
						//add the file as input

						inputfiles.put(infile.sourceDirectoryRelativePath, infile);
					});

			//XXX make more efficient
			for (Entry<SakerPath, OutputFileState> entry : nstate.pathOutputs.entrySet()) {
				outputdependencies.put(entry.getKey(), entry.getValue().outputContents);
			}
		} else {
			nstate = new CompilerState();
			nstate.buildToolsSDK = buildtoolssdk;
			nstate.platformsSDK = platformssdk;
			nstate.pathInputs = new ConcurrentSkipListMap<>();
			nstate.pathOutputs = new ConcurrentSkipListMap<>();

			inputfiles = collectedrelativeinputfiles;

			outputdir.clear();
		}

		SakerDirectory javaoutdir = outputdir.getDirectoryCreate("java");

		SakerPath javasourcedirectory = javaoutdir.getSakerPath();

		Path javaoutdirlocalpath = taskcontext.mirror(javaoutdir);

		ThreadUtils.runParallelItems(inputfiles.values(), in -> {
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

			SakerPath outfilepath = outputfile.getSakerPath();
			outputdependencies.put(outfilepath, outputfilecd);

			nstate.pathInputs.put(in.absolutePath,
					new InputFileState(in.absolutePath, ImmutableUtils.singletonNavigableSet(outfilepath)));
			nstate.pathOutputs.put(outfilepath, new OutputFileState(in.absolutePath, outputfilecd));
		});

		outputdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(AidlTaskTags.OUTPUT_FILE, outputdependencies);

		taskcontext.setTaskOutput(CompilerState.class, nstate);

		return new AidlTaskOutputImpl(javasourcedirectory);
	}

	private static InputFileState removeOutputsForInputPath(TaskContext taskcontext, CompilerState nstate,
			SakerPath inputpath) {
		InputFileState instate = nstate.pathInputs.remove(inputpath);
		if (instate != null) {
			for (SakerPath outpath : instate.outputPaths) {
				SakerFile f = taskcontext.getTaskUtilities().resolveAtAbsolutePath(outpath);
				if (f != null) {
					f.remove();
				}
			}
			nstate.pathOutputs.keySet().removeAll(instate.outputPaths);
		}
		return instate;
	}

	private static SakerPath getFrameworkAidlPath(SDKReference platformssdk) throws Exception {
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

	private static class InputFileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath path;
		protected NavigableSet<SakerPath> outputPaths;

		/**
		 * For {@link Externalizable}.
		 */
		public InputFileState() {
		}

		public InputFileState(SakerPath path, NavigableSet<SakerPath> outputPaths) {
			this.path = path;
			this.outputPaths = outputPaths;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(path);
			SerialUtils.writeExternalCollection(out, outputPaths);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			path = SerialUtils.readExternalObject(in);
			outputPaths = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}

	}

	private static class OutputFileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath inputPath;
		protected ContentDescriptor outputContents;

		/**
		 * For {@link Externalizable}.
		 */
		public OutputFileState() {
		}

		public OutputFileState(SakerPath inputPath, ContentDescriptor outputContents) {
			this.inputPath = inputPath;
			this.outputContents = outputContents;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(inputPath);
			out.writeObject(outputContents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputPath = SerialUtils.readExternalObject(in);
			outputContents = SerialUtils.readExternalObject(in);
		}

	}

	private static class CompilerState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SDKReference buildToolsSDK;
		protected SDKReference platformsSDK;
		protected NavigableMap<SakerPath, InputFileState> pathInputs;
		protected NavigableMap<SakerPath, OutputFileState> pathOutputs;

		/**
		 * For {@link Externalizable}.
		 */
		public CompilerState() {
		}

		public CompilerState(CompilerState copy) {
			this.buildToolsSDK = copy.buildToolsSDK;
			this.platformsSDK = copy.platformsSDK;
			this.pathInputs = new ConcurrentSkipListMap<>(copy.pathInputs);
			this.pathOutputs = new ConcurrentSkipListMap<>(copy.pathOutputs);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(buildToolsSDK);
			out.writeObject(platformsSDK);
			SerialUtils.writeExternalMap(out, pathInputs);
			SerialUtils.writeExternalMap(out, pathOutputs);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			buildToolsSDK = SerialUtils.readExternalObject(in);
			platformsSDK = SerialUtils.readExternalObject(in);
			pathInputs = SerialUtils.readExternalSortedImmutableNavigableMap(in);
			pathOutputs = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}
	}
}
