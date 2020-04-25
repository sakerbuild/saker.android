package saker.android.main.apk.sign.option;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.api.apk.sign.SignApkTaskOutput;
import saker.android.api.zipalign.ZipAlignTaskOutput;
import saker.android.main.TaskDocs.DocZipAlignTaskOutput;
import saker.android.main.apk.sign.SignApkTaskFactory;
import saker.android.main.zipalign.ZipAlignTaskFactory;
import saker.build.file.path.SakerPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.TaskDocs.DocFileLocation;
import saker.zip.api.create.ZipCreatorTaskOutput;

@NestInformation("Input APK for the " + SignApkTaskFactory.TASK_NAME + "() task.\n"
		+ "May be a path to the APK to be signed, output of the " + ZipAlignTaskFactory.TASK_NAME
		+ "() task, or result of a ZIP archive creation.")
@NestTypeInformation(relatedTypes = { @NestTypeUsage(SakerPath.class), @NestTypeUsage(DocFileLocation.class),
		@NestTypeUsage(DocZipAlignTaskOutput.class), })
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

	public static SignApkInputTaskOption valueOf(SignApkTaskOutput signapkout) {
		return valueOf(signapkout.getPath());
	}

}
