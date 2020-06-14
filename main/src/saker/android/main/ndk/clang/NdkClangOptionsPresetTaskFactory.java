package saker.android.main.ndk.clang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.android.impl.AndroidUtils;
import saker.android.main.TaskDocs.DocAndroidAbi;
import saker.android.main.TaskDocs.DocAndroidNdkLibrary;
import saker.android.main.TaskDocs.DocAndroidNdkRuntimeFeatures;
import saker.android.main.TaskDocs.DocNdkClangOptionsPreset;
import saker.android.main.TaskDocs.DocSymbolVisibility;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocNdkClangOptionsPreset.class))
@NestInformation("Creates a clang configuration preset for compiling and linking Android native binaries.\n"
		+ "The task can be used to create a configuration object that can be passed to saker.clang.compile() and "
		+ "saker.clang.link() build tasks for their CompilerOptions and LinkerOptions parameters.\n"
		+ "The created configuration includes the default arguments for the compilers and sets up the "
		+ "include and library paths, and sysroots for the compilation.")

@NestParameterInformation(value = "ABI",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAndroidAbi.class }),
		info = @NestInformation("Specifies the Application Binary Interface for which the compilation should be done.\n"
				+ "Each combination of CPU and instruction set has its own Application Binary Interface (ABI). See "
				+ "https://developer.android.com/ndk/guides/abis for more information."))
@NestParameterInformation(value = "API",
		type = @NestTypeUsage(int.class),
		info = @NestInformation("Specifies the target API level for which the compilation should be done.\n"
				+ "The target API level is used to set up the proper roots for the compilation. It determines "
				+ "which APIs are available for your application.\n"
				+ "Generally it should be the latest Android release version, however, you should still make sure "
				+ "not to call any APIs that aren't available during runtime."))
@NestParameterInformation(value = "Release",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Specifies the optimization options that should be added when compiling and linking.\n"
				+ "This value is false by default. You should set it to true when producing the release APK for your application.\n"
				+ "Setting this to null will result in no optimization related configuration to be included."))
@NestParameterInformation(value = "StaticStdLib",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Specifies if the standard library should be linked statically to the application.\n"
				+ "Specifying true will cause the -nostdlib++, -lc++_static and -lc++abi arguments to be "
				+ "added to clang for linking.\n" + "This is false by default."))
@NestParameterInformation(value = "RuntimeFeatures",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAndroidNdkRuntimeFeatures.class }),
		info = @NestInformation("Specifies which runtime features can be used in the application.\n"
				+ "The parameter accepts \"exceptions\" and \"rtti\" values to enable the usage of exceptions and run-time type "
				+ "information when compiling C++ sources.\n" + "None of these features are enabled by default."))
@NestParameterInformation(value = "Visibility",
		type = @NestTypeUsage(DocSymbolVisibility.class),
		info = @NestInformation("Specifies the default visibility of symbols in your application.\n"
				+ "The argument corresponds to the -fvisibility=<VALUE> clang parameter. Can be set to null to don't use it."
				+ "This is \"hidden\" by default."))
@NestParameterInformation(value = "Libraries",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAndroidNdkLibrary.class }),
		info = @NestInformation("Specifies the libraries that should be linked.\n"
				+ "Each specified library will be added as a -l<LIB> argument to the clang linker.\n"
				+ "If this parameter is not specified, it will only contain \"android\". However, if you "
				+ "specify it, you need to add \"android\" in addition to the other libraries you want.\n"
				+ "See https://developer.android.com/ndk/guides/stable_apis for the available libraries."))
@NestParameterInformation(value = "Identifier",
		type = @NestTypeUsage(CompilationIdentifierTaskOption.class),
		info = @NestInformation("Compilation identifier that specifies for which compilations this preset can be applied to."))
public class NdkClangOptionsPresetTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.ndk.clang.preset";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "ABI" })
			public String abiOption;

			@SakerInput(value = { "API" })
			public Integer targetAPIOption;

			@SakerInput(value = "Release")
			public Boolean releaseOption = Boolean.FALSE;

			@SakerInput(value = "StaticStdLib")
			public Boolean staticStdLibOption = Boolean.FALSE;

			@SakerInput(value = { "RuntimeFeatures" })
			public Collection<String> runtimeFeaturesOption;

			@SakerInput(value = "Visibility")
			public String visibilityOption = "hidden";

			//link the android library by default
			@SakerInput(value = { "Libraries" })
			public Collection<String> librariesOption = ImmutableUtils.asUnmodifiableArrayList("android");

			@SakerInput(value = { "Identifier" })
			public CompilationIdentifierTaskOption identifierOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_CONFIGURATION);
				}

				if (targetAPIOption != null) {
					if (targetAPIOption < 1) {
						throw new IllegalArgumentException("Invalid TargetAPI: " + targetAPIOption);
					}
				}

				CompilationIdentifier identifier = CompilationIdentifierTaskOption.getIdentifier(identifierOption);

				List<String> linkerparams = new ArrayList<>();
				List<String> compilerparams = new ArrayList<>();
				String targetparam = null;
				String armeabimarch = null;
				boolean exceptions = false;
				boolean rtti = false;

				if (!ObjectUtils.isNullOrEmpty(abiOption)) {
					String targetapi = targetAPIOption == null ? "" : targetAPIOption.toString();
					int targetapiint;
					try {
						targetapiint = Integer.parseUnsignedInt(targetapi);
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException("Target API must be an integer: " + targetapi);
					}
					switch (abiOption) {
						case "x86": {
							targetparam = "--target=i686-none-linux-android" + targetapi;
							//based on build/core/build-binary.mk in the NDK
//							# http://b.android.com/222239
//							# http://b.android.com/220159 (internal http://b/31809417)
//							# x86 devices have stack alignment issues.
//							ifeq ($(TARGET_ARCH_ABI),x86)
//							    ifneq (,$(call lt,$(APP_PLATFORM_LEVEL),24))
//							        LOCAL_CFLAGS += -mstackrealign
//							    endif
//							endif
							if (targetapiint < 24) {
								compilerparams.add("-mstackrealign");
							}
							break;
						}
						case "x86_64": {
							targetparam = "--target=x86_64-none-linux-android" + targetapi;
							break;
						}
						case "arm64-v8a": {
							targetparam = "--target=aarch64-none-linux-android" + targetapi;
							break;
						}
						case "armeabi-v7a": {
							targetparam = "--target=armv7-none-linux-androideabi" + targetapi;
							armeabimarch = "-march=armv7-a";
							break;
						}
						default: {
							SakerLog.warning().taskScriptPosition(taskcontext)
									.println("Unrecognized ABI: " + abiOption);
							break;
						}
					}
				}

				if (!ObjectUtils.isNullOrEmpty(runtimeFeaturesOption)) {
					for (String opt : runtimeFeaturesOption) {
						if (opt == null) {
							continue;
						}
						switch (opt.toLowerCase(Locale.ENGLISH)) {
							case "exceptions": {
								exceptions = true;
								break;
							}
							case "rtti": {
								rtti = true;
								break;
							}
							default: {
								throw new IllegalArgumentException("Unrecognized runtime feature value: " + opt);
							}
						}
					}
				}

				NavigableMap<String, String> macros = new TreeMap<>();
				if (targetparam != null) {
					compilerparams.add(targetparam);
				}
				if (armeabimarch != null) {
					compilerparams.add(armeabimarch);
				}
				if (!exceptions) {
					compilerparams.add("-fno-exceptions");
				}
				if (!rtti) {
					compilerparams.add("-fno-rtti");
				}

				compilerparams.add("-fpic");
				compilerparams.add("-g");
				compilerparams.add("-fdata-sections");
				compilerparams.add("-ffunction-sections");
				compilerparams.add("-funwind-tables");
				compilerparams.add("-fstack-protector-strong");
				compilerparams.add("-no-canonical-prefixes");

				//flag for these?
				compilerparams.add("-Wformat");
				compilerparams.add("-Werror=format-security");
				compilerparams.add("-Wno-invalid-command-line-argument");
				compilerparams.add("-Wno-unused-command-line-argument");

				if (!ObjectUtils.isNullOrEmpty(visibilityOption)) {
					compilerparams.add("-fvisibility=" + visibilityOption);
				}
				if (releaseOption != null) {
					if (!releaseOption) {
						compilerparams.add("-fno-limit-debug-info");
						compilerparams.add("-O0");
					} else {
						compilerparams.add("-O2");
					}
				}

				if (targetparam != null) {
					linkerparams.add(targetparam);
				}
				linkerparams.add("-shared");
				linkerparams.add("-Wl,--no-undefined");
				linkerparams.add("-Wl,--fatal-warnings");
				if (staticStdLibOption != null) {
					if (staticStdLibOption) {
						linkerparams.add("-nostdlib++");
						linkerparams.add("-lc++_static");
						linkerparams.add("-lc++abi");
					}
				}
				if (!ObjectUtils.isNullOrEmpty(librariesOption)) {
					//deduplicate
					Set<String> addedlibs = new TreeSet<>();
					for (String lib : librariesOption) {
						if (ObjectUtils.isNullOrEmpty(lib)) {
							continue;
						}
						if (!addedlibs.add(lib)) {
							continue;
						}
						linkerparams.add("-l" + lib);
					}
				}

				//__ANDROID_API__ is defined by the toolchain
				macros.put("ANDROID", "");
				macros.put("_FORTIFY_SOURCE", "2");
				if (releaseOption != null) {
					if (releaseOption) {
						macros.put("NDEBUG", "");
					}
				}

				NavigableMap<String, SDKDescription> sdkmap = new TreeMap<>(SDKSupportUtils.getSDKNameComparator());
				sdkmap.put("Clang", AndroidUtils.DEFAULT_NDK_SDK.getClangXXSDK());

				NavigableMap<Object, Object> presetmap = new TreeMap<>();
				if (identifier != null) {
					presetmap.put("Identifier", identifier);
				}
				presetmap.put("SimpleCompilerParameters", ImmutableUtils.makeImmutableList(compilerparams));
				presetmap.put("SimpleLinkerParameters", ImmutableUtils.makeImmutableList(linkerparams));
				presetmap.put("MacroDefinitions", ImmutableUtils.makeImmutableNavigableMap(macros));
				presetmap.put("SDKs", ImmutableUtils.makeImmutableNavigableMap(sdkmap));
				NavigableMap<Object, Object> result = ImmutableUtils.makeImmutableNavigableMap(presetmap);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
