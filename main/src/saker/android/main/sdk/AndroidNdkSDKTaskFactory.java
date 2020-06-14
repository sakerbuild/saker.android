package saker.android.main.sdk;

import java.util.Collection;
import java.util.Set;

import saker.android.impl.sdk.AndroidNdkSDKReference;
import saker.android.impl.sdk.VersionsAndroidNdkSDKDescription;
import saker.android.main.TaskDocs.DocAndroidNdkSDKDescription;
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

@NestTaskInformation(returnType = @NestTypeUsage(DocAndroidNdkSDKDescription.class))
@NestInformation("Gets an SDK description for the Android NDK (Native Development Kit).\n"
		+ "The task will retrieve an SDK description that locates the Android NDK with a specific version.\n"
		+ "The returned description can be passed to tasks that supports SDKs.\n"
		+ "The commonly used name for the SDK is " + AndroidNdkSDKReference.SDK_NAME + ".")
@NestParameterInformation(value = "Version",
		aliases = { "", "Versions" },
		type = @NestTypeUsage(value = Collection.class, elementTypes = { String.class }),
		info = @NestInformation("Specifies the version numbers that the located Android NDK should conform to.\n"
				+ "The version numbers should equal to the Pkg.Revision property of the source.properties file in an "
				+ "Android NDK installation."))
public class AndroidNdkSDKTaskFactory extends FrontendTaskFactory<SDKDescription> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.ndk.sdk";

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
				return VersionsAndroidNdkSDKDescription.create(versions);
			}
		};
	}

}
