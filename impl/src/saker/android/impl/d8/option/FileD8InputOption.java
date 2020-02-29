package saker.android.impl.d8.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;

public class FileD8InputOption implements D8InputOption, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation file;

	/**
	 * For {@link Externalizable}.
	 */
	public FileD8InputOption() {
	}

	public FileD8InputOption(FileLocation file) {
		this.file = file;
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
		file = SerialUtils.readExternalObject(in);
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
		FileD8InputOption other = (FileD8InputOption) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[file=" + file + "]";
	}

}
