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
import saker.sdk.support.main.TaskDocs.DocSDKDescription;
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

	@NestTypeInformation(qualifiedName = "Aapt2CompileTaskOutput")
	@NestInformation("Output of aapt2 compilation.")
	@NestFieldInformation(value = "AarCompilations",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAapt2AarCompileTaskOutput.class }),
			info = @NestInformation("Collection of AAR resource compilation results.\n"
					+ "If any AAR inputs were specified as the compilation input, they are available via this field.\n"
					+ "Each element is a result of an AAR compilation task."))
	@NestFieldInformation(value = "OutputPaths",
			type = @NestTypeUsage(value = Collection.class, elementTypes = { SakerPath.class }),
			info = @NestInformation(AAPT2_OUTPUTPATHS))
	public static class DocAapt2CompileTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "Aapt2AarCompileTaskOutput")
	@NestInformation("Output of aapt2 compilation of an AAR bundle.")
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
	public static class DocAapt2AarCompileTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "Aapt2LinkTaskOutput")
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
			type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAapt2LinkInputLibrary.class }),
			info = @NestInformation("A list of input AAR libraries that were used for the linking."))
	public static class DocAapt2LinkTaskOutput {
	}

	@NestTypeInformation(qualifiedName = "Aapt2LinkInputLibrary")
	@NestInformation("Represents an input library for aapt2 linking operation.")
	@NestFieldInformation(value = "AarFile",
			type = @NestTypeUsage(DocFileLocation.class),
			info = @NestInformation("The file location of the input AAR library."))
	public static class DocAapt2LinkInputLibrary {
	}

	@NestTypeInformation(qualifiedName = "AndroidNdkLibrary",
			enumValues = {

					@NestFieldInformation(value = "android",
							info = @NestInformation("The Android native runtime library.")),
					@NestFieldInformation(value = "log", info = @NestInformation("Logging library for Android.")),
					@NestFieldInformation(value = "z", info = @NestInformation("The Zlib compression library. ")),
					@NestFieldInformation(value = "GLESv1_CM",
							info = @NestInformation("Library for using OpenGL ES 1.x. All Android-based devices support OpenGL ES 1.0 and 2.0.")),
					@NestFieldInformation(value = "GLESv2",
							info = @NestInformation("Library for using OpenGL ES 2.0. All Android-based devices support OpenGL ES 1.0 and 2.0.")),
					@NestFieldInformation(value = "GLESv3",
							info = @NestInformation("Library for using OpenGL ES 3.x.")),
					@NestFieldInformation(value = "EGL",
							info = @NestInformation("EGL provides a native platform interface via the <EGL/egl.h> and <EGL/eglext.h> headers for allocating and managing OpenGL ES contexts and surfaces.")),
					@NestFieldInformation(value = "vulkan",
							info = @NestInformation("Vulkan is a low-overhead, cross-platform API for high-performance 3D graphics rendering.")),
					@NestFieldInformation(value = "jnigraphics",
							info = @NestInformation("The libjnigraphics library exposes API that allows access to the pixel buffers of Java Bitmap objects.")),
					@NestFieldInformation(value = "sync", info = @NestInformation("Library for handling sync files")),
					@NestFieldInformation(value = "camera2ndk",
							info = @NestInformation("Native camera APIs to perform fine-grained photo capture and processing.")),
					@NestFieldInformation(value = "mediandk",
							info = @NestInformation("The Media APIs provide low-level native interfaces similar to MediaExtractor, MediaCodec and other related Java APIs.")),
					@NestFieldInformation(value = "OpenMAXAL",
							info = @NestInformation("Android native multimedia handling based on Khronos Group OpenMAX AL 1.0.1 API.")),
					@NestFieldInformation(value = "nativewindow",
							info = @NestInformation("Native Window functionality library.")),
					@NestFieldInformation(value = "aaudio",
							info = @NestInformation("AAudio is a high-performance native audio API for Android")),
					@NestFieldInformation(value = "OpenSLES",
							info = @NestInformation("OpenSL ES is a native audio API.")),
					@NestFieldInformation(value = "neuralnetworks",
							info = @NestInformation("The Neural Networks API (NNAPI) provides apps with hardware acceleration for on-device machine learning operations.")),
					@NestFieldInformation(value = "dl",
							info = @NestInformation("Library for dynamically loading libraries.")),

			})
	@NestInformation("Identifier of an Android NDK library.")
	public static class DocAndroidNdkLibrary {
	}

	@NestTypeInformation(qualifiedName = "AndroidAbi",
			enumValues = {

					@NestFieldInformation(value = "armeabi-v7a",
							info = @NestInformation("ABI for 32-bit ARM-based CPUs.")),
					@NestFieldInformation(value = "arm64-v8a",
							info = @NestInformation("ABI for ARMv8-A based CPUs, which support the 64-bit AArch64 architecture.")),
					@NestFieldInformation(value = "x86",
							info = @NestInformation("ABI for CPUs supporting the instruction set commonly known as \"x86\", \"i386\", or \"IA-32\".")),
					@NestFieldInformation(value = "x86_64",
							info = @NestInformation("ABI for CPUs supporting the instruction set commonly referred to as \"x86-64.\"")),

			})
	@NestInformation("Identifier of an Android ABI (Application Binary Interface).")
	public static class DocAndroidAbi {
	}

	@NestTypeInformation(qualifiedName = "AndroidNdkRuntimeFeatures",
			enumValues = {

					@NestFieldInformation(value = "exceptions",
							info = @NestInformation("Feature to enable using C++ exceptions.")),
					@NestFieldInformation(value = "rtti",
							info = @NestInformation("Feature to enable using RTTI (Run-time type information).")),

			})
	@NestInformation("An Android runtime feature option.")
	public static class DocAndroidNdkRuntimeFeatures {
	}

	@NestTypeInformation(qualifiedName = "SymbolVisibility",
			enumValues = {
					//from https://www.cita.utoronto.ca/~merz/intel_f10b/main_for/mergedProjects/copts_for/common_options/option_fvisibility.htm
					@NestFieldInformation(value = "hidden",
							info = @NestInformation("Hidden visiblity. Other components cannot directly reference the symbol. "
									+ "However, its address may be passed to other components indirectly.")),
					@NestFieldInformation(value = "default",
							info = @NestInformation("Default visibility. Other components can reference the symbol, "
									+ "and the symbol definition can be overridden (preempted) by a definition of the same name in another component.")),
					@NestFieldInformation(value = "protected",
							info = @NestInformation("Protected visibility. Other components can reference the symbol, "
									+ "but it cannot be overridden by a definition of the same name in another component.")),
					@NestFieldInformation(value = "internal",
							info = @NestInformation("Protected visibility. The symbol cannot be referenced "
									+ "outside its defining component, either directly or indirectly.")),
					@NestFieldInformation(value = "extern",
							info = @NestInformation("Protected visibility. The symbol is treated as though it is defined in another component. "
									+ "It also means that the symbol can be overridden by a definition of the same name in another component.")),

			})
	@NestInformation("Symbol visibility value.")
	public static class DocSymbolVisibility {
	}

	@NestInformation("SDK description for the Android NDK.")
	@NestTypeInformation(qualifiedName = "AndroidNdkSDKDescription")
	@NestFieldInformation(value = "ClangSDK",
			type = @NestTypeUsage(DocSDKDescription.class),
			info = @NestInformation("Gets a Clang SDK that uses clang in the located Android NDK."))
	@NestFieldInformation(value = "ClangXXSDK",
			type = @NestTypeUsage(DocSDKDescription.class),
			info = @NestInformation("Gets a Clang SDK that uses clang++ in the located Android NDK."))
	public static class DocAndroidNdkSDKDescription {
	}

	@NestTypeInformation(qualifiedName = "AndroidNdkClangOptionsPreset")
	@NestInformation("Configuration preset for using clang with Android NDK.")
	public static class DocNdkClangOptionsPreset {
	}

	@NestTypeInformation(qualifiedName = "StripWorkerTaskOutput")
	@NestInformation("Worker task output of the strip tool invocation from the Android NDK.")
	@NestFieldInformation(value = "Path",
			type = @NestTypeUsage(SakerPath.class),
			info = @NestInformation("The output path of the stripped binary."))
	public static class DocStripWorkerTaskOutput {
	}
}
