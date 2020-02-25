package saker.android.main.classpath;

import saker.android.impl.classpath.AndroidPlatformClassPathReference;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.trace.BuildTrace;
import saker.nest.utils.FrontendTaskFactory;

public class AndroidPlatformClassPathTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.classpath.platform";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput("CoreLambdaStubs")
			public boolean includeCoreLambdaStubs = true;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_CONFIGURATION);
				}

				return new AndroidPlatformClassPathReference(includeCoreLambdaStubs);
			}
		};
	}

}
