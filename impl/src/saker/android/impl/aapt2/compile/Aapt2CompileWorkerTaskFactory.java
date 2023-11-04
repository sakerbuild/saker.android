package saker.android.impl.aapt2.compile;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import saker.android.api.aapt2.compile.Aapt2CompileWorkerTaskOutput;
import saker.android.impl.aapt2.Aapt2Executor;
import saker.android.impl.aapt2.Aapt2Utils;
import saker.android.impl.aapt2.compile.option.Aapt2CompilerInputOption;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.main.aapt2.Aapt2CompileTaskFactory;
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
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.DeltaType;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public class Aapt2CompileWorkerTaskFactory
		implements TaskFactory<Aapt2CompileWorkerTaskOutput>, Task<Aapt2CompileWorkerTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<Aapt2CompilerInputOption> inputs;
	private Aapt2CompilationConfiguration configuration;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	private transient boolean verbose;
	private transient String displayName;

	/**
	 * For {@link Externalizable}.
	 */
	public Aapt2CompileWorkerTaskFactory() {
	}

	public Aapt2CompileWorkerTaskFactory(Set<Aapt2CompilerInputOption> inputs,
			Aapt2CompilationConfiguration configuration) {
		this.inputs = inputs;
		this.configuration = configuration;
	}

	public void setConfiguration(Aapt2CompilationConfiguration configuration) {
		this.configuration = configuration;
	}

	public void setInputs(Set<Aapt2CompilerInputOption> inputs) {
		this.inputs = inputs;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidBuildToolsSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not specified.");
		}
		//TODO turn back remote dispatchability if the local file location inputs are taken into account
