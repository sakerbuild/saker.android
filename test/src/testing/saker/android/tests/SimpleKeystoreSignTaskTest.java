package testing.saker.android.tests;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import testing.saker.SakerTest;

@SakerTest
public class SimpleKeystoreSignTaskTest extends BaseAndroidTestCase {
	//keystore generated with command:
	//  keytool -genkey -noprompt -keystore keystore -alias testalias -storepass storepass -keypass keypass -keyalg RSA -validity 35600 -dname "CN=Android Test, OU=, O=Android, L=, S=, C=US"

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		SakerPath outapkpath = SakerPath.valueOf(res.getTargetTaskResult("apkpath").toString());
		files.getFileAttributes(outapkpath);

		Certificate certificate;
		try (InputStream ksin = LocalFileProvider.getInstance()
				.openInputStream(getWorkingDirectory().resolve("keystore"))) {
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(ksin, "storepass".toCharArray());
			certificate = ks.getCertificate("testalias");
		}

		Path outapklocalpath = getBuildDirectory().resolve("out.apk");
		//verify signature via jarfile
		try (InputStream outapkin = ByteSource.toInputStream(files.openInput(outapkpath))) {
			FileUtils.writeStreamEqualityCheckTo(outapkin, outapklocalpath);
		}
		try (JarFile jf = new JarFile(outapklocalpath.toFile(), true)) {
			Enumeration<JarEntry> entries = jf.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				try (InputStream entryin = jf.getInputStream(entry)) {
					StreamUtils.consumeStream(entryin);
				}
				verifyEntrySigned(entry, certificate);
			}
		}

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

	}

	private static void verifyEntrySigned(JarEntry entry, Certificate certificate) throws AssertionError {
		String entryname = entry.getName();
		if (!entryname.startsWith("META-INF/")) {
			Certificate[] certs = entry.getCertificates();
			assertNonNull(certs, entryname);
			assertEquals(certs.length, 1, entryname);
			assertEquals(certs[0], certificate);
		}
	}

}
