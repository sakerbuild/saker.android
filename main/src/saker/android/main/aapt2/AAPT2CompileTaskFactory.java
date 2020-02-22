package saker.android.main.aapt2;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import saker.android.impl.AndroidUtils;
import saker.android.impl.aapt2.AAPT2CompileWorkerTaskFactory;
import saker.android.impl.aapt2.AAPT2CompileWorkerTaskIdentifier;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNameConflictException;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

public class AAPT2CompileTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aapt2.compile";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Directory" }, required = true)
			public SakerPath directoryOption;

			@SakerInput("Identifier")
			public CompilationIdentifierTaskOption identifierOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				CompilationIdentifier compilationid = CompilationIdentifierTaskOption.getIdentifier(identifierOption);
				if (compilationid == null) {
					compilationid = CompilationIdentifier.valueOf("default");
				}

				//XXX heavily duplicated with other frontend tasks
				Map<String, SDKDescriptionTaskOption> sdkoptions = new TreeMap<>(
						SDKSupportUtils.getSDKNameComparator());
				if (!ObjectUtils.isNullOrEmpty(this.sdksOption)) {
					for (Entry<String, SDKDescriptionTaskOption> entry : this.sdksOption.entrySet()) {
						SDKDescriptionTaskOption sdktaskopt = entry.getValue();
						if (sdktaskopt == null) {
							continue;
						}
						SDKDescriptionTaskOption prev = sdkoptions.putIfAbsent(entry.getKey(), sdktaskopt.clone());
						if (prev != null) {
							taskcontext.abortExecution(new SDKNameConflictException(
									"SDK with name " + entry.getKey() + " defined multiple times."));
							return null;
						}
					}
				}
				NavigableMap<String, SDKDescription> sdkdescriptions = new TreeMap<>(
						SDKSupportUtils.getSDKNameComparator());
				for (Entry<String, SDKDescriptionTaskOption> entry : sdkoptions.entrySet()) {
					SDKDescriptionTaskOption val = entry.getValue();
					SDKDescription[] desc = { null };
					if (val != null) {
						val.accept(new SDKDescriptionTaskOption.Visitor() {
							@Override
							public void visit(SDKDescription description) {
								desc[0] = description;
							}
						});
					}
					sdkdescriptions.putIfAbsent(entry.getKey(), desc[0]);
				}
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				AAPT2CompileWorkerTaskIdentifier workertaskid = new AAPT2CompileWorkerTaskIdentifier(compilationid);
				AAPT2CompileWorkerTaskFactory workertask = new AAPT2CompileWorkerTaskFactory();
				workertask.setResourceDirectory(directoryOption);
				workertask.setSDKDescriptions(sdkdescriptions);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
