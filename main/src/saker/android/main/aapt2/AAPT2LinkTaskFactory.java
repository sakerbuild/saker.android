package saker.android.main.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileFrontendTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskFactory;
import saker.android.impl.aapt2.link.AAPT2LinkWorkerTaskIdentifier;
import saker.android.impl.aapt2.link.AAPT2LinkerFlag;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.AarCompilationAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.CompilationAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.FileAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.LinkAAPT2LinkerInput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocAAPT2LinkTaskOutput;
import saker.android.main.aapt2.option.AAPT2LinkerInputTaskOption;
import saker.android.main.aapt2.option.AAPT2OutputFormatTaskOption;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationWorkerTaskOutput;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocAAPT2LinkTaskOutput.class))
@NestInformation("Links compiled Android resources using the aapt2 tool.\n"
		+ "The task can be used to link previously compiled resources together for creating a base APK. "
		+ "The output APK will contain the input resources and AndroidManifest.xml in compiled format.\n"
		+ "The link operation produces the R Java class which contains the identifiers for each resource "
		+ "in your application. You can use these identifiers to reference from your code. Make sure to add the "
		+ "JavaSourceDirectories to your Java compilation task as input.\n"
		+ "You can add AAR dependencies to the compilation task with the Overlay parameter. In that case make sure "
		+ "to set the AutoAddOverlay to true.")
@NestParameterInformation(value = "Input",
		aliases = { "" },
		required = true,
		type = @NestTypeUsage(value = Collection.class, elementTypes = { AAPT2LinkerInputTaskOption.class }),
		info = @NestInformation("The inputs for the linking operation.\n"
				+ "The parameter accepts one or more elemets which may be outputs from the "
				+ AAPT2CompileTaskFactory.TASK_NAME
				+ "() task, as well as direct file paths to compiled resource files.\n"
				+ "Generally, you will be passing the output of aapt2 compilation for this parameter.\n"
				+ "Any implicit AAR compilation results that are part of the input will be automatically added."))
@NestParameterInformation(value = "Overlay",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { AAPT2LinkerInputTaskOption.class }),
		info = @NestInformation("Adds compiled resources for the linking operation using overlay semantics.\n"
				+ "When you a provide a resource file that overlays (extends or modifies) an existing file, the last conflicting resource given is used."
				+ "The parameter accepts one or more elemets which may be outputs from the "
				+ AAPT2CompileTaskFactory.TASK_NAME
				+ "() task, as well as direct file paths to compiled resource files.\n"
				+ "Generally, you will be passing the aapt2 compilation output of the library dependencies for this parameter.\n"
				+ "Any implicit AAR compilation results that are part of the input will be automatically added.\n"
				+ "When using this parameter, you probably want to set the AutoAddOverlay parameter to true.\n"
				+ "This parameter corresponds to the -R option of aapt2."))
@NestParameterInformation(value = "Manifest",
		required = true,
		type = @NestTypeUsage(value = FileLocationTaskOption.class),
		info = @NestInformation("Specifies the AndroidManifest.xml file for the linking operation.\n"
				+ "This is a required parameter because the manifest file encloses essential information about your app like package name and application ID.\n"
				+ "This parameter corresponds to the --manifest option of aapt2."))

@NestParameterInformation(value = "PackageId",
		type = @NestTypeUsage(value = Integer.class),
		info = @NestInformation("Specifies the package ID to use for your app.\n"
				+ "The package ID that you specify must be greater than or equal to 0x7f unless used in combination with --allow-reserved-package-id.\n"
				+ "This parameter corresponds to the --package-id option of aapt2."))

@NestParameterInformation(value = "GenerateProguardRules",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Flag to specify if ProGuard files should be generated.\n"
				+ "This parameter corresponds to the --proguard option of aapt2."))
@NestParameterInformation(value = "GenerateMainDexProguardRules",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Flag to specify if ProGuard files for the main dex file should be generated.\n"
				+ "This parameter corresponds to the --proguard-main-dex option of aapt2."))

@NestParameterInformation(value = "Configurations",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("List of configurations. Each element will be joined with a comma separator and passed as the -c aapt2 parameter.\n"
				+ "For example, if you have dependencies on the support library (which contains translations "
				+ "for multiple languages), you can filter resources just for the given language configuration, "
				+ "like English or Spanish.\n"
				+ "You must define the language configuration by a two-letter ISO 639-1 language code, "
				+ "optionally followed by a two letter ISO 3166-1-alpha-2 region code preceded "
				+ "by lowercase 'r' (for example, en-rUS)."))

