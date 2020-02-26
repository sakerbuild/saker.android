package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aar.AarClassesTaskOutput;
import saker.android.api.aar.AarEntryNotFoundException;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

final class AarNotFoundAarClassesTaskOutput implements AarClassesTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath inpath;

	/**
	 * For {@link Externalizable}.
	 */
	public AarNotFoundAarClassesTaskOutput() {
	}

	AarNotFoundAarClassesTaskOutput(SakerPath inpath) {
		this.inpath = inpath;
	}

	@Override
	public FileLocation getFileLocation() {
		throw new AarEntryNotFoundException("AAR file not found: " + inpath);
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