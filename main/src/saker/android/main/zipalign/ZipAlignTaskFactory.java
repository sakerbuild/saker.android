package saker.android.main.zipalign;

import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.impl.zipalign.ZipAlignWorkerTaskFactory;
import saker.android.impl.zipalign.ZipAlignWorkerTaskIdentifier;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocZipAlignTaskOutput;
import saker.android.main.zipalign.option.ZipAlignInputTaskOption;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.SDKSupportFrontendUtils;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.FileLocation;

@NestTaskInformation(returnType = @NestTypeUsage(DocZipAlignTaskOutput.class))
@NestInformation("Performs ZIP alignment on the specified archive.\n"
		+ "The task uses the zipalign tool on the specified APK (or any other ZIP archive).\n"
		+ "The input archive is not overwritten. The output is written to the build directory for the task.")
@NestParameterInformation(value = "Input",
		aliases = { "", "Input" },
		required = true,
		type = @NestTypeUsage(ZipAlignInputTaskOption.class),
		info = @NestInformation("The input APK (or any kind of ZIP archive) that should be aligned."))
@NestParameterInformation(value = "Output",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("Specifies an output path for the aligned APK.\n"
				+ "The output path should be forward relative. It will be used to place the "
				+ "aligned APK in the build directory."))
@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))
public class ZipAlignTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.zipalign";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {
			@SakerInput(value = { "", "APK", "Input" }, required = true)
			public ZipAlignInputTaskOption inputOption;

			@SakerInput(value = "Output")
			public SakerPath outputOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				FileLocation inputzipfile = inputOption.getInputFileLocation();

				SakerPath outputpath;
				try {
					outputpath = AndroidFrontendUtils.getOutputPathForForwardRelativeWithFileName(outputOption,
							inputzipfile, "Signed APK output path", ZipAlignTaskFactory::toAlignedOutputApkFileName);
				} catch (Exception e) {
					taskcontext.abortExecution(e);
					return null;
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = SDKSupportFrontendUtils
						.toSDKDescriptionMap(sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				ZipAlignWorkerTaskIdentifier workertaskid = new ZipAlignWorkerTaskIdentifier(outputpath);
				ZipAlignWorkerTaskFactory workertask = new ZipAlignWorkerTaskFactory();
				workertask.setInputFile(inputzipfile);
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
