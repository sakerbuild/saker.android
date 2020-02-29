package saker.android.main.aapt2.option;

import java.util.Collection;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ReducedWildcardPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.dependency.MavenDependencyResolutionTaskOutput;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;

public abstract class AAPT2LinkerInputTaskOption {
	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation file);

		public void visit(FileCollection file);

		public void visit(SakerPath path);

		public void visit(WildcardPath wildcard);

		public void visit(AAPT2CompileTaskOutput compilationinput);

		public void visit(AAPT2LinkTaskOutput linkinput);

		public void visit(MavenOperationConfiguration config, Collection<? extends ResolvedDependencyArtifact> input);
	}

	//TODO start the aar worker task factories directly, and pass those as inputs instead of letting the worker start it

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2CompileTaskOutput output) {
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

	public static AAPT2LinkerInputTaskOption valueOf(ResolvedDependencyArtifact input) {
		return createForResolvedArtifacts(input.getConfiguration(), ImmutableUtils.singletonSet(input));
	}

	public static AAPT2LinkerInputTaskOption valueOf(MavenDependencyResolutionTaskOutput input) {
		Collection<ResolvedDependencyArtifact> resolvedartifacts = input.getResolvedArtifacts();
		return createForResolvedArtifacts(input.getConfiguration(), resolvedartifacts);
	}

	private static AAPT2LinkerInputTaskOption createForResolvedArtifacts(MavenOperationConfiguration config,
			Collection<? extends ResolvedDependencyArtifact> input) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(config, input);
			}
		};
	}
}
