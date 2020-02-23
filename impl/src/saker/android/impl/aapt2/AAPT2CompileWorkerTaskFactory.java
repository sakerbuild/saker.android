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
import java.util.concurrent.ConcurrentSkipListSet;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.main.aapt2.AAPT2CompileTaskFactory;
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
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;
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

	private AAPT2CompilationConfiguration configuration;
	private transient boolean verbose;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2CompileWorkerTaskFactory() {
	}

	public void setConfiguration(AAPT2CompilationConfiguration configuration) {
		this.configuration = configuration;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
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

		CompileState prevstate = taskcontext.getPreviousTaskOutput(CompileState.class, CompileState.class);

		AAPT2CompileWorkerTaskIdentifier taskid = (AAPT2CompileWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		taskcontext.setStandardOutDisplayIdentifier(AAPT2CompileTaskFactory.TASK_NAME + ":" + compilationid);

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AAPT2CompileTaskFactory.TASK_NAME + "/" + compilationid));

		SakerPath outputdirpath = outputdir.getSakerPath();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}
		SDKReference buildtoolssdkref = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		if (buildtoolssdkref == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not found.");
		}
		SakerPath exepath = buildtoolssdkref.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdkref);
		}

		NavigableMap<SakerPath, SakerFile> collectedfiles = taskcontext.getTaskUtilities()
				.collectFilesReportInputFileAndAdditionDependency(AAPT2TaskTags.INPUT_RESOURCE,
						new AndroidResourcesFileCollectionStrategy(resourceDirectory));

		AAPT2CompilationConfiguration thiscompilationconfig = this.configuration;

		if (prevstate != null) {
			if (!prevstate.compilationConfig.equals(thiscompilationconfig)
					|| !prevstate.sdkReferences.equals(sdkrefs)) {
				prevstate = null;
			}
		}

		NavigableMap<SakerPath, ContentDescriptor> outputfilecontents = new ConcurrentSkipListMap<>();

		NavigableMap<SakerPath, SakerFile> inputfiles;

		CompileState nstate;
		if (prevstate != null) {
			nstate = new CompileState(prevstate);

			NavigableMap<SakerPath, SakerFile> changedinputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.INPUT_FILE_CHANGE), AAPT2TaskTags.INPUT_RESOURCE);
			NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), AAPT2TaskTags.OUTPUT_COMPILED);

			inputfiles = new TreeMap<>();

			ObjectUtils.iterateSortedMapEntriesDual(prevstate.pathOutputFiles, changedoutputfiles,
					(filepath, prevoutput, file) -> {
						InputFileState instate = deleteOutputDirectoryForInputPath(taskcontext, outputdir, nstate,
								prevoutput.inputFilePath);
						if (instate != null) {
							SakerFile infile = collectedfiles.get(instate.path);
							if (infile != null) {
								inputfiles.put(instate.path, infile);
							}
						}
					});
			ObjectUtils.iterateSortedMapEntries(prevstate.pathInputFiles, collectedfiles,
					(filepath, previnput, file) -> {
						if (file != null) {
							if (previnput == null) {
								//an input file was added
							} else if (changedinputfiles.containsKey(filepath)) {
								//an input file was changed
							} else {
								//unchanged input
								return;
							}
						} else {
							//an input file was removed
						}

						deleteOutputDirectoryForInputPath(taskcontext, outputdir, nstate, filepath);
						if (file != null) {
							//add the file as input
							inputfiles.put(filepath, file);
						}
					});

			//XXX make more efficient
			for (Entry<SakerPath, OutputFileState> entry : nstate.pathOutputFiles.entrySet()) {
				outputfilecontents.put(entry.getKey(), entry.getValue().content);
			}
		} else {
			nstate = new CompileState(thiscompilationconfig, sdkrefs);
			nstate.pathInputFiles = new ConcurrentSkipListMap<>();
			nstate.pathOutputFiles = new ConcurrentSkipListMap<>();

			outputdir.clear();
			inputfiles = collectedfiles;
		}

		if (!inputfiles.isEmpty()) {
			Map<String, Map<String, InputFileConfig>> resdirinputfiles = new TreeMap<>();

			Collection<InputFileConfig> inputs = new ArrayList<>();

			for (Entry<SakerPath, SakerFile> entry : inputfiles.entrySet()) {
				SakerPath resourcepath = entry.getKey();
				String parentfname = resourcepath.getParent().getFileName();
				Map<String, InputFileConfig> filenameconfigs = resdirinputfiles.computeIfAbsent(parentfname,
						Functionals.treeMapComputer());

				InputFileState inputstate = new InputFileState(resourcepath,
						SakerPath.valueOf(parentfname).resolve(resourcepath.getFileName()));

				InputFileConfig fileconfig = new InputFileConfig(inputstate, entry.getValue());
				InputFileConfig prev = filenameconfigs.put(resourcepath.getFileName(), fileconfig);
				if (prev != null) {
					throw new IllegalArgumentException("Name conflict: " + resourcepath.getFileName() + " with "
							+ resourcepath + " and " + prev.input.path);
				}
				inputs.add(fileconfig);
			}

			LocalFileProvider fp = LocalFileProvider.getInstance();

			//XXX make more build cluster RMI performant
			ThreadUtils.runParallelItems(inputs, in -> {
				SakerFile file = in.file;

				SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(outputdir,
						in.input.outputDirectoryRelativePath);

				outdir.clear();
				Path outputdirlocalpath = taskcontext.mirror(outdir,
						OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate.INSTANCE);

				ArrayList<String> cmd = new ArrayList<>();
				cmd.add("compile");
				if (thiscompilationconfig.isLegacy()) {
					cmd.add("--legacy");
				}
				if (thiscompilationconfig.isNoCrunch()) {
					cmd.add("--no-crunch");
				}
				if (thiscompilationconfig.isPseudoLocalize()) {
					cmd.add("--pseudo-localize");
				}
				if (verbose) {
					cmd.add("-v");
				}
				cmd.add("-o");
				cmd.add(outputdirlocalpath.toString());
				cmd.add(taskcontext.mirror(file).toString());

				UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
				try {
					int res = AAPT2Utils.invokeAAPT2WithArguments(environment, exepath, cmd, procout);
					if (res != 0) {
						throw new IOException("aapt2 compilation failed.");
					}
				} finally {
					if (!procout.isEmpty()) {
						procout.writeTo(taskcontext.getStandardOut());
					}
				}

				for (String fname : fp.getDirectoryEntries(outputdirlocalpath).keySet()) {
					ProviderHolderPathKey outpathkey = fp.getPathKey(outputdirlocalpath.resolve(fname));

					//XXX don't call file.getContentDescriptor but store it during file collection
					CompiledAAPT2FileContentDescriptor outputfilecd = new CompiledAAPT2FileContentDescriptor(
							file.getContentDescriptor(), in.input.outputDirectoryRelativePath, fname);

					taskcontext.invalidate(outpathkey);
					//XXX the invalidation and file creation should be 1 call
					SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outpathkey,
							outputfilecd);
					outdir.add(outfile);
					SakerPath outfilepath = outfile.getSakerPath();

					outputfilecontents.put(outfilepath, outputfilecd);

					in.input.outputFiles.add(outfilepath);
					nstate.pathOutputFiles.put(outfilepath, new OutputFileState(in.input.path, outputfilecd));
				}
				nstate.pathInputFiles.put(in.input.path, in.input);
			});
		}
		outputdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(AAPT2TaskTags.OUTPUT_COMPILED, outputfilecontents);

		NavigableMap<String, SDKDescription> pinnedsdks = new TreeMap<>(SDKSupportUtils.getSDKNameComparator());
		for (Entry<String, SDKReference> entry : sdkrefs.entrySet()) {
			String sdkname = entry.getKey();
			SDKDescription desc = sdkDescriptions.get(sdkname);
			if (desc instanceof IndeterminateSDKDescription) {
				desc = ((IndeterminateSDKDescription) desc).pinSDKDescription(entry.getValue());
			}
			pinnedsdks.put(sdkname, desc);
		}

		taskcontext.setTaskOutput(CompileState.class, nstate);

		return new AAPT2CompileTaskOutputImpl(outputdirpath,
				ImmutableUtils.makeImmutableNavigableSet(outputfilecontents.navigableKeySet()), compilationid,
				pinnedsdks);
	}

	private static InputFileState deleteOutputDirectoryForInputPath(TaskContext taskcontext, SakerDirectory outputdir,
			CompileState nstate, SakerPath inputpath) {
		InputFileState previnstate = nstate.pathInputFiles.remove(inputpath);
		if (previnstate != null) {
			nstate.pathOutputFiles.keySet().removeAll(previnstate.outputFiles);
			deleteOutputDirectoryForInput(taskcontext, outputdir, previnstate);
		}
		return previnstate;
	}

	private static void deleteOutputDirectoryForInput(TaskContext taskcontext, SakerDirectory outputdir,
			InputFileState previnstate) {
		SakerFile outdirfile = taskcontext.getTaskUtilities().resolveAtRelativePath(outputdir,
				previnstate.outputDirectoryRelativePath);
		if (outdirfile != null) {
			outdirfile.remove();
		}
	}

	private static class InputFileConfig {
		protected InputFileState input;
		protected SakerFile file;

		public InputFileConfig(InputFileState input, SakerFile file) {
			this.input = input;
			this.file = file;
		}

	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(configuration);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);

		out.writeObject(resourceDirectory);
		out.writeBoolean(verbose);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		configuration = SerialUtils.readExternalObject(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();

		resourceDirectory = (SakerPath) in.readObject();
		verbose = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configuration == null) ? 0 : configuration.hashCode());
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
		if (configuration == null) {
			if (other.configuration != null)
				return false;
		} else if (!configuration.equals(other.configuration))
			return false;
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

	private static class InputFileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath path;
		protected SakerPath outputDirectoryRelativePath;
		protected NavigableSet<SakerPath> outputFiles;

		/**
		 * For {@link Externalizable}.
		 */
		public InputFileState() {
		}

		public InputFileState(SakerPath path, SakerPath outputDirectoryRelativePath) {
			this.path = path;
			this.outputDirectoryRelativePath = outputDirectoryRelativePath;
			this.outputFiles = new ConcurrentSkipListSet<>();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(path);
			out.writeObject(outputDirectoryRelativePath);
			SerialUtils.writeExternalCollection(out, outputFiles);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			path = (SakerPath) in.readObject();
			outputDirectoryRelativePath = (SakerPath) in.readObject();
			outputFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}

	}

	private static class OutputFileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath inputFilePath;
		protected ContentDescriptor content;

		/**
		 * For {@link Externalizable}.
		 */
		public OutputFileState() {
		}

		public OutputFileState(SakerPath inputFilePath, ContentDescriptor content) {
			this.inputFilePath = inputFilePath;
			this.content = content;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(inputFilePath);
			out.writeObject(content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputFilePath = (SakerPath) in.readObject();
			content = (ContentDescriptor) in.readObject();
		}
	}

	private static class CompileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected AAPT2CompilationConfiguration compilationConfig;
		protected NavigableMap<String, SDKReference> sdkReferences;
		protected NavigableMap<SakerPath, InputFileState> pathInputFiles;
		protected NavigableMap<SakerPath, OutputFileState> pathOutputFiles;

		/**
		 * For {@link Externalizable}.
		 */
		public CompileState() {
		}

		public CompileState(AAPT2CompilationConfiguration compilationConfig,
				NavigableMap<String, SDKReference> sdkReferences) {
			this.compilationConfig = compilationConfig;
			this.sdkReferences = sdkReferences;
		}

		public CompileState(CompileState copy) {
			this.compilationConfig = copy.compilationConfig;
			this.sdkReferences = copy.sdkReferences;
			this.pathInputFiles = new ConcurrentSkipListMap<>(copy.pathInputFiles);
			this.pathOutputFiles = new ConcurrentSkipListMap<>(copy.pathOutputFiles);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(compilationConfig);
			SerialUtils.writeExternalMap(out, sdkReferences);
			SerialUtils.writeExternalMap(out, pathInputFiles);
			SerialUtils.writeExternalMap(out, pathOutputFiles);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			compilationConfig = SerialUtils.readExternalObject(in);
			sdkReferences = SerialUtils.readExternalSortedImmutableNavigableMap(in,
					SDKSupportUtils.getSDKNameComparator());
			pathInputFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
			pathOutputFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}

	}
}
