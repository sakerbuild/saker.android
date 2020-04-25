package saker.android.main.d8;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.impl.AndroidUtils;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.D8WorkerTaskIdentifier;
import saker.android.impl.d8.option.D8InputOption;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.d8.option.D8InputTaskOption;
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

public class D8TaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.d8";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Input" }, required = true)
			public Collection<D8InputTaskOption> inputOption;

			@SakerInput(value = { "NoDesugaring" })
			public boolean noDesugaringOption;

			@SakerInput(value = { "Release" })
			public boolean releaseOption;

			//XXX binary names
			@SakerInput(value = { "MainDexClasses" })
			public Collection<String> mainDexClassesOption;

			@SakerInput("MinAPI")
			public Integer minApiOpion;

			@SakerInput("OptimizeMultidexForLinearAlloc")
			public boolean optimizeMultidexForLinearAllocOption;

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
				Set<CompilationIdentifier> inferredidentifiers;
				if (compilationid == null) {
					inferredidentifiers = new HashSet<>();
				} else {
					inferredidentifiers = null;
				}
				Set<D8InputOption> inputs = new LinkedHashSet<>();
				for (D8InputTaskOption intaskoption : inputOption) {
					if (intaskoption == null) {
						continue;
					}
					if (inferredidentifiers != null) {
						CompilationIdentifier inferred = intaskoption.inferCompilationIdentifier();
						if (inferred != null) {
							inferredidentifiers.add(inferred);
						} else {
							//don't infer.
							// only use inferred identifier if there's a single input
							inferredidentifiers = null;
						}
					}
					Set<D8InputOption> inoptions = intaskoption.toInputOption(taskcontext);
					inputs.addAll(inoptions);
				}
				if (compilationid == null) {
					if (inferredidentifiers != null && inferredidentifiers.size() == 1) {
						compilationid = inferredidentifiers.iterator().next();
					} else {
						compilationid = CompilationIdentifier.valueOf("default");
					}
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
				workertask.setOptimizeMultidexForLinearAlloc(optimizeMultidexForLinearAllocOption);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
