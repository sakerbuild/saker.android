package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;

@SakerTest
public class SimpleAapt2LinkTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		SakerPath apkoutpath = PATH_BUILD_DIRECTORY.resolve("saker.android.aapt2.link/default/output.apk");
		SakerPath strxmlpath = PATH_WORKING_DIRECTORY.resolve("res/values/strings.xml");
		SakerPath manifestpath = PATH_WORKING_DIRECTORY.resolve("AndroidManifest.xml");
		SakerPath rjavapath = PATH_BUILD_DIRECTORY.resolve("saker.android.aapt2.link/default/java/com/example/R.java");
		SakerPath resoutpath = PATH_BUILD_DIRECTORY
				.resolve("saker.android.aapt2.compile/default/values/strings.xml/values_strings.arsc.flat");

		runScriptTask("build");
		ByteArrayRegion bytes1 = files.getAllBytes(apkoutpath);
		ByteArrayRegion resbytes1 = files.getAllBytes(resoutpath);
		//test R.java existence
		files.getFileAttributes(rjavapath);

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(strxmlpath, files.getAllBytes(strxmlpath).toString().replace("Example", "Modified"));
		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		//test R.java existence
		files.getFileAttributes(rjavapath);
		ByteArrayRegion resbytes2 = files.getAllBytes(resoutpath);
		assertFalse(resbytes1.regionEquals(resbytes2), "compiled resource unchanged");
		ByteArrayRegion bytes2 = files.getAllBytes(apkoutpath);
		//assert that the output file changed
		assertFalse(bytes1.regionEquals(bytes2), "apk output unchanged");

		files.putFile(manifestpath, files.getAllBytes(manifestpath).toString().replace("21", "23"));
		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		//test R.java existence
		files.getFileAttributes(rjavapath);
		ByteArrayRegion bytes3 = files.getAllBytes(apkoutpath);
		assertFalse(bytes2.regionEquals(bytes3), "apk output unchanged");
	}

}
