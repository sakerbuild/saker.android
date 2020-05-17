package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.android.api.aar.AarExtractWorkerTaskOutput;
import saker.android.api.aar.exc.AarEntryNotFoundException;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;

public final class LocalAarExtractTaskOutput implements AarExtractWorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputLocalFilePath;
	private Set<FileLocation> directoryFileLocations;

	/**
	 * For {@link Externalizable}.
	 */
	public LocalAarExtractTaskOutput() {
	}

	public LocalAarExtractTaskOutput(SakerPath outfilepath) {
		this.outputLocalFilePath = outfilepath;
	}

	public LocalAarExtractTaskOutput(SakerPath outputLocalFilePath, Set<FileLocation> directoryFileLocations) {
		this.outputLocalFilePath = outputLocalFilePath;
		this.directoryFileLocations = directoryFileLocations;
	}

	@Override
	public FileLocation getFileLocation() throws AarEntryNotFoundException {
		return LocalFileLocation.create(outputLocalFilePath);
	}

	public SakerPath getLocalPath() {
		return outputLocalFilePath;
	}

	@Override
	public Set<FileLocation> getDirectoryFileLocations() {
		return directoryFileLocations;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputLocalFilePath);
		SerialUtils.writeExternalCollection(out, directoryFileLocations);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputLocalFilePath = (SakerPath) in.readObject();
		directoryFileLocations = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directoryFileLocations == null) ? 0 : directoryFileLocations.hashCode());
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
		LocalAarExtractTaskOutput other = (LocalAarExtractTaskOutput) obj;
		if (directoryFileLocations == null) {
			if (other.directoryFileLocations != null)
				return false;
		} else if (!directoryFileLocations.equals(other.directoryFileLocations))
			return false;
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