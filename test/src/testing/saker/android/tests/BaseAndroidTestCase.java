package testing.saker.android.tests;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

public abstract class BaseAndroidTestCase extends NestRepositoryCachingEnvironmentTestCase {
	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		LinkedHashSet<EnvironmentTestCaseConfiguration> result = new LinkedHashSet<>();
		for (EnvironmentTestCaseConfiguration tcc : super.getTestConfigurations()) {
			TreeMap<String, String> userparams = ObjectUtils.newTreeMap(tcc.getEnvironmentUserParameters());
			userparams.put("saker.android.sdk.install.location",
					testParameters.get("AndroidSDKLocationEnvironmentUserParameter"));
			userparams.put("saker.android.ndk.install.location",
					testParameters.get("AndroidNDKLocationEnvironmentUserParameter"));
			result.add(EnvironmentTestCaseConfiguration.builder(tcc).setEnvironmentUserParameters(userparams).build());
		}
		return result;
	}

	@Override
	protected String getRepositoryStorageConfiguration() {
		//TODO exclude the saker.android bundles from the server storage
		return "[:params, :server]";
	}

	protected void assertContainsZipEntry(SakerPath apkpath, String entryname) throws IOException, NoSuchFileException {
		Set<String> found = new TreeSet<>();
		try (ZipInputStream jis = new ZipInputStream(ByteSource.toInputStream(files.openInput(apkpath)))) {
			for (ZipEntry entry; (entry = jis.getNextEntry()) != null;) {
				if (entryname.equals(entry.getName())) {
					return;
				}
				found.add(entry.getName());
			}
		}
		found.forEach(System.err::println);
		fail("Missing entry: " + entryname + " in " + apkpath);
	}
}
