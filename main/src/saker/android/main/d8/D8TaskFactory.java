package saker.android.main.d8;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.impl.AndroidUtils;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.D8WorkerTaskIdentifier;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.option.MultiFileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

public class D8TaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.d8";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public Collection<MultiFileLocationTaskOption> inputOption;

			@SakerInput(value = { "NoDesugaring" })
			public boolean noDesugaringOption;

			@SakerInput(value = { "Release" })
			public boolean releaseOption;

			@SakerInput(value = { "MainDexClasses" })
			public Collection<String> mainDexClassesOption;

			@SakerInput("MinAPI")
			public Integer minApiOpion;

			@SakerInput("Identifier")
			public CompilationIdentifierTaskOption identifier;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifier);
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}
				Set<FileLocation> inputs = new LinkedHashSet<>();
				for (MultiFileLocationTaskOption intaskoption : inputOption) {
					inputs.addAll(TaskOptionUtils.toFileLocations(intaskoption, taskcontext, null));
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				D8WorkerTaskIdentifier workertaskid = new D8WorkerTaskIdentifier(compilationid);
				D8WorkerTaskFactory workertask = new D8WorkerTaskFactory(inputs);
				workertask.setNoDesugaring(noDesugaringOption);
				workertask.setRelease(releaseOption);
				workertask.setMainDexClasses(ImmutableUtils.makeImmutableNavigableSet(mainDexClassesOption));
				workertask.setMinApi(minApiOpion);
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
