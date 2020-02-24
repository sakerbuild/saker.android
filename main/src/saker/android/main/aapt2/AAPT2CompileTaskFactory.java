package saker.android.main.aapt2;

import java.util.EnumSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.AAPT2CompilationConfiguration;
import saker.android.impl.aapt2.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.aapt2.AAPT2CompilerFlag;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

public class AAPT2CompileTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.compile";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Directory" }, required = true)
			public SakerPath directoryOption;

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

				AAPT2CompileWorkerTaskIdentifier workertaskid = new AAPT2CompileWorkerTaskIdentifier(compilationid);
				AAPT2CompileWorkerTaskFactory workertask = new AAPT2CompileWorkerTaskFactory();

				workertask.setResourceDirectory(directoryOption);
				workertask.setConfiguration(config);
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
