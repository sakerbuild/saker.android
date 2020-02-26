package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aar.AarClassesNotFoundException;
import saker.android.api.aar.AarClassesTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;

final class ExecutionAarClassesTaskOutput implements AarClassesTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputFilePath;

	/**
	 * For {@link Externalizable}.
	 */
	public ExecutionAarClassesTaskOutput() {
	}

	ExecutionAarClassesTaskOutput(SakerPath outfilepath) {
		this.outputFilePath = outfilepath;
	}

	@Override
	public FileLocation getFileLocation() throws AarClassesNotFoundException {
		return ExecutionFileLocation.create(outputFilePath);
	}

	public SakerPath getPath() {
		return outputFilePath;
	}

	public SakerPath toSakerPath() {
		return outputFilePath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputFilePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputFilePath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputFilePath == null) ? 0 : outputFilePath.hashCode());
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
		ExecutionAarClassesTaskOutput other = (ExecutionAarClassesTaskOutput) obj;
		if (outputFilePath == null) {
			if (other.outputFilePath != null)
				return false;
		} else if (!outputFilePath.equals(other.outputFilePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + outputFilePath + "]";
	}

}