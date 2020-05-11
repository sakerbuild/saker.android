package saker.android.impl.aapt2.link.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.android.api.aapt2.aar.Aapt2AarCompileTaskOutput;

public class AarCompilationAapt2LinkerInput implements Aapt2LinkerInput, Externalizable {
	private static final long serialVersionUID = 1L;

	private Aapt2AarCompileTaskOutput compilationOutput;

	/**
	 * For {@link Externalizable}.
	 */
	public AarCompilationAapt2LinkerInput() {
	}

	public AarCompilationAapt2LinkerInput(Aapt2AarCompileTaskOutput compilationOutput) {
		Objects.requireNonNull(compilationOutput, "compilation output");
		this.compilationOutput = compilationOutput;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(compilationOutput);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(compilationOutput);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		compilationOutput = (Aapt2AarCompileTaskOutput) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((compilationOutput == null) ? 0 : compilationOutput.hashCode());
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
		AarCompilationAapt2LinkerInput other = (AarCompilationAapt2LinkerInput) obj;
		if (!compilationOutput.equals(other.compilationOutput))
			return false;
		return true;
	}

}
