package saker.android.impl.d8.incremental;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;

public class InputFileInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected SakerPath path;
	protected String descriptor;

	/**
	 * For {@link Externalizable}.
	 */
	public InputFileInformation() {
	}

	public InputFileInformation(SakerPath path, String descriptor) {
		this.path = path;
		this.descriptor = descriptor;
	}

	public SakerPath getPath() {
		return path;
	}

	public String getDescriptor() {
		return descriptor;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
		out.writeObject(descriptor);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = (SakerPath) in.readObject();
		descriptor = (String) in.readObject();
	}

	@Override
	public String toString() {
		return "InputFileInformation[path=" + path + "]";
	}
}