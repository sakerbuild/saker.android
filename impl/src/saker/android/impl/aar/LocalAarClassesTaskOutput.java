package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aar.AarClassesTaskOutput;
import saker.android.api.aar.AarEntryNotFoundException;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;

final class LocalAarClassesTaskOutput implements AarClassesTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputLocalFilePath;

	/**
	 * For {@link Externalizable}.
	 */
	public LocalAarClassesTaskOutput() {
	}

	LocalAarClassesTaskOutput(SakerPath outfilepath) {
		this.outputLocalFilePath = outfilepath;
	}

	@Override
	public FileLocation getFileLocation() throws AarEntryNotFoundException {
		return LocalFileLocation.create(outputLocalFilePath);
	}

	public SakerPath getLocalPath() {
		return outputLocalFilePath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputLocalFilePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputLocalFilePath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputLocalFilePath == null) ? 0 : outputLocalFilePath.hashCode());
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
		LocalAarClassesTaskOutput other = (LocalAarClassesTaskOutput) obj;
		if (outputLocalFilePath == null) {
			if (other.outputLocalFilePath != null)
				return false;
		} else if (!outputLocalFilePath.equals(other.outputLocalFilePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + outputLocalFilePath + "]";
	}

}