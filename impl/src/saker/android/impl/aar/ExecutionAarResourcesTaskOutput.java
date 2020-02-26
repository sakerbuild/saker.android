package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;

final class ExecutionAarResourcesTaskOutput implements AarResourcesTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath outputDirectoryPath;
	private Set<FileLocation> resourceFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public ExecutionAarResourcesTaskOutput() {
	}

	public ExecutionAarResourcesTaskOutput(SakerPath outputDirectoryPath, Set<FileLocation> resourceFiles) {
		this.outputDirectoryPath = outputDirectoryPath;
		this.resourceFiles = resourceFiles;
	}

	@Override
	public FileLocation getFileLocation() throws AarEntryNotFoundException {
		return ExecutionFileLocation.create(outputDirectoryPath);
	}

	@Override
	public Set<FileLocation> getResourceFiles() throws AarEntryNotFoundException {
		return resourceFiles;
	}

	public SakerPath getPath() {
		return outputDirectoryPath;
	}

	public SakerPath toSakerPath() {
		return outputDirectoryPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputDirectoryPath);
		SerialUtils.writeExternalCollection(out, resourceFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputDirectoryPath = (SakerPath) in.readObject();
		resourceFiles = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputDirectoryPath == null) ? 0 : outputDirectoryPath.hashCode());
		result = prime * result + ((resourceFiles == null) ? 0 : resourceFiles.hashCode());
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
		ExecutionAarResourcesTaskOutput other = (ExecutionAarResourcesTaskOutput) obj;
		if (outputDirectoryPath == null) {
			if (other.outputDirectoryPath != null)
				return false;
		} else if (!outputDirectoryPath.equals(other.outputDirectoryPath))
			return false;
		if (resourceFiles == null) {
			if (other.resourceFiles != null)
				return false;
		} else if (!resourceFiles.equals(other.resourceFiles))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + outputDirectoryPath + "]";
	}

}