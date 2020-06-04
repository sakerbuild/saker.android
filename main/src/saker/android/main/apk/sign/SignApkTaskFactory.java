package saker.android.main.apk.sign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import saker.android.impl.AndroidUtils;
import saker.android.impl.apk.sign.SignApkWorkerTaskFactory;
import saker.android.impl.apk.sign.SignApkWorkerTaskIdentifier;
import saker.android.impl.apk.sign.SignerOption;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs;
import saker.android.main.TaskDocs.DocSignApkTaskOutput;
import saker.android.main.apk.create.ApkCreateTaskFactory;
import saker.android.main.apk.sign.option.SignApkInputTaskOption;
import saker.android.main.apk.sign.option.SignerTaskOption;
import saker.android.main.apk.sign.option.V4SigningEnabledInputTaskOption;
import saker.android.main.zipalign.ZipAlignTaskFactory;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.main.SDKSupportFrontendUtils;
import saker.sdk.support.main.option.SDKDescriptionTaskOption;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.utils.TaskOptionUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocSignApkTaskOutput.class))
@NestInformation("Signs an APK using the apksigner tool.\n"
		+ "The task will sign the input APK using the apksigner tool in the " + AndroidBuildToolsSDKReference.SDK_NAME
		+ " SDK.\n" + "The input APK should be already ZIP aligned.\n"
		+ "The task requires an input keystore that is used for the signing. If one is not provided, the "
		+ "APK is signed using an automatically generated debug key.")
@NestParameterInformation(value = "APK",
		aliases = { "" },
		type = @NestTypeUsage(SignApkInputTaskOption.class),
		required = true,
		info = @NestInformation("Input APK to be signed.\n"
				+ "The parameter accepts path to the input APK, output of the " + ApkCreateTaskFactory.TASK_NAME
				+ "() task, and  the output of the " + ZipAlignTaskFactory.TASK_NAME + "() task."))
@NestParameterInformation(value = "Output",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("Specifies an output path for the signed APK.\n"
				+ "The output path should be forward relative. It will be used to place the "
				+ "signed APK in the build directory."))

@NestParameterInformation(value = "SDKs",
		type = @NestTypeUsage(value = Map.class,
				elementTypes = { saker.sdk.support.main.TaskDocs.DocSdkNameOption.class,
						SDKDescriptionTaskOption.class }),
		info = @NestInformation(TaskDocs.SDKS))

@NestParameterInformation(value = "Signers",
		aliases = { "Signer" },
		type = @NestTypeUsage(value = List.class, elementTypes = { SignerTaskOption.class }),
		info = @NestInformation("One or more signers to be used when signing the APK.\n"
				+ "The parameter accepts zero, one, or more signer configurations that should be used to sign the APK.\n"
				+ "If no signers are specified, or the KeyStore field for them is null, then the APK is signed using the "
				+ "automatically generated debug key.\n"
				+ "If more than one signer is specified, the --next-signer flag is used to separate them."))

public class SignApkTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.apk.sign";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "APK" }, required = true)
			public SignApkInputTaskOption apkOption;

			@SakerInput(value = "Output")
			public SakerPath outputOption;

			@SakerInput(value = { "SDKs" })
			public Map<String, SDKDescriptionTaskOption> sdksOption;

			@SakerInput(value = { "Signer", "Signers" })
			public List<SignerTaskOption> signersOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				FileLocation[] apkfilelocation = { null };
				apkOption.accept(new SignApkInputTaskOption.Visitor() {
					@Override
					public void visit(SakerPath path) {
						visit(ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath().tryResolve(path)));
					}

					@Override
					public void visit(FileLocation file) {
						apkfilelocation[0] = file;
					}
				});
				final SakerPath outputpath = AndroidFrontendUtils.getOutputPathForForwardRelativeWithFileName(
						outputOption, apkfilelocation[0], "Signed APK output path",
						SignApkTaskFactory::toSignedOutputApkFileName);

				NavigableMap<String, SDKDescription> sdkdescriptions = SDKSupportFrontendUtils
						.toSDKDescriptionMap(sdksOption);
				sdkdescriptions.putIfAbsent(AndroidBuildToolsSDKReference.SDK_NAME,
						AndroidUtils.DEFAULT_BUILD_TOOLS_SDK);
				sdkdescriptions.putIfAbsent(AndroidPlatformSDKReference.SDK_NAME, AndroidUtils.DEFAULT_PLATFORM_SDK);

				SignApkWorkerTaskIdentifier workertaskid = new SignApkWorkerTaskIdentifier(outputpath);
				SignApkWorkerTaskFactory workertask = new SignApkWorkerTaskFactory();
				workertask.setInputFile(apkfilelocation[0]);
				workertask.setOutputPath(SakerPath.valueOf(TASK_NAME).resolve(outputpath));
				workertask.setSDKDescriptions(sdkdescriptions);

				List<SignerOption> signers = null;
				if (!ObjectUtils.isNullOrEmpty(signersOption)) {
					signers = new ArrayList<>();
					for (SignerTaskOption signeropt : signersOption) {
						SignerOption so = new SignerOption();
						FileLocation ksfile = TaskOptionUtils.toFileLocation(signeropt.getKeyStore(), taskcontext);
						//if keystore file is null, debug signing is used
						so.setSigning(ksfile, signeropt.getStorePassword(), signeropt.getAlias(),
								signeropt.getKeyPassword());
						so.setV1SigningEnabled(signeropt.getV1SigningEnabled());
						so.setV1SignerName(signeropt.getV1SignerName());
						so.setV2SigningEnabled(signeropt.getV2SigningEnabled());
						so.setV3SigningEnabled(signeropt.getV3SigningEnabled());
						V4SigningEnabledInputTaskOption v4signopt = signeropt.getV4SigningEnabled();
						if (v4signopt != null) {
							so.setV4SigningEnabled(v4signopt.getValue());
						}
						Boolean v4merkle = signeropt.getV4NoMerkleTree();
						if (v4merkle != null) {
							so.setV4NoMerkleTree(v4merkle);
						}
						signers.add(so);
					}
				}
				workertask.setSigners(signers);

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
	}

	private static String toSignedOutputApkFileName(String apkfname) {
		String ext = FileUtils.getExtension(apkfname);
		if (ext == null) {
			return apkfname + "-signed.apk";
		}
		String withoutextension = apkfname.substring(0, apkfname.length() - (ext.length() + 1));
		return withoutextension + "-signed." + ext;
	}
}
