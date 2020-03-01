package saker.android.main.aidl;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import saker.android.impl.AndroidUtils;
import saker.android.impl.aidl.AidlWorkerTaskFactory;
import saker.android.impl.aidl.AidlWorkerTaskIdentifier;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.compiler.utils.main.CompilationIdentifierTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

public class AidlTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.aidl";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "SourceDirectory", "SourceDirectories" }, required = true)
			public Collection<SakerPath> sourceDirectoriesOption;

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
					compilationid = generateCompilationIdentifier(taskcontext.getTaskWorkingDirectoryPath());
				}

				NavigableMap<String, SDKDescription> sdkdescriptions = AndroidFrontendUtils
						.sdksTaskOptionToDescriptions(taskcontext, this.sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				NavigableSet<SakerPath> sourcedirs = new TreeSet<>();
				for (SakerPath srcdir : sourceDirectoriesOption) {
					sourcedirs.add(taskcontext.getTaskWorkingDirectoryPath().tryResolve(srcdir));
				}

				AidlWorkerTaskFactory workertask = new AidlWorkerTaskFactory(sourcedirs);
				workertask.setSDKDescriptions(sdkdescriptions);
				AidlWorkerTaskIdentifier workertaskid = new AidlWorkerTaskIdentifier(compilationid);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

			private CompilationIdentifier generateCompilationIdentifier(SakerPath taskworkingdir) {
				Set<String> dirnames = new TreeSet<>();
				for (SakerPath srcdir : sourceDirectoriesOption) {
					String dirfilename = srcdir.getFileName();
					if (dirfilename != null) {
						dirnames.add(dirfilename);
					}
				}
				String passidstring;
				String wdfilename = taskworkingdir == null ? null : taskworkingdir.getFileName();
				if (wdfilename != null) {
					passidstring = wdfilename + StringUtils.toStringJoin("-", "-", dirnames, null);
				} else {
					passidstring = StringUtils.toStringJoin("-", dirnames);
				}
				if (passidstring.isEmpty()) {
					passidstring = "default";
				}
				return CompilationIdentifier.valueOf(passidstring);
			}
		};
	}

}
