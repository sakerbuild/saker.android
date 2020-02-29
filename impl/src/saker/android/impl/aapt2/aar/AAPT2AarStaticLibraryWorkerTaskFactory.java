package saker.android.impl.aapt2.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.aapt2.compile.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.aapt2.compile.option.AAPT2CompilerInputOption;
import saker.android.impl.aapt2.compile.option.ResourcesAAPT2CompilerInputOption;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskFactory;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskIdentifier;
import saker.android.impl.aapt2.link.AAPT2LinkerFlag;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.FileAAPT2LinkerInput;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.AarEntryNotFoundException;
import saker.android.impl.classpath.LiteralStructuredTaskResult;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;

public class AAPT2AarStaticLibraryWorkerTaskFactory
		implements TaskFactory<AAPT2LinkTaskOutput>, Task<AAPT2LinkTaskOutput>, Externalizable, TaskIdentifier {
	private static final long serialVersionUID = 1L;

	private StructuredTaskResult input;
	private AAPT2CompilationConfiguration configuration;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient boolean verbose;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2AarStaticLibraryWorkerTaskFactory() {
	}

	public AAPT2AarStaticLibraryWorkerTaskFactory(FileLocation input, AAPT2CompilationConfiguration configuration) {
		this(new LiteralStructuredTaskResult(input), configuration);
	}

	public AAPT2AarStaticLibraryWorkerTaskFactory(StructuredTaskResult input,
			AAPT2CompilationConfiguration configuration) {
		this.input = input;
		this.configuration = configuration;
	}

	private String createInputFileNameHash(FileLocation input) {
		SakerPath filelocationpath = AarEntryExtractWorkerTaskFactory.getFileLocationPath(input);
		return StringUtils.toHexString(FileUtils.hashString(
				filelocationpath.toString() + "/" + StringUtils.toStringJoin(",", configuration.getFlags())));
	}

//	public TaskIdentifier createWorkerTaskIdentifier() {
//		SakerPath filelocationpath = AarEntryExtractWorkerTaskFactory.getFileLocationPath(input);
//		String infilenamehash = StringUtils.toHexString(FileUtils.hashString(
//				filelocationpath.toString() + "/" + StringUtils.toStringJoin(",", configuration.getFlags())));
//		return new AAPT2AarStaticLibraryWorkerTaskIdentifier(SakerPath.valueOf("saker.android.aapt2.aar")
//				.resolve(infilenamehash).resolve(filelocationpath.getFileName()));
//	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidBuildToolsSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not specified.");
		}
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	@Override
	public Task<? extends AAPT2LinkTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2LinkTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		//TODO better displayid
		taskcontext.setStandardOutDisplayIdentifier("saker.android.aapt2.aar");

		FileLocation inputfilelocation = (FileLocation) input.toResult(taskcontext);

		AarEntryExtractWorkerTaskFactory resworker = new AarEntryExtractWorkerTaskFactory(inputfilelocation, "res");
		AarExtractTaskOutput resourcesout = taskcontext.getTaskUtilities().runTaskResult(resworker.createTaskId(),
				resworker);

		AarEntryExtractWorkerTaskFactory manifestworker = new AarEntryExtractWorkerTaskFactory(inputfilelocation,
				"AndroidManifest.xml");
		AarExtractTaskOutput manifestout = taskcontext.getTaskUtilities().runTaskResult(manifestworker.createTaskId(),
				manifestworker);
		Set<FileLocation> resdirfiles;
		try {
			resdirfiles = resourcesout.getDirectoryFileLocations();
			if (resdirfiles == null) {
				throw new IllegalArgumentException("AAR res entry doesn't contain any files: " + inputfilelocation);
			}
		} catch (AarEntryNotFoundException e) {
			return null;
		}

		Set<AAPT2CompilerInputOption> compileworkerinput = new HashSet<>();
		compileworkerinput.add(new ResourcesAAPT2CompilerInputOption(resdirfiles));

		AAPT2CompileWorkerTaskFactory compileworkertask = new AAPT2CompileWorkerTaskFactory(compileworkerinput,
				configuration);
		compileworkertask.setVerbose(verbose);
		compileworkertask.setSDKDescriptions(sdkDescriptions);
		AAPT2CompileWorkerTaskIdentifier compileworkertaskid = new AAPT2CompileWorkerTaskIdentifier(
				CompilationIdentifier.valueOf(createInputFileNameHash(inputfilelocation)));
		AAPT2CompileTaskOutput compileoutput = taskcontext.getTaskUtilities().runTaskResult(compileworkertaskid,
				compileworkertask);

		Set<AAPT2LinkerInput> linkerinputs = new LinkedHashSet<>();
		for (SakerPath outpath : compileoutput.getOutputPaths()) {
			linkerinputs.add(new FileAAPT2LinkerInput(ExecutionFileLocation.create(outpath)));
		}
		Set<AAPT2LinkerFlag> linkflags = EnumSet.noneOf(AAPT2LinkerFlag.class);
		linkflags.add(AAPT2LinkerFlag.STATIC_LIB);
		AAPT2LinkWorkerTaskFactory linkworkertask = new AAPT2LinkWorkerTaskFactory(linkerinputs,
				manifestout.getFileLocation());
		linkworkertask.setVerbose(verbose);
		linkworkertask.setFlags(linkflags);
		linkworkertask.setSDKDescriptions(sdkDescriptions);
		AAPT2LinkWorkerTaskIdentifier linkworkertaskid = new AAPT2LinkWorkerTaskIdentifier(
				compileworkertaskid.getCompilationIdentifier());
		AAPT2LinkTaskOutput linkoutput = taskcontext.getTaskUtilities().runTaskResult(linkworkertaskid, linkworkertask);
		return linkoutput;
