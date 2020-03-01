package saker.android.impl.aapt2.link.option;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.std.api.file.location.FileLocation;

public interface AAPT2LinkerInput {
	public void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation inputfile);

		public void visit(AAPT2CompileWorkerTaskOutput compilationinput);

		public void visit(AAPT2AarCompileTaskOutput compilationinput);

		public void visit(AAPT2LinkTaskOutput linkinput);
	}
}
