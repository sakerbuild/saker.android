package saker.android.impl.aapt2.aar;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.android.impl.aapt2.AAPT2Utils;
import saker.android.impl.aar.AarFolderExtractWorkerTaskFactory;
import saker.android.impl.aar.AarResourcesTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
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
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.trace.BuildTrace;
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

public class AAPT2AarWorkerTaskFactory
		implements TaskFactory<AAPT2AarWorkerTaskOutput>, Task<AAPT2AarWorkerTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation input;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2AarWorkerTaskFactory() {
	}

	public AAPT2AarWorkerTaskFactory(FileLocation input) {
		this.input = input;
	}

	public TaskIdentifier createWorkerTaskIdentifier() {
		return new AAPT2AarWorkerTaskIdentifier(SakerPath.valueOf("saker.android.aapt2.aar")
				.resolve(AarFolderExtractWorkerTaskFactory.createOutputRelativePath(input)));
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
	public Task<? extends AAPT2AarWorkerTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2AarWorkerTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		AAPT2AarWorkerTaskIdentifier taskid = (AAPT2AarWorkerTaskIdentifier) taskcontext.getTaskId();

		//TODO better displayid
		taskcontext.setStandardOutDisplayIdentifier("saker.android.aapt2.aar");

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
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

		AarFolderExtractWorkerTaskFactory resworker = new AarFolderExtractWorkerTaskFactory(input, "res/");
		AarResourcesTaskOutput resourcesout = taskcontext.getTaskUtilities().runTaskResult(resworker.getWorkerTaskId(),
				resworker);
		FileLocation resourcesbasedir = resourcesout.getFileLocation();

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				taskid.getOutputRelativePath());
		outdir.clear();
		SakerPath outdirpath = outdir.getSakerPath();

		Path outdirmirrorpath = taskcontext.mirror(outdir);

		NavigableMap<SakerPath, ContentDescriptor> inputdependencies = new ConcurrentSkipListMap<>();
		NavigableMap<SakerPath, ContentDescriptor> outputdependencies = new ConcurrentSkipListMap<>();

		ThreadUtils.runParallelItems(resourcesout.getResourceFiles(), fl -> {
			ArrayList<String> cmd = new ArrayList<>();
			cmd.add("compile");
			SakerPath[] flrelativepath = { null };
			fl.accept(new FileLocationVisitor() {

				@Override
				public void visit(ExecutionFileLocation loc) {
					flrelativepath[0] = ((ExecutionFileLocation) resourcesbasedir).getPath().relativize(loc.getPath());
					SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(loc.getPath());
					if (f == null) {
						throw ObjectUtils.sneakyThrow(new FileNotFoundException(loc.getPath().toString()));
					}
					try {
						MirroredFileContents filecontents = taskcontext.getTaskUtilities()
								.mirrorFileAtPathContents(loc.getPath());
						inputdependencies.put(loc.getPath(), filecontents.getContents());
						cmd.add(filecontents.getPath().toString());
					} catch (Exception e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				}

				@Override
				public void visit(LocalFileLocation loc) {
					flrelativepath[0] = ((LocalFileLocation) resourcesbasedir).getLocalPath()
							.relativize(loc.getLocalPath());
					taskcontext.getTaskUtilities().getReportExecutionDependency(
							SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(loc.getLocalPath(),
									taskcontext.getTaskId()));
					cmd.add(loc.getLocalPath().toString());
				}
			});

			cmd.add("-o");
			cmd.add(outdirmirrorpath.toString());

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
		});
		LocalFileProvider fp = LocalFileProvider.getInstance();
		for (String fname : fp.getDirectoryEntries(outdirmirrorpath).keySet()) {
			ProviderHolderPathKey outpathkey = fp.getPathKey(outdirmirrorpath.resolve(fname));

			taskcontext.invalidate(outpathkey);

			SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outpathkey);
			outdir.add(outfile);
			SakerPath outfilepath = outfile.getSakerPath();

			outputdependencies.put(outfilepath, outfile.getContentDescriptor());
		}

		outdir.synchronize();

		taskcontext.getTaskUtilities().reportInputFileDependency(null, inputdependencies);
		taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputdependencies);

		return new AAPT2AarWorkerTaskOutputImpl(
				ImmutableUtils.makeImmutableNavigableSet(outputdependencies.navigableKeySet()));
	}

	//TODO enable computation tokens for this if the task is started from the frontend instead
//	@Override
//	public int getRequestedComputationTokenCount() {
//		return 1;
//	}
//	@Override
//	public Set<String> getCapabilities() {
//		if (remoteDispatchableEnvironmentSelector != null) {
//			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
//		}
//		return TaskFactory.super.getCapabilities();
//	}
//
//	@Override
//	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
//		if (remoteDispatchableEnvironmentSelector != null) {
//			return remoteDispatchableEnvironmentSelector;
//		}
//		return TaskFactory.super.getExecutionEnvironmentSelector();
//	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(input);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		input = (FileLocation) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		AAPT2AarWorkerTaskFactory other = (AAPT2AarWorkerTaskFactory) obj;
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
