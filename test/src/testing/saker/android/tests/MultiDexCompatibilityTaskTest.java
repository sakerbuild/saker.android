package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class MultiDexCompatibilityTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");

		SakerPath apkpath = SakerPath.valueOf(res.getTargetTaskResult("apkpath").toString());
		assertContainsZipEntry(apkpath, "classes.dex");
		assertContainsZipEntry(apkpath, "classes2.dex");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
