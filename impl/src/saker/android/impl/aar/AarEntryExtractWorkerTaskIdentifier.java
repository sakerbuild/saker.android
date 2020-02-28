package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;

public class AarEntryExtractWorkerTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected SakerPath outputPath;
	protected int outPathKind;
	protected String entryName;

	/**
	 * For {@link Externalizable}.
	 */
	public AarEntryExtractWorkerTaskIdentifier() {
	}

	public AarEntryExtractWorkerTaskIdentifier(SakerPath outputPath, int outPathKind, String entryName) {
		this.outputPath = outputPath;
		this.outPathKind = outPathKind;
		this.entryName = entryName;
	}

	public SakerPath getOutputPath() {
		return outputPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputPath);
		out.writeInt(outPathKind);
		out.writeObject(entryName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputPath = (SakerPath) in.readObject();
		outPathKind = in.readInt();
		entryName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entryName == null) ? 0 : entryName.hashCode());
		result = prime * result + outPathKind;
		result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
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
		AarEntryExtractWorkerTaskIdentifier other = (AarEntryExtractWorkerTaskIdentifier) obj;
		if (entryName == null) {
			if (other.entryName != null)
				return false;
		} else if (!entryName.equals(other.entryName))
			return false;
		if (outPathKind != other.outPathKind)
			return false;
		if (outputPath == null) {
			if (other.outputPath != null)
				return false;
		} else if (!outputPath.equals(other.outputPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + outputPath + "]";
	}

}
