package saker.android.main.aar;

import saker.android.impl.aar.ExecutionAarClassesWorkerTaskIdentifier;
import saker.android.impl.aar.ExecutionFileAarClassesWorkerTaskFactory;
import saker.android.impl.aar.LocalAarClassesWorkerTaskIdentifier;
import saker.android.impl.aar.LocalFileAarClassesWorkerTaskFactory;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.nest.utils.FrontendTaskFactory;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

public class AarClassesTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.aar.classes";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "AAR", "Input" }, required = true)
			public FileLocationTaskOption inputOption;

			@SakerInput(value = { "Output" })
			public SakerPath outputPathOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				if (outputPathOption != null) {
					if (!outputPathOption.isForwardRelative()) {
						taskcontext.abortExecution(new InvalidPathFormatException(
								"Output path must be forward relative: " + outputPathOption));
						return null;
					}
					if (outputPathOption.getFileName() == null) {
						taskcontext.abortExecution(
								new InvalidPathFormatException("Output path has no file name: " + outputPathOption));
						return null;
					}
				}

				TaskIdentifier[] workertaskid = { null };
				TaskFactory<?>[] workertask = { null };

				FileLocation filelocation = TaskOptionUtils.toFileLocation(inputOption, taskcontext);
				filelocation.accept(new FileLocationVisitor() {
					@Override
					public void visit(ExecutionFileLocation loc) {
						SakerPath outputRelativePath;
						if (outputPathOption != null) {
							outputRelativePath = outputPathOption;
						} else {
							SakerPath inpath = loc.getPath();
							outputRelativePath = SakerPath
									.valueOf(StringUtils.toHexString(FileUtils.hashString(inpath.toString())))
									.resolve(inpath.getFileName());
						}
						ExecutionFileAarClassesWorkerTaskFactory wtask = new ExecutionFileAarClassesWorkerTaskFactory(
								loc, outputRelativePath);
						workertaskid[0] = new ExecutionAarClassesWorkerTaskIdentifier(outputRelativePath);
						workertask[0] = wtask;
					}

					@Override
					public void visit(LocalFileLocation loc) {
						if (outputPathOption == null) {
							SakerPath inpath = loc.getLocalPath();
							SakerPath outputRelativePath = SakerPath
									.valueOf(StringUtils.toHexString(FileUtils.hashString(inpath.toString())))
									.resolve(inpath.getFileName());

							LocalFileAarClassesWorkerTaskFactory wtask = new LocalFileAarClassesWorkerTaskFactory(loc,
									outputRelativePath);
							workertaskid[0] = new LocalAarClassesWorkerTaskIdentifier(outputRelativePath);
							workertask[0] = wtask;
							return;
						}
						ExecutionFileAarClassesWorkerTaskFactory wtask = new ExecutionFileAarClassesWorkerTaskFactory(
								loc, outputPathOption);
						workertaskid[0] = new ExecutionAarClassesWorkerTaskIdentifier(outputPathOption);
						workertask[0] = wtask;
					}
				});
				taskcontext.startTask(workertaskid[0], workertask[0], null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid[0]);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
