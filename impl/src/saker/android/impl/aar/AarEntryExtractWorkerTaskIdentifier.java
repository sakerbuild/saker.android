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

	/**
	 * For {@link Externalizable}.
	 */
	public AarEntryExtractWorkerTaskIdentifier() {
	}

	public AarEntryExtractWorkerTaskIdentifier(SakerPath outputPath, int outPathKind) {
		this.outputPath = outputPath;
		this.outPathKind = outPathKind;
	}

	public SakerPath getOutputPath() {
		return outputPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputPath);
		out.writeInt(outPathKind);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputPath = (SakerPath) in.readObject();
		outPathKind = in.readInt();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
