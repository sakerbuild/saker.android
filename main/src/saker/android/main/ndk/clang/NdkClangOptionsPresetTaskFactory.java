package saker.android.main.ndk.clang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.android.impl.AndroidUtils;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;

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

			@SakerInput(value = { "Libraries" })
			public Collection<String> librariesOption;

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
							throw new IllegalArgumentException("Unrecognized ABI: " + abiOption);
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
					for (String lib : librariesOption) {
						if (ObjectUtils.isNullOrEmpty(lib)) {
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
