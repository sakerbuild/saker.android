package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.android.api.aar.AarExtractTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;

public final class ExecutionAarExtractTaskOutput implements AarExtractTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputFilePath;
	private Set<FileLocation> directoryFileLocations;

	/**
	 * For {@link Externalizable}.
	 */
	public ExecutionAarExtractTaskOutput() {
	}

	public ExecutionAarExtractTaskOutput(SakerPath outfilepath) {
		this.outputFilePath = outfilepath;
	}

	public ExecutionAarExtractTaskOutput(SakerPath outputFilePath, Set<FileLocation> directoryFileLocations) {
		this.outputFilePath = outputFilePath;
		this.directoryFileLocations = directoryFileLocations;
	}

	@Override
	public FileLocation getFileLocation() throws AarEntryNotFoundException {
		return ExecutionFileLocation.create(outputFilePath);
	}

	@Override
	public Set<FileLocation> getDirectoryFileLocations() {
		return directoryFileLocations;
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
		SerialUtils.writeExternalCollection(out, directoryFileLocations);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputFilePath = (SakerPath) in.readObject();
		directoryFileLocations = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directoryFileLocations == null) ? 0 : directoryFileLocations.hashCode());
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
		ExecutionAarExtractTaskOutput other = (ExecutionAarExtractTaskOutput) obj;
		if (directoryFileLocations == null) {
			if (other.directoryFileLocations != null)
				return false;
		} else if (!directoryFileLocations.equals(other.directoryFileLocations))
			return false;
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