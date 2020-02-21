package saker.android.impl.d8.incremental;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;

public class InputFileInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected ContentDescriptor contents;
	protected SakerPath path;
	protected String descriptor;

	/**
	 * For {@link Externalizable}.
	 */
	public InputFileInformation() {
	}

	public InputFileInformation(ContentDescriptor contents, SakerPath path, String descriptor) {
		this.contents = contents;
		this.path = path;
		this.descriptor = descriptor;
	}

	public ContentDescriptor getContents() {
		return contents;
	}

	public SakerPath getPath() {
		return path;
	}

	public String getDescriptor() {
		return descriptor;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(contents);
		out.writeObject(path);
		out.writeObject(descriptor);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		contents = (ContentDescriptor) in.readObject();
		path = (SakerPath) in.readObject();
		descriptor = (String) in.readObject();
	}

	@Override
	public String toString() {
		return "InputFileInformation[path=" + path + "]";
	}
}