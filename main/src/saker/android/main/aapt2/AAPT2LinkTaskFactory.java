package saker.android.main.aapt2;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.impl.aapt2.AAPT2LinkWorkerTaskFactory;
import saker.android.impl.aapt2.AAPT2LinkWorkerTaskIdentifier;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.trace.BuildTrace;
import saker.nest.utils.FrontendTaskFactory;

public class AAPT2LinkTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.link";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public AAPT2CompileTaskOutput input;

			@SakerInput(value = "Manifest", required = true)
			public SakerPath manifestOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				AAPT2LinkWorkerTaskIdentifier workertaskid = new AAPT2LinkWorkerTaskIdentifier(input.getIdentifier());
				AAPT2LinkWorkerTaskFactory workertask = new AAPT2LinkWorkerTaskFactory();
				workertask.setSDKDescriptions(input.getSDKs());
				workertask.setInputFiles(input.getOutputPaths());
				workertask.setManifest(taskcontext.getTaskWorkingDirectoryPath().tryResolve(manifestOption));

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
