package saker.android.main.aapt2.option;

import saker.android.api.aapt2.aar.Aapt2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.Aapt2CompileFrontendTaskOutput;
import saker.android.api.aapt2.compile.Aapt2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.Aapt2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ReducedWildcardPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;

@NestInformation("Input for aapt2 linking operation.")
public abstract class Aapt2LinkerInputTaskOption {
	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation file);

		public void visit(FileCollection file);

		public void visit(SakerPath path);

		public void visit(WildcardPath wildcard);

		public void visit(Aapt2CompileWorkerTaskOutput compilationinput);

		public void visit(Aapt2AarCompileTaskOutput compilationinput);

		public void visit(Aapt2CompileFrontendTaskOutput compilationinput);

		public void visit(Aapt2LinkTaskOutput linkinput);

	}

	public static Aapt2LinkerInputTaskOption valueOf(Aapt2CompileWorkerTaskOutput output) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(Aapt2CompileFrontendTaskOutput output) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(Aapt2AarCompileTaskOutput output) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(Aapt2LinkTaskOutput output) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(String filepath) {
		return valueOf(WildcardPath.valueOf(filepath));
	}

	public static Aapt2LinkerInputTaskOption valueOf(WildcardPath input) {
		ReducedWildcardPath reduced = input.reduce();
		if (reduced.getWildcard() == null) {
			SakerPath fileinput = reduced.getFile();
			if (fileinput.isAbsolute()) {
				return valueOf(ExecutionFileLocation.create(fileinput));
			}
			return new Aapt2LinkerInputTaskOption() {
				@Override
				public void accept(Visitor visitor) {
					visitor.visit(fileinput);
				}
			};
		}
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(input);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(FileCollection input) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(input);
			}
		};
	}

	public static Aapt2LinkerInputTaskOption valueOf(SakerPath fileinput) {
		return valueOf(WildcardPath.valueOf(fileinput));
	}

	public static Aapt2LinkerInputTaskOption valueOf(FileLocation fileinput) {
		return new Aapt2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(fileinput);
			}
		};
	}

}
