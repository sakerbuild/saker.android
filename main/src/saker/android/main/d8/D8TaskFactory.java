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
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocClassBinaryName;
import saker.android.main.TaskDocs.DocD8TaskOutput;
import saker.android.main.apk.create.ApkCreateTaskFactory;
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
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.SDKSupportFrontendUtils;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;

@NestTaskInformation(returnType = @NestTypeUsage(DocD8TaskOutput.class))
@NestInformation("Performs dexing operations on Java bytecode using the Android d8 tool.\n"
		+ "The task converts the input Java bytecode to dex format that can run on Android devices.\n"
		+ "The result of this operation can be passed to the " + ApkCreateTaskFactory.TASK_NAME
		+ "() task to generate the APK that contains the classes.")
@NestParameterInformation(value = "Input",
		aliases = { "" },
		required = true,
		type = @NestTypeUsage(value = Collection.class, elementTypes = { D8InputTaskOption.class }),
		info = @NestInformation("Specifies the inputs for the dexing operation.\n"
				+ "The inputs may be paths or wildcards to Java archives and directories containing .class files.\n"
				+ "Outputs of the saker.java.compile() task can also be passed directly as input. Classpath objects are also accepted.\n"
				+ "The parameter accepts results of Maven artifact resolutions.\n"
				+ "Note that aar bundles are not accepted as a direct input to this parameter. You may need to "
				+ "perform the class extractions from aar bundles if necessary."))
@NestParameterInformation(value = "NoDesugaring",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Disables Java 8 language features.\n"
				+ "Set this parameter to tru only if you don't intend to compile Java bytecode that uses Java 8 language features.\n"
				+ "Corresponds to the --no-desugaring flag of d8."))
@NestParameterInformation(value = "Release",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Compiles DEX bytecode without debug information.\n"
				+ "Set this parameter to true when compiling bytecode for a public release.\n"
				+ "Corresponds to the --release flag of d8."))

@NestParameterInformation(value = "MainDexClasses",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { DocClassBinaryName.class }),
		info = @NestInformation("Specifies the binary names of Java classes that should be part of the main dex file.\n"
				+ "This parameter must be used to enable multidexing. The specified classes will be placed in the main "
				+ "dex file."))

@NestParameterInformation(value = "MinAPI",
		aliases = { "MinSDKVersion" },
		type = @NestTypeUsage(int.class),
		info = @NestInformation("Specifies the minimum API level you want the output DEX files to support.\n"
				+ "Corresponds to the --min-api argument of d8."))
@NestParameterInformation(value = "OptimizeMultidexForLinearAlloc",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("If set to true, legacy multidex partitioning will be optimized to reduce LinearAlloc usage "
				+ " during Dalvik DexOpt.\n" + "This option may not be supported in older versions of d8."
				+ "Corresponds to the --optimize-multidex-for-linearalloc flag of d8."))

@NestParameterInformation(value = "Identifier",
		type = @NestTypeUsage(CompilationIdentifierTaskOption.class),
		info = @NestInformation("Specifies an identifier for the dexing operation.\n"
				+ "The identifier will be used to uniquely identify this operation, and to generate the output directory name."))
@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))
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

			@SakerInput(value = { "MainDexClasses" })
			public Collection<String> mainDexClassesOption;

			//alias with MinSDKVersion so its the same with aapt2 task
			@SakerInput(value = { "MinAPI", "MinSDKVersion" })
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

				NavigableMap<String, SDKDescription> sdkdescriptions = SDKSupportFrontendUtils
						.toSDKDescriptionMap(sdksOption);
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
