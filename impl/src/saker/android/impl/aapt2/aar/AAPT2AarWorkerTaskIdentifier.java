package saker.android.impl.aapt2.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;

public class AAPT2AarWorkerTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputRelativePath;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2AarWorkerTaskIdentifier() {
	}

	public AAPT2AarWorkerTaskIdentifier(SakerPath outputRelativePath) {
		this.outputRelativePath = outputRelativePath;
	}

	public SakerPath getOutputRelativePath() {
		return outputRelativePath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputRelativePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputRelativePath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputRelativePath == null) ? 0 : outputRelativePath.hashCode());
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
		AAPT2AarWorkerTaskIdentifier other = (AAPT2AarWorkerTaskIdentifier) obj;
		if (outputRelativePath == null) {
			if (other.outputRelativePath != null)
				return false;
		} else if (!outputRelativePath.equals(other.outputRelativePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "AAPT2AarWorkerTaskIdentifier[outputRelativePath=" + outputRelativePath + "]";
	}
}
