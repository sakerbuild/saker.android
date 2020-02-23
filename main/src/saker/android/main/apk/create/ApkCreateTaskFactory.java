package saker.android.main.apk.create;

import saker.android.main.apk.create.option.ApkClassesTaskOption;
import saker.android.main.apk.create.option.ApkResourcesTaskOption;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.trace.BuildTrace;
import saker.nest.utils.FrontendTaskFactory;
import saker.zip.api.create.ZipCreationTaskBuilder;

public class ApkCreateTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.apk.create";

	private static final SakerPath DEFAULT_BUILD_SUBDIRECTORY_PATH = SakerPath.valueOf(TASK_NAME);

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "Resources" }, required = true)
			public ApkResourcesTaskOption resourcesOption;

			@SakerInput(value = { "Classes" })
			public ApkClassesTaskOption classesOption;

			@SakerInput(value = { "Output" })
			public SakerPath outputOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				SakerPath outputpath = outputOption;
				if (outputpath != null) {
					if (!outputpath.isForwardRelative()) {
						taskcontext.abortExecution(new InvalidPathFormatException(
								"Signed APK output path must be forward relative: " + outputpath));
						return null;
					}
				} else {
					outputpath = SakerPath.valueOf("default.apk");
				}

				SakerPath builddirpath = SakerPathFiles.requireBuildDirectoryPath(taskcontext)
						.resolve(DEFAULT_BUILD_SUBDIRECTORY_PATH);

				ZipCreationTaskBuilder taskbuilder = ZipCreationTaskBuilder.newBuilder();
				taskbuilder.setOutputPath(builddirpath.resolve(outputpath));
				resourcesOption.applyTo(taskbuilder);
				if (classesOption != null) {
					classesOption.applyTo(taskbuilder);
				}
				TaskIdentifier workertaskid = taskbuilder.buildTaskIdentifier();
				TaskFactory<?> workertask = taskbuilder.buildTaskFactory();

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