//		remoteDispatchableEnvironmentSelector = SDKSupportUtils
//				.getSDKBasedClusterExecutionEnvironmentSelector(sdkdescriptions.values());
	}

	@Override
	public TaskInvocationConfiguration getInvocationConfiguration() {
		TaskInvocationConfiguration.Builder builder = TaskInvocationConfiguration.builder()
				.setRequestedComputationTokenCount(1);
		if (remoteDispatchableEnvironmentSelector != null) {
			builder.setRemoteDispatchable(true);
			builder.setExecutionEnvironmentSelector(remoteDispatchableEnvironmentSelector);
		}
		return builder.build();
	}

	@Override
	public Task<? extends Aapt2CompileWorkerTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	private static class InputFileSpec<T> {
		protected T fileData;
		protected SakerPath basePath;

		public InputFileSpec(T fileData, SakerPath basePath) {
			this.fileData = fileData;
			this.basePath = basePath;
		}
	}

	@Override
	public Aapt2CompileWorkerTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		CompileState prevstate = taskcontext.getPreviousTaskOutput(CompileState.class, CompileState.class);

		Aapt2CompileWorkerTaskIdentifier taskid = (Aapt2CompileWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		String displaystr = ObjectUtils.nullDefault(displayName, compilationid::toString);
		taskcontext.setStandardOutDisplayIdentifier("aapt2.compile:" + displaystr);
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.setDisplayInformation("aapt2.compile:" + displaystr,
					Aapt2CompileTaskFactory.TASK_NAME + ":" + displaystr);
		}

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(Aapt2CompileTaskFactory.TASK_NAME + "/" + compilationid));

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

		NavigableMap<SakerPath, SakerFile> directinputresourcefiles = new TreeMap<>();

		NavigableMap<SakerPath, Integer> assignedresourcenames = new ConcurrentSkipListMap<>();
		NavigableMap<SakerPath, InputFileSpec<ContentDescriptor>> collectedlocalinputfiles = new TreeMap<>();
		NavigableMap<SakerPath, InputFileSpec<SakerFile>> collectedfiles = new TreeMap<>();

		LinkedHashSet<FileCollectionStrategy> inputcollectionstrategies = new LinkedHashSet<>();
		for (Aapt2CompilerInputOption inoption : inputs) {
			inoption.accept(new Aapt2CompilerInputOption.Visitor() {
				@Override
				public void visitResources(Set<FileLocation> files) {
					for (FileLocation fl : files) {
						fl.accept(new FileLocationVisitor() {
							@Override
							public void visit(ExecutionFileLocation loc) {
								SakerPath infilepath = loc.getPath();
								SakerFile resfile = taskcontext.getTaskUtilities().resolveFileAtPath(infilepath);
								if (resfile == null) {
									throw ObjectUtils.sneakyThrow(
											new FileNotFoundException("Input resource file not found: " + infilepath));
								}
								directinputresourcefiles.put(infilepath, resfile);
							}

							@Override
							public void visit(LocalFileLocation loc) {
								SakerPath localpath = loc.getLocalPath();
								ContentDescriptor filecd = taskcontext.getTaskUtilities().getReportExecutionDependency(
										SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(localpath,
												taskcontext.getTaskId()));
								collectedlocalinputfiles.put(localpath, new InputFileSpec<>(filecd,
										getResourceBasePath(localpath, assignedresourcenames)));
							}
						});
					}
				}

				@Override
				public void visitResourceDirectory(FileLocation dir) {
					dir.accept(new FileLocationVisitor() {
						@Override
						public void visit(ExecutionFileLocation loc) {
							inputcollectionstrategies.add(new AndroidResourcesFileCollectionStrategy(loc.getPath()));
						}

						@Override
						public void visit(LocalFileLocation loc) {
							NavigableMap<SakerPath, ContentDescriptor> resfiles = taskcontext.getTaskUtilities()
									.getReportExecutionDependency(
											new AndroidResourcesLocalInputFileContentsExecutionProperty(
													loc.getLocalPath(), taskcontext.getTaskId()));
							for (Entry<SakerPath, ContentDescriptor> entry : resfiles.entrySet()) {
								collectedlocalinputfiles.put(entry.getKey(), new InputFileSpec<>(entry.getValue(),
										getResourceBasePath(entry.getKey(), assignedresourcenames)));
							}
						}
					});
				}
			});
		}

		taskcontext.getTaskUtilities().reportInputFileDependency(Aapt2CompilerTags.INPUT_RESOURCE,
				directinputresourcefiles.values());

		NavigableMap<SakerPath, SakerFile> collectedinputfiles = taskcontext.getTaskUtilities()
				.collectFilesReportInputFileAndAdditionDependency(Aapt2CompilerTags.INPUT_RESOURCE,
						inputcollectionstrategies);

		for (Entry<SakerPath, SakerFile> entry : collectedinputfiles.entrySet()) {
			SakerPath resourcepath = entry.getKey();
			collectedfiles.put(resourcepath,
					new InputFileSpec<>(entry.getValue(), getResourceBasePath(resourcepath, assignedresourcenames)));
		}
		for (Entry<SakerPath, SakerFile> entry : directinputresourcefiles.entrySet()) {
			SakerPath resourcepath = entry.getKey();
			collectedfiles.put(resourcepath,
					new InputFileSpec<>(entry.getValue(), getResourceBasePath(resourcepath, assignedresourcenames)));
		}

		Aapt2CompilationConfiguration thiscompilationconfig = this.configuration;

		if (prevstate != null) {
			if (!prevstate.compilationConfig.equals(thiscompilationconfig)
					|| !prevstate.sdkReferences.equals(sdkrefs)) {
				prevstate = null;
			}
		}

		NavigableMap<SakerPath, ContentDescriptor> outputfilecontents = new ConcurrentSkipListMap<>();

		NavigableMap<SakerPath, InputFileSpec<SakerFile>> inputfiles;
		NavigableMap<SakerPath, InputFileSpec<ContentDescriptor>> localinputfiles;

		CompileState nstate;
		if (prevstate != null) {
			nstate = new CompileState(prevstate);

			NavigableMap<SakerPath, SakerFile> changedinputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.INPUT_FILE_CHANGE), Aapt2CompilerTags.INPUT_RESOURCE);
			NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), Aapt2CompilerTags.OUTPUT_COMPILED);

			inputfiles = new TreeMap<>();
			localinputfiles = new TreeMap<>();

			if (!changedoutputfiles.isEmpty()) {
				ObjectUtils.iterateSortedMapEntriesDual(
						prevstate.pathOutputFiles.subMap(changedoutputfiles.firstKey(), true,
								changedoutputfiles.lastKey(), true),
						changedoutputfiles, (filepath, prevoutput, file) -> {
							InputFileState instate = deleteOutputDirectoryForInputPath(taskcontext, outputdir, nstate,
									prevoutput.inputFile);
							if (instate != null) {
								instate.fileLocation.accept(new FileLocationVisitor() {
									@Override
									public void visit(ExecutionFileLocation loc) {
										SakerPath inpath = loc.getPath();
										InputFileSpec<SakerFile> infile = collectedfiles.get(inpath);
										if (infile != null) {
											inputfiles.put(inpath, infile);
										}
									}

									@Override
									public void visit(LocalFileLocation loc) {
										SakerPath inpath = loc.getLocalPath();
										InputFileSpec<ContentDescriptor> infile = collectedlocalinputfiles.get(inpath);
										if (infile != null) {
											localinputfiles.put(inpath, infile);
										}
									}
								});
							}
						});
			}
			ObjectUtils.iterateSortedMapEntries(prevstate.pathInputFiles, collectedfiles,
					(filepath, previnput, filespec) -> {
						if (filespec != null) {
							if (previnput == null) {
								//an input file was added
							} else if (changedinputfiles.containsKey(filepath)) {
								//an input file was changed
							} else if (previnput.outputDirectoryRelativePath.equals(filespec.basePath)) {
								//unchanged input
								return;
							}
						} else {
							//an input file was removed
						}

						deleteOutputDirectoryForInputPath(taskcontext, outputdir, nstate,
								ExecutionFileLocation.create(filepath));
						if (filespec != null) {
							//add the file as input
							inputfiles.put(filepath, filespec);
						}
					});

			ObjectUtils.iterateSortedMapEntries(prevstate.localPathInputFiles, collectedlocalinputfiles,
					(filepath, previnput, filespec) -> {
						if (previnput != null && filespec != null && previnput.contents.equals(filespec.fileData)
								&& previnput.outputDirectoryRelativePath.equals(filespec.basePath)) {
							//no need to recompile
							return;
						}
						deleteOutputDirectoryForInputPath(taskcontext, outputdir, nstate,
								LocalFileLocation.create(filepath));
						if (filespec != null) {
							localinputfiles.put(filepath, filespec);
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

			nstate.localPathInputFiles = new ConcurrentSkipListMap<>();

			outputdir.clear();
			inputfiles = collectedfiles;
			localinputfiles = collectedlocalinputfiles;
		}

		if (!inputfiles.isEmpty() || !localinputfiles.isEmpty()) {
			Collection<InputFileConfig> inputs = new ArrayList<>();

			for (Entry<SakerPath, InputFileSpec<SakerFile>> entry : inputfiles.entrySet()) {
				SakerPath resourcepath = entry.getKey();

				InputFileState inputstate = new InputFileState(ExecutionFileLocation.create(resourcepath),
						entry.getValue().basePath);

				InputFileConfig fileconfig = new ExecutionInputFileConfig(inputstate, entry.getValue().fileData);
				inputs.add(fileconfig);
			}
			for (Entry<SakerPath, InputFileSpec<ContentDescriptor>> entry : localinputfiles.entrySet()) {
				SakerPath resourcepath = entry.getKey();

				LocalInputFileState inputstate = new LocalInputFileState(LocalFileLocation.create(resourcepath),
						entry.getValue().basePath, entry.getValue().fileData);

				InputFileConfig fileconfig = new LocalInputFileConfig(inputstate, resourcepath);
				inputs.add(fileconfig);
			}

			LocalFileProvider fp = LocalFileProvider.getInstance();

			//XXX make more build cluster RMI performant
			ThreadUtils.runParallelItems(inputs, in -> {

				SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(outputdir,
						in.input.outputDirectoryRelativePath);

				outdir.clear();
				Path outputdirlocalpath = taskcontext.mirror(outdir);

				ArrayList<String> cmd = new ArrayList<>();
				cmd.add("compile");
				for (Aapt2CompilerFlag f : thiscompilationconfig.getFlags()) {
					cmd.add(f.argument);
				}
				if (verbose) {
					cmd.add("-v");
				}
				cmd.add("-o");
				cmd.add(outputdirlocalpath.toString());

				//XXX output text symbols?
//				cmd.add("--output-text-symbols");
//				cmd.add(outputdirlocalpath.resolve("textsymbols.txt").toString());

				cmd.add(in.getPath(taskcontext).toString());

				UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
				try {
					Aapt2Executor executor = Aapt2Utils.getAapt2Executor(environment, sdkrefs);
					int res = executor.invokeAapt2WithArguments(cmd, procout);
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
					CompiledAapt2FileContentDescriptor outputfilecd = new CompiledAapt2FileContentDescriptor(
							in.getContentDescriptor(), in.input.outputDirectoryRelativePath, fname);

					taskcontext.invalidate(outpathkey);
					//XXX the invalidation and file creation should be 1 call
					SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outpathkey,
							outputfilecd);
					outdir.add(outfile);
					SakerPath outfilepath = outfile.getSakerPath();

					outputfilecontents.put(outfilepath, outputfilecd);

					in.input.outputFiles.add(outfilepath);
					nstate.pathOutputFiles.put(outfilepath, new OutputFileState(in.input.fileLocation, outputfilecd));
				}
				in.input.fileLocation.accept(new FileLocationVisitor() {

					@Override
					public void visit(ExecutionFileLocation loc) {
						nstate.pathInputFiles.put(loc.getPath(), in.input);
					}

					@Override
					public void visit(LocalFileLocation loc) {
						nstate.localPathInputFiles.put(loc.getLocalPath(), (LocalInputFileState) in.input);
					}
				});
			});
		}
		outputdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(Aapt2CompilerTags.OUTPUT_COMPILED,
				outputfilecontents);

		NavigableMap<String, SDKDescription> pinnedsdks = SDKSupportUtils.pinSDKSelection(sdkDescriptions, sdkrefs);

		taskcontext.setTaskOutput(CompileState.class, nstate);

		return new Aapt2CompileTaskOutputImpl(
				ImmutableUtils.makeImmutableNavigableSet(outputfilecontents.navigableKeySet()), compilationid,
				pinnedsdks);
	}

	private static SakerPath getResourceBasePath(SakerPath resourcepath,
			NavigableMap<SakerPath, Integer> basepathcounter) {
		String parentfname = resourcepath.getParent().getFileName();

		String resfilename = resourcepath.getFileName();
		SakerPath res = SakerPath.valueOf(parentfname).resolve(resfilename);
		int id = basepathcounter.compute(res, (k, i) -> {
			if (i == null) {
				return 0;
			}
			return i + 1;
		});
		if (id == 0) {
			return res;
		}
		return res.resolveSibling(resfilename + "." + id);
	}

	private static InputFileState deleteOutputDirectoryForInputPath(TaskContext taskcontext, SakerDirectory outputdir,
			CompileState nstate, FileLocation inputfile) {
		InputFileState[] previnstate = { null };
		inputfile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				previnstate[0] = nstate.pathInputFiles.remove(loc.getPath());
			}

			@Override
			public void visit(LocalFileLocation loc) {
				previnstate[0] = nstate.localPathInputFiles.remove(loc.getLocalPath());
			}

		});
		return deleteOutputDirectoryForInputState(taskcontext, outputdir, nstate, previnstate[0]);
	}

	private static InputFileState deleteOutputDirectoryForInputState(TaskContext taskcontext, SakerDirectory outputdir,
			CompileState nstate, InputFileState pinstate) {
		if (pinstate != null) {
			nstate.pathOutputFiles.keySet().removeAll(pinstate.outputFiles);
			deleteOutputDirectoryForInput(taskcontext, outputdir, pinstate);
		}
		return pinstate;
	}

	private static void deleteOutputDirectoryForInput(TaskContext taskcontext, SakerDirectory outputdir,
			InputFileState previnstate) {
		SakerFile outdirfile = taskcontext.getTaskUtilities().resolveAtRelativePath(outputdir,
				previnstate.outputDirectoryRelativePath);
		if (outdirfile != null) {
			outdirfile.remove();
		}
	}

	private static abstract class InputFileConfig {
		protected InputFileState input;

		public InputFileConfig(InputFileState input) {
			this.input = input;
		}

		public abstract Path getPath(TaskContext taskcontext) throws IOException;

		public abstract ContentDescriptor getContentDescriptor();
	}

	private static class ExecutionInputFileConfig extends InputFileConfig {
		protected SakerFile file;

		public ExecutionInputFileConfig(InputFileState input, SakerFile file) {
			super(input);
			this.file = file;
		}

		@Override
		public Path getPath(TaskContext taskcontext) throws IOException {
			return taskcontext.mirror(file);
		}

		@Override
		public ContentDescriptor getContentDescriptor() {
			return file.getContentDescriptor();
		}
	}

	private static class LocalInputFileConfig extends InputFileConfig {
		protected SakerPath file;
		protected ContentDescriptor contents;

		public LocalInputFileConfig(LocalInputFileState input, SakerPath file) {
			super(input);
			this.file = file;
			this.contents = input.contents;
		}

		@Override
		public Path getPath(TaskContext taskcontext) throws IOException {
			return LocalFileProvider.toRealPath(file);
		}

		@Override
		public ContentDescriptor getContentDescriptor() {
			return contents;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, inputs);
		out.writeObject(configuration);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);

		out.writeBoolean(verbose);
		out.writeObject(displayName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputs = SerialUtils.readExternalImmutableLinkedHashSet(in);
		configuration = SerialUtils.readExternalObject(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();

		verbose = in.readBoolean();
		displayName = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
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
		Aapt2CompileWorkerTaskFactory other = (Aapt2CompileWorkerTaskFactory) obj;
		if (configuration == null) {
			if (other.configuration != null)
				return false;
		} else if (!configuration.equals(other.configuration))
			return false;
		if (inputs == null) {
			if (other.inputs != null)
				return false;
		} else if (!inputs.equals(other.inputs))
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

		protected FileLocation fileLocation;
		protected SakerPath outputDirectoryRelativePath;
		protected NavigableSet<SakerPath> outputFiles;

		/**
		 * For {@link Externalizable}.
		 */
		public InputFileState() {
		}

		public InputFileState(FileLocation path, SakerPath outputDirectoryRelativePath) {
			this.fileLocation = path;
			this.outputDirectoryRelativePath = outputDirectoryRelativePath;
			this.outputFiles = new ConcurrentSkipListSet<>();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(fileLocation);
			out.writeObject(outputDirectoryRelativePath);
			SerialUtils.writeExternalCollection(out, outputFiles);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			fileLocation = (FileLocation) in.readObject();
			outputDirectoryRelativePath = (SakerPath) in.readObject();
			outputFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}
	}

	private static class LocalInputFileState extends InputFileState {
		private static final long serialVersionUID = 1L;

		protected ContentDescriptor contents;

		/**
		 * For {@link Externalizable}.
		 */
		public LocalInputFileState() {
		}

		public LocalInputFileState(FileLocation path, SakerPath outputDirectoryRelativePath,
				ContentDescriptor contents) {
			super(path, outputDirectoryRelativePath);
			this.contents = contents;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(contents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			contents = SerialUtils.readExternalObject(in);
		}
	}

	private static class OutputFileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected FileLocation inputFile;
		protected ContentDescriptor content;

		/**
		 * For {@link Externalizable}.
		 */
		public OutputFileState() {
		}

		public OutputFileState(FileLocation inputFile, ContentDescriptor content) {
			this.inputFile = inputFile;
			this.content = content;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(inputFile);
			out.writeObject(content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputFile = (FileLocation) in.readObject();
			content = (ContentDescriptor) in.readObject();
		}
	}

	private static class CompileState implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected Aapt2CompilationConfiguration compilationConfig;
		protected NavigableMap<String, SDKReference> sdkReferences;
		protected NavigableMap<SakerPath, InputFileState> pathInputFiles;
		protected NavigableMap<SakerPath, OutputFileState> pathOutputFiles;

		protected NavigableMap<SakerPath, LocalInputFileState> localPathInputFiles;

		/**
		 * For {@link Externalizable}.
		 */
		public CompileState() {
		}

		public CompileState(Aapt2CompilationConfiguration compilationConfig,
				NavigableMap<String, SDKReference> sdkReferences) {
			this.compilationConfig = compilationConfig;
			this.sdkReferences = sdkReferences;
		}

		public CompileState(CompileState copy) {
			this.compilationConfig = copy.compilationConfig;
			this.sdkReferences = copy.sdkReferences;
			this.pathInputFiles = new ConcurrentSkipListMap<>(copy.pathInputFiles);
			this.pathOutputFiles = new ConcurrentSkipListMap<>(copy.pathOutputFiles);

			this.localPathInputFiles = new ConcurrentSkipListMap<>(copy.localPathInputFiles);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(compilationConfig);
			SerialUtils.writeExternalMap(out, sdkReferences);
			SerialUtils.writeExternalMap(out, pathInputFiles);
			SerialUtils.writeExternalMap(out, pathOutputFiles);
			SerialUtils.writeExternalMap(out, localPathInputFiles);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			compilationConfig = SerialUtils.readExternalObject(in);
			sdkReferences = SerialUtils.readExternalSortedImmutableNavigableMap(in,
					SDKSupportUtils.getSDKNameComparator());
			pathInputFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
			pathOutputFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
			localPathInputFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}

	}
}
