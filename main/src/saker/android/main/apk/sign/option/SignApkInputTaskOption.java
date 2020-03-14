package saker.android.main.apk.sign.option;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.api.zipalign.ZipAlignTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.zip.api.create.ZipCreatorTaskOutput;

public abstract class SignApkInputTaskOption {

	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(FileLocation file);

		public void visit(SakerPath path);
	}

	public static SignApkInputTaskOption valueOf(FileLocation filelocation) {
		return new SignApkInputTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(filelocation);
			}
		};

	}

	public static SignApkInputTaskOption valueOf(SakerPath path) {
		if (path.isRelative()) {
			return new SignApkInputTaskOption() {
				@Override
				public void accept(Visitor visitor) {
					visitor.visit(path);
				}
			};
		}
		return valueOf(ExecutionFileLocation.create(path));
	}

	public static SignApkInputTaskOption valueOf(ZipCreatorTaskOutput zipout) {
		return valueOf(zipout.getPath());
	}

	public static SignApkInputTaskOption valueOf(AAPT2LinkTaskOutput aapt2linkout) {
		return valueOf(aapt2linkout.getAPKPath());
	}

	public static SignApkInputTaskOption valueOf(ZipAlignTaskOutput zipalignout) {
		return valueOf(zipalignout.getPath());
	}

}
