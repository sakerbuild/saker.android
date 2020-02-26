package saker.android.impl.aapt2.link.option;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.std.api.file.location.FileLocation;

public interface AAPT2LinkerInput {
	public void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation inputfile);

		public void visit(AAPT2CompileTaskOutput compilationinput);
	}
}
