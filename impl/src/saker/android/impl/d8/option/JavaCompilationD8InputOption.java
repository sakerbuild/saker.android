package saker.android.impl.d8.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.java.compiler.api.compile.JavaCompilationWorkerTaskIdentifier;

public class JavaCompilationD8InputOption implements D8InputOption, Externalizable {
	private static final long serialVersionUID = 1L;

	private JavaCompilationWorkerTaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public JavaCompilationD8InputOption() {
	}

	public JavaCompilationD8InputOption(JavaCompilationWorkerTaskIdentifier taskId) {
		this.taskId = taskId;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(taskId);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		JavaCompilationD8InputOption other = (JavaCompilationD8InputOption) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskId + "]";
	}

}
