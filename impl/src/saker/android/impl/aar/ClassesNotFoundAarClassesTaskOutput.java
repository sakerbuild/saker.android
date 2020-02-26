package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aar.AarClassesNotFoundException;
import saker.android.api.aar.AarClassesTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

final class ClassesNotFoundAarClassesTaskOutput implements AarClassesTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath inputPath;

	/**
	 * For {@link Externalizable}.
	 */
	public ClassesNotFoundAarClassesTaskOutput() {
	}

	ClassesNotFoundAarClassesTaskOutput(SakerPath inpath) {
		this.inputPath = inpath;
	}

	@Override
	public FileLocation getFileLocation() {
		throw new AarClassesNotFoundException("classes.jar not found in AAR: " + inputPath);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputPath = (SakerPath) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + inputPath + "]";
	}
}