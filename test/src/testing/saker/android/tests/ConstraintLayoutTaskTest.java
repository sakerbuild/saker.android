package testing.saker.android.tests;

import testing.saker.SakerTest;

@SakerTest
public class ConstraintLayoutTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
