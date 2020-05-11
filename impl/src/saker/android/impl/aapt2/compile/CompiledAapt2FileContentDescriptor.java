package saker.android.impl.aapt2.compile;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;

public class CompiledAapt2FileContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private ContentDescriptor inputContents;
	private SakerPath outputDirectoryRelativePath;
	private String outputFileName;

	/**
	 * For {@link Externalizable}.
	 */
	public CompiledAapt2FileContentDescriptor() {
	}

	public CompiledAapt2FileContentDescriptor(ContentDescriptor inputContents, SakerPath outputDirectoryRelativePath,
			String outputFileName) {
		this.inputContents = inputContents;
		this.outputDirectoryRelativePath = outputDirectoryRelativePath;
		this.outputFileName = outputFileName;
	}

	@Override
	public boolean isChanged(ContentDescriptor previouscontent) {
		if (!(previouscontent instanceof CompiledAapt2FileContentDescriptor)) {
			return true;
		}
		CompiledAapt2FileContentDescriptor cd = (CompiledAapt2FileContentDescriptor) previouscontent;
		if (this.inputContents.isChanged(cd.inputContents)) {
			return true;
		}
		if (!this.outputDirectoryRelativePath.equals(cd.outputDirectoryRelativePath)
				|| !this.outputFileName.equals(cd.outputFileName)) {
			return true;
		}
		return false;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputContents);
		out.writeObject(outputDirectoryRelativePath);
		out.writeObject(outputFileName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputContents = (ContentDescriptor) in.readObject();
		outputDirectoryRelativePath = (SakerPath) in.readObject();
		outputFileName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputContents == null) ? 0 : inputContents.hashCode());
		result = prime * result + ((outputDirectoryRelativePath == null) ? 0 : outputDirectoryRelativePath.hashCode());
		result = prime * result + ((outputFileName == null) ? 0 : outputFileName.hashCode());
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
		CompiledAapt2FileContentDescriptor other = (CompiledAapt2FileContentDescriptor) obj;
		if (inputContents == null) {
			if (other.inputContents != null)
				return false;
		} else if (!inputContents.equals(other.inputContents))
			return false;
		if (outputDirectoryRelativePath == null) {
			if (other.outputDirectoryRelativePath != null)
				return false;
		} else if (!outputDirectoryRelativePath.equals(other.outputDirectoryRelativePath))
			return false;
		if (outputFileName == null) {
			if (other.outputFileName != null)
				return false;
		} else if (!outputFileName.equals(other.outputFileName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + inputContents + " --- " + outputDirectoryRelativePath + "/"
				+ outputFileName + "]";
	}

}
