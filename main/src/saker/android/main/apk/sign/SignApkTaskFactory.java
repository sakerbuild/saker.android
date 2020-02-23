package saker.android.main.apk.sign;

import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.apk.sign.SignApkWorkerTaskFactory;
import saker.android.impl.apk.sign.SignApkWorkerTaskIdentifier;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
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

public class SignApkTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.apk.sign";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "APK" }, required = true)
			public SakerPath apkOption;

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
					String apkfname = apkOption.getFileName();
					outputpath = SakerPath.valueOf(toSignedOutputApkFileName(apkfname));
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				SignApkWorkerTaskIdentifier workertaskid = new SignApkWorkerTaskIdentifier(outputpath);
				SignApkWorkerTaskFactory workertask = new SignApkWorkerTaskFactory();
				workertask.setInputPath(apkOption);
				workertask.setOutputPath(SakerPath.valueOf(TASK_NAME).resolve(outputpath));
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
	}

	private static String toSignedOutputApkFileName(String apkfname) {
		String ext = FileUtils.getExtension(apkfname);
		if (ext == null) {
			return apkfname + "-signed.apk";
		}
		return apkfname.substring(0, apkfname.length() - (ext.length() + 1)) + "-signed." + ext;
	}
}
