package saker.android.d8support;

import java.util.NavigableMap;
import java.util.NavigableSet;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;

import saker.android.impl.d8.AndroidJarDescriptorsCacheKey;
import saker.android.impl.d8.D8Executor;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.AndroidJarDescriptorsCacheKey.AndroidJarData;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.sdk.support.api.SDKReference;

public class D8ExecutorImpl {
	public static D8Executor createD8Executor() {
//		try {
//			Class.forName("com.android.tools.r8.DataEntryResource", false, D8ExecutorImpl.class.getClassLoader());
//			//we can perform incremental dexing
//			return new IncrementalD8Executor();
//		} catch (ClassNotFoundException e) {
//			//we're using the older API of d8. we cannot properly determine the incremental origins of the generated per-file dex files
//			//therefore fall back to full dexing
//		}
		return new LegacyD8Executor();
	}

	public static String getDefaultDexFileName(int fileIndex) {
		return fileIndex == 0 ? "classes" + ".dex" : ("classes" + (fileIndex + 1) + ".dex");
	}

	public static void setD8BuilderCommonConfigurations(D8Command.Builder builder, D8WorkerTaskFactory workertask,
			SakerEnvironment environment, NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		setD8BuilderAndroidJar(builder, environment, sdkreferences);
		setD8BuilderMinApi(builder, workertask);
		setD8BuilderEnableDesugaring(builder, workertask);
		setD8BuilderMode(builder, workertask);
		setD8BuilderMainDexClasses(builder, workertask);
	}

	public static void setD8BuilderMode(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setMode(workertask.isRelease() ? CompilationMode.RELEASE : CompilationMode.DEBUG);
	}

	public static void setD8BuilderEnableDesugaring(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setEnableDesugaring(!workertask.isNoDesugaring());
	}

	public static void setD8BuilderMinApi(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		int minapi = workertask.getMinApi();
		if (minapi > 0) {
			builder.setMinApiLevel(minapi);
		}
	}

	public static void setD8BuilderAndroidJar(D8Command.Builder builder, SakerEnvironment environment,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		SDKReference platformsdk = sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME);
		if (platformsdk == null) {
			return;
		}
		SakerPath androidjarpath = platformsdk.getPath(AndroidPlatformSDKReference.PATH_ANDROID_JAR);
		if (androidjarpath == null) {
			return;
		}
		AndroidJarData jardata = environment.getCachedData(new AndroidJarDescriptorsCacheKey(androidjarpath));
		builder.addLibraryResourceProvider(new JarFileClassFileResourceProvider(jardata));
	}

	public static void setD8BuilderMainDexClasses(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		NavigableSet<String> maindexclasses = workertask.getMainDexClasses();
		if (!ObjectUtils.isNullOrEmpty(maindexclasses)) {
			builder.addMainDexClasses(maindexclasses);
		}
	}

	public static String getDescriptorFromClassFileRelativePath(SakerPath cfpath) {
		StringBuilder sb = new StringBuilder();
		sb.append('L');
		String pathstr = cfpath.toString();
		//remove .class extension
		sb.append(pathstr.substring(0, pathstr.length() - 6));
		sb.append(';');
		return sb.toString();
	}

}
