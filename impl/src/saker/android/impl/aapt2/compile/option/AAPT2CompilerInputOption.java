package saker.android.impl.aapt2.compile.option;

import java.util.Set;

import saker.std.api.file.location.FileLocation;

public interface AAPT2CompilerInputOption {
	public void accept(Visitor visitor);

	public interface Visitor {
		public void visitResources(Set<FileLocation> files);

		public void visitResourceDirectory(FileLocation dir);
	}
}