@NestParameterInformation(value = "PreferredDensity",
		type = @NestTypeUsage(value = String.class),
		info = @NestInformation("Specify a preferred density, to cause AAPT2 to select "
				+ "and store the closest matching density in the resource table and remove all others.\n"
				+ "This parameter corresponds to the --preferred-density option of aapt2."))

@NestParameterInformation(value = "ProductNames",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("List of product names to keep. Each element will be joined with a "
				+ "comma separator and passed as the -product aapt2 parameter."))

@NestParameterInformation(value = "MinSDKVersion",
		type = @NestTypeUsage(value = Integer.class),
		info = @NestInformation("Sets the default minimum SDK version to use for AndroidManifest.xml.\n"
				+ "This parameter corresponds to the --min-sdk-version option of aapt2."))
@NestParameterInformation(value = "TargetSDKVersion",
		type = @NestTypeUsage(value = Integer.class),
		info = @NestInformation("Sets the default target SDK version to use for AndroidManifest.xml.\n"
				+ "This parameter corresponds to the --target-sdk-version option of aapt2."))

@NestParameterInformation(value = "VersionCode",
		type = @NestTypeUsage(value = Integer.class),
		info = @NestInformation("Specifies the version code to inject into the AndroidManifest.xml if none is present.\n"
				+ "This parameter corresponds to the --version-code option of aapt2."))
@NestParameterInformation(value = "VersionCodeMajor",
		type = @NestTypeUsage(value = Integer.class),
		info = @NestInformation("Specifies the version code major to inject into the AndroidManifest.xml if none is present.\n"
				+ "This parameter corresponds to the --version-code-major option of aapt2."))
@NestParameterInformation(value = "VersionName",
		type = @NestTypeUsage(value = String.class),
		info = @NestInformation("Specifies the version name to inject into the AndroidManifest.xml if none is present.\n"
				+ "This parameter corresponds to the --version-name option of aapt2."))

@NestParameterInformation(value = "CompileSDKVersionCode",
		type = @NestTypeUsage(value = String.class),
		info = @NestInformation("Specifies the version code to inject into the AndroidManifest.xml if none is present.\n"
				+ "This parameter corresponds to the --compile-sdk-version-code option of aapt2."))
@NestParameterInformation(value = "CompileSDKVersionName",
		type = @NestTypeUsage(value = String.class),
		info = @NestInformation("Specifies the version name to inject into the AndroidManifest.xml if none is present.\n"
				+ "This parameter corresponds to the --compile-sdk-version-name option of aapt2."))

@NestParameterInformation(value = "CreateIDMappings",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Cause the build task to emit a file with a list of names of resource types and their ID mappings.\n"
				+ "Causes the task to use the --emit-ids option of aapt2."))
@NestParameterInformation(value = "StableIDsFile",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("Path to a file generated with --emit-ids containing the list of names of resource types and their assigned IDs.\n"
				+ "This option allows assigned IDs to remain stable even when you delete or add new resources while linking.\n"
				+ "This parameter corresponds to the --stable-ids option of aapt2."))

@NestParameterInformation(value = "PrivateSymbols",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("Package name to use when generating R.java for private symbols.\n"
				+ "If not specified, public and private symbols will use the application's package name.\n"
				+ "This parameter corresponds to the --private-symbols option of aapt2."))
@NestParameterInformation(value = "CustomPackage",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("Specifies custom Java package under which to generate R.java.\n"
				+ "This parameter corresponds to the --custom-package option of aapt2."))

@NestParameterInformation(value = "ExtraPackages",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("Specifies one or more Java package names under which the same R.java source file should be generated.\n"
				+ "Each element in this parameter corresponds to an --extra-packages option of aapt2."))
@NestParameterInformation(value = "JavadocAnnotations",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("Adds a JavaDoc annotation to all generated Java classes.\n"
				+ "Each element in this parameter corresponds to an --add-javadoc-annotation option of aapt2."))

@NestParameterInformation(value = "RenameManifestPackage",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("Renames the package in AndroidManifest.xml.\n"
				+ "This parameter corresponds to the --rename-manifest-package option of aapt2."))
@NestParameterInformation(value = "RenameInstrumentationTargetPackage",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("Changes the name of the target package for instrumentation.\n"
				+ "It should be used in conjunction with --rename-manifest-package.\n"
				+ "This parameter corresponds to the --rename-instrumentation-target-package option of aapt2."))

