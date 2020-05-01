package saker.android.impl.aapt2.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileWorkerTaskOutput;
import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.aapt2.compile.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.aapt2.compile.option.AAPT2CompilerInputOption;
import saker.android.impl.aapt2.compile.option.ResourcesAAPT2CompilerInputOption;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.AarEntryNotFoundException;
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
import saker.std.api.file.location.FileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AAPT2AarCompileWorkerTaskFactory implements TaskFactory<AAPT2AarCompileTaskOutput>,
		Task<AAPT2AarCompileTaskOutput>, Externalizable, TaskIdentifier {

	private static final long serialVersionUID = 1L;

	private StructuredTaskResult input;
	private AAPT2CompilationConfiguration configuration;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient boolean verbose;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2AarCompileWorkerTaskFactory() {
	}

	public AAPT2AarCompileWorkerTaskFactory(StructuredTaskResult input, AAPT2CompilationConfiguration configuration) {
		this.input = input;
		this.configuration = configuration;
	}

	private String createInputFileNameHash(FileLocation input) {
		SakerPath filelocationpath = AarEntryExtractWorkerTaskFactory.getFileLocationPath(input);
		return StringUtils.toHexString(FileUtils.hashString(
				filelocationpath.toString() + "/" + StringUtils.toStringJoin(",", configuration.getFlags())));
	}

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
	public Task<? extends AAPT2AarCompileTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2AarCompileTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			//this is a frontend task, as the compilation is done using another worker
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
		}

		taskcontext.setStandardOutDisplayIdentifier("aapt2.aar");

		FileLocation inputfilelocation = (FileLocation) input.toResult(taskcontext);
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			String fname = SakerStandardUtils.getFileLocationFileName(inputfilelocation);
			BuildTrace.setDisplayInformation("aapt2.aar:" + fname, "saker.android.aapt2.aar:" + fname);
		}

		AarEntryExtractWorkerTaskFactory resworker = new AarEntryExtractWorkerTaskFactory(inputfilelocation, "res");
		TaskIdentifier resworkertaskid = resworker.createTaskId();
		taskcontext.startTask(resworkertaskid, resworker, null);

		AarEntryExtractWorkerTaskFactory rtxtworker = new AarEntryExtractWorkerTaskFactory(inputfilelocation, "R.txt");
		TaskIdentifier rtxtworkertaskid = rtxtworker.createTaskId();
		taskcontext.startTask(rtxtworkertaskid, rtxtworker, null);

		AarEntryExtractWorkerTaskFactory manifestworker = new AarEntryExtractWorkerTaskFactory(inputfilelocation,
				"AndroidManifest.xml");
		TaskIdentifier manifestworkertaskid = manifestworker.createTaskId();
		taskcontext.startTask(manifestworkertaskid, manifestworker, null);

		AarExtractTaskOutput resourcesout = (AarExtractTaskOutput) taskcontext.getTaskResult(resworkertaskid);

		AarExtractTaskOutput rtxtout = (AarExtractTaskOutput) taskcontext.getTaskResult(rtxtworkertaskid);

		AarExtractTaskOutput manifestout = (AarExtractTaskOutput) taskcontext.getTaskResult(manifestworkertaskid);

		FileLocation rtxtfile = null;
		try {
			rtxtfile = rtxtout.getFileLocation();
		} catch (AarEntryNotFoundException e) {
			//ignore
		}

		FileLocation manifestfile;
		try {
			manifestfile = manifestout.getFileLocation();
		} catch (AarEntryNotFoundException e) {
			throw new IllegalArgumentException("AndroidManifest.xml not found in aar: " + inputfilelocation);
		}

		Set<FileLocation> resdirfiles;
		try {
			resdirfiles = resourcesout.getDirectoryFileLocations();
			if (resdirfiles == null) {
				throw new IllegalArgumentException("AAR res entry is not a folder: " + inputfilelocation);
			}
		} catch (AarEntryNotFoundException e) {
			return new AAPT2AarCompileTaskOutputImpl(inputfilelocation, null, rtxtfile, manifestfile);
		}

		Set<AAPT2CompilerInputOption> compileworkerinput = new HashSet<>();
		compileworkerinput.add(new ResourcesAAPT2CompilerInputOption(resdirfiles));

		AAPT2CompileWorkerTaskFactory compileworkertask = new AAPT2CompileWorkerTaskFactory(compileworkerinput,
				configuration);
		compileworkertask.setDisplayName(SakerStandardUtils.getFileLocationFileName(inputfilelocation));
		compileworkertask.setVerbose(verbose);
		compileworkertask.setSDKDescriptions(sdkDescriptions);
		AAPT2CompileWorkerTaskIdentifier compileworkertaskid = new AAPT2CompileWorkerTaskIdentifier(
				CompilationIdentifier.valueOf(createInputFileNameHash(inputfilelocation)));
		AAPT2CompileWorkerTaskOutput compileoutput = taskcontext.getTaskUtilities().runTaskResult(compileworkertaskid,
				compileworkertask);

		return new AAPT2AarCompileTaskOutputImpl(inputfilelocation, compileoutput, rtxtfile, manifestfile);
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
		AAPT2AarCompileWorkerTaskFactory other = (AAPT2AarCompileWorkerTaskFactory) obj;
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

	private static final class AAPT2AarCompileTaskOutputImpl implements AAPT2AarCompileTaskOutput, Externalizable {
		private static final long serialVersionUID = 1L;

		private FileLocation aarFile;
		private FileLocation rTxtFile;
		private FileLocation androidManifestFile;
		private NavigableSet<SakerPath> outputPaths;

		/**
		 * For {@link Externalizable}.
		 */
		public AAPT2AarCompileTaskOutputImpl() {
		}

		public AAPT2AarCompileTaskOutputImpl(FileLocation aarFile, AAPT2CompileWorkerTaskOutput compileOutput,
				FileLocation rTxtFile, FileLocation androidManifestFile) {
			this.aarFile = aarFile;
			this.outputPaths = compileOutput == null ? Collections.emptyNavigableSet() : compileOutput.getOutputPaths();
			this.rTxtFile = rTxtFile;
			this.androidManifestFile = androidManifestFile;
		}

		@Override
		public FileLocation getAarFile() {
			return aarFile;
		}

		@Override
		public NavigableSet<SakerPath> getOutputPaths() {
			return outputPaths;
		}

		@Override
		public FileLocation getRTxtFile() {
			return rTxtFile;
		}

		@Override
		public FileLocation getAndroidManifestXmlFile() {
			return androidManifestFile;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(aarFile);
			out.writeObject(rTxtFile);
			out.writeObject(androidManifestFile);
			SerialUtils.writeExternalCollection(out, outputPaths);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			aarFile = SerialUtils.readExternalObject(in);
			rTxtFile = SerialUtils.readExternalObject(in);
			androidManifestFile = SerialUtils.readExternalObject(in);
			outputPaths = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}
	}
}
