package saker.android.impl.ndk.strip;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.ndk.strip.StripWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class StripTaskOutputImpl implements StripWorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public StripTaskOutputImpl() {
	}

	public StripTaskOutputImpl(SakerPath path) {
		this.path = path;
	}

	@Override
	public SakerPath getPath() {
		return path;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = SerialUtils.readExternalObject(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + path + "]";
	}

}