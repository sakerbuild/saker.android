package saker.android.main.aapt2.option;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileFrontendTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileWorkerTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ReducedWildcardPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;

@NestInformation("Input for aapt2 linking operation.")
public abstract class AAPT2LinkerInputTaskOption {
	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation file);

		public void visit(FileCollection file);

		public void visit(SakerPath path);

		public void visit(WildcardPath wildcard);

		public void visit(AAPT2CompileWorkerTaskOutput compilationinput);

		public void visit(AAPT2AarCompileTaskOutput compilationinput);

		public void visit(AAPT2CompileFrontendTaskOutput compilationinput);

		public void visit(AAPT2LinkTaskOutput linkinput);

	}

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2CompileWorkerTaskOutput output) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2CompileFrontendTaskOutput output) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2AarCompileTaskOutput output) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2LinkTaskOutput output) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(output);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(String filepath) {
		return valueOf(WildcardPath.valueOf(filepath));
	}

	public static AAPT2LinkerInputTaskOption valueOf(WildcardPath input) {
		ReducedWildcardPath reduced = input.reduce();
		if (reduced.getWildcard() == null) {
			SakerPath fileinput = reduced.getFile();
			if (fileinput.isAbsolute()) {
				return valueOf(ExecutionFileLocation.create(fileinput));
			}
			return new AAPT2LinkerInputTaskOption() {
				@Override
				public void accept(Visitor visitor) {
					visitor.visit(fileinput);
				}
			};
		}
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(input);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(FileCollection input) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(input);
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(SakerPath fileinput) {
		return valueOf(WildcardPath.valueOf(fileinput));
	}

	public static AAPT2LinkerInputTaskOption valueOf(FileLocation fileinput) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(fileinput);
			}
		};
	}

}