@NestParameterInformation(value = "NonCompressedExtensions",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("Specifies the extensions of files that you do not want to compress.\n"
				+ "Each element in this parameter corresponds to an -0 option of aapt2."))

@NestParameterInformation(value = "NoCompressRegex",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("Do not compress extensions matching the regular expression. "
				+ "Remember to use the '$' symbol for end of line. Uses a case-sensitive ECMAScriptregular expression grammar.\n"
				+ "This parameter corresponds to the --no-compress-regex option of aapt2."))

@NestParameterInformation(value = "ExcludeConfigurations",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("Excludes values of resources whose configs contain the specified qualifiers.\n"
				+ "Each element in this parameter corresponds to an --exclude-configs option of aapt2."))

@NestParameterInformation(value = "Splits",
		type = @NestTypeUsage(value = Map.class, elementTypes = { String.class, Collection.class }),
		info = @NestInformation("Splits resources based on a set of configurations to generate a different version of the APK.\n"
				+ "The parameter takes a map as its input where each key is an APK name and the value is a list of configurations that the "
				+ "associated APK should contain."
				+ "Each element in this parameter corresponds to a --split option of aapt2."))

@NestParameterInformation(value = "AllowReservedPackageId",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Allows the use of a reserved package ID."
				+ "Reserved package IDs are IDs that are normally assigned to shared libraries and are in the range "
				+ "from 0x02 to 0x7e inclusive. By using --allow-reserved-package-id, you can assign IDs "
				+ "that fall in the range of reserved package IDs.\n"
				+ "This should only be used for packages with a min-sdk version of 26 or lower.\n"
				+ "This parameter corresponds to the --allow-reserved-package-id option of aapt2."))
@NestParameterInformation(value = "ProguardConditionalKeepRules",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Generate conditional Proguard keep rules.\n"
				+ "This parameter corresponds to the --proguard-conditional-keep-rules option of aapt2."))
@NestParameterInformation(value = "ProguardMinimalKeepRules",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Generate a minimal set of Proguard keep rules.\n"
				+ "This parameter corresponds to the --proguard-minimal-keep-rules option of aapt2."))
@NestParameterInformation(value = "NoAutoVersion",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Disables automatic style and layout SDK versioning.\n"
				+ "This parameter corresponds to the --no-auto-version option of aapt2."))
@NestParameterInformation(value = "NoVersionVectors",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Disables automatic versioning of vector drawables. "
				+ "Use this only when building your APK with the Vector Drawable Library.\n"
				+ "This parameter corresponds to the --no-version-vectors option of aapt2."))
@NestParameterInformation(value = "NoVersionTransitions",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Disables automatic versioning of transition resources. "
				+ "Use this only when building your APK with Transition Support library.\n"
				+ "This parameter corresponds to the --no-version-transitions option of aapt2."))
@NestParameterInformation(value = "NoResourceDeduping",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Disables automatic de-duplication of resources with identical "
				+ "values across compatible configurations.\n"
				+ "This parameter corresponds to the --no-resource-deduping option of aapt2."))
@NestParameterInformation(value = "NoResourceRemoval",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Disables automatic removal of resources without defaults. "
				+ "Use this only when building runtime resource overlay packages.\n"
				+ "This parameter corresponds to the --no-resource-removal option of aapt2."))
@NestParameterInformation(value = "EnableSparseEncoding",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Enables encoding of sparse entries using a binary search tree. "
				+ "This is useful for optimization of APK size, but at the cost of "
				+ "resource retrieval performance.\n"
				+ "This parameter corresponds to the --enable-sparse-encoding option of aapt2."))
@NestParameterInformation(value = "RequireSuggestedLocalization",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Requires localization of strings marked 'suggested'.\n"
				+ "This parameter corresponds to the -z option of aapt2."))
@NestParameterInformation(value = "NoXmlNamespaces",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Removes XML namespace prefix and URI information from AndroidManifest.xml "
				+ "and XML binaries in res/*.\n"
				+ "This parameter corresponds to the --no-xml-namespaces option of aapt2."))
@NestParameterInformation(value = "ReplaceVersion",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("If --version-code and/or --version-name are specified, these "
				+ "values will replace any value already in the manifest. By "
				+ "default, nothing is changed if the manifest already defines these attributes.\n"
				+ "This parameter corresponds to the --replace-version option of aapt2."))
