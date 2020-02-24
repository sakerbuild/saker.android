package saker.android.main.apk.create.option;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.zip.api.create.ZipCreationTaskBuilder;

public abstract class ApkResourcesTaskOption {
	public abstract void applyTo(ZipCreationTaskBuilder zipbuilder);

	public static ApkResourcesTaskOption valueOf(AAPT2LinkTaskOutput aaptoutput) {
		SakerPath resapkpath = aaptoutput.getAPKPath();
		return valueOf(resapkpath);
	}

	public static ApkResourcesTaskOption valueOf(SakerPath resapkpath) {
		return new ApkResourcesTaskOption() {
			@Override
			public void applyTo(ZipCreationTaskBuilder zipbuilder) {
				zipbuilder.addIncludeArchive(ExecutionFileLocation.create(resapkpath), null);
			}
		};
	}
}
