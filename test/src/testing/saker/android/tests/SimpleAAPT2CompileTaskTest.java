package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;

@SakerTest
public class SimpleAAPT2CompileTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		SakerPath resoutpath = PATH_BUILD_DIRECTORY
				.resolve("saker.android.aapt2.compile/default/values/strings.xml/values_strings.arsc.flat");
		SakerPath xmlpath = PATH_WORKING_DIRECTORY.resolve("res/values/strings.xml");

		runScriptTask("build");
		ByteArrayRegion bytes1 = files.getAllBytes(resoutpath);

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(xmlpath, files.getAllBytes(xmlpath).toString().replace("Example", "Modified"));
		runScriptTask("build");
		ByteArrayRegion bytes2 = files.getAllBytes(resoutpath);
		//assert that the output file changed
		assertFalse(bytes1.regionEquals(bytes2));
	}

}
