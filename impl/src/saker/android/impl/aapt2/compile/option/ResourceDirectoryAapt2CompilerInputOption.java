package saker.android.impl.aapt2.compile.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;

public class ResourceDirectoryAapt2CompilerInputOption implements Aapt2CompilerInputOption, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation resourceDirectory;

	/**
	 * For {@link Externalizable}.
	 */
	public ResourceDirectoryAapt2CompilerInputOption() {
	}

	public ResourceDirectoryAapt2CompilerInputOption(FileLocation resourceDirectory) {
		this.resourceDirectory = resourceDirectory;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visitResourceDirectory(resourceDirectory);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(resourceDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		resourceDirectory = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((resourceDirectory == null) ? 0 : resourceDirectory.hashCode());
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
		ResourceDirectoryAapt2CompilerInputOption other = (ResourceDirectoryAapt2CompilerInputOption) obj;
		if (resourceDirectory == null) {
			if (other.resourceDirectory != null)
				return false;
		} else if (!resourceDirectory.equals(other.resourceDirectory))
			return false;
		return true;
	}

}
