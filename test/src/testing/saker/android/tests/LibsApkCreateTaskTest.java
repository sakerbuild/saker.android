package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class LibsApkCreateTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");

		SakerPath apkpath = SakerPath.valueOf(res.getTargetTaskResult("apkpath").toString());
		assertContainsZipEntry(apkpath, "lib/armeabi/libmain.so");
		assertContainsZipEntry(apkpath, "lib/x86/libmain.so");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
