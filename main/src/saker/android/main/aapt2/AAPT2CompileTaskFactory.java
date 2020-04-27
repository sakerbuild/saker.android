package saker.android.main.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.api.aapt2.compile.AAPT2CompileFrontendTaskOutput;
import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.aar.AAPT2AarCompileWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.compile.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.aapt2.compile.AAPT2CompilerFlag;
import saker.android.impl.aapt2.compile.option.AAPT2CompilerInputOption;
import saker.android.impl.aapt2.compile.option.ResourceDirectoryAAPT2CompilerInputOption;
import saker.android.impl.classpath.LiteralStructuredTaskResult;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocAAPT2CompileTaskOutput;
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
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ObjectUtils;
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
import saker.std.api.util.SakerStandardUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocAAPT2CompileTaskOutput.class))
@NestInformation("Performs Android resource compilation using the aapt2 tool from the Android SDK.\n"
		+ "The task will compile the given resources which can then be linked together using the "
		+ AAPT2LinkTaskFactory.TASK_NAME + "() task.\n"
		+ "The task can be used to compile the resources from AAR library dependencies as well.")
@NestParameterInformation(value = "Input",
		aliases = { "" },
		required = true,
		type = @NestTypeUsage(AAPT2CompilerInputTaskOption.class),
		info = @NestInformation("The input resources for the aapt2 compilation.\n"
				+ "The input can be one or multiple paths to Android resource directories, AAR bundles, "
				+ "resolved Maven AAR artifacts."))
@NestParameterInformation(value = "Identifier",
		type = @NestTypeUsage(CompilationIdentifierTaskOption.class),
		info = @NestInformation("Specifies an identifier for the compilation operation.\n"
				+ "The identifier will be used to uniquely identify this operation, and to generate the output directory name."))

@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))

@NestParameterInformation(value = "Legacy",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Treats errors that are permissible when using earlier versions of AAPT as warnings.\n"
				+ "Corresponds to the --legacy flag of aapt2."))
@NestParameterInformation(value = "NoCrunch",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Disables PNG processing.\n"
				+ "Use this option if you have already processed the PNG files, "
				+ "or if you are creating debug builds that do not require file size reduction. "
				+ "Enabling this option results in a faster execution, but increases the output file size.\n"
				+ "Corresponds to the --no-crunch flag of aapt2."))
@NestParameterInformation(value = "PseudoLocalize",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Generates pseudo-localized versions of default strings, such as en-XA and en-XB.\n"
				+ "Corresponds to the --pseudo-localize flag of aapt2."))
