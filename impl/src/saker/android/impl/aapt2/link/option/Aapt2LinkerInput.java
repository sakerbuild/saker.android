package saker.android.impl.aapt2.link.option;

import saker.android.api.aapt2.aar.Aapt2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.Aapt2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.Aapt2LinkTaskOutput;
import saker.std.api.file.location.FileLocation;

public interface Aapt2LinkerInput {
	public void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation inputfile);

		public void visit(Aapt2CompileWorkerTaskOutput compilationinput);

		public void visit(Aapt2AarCompileTaskOutput compilationinput);

		public void visit(Aapt2LinkTaskOutput linkinput);
	}
}
