package saker.android.main.aapt2;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskFactory;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskIdentifier;
import saker.android.impl.aapt2.link.AAPT2LinkerFlag;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.aapt2.option.AAPT2LinkerInputTaskOption;
import saker.android.main.aapt2.option.AAPT2OutputFormatTaskOption;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

public class AAPT2LinkTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.link";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public Collection<AAPT2LinkerInputTaskOption> input;
			@SakerInput(value = { "Overlay" })
			public Collection<AAPT2LinkerInputTaskOption> overlayOption;

			@SakerInput(value = "Manifest", required = true)
			public FileLocationTaskOption manifestOption;

			@SakerInput(value = { "PackageId" })
			public Integer packageIdOption;

			@SakerInput(value = { "GenerateProguardRules" })
			public boolean generateProguardRulesOption;
			@SakerInput(value = { "GenerateMainDexProguardRules" })
			public boolean generateMainDexProguardRulesOption;

			@SakerInput(value = { "Configurations" })
			public Collection<String> configurationsOption;

			@SakerInput(value = { "PreferredDensity" })
			public String preferredDensityOption;

			@SakerInput(value = { "ProductNames" })
			public Collection<String> productNamesOption;

			@SakerInput(value = { "MinSDKVersion" })
			public Integer minSDKVersionOption;
			@SakerInput(value = { "TargetSDKVersion" })
			public Integer targetSDKVersionOption;

			@SakerInput(value = { "VersionCode" })
			public Integer versionCodeOption;
			@SakerInput(value = { "VersionCodeMajor" })
			public Integer versionCodeMajorOption;
			@SakerInput(value = { "VersionName" })
			public String versionNameOption;

			@SakerInput(value = { "CompileSDKVersionCode" })
			public String compileSDKVersionCodeOption;
			@SakerInput(value = { "CompileSDKVersionName" })
			public String compileSDKVersionNameOption;

			@SakerInput(value = { "CreateIDMappings" })
			public boolean createIDMappingsOption;
			@SakerInput(value = { "StableIDsFile" })
			public SakerPath stableIDsFileOption;

			@SakerInput(value = { "PrivateSymbols" })
			public String privateSymbolsOption;
			@SakerInput(value = { "CustomPackage" })
			public String customPackageOption;

			@SakerInput(value = { "ExtraPackages" })
			public Collection<String> extraPackagesOption;
			@SakerInput(value = { "JavadocAnnotations" })
			public Collection<String> javadocAnnotationsOption;

			@SakerInput(value = { "CreateTextSymbols" })
			public boolean createTextSymbolsOption;

			@SakerInput(value = { "RenameManifestPackage" })
			public String renameManifestPackageOption;
			@SakerInput(value = { "renameInstrumentationTargetPackage" })
			public String renameInstrumentationTargetPackageOption;

			@SakerInput(value = { "NonCompressedExtensions" })
			public Collection<String> nonCompressedExtensionsOption;

			@SakerInput(value = { "NoCompressRegex" })
			public String noCompressRegexOption;

			@SakerInput(value = { "ExcludeConfigurations" })
			public Collection<String> excludeConfigurationsOption;

			@SakerInput(value = { "Splits" })
			public Map<String, Collection<String>> splitsOption;

			@SakerInput("AllowReservedPackageId")
			public boolean allowReservedPackageIdOption;
			@SakerInput("ProguardConditionalKeepRules")
			public boolean proguardConditionalKeepRulesOption;
			@SakerInput("ProguardMinimalKeepRules")
			public boolean proguardMinimalKeepRulesOption;
			@SakerInput("NoAutoVersion")
			public boolean noAutoVersionOption;
			@SakerInput("NoVersionVectors")
			public boolean noVersionVectorsOption;
			@SakerInput("NoVersionTransitions")
			public boolean noVersionTransitionsOption;
			@SakerInput("NoResourceDeduping")
			public boolean noResourceDedupingOption;
			@SakerInput("NoResourceRemoval")
			public boolean noResourceRemovalOption;
			@SakerInput("EnableSparseEncoding")
			public boolean enableSparseEncodingOption;
			@SakerInput("RequireSuggestedLocalization")
			public boolean requireSuggestedLocalizationOption;
			@SakerInput("NoXmlNamespaces")
			public boolean noXmlNamespacesOption;
			@SakerInput("ReplaceVersion")
			public boolean replaceVersionOption;
			@SakerInput("NoStaticLibPackages")
			public boolean noStaticLibPackagesOption;
			@SakerInput("NonFinalIds")
			public boolean nonFinalIdsOption;
			@SakerInput("AutoAddOverlay")
			public boolean autoAddOverlayOption;
			@SakerInput("NoCompress")
			public boolean noCompressOption;
			@SakerInput("KeepRawValues")
			public boolean keepRawValuesOption;
			@SakerInput("WarnManifestValidation")
			public boolean warnManifestValidationOption;
			@SakerInput("DebugMode")
			public boolean debugModeOption;
			@SakerInput("StrictVisibility")
			public boolean strictVisibilityOption;

			@SakerInput("OutputFormat")
			public AAPT2OutputFormatTaskOption outputFormat;

			@SakerInput("Verbose")
			public boolean verboseOption;

			@SakerInput("Identifier")
			public CompilationIdentifierTaskOption identifierOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				SakerPath workingdirpath = taskcontext.getTaskWorkingDirectoryPath();

				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifierOption);

				LinkedHashSet<AAPT2LinkerInput> inputset = new LinkedHashSet<>();
				CompilationIdentifier inferredid = null;
				for (AAPT2LinkerInputTaskOption inoption : input) {
					if (inoption == null) {
						continue;
					}
					inputset.addAll(inoption.toLinkerInput(taskcontext));
					if (compilationid == null) {
						inferredid = CompilationIdentifier.concat(inferredid, inoption.inferCompilationIdentifier());
					}
				}

				if (compilationid == null) {
					compilationid = inferredid;
					if (compilationid == null) {
						compilationid = CompilationIdentifier.valueOf("default");
					}
				}
				Set<AAPT2LinkerInput> overlayset = new LinkedHashSet<>();
				if (!ObjectUtils.isNullOrEmpty(overlayOption)) {
					for (AAPT2LinkerInputTaskOption inoption : overlayOption) {
						if (inoption == null) {
							continue;
						}
						overlayset.addAll(inoption.toLinkerInput(taskcontext));
					}
				}

				AAPT2LinkWorkerTaskIdentifier workertaskid = new AAPT2LinkWorkerTaskIdentifier(compilationid);
				AAPT2LinkWorkerTaskFactory workertask = new AAPT2LinkWorkerTaskFactory();
				workertask.setInput(inputset);
				workertask.setOverlay(overlayset);
				workertask.setManifest(TaskOptionUtils.toFileLocation(manifestOption, taskcontext));
				workertask.setPackageId(packageIdOption);
				workertask.setGenerateProguardRules(generateProguardRulesOption);
				workertask.setGenerateMainDexProguardRules(generateMainDexProguardRulesOption);
				workertask.setConfigurations(ImmutableUtils.makeImmutableList(configurationsOption));
				workertask.setPreferredDensity(preferredDensityOption);
				workertask.setProductNames(ImmutableUtils.makeImmutableList(productNamesOption));
				workertask.setMinSdkVersion(minSDKVersionOption);
				workertask.setTargetSdkVersion(targetSDKVersionOption);
				workertask.setVersionCode(versionCodeOption);
				workertask.setVersionCodeMajor(versionCodeMajorOption);
				workertask.setVersionName(versionNameOption);
				workertask.setCompileSdkVersionCode(compileSDKVersionCodeOption);
				workertask.setCompileSdkVersionName(compileSDKVersionNameOption);
				workertask.setEmitIds(createIDMappingsOption);
				if (stableIDsFileOption != null) {
					workertask.setStableIdsFilePath(workingdirpath.resolve(stableIDsFileOption));
				}
				workertask.setPrivateSymbols(privateSymbolsOption);
				workertask.setCustomPackage(customPackageOption);
				workertask.setExtraPackages(ImmutableUtils.makeImmutableNavigableSet(extraPackagesOption));
				workertask.setAddJavadocAnnotation(ImmutableUtils.makeImmutableList(javadocAnnotationsOption));
				workertask.setOutputTextSymbols(createTextSymbolsOption);
				workertask.setRenameManifestPackage(renameManifestPackageOption);
				workertask.setRenameInstrumentationTargetPackage(renameInstrumentationTargetPackageOption);
				workertask.setNoncompressedExtensions(
						ImmutableUtils.makeImmutableNavigableSet(nonCompressedExtensionsOption));
				workertask.setNoCompressRegex(noCompressRegexOption);
				workertask.setExcludeConfigs(ImmutableUtils.makeImmutableNavigableSet(excludeConfigurationsOption));
				if (!ObjectUtils.isNullOrEmpty(splitsOption)) {
					NavigableMap<String, NavigableSet<String>> splits = new TreeMap<>();
					for (Entry<String, Collection<String>> entry : splitsOption.entrySet()) {
						splits.put(entry.getKey(), ImmutableUtils.makeImmutableNavigableSet(entry.getValue()));
					}
					workertask.setSplits(splits);
				}

				Set<AAPT2LinkerFlag> flags = EnumSet.noneOf(AAPT2LinkerFlag.class);
				addFlagIfSet(flags, AAPT2LinkerFlag.ALLOW_RESERVED_PACKAGE_ID, allowReservedPackageIdOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.PROGUARD_CONDITIONAL_KEEP_RULES,
						proguardConditionalKeepRulesOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.PROGUARD_MINIMAL_KEEP_RULES, proguardMinimalKeepRulesOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_AUTO_VERSION, noAutoVersionOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_VERSION_VECTORS, noVersionVectorsOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_VERSION_TRANSITIONS, noVersionTransitionsOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_RESOURCE_DEDUPING, noResourceDedupingOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_RESOURCE_REMOVAL, noResourceRemovalOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.ENABLE_SPARSE_ENCODING, enableSparseEncodingOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.REQUIRE_SUGGESTED_LOCALIZATION, requireSuggestedLocalizationOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_XML_NAMESPACES, noXmlNamespacesOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.REPLACE_VERSION, replaceVersionOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_STATIC_LIB_PACKAGES, noStaticLibPackagesOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NON_FINAL_IDS, nonFinalIdsOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.AUTO_ADD_OVERLAY, autoAddOverlayOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.NO_COMPRESS, noCompressOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.KEEP_RAW_VALUES, keepRawValuesOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.WARN_MANIFEST_VALIDATION, warnManifestValidationOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.DEBUG_MODE, debugModeOption);
				addFlagIfSet(flags, AAPT2LinkerFlag.STRICT_VISIBILITY, strictVisibilityOption);
				if (outputFormat != null) {
					flags.add(outputFormat.getFlag());
				}
				workertask.setFlags(flags);

				workertask.setVerbose(verboseOption);

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
	}

	private static <T> void addFlagIfSet(Set<? super T> flags, T en, boolean flag) {
		if (flag) {
			flags.add(en);
		}
	}

}