@NestParameterInformation(value = "Verbose",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Enable verbose logging.\n" + "Corresponds to the -v flag of aapt2."))
public class AAPT2CompileTaskFactory extends FrontendTaskFactory<AAPT2CompileFrontendTaskOutput> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.compile";

	@Override
	public ParameterizableTask<? extends AAPT2CompileFrontendTaskOutput> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<AAPT2CompileFrontendTaskOutput>() {

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
			public AAPT2CompileFrontendTaskOutput run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifierOption);
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}

				EnumSet<AAPT2CompilerFlag> flags = EnumSet.noneOf(AAPT2CompilerFlag.class);
				addFlagIfSet(flags, AAPT2CompilerFlag.LEGACY, legacyOption);
				addFlagIfSet(flags, AAPT2CompilerFlag.NO_CRUNCH, noCrunchOption);
				addFlagIfSet(flags, AAPT2CompilerFlag.PSEUDO_LOCALIZE, pseudoLocalizeOption);
				AAPT2CompilationConfiguration compilationconfig = new AAPT2CompilationConfiguration(flags);

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				Set<AAPT2CompilerInputOption> inputs = new LinkedHashSet<>();
				Collection<StructuredTaskResult> aarCompilations = new LinkedHashSet<>();
				for (AAPT2CompilerInputTaskOption intaskoption : inputOption) {
					intaskoption.accept(new AAPT2CompilerInputTaskOption.Visitor() {
						@Override
						public void visit(FileLocation file) {
							if (FileUtils.hasExtensionIgnoreCase(SakerStandardUtils.getFileLocationFileName(file),
									"aar")) {
								aarCompilations.add(startAarCompilationTask(taskcontext, compilationconfig,
										LiteralStructuredTaskResult.create(file)));
							} else {
								inputs.add(new ResourceDirectoryAAPT2CompilerInputOption(file));
							}
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

							for (ArtifactCoordinates coords : coordinates) {
								ArtifactLocalizationOutputFileLocationStructuredTaskResult artifactfilelocationtaskresult = new ArtifactLocalizationOutputFileLocationStructuredTaskResult(
										taskid, coords);

								aarCompilations.add(startAarCompilationTask(taskcontext, compilationconfig,
										artifactfilelocationtaskresult));
							}
						}

						private StructuredTaskResult startAarCompilationTask(TaskContext taskcontext,
								AAPT2CompilationConfiguration compilationconfig, StructuredTaskResult input) {
							AAPT2AarCompileWorkerTaskFactory aarcompilertask = new AAPT2AarCompileWorkerTaskFactory(
									input, compilationconfig);
							aarcompilertask.setSDKDescriptions(sdkdescriptions);
							aarcompilertask.setVerbose(verboseOption);
							taskcontext.startTask(aarcompilertask, aarcompilertask, null);

							return new SimpleStructuredObjectTaskResult(aarcompilertask);
						}
					});
				}

				AAPT2CompileWorkerTaskIdentifier workertaskid = new AAPT2CompileWorkerTaskIdentifier(compilationid);
				AAPT2CompileWorkerTaskFactory workertask = new AAPT2CompileWorkerTaskFactory(inputs, compilationconfig);

				workertask.setVerbose(verboseOption);
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				AAPT2CompileFrontendTaskOutputImpl result = new AAPT2CompileFrontendTaskOutputImpl(workertaskid,
						aarCompilations);
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

	private static final class AAPT2CompileFrontendTaskOutputImpl
			implements AAPT2CompileFrontendTaskOutput, StructuredObjectTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		private AAPT2CompileWorkerTaskIdentifier workerTaskId;
		private Collection<StructuredTaskResult> aarCompilations;

		/**
		 * For {@link Externalizable}.
		 */
		public AAPT2CompileFrontendTaskOutputImpl() {
		}

		AAPT2CompileFrontendTaskOutputImpl(AAPT2CompileWorkerTaskIdentifier workertaskid,
				Collection<StructuredTaskResult> aarCompilations) {
			this.aarCompilations = aarCompilations;
			this.workerTaskId = workertaskid;
		}

		@Override
		public TaskIdentifier getTaskIdentifier() {
			return workerTaskId;
		}

		@Override
		public TaskIdentifier getWorkerTaskIdentifier() {
			return workerTaskId;
		}

		@Override
		public Collection<StructuredTaskResult> getAarCompilations() {
			return aarCompilations;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(workerTaskId);
			SerialUtils.writeExternalCollection(out, aarCompilations);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			workerTaskId = SerialUtils.readExternalObject(in);
			aarCompilations = SerialUtils.readExternalImmutableLinkedHashSet(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((aarCompilations == null) ? 0 : aarCompilations.hashCode());
			result = prime * result + ((workerTaskId == null) ? 0 : workerTaskId.hashCode());
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
			AAPT2CompileFrontendTaskOutputImpl other = (AAPT2CompileFrontendTaskOutputImpl) obj;
			if (aarCompilations == null) {
				if (other.aarCompilations != null)
					return false;
			} else if (!aarCompilations.equals(other.aarCompilations))
				return false;
			if (workerTaskId == null) {
				if (other.workerTaskId != null)
					return false;
			} else if (!workerTaskId.equals(other.workerTaskId))
				return false;
			return true;
		}

	}
}
