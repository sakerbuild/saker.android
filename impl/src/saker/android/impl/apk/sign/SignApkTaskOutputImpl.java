package saker.android.impl.apk.sign;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.apk.sign.SignApkWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public final class SignApkTaskOutputImpl implements SignApkWorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public SignApkTaskOutputImpl() {
	}

	public SignApkTaskOutputImpl(SakerPath path) {
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		SignApkTaskOutputImpl other = (SignApkTaskOutputImpl) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + path + "]";
	}

}