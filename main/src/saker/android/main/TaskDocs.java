package saker.android.main;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.sdk.AndroidBuildToolsSDKTaskFactory;
import saker.android.main.sdk.AndroidPlatformSDKTaskFactory;
import saker.build.file.path.SakerPath;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;

public class TaskDocs {
	public static final String SDKS = "Specifies the SDKs (Software Development Kits) used by the task.\n"
			+ "SDKs represent development kits that are available in the build environment and to the task. They are used "
			+ "to find the necessary tools to perform the necessary operations.\n" + "You should specify the "
			+ AndroidBuildToolsSDKReference.SDK_NAME + " and " + AndroidPlatformSDKReference.SDK_NAME
			+ " SDKs so the build tasks can find the relevant tools to perform their operations.\n"
			+ "If you don't specify these SDKs, then the build tasks will attempt to locate them automatically. If they "
			+ "fail to do so, an exception is thrown.\n" + "You can use the "
			+ AndroidBuildToolsSDKTaskFactory.TASK_NAME + "() and " + AndroidPlatformSDKTaskFactory.TASK_NAME
			+ "() tasks to retrieve SDKs with specific versions.\n"
			+ "The SDK names are compared in a case-insensitive way.";

	@NestInformation("Output of ZIP alignment.")
	@NestFieldInformation(value = "Path",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("Output path of the aligned ZIP archive."))
	@NestTypeInformation(qualifiedName = "ZipAlignTaskOutput")
	public static class DocZipAlignTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AndroidPlatformClassPath")
	@NestInformation("Classpath reference to an Android platform.")
	public static class DocAndroidPlatformClassPath {
	}

	@NestTypeInformation(qualifiedName = "SignApkTaskOutput")
	@NestInformation("Output of the APK signer task.")
	@NestFieldInformation(value = "Path", info = @NestInformation("Output path of the signed APK."))
	public static class DocSignApkTaskOutput {
	}
}
