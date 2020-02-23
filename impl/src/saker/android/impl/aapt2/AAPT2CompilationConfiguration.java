package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class AAPT2CompilationConfiguration implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected boolean pseudoLocalize;
	protected boolean noCrunch;
	protected boolean legacy;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2CompilationConfiguration() {
	}

	public boolean isLegacy() {
		return legacy;
	}

	public boolean isNoCrunch() {
		return noCrunch;
	}

	public boolean isPseudoLocalize() {
		return pseudoLocalize;
	}

	public void setPseudoLocalize(boolean pseudoLocalize) {
		this.pseudoLocalize = pseudoLocalize;
	}

	public void setNoCrunch(boolean noCrunch) {
		this.noCrunch = noCrunch;
	}

	public void setLegacy(boolean legacy) {
		this.legacy = legacy;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeBoolean(pseudoLocalize);
		out.writeBoolean(noCrunch);
		out.writeBoolean(legacy);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		pseudoLocalize = in.readBoolean();
		noCrunch = in.readBoolean();
		legacy = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (legacy ? 1231 : 1237);
		result = prime * result + (noCrunch ? 1231 : 1237);
		result = prime * result + (pseudoLocalize ? 1231 : 1237);
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
		AAPT2CompilationConfiguration other = (AAPT2CompilationConfiguration) obj;
		if (legacy != other.legacy)
			return false;
		if (noCrunch != other.noCrunch)
			return false;
		if (pseudoLocalize != other.pseudoLocalize)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "AAPT2CompilationConfiguration[pseudoLocalize=" + pseudoLocalize + ", noCrunch=" + noCrunch + ", legacy="
				+ legacy + "]";
	}
}