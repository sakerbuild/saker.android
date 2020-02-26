package saker.android.impl.aar;

import java.io.Externalizable;

import saker.android.api.aar.AarClassesTaskOutput;
import saker.android.main.aar.AarClassesTaskFactory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.std.api.file.location.FileLocation;

public class AarClassesWorkerTaskFactory extends AarEntryExporterWorkerTaskFactoryBase<AarClassesTaskOutput> {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public AarClassesWorkerTaskFactory() {
	}

	public AarClassesWorkerTaskFactory(FileLocation inputFile, SakerPath outputRelativePath, int outPathKind) {
		super(inputFile, outputRelativePath, outPathKind);
	}

	@Override
	public AarClassesTaskOutput run(TaskContext taskcontext) throws Exception {
		taskcontext.setStandardOutDisplayIdentifier(AarClassesTaskFactory.TASK_NAME);
		return super.run(taskcontext);
	}

	@Override
	protected AarClassesTaskOutput createExecutionResult(SakerPath outfilepath) {
		return new ExecutionAarClassesTaskOutput(outfilepath);
	}

	@Override
	protected AarClassesTaskOutput createLocalResult(SakerPath outputlocalsakerpath) {
		return new LocalAarClassesTaskOutput(outputlocalsakerpath);
	}

	@Override
	protected AarClassesTaskOutput executionAarNotFound(SakerPath inputFile) {
		return new AarNotFoundAarClassesTaskOutput(inputFile);
	}

	@Override
	protected AarClassesTaskOutput localAarNotFound(SakerPath localinputpath) {
		return new AarNotFoundAarClassesTaskOutput(localinputpath);
	}

	@Override
	protected AarClassesTaskOutput handleLocalFile(TaskContext taskcontext, SakerPath localpath) throws Exception {
		ByteArrayRegion classesbytes = getLocalFileClassesJarBytes(localpath);
		if (classesbytes == null) {
			return new EntryNotFoundAarClassesTaskOutput(localpath, "classes.jar");
		}
		return addResultFile(taskcontext, classesbytes, ".classes.jar");
	}

	@Override
	protected AarClassesTaskOutput handleExecutionFile(TaskContext taskcontext, SakerFile f) throws Exception {
		ByteArrayRegion classesbytes = getFileClassesJarBytes(f);
		if (classesbytes == null) {
			return new EntryNotFoundAarClassesTaskOutput(f.getSakerPath(), "classes.jar");
		}
		return addResultFile(taskcontext, classesbytes, ".classes.jar");
	}

}
