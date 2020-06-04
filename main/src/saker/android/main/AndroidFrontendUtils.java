package saker.android.main;

import java.util.function.Function;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AndroidFrontendUtils {
	private AndroidFrontendUtils() {
		throw new UnsupportedOperationException();
	}

	public static SakerPath getOutputPathForForwardRelativeWithFileName(SakerPath outputpath, FileLocation input,
			String message, Function<String, String> filenametransformer) {
		if (outputpath != null) {
			return requireFormwardRelativeWithFileName(outputpath, message);
		}
		if (input != null) {
			String fname = SakerStandardUtils.getFileLocationFileName(input);
			if (fname == null) {
				throw new InvalidPathFormatException("Failed to determine file name from: " + input);
			}
			if (filenametransformer != null) {
				fname = filenametransformer.apply(fname);
			}
			return SakerPath.valueOf(fname);
		}
		return null;
	}

	public static SakerPath requireFormwardRelativeWithFileName(SakerPath outputpath, String message) {
		if (!outputpath.isForwardRelative()) {
			throw new InvalidPathFormatException(message + " must be forward relative: " + outputpath);
		}
		if (outputpath.getFileName() == null) {
			throw new InvalidPathFormatException(message + " must have a file name: " + outputpath);
		}
		return outputpath;
	}
}
