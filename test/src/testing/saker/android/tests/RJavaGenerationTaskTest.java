package testing.saker.android.tests;

import saker.build.file.path.SakerPath;
import testing.saker.SakerTest;

@SakerTest
public class RJavaGenerationTaskTest extends BaseAndroidTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		SakerPath strxmlpath = PATH_WORKING_DIRECTORY.resolve("res/values/strings.xml");

		CombinedTargetTaskResult res = runScriptTask("build");
		//check that output class file exists
		files.getFileAttributes(
				SakerPath.valueOf(res.getTargetTaskResult("cdir").toString()).resolve("test/Main.class"));

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(strxmlpath, files.getAllBytes(strxmlpath).toString().replace("Example", "Modified"));
		runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
	}

}
