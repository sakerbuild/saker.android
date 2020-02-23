package saker.android.impl.d8;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.d8.D8TaskFactory;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.JarClassLoaderDataFinder;
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

public class D8WorkerTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Deprecated
	private SakerPath classDirectory;
	private boolean release;
	private int minApi;
	private boolean noDesugaring;
	private NavigableSet<String> mainDexClasses;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public D8WorkerTaskFactory() {
	}

	public SDKDescription getAndroidBuildToolsSDKDescription() {
		return ObjectUtils.getMapValue(sdkDescriptions, AndroidBuildToolsSDKReference.SDK_NAME);
	}

	public SDKDescription getAndroidPlatformSDKDescription() {
		return ObjectUtils.getMapValue(sdkDescriptions, AndroidPlatformSDKReference.SDK_NAME);
	}

	public int getMinApi() {
		return minApi;
	}

	public boolean isNoDesugaring() {
		return noDesugaring;
	}

	public boolean isRelease() {
		return release;
	}

	public NavigableSet<String> getMainDexClasses() {
		return mainDexClasses;
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

	@Deprecated
	public void setClassDirectory(SakerPath classDirectory) {
		this.classDirectory = classDirectory;
	}

	@Deprecated
	public SakerPath getClassDirectory() {
		return classDirectory;
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
	public Object run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		D8WorkerTaskIdentifier taskid = (D8WorkerTaskIdentifier) taskcontext.getTaskId();

		taskcontext.setStandardOutDisplayIdentifier(D8TaskFactory.TASK_NAME + ":" + taskid.getCompilationIdentifier());

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
		D8Executor executor = environment
				.getCachedData(new D8ExecutorCacheKey(environment, getAndroidBuildToolsSDKDescription()));

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}
		return executor.run(taskcontext, this, sdkrefs);
	}

	@Override
	public Task<? extends Object> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(release);
		out.writeInt(minApi);
		out.writeBoolean(noDesugaring);
		SerialUtils.writeExternalCollection(out, mainDexClasses);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(remoteDispatchableEnvironmentSelector);

		out.writeObject(classDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		release = in.readBoolean();
		minApi = in.readInt();
		noDesugaring = in.readBoolean();
		mainDexClasses = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();

		classDirectory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classDirectory == null) ? 0 : classDirectory.hashCode());
		result = prime * result + ((mainDexClasses == null) ? 0 : mainDexClasses.hashCode());
		result = prime * result + minApi;
		result = prime * result + (noDesugaring ? 1231 : 1237);
		result = prime * result + (release ? 1231 : 1237);
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
		D8WorkerTaskFactory other = (D8WorkerTaskFactory) obj;
		if (classDirectory == null) {
			if (other.classDirectory != null)
				return false;
		} else if (!classDirectory.equals(other.classDirectory))
			return false;
		if (mainDexClasses == null) {
			if (other.mainDexClasses != null)
				return false;
		} else if (!mainDexClasses.equals(other.mainDexClasses))
			return false;
		if (minApi != other.minApi)
			return false;
		if (noDesugaring != other.noDesugaring)
			return false;
		if (release != other.release)
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		return true;
	}

	private static class D8ExecutorCacheKey implements CacheKey<D8Executor, MultiDataClassLoader> {
		private static final ClassLoader D8EXECUTOR_CLASSLOADER = D8Executor.class.getClassLoader();

		private transient SakerEnvironment environment;

		private SDKDescription buildToolsSdk;

		public D8ExecutorCacheKey(SakerEnvironment environment, SDKDescription buildToolsSdk) {
			this.environment = environment;
			this.buildToolsSdk = buildToolsSdk;
		}

		@Override
		public MultiDataClassLoader allocate() throws Exception {
			SDKReference sdkref = SDKSupportUtils.resolveSDKReference(environment, buildToolsSdk);
			SakerPath d8jarpath = sdkref.getPath(AndroidBuildToolsSDKReference.PATH_D8_JAR);
			if (d8jarpath == null) {
				throw new SDKPathNotFoundException("d8.jar not found in Android build tools: " + buildToolsSdk);
			}
			ClassLoaderDataFinder jarcldf;
			try {
				jarcldf = new JarClassLoaderDataFinder(LocalFileProvider.toRealPath(d8jarpath));
			} catch (IOException e) {
				throw new SDKPathNotFoundException("d8.jar not found in Android build tools: " + buildToolsSdk, e);
			}
			return new MultiDataClassLoader(D8EXECUTOR_CLASSLOADER, jarcldf,
					SubDirectoryClassLoaderDataFinder.create("d8support", D8EXECUTOR_CLASSLOADER));
		}

		@Override
		public D8Executor generate(MultiDataClassLoader resource) throws Exception {
			Class<?> d8executorimplclass = Class.forName("saker.android.d8support.D8ExecutorImpl", false, resource);
			return (D8Executor) d8executorimplclass.getMethod("createD8Executor").invoke(null);
		}

		@Override
		public boolean validate(D8Executor data, MultiDataClassLoader resource) {
			return true;
		}

		@Override
		public long getExpiry() {
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(D8Executor data, MultiDataClassLoader resource) throws Exception {
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
			D8ExecutorCacheKey other = (D8ExecutorCacheKey) obj;
			if (buildToolsSdk == null) {
				if (other.buildToolsSdk != null)
					return false;
			} else if (!buildToolsSdk.equals(other.buildToolsSdk))
				return false;
			return true;
		}

	}

}
