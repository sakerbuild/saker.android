package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class AndroidJarBootClassPathTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		files.getFileAttributes(
				SakerPath.valueOf(res.getTargetTaskResult("cdir").toString()).resolve("test/MainActivity.class"));

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
