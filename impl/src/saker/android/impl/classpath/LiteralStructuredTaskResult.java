package saker.android.impl.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredTaskResult;

public class LiteralStructuredTaskResult implements StructuredTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	private Object value;

	/**
	 * For {@link Externalizable}.
	 */
	@Deprecated
	public LiteralStructuredTaskResult() {
	}

	//XXX remove this after some time and use StructuredTaskResult.createLiteral directly from saker.build 0.8.10 instead
	public static StructuredTaskResult create(Object value) {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_010) {
			return StructuredTaskResult.createLiteral(value);
		}
		return new LiteralStructuredTaskResult(value);
	}

	@Deprecated
	public LiteralStructuredTaskResult(Object value) {
		this.value = value;
	}

	@Override
	public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
		return value;
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return TaskResultDependencyHandle.create(value);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		LiteralStructuredTaskResult other = (LiteralStructuredTaskResult) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + value + "]";
	}

}
