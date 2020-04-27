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
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocAidlTaskOutput;
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
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

@NestTaskInformation(returnType = @NestTypeUsage(DocAidlTaskOutput.class))
@NestInformation("Performs AIDL (Android Interface Definition Language) compilation.\n"
		+ "The task uses the aidl tool that comes with the Android SDK to compile the input .aidl files.\n"
		+ "The tasks takes the source directories as the input, and generates the Java source files accordingly. "
		+ "The .aidl files should be in the appropriate package hierarchy in each source directory.\n")
@NestParameterInformation(value = "SourceDirectories",
		aliases = { "", "SourceDirectory" },
		required = true,
		type = @NestTypeUsage(value = Collection.class, elementTypes = { SakerPath.class }),
		info = @NestInformation("Specifies the source directories containing the .aidl files.\n"
				+ "The parameter accepts one or more paths to the AIDL source directories which contain the .aidl "
				+ "files that should be compiled.\n"
				+ "The .aidl files should be in a directory hierarchy respective to their enclosing package name.\n"
				+ "E.g. A service called com.example.IRemoteService should be in the com/example/IRemoteService.aidl file."))
@NestParameterInformation(value = "Identifier",
		type = @NestTypeUsage(CompilationIdentifierTaskOption.class),
		info = @NestInformation("Specifies an identifier for the AIDL compilation.\n"
				+ "The identifier will be used to uniquely identify this compilation, and to generate the output directory name."))
@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))
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
