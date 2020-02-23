package saker.android.main.d8;

import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.D8WorkerTaskIdentifier;
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

public class D8TaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.d8";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = "Directory", required = true)
			public SakerPath directory;

			@SakerInput("Identifier")
			public CompilationIdentifierTaskOption identifier;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifier);
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				D8WorkerTaskIdentifier workertaskid = new D8WorkerTaskIdentifier(compilationid);
				D8WorkerTaskFactory workertask = new D8WorkerTaskFactory();
				workertask.setClassDirectory(directory);
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