@NestParameterInformation(value = "NoStaticLibPackages",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Merge all library resources under the app's package.\n"
				+ "This parameter corresponds to the --no-static-lib-packages option of aapt2."))
@NestParameterInformation(value = "NonFinalIds",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Generates R.java with non-final resource IDs.\n"
				+ "This parameter corresponds to the --non-final-ids option of aapt2."))
@NestParameterInformation(value = "AutoAddOverlay",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Allows the addition of new resources in overlays without using the <add-resource> tag.\n"
				+ "This parameter corresponds to the --auto-add-overlay option of aapt2."))
@NestParameterInformation(value = "NoCompress",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Do not compress any resources.\n"
				+ "This parameter corresponds to the --no-compress option of aapt2."))
@NestParameterInformation(value = "KeepRawValues",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Preserve raw attribute values in xml files.\n"
				+ "This parameter corresponds to the --keep-raw-values option of aapt2."))
@NestParameterInformation(value = "WarnManifestValidation",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Treat manifest validation errors as warnings.\n"
				+ "This parameter corresponds to the --warn-manifest-validation option of aapt2."))
@NestParameterInformation(value = "DebugMode",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Inserts android:debuggable=\"true\" in to the application node of the "
				+ "manifest, making the application debuggable even on production devices.\n"
				+ "This parameter corresponds to the --debug-mode option of aapt2."))
@NestParameterInformation(value = "StrictVisibility",
		type = @NestTypeUsage(value = boolean.class),
		info = @NestInformation("Do not allow overlays with different visibility levels.\n"
				+ "This parameter corresponds to the --strict-visibility option of aapt2."))

@NestParameterInformation(value = "OutputFormat",
		type = @NestTypeUsage(AAPT2OutputFormatTaskOption.class),
		info = @NestInformation("Specifies the output format of the linking.\n"
				+ "Corresponds to the --shared-lib, --static-lib, --proto-format flags of aapt2."))

@NestParameterInformation(value = "Verbose",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Enable verbose logging.\n" + "Corresponds to the -v flag of aapt2."))

@NestParameterInformation(value = "Identifier",
		type = @NestTypeUsage(CompilationIdentifierTaskOption.class),
		info = @NestInformation("Specifies an identifier for the linking operation.\n"
				+ "The identifier will be used to uniquely identify this operation, and to generate the output directory name."))
@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))
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

			@SakerInput(value = { "RenameManifestPackage" })
			public String renameManifestPackageOption;
			@SakerInput(value = { "RenameInstrumentationTargetPackage" })
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

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				Set<AAPT2LinkerInput> inputset = new LinkedHashSet<>();
				Set<AAPT2LinkerInput> overlayset = new LinkedHashSet<>();
				addLinkerInputOptions(taskcontext, inputset, inputOption);
				addLinkerInputOptions(taskcontext, overlayset, overlayOption);

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
			Collection<AAPT2LinkerInputTaskOption> inoptions) {
		if (inoptions == null) {
			return;
		}
		for (AAPT2LinkerInputTaskOption inoption : inoptions) {
			if (inoption == null) {
				continue;
			}
			inoption.accept(new AAPT2LinkerInputTaskOption.Visitor() {
				@Override
				public void visit(AAPT2LinkTaskOutput linkinput) {
					inputset.add(new LinkAAPT2LinkerInput(linkinput));
				}

				@Override
				public void visit(AAPT2CompileWorkerTaskOutput compilationinput) {
					inputset.add(new CompilationAAPT2LinkerInput(compilationinput));
				}

				@Override
				public void visit(AAPT2AarCompileTaskOutput compilationinput) {
					inputset.add(new AarCompilationAAPT2LinkerInput(compilationinput));
				}

				@Override
				public void visit(AAPT2CompileFrontendTaskOutput compilationinput) {
					visit((AAPT2CompileWorkerTaskOutput) taskcontext
							.getTaskResult(compilationinput.getWorkerTaskIdentifier()));
					Collection<StructuredTaskResult> aarcompilations = compilationinput.getAarCompilations();
					if (!ObjectUtils.isNullOrEmpty(aarcompilations)) {
						for (StructuredTaskResult aarctaskresult : aarcompilations) {
							AAPT2AarCompileTaskOutput compiletaskout = (AAPT2AarCompileTaskOutput) aarctaskresult
									.toResult(taskcontext);
							visit(compiletaskout);
						}
					}
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
					inputset.add(new FileAAPT2LinkerInput(file));
				}
			});
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
