package saker.android.main.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.aar.AAPT2AarStaticLibraryWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskFactory;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskIdentifier;
import saker.android.impl.aapt2.link.AAPT2LinkerFlag;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.CompilationAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.FileAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.LinkAAPT2LinkerInput;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.classpath.LiteralStructuredTaskResult;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.aapt2.option.AAPT2LinkerInputTaskOption;
import saker.android.main.aapt2.option.AAPT2OutputFormatTaskOption;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationUtils;
import saker.maven.support.api.localize.ArtifactLocalizationWorkerTaskOutput;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

public class AAPT2LinkTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.link";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public Collection<AAPT2LinkerInputTaskOption> inputOption;
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
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}

				AAPT2CompilationConfiguration libcompileconfiguration = new AAPT2CompilationConfiguration();
				libcompileconfiguration.setFlags(Collections.emptySet());
				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				Set<AAPT2LinkerInput> inputset = new LinkedHashSet<>();
				Set<AAPT2LinkerInput> overlayset = new LinkedHashSet<>();
				Set<TaskIdentifier> inputstaticlibtasks = new HashSet<>();
				Set<TaskIdentifier> overlaystaticlibtasks = new HashSet<>();
				addLinkerInputOptions(taskcontext, inputset, inputstaticlibtasks, inputOption, libcompileconfiguration,
						sdkdescriptions);
				addLinkerInputOptions(taskcontext, overlayset, overlaystaticlibtasks, overlayOption,
						libcompileconfiguration, sdkdescriptions);

				addStaticLibraryInputOptions(taskcontext, inputset, inputstaticlibtasks);
				addStaticLibraryInputOptions(taskcontext, overlayset, overlaystaticlibtasks);

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

	private static void addLinkerInputOptions(TaskContext taskcontext, Set<AAPT2LinkerInput> inputset,
			Set<TaskIdentifier> inputstaticlibtasks, Collection<AAPT2LinkerInputTaskOption> inoptions,
			AAPT2CompilationConfiguration configuration, NavigableMap<String, SDKDescription> sdkdescriptions) {
		if (inoptions == null) {
			return;
		}
		//TODO remove aar input support
		for (AAPT2LinkerInputTaskOption inoption : inoptions) {
			if (inoption == null) {
				continue;
			}
			inoption.accept(new AAPT2LinkerInputTaskOption.Visitor() {
				@Override
				public void visit(MavenOperationConfiguration config,
						Collection<? extends ResolvedDependencyArtifact> input) {
					Set<ArtifactCoordinates> coordinates = new LinkedHashSet<>();
					for (ResolvedDependencyArtifact in : input) {
						ArtifactCoordinates coords = in.getCoordinates();
						if (!"aar".equals(coords.getExtension())) {
							continue;
						}
						coordinates.add(coords);
					}
					if (coordinates.isEmpty()) {
						SakerLog.info().verbose().taskScriptPosition(taskcontext)
								.println("No input artifacts found with aar extension.");
						return;
					}
					TaskFactory<? extends ArtifactLocalizationTaskOutput> taskfactory = ArtifactLocalizationUtils
							.createLocalizeArtifactsTaskFactory(config, coordinates);
					TaskIdentifier taskid = ArtifactLocalizationUtils.createLocalizeArtifactsTaskIdentifier(config,
							coordinates);
					//TODO keep insertion order for input artifacts in input set
					taskcontext.startTask(taskid, taskfactory, null);

					//TODO this user.dir/.m2/repository should depend on an environment property or something
					String repohash = StringUtils
							.toHexString(FileUtils.hashString(Objects.toString(config.getLocalRepositoryPath(),
									System.getProperty("user.dir") + "/.m2/repository")));

					for (ArtifactCoordinates coords : coordinates) {
						addAarInputOption(taskcontext,
								new ArtifactLocalizationOutputFileLocationStructuredTaskResult(taskid, coords),
								inputset, inputstaticlibtasks, SakerPath.valueOf(repohash).resolve(coords.getGroupId(),
										coords.getArtifactId(), coords.getVersion()),
								configuration, sdkdescriptions);
					}
				}

				@Override
				public void visit(AAPT2LinkTaskOutput linkinput) {
					inputset.add(new LinkAAPT2LinkerInput(linkinput));
				}

				@Override
				public void visit(AAPT2CompileTaskOutput compilationinput) {
					inputset.add(new CompilationAAPT2LinkerInput(compilationinput));
				}

				@Override
				public void visit(WildcardPath wildcard) {
					FileCollectionStrategy collectionstrategy = WildcardFileCollectionStrategy
							.create(taskcontext.getTaskWorkingDirectoryPath(), wildcard);
					NavigableMap<SakerPath, SakerFile> cpfiles = taskcontext.getTaskUtilities()
							.collectFilesReportAdditionDependency(null, collectionstrategy);
					taskcontext.getTaskUtilities().reportInputFileDependency(null, ObjectUtils
							.singleValueMap(cpfiles.navigableKeySet(), CommonTaskContentDescriptors.PRESENT));
					cpfiles.keySet().forEach(this::visit);
				}

				@Override
				public void visit(SakerPath path) {
					visit(ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath().tryResolve(path)));
				}

				@Override
				public void visit(FileCollection file) {
					file.forEach(this::visit);
				}

				@Override
				public void visit(FileLocation file) {
					if (FileUtils.hasExtensionIgnoreCase(SakerStandardUtils.getFileLocationFileName(file), "aar")) {
						addAarInputOption(taskcontext, new LiteralStructuredTaskResult(file), inputset,
								inputstaticlibtasks,
								SakerPath.valueOf(StringUtils.toHexString(FileUtils.hashString(
										AarEntryExtractWorkerTaskFactory.getFileLocationPath(file).toString()))),
								configuration, sdkdescriptions);
					} else {
						inputset.add(new FileAAPT2LinkerInput(file));
					}
				}
			});
		}
	}

	private static void addAarInputOption(TaskContext taskcontext, StructuredTaskResult filelocation,
			Set<AAPT2LinkerInput> inputset, Set<TaskIdentifier> inputstaticlibtasks, SakerPath taskidhash,
			AAPT2CompilationConfiguration configuration, NavigableMap<String, SDKDescription> sdkdescriptions) {
		//XXX better static lib worker task configuration. include verbosity as well

		AAPT2AarStaticLibraryWorkerTaskFactory workertask = new AAPT2AarStaticLibraryWorkerTaskFactory(filelocation,
				configuration);
		workertask.setSDKDescriptions(sdkdescriptions);

		taskcontext.startTask(workertask, workertask, null);
		inputstaticlibtasks.add(workertask);
	}

	private static void addStaticLibraryInputOptions(TaskContext taskcontext, Set<AAPT2LinkerInput> inputset,
			Set<TaskIdentifier> inputstaticlibtasks) {
		for (TaskIdentifier staticlibtask : inputstaticlibtasks) {
			TaskResultDependencyHandle dephandle = taskcontext.getTaskResultDependencyHandle(staticlibtask);
			//we don't care about changes in the static library task result

			AAPT2LinkTaskOutput linktaskoutput = (AAPT2LinkTaskOutput) dephandle.get();
			if (linktaskoutput == null) {
				dephandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(null));
				//the aar doesn't contain any resources or otherwise static library related contents.
				//don't pass as linker input
				continue;
			} else {
				dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.NEVER);
			}

			inputset.add(new LinkAAPT2LinkerInput(linktaskoutput));
		}
	}

	public static class ArtifactLocalizationOutputFileLocationStructuredTaskResult
			implements StructuredTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier localizationTaskId;
		private ArtifactCoordinates coordinates;

		/**
		 * For {@link Externalizable}.
		 */
		public ArtifactLocalizationOutputFileLocationStructuredTaskResult() {
		}

		public ArtifactLocalizationOutputFileLocationStructuredTaskResult(TaskIdentifier localizationTaskId,
				ArtifactCoordinates coordinates) {
			this.localizationTaskId = localizationTaskId;
			this.coordinates = coordinates;
		}

		@Override
		public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
			ArtifactLocalizationTaskOutput localizationresult = (ArtifactLocalizationTaskOutput) results
					.getTaskResult(localizationTaskId);
			StructuredTaskResult coordtaskresults = localizationresult.getLocalizationResult(coordinates);
			if (coordtaskresults == null) {
				throw new RuntimeException("Artifact localization result not found for: " + coordinates);
			}
			ArtifactLocalizationWorkerTaskOutput result = (ArtifactLocalizationWorkerTaskOutput) coordtaskresults
					.toResult(results);
			//TODO should report file location equality depedendency
			return LocalFileLocation.create(result.getLocalPath());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(localizationTaskId);
			out.writeObject(coordinates);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			localizationTaskId = SerialUtils.readExternalObject(in);
			coordinates = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((coordinates == null) ? 0 : coordinates.hashCode());
			result = prime * result + ((localizationTaskId == null) ? 0 : localizationTaskId.hashCode());
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
			ArtifactLocalizationOutputFileLocationStructuredTaskResult other = (ArtifactLocalizationOutputFileLocationStructuredTaskResult) obj;
			if (coordinates == null) {
				if (other.coordinates != null)
					return false;
			} else if (!coordinates.equals(other.coordinates))
				return false;
			if (localizationTaskId == null) {
				if (other.localizationTaskId != null)
					return false;
			} else if (!localizationTaskId.equals(other.localizationTaskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + coordinates + "]";
		}

	}
}
