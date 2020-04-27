package saker.android.main;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.apk.create.ApkCreateTaskFactory;
import saker.android.main.sdk.AndroidBuildToolsSDKTaskFactory;
import saker.android.main.sdk.AndroidPlatformSDKTaskFactory;
import saker.build.file.path.SakerPath;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.std.main.TaskDocs.DocFileLocation;

public class TaskDocs {
	//TODO fix the capitalization of classes, fields, methods

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

	private static final String AAPT2_OUTPUTPATHS = "Collection of paths to the results of the resource compilation.\n"
			+ "Each path is the output of a resource compilation operation.";

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

	@NestTypeInformation(qualifiedName = "AidlTaskOutput")
	@NestInformation("Output of the AIDL compilation task.")
	@NestFieldInformation(value = "JavaSourceDirectory",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("Path to the Java source directory that contains the generated .java source files based on the input .aidl sources.\n"
					+ "This directory can used as an input to Java compilation."))
	public static class DocAidlTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "D8TaskOutput")
	@NestInformation("Output of the d8 dexing operation.\n" + "It can be passed to the "
			+ ApkCreateTaskFactory.TASK_NAME + "() task to create an APK that contains the dexed classes.")
	@NestFieldInformation(value = "DexFiles",
			type = @NestTypeUsage(value = Set.class, elementTypes = { SakerPath.class }),
			info = @NestInformation("Set of output paths that are the result of the dexing operation.\n"
					+ "This set usually contains a path to one .dex file that is the result. However, in case of using multi-dex, "
					+ "multiple output dex files may be present."))
	public static class DocD8TaskOutput {
	}

	@NestTypeInformation(qualifiedName = "ClassBinaryName")
	@NestInformation("Binary name of a Java class.\n"
			+ "Inner classes are separated using the $ sign from the enclosing class.\n"
			+ "E.g. The binary name for com.example.Foo.Inner is com.example.Foo$Inner")
	public static class DocClassBinaryName {
	}

	@NestTypeInformation(qualifiedName = "AndroidClassPathInputTaskOption")
	@NestInformation("Input for the Android classpath creation task.\n"
			+ "May be paths to .aar, .jar files or class directories.\n" + "May be resolved Maven artifacts.\n"
			+ "May be result of aapt2 compilation that causes the referenced .aar files to be part of the classpath.")
	public static class DocAndroidClassPathInputTaskOption {
	}

	@NestTypeInformation(qualifiedName = "AndroidClassPathReference")
	@NestInformation("Classpath configuration for Android.")
	public static class DocAndroidClassPathReference {
	}

	@NestInformation("Represents the task output of an APK creation.\n"
			+ "Provides access to the output Path of the created APK.")
	@NestFieldInformation(value = "Path",
			type = @NestTypeUsage(kind = TypeInformationKind.FILE_PATH, value = SakerPath.class),
			info = @NestInformation("The path to the created APK."))
	@NestTypeInformation(qualifiedName = "ApkCreatorTaskOutput")
	public static class DocApkCreatorTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AssetsDirectoryPath")
	@NestInformation("Path to an Android assets directory.")
	public static class DocAssetsDirectory {
	}

	@NestTypeInformation(qualifiedName = "AarExtractTaskOutput")
	@NestInformation("Output of an AAR entry extraction operation.")
	@NestFieldInformation(value = "FileLocation",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("The output file location of the extracted entry.\n"
					+ "May be a local or execution file location based on the configuration."))
	@NestFieldInformation(value = "DirectoryFileLocations",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { DocFileLocation.class }),
			info = @NestInformation("Set of file locations that were extracted as the result of a directory extraction.\n"
					+ "If the specified entry is a directory, this field contains the extracted file locations."))
	public static class DocAarExtractTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AAPT2CompileTaskOutput")
	@NestFieldInformation(value = "AarCompilations",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAAPT2AarCompileTaskOutput.class }),
			info = @NestInformation("Collection of AAR resource compilation results.\n"
					+ "If any AAR inputs were specified as the compilation input, they are available via this field.\n"
					+ "Each element is a result of an AAR compilation task."))
	@NestFieldInformation(value = "OutputPaths",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { SakerPath.class }),
			info = @NestInformation(AAPT2_OUTPUTPATHS))
	public static class DocAAPT2CompileTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AAPT2AarCompileTaskOutput")
	@NestFieldInformation(value = "AarFile",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("The file location of the input AAR file."))
	@NestFieldInformation(value = "RTxtFile",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("File location of the R.txt file that was extracted from the input AAR bundle."))
	@NestFieldInformation(value = "AndroidManifestXmlFile",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("File location of the AndroidManifest.xml file that was extracted from the input AAR bundle."))
	@NestFieldInformation(value = "OutputPaths",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { SakerPath.class }),
			info = @NestInformation(AAPT2_OUTPUTPATHS))
	public static class DocAAPT2AarCompileTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AAPT2LinkTaskOutput")
	@NestInformation("Output of an aapt2 linking operation.")
	@NestFieldInformation(value = "APKPath",
			info = @NestInformation("Path to the output APK that the aapt2 linking operation produced.\n"
					+ "The APK contains the compiled resources and manifest file. It doesn't contain the Java "
					+ "classes or other files requires for the Android app."))
	@NestFieldInformation(value = "JavaSourceDirectories",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { SakerPath.class }),
			info = @NestInformation("Paths to the Java source directories where the R.java files were generated.\n"
					+ "You should pass this as an input to the Java compilation for your Android app."))
	@NestFieldInformation(value = "ProguardPath",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("The path to the generated ProGuard rules file.\n"
					+ "This file is only generated if the GenerateProguardRules parameter was set to true."))
	@NestFieldInformation(value = "ProguardMainDexPath",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("The path to the generated ProGuard rules file for the main dex.\n"
					+ "This file is only generated if the GenerateMainDexProguardRules parameter was set to true."))
	@NestFieldInformation(value = "IDMappingsPath",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("The path to the generated ID mappings file.\n"
					+ "This file is only generated if the CreateIDMappings parameter was set to true."))
	@NestFieldInformation(value = "TextSymbolsPath",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("The path to the generated text symbols file.\n"
					+ "This file is always generated using the --output-text-symbols aapt2 option."))
	@NestFieldInformation(value = "SplitPaths",
			type = @NestTypeUsage(value = Map.class, elementTypes = { String.class, SakerPath.class }),
			info = @NestInformation("Map of the generated split APK names and their output paths.\n"
					+ "The field contains the split names specified in the Splits parameter mapped to their "
					+ "respective output paths."))
	@NestFieldInformation(value = "InputLibraries",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAAPT2LinkInputLibrary.class }),
			info = @NestInformation("A list of input AAR libraries that were used for the linking."))
	public static class DocAAPT2LinkTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "AAPT2LinkInputLibrary")
	@NestInformation("Represents an input library for aapt2 linking operation.")
	@NestFieldInformation(value = "AarFile",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("The file location of the input AAR library."))
	public static class DocAAPT2LinkInputLibrary {
	}

}