//
//		NavigableMap<String, SDKReference> sdkrefs;
//		//if we have an environment selector then the dependencies are reported during selection
//		if (remoteDispatchableEnvironmentSelector == null) {
//			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
//		} else {
//			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
//		}
//		SDKReference buildtoolssdkref = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
//		if (buildtoolssdkref == null) {
//			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not found.");
//		}
//		SakerPath exepath = buildtoolssdkref.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
//		if (exepath == null) {
//			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdkref);
//		}
//
//		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
//
//		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
//				taskid.getOutputRelativePath());
//		outdir.clear();
//		SakerPath outdirpath = outdir.getSakerPath();
//
//		Path outdirmirrorpath = taskcontext.mirror(outdir);
//
//		NavigableMap<SakerPath, ContentDescriptor> inputdependencies = new ConcurrentSkipListMap<>();
//		NavigableMap<SakerPath, ContentDescriptor> outputdependencies = new ConcurrentSkipListMap<>();
//
//		ThreadUtils.runParallelItems(resdirfiles, fl -> {
//			ArrayList<String> cmd = new ArrayList<>();
//			cmd.add("compile");
//			for (AAPT2CompilerFlag f : this.configuration.getFlags()) {
//				cmd.add(f.argument);
//			}
//			fl.accept(new FileLocationVisitor() {
//
//				@Override
//				public void visit(ExecutionFileLocation loc) {
//					SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(loc.getPath());
//					if (f == null) {
//						throw ObjectUtils.sneakyThrow(new FileNotFoundException(loc.getPath().toString()));
//					}
//					try {
//						MirroredFileContents filecontents = taskcontext.getTaskUtilities()
//								.mirrorFileAtPathContents(loc.getPath());
//						inputdependencies.put(loc.getPath(), filecontents.getContents());
//						cmd.add(filecontents.getPath().toString());
//					} catch (Exception e) {
//						throw ObjectUtils.sneakyThrow(e);
//					}
//				}
//
//				@Override
//				public void visit(LocalFileLocation loc) {
//					taskcontext.getTaskUtilities().getReportExecutionDependency(
//							SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(loc.getLocalPath(),
//									taskcontext.getTaskId()));
//					cmd.add(loc.getLocalPath().toString());
//				}
//			});
//			if (verbose) {
//				cmd.add("-v");
//			}
//			cmd.add("-o");
//			cmd.add(outdirmirrorpath.toString());
//
//			UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
//			try {
//				int res = AAPT2Utils.invokeAAPT2WithArguments(environment, exepath, cmd, procout);
//				if (res != 0) {
//					throw new IOException("aapt2 compilation failed.");
//				}
//			} finally {
//				if (!procout.isEmpty()) {
//					procout.writeTo(taskcontext.getStandardOut());
//				}
//			}
//		});
//		LocalFileProvider fp = LocalFileProvider.getInstance();
//		for (String fname : fp.getDirectoryEntries(outdirmirrorpath).keySet()) {
//			ProviderHolderPathKey outpathkey = fp.getPathKey(outdirmirrorpath.resolve(fname));
//
//			taskcontext.invalidate(outpathkey);
//
//			SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outpathkey);
//			outdir.add(outfile);
//			SakerPath outfilepath = outfile.getSakerPath();
//
//			outputdependencies.put(outfilepath, outfile.getContentDescriptor());
//		}
//
//		outdir.synchronize();
//
//		taskcontext.getTaskUtilities().reportInputFileDependency(null, inputdependencies);
//		taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputdependencies);
//
//		return new AAPT2AarStaticLibraryWorkerTaskOutputImpl(
//				ImmutableUtils.makeImmutableNavigableSet(outputdependencies.navigableKeySet()));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(input);
		out.writeObject(configuration);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeBoolean(verbose);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		input = SerialUtils.readExternalObject(in);
		configuration = SerialUtils.readExternalObject(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		verbose = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configuration == null) ? 0 : configuration.hashCode());
		result = prime * result + ((input == null) ? 0 : input.hashCode());
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
		AAPT2AarStaticLibraryWorkerTaskFactory other = (AAPT2AarStaticLibraryWorkerTaskFactory) obj;
		if (configuration == null) {
			if (other.configuration != null)
				return false;
		} else if (!configuration.equals(other.configuration))
			return false;
		if (input == null) {
			if (other.input != null)
				return false;
		} else if (!input.equals(other.input))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		return true;
	}
}
