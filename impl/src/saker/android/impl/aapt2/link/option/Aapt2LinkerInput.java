package saker.android.impl.aapt2.link.option;

import saker.android.api.aapt2.aar.Aapt2AarCompileWorkerTaskOutput;
import saker.android.api.aapt2.compile.Aapt2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.Aapt2LinkWorkerTaskOutput;
import saker.std.api.file.location.FileLocation;

public interface Aapt2LinkerInput {
	public void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation inputfile);

		public void visit(Aapt2CompileWorkerTaskOutput compilationinput);

		public void visit(Aapt2AarCompileWorkerTaskOutput compilationinput);

		public void visit(Aapt2LinkWorkerTaskOutput linkinput);
	}
}
