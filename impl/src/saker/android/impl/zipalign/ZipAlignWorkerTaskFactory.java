package saker.android.impl.zipalign;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import saker.android.api.zipalign.ZipAlignWorkerTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.main.zipalign.ZipAlignTaskFactory;
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
import saker.build.task.TaskInvocationConfiguration;
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

public class ZipAlignWorkerTaskFactory
		implements TaskFactory<ZipAlignWorkerTaskOutput>, Task<ZipAlignWorkerTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final int DEFAULT_ALIGNMENT = 4;

	private FileLocation inputFile;
	private SakerPath outputPath;

	private int alignment = DEFAULT_ALIGNMENT;

	private boolean pageAlignSharedObjectFile;
	private boolean verbose;
	private boolean zopfli;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public ZipAlignWorkerTaskFactory() {
	}

	public void setInputFile(FileLocation inputFile) {
		this.inputFile = inputFile;
	}

	public void setOutputPath(SakerPath outputPath) {
		this.outputPath = outputPath;
	}

	public void setPageAlignSharedObjectFile(boolean pageAlignSharedObjectFile) {
		this.pageAlignSharedObjectFile = pageAlignSharedObjectFile;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setZopfli(boolean zopfli) {
		this.zopfli = zopfli;
	}

	public void setAlignment(int alignment) {
		this.alignment = alignment;
	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidBuildToolsSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not specified.");
		}
		//TODO make remote dispatchable when local files are supported
//		remoteDispatchableEnvironmentSelector = SDKSupportUtils
//				.getSDKBasedClusterExecutionEnvironmentSelector(sdkdescriptions.values());
	}

	public SDKDescription getAndroidBuildToolsSDKDescription() {
		return ObjectUtils.getMapValue(sdkDescriptions, AndroidBuildToolsSDKReference.SDK_NAME);
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
	public ZipAlignWorkerTaskOutput run(TaskContext taskcontext) throws Exception {
		String fname = outputPath.getFileName();
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
			BuildTrace.setDisplayInformation("zipalign:" + fname, ZipAlignTaskFactory.TASK_NAME + ":" + fname);
		}
		taskcontext.setStandardOutDisplayIdentifier("zipalign:" + fname);

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
				ExecutionProperty<? extends ContentDescriptor> envprop = SakerStandardUtils
						.createLocalFileContentDescriptorExecutionProperty(loc.getLocalPath(), UUID.randomUUID());
				inputfilelocalpath[0] = LocalFileProvider.toRealPath(loc.getLocalPath());
				inputfilecd[0] = taskcontext.getTaskUtilities().getReportExecutionDependency(envprop);
			}
		});

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputPath.getParent());

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}

		SDKReference buildtoolssdk = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		SakerPath exepath = buildtoolssdk.getPath(AndroidBuildToolsSDKReference.PATH_ZIPALIGN_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("zipalign executable not found in SDK: " + buildtoolssdk);
		}

		//synchronize nothing to only create the directory
		Path outputfilelocalpath = taskcontext.mirror(outputdir, DirectoryVisitPredicate.synchronizeNothing())
				.resolve(fname);

		List<String> processargslist = new ArrayList<>();
		processargslist.add(exepath.toString());
		if (pageAlignSharedObjectFile) {
			processargslist.add("-p");
		}
		if (verbose) {
			processargslist.add("-v");
		}
		if (zopfli) {
			processargslist.add("-z");
		}
		processargslist.add("-f");
		processargslist.add(Integer.toString(alignment));
		processargslist.add(inputfilelocalpath[0].toString());
		processargslist.add(outputfilelocalpath.toString());

		ProcessBuilder pb = new ProcessBuilder(processargslist);
		pb.redirectErrorStream(true);

		Process proc = pb.start();
		IOUtils.close(proc.getOutputStream());
		UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();
		try {
			StreamUtils.copyStream(proc.getInputStream(), procout);
			int res = proc.waitFor();
			if (res != 0) {
				throw new IOException("zipalign failed: " + res);
			}
		} finally {
			if (!procout.isEmpty()) {
				procout.writeTo(taskcontext.getStandardOut());
			}
		}

		ProviderHolderPathKey outputfilepathkey = LocalFileProvider.getInstance().getPathKey(outputfilelocalpath);
		taskcontext.invalidate(outputfilepathkey);
		ContentDescriptor outputfilecd = new ZipAlignedArchiveContentDescriptor(inputfilecd[0]);
		SakerFile outputfile = taskcontext.getTaskUtilities().createProviderPathFile(fname, outputfilepathkey,
				outputfilecd);
		outputdir.add(outputfile);

		outputfile.synchronize();

		SakerPath outputabsolutepath = outputfile.getSakerPath();
		taskcontext.reportOutputFileDependency(null, outputabsolutepath, outputfilecd);

		return new ZipAlignTaskOutputImpl(outputabsolutepath);
	}

	@Override
	public Task<? extends ZipAlignWorkerTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputFile);
		out.writeObject(outputPath);
		out.writeInt(alignment);
		out.writeBoolean(pageAlignSharedObjectFile);
		out.writeBoolean(verbose);
		out.writeBoolean(zopfli);

		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = (FileLocation) in.readObject();
		outputPath = (SakerPath) in.readObject();
		alignment = in.readInt();
		pageAlignSharedObjectFile = in.readBoolean();
		verbose = in.readBoolean();
		zopfli = in.readBoolean();

		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(outputPath);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ZipAlignWorkerTaskFactory other = (ZipAlignWorkerTaskFactory) obj;
		if (alignment != other.alignment)
			return false;
		if (inputFile == null) {
			if (other.inputFile != null)
				return false;
		} else if (!inputFile.equals(other.inputFile))
			return false;
		if (outputPath == null) {
			if (other.outputPath != null)
				return false;
		} else if (!outputPath.equals(other.outputPath))
			return false;
		if (pageAlignSharedObjectFile != other.pageAlignSharedObjectFile)
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		if (verbose != other.verbose)
			return false;
		if (zopfli != other.zopfli)
			return false;
		return true;
	}
}
