package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;

@SakerTest
public class SimpleD8TaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		runScriptTask("build");
		//check existence
		ByteArrayRegion bytes1 = files
				.getAllBytes(PATH_BUILD_DIRECTORY.resolve("saker.android.d8/src/classes/classes.dex"));

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		SakerPath mainjavapath = PATH_WORKING_DIRECTORY.resolve("src/test/Main.java");
		files.putFile(mainjavapath, files.getAllBytes(mainjavapath).toString().replace("//f", "//f\npublic int i;"));
		runScriptTask("build");
		ByteArrayRegion bytes2 = files
				.getAllBytes(PATH_BUILD_DIRECTORY.resolve("saker.android.d8/src/classes/classes.dex"));
		//assert that the output dex file changed
		assertFalse(bytes1.regionEquals(bytes2));
	}

}
