package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;

final class AAPT2LinkTaskOutputImpl implements AAPT2LinkTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath resourceAPK;
	private SakerPath rJavaSourceDirectory;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2LinkTaskOutputImpl() {
	}

	public AAPT2LinkTaskOutputImpl(SakerPath resourceAPK, SakerPath rJavaSourceDirectory) {
		this.resourceAPK = resourceAPK;
		this.rJavaSourceDirectory = rJavaSourceDirectory;
	}

	@Override
	public SakerPath getResourceAPK() {
		return resourceAPK;
	}

	@Override
	public SakerPath getRJavaSourceDirectory() {
		return rJavaSourceDirectory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(rJavaSourceDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		rJavaSourceDirectory = (SakerPath) in.readObject();
	}

}