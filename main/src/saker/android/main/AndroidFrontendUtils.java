package saker.android.main;

import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNameConflictException;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.FileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AndroidFrontendUtils {
	private AndroidFrontendUtils() {
		throw new UnsupportedOperationException();
	}

	public static NavigableMap<String, SDKDescription> sdksTaskOptionToDescriptions(TaskContext taskcontext,
			Map<String, SDKDescriptionTaskOption> sdksoption) {
		Map<String, SDKDescriptionTaskOption> sdkoptions = new TreeMap<>(SDKSupportUtils.getSDKNameComparator());
		if (!ObjectUtils.isNullOrEmpty(sdksoption)) {
			for (Entry<String, SDKDescriptionTaskOption> entry : sdksoption.entrySet()) {
				SDKDescriptionTaskOption sdktaskopt = entry.getValue();
				if (sdktaskopt == null) {
					continue;
				}
				SDKDescriptionTaskOption prev = sdkoptions.putIfAbsent(entry.getKey(), sdktaskopt.clone());
				if (prev != null) {
					taskcontext.abortExecution(new SDKNameConflictException(
							"SDK with name " + entry.getKey() + " defined multiple times."));
					return null;
				}
			}
		}
		NavigableMap<String, SDKDescription> sdkdescriptions = new TreeMap<>(SDKSupportUtils.getSDKNameComparator());
		for (Entry<String, SDKDescriptionTaskOption> entry : sdkoptions.entrySet()) {
			SDKDescriptionTaskOption val = entry.getValue();
			SDKDescription[] desc = { null };
			if (val != null) {
				val.accept(new SDKDescriptionTaskOption.Visitor() {
					@Override
					public void visit(SDKDescription description) {
						desc[0] = description;
					}
				});
			}
			sdkdescriptions.putIfAbsent(entry.getKey(), desc[0]);
		}
		return sdkdescriptions;
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
