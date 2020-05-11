package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.android.api.aar.AarExtractWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

final class EntryNotFoundAarExtractTaskOutput implements AarExtractWorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath inputPath;
	private String name;

	/**
	 * For {@link Externalizable}.
	 */
	public EntryNotFoundAarExtractTaskOutput() {
	}

	public EntryNotFoundAarExtractTaskOutput(SakerPath inputPath, String name) {
		this.inputPath = inputPath;
		this.name = name;
	}

	@Override
	public FileLocation getFileLocation() {
		throw failException();
	}

	@Override
	public Set<FileLocation> getDirectoryFileLocations() {
		throw failException();
	}

	private AarEntryNotFoundException failException() {
		return new AarEntryNotFoundException(name + " not found in AAR: " + inputPath);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputPath);
		out.writeObject(name);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputPath = (SakerPath) in.readObject();
		name = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputPath == null) ? 0 : inputPath.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		EntryNotFoundAarExtractTaskOutput other = (EntryNotFoundAarExtractTaskOutput) obj;
		if (inputPath == null) {
			if (other.inputPath != null)
				return false;
		} else if (!inputPath.equals(other.inputPath))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + inputPath + "]";
	}
}