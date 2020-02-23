package saker.android.impl.apk.sign;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.api.apk.sign.SignApkTaskOutput;
import saker.android.impl.aapt2.OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.support.InstrumentingJarClassLoaderDataFinder;
import saker.android.main.apk.sign.SignApkTaskFactory;
import saker.build.exception.InvalidFileTypeException;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.build.util.cache.CacheKey;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;

public class SignApkWorkerTaskFactory
		implements TaskFactory<SignApkTaskOutput>, Task<SignApkTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath inputPath;
	private SakerPath outputPath;
	private Boolean v1SigningEnabled;
	private Boolean v2SigningEnabled;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public SignApkWorkerTaskFactory() {
	}

	public void setInputPath(SakerPath inputPath) {
		this.inputPath = inputPath;
	}

	public void setOutputPath(SakerPath outputPath) {
		this.outputPath = outputPath;
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

	public SDKDescription getAndroidBuildToolsSDKDescription() {
		return ObjectUtils.getMapValue(sdkDescriptions, AndroidBuildToolsSDKReference.SDK_NAME);
	}

	public void setV1SigningEnabled(Boolean v1SigningEnabled) {
		this.v1SigningEnabled = v1SigningEnabled;
	}

	public void setV2SigningEnabled(Boolean v2SigningEnabled) {
		this.v2SigningEnabled = v2SigningEnabled;
	}

	public Boolean getV1SigningEnabled() {
		return v1SigningEnabled;
	}

	public Boolean getV2SigningEnabled() {
		return v2SigningEnabled;
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
	public SignApkTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}
		taskcontext.setStandardOutDisplayIdentifier(SignApkTaskFactory.TASK_NAME + ":" + outputPath.getFileName());

		MirroredFileContents mirroredinputfile;
		try {
			mirroredinputfile = taskcontext.getTaskUtilities().mirrorFileAtPathContents(inputPath);
		} catch (FileNotFoundException | InvalidFileTypeException e) {
			taskcontext.reportInputFileDependency(null, inputPath, CommonTaskContentDescriptors.IS_NOT_FILE);
			FileNotFoundException fnfe = new FileNotFoundException(inputPath.toString());
			fnfe.initCause(e);
			taskcontext.abortExecution(fnfe);
			return null;
		}
		taskcontext.reportInputFileDependency(null, inputPath, mirroredinputfile.getContents());

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputPath.getParent());

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
		ApkSignExecutor executor = environment
				.getCachedData(new ApkSignExecutorCacheKey(environment, getAndroidBuildToolsSDKDescription()));

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}

		Path inputfilelocalpath = mirroredinputfile.getPath();
		Path outputfilelocalpath = taskcontext
				.mirror(outputdir, OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate.INSTANCE)
				.resolve(outputPath.getFileName());

		executor.run(taskcontext, this, sdkrefs, inputfilelocalpath, outputfilelocalpath);

		ProviderHolderPathKey outputfilepathkey = LocalFileProvider.getInstance().getPathKey(outputfilelocalpath);
		ContentDescriptor outputfilecd = taskcontext.invalidateGetContentDescriptor(outputfilepathkey);
		SakerFile outputfile = taskcontext.getTaskUtilities().createProviderPathFile(outputPath.getFileName(),
				outputfilepathkey);
		outputdir.add(outputfile);

		outputfile.synchronize();

		SakerPath outputabsolutepath = outputfile.getSakerPath();
		taskcontext.reportOutputFileDependency(null, outputabsolutepath, outputfilecd);

		return new SignApkTaskOutputImpl(outputabsolutepath);
	}

	@Override
	public Task<? extends SignApkTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputPath);
		out.writeObject(outputPath);
		out.writeObject(v1SigningEnabled);
		out.writeObject(v2SigningEnabled);

		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputPath = (SakerPath) in.readObject();
		outputPath = (SakerPath) in.readObject();
		v1SigningEnabled = (Boolean) in.readObject();
		v2SigningEnabled = (Boolean) in.readObject();

		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputPath == null) ? 0 : inputPath.hashCode());
		result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
		result = prime * result + ((sdkDescriptions == null) ? 0 : sdkDescriptions.hashCode());
		result = prime * result + ((v1SigningEnabled == null) ? 0 : v1SigningEnabled.hashCode());
		result = prime * result + ((v2SigningEnabled == null) ? 0 : v2SigningEnabled.hashCode());
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
		SignApkWorkerTaskFactory other = (SignApkWorkerTaskFactory) obj;
		if (inputPath == null) {
			if (other.inputPath != null)
				return false;
		} else if (!inputPath.equals(other.inputPath))
			return false;
		if (outputPath == null) {
			if (other.outputPath != null)
				return false;
		} else if (!outputPath.equals(other.outputPath))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		if (v1SigningEnabled == null) {
			if (other.v1SigningEnabled != null)
				return false;
		} else if (!v1SigningEnabled.equals(other.v1SigningEnabled))
			return false;
		if (v2SigningEnabled == null) {
			if (other.v2SigningEnabled != null)
				return false;
		} else if (!v2SigningEnabled.equals(other.v2SigningEnabled))
			return false;
		return true;
	}

	private static class ApkSignExecutorCacheKey implements CacheKey<ApkSignExecutor, MultiDataClassLoader> {
		private static final ClassLoader APKSIGNEXECUTOR_CLASSLOADER = ApkSignExecutor.class.getClassLoader();

		private transient SakerEnvironment environment;

		private SDKDescription buildToolsSdk;

		public ApkSignExecutorCacheKey(SakerEnvironment environment, SDKDescription buildToolsSdk) {
			this.environment = environment;
			this.buildToolsSdk = buildToolsSdk;
		}

		@Override
		public MultiDataClassLoader allocate() throws Exception {
			SDKReference sdkref = SDKSupportUtils.resolveSDKReference(environment, buildToolsSdk);
			SakerPath apksignerjarpath = sdkref.getPath(AndroidBuildToolsSDKReference.PATH_APKSIGNER_JAR);
			if (apksignerjarpath == null) {
				throw new SDKPathNotFoundException("apksigner.jar not found in Android build tools: " + buildToolsSdk);
			}
			ClassLoaderDataFinder jarcldf;
			try {
				jarcldf = new InstrumentingJarClassLoaderDataFinder(LocalFileProvider.toRealPath(apksignerjarpath));
			} catch (IOException e) {
				throw new SDKPathNotFoundException("apksigner.jar not found in Android build tools: " + buildToolsSdk,
						e);
			}
			return new MultiDataClassLoader(APKSIGNEXECUTOR_CLASSLOADER, jarcldf,
					SubDirectoryClassLoaderDataFinder.create("apksignersupport", APKSIGNEXECUTOR_CLASSLOADER));
		}

		@Override
		public ApkSignExecutor generate(MultiDataClassLoader resource) throws Exception {
			Class<?> apksignerexecutorimplclass = Class.forName("saker.android.apksignersupport.ApkSignExecutorImpl",
					false, resource);
			return (ApkSignExecutor) apksignerexecutorimplclass.getMethod("createApkSignExecutor").invoke(null);
		}

		@Override
		public boolean validate(ApkSignExecutor data, MultiDataClassLoader resource) {
			return true;
		}

		@Override
		public long getExpiry() {
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(ApkSignExecutor data, MultiDataClassLoader resource) throws Exception {
			IOUtils.close(resource.getDatasFinders());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((buildToolsSdk == null) ? 0 : buildToolsSdk.hashCode());
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
			ApkSignExecutorCacheKey other = (ApkSignExecutorCacheKey) obj;
			if (buildToolsSdk == null) {
				if (other.buildToolsSdk != null)
					return false;
			} else if (!buildToolsSdk.equals(other.buildToolsSdk))
				return false;
			return true;
		}

	}

}
