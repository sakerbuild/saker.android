package saker.android.impl.aapt2.link.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.std.api.file.location.FileLocation;

public class FileAAPT2LinkerInput implements AAPT2LinkerInput, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation file;

	/**
	 * For {@link Externalizable}.
	 */
	public FileAAPT2LinkerInput() {
	}

	public FileAAPT2LinkerInput(FileLocation inputFile) {
		this.file = inputFile;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(file);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(file);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		file = (FileLocation) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
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
		FileAAPT2LinkerInput other = (FileAAPT2LinkerInput) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		return true;
	}

}
