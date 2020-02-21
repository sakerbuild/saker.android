package saker.android.impl.d8.incremental;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class OutputFileInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected ContentDescriptor contents;
	protected SakerPath path;
	protected String descriptor;
	protected Set<String> descriptors;

	/**
	 * For {@link Externalizable}.
	 */
	public OutputFileInformation() {
	}

	public OutputFileInformation(ContentDescriptor contents, SakerPath path, String descriptor,
			Set<String> descriptors) {
		this.contents = contents;
		this.path = path;
		this.descriptor = descriptor;
		this.descriptors = descriptors;
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

	public Set<String> getDescriptors() {
		return descriptors;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(contents);
		out.writeObject(path);
		out.writeObject(descriptor);
		SerialUtils.writeExternalCollection(out, descriptors);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		contents = (ContentDescriptor) in.readObject();
		path = (SakerPath) in.readObject();
		descriptor = (String) in.readObject();
		descriptors = SerialUtils.readExternalImmutableNavigableSet(in);
	}

	@Override
	public String toString() {
		return "OutputFileInformation[path=" + path + "]";
	}

}