package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.android.api.aar.AarExtractTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

final class AarNotFoundAarExtractTaskOutput implements AarExtractTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath inpath;

	/**
	 * For {@link Externalizable}.
	 */
	public AarNotFoundAarExtractTaskOutput() {
	}

	AarNotFoundAarExtractTaskOutput(SakerPath inpath) {
		this.inpath = inpath;
	}

	@Override
	public FileLocation toFileLocation() {
		return getFileLocation();
	}

	@Override
	public FileLocation getFileLocation() {
		throw failException();
	}

	private AarEntryNotFoundException failException() {
		return new AarEntryNotFoundException("AAR file not found: " + inpath);
	}

	@Override
	public Set<FileLocation> getDirectoryFileLocations() {
		throw failException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inpath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inpath = (SakerPath) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + inpath + "]";
	}
}