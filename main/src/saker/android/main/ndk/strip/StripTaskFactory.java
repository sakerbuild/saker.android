package saker.android.main.ndk.strip;

import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.ndk.strip.StripWorkerTaskFactory;
import saker.android.impl.ndk.strip.StripWorkerTaskIdentifier;
import saker.android.impl.sdk.AndroidNdkSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocStripWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
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
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocStripWorkerTaskOutput.class))
@NestInformation("Invokes the strip tool in the Android NDK.\n"
		+ "The task can be used to run strip on the compiled binaries when using Android NDK.")

@NestParameterInformation(value = "Input",
		aliases = { "" },
		required = true,
		type = @NestTypeUsage(value = FileLocationTaskOption.class),
		info = @NestInformation("The input file to strip."))
@NestParameterInformation(value = "Output",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("A forward relative output path that specifies the output location of the stripped binary.\n"
				+ "It can be used to have a better output location than the automatically generated one."))
@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))
public class StripTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.ndk.strip";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {
			@SakerInput(value = { "", "Input" }, required = true)
			public FileLocationTaskOption inputOption;

			@SakerInput(value = "Output")
			public SakerPath outputOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				FileLocation inputfilelocation = TaskOptionUtils.toFileLocation(inputOption, taskcontext);
				final SakerPath outputpath = SakerPath.valueOf(TASK_NAME)
						.resolve(AndroidFrontendUtils.getOutputPathForForwardRelativeWithFileName(outputOption,
								inputfilelocation, "Stripped binary output path", f -> "stripped-" + f));

				NavigableMap<String, SDKDescription> sdkdescriptions = SDKSupportFrontendUtils
						.toSDKDescriptionMap(this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidNdkSDKReference.SDK_NAME, AndroidUtils.DEFAULT_NDK_SDK);

				StripWorkerTaskIdentifier workertaskid = new StripWorkerTaskIdentifier(outputpath);
				StripWorkerTaskFactory workertask = new StripWorkerTaskFactory(inputfilelocation);
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}
}
