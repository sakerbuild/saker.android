package saker.android.main.apk.create.option;

import saker.android.api.ndk.strip.StripWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.clang.api.link.ClangLinkerWorkerTaskOutput;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.option.FileLocationTaskOption;

@NestInformation("An Android library location.\n"
		+ "Can be specified as a path, file location or output of the clang linker or NDK strip tasks.")
public class ApkLibraryLocationTaskOption {
	private FileLocationTaskOption fileLocationTaskOption;

	public ApkLibraryLocationTaskOption(FileLocationTaskOption fileLocationTaskOption) {
		this.fileLocationTaskOption = fileLocationTaskOption;
	}

	@Override
	public ApkLibraryLocationTaskOption clone() {
		return new ApkLibraryLocationTaskOption(fileLocationTaskOption.clone());
	}

	public FileLocationTaskOption getFileLocationTaskOption() {
		return fileLocationTaskOption;
	}

	public static ApkLibraryLocationTaskOption valueOf(FileLocation filelocation) {
		return new ApkLibraryLocationTaskOption(FileLocationTaskOption.valueOf(filelocation));
	}

	public static ApkLibraryLocationTaskOption valueOf(SakerPath path) {
		return new ApkLibraryLocationTaskOption(FileLocationTaskOption.valueOf(path));
	}

	public static ApkLibraryLocationTaskOption valueOf(String path) {
		return valueOf(SakerPath.valueOf(path));
	}

	public static ApkLibraryLocationTaskOption valueOf(ClangLinkerWorkerTaskOutput input) {
		return valueOf(input.getOutputPath());
	}

	public static ApkLibraryLocationTaskOption valueOf(StripWorkerTaskOutput input) {
		return valueOf(input.getPath());
	}

}
