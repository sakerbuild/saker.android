package saker.android.impl.aapt2.link.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.api.aapt2.compile.Aapt2CompileWorkerTaskOutput;

public class CompilationAapt2LinkerInput implements Aapt2LinkerInput, Externalizable {
	private static final long serialVersionUID = 1L;

	private Aapt2CompileWorkerTaskOutput compilationOutput;

	/**
	 * For {@link Externalizable}.
	 */
	public CompilationAapt2LinkerInput() {
	}

	public CompilationAapt2LinkerInput(Aapt2CompileWorkerTaskOutput compilationOutput) {
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
		compilationOutput = (Aapt2CompileWorkerTaskOutput) in.readObject();
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
		CompilationAapt2LinkerInput other = (CompilationAapt2LinkerInput) obj;
		if (!compilationOutput.equals(other.compilationOutput))
			return false;
		return true;
	}

}
