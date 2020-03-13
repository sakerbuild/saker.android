package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class SimpleZipalignTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		files.getFileAttributes(SakerPath.valueOf(res.getTargetTaskResult("apkpath").toString()));
		
		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
