package saker.android.main.classpath;

import saker.android.impl.classpath.AndroidPlatformClassPathReference;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.TaskDocs.DocAndroidPlatformClassPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;

@NestTaskInformation(returnType = @NestTypeUsage(DocAndroidPlatformClassPath.class))
@NestInformation("Gets a classpath reference to an Android platform.\n"
		+ "The class will get a classpath configuration object for the android.jar for the "
		+ AndroidPlatformSDKReference.SDK_NAME + " SDK.\n"
		+ "The output of this task can be passed to the saker.java.compile() task to compile an Android application."
		+ "Make sure to specify the " + AndroidPlatformSDKReference.SDK_NAME
		+ " SDK for the task with which you use this classpath.")
@NestParameterInformation(value = "CoreLambdaStubs",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Sets whether or not the core-lambda-stubs.jar should be part of this classpath.\n"
				+ "The core-lambda-stubs.jar may be necessary if you want to use Java 8 features in your code.\n"
				+ "The default value is true.\n" + "The " + AndroidBuildToolsSDKReference.SDK_NAME
				+ " SDK is required to resolve the core-lambda-stubs.jar."))
public class AndroidPlatformClassPathTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.classpath.platform";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput("CoreLambdaStubs")
			public boolean includeCoreLambdaStubs = true;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_CONFIGURATION);
				}

				return new AndroidPlatformClassPathReference(includeCoreLambdaStubs);
			}
		};
	}

}
