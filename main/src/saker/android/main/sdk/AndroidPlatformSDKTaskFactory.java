package saker.android.main.sdk;

import java.util.Collection;
import java.util.Set;

import saker.android.impl.sdk.VersionsAndroidPlatformSDKDescription;
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
@NestInformation("Retrieves an Android platform SDK description.\n"
		+ "The task can be used to get an SDK reference to the Android platform with a specific version. "
		+ "These SDKs can be passed as the input to other tasks to be used to retrieve the required tooling.\n"
		+ "Android platforms are located in the platforms directory of the Android SDK.")
@NestParameterInformation(value = "Version",
		aliases = { "", "Versions" },
		type = @NestTypeUsage(value = Collection.class, elementTypes = String.class),
		info = @NestInformation("Collection of versions that specify the version of the Android platform that should be referenced.\n"
				+ "The elements can be platform identifiers that are present in the android-<id> format, or the complete directory name as well.\n"
				+ "E.g. \"android-R\", or simply \"R\" will reference the android-R platform.\n"
				+ "29 will simply reference the android-29 platform."))
public class AndroidPlatformSDKTaskFactory extends FrontendTaskFactory<SDKDescription> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.sdk.platform";

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
				return VersionsAndroidPlatformSDKDescription.create(versions);
			}
		};
	}

}
