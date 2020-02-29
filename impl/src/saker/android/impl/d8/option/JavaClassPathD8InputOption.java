package saker.android.impl.d8.option;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.java.compiler.api.classpath.JavaClassPath;

public class JavaClassPathD8InputOption implements D8InputOption, Externalizable {
	private static final long serialVersionUID = 1L;

	private JavaClassPath classPath;

	/**
	 * For {@link Externalizable}.
	 */
	public JavaClassPathD8InputOption() {
	}

	public JavaClassPathD8InputOption(JavaClassPath classPath) {
		this.classPath = classPath;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(classPath);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(classPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		classPath = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classPath == null) ? 0 : classPath.hashCode());
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
		JavaClassPathD8InputOption other = (JavaClassPathD8InputOption) obj;
		if (classPath == null) {
			if (other.classPath != null)
				return false;
		} else if (!classPath.equals(other.classPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + classPath + "]";
	}

}
