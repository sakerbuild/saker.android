package saker.android.main;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNameConflictException;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

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
}
