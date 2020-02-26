package saker.android.impl.aapt2.link;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
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

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.impl.aapt2.AAPT2Utils;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.aapt2.AAPT2LinkTaskFactory;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.HashContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
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

public class AAPT2LinkWorkerTaskFactory
		implements TaskFactory<AAPT2LinkTaskOutput>, Task<AAPT2LinkTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<SakerPath> inputFiles;
	private SakerPath manifest;
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
	private boolean outputTextSymbols;
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

	public void setOutputTextSymbols(boolean outputTextSymbols) {
		this.outputTextSymbols = outputTextSymbols;
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

	public void setInputFiles(NavigableSet<SakerPath> inputFiles) {
		this.inputFiles = inputFiles;
	}

	public void setManifest(SakerPath manifest) {
		this.manifest = manifest;
	}

	@Override
	public Set<String> getCapabilities() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}
		return TaskFactory.super.getCapabilities();
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return remoteDispatchableEnvironmentSelector;
		}
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@Override
	public int getRequestedComputationTokenCount() {
		return 1;
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
		taskcontext.setStandardOutDisplayIdentifier(AAPT2LinkTaskFactory.TASK_NAME + ":" + compilationid);

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
		Path outputtextsymbolslocalpath = getOutputPathIfEnabledForFileName(outputdirmirror, outputTextSymbols,
				"R.txt");

		Path javaoutputdirpath = taskcontext.mirror(javaoutdir);

		NavigableMap<String, Path> splitoutputapkpaths = new TreeMap<>();

		ArrayList<String> cmd = new ArrayList<>();
		cmd.add("link");
		//XXX  parallelize if necessary
		for (SakerPath inpath : inputFiles) {
			MirroredFileContents filecontents = taskutils.mirrorFileAtPathContents(inpath);
			cmd.add(filecontents.getPath().toString());
			inputfilecontents.put(inpath, filecontents.getContents());
		}
		cmd.add("-o");
		cmd.add(outputapkfilelocalpath.toString());

		cmd.add("--manifest");
		MirroredFileContents manifestcontents = taskutils.mirrorFileAtPathContents(manifest);
		cmd.add(manifestcontents.getPath().toString());
		inputfilecontents.put(manifest, manifestcontents.getContents());

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

		outputdir.synchronize();

		taskutils.reportOutputFileDependency(null, outputfilecontents);
		taskutils.reportInputFileDependency(null, inputfilecontents);

		SakerPath rjavasourcedirpath = javaoutdir.getSakerPath();

		AAPT2LinkTaskOutputImpl result = new AAPT2LinkTaskOutputImpl(outputapkpath, rjavasourcedirpath);
		result.setProguardPath(outputproguardpath);
		result.setProguardMainDexPath(outputmaindexproguardpath);
		result.setIDMappingsPath(outputemitidspath);
		result.setTextSymbolsPath(outputtextsymbolspath);
		result.setSplits(splitoutpaths);
		return result;
	}

	private static SakerPath discoverOutputFile(TaskContext taskcontext, SakerDirectory outputdir, Path outputfilepath,
			Map<SakerPath, ContentDescriptor> outputfilecontents, LocalFileProvider fp) throws IOException {
		if (outputfilepath == null) {
			return null;
		}
		ProviderHolderPathKey outpathkey = fp.getPathKey(outputfilepath);
		taskcontext.invalidate(outpathkey);

		SakerFile outfile = taskcontext.getTaskUtilities().createProviderPathFile(outpathkey.getPath().getFileName(),
				outpathkey);
		outputdir.add(outfile);

		SakerPath outputpath = outfile.getSakerPath();
		outputfilecontents.put(outputpath, outfile.getContentDescriptor());
		return outputpath;
	}

	private static Path getOutputPathIfEnabledForFileName(Path outputdirmirror, boolean genproguardrules,
			String fname) {
		Path outputproguardrulespath;
		if (genproguardrules) {
			outputproguardrulespath = outputdirmirror.resolve(fname);
		} else {
			outputproguardrulespath = null;
		}
		return outputproguardrulespath;
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
		SerialUtils.writeExternalCollection(out, inputFiles);
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
		out.writeBoolean(outputTextSymbols);
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
		inputFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		manifest = (SakerPath) in.readObject();
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
		outputTextSymbols = in.readBoolean();
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
		if (inputFiles == null) {
			if (other.inputFiles != null)
				return false;
		} else if (!inputFiles.equals(other.inputFiles))
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
		if (outputTextSymbols != other.outputTextSymbols)
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
