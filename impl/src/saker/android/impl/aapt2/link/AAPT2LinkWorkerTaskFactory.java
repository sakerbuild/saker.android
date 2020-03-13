package saker.android.impl.aapt2.link;

import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkInputLibrary;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.impl.aapt2.AAPT2Utils;
import saker.android.impl.aapt2.aar.AAPT2AarCompileWorkerTaskFactory;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.aapt2.AAPT2LinkTaskFactory;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.HashContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AAPT2LinkWorkerTaskFactory
		implements TaskFactory<AAPT2LinkTaskOutput>, Task<AAPT2LinkTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<AAPT2LinkerInput> input;
	private Set<AAPT2LinkerInput> overlay;
	private FileLocation manifest;
	private Set<AAPT2LinkerFlag> flags = Collections.emptySet();
	private Integer packageId;

	private boolean generateProguardRules;
	private boolean generateMainDexProguardRules;

	/**
	 * <code>-c</code> argument.
	 */
	private List<String> configurations = Collections.emptyList();

	private String preferredDensity;
	private List<String> productNames = Collections.emptyList();
	private Integer minSdkVersion;
	private Integer targetSdkVersion;
	private Integer versionCode;
	private Integer versionCodeMajor;
	private String versionName;
	private String compileSdkVersionCode;
	private String compileSdkVersionName;

	private boolean emitIds;
	private SakerPath stableIdsFilePath;
	private String privateSymbols;
	private String customPackage;
	private NavigableSet<String> extraPackages = Collections.emptyNavigableSet();
	private List<String> addJavadocAnnotation = Collections.emptyList();
	private String renameManifestPackage;
	private String renameInstrumentationTargetPackage;
	private NavigableSet<String> noncompressedExtensions = Collections.emptyNavigableSet();
	private String noCompressRegex;

	private NavigableSet<String> excludeConfigs = Collections.emptyNavigableSet();

	/**
	 * Maps user provided ids, to set of configurations
	 */
	private NavigableMap<String, NavigableSet<String>> splits = Collections.emptyNavigableMap();

	private transient boolean verbose;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2LinkWorkerTaskFactory() {
	}

	public AAPT2LinkWorkerTaskFactory(Set<AAPT2LinkerInput> input, FileLocation manifest) {
		this.input = input;
		this.manifest = manifest;
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

	public void setPackageId(Integer packageId) {
		this.packageId = packageId;
	}

	public void setGenerateProguardRules(boolean generateProguardRules) {
		this.generateProguardRules = generateProguardRules;
	}

	public void setGenerateMainDexProguardRules(boolean generateMainDexProguardRules) {
		this.generateMainDexProguardRules = generateMainDexProguardRules;
	}

	public void setConfigurations(List<String> configurations) {
		this.configurations = ObjectUtils.isNullOrEmpty(configurations) ? Collections.emptyList() : configurations;
	}

	public void setPreferredDensity(String preferredDensity) {
		this.preferredDensity = preferredDensity;
	}

	public void setProductNames(List<String> productNames) {
		this.productNames = ObjectUtils.isNullOrEmpty(productNames) ? Collections.emptyList() : productNames;
	}

	public void setMinSdkVersion(Integer minSdkVersion) {
		this.minSdkVersion = minSdkVersion;
	}

	public void setTargetSdkVersion(Integer targetSdkVersion) {
		this.targetSdkVersion = targetSdkVersion;
	}

	public void setVersionCode(Integer versionCode) {
		this.versionCode = versionCode;
	}

	public void setVersionCodeMajor(Integer versionCodeMajor) {
		this.versionCodeMajor = versionCodeMajor;
	}

	public void setVersionName(String versionName) {
		this.versionName = versionName;
	}

	public void setCompileSdkVersionCode(String compileSdkVersionCode) {
		this.compileSdkVersionCode = compileSdkVersionCode;
	}

	public void setCompileSdkVersionName(String compileSdkVersionName) {
		this.compileSdkVersionName = compileSdkVersionName;
	}

	public void setEmitIds(boolean emitIds) {
		this.emitIds = emitIds;
	}

	public void setStableIdsFilePath(SakerPath stableIdsFilePath) {
		this.stableIdsFilePath = stableIdsFilePath;
	}

	public void setPrivateSymbols(String privateSymbols) {
		this.privateSymbols = privateSymbols;
	}

	public void setCustomPackage(String customPackage) {
		this.customPackage = customPackage;
	}

	public void setExtraPackages(NavigableSet<String> extraPackages) {
		this.extraPackages = ObjectUtils.isNullOrEmpty(extraPackages) ? Collections.emptyNavigableSet() : extraPackages;
	}

	public void setAddJavadocAnnotation(List<String> addJavadocAnnotation) {
		this.addJavadocAnnotation = ObjectUtils.isNullOrEmpty(addJavadocAnnotation) ? Collections.emptyList()
				: addJavadocAnnotation;
	}

	public void setRenameManifestPackage(String renameManifestPackage) {
		this.renameManifestPackage = renameManifestPackage;
	}

	public void setRenameInstrumentationTargetPackage(String renameInstrumentationTargetPackage) {
		this.renameInstrumentationTargetPackage = renameInstrumentationTargetPackage;
	}

	public void setNoncompressedExtensions(NavigableSet<String> noncompressedExtensions) {
		this.noncompressedExtensions = ObjectUtils.isNullOrEmpty(noncompressedExtensions)
				? Collections.emptyNavigableSet()
				: noncompressedExtensions;
	}

	public void setNoCompressRegex(String noCompressRegex) {
		this.noCompressRegex = noCompressRegex;
	}

	public void setExcludeConfigs(NavigableSet<String> excludeConfigs) {
		this.excludeConfigs = ObjectUtils.isNullOrEmpty(excludeConfigs) ? Collections.emptyNavigableSet()
				: excludeConfigs;
	}

	public void setSplits(NavigableMap<String, NavigableSet<String>> splits) {
		this.splits = ObjectUtils.isNullOrEmpty(splits) ? Collections.emptyNavigableMap() : splits;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setFlags(Set<AAPT2LinkerFlag> flags) {
		this.flags = flags == null ? Collections.emptySet() : flags;
	}

	public void setInput(Set<AAPT2LinkerInput> input) {
		this.input = input;
	}

	public void setOverlay(Set<AAPT2LinkerInput> overlay) {
		this.overlay = overlay;
	}

	public void setManifest(FileLocation manifest) {
		this.manifest = manifest;
	}

	@Override
	public int getRequestedComputationTokenCount() {
		return 1;
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return remoteDispatchableEnvironmentSelector;
		}
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@Override
	public Set<String> getCapabilities() {
		//TODO re-enable remote dispatchability when local file locations are taken into account
//		if (remoteDispatchableEnvironmentSelector != null) {
//			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
//		}
		return TaskFactory.super.getCapabilities();
	}

	@Override
	public Task<? extends AAPT2LinkTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2LinkTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		AAPT2LinkWorkerTaskIdentifier taskid = (AAPT2LinkWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		taskcontext.setStandardOutDisplayIdentifier("aapt2.link:" + compilationid);
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.setDisplayInformation("aapt2.link:" + compilationid,
					AAPT2LinkTaskFactory.TASK_NAME + ":" + compilationid);
		}

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
		SakerDirectory outputdir = taskutils.resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AAPT2LinkTaskFactory.TASK_NAME + "/" + compilationid));

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}
		SDKReference buildtoolssdkref = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		if (buildtoolssdkref == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not found.");
		}
		SakerPath exepath = buildtoolssdkref.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdkref);
		}

		NavigableMap<SakerPath, ContentDescriptor> inputfilecontents = new ConcurrentSkipListMap<>();

		outputdir.clear();

		SakerDirectory javaoutdir = outputdir.getDirectoryCreate("java");

		String outputapkfilename = "output.apk";
		Path outputdirmirror = taskcontext.mirror(outputdir);
		Path outputapkfilelocalpath = outputdirmirror.resolve(outputapkfilename);

		Path outputproguardruleslocalpath = getOutputPathIfEnabledForFileName(outputdirmirror, generateProguardRules,
				"rules.pro");
		Path outputmaindexproguardruleslocalpath = getOutputPathIfEnabledForFileName(outputdirmirror,
				generateMainDexProguardRules, "rules_main.pro");
		Path outputemitidslocalpath = getOutputPathIfEnabledForFileName(outputdirmirror, emitIds, "emit_ids.txt");
		Path outputtextsymbolslocalpath = outputdirmirror.resolve("R.txt");

		Path javaoutputdirpath = taskcontext.mirror(javaoutdir);

		NavigableMap<String, Path> splitoutputapkpaths = new TreeMap<>();

		Map<String, FileLocation> packagenamertxtlocations = new TreeMap<>();

		List<AAPT2LinkInputLibrary> inputlibraries = new ArrayList<>();

		ArrayList<String> cmd = new ArrayList<>();
		cmd.add("link");
		//XXX  parallelize if necessary
		for (AAPT2LinkerInput linkerinput : input) {
			addInputCommandsForLinkerInput(taskcontext, taskutils, inputfilecontents, cmd, linkerinput, null,
					packagenamertxtlocations, inputlibraries);
		}
		if (!ObjectUtils.isNullOrEmpty(overlay)) {
			for (AAPT2LinkerInput linkerinput : overlay) {
				addInputCommandsForLinkerInput(taskcontext, taskutils, inputfilecontents, cmd, linkerinput, "-R",
						packagenamertxtlocations, inputlibraries);
			}
		}

		cmd.add("-o");
		cmd.add(outputapkfilelocalpath.toString());

		if (manifest != null) {
			cmd.add("--manifest");
			manifest.accept(new FileLocationVisitor() {
				@Override
				public void visit(ExecutionFileLocation loc) {
					SakerPath manifestpath = loc.getPath();
					try {
						MirroredFileContents manifestcontents = taskutils.mirrorFileAtPathContents(manifestpath);
						cmd.add(manifestcontents.getPath().toString());
						inputfilecontents.put(manifestpath, manifestcontents.getContents());
					} catch (IOException e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				}

				@Override
				public void visit(LocalFileLocation loc) {
					SakerPath localpath = loc.getLocalPath();
					cmd.add(localpath.toString());
					taskcontext.getTaskUtilities().getReportExecutionDependency(SakerStandardUtils
							.createLocalFileContentDescriptorExecutionProperty(localpath, taskcontext.getTaskId()));
				}
			});
		}

		if (stableIdsFilePath != null) {
			MirroredFileContents stableidscontents = taskutils.mirrorFileAtPathContents(stableIdsFilePath);
			cmd.add("--stable-ids");
			cmd.add(stableidscontents.getPath().toString());
			inputfilecontents.put(stableIdsFilePath, stableidscontents.getContents());
		}

		cmd.add("--java");
		cmd.add(javaoutputdirpath.toString());

		addArgumentIfNonNull(cmd, "--preferred-density", preferredDensity);
		if (!ObjectUtils.isNullOrEmpty(productNames)) {
			cmd.add("--product");
			cmd.add(StringUtils.toStringJoin(",", productNames));
		}
		if (!ObjectUtils.isNullOrEmpty(configurations)) {
			cmd.add("-c");
			cmd.add(StringUtils.toStringJoin(",", configurations));
		}
		if (!ObjectUtils.isNullOrEmpty(noncompressedExtensions)) {
			for (String ext : noncompressedExtensions) {
				cmd.add("-0");
				cmd.add(ext);
			}
		}
		if (!ObjectUtils.isNullOrEmpty(extraPackages)) {
			for (String ep : extraPackages) {
				cmd.add("--extra-packages");
				cmd.add(ep);
			}
		}
		if (!ObjectUtils.isNullOrEmpty(excludeConfigs)) {
			for (String c : excludeConfigs) {
				cmd.add("--exclude-configs");
				cmd.add(c);
			}
		}
		if (!ObjectUtils.isNullOrEmpty(splits)) {
			for (Entry<String, NavigableSet<String>> entry : splits.entrySet()) {
				if (ObjectUtils.isNullOrEmpty(entry.getValue())) {
					continue;
				}
				Path splitapkoutputlocalpath = outputdirmirror.resolve(entry.getKey() + ".split.apk");

				cmd.add("--split");
				cmd.add(splitapkoutputlocalpath + File.pathSeparator + StringUtils.toStringJoin(",", entry.getValue()));
				splitoutputapkpaths.put(entry.getKey(), splitapkoutputlocalpath);
			}
		}
		if (!ObjectUtils.isNullOrEmpty(addJavadocAnnotation)) {
			for (String annot : addJavadocAnnotation) {
				cmd.add("--add-javadoc-annotation");
				cmd.add(annot);
			}
		}
		addArgumentIfNonNull(cmd, "--min-sdk-version", minSdkVersion);
		addArgumentIfNonNull(cmd, "--target-sdk-version", targetSdkVersion);
		addArgumentIfNonNull(cmd, "--version-code", versionCode);
		addArgumentIfNonNull(cmd, "--version-code-major", versionCodeMajor);
		addArgumentIfNonNull(cmd, "--version-name", versionName);
		addArgumentIfNonNull(cmd, "--compile-sdk-version-code", compileSdkVersionCode);
		addArgumentIfNonNull(cmd, "--compile-sdk-version-name", compileSdkVersionName);

		addArgumentIfNonNull(cmd, "--private-symbols", privateSymbols);
		addArgumentIfNonNull(cmd, "--custom-packag", customPackage);
		addArgumentIfNonNull(cmd, "--rename-manifest-package", renameManifestPackage);
		addArgumentIfNonNull(cmd, "--rename-instrumentation-target-package", renameInstrumentationTargetPackage);
		addArgumentIfNonNull(cmd, "--no-compress-regex", noCompressRegex);

		addArgumentIfNonNull(cmd, "--proguard", outputproguardruleslocalpath);
		addArgumentIfNonNull(cmd, "--proguard-main-dex", outputmaindexproguardruleslocalpath);
		addArgumentIfNonNull(cmd, "--emit-ids", outputemitidslocalpath);
		addArgumentIfNonNull(cmd, "--output-text-symbols", outputtextsymbolslocalpath);

		SDKReference platformsdk = sdkrefs.get(AndroidPlatformSDKReference.SDK_NAME);
		if (platformsdk != null) {
			SakerPath androidjarpath = platformsdk.getPath(AndroidPlatformSDKReference.PATH_ANDROID_JAR);
			if (androidjarpath != null) {
				cmd.add("-I");
				cmd.add(androidjarpath.toString());
			}
		}
		if (packageId != null) {
			cmd.add("--package-id");
			cmd.add("0x" + Integer.toHexString(packageId));
		}

		for (AAPT2LinkerFlag f : flags) {
			cmd.add(f.argument);
		}
		if (verbose) {
			cmd.add("-v");
		}

		UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();

		try {
			int res = AAPT2Utils.invokeAAPT2WithArguments(environment, exepath, cmd, procout);
			if (res != 0) {
				throw new IOException("aapt2 linking failed.");
			}
		} finally {
			if (!procout.isEmpty()) {
				procout.writeTo(taskcontext.getStandardOut());
			}
		}

		TreeMap<SakerPath, ContentDescriptor> outputfilecontents = new TreeMap<>();

		LocalFileProvider fp = LocalFileProvider.getInstance();
		SakerPath outputapkpath = discoverOutputFile(taskcontext, outputdir, outputapkfilelocalpath, outputfilecontents,
				fp);

		SakerPath outputproguardpath = discoverOutputFile(taskcontext, outputdir, outputproguardruleslocalpath,
				outputfilecontents, fp);
		SakerPath outputmaindexproguardpath = discoverOutputFile(taskcontext, outputdir,
				outputmaindexproguardruleslocalpath, outputfilecontents, fp);

		SakerPath outputemitidspath = discoverOutputFile(taskcontext, outputdir, outputemitidslocalpath,
				outputfilecontents, fp);
		SakerPath outputtextsymbolspath = discoverOutputFile(taskcontext, outputdir, outputtextsymbolslocalpath,
				outputfilecontents, fp);

		NavigableMap<String, SakerPath> splitoutpaths = new TreeMap<>();
		if (!ObjectUtils.isNullOrEmpty(splitoutputapkpaths)) {
			for (Entry<String, Path> entry : splitoutputapkpaths.entrySet()) {
				SakerPath splitoutputpath = discoverOutputFile(taskcontext, outputdir, entry.getValue(),
						outputfilecontents, fp);
				splitoutpaths.put(entry.getKey(), splitoutputpath);
			}
		}

		Set<SakerPath> rjavaentries = getCreateRJavaFilePathInEntries(
				fp.getDirectoryEntriesRecursively(javaoutputdirpath));
		if (ObjectUtils.isNullOrEmpty(rjavaentries)) {
			throw new IOException("R.java not found.");
		}
		for (SakerPath rjavapath : rjavaentries) {
			SakerDirectory rjavaparentdir = taskutils.resolveDirectoryAtRelativePathCreate(javaoutdir,
					rjavapath.getParent());
			SakerPath rjavaabsolutepath = SakerPath.valueOf(javaoutputdirpath).resolve(rjavapath);
			ProviderHolderPathKey rjavapathkey = fp.getPathKey(rjavaabsolutepath);
			taskcontext.invalidate(rjavapathkey);

			//create our own content descriptor so the reinvocation of downstream java compilation tasks can be easily avoided 
			//    if the actual contents didnt change
			//make it hash based so it depends on the contents
			ContentDescriptor rjavafilecontents = HashContentDescriptor
					.createWithHash(fp.hash(rjavaabsolutepath, FileUtils.DEFAULT_FILE_HASH_ALGORITHM));

			SakerFile rjavafile = taskutils.createProviderPathFile(rjavapath.getFileName(), rjavapathkey,
					rjavafilecontents);
			rjavaparentdir.add(rjavafile);

			outputfilecontents.put(rjavafile.getSakerPath(), rjavafilecontents);
		}

		if (!packagenamertxtlocations.isEmpty()) {
			SakerFile outrtxtfile = outputdir.get("R.txt");
			if (outrtxtfile == null) {
				throw new IllegalStateException("AAPT2 Generated R.txt not found.");
			}
			SymbolTable rtxtsymbols;
			try (InputStream rtxtin = outrtxtfile.openInputStream()) {
				rtxtsymbols = readRSymbolTable(rtxtin);
			}

			for (Entry<String, FileLocation> entry : packagenamertxtlocations.entrySet()) {
				FileLocation rtxtfile = entry.getValue();
				if (rtxtfile == null) {
					continue;
				}

				String pkgname = entry.getKey();
				SymbolTable packagersymbols = readFileRSymbolTable(taskcontext, rtxtfile);
				ByteArrayRegion rjavabytes;
				try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
					try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
						writeRJavaForPackageRSymbols(rtxtsymbols, pkgname, packagersymbols, writer);
					}
					rjavabytes = baos.toByteArrayRegion();
				}
				ByteArraySakerFile rjavafile = new ByteArraySakerFile("R.java", rjavabytes);

				taskcontext.getTaskUtilities()
						.resolveDirectoryAtRelativePathCreate(javaoutdir, SakerPath.valueOf(pkgname.replace('.', '/')))
						.add(rjavafile);
				outputfilecontents.put(rjavafile.getSakerPath(), rjavafile.getContentDescriptor());
			}
		}

		outputdir.synchronize();

		taskutils.reportOutputFileDependency(null, outputfilecontents);
		taskutils.reportInputFileDependency(null, inputfilecontents);

		SakerPath rjavasourcedirpath = javaoutdir.getSakerPath();

		AAPT2LinkTaskOutputImpl result = new AAPT2LinkTaskOutputImpl(compilationid, outputapkpath);
		result.setJavaSourceDirectories(ImmutableUtils.asUnmodifiableArrayList(rjavasourcedirpath));
		result.setProguardPath(outputproguardpath);
		result.setProguardMainDexPath(outputmaindexproguardpath);
		result.setIDMappingsPath(outputemitidspath);
		result.setTextSymbolsPath(outputtextsymbolspath);
		result.setSplits(splitoutpaths);
		result.setInputLibraries(inputlibraries);
		return result;
	}

	private static void writeRJavaForPackageRSymbols(SymbolTable rtxtsymbols, String pkgname,
			SymbolTable packagersymbols, OutputStreamWriter writer) throws IOException {
		String ls = System.lineSeparator();
		writer.append("package ");
		writer.append(pkgname);
		writer.append(";");
		writer.append(ls);
		writer.append("public class R {");
		writer.append(ls);
		for (Entry<String, NavigableMap<String, RSymbolEntry>> rentry : packagersymbols.entries.entrySet()) {
			String restypes = rentry.getKey();

			writer.append("\tpublic static class ");
			writer.append(restypes);
			writer.append("{");
			writer.append(ls);

			for (RSymbolEntry symbolentry : rentry.getValue().values()) {
				RSymbolEntry fixsymbol = rtxtsymbols.getSymbol(restypes, symbolentry.name);
				if (fixsymbol == null) {
					throw new IllegalStateException("R symbol not found: " + restypes + " " + symbolentry.name);
				}
				writer.append("\t\tpublic static final ");
				writer.append(symbolentry.type);
				writer.append(" ");
				writer.append(symbolentry.name);
				writer.append(" = ");
				writer.append(fixsymbol.value);
				writer.append(";");
				writer.append(ls);
			}

			writer.append("\t}");
			writer.append(ls);
		}
		writer.append("}");
		writer.append(ls);
	}

	private static SymbolTable readFileRSymbolTable(TaskContext taskcontext, FileLocation file) {
		if (file == null) {
			return null;
		}
		SymbolTable[] result = { null };
		file.accept(new FileLocationVisitor() {
			@Override
			public void visit(LocalFileLocation loc) {
				try (InputStream is = LocalFileProvider.getInstance().openInputStream(loc.getLocalPath())) {
					result[0] = readRSymbolTable(is);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
				taskcontext.getTaskUtilities().getReportExecutionDependency(
						SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(loc.getLocalPath(),
								taskcontext.getTaskId()));
			}

			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(loc.getPath());
				if (f == null) {
					taskcontext.reportInputFileDependency(null, loc.getPath(),
							CommonTaskContentDescriptors.IS_NOT_FILE);
					throw ObjectUtils.sneakyThrow(new FileNotFoundException("R.txt not found at: " + loc.getPath()));
				}
				try (InputStream is = f.openInputStream()) {
					result[0] = readRSymbolTable(is);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

		});
		return result[0];
	}

	private static SymbolTable readRSymbolTable(InputStream is) throws Exception {
		SymbolTable rtxtsymbols = new SymbolTable();
		try (InputStream rtxtin = is;
				BufferedReader reader = new BufferedReader(new InputStreamReader(rtxtin, StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				RSymbolEntry symbolentry = createAaptRTxtSymbolEntryFromLine(line);

				NavigableMap<String, RSymbolEntry> symbolentries = rtxtsymbols.entries
						.computeIfAbsent(symbolentry.resourceType, Functionals.treeMapComputer());
				symbolentries.put(symbolentry.name, symbolentry);
			}
		}
		return rtxtsymbols;
	}

	private static RSymbolEntry createAaptRTxtSymbolEntryFromLine(String line) {
		int idx1 = line.indexOf(' ');
		int idx2 = line.indexOf(' ', idx1 + 1);
		int idx3 = line.indexOf(' ', idx2 + 1);

		String type = line.substring(0, idx1);
		String restype = line.substring(idx1 + 1, idx2);
		String name = line.substring(idx2 + 1, idx3);
		String value = line.substring(idx3 + 1);
		RSymbolEntry symbolentry = new RSymbolEntry(type, restype, name, value);
		return symbolentry;
	}

	private static final class AAPT2LinkInputLibraryImpl implements AAPT2LinkInputLibrary, Externalizable {
		private static final long serialVersionUID = 1L;
		private FileLocation aarfile;

		/**
		 * For {@link Externalizable}.
		 */
		public AAPT2LinkInputLibraryImpl() {
		}

		private AAPT2LinkInputLibraryImpl(FileLocation aarfile) {
			this.aarfile = aarfile;
		}

		@Override
		public FileLocation getAarFile() {
			return aarfile;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(aarfile);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			aarfile = SerialUtils.readExternalObject(in);
		}
	}

	private static class SymbolTable {
		protected Map<String, NavigableMap<String, RSymbolEntry>> entries = new TreeMap<>();

		public RSymbolEntry getSymbol(String restype, String name) {
			NavigableMap<String, RSymbolEntry> table = entries.get(restype);
			if (table == null) {
				return null;
			}
			return table.get(name);
		}
	}

	private static class RSymbolEntry {
		protected String type;
		protected String resourceType;
		protected String name;
		protected String value;

		public RSymbolEntry(String type, String resourceType, String name, String value) {
			this.type = type;
			this.resourceType = resourceType;
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return type + " " + resourceType + " " + name + " " + value;
		}
	}

	private static void addInputCommandsForLinkerInput(TaskContext taskcontext, TaskExecutionUtilities taskutils,
			NavigableMap<SakerPath, ContentDescriptor> inputfilecontents, List<String> cmd,
			AAPT2LinkerInput linkerinput, String prearg, Map<String, FileLocation> packagenamertxtlocations,
			Collection<? super AAPT2LinkInputLibrary> outlinkinputlibs) throws Exception {
		linkerinput.accept(new AAPT2LinkerInput.Visitor() {
			@Override
			public void visit(AAPT2CompileWorkerTaskOutput compilationinput) {
				for (SakerPath inpath : compilationinput.getOutputPaths()) {
					handleExecutionInputFile(inpath);
				}
			}

			@Override
			public void visit(AAPT2AarCompileTaskOutput compilationinput) {
				//TODO generate R.java files for the aar results
				NavigableSet<SakerPath> outpaths = compilationinput.getOutputPaths();
				if (!ObjectUtils.isNullOrEmpty(outpaths)) {
					for (SakerPath inpath : outpaths) {
						handleExecutionInputFile(inpath);
					}
				}
				FileLocation manifestfile = compilationinput.getAndroidManifestXmlFile();
				String packagename;
				try {
					packagename = AAPT2AarCompileWorkerTaskFactory.getAndroidManifestPackageName(taskcontext,
							manifestfile);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
				if (packagename == null) {
					throw new IllegalArgumentException(
							"AAR android manifest doesn't have a package name in " + manifestfile);
				}
				FileLocation prev = packagenamertxtlocations.put(packagename, compilationinput.getRTxtFile());
				if (prev != null) {
					throw new IllegalArgumentException("Duplicate input aar package names: " + packagename);
				}
				if (outlinkinputlibs != null) {
					outlinkinputlibs.add(new AAPT2LinkInputLibraryImpl(compilationinput.getAarFile()));
				}
			}

			@Override
			public void visit(FileLocation inputfile) {
//				String inputfname = SakerStandardUtils.getFileLocationFileName(inputfile);
//				if (FileUtils.hasExtensionIgnoreCase(inputfname, "aar")) {
//					AAPT2AarStaticLibraryWorkerTaskFactory inworker = new AAPT2AarStaticLibraryWorkerTaskFactory(
//							inputfile, new AAPT2CompilationConfiguration(EnumSet.noneOf(AAPT2CompilerFlag.class)));
//					inworker.setSDKDescriptions(sdkDescriptions);
//					AAPT2AarStaticLibraryWorkerTaskOutput aarlibres = taskcontext.getTaskUtilities()
//							.runTaskResult(inworker.createWorkerTaskIdentifier(), inworker);
//					for (SakerPath path : aarlibres.getOutputFiles()) {
//						handleExecutionInputFile(path);
//					}
//					return;
//				}
				inputfile.accept(new FileLocationVisitor() {
					@Override
					public void visit(ExecutionFileLocation loc) {
						SakerPath inpath = loc.getPath();
						handleExecutionInputFile(inpath);
					}

					@Override
					public void visit(LocalFileLocation loc) {
						SakerPath localpath = loc.getLocalPath();
						addCommand(cmd, localpath.toString());
						taskcontext.getTaskUtilities().getReportExecutionDependency(SakerStandardUtils
								.createLocalFileContentDescriptorExecutionProperty(localpath, taskcontext.getTaskId()));
					}

				});
			}

			@Override
			public void visit(AAPT2LinkTaskOutput linkinput) {
				handleExecutionInputFile(linkinput.getAPKPath());
			}

			private void handleExecutionInputFile(SakerPath inpath) {
				try {
					MirroredFileContents filecontents = taskutils.mirrorFileAtPathContents(inpath);
					addCommand(cmd, filecontents.getPath().toString());
					inputfilecontents.put(inpath, filecontents.getContents());
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

			private void addCommand(List<String> cmd, String str) {
				if (prearg != null) {
					cmd.add(prearg);
				}
				cmd.add(str);
			}
		});
	}

	private static SakerPath discoverOutputFile(TaskContext taskcontext, SakerDirectory outputdir, Path outputfilepath,
			Map<SakerPath, ContentDescriptor> outputfilecontents, LocalFileProvider fp) throws Exception {
		if (outputfilepath == null) {
			return null;
		}
		ProviderHolderPathKey outpathkey = fp.getPathKey(outputfilepath);
		taskcontext.invalidate(outpathkey);
		
		FileHashResult hash = fp.hash(outputfilepath, FileUtils.DEFAULT_FILE_HASH_ALGORITHM);

		//Use a hash content descriptor to avoid accidental unnoticed file changes with large granularity file systems
		ContentDescriptor outputfilecd = HashContentDescriptor.createWithHash(hash);
		SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(outpathkey.getPath().getFileName(),
				outpathkey, outputfilecd);
		outputdir.add(outfile);

		SakerPath outputpath = outfile.getSakerPath();
		outputfilecontents.put(outputpath, outputfilecd);
		return outputpath;
	}

	private static Path getOutputPathIfEnabledForFileName(Path outputdirmirror, boolean flag, String fname) {
		Path outputpath;
		if (flag) {
			outputpath = outputdirmirror.resolve(fname);
		} else {
			outputpath = null;
		}
		return outputpath;
	}

	private static void addArgumentIfNonNull(ArrayList<String> cmd, String argname, Object arg) {
		if (arg == null) {
			return;
		}
		cmd.add(argname);
		cmd.add(arg.toString());
	}

	private static Set<SakerPath> getCreateRJavaFilePathInEntries(
			NavigableMap<SakerPath, ? extends FileEntry> entries) {
		TreeSet<SakerPath> result = new TreeSet<>();
		for (Entry<SakerPath, ? extends FileEntry> entry : entries.entrySet()) {
			if (!entry.getValue().isRegularFile()) {
				continue;
			}
			if ("R.java".equals(entry.getKey().getFileName())) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, input);
		SerialUtils.writeExternalCollection(out, overlay);
		out.writeObject(manifest);
		out.writeObject(packageId);
		SerialUtils.writeExternalCollection(out, flags);
		out.writeBoolean(generateProguardRules);
		out.writeBoolean(generateMainDexProguardRules);
		SerialUtils.writeExternalCollection(out, configurations);
		out.writeObject(preferredDensity);
		SerialUtils.writeExternalCollection(out, productNames);
		out.writeObject(minSdkVersion);
		out.writeObject(targetSdkVersion);
		out.writeObject(versionCode);
		out.writeObject(versionCodeMajor);
		out.writeObject(versionName);
		out.writeObject(compileSdkVersionCode);
		out.writeObject(compileSdkVersionName);
		out.writeBoolean(emitIds);
		out.writeObject(stableIdsFilePath);
		out.writeObject(privateSymbols);
		out.writeObject(customPackage);
		SerialUtils.writeExternalCollection(out, extraPackages);
		SerialUtils.writeExternalCollection(out, addJavadocAnnotation);
		out.writeObject(renameManifestPackage);
		out.writeObject(renameInstrumentationTargetPackage);
		SerialUtils.writeExternalCollection(out, noncompressedExtensions);
		out.writeObject(noCompressRegex);
		SerialUtils.writeExternalCollection(out, excludeConfigs);
		SerialUtils.writeExternalMap(out, splits, SerialUtils::writeExternalObject,
				SerialUtils::writeExternalCollection);
		out.writeBoolean(verbose);

		SerialUtils.writeExternalMap(out, sdkDescriptions);

		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		input = SerialUtils.readExternalImmutableLinkedHashSet(in);
		overlay = SerialUtils.readExternalImmutableLinkedHashSet(in);
		manifest = (FileLocation) in.readObject();
		packageId = SerialUtils.readExternalObject(in);
		flags = SerialUtils.readExternalEnumSetCollection(AAPT2LinkerFlag.class, in);
		generateProguardRules = in.readBoolean();
		generateMainDexProguardRules = in.readBoolean();
		configurations = SerialUtils.readExternalImmutableList(in);
		preferredDensity = (String) in.readObject();
		productNames = SerialUtils.readExternalImmutableList(in);
		minSdkVersion = (Integer) in.readObject();
		targetSdkVersion = (Integer) in.readObject();
		versionCode = (Integer) in.readObject();
		versionCodeMajor = (Integer) in.readObject();
		versionName = (String) in.readObject();
		compileSdkVersionCode = (String) in.readObject();
		compileSdkVersionName = (String) in.readObject();
		emitIds = in.readBoolean();
		stableIdsFilePath = (SakerPath) in.readObject();
		privateSymbols = (String) in.readObject();
		customPackage = (String) in.readObject();
		extraPackages = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		addJavadocAnnotation = SerialUtils.readExternalImmutableList(in);
		renameManifestPackage = (String) in.readObject();
		renameInstrumentationTargetPackage = (String) in.readObject();
		noncompressedExtensions = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		noCompressRegex = (String) in.readObject();
		excludeConfigs = SerialUtils.readExternalImmutableNavigableSet(in);
		splits = SerialUtils.readExternalMap(new TreeMap<>(), in, SerialUtils::readExternalObject,
				SerialUtils::readExternalImmutableNavigableSet);
		verbose = in.readBoolean();

		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());

		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((manifest == null) ? 0 : manifest.hashCode());
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
		AAPT2LinkWorkerTaskFactory other = (AAPT2LinkWorkerTaskFactory) obj;
		if (addJavadocAnnotation == null) {
			if (other.addJavadocAnnotation != null)
				return false;
		} else if (!addJavadocAnnotation.equals(other.addJavadocAnnotation))
			return false;
		if (compileSdkVersionCode == null) {
			if (other.compileSdkVersionCode != null)
				return false;
		} else if (!compileSdkVersionCode.equals(other.compileSdkVersionCode))
			return false;
		if (compileSdkVersionName == null) {
			if (other.compileSdkVersionName != null)
				return false;
		} else if (!compileSdkVersionName.equals(other.compileSdkVersionName))
			return false;
		if (configurations == null) {
			if (other.configurations != null)
				return false;
		} else if (!configurations.equals(other.configurations))
			return false;
		if (customPackage == null) {
			if (other.customPackage != null)
				return false;
		} else if (!customPackage.equals(other.customPackage))
			return false;
		if (emitIds != other.emitIds)
			return false;
		if (excludeConfigs == null) {
			if (other.excludeConfigs != null)
				return false;
		} else if (!excludeConfigs.equals(other.excludeConfigs))
			return false;
		if (extraPackages == null) {
			if (other.extraPackages != null)
				return false;
		} else if (!extraPackages.equals(other.extraPackages))
			return false;
		if (flags == null) {
			if (other.flags != null)
				return false;
		} else if (!flags.equals(other.flags))
			return false;
		if (generateMainDexProguardRules != other.generateMainDexProguardRules)
			return false;
		if (generateProguardRules != other.generateProguardRules)
			return false;
		if (input == null) {
			if (other.input != null)
				return false;
		} else if (!input.equals(other.input))
			return false;
		if (manifest == null) {
			if (other.manifest != null)
				return false;
		} else if (!manifest.equals(other.manifest))
			return false;
		if (minSdkVersion == null) {
			if (other.minSdkVersion != null)
				return false;
		} else if (!minSdkVersion.equals(other.minSdkVersion))
			return false;
		if (noCompressRegex == null) {
			if (other.noCompressRegex != null)
				return false;
		} else if (!noCompressRegex.equals(other.noCompressRegex))
			return false;
		if (noncompressedExtensions == null) {
			if (other.noncompressedExtensions != null)
				return false;
		} else if (!noncompressedExtensions.equals(other.noncompressedExtensions))
			return false;
		if (overlay == null) {
			if (other.overlay != null)
				return false;
		} else if (!overlay.equals(other.overlay))
			return false;
		if (packageId == null) {
			if (other.packageId != null)
				return false;
		} else if (!packageId.equals(other.packageId))
			return false;
		if (preferredDensity == null) {
			if (other.preferredDensity != null)
				return false;
		} else if (!preferredDensity.equals(other.preferredDensity))
			return false;
		if (privateSymbols == null) {
			if (other.privateSymbols != null)
				return false;
		} else if (!privateSymbols.equals(other.privateSymbols))
			return false;
		if (productNames == null) {
			if (other.productNames != null)
				return false;
		} else if (!productNames.equals(other.productNames))
			return false;
		if (renameInstrumentationTargetPackage == null) {
			if (other.renameInstrumentationTargetPackage != null)
				return false;
		} else if (!renameInstrumentationTargetPackage.equals(other.renameInstrumentationTargetPackage))
			return false;
		if (renameManifestPackage == null) {
			if (other.renameManifestPackage != null)
				return false;
		} else if (!renameManifestPackage.equals(other.renameManifestPackage))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		if (splits == null) {
			if (other.splits != null)
				return false;
		} else if (!splits.equals(other.splits))
			return false;
		if (stableIdsFilePath == null) {
			if (other.stableIdsFilePath != null)
				return false;
		} else if (!stableIdsFilePath.equals(other.stableIdsFilePath))
			return false;
		if (targetSdkVersion == null) {
			if (other.targetSdkVersion != null)
				return false;
		} else if (!targetSdkVersion.equals(other.targetSdkVersion))
			return false;
		if (versionCode == null) {
			if (other.versionCode != null)
				return false;
		} else if (!versionCode.equals(other.versionCode))
			return false;
		if (versionCodeMajor == null) {
			if (other.versionCodeMajor != null)
				return false;
		} else if (!versionCodeMajor.equals(other.versionCodeMajor))
			return false;
		if (versionName == null) {
			if (other.versionName != null)
				return false;
		} else if (!versionName.equals(other.versionName))
			return false;
		return true;
	}
}
