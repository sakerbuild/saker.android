package saker.android.impl.aidl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.identifier.TaskIdentifier;
import saker.compiler.utils.api.CompilationIdentifier;

public class AidlWorkerTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private CompilationIdentifier compilationIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public AidlWorkerTaskIdentifier() {
	}

	public AidlWorkerTaskIdentifier(CompilationIdentifier compilationIdentifier) {
		this.compilationIdentifier = compilationIdentifier;
	}

	public CompilationIdentifier getCompilationIdentifier() {
		return compilationIdentifier;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(compilationIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		compilationIdentifier = (CompilationIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((compilationIdentifier == null) ? 0 : compilationIdentifier.hashCode());
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
		AidlWorkerTaskIdentifier other = (AidlWorkerTaskIdentifier) obj;
		if (compilationIdentifier == null) {
			if (other.compilationIdentifier != null)
				return false;
		} else if (!compilationIdentifier.equals(other.compilationIdentifier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + compilationIdentifier + "]";
	}

}
