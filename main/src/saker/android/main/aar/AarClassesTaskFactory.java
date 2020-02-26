package saker.android.main.aar;

import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.AarEntryExtractWorkerTaskIdentifier;
import saker.android.impl.aar.AarEntryExporterWorkerTaskFactoryBase;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
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

	private static final SakerPath PATH_OUTPUT_BASE_DIRECTORY = SakerPath.valueOf("saker.aar.extract");

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

				AarEntryExporterWorkerTaskFactoryBase<?>[] workertask = { null };

				FileLocation filelocation = TaskOptionUtils.toFileLocation(inputOption, taskcontext);
				filelocation.accept(new FileLocationVisitor() {
					@Override
					public void visit(ExecutionFileLocation loc) {
						SakerPath outputRelativePath;
						if (outputPathOption != null) {
							outputRelativePath = outputPathOption;
						} else {
							SakerPath inpath = loc.getPath();
							outputRelativePath = SakerPath.valueOf(inpath.getFileName())
									.resolve(StringUtils.toHexString(FileUtils.hashString(inpath.toString())));
						}
						AarEntryExtractWorkerTaskFactory wtask = new AarEntryExtractWorkerTaskFactory(loc,
								PATH_OUTPUT_BASE_DIRECTORY.resolve(outputRelativePath),
								AarEntryExtractWorkerTaskFactory.OUTPUT_KIND_EXECUTION,
								AarEntryExporterWorkerTaskFactoryBase.ENTRY_NAME_CLASSES_JAR);
						workertask[0] = wtask;
					}

					@Override
					public void visit(LocalFileLocation loc) {
						if (outputPathOption == null) {
							SakerPath inpath = loc.getLocalPath();
							SakerPath outputRelativePath = SakerPath.valueOf(inpath.getFileName())
									.resolve(StringUtils.toHexString(FileUtils.hashString(inpath.toString())));

							AarEntryExtractWorkerTaskFactory wtask = new AarEntryExtractWorkerTaskFactory(loc,
									PATH_OUTPUT_BASE_DIRECTORY.resolve(outputRelativePath),
									AarEntryExtractWorkerTaskFactory.OUTPUT_KIND_BUNDLE_STORAGE,
									AarEntryExporterWorkerTaskFactoryBase.ENTRY_NAME_CLASSES_JAR);
							workertask[0] = wtask;
							return;
						}
						AarEntryExtractWorkerTaskFactory wtask = new AarEntryExtractWorkerTaskFactory(loc,
								PATH_OUTPUT_BASE_DIRECTORY.resolve(outputPathOption),
								AarEntryExtractWorkerTaskFactory.OUTPUT_KIND_EXECUTION,
								AarEntryExporterWorkerTaskFactoryBase.ENTRY_NAME_CLASSES_JAR);
						workertask[0] = wtask;
					}
				});
				TaskIdentifier workertaskid = new AarEntryExtractWorkerTaskIdentifier(workertask[0].getOutputRelativePath(),
						workertask[0].getOutPathKind());
				taskcontext.startTask(workertaskid, workertask[0], null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
