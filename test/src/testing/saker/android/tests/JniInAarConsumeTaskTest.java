package testing.saker.android.tests;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.ByteSource;
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
