package saker.android.main.aapt2;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.compile.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.aapt2.compile.AAPT2CompilerFlag;
import saker.android.impl.aapt2.compile.option.AAPT2CompilerInputOption;
import saker.android.impl.aapt2.compile.option.ResourceDirectoryAAPT2CompilerInputOption;
import saker.android.impl.aapt2.compile.option.ResourcesAAPT2CompilerInputOption;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.AarEntryNotFoundException;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.aapt2.AAPT2LinkTaskFactory.ArtifactLocalizationOutputFileLocationStructuredTaskResult;
import saker.android.main.aapt2.option.AAPT2CompilerInputTaskOption;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationUtils;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;

public class AAPT2CompileTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.compile";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public Collection<AAPT2CompilerInputTaskOption> inputOption;

			@SakerInput("Identifier")
			public CompilationIdentifierTaskOption identifierOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@SakerInput("Legacy")
			public boolean legacyOption;
			@SakerInput("NoCrunch")
			public boolean noCrunchOption;
			@SakerInput("PseudoLocalize")
			public boolean pseudoLocalizeOption;

			@SakerInput("Verbose")
			public boolean verboseOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifierOption);
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}
				Set<AAPT2CompilerInputOption> inputs = new LinkedHashSet<>();
				Set<TaskIdentifier> resextracttaskids = new LinkedHashSet<>();
				for (AAPT2CompilerInputTaskOption intaskoption : inputOption) {
					intaskoption.accept(new AAPT2CompilerInputTaskOption.Visitor() {
						@Override
						public void visit(FileLocation file) {
							inputs.add(new ResourceDirectoryAAPT2CompilerInputOption(file));
						}

						@Override
						public void visit(FileCollection file) {
							file.forEach(this::visit);
						}

						@Override
						public void visit(SakerPath path) {
							this.visit(ExecutionFileLocation
									.create(taskcontext.getTaskWorkingDirectoryPath().tryResolve(path)));
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
							TaskIdentifier taskid = ArtifactLocalizationUtils
									.createLocalizeArtifactsTaskIdentifier(config, coordinates);
							//TODO keep insertion order for input artifacts in input set
							taskcontext.startTask(taskid, taskfactory, null);

							//TODO this user.dir/.m2/repository should depend on an environment property or something
							String repohash = StringUtils
									.toHexString(FileUtils.hashString(Objects.toString(config.getLocalRepositoryPath(),
											System.getProperty("user.dir") + "/.m2/repository")));

							for (ArtifactCoordinates coords : coordinates) {
								AarEntryExtractWorkerTaskFactory extractworkertaskid = new AarEntryExtractWorkerTaskFactory(
										new ArtifactLocalizationOutputFileLocationStructuredTaskResult(taskid, coords),
										SakerPath.valueOf(repohash).resolve(coords.getGroupId(), coords.getArtifactId(),
												coords.getVersion()),
										"res", AarEntryExtractWorkerTaskFactory.OUTPUT_KIND_BUNDLE_STORAGE);
								TaskIdentifier extracttaskid = extractworkertaskid.createTaskId();
								taskcontext.startTask(extracttaskid, extractworkertaskid, null);
								resextracttaskids.add(extracttaskid);
							}
						}

					});
				}
				for (TaskIdentifier extracttaskid : resextracttaskids) {
					AarExtractTaskOutput extractout = (AarExtractTaskOutput) taskcontext.getTaskResult(extracttaskid);
					if (extractout == null) {
						//contains no res
						continue;
					}
					Set<FileLocation> resfiles;
					try {
						resfiles = extractout.getDirectoryFileLocations();
					} catch (AarEntryNotFoundException e) {
						//TODO warning or something
						continue;
					}
					if (resfiles == null) {
						//TODO warning or something
						continue;
					}
					inputs.add(new ResourcesAAPT2CompilerInputOption(resfiles));
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				EnumSet<AAPT2CompilerFlag> flags = EnumSet.noneOf(AAPT2CompilerFlag.class);
				addFlagIfSet(flags, AAPT2CompilerFlag.LEGACY, legacyOption);
				addFlagIfSet(flags, AAPT2CompilerFlag.NO_CRUNCH, noCrunchOption);
				addFlagIfSet(flags, AAPT2CompilerFlag.PSEUDO_LOCALIZE, pseudoLocalizeOption);
				AAPT2CompilationConfiguration config = new AAPT2CompilationConfiguration(flags);

				//TODO pre-extract aar input files

				AAPT2CompileWorkerTaskIdentifier workertaskid = new AAPT2CompileWorkerTaskIdentifier(compilationid);
				AAPT2CompileWorkerTaskFactory workertask = new AAPT2CompileWorkerTaskFactory(inputs, config);

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
}
