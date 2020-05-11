package saker.android.impl.aapt2.compile.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;

public class ResourcesAapt2CompilerInputOption implements Aapt2CompilerInputOption, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<FileLocation> resourceFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public ResourcesAapt2CompilerInputOption() {
	}

	public ResourcesAapt2CompilerInputOption(Set<FileLocation> resourceFiles) {
		this.resourceFiles = resourceFiles;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visitResources(resourceFiles);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, resourceFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		resourceFiles = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((resourceFiles == null) ? 0 : resourceFiles.hashCode());
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
		ResourcesAapt2CompilerInputOption other = (ResourcesAapt2CompilerInputOption) obj;
		if (resourceFiles == null) {
			if (other.resourceFiles != null)
				return false;
		} else if (!resourceFiles.equals(other.resourceFiles))
			return false;
		return true;
	}

}
