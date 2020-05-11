package saker.android.impl.aapt2.compile;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.thirdparty.saker.util.io.SerialUtils;

public class Aapt2CompilationConfiguration implements Externalizable {
	private static final long serialVersionUID = 1L;

	protected Set<Aapt2CompilerFlag> flags;

	/**
	 * For {@link Externalizable}.
	 */
	public Aapt2CompilationConfiguration() {
	}

	public Aapt2CompilationConfiguration(Set<Aapt2CompilerFlag> flags) {
		this.flags = flags;
	}

	public void setFlags(Set<Aapt2CompilerFlag> flags) {
		this.flags = flags;
	}

	public Set<Aapt2CompilerFlag> getFlags() {
		return flags;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, flags);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		flags = SerialUtils.readExternalEnumSetCollection(Aapt2CompilerFlag.class, in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((flags == null) ? 0 : flags.hashCode());
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
		Aapt2CompilationConfiguration other = (Aapt2CompilationConfiguration) obj;
		if (flags == null) {
			if (other.flags != null)
				return false;
		} else if (!flags.equals(other.flags))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + flags + "]";
	}
}