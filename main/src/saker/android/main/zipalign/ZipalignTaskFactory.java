package saker.android.main.zipalign;

import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.impl.zipalign.ZipalignWorkerTaskFactory;
import saker.android.impl.zipalign.ZipalignWorkerTaskIdentifier;
import saker.android.main.AndroidFrontendUtils;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

public class ZipalignTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.zipalign";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {
			@SakerInput(value = { "", "APK", "Input" }, required = true)
			public SakerPath inputOption;

			@SakerInput(value = "Output")
			public SakerPath outputOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				SakerPath outputpath = outputOption;
				if (outputpath != null) {
					if (!outputpath.isForwardRelative()) {
						taskcontext.abortExecution(new InvalidPathFormatException(
								"Signed APK output path must be forward relative: " + outputpath));
						return null;
					}
				} else {
					String apkfname = inputOption.getFileName();
					outputpath = SakerPath.valueOf(toAlignedOutputApkFileName(apkfname));
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				ZipalignWorkerTaskIdentifier workertaskid = new ZipalignWorkerTaskIdentifier(outputpath);
				ZipalignWorkerTaskFactory workertask = new ZipalignWorkerTaskFactory();
				workertask.setInputPath(inputOption);
				workertask.setOutputPath(SakerPath.valueOf(TASK_NAME).resolve(outputpath));
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

	private static String toAlignedOutputApkFileName(String apkfname) {
		String ext = FileUtils.getExtension(apkfname);
		if (ext == null) {
			return apkfname + "-aligned.apk";
		}
		return apkfname.substring(0, apkfname.length() - (ext.length() + 1)) + "-aligned." + ext;
	}
}
