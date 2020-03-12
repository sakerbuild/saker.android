package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;

@SakerTest
public class SimpleAidlTaskTest extends BaseAndroidTestCase {
	//example Aidl taken from https://developer.android.com/guide/components/aidl

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		SakerPath javaoutpath = PATH_BUILD_DIRECTORY
				.resolve("saker.android.aidl/aidl/java/com/example/android/IRemoteService.java");

		runScriptTask("build");
		ByteArrayRegion bytes1 = files.getAllBytes(javaoutpath);

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		SakerPath aidlpath = PATH_WORKING_DIRECTORY.resolve("aidl/com/example/android/IRemoteService.aidl");
		files.putFile(aidlpath, files.getAllBytes(aidlpath).toString().replace("int anInt", "long anInt"));

		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		ByteArrayRegion bytes2 = files.getAllBytes(javaoutpath);
		//assert that the output file changed
		assertFalse(bytes1.regionEquals(bytes2));
	}

}
