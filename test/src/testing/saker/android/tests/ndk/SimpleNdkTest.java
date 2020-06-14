package testing.saker.android.tests.ndk;

import testing.saker.SakerTest;
import testing.saker.android.tests.BaseAndroidTestCase;

@SakerTest
public class SimpleNdkTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
