package saker.android.impl.ndk.strip;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

import saker.android.api.ndk.strip.StripWorkerTaskOutput;
import saker.android.impl.sdk.AndroidNdkSDKReference;
import saker.android.main.ndk.strip.StripTaskFactory;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
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

public class StripWorkerTaskFactory
		implements TaskFactory<StripWorkerTaskOutput>, Task<StripWorkerTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation inputFile;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public StripWorkerTaskFactory() {
	}

	public StripWorkerTaskFactory(FileLocation inputFile) {
		this.inputFile = inputFile;
	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidNdkSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidNdkSDKReference.SDK_NAME + " SDK not specified.");
		}
		//TODO make remote dispatchable when local files are supported
//		remoteDispatchableEnvironmentSelector = SDKSupportUtils
//				.getSDKBasedClusterExecutionEnvironmentSelector(sdkdescriptions.values());
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
	public StripWorkerTaskOutput run(TaskContext taskcontext) throws Exception {
		StripWorkerTaskIdentifier taskid = (StripWorkerTaskIdentifier) taskcontext.getTaskId();
		SakerPath outputpath = taskid.getOutputPath();
		String fname = outputpath.getFileName();
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
			BuildTrace.setDisplayInformation("strip:" + fname, StripTaskFactory.TASK_NAME + ":" + fname);
		}
		taskcontext.setStandardOutDisplayIdentifier("strip:" + fname);

		Path[] inputfilelocalpath = { null };
		ContentDescriptor[] inputfilecd = { null };
		inputFile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerPath inputpath = loc.getPath();
				MirroredFileContents mirroredinputfile;
				try {
					mirroredinputfile = taskcontext.getTaskUtilities().mirrorFileAtPathContents(inputpath);
				} catch (IOException e) {
					taskcontext.reportInputFileDependency(null, inputpath, CommonTaskContentDescriptors.IS_NOT_FILE);
					FileNotFoundException fnfe = new FileNotFoundException(loc.toString());
					fnfe.initCause(e);
					ObjectUtils.sneakyThrow(fnfe);
					return;
				}
				inputfilecd[0] = mirroredinputfile.getContents();
				taskcontext.reportInputFileDependency(null, inputpath, mirroredinputfile.getContents());
				inputfilelocalpath[0] = mirroredinputfile.getPath();
			}

			@Override
			public void visit(LocalFileLocation loc) {
				SakerPath inputpath = loc.getLocalPath();
				ExecutionProperty<? extends ContentDescriptor> envprop = SakerStandardUtils
						.createLocalFileContentDescriptorExecutionProperty(inputpath, UUID.randomUUID());
				inputfilelocalpath[0] = LocalFileProvider.toRealPath(inputpath);
				inputfilecd[0] = taskcontext.getTaskUtilities().getReportExecutionDependency(envprop);
			}
		});

		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(
				SakerPathFiles.requireBuildDirectory(taskcontext), outputpath.getParent());

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}

		SDKReference ndksdk = sdkrefs.get(AndroidNdkSDKReference.SDK_NAME);
		SakerPath exepath = ndksdk.getPath(AndroidNdkSDKReference.PATH_STRIP_EXE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("strip executable not found in Android NDK: " + ndksdk);
		}

		//synchronize nothing to only create the directory
		Path outputfilelocalpath = taskcontext.mirror(outputdir, DirectoryVisitPredicate.synchronizeNothing())
				.resolve(fname);

		ProcessBuilder pb = new ProcessBuilder(exepath.toString(), "-o", outputfilelocalpath.toString(),
				inputfilelocalpath[0].toString());
		pb.redirectErrorStream(true);

		Process proc = pb.start();
		IOUtils.close(proc.getOutputStream());
		UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
		try {
			StreamUtils.copyStream(proc.getInputStream(), procout);
			int res = proc.waitFor();
			if (res != 0) {
				throw new IOException("strip failed: " + res);
			}
		} finally {
			if (!procout.isEmpty()) {
				procout.writeTo(taskcontext.getStandardOut());
			}
		}

		ProviderHolderPathKey outputfilepathkey = LocalFileProvider.getInstance().getPathKey(outputfilelocalpath);
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_013) {
			taskcontext.getTaskUtilities().invalidateWithPosixFilePermissions(outputfilepathkey);
		} else {
			taskcontext.invalidate(outputfilepathkey);
		}
		SakerFile outputfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outputfilepathkey);
		outputdir.add(outputfile);

		outputfile.synchronize();

		SakerPath outputabsolutepath = outputfile.getSakerPath();
		taskcontext.reportOutputFileDependency(null, outputabsolutepath, outputfile.getContentDescriptor());

		return new StripTaskOutputImpl(outputabsolutepath);
	}

	@Override
	public Task<? extends StripWorkerTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputFile);

		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = (FileLocation) in.readObject();

		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputFile == null) ? 0 : inputFile.hashCode());
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
		StripWorkerTaskFactory other = (StripWorkerTaskFactory) obj;
		if (inputFile == null) {
			if (other.inputFile != null)
				return false;
		} else if (!inputFile.equals(other.inputFile))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		return true;
	}

}
