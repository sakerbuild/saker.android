package saker.android.main.aapt2.option;

import java.util.Set;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.android.impl.aapt2.link.option.AAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.CompilationAAPT2LinkerInput;
import saker.android.impl.aapt2.link.option.FileAAPT2LinkerInput;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;

public abstract class AAPT2LinkerInputTaskOption {
	public abstract Set<AAPT2LinkerInput> toLinkerInput(TaskContext taskcontext);

	public CompilationIdentifier inferCompilationIdentifier() {
		return null;
	}

	public static AAPT2LinkerInputTaskOption valueOf(AAPT2CompileTaskOutput compileoutput) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public Set<AAPT2LinkerInput> toLinkerInput(TaskContext taskcontext) {
				return ImmutableUtils.singletonSet(new CompilationAAPT2LinkerInput(compileoutput));
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(String filepath) {
		return valueOf(SakerPath.valueOf(filepath));
	}

	public static AAPT2LinkerInputTaskOption valueOf(SakerPath fileinput) {
		if (fileinput.isAbsolute()) {
			return valueOf(ExecutionFileLocation.create(fileinput));
		}
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public Set<AAPT2LinkerInput> toLinkerInput(TaskContext taskcontext) {
				return ImmutableUtils.singletonSet(new FileAAPT2LinkerInput(
						ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath().resolve(fileinput))));
			}
		};
	}

	public static AAPT2LinkerInputTaskOption valueOf(FileLocation fileinput) {
		return new AAPT2LinkerInputTaskOption() {
			@Override
			public Set<AAPT2LinkerInput> toLinkerInput(TaskContext taskcontext) {
				return ImmutableUtils.singletonSet(new FileAAPT2LinkerInput(fileinput));
			}
		};
	}
}
