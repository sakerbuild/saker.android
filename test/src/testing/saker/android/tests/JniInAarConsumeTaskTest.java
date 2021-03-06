package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class JniInAarConsumeTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");

		SakerPath apkpath = SakerPath.valueOf(res.getTargetTaskResult("apkpath").toString());
		assertContainsZipEntry(apkpath, "lib/armeabi-v7a/libMylib.so");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
