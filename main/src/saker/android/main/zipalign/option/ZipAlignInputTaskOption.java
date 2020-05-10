package saker.android.main.zipalign.option;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.zip.api.create.ZipCreatorTaskOutput;

@NestInformation("Input for ZIP alignment.")
public abstract class ZipAlignInputTaskOption {
	public abstract FileLocation getInputFileLocation();

	public static ZipAlignInputTaskOption valueOf(FileLocation filelocation) {
		return new ZipAlignInputTaskOption() {
			@Override
			public FileLocation getInputFileLocation() {
				return filelocation;
			}
		};
	}

	public static ZipAlignInputTaskOption valueOf(SakerPath path) {
		return valueOf(ExecutionFileLocation.create(path));
	}

	public static ZipAlignInputTaskOption valueOf(ZipCreatorTaskOutput zipout) {
		return valueOf(zipout.getPath());
	}

	public static ZipAlignInputTaskOption valueOf(AAPT2LinkTaskOutput aapt2linkout) {
		return valueOf(aapt2linkout.getAPKPath());
	}

}
