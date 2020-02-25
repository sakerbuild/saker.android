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
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;

public class SignApkWorkerTaskFactory
		implements TaskFactory<SignApkTaskOutput>, Task<SignApkTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation inputFile;
	private SakerPath outputPath;
	private Boolean v1SigningEnabled;
	private Boolean v2SigningEnabled;

	private FileLocation keyStoreFile;
	private String alias;
	private String keyStorePassword;
	private String keyPassword;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public SignApkWorkerTaskFactory() {
	}

	public void setInputFile(FileLocation inputPath) {
		this.inputFile = inputPath;
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

	public void setSigning(FileLocation keystore, String keystorepassword, String alias, String keypassword) {
		if (keystore != null) {
			this.keyStoreFile = keystore;
			this.keyStorePassword = keystorepassword;
			this.alias = alias;
			this.keyPassword = keypassword;
		} else {
			this.keyStoreFile = null;
			this.keyStorePassword = null;
			this.alias = null;
			this.keyPassword = null;
		}
	}

	public FileLocation getKeyStoreFile() {
		return keyStoreFile;
	}

	public String getKeyPassword() {
		return keyPassword;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	public String getAlias() {
		return alias;
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

		Path[] inputfilelocalpath = { null };
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
				taskcontext.reportInputFileDependency(null, inputpath, mirroredinputfile.getContents());
				inputfilelocalpath[0] = mirroredinputfile.getPath();
			}
		});

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

		Path outputfilelocalpath = taskcontext
				.mirror(outputdir, OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate.INSTANCE)
				.resolve(outputPath.getFileName());

		executor.run(taskcontext, this, sdkrefs, inputfilelocalpath[0], outputfilelocalpath);

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
		out.writeObject(inputFile);
		out.writeObject(outputPath);
		out.writeObject(v1SigningEnabled);
		out.writeObject(v2SigningEnabled);
		out.writeObject(keyStoreFile);
		out.writeObject(alias);
		out.writeObject(keyStorePassword);
		out.writeObject(keyPassword);

		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = (FileLocation) in.readObject();
		outputPath = (SakerPath) in.readObject();
		v1SigningEnabled = (Boolean) in.readObject();
		v2SigningEnabled = (Boolean) in.readObject();
		keyStoreFile = (FileLocation) in.readObject();
		alias = (String) in.readObject();
		keyStorePassword = (String) in.readObject();
		keyPassword = (String) in.readObject();

		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
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
		if (alias == null) {
			if (other.alias != null)
				return false;
		} else if (!alias.equals(other.alias))
			return false;
		if (inputFile == null) {
			if (other.inputFile != null)
				return false;
		} else if (!inputFile.equals(other.inputFile))
			return false;
		if (keyPassword == null) {
			if (other.keyPassword != null)
				return false;
		} else if (!keyPassword.equals(other.keyPassword))
			return false;
		if (keyStoreFile == null) {
			if (other.keyStoreFile != null)
				return false;
		} else if (!keyStoreFile.equals(other.keyStoreFile))
			return false;
		if (keyStorePassword == null) {
			if (other.keyStorePassword != null)
				return false;
		} else if (!keyStorePassword.equals(other.keyStorePassword))
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
