package saker.android.main.sdk;

import java.util.Collection;
import java.util.Set;

import saker.android.impl.sdk.VersionsAndroidBuildToolsSDKDescription;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.TaskDocs.DocSDKDescription;

@NestTaskInformation(returnType = @NestTypeUsage(DocSDKDescription.class))
@NestInformation("Retrieves an Android build-tools SDK description.\n"
		+ "The task can be used to get an SDK reference to the Android build-tools with a specific version. "
		+ "These SDKs can be passed as the input to other tasks to be used to retrieve the required tooling.")
@NestParameterInformation(value = "Version",
		aliases = { "", "Versions" },
		type = @NestTypeUsage(value = Collection.class, elementTypes = String.class),
		info = @NestInformation("Collection of version ranges that specify the version of the Android build-tools that should be referenced.\n"
				+ "The version elements can be version ranges, or specific versions that match the version of the SDK.\n"
				+ "The versions will be compared with the build-tools version directory name and matched accordingly.\n"
				+ "E.g. specifying \"29\" will match any non-preview versions that have the major of 29.\n"
				+ "\"30.0.0-preview\" will match only the 30.0.0-preview version."))
public class AndroidBuildToolsSDKTaskFactory extends FrontendTaskFactory<SDKDescription> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.sdk.buildtools";

	@Override
	public ParameterizableTask<? extends SDKDescription> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<SDKDescription>() {

			@SakerInput(value = { "", "Version", "Versions" })
			public Collection<String> versionsOption;

			@Override
			public SDKDescription run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_CONFIGURATION);
				}

				Set<String> versions = ImmutableUtils.makeImmutableNavigableSet(versionsOption);
				return VersionsAndroidBuildToolsSDKDescription.create(versions);
			}
		};
	}

}
