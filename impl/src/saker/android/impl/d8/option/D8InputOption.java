package saker.android.impl.d8.option;

import saker.java.compiler.api.classpath.JavaClassPath;
import saker.java.compiler.api.compile.JavaCompilationWorkerTaskIdentifier;
import saker.std.api.file.location.FileLocation;

public interface D8InputOption {
	public void accept(Visitor visitor);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	public interface Visitor {
		public void visit(FileLocation file);

		public void visit(JavaClassPath classpath);

		public void visit(JavaCompilationWorkerTaskIdentifier javactaskid);
	}
}
