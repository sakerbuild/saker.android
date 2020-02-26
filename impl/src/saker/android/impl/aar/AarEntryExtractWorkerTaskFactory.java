package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.std.api.file.location.FileLocation;

public class AarEntryExtractWorkerTaskFactory extends AarEntryExporterWorkerTaskFactoryBase<AarEntryExtractTaskOutput> {
	private static final long serialVersionUID = 1L;

	private String entryName;

	/**
	 * For {@link Externalizable}.
	 */
	public AarEntryExtractWorkerTaskFactory() {
	}

	public AarEntryExtractWorkerTaskFactory(FileLocation inputFile, SakerPath outputRelativePath, int outPathKind,
			String entryName) {
		super(inputFile, outputRelativePath, outPathKind);
		this.entryName = entryName;
	}

	@Override
	public AarEntryExtractTaskOutput run(TaskContext taskcontext) throws Exception {
		//TODO some display identifier
		return super.run(taskcontext);
	}

	@Override
	protected AarEntryExtractTaskOutput createExecutionResult(SakerPath outfilepath) {
		return new ExecutionAarClassesTaskOutput(outfilepath);
	}

	@Override
	protected AarEntryExtractTaskOutput createLocalResult(SakerPath outputlocalsakerpath) {
		return new LocalAarClassesTaskOutput(outputlocalsakerpath);
	}

	@Override
	protected AarEntryExtractTaskOutput executionAarNotFound(SakerPath inputFile) {
		return new AarNotFoundAarClassesTaskOutput(inputFile);
	}

	@Override
	protected AarEntryExtractTaskOutput localAarNotFound(SakerPath localinputpath) {
		return new AarNotFoundAarClassesTaskOutput(localinputpath);
	}

	@Override
	protected AarEntryExtractTaskOutput handleLocalFile(TaskContext taskcontext, SakerPath localpath) throws Exception {
		ByteArrayRegion classesbytes = getLocalFileClassesJarBytes(localpath);
		if (classesbytes == null) {
			return new EntryNotFoundAarClassesTaskOutput(localpath, entryName);
		}
		return addResultFile(taskcontext, classesbytes, entryName);
	}

	@Override
	protected AarEntryExtractTaskOutput handleExecutionFile(TaskContext taskcontext, SakerFile f) throws Exception {
		ByteArrayRegion classesbytes = getFileClassesJarBytes(f);
		if (classesbytes == null) {
			return new EntryNotFoundAarClassesTaskOutput(f.getSakerPath(), entryName);
		}
		return addResultFile(taskcontext, classesbytes, entryName);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(entryName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		entryName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((entryName == null) ? 0 : entryName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AarEntryExtractWorkerTaskFactory other = (AarEntryExtractWorkerTaskFactory) obj;
		if (entryName == null) {
			if (other.entryName != null)
				return false;
		} else if (!entryName.equals(other.entryName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[entryName=" + entryName + ", inputFile=" + inputFile
				+ ", outputRelativePath=" + outputRelativePath + "]";
	}

}
