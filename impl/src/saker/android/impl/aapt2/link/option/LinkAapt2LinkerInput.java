package saker.android.impl.aapt2.link.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aapt2.link.Aapt2LinkTaskOutput;

public class LinkAapt2LinkerInput implements Aapt2LinkerInput, Externalizable {
	private static final long serialVersionUID = 1L;

	private Aapt2LinkTaskOutput linkOutput;

	/**
	 * For {@link Externalizable}.
	 */
	public LinkAapt2LinkerInput() {
	}

	public LinkAapt2LinkerInput(Aapt2LinkTaskOutput output) {
		this.linkOutput = output;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(linkOutput);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(linkOutput);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		linkOutput = (Aapt2LinkTaskOutput) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((linkOutput == null) ? 0 : linkOutput.hashCode());
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
		LinkAapt2LinkerInput other = (LinkAapt2LinkerInput) obj;
		if (!linkOutput.equals(other.linkOutput))
			return false;
		return true;
	}

}
