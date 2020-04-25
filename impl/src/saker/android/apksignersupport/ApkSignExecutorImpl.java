package saker.android.apksignersupport;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.UUID;

import com.android.apksigner.ApkSignerTool;

import saker.android.impl.apk.sign.ApkSignExecutor;
import saker.android.impl.apk.sign.SignApkWorkerTaskFactory;
import saker.android.impl.apk.sign.SignerOption;
import saker.android.impl.support.SupportToolSystemExitError;
import saker.build.file.path.SakerPath;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.bundle.NestBundleClassLoader;
import saker.sdk.support.api.SDKReference;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class ApkSignExecutorImpl implements ApkSignExecutor {
	private static final String DEBUG_KEYSTORE_NAME = "android_debug.jks";
	private static final String DEBUG_KEY_ALIAS = "androiddebugkey";
	private static final String DEBUG_KEY_PASSWORD = "android";
	private static final char[] DEBUG_KEY_PASSWORD_CHARS = DEBUG_KEY_PASSWORD.toCharArray();

	public static ApkSignExecutor createApkSignExecutor() {
		return new ApkSignExecutorImpl();
	}

	@Override
	public void run(TaskContext taskcontext, SignApkWorkerTaskFactory workertask,
			NavigableMap<String, SDKReference> sdkrefs, Path inputfilelocalpath, Path outputfilelocalpath)
			throws Exception {
		NestBundleClassLoader nestcl = (NestBundleClassLoader) ApkSignExecutor.class.getClassLoader();

		List<SignerOption> signers = workertask.getSigners();
		if (ObjectUtils.isNullOrEmpty(signers)) {
			SignerOption debugsigner = new SignerOption();

			debugsigner.setSigning(null, DEBUG_KEY_PASSWORD, DEBUG_KEY_ALIAS, DEBUG_KEY_PASSWORD);
			signers = ImmutableUtils.singletonList(debugsigner);
		}

		try {
			ArrayList<String> args = new ArrayList<>(11 + 4 + 1);

			args.add("sign");
			boolean first = true;
			for (SignerOption opt : signers) {
				if (!first) {
					args.add("--next-signer");
				}
				appendSignerArguments(opt, taskcontext, nestcl, args);
				first = false;
			}

			args.add("--out");
			args.add(outputfilelocalpath.toString());

			args.add(inputfilelocalpath.toString());
			ApkSignerTool.main(args.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		} catch (SupportToolSystemExitError e) {
			throw new IOException("apksigner execution failed: " + e.getExitCode());
		} catch (NoClassDefFoundError e) {
			throw new IOException(
					"Failed to run apksigner. This may be due to the class version of apksigner is not supported by the current JVM.",
					e);
		}
	}

	private static void appendSignerArguments(SignerOption opt, TaskContext taskcontext, NestBundleClassLoader nestcl,
			ArrayList<String> args) throws IOException, Exception {
		FileLocation ksfilelocation = opt.getKeyStoreFile();
		String[] storepassword = { null };
		String[] keypassword = { null };
		Path[] keystorepath = { null };
		String[] alias = { null };
		if (ksfilelocation == null) {
			Path storagepath = nestcl.getBundle().getBundleStoragePath();
			Path debugkeypath = storagepath.resolve(DEBUG_KEYSTORE_NAME);
			if (!Files.exists(debugkeypath)) {
				synchronized ((ApkSignExecutorImpl.class.getName() + ":debug_key:" + debugkeypath).intern()) {
					Files.createDirectories(debugkeypath.getParent());
					Path temppath = debugkeypath.resolveSibling(DEBUG_KEYSTORE_NAME + "_" + UUID.randomUUID());
					try {
						generateDebugKeyStoreToPath(temppath);

						try {
							//no REPLACE_EXISTING
							Files.move(temppath, debugkeypath);
						} catch (IOException e) {
							//failed to move, might be because others concurrently created it
						}
					} finally {
						Files.deleteIfExists(temppath);
					}
				}
			}

			keystorepath[0] = debugkeypath;
			storepassword[0] = DEBUG_KEY_PASSWORD;
			keypassword[0] = DEBUG_KEY_PASSWORD;
			alias[0] = DEBUG_KEY_ALIAS;
		} else {
			ksfilelocation.accept(new FileLocationVisitor() {
				@Override
				public void visit(ExecutionFileLocation loc) {
					SakerPath inputpath = loc.getPath();
					MirroredFileContents mirroredinputfile;
					try {
						mirroredinputfile = taskcontext.getTaskUtilities().mirrorFileAtPathContents(inputpath);
					} catch (IOException e) {
						taskcontext.reportInputFileDependency(null, inputpath,
								CommonTaskContentDescriptors.IS_NOT_FILE);
						FileNotFoundException fnfe = new FileNotFoundException(loc.toString());
						fnfe.initCause(e);
						ObjectUtils.sneakyThrow(fnfe);
						return;
					}
					taskcontext.reportInputFileDependency(null, inputpath, mirroredinputfile.getContents());
					keystorepath[0] = mirroredinputfile.getPath();
				}
			});
			storepassword[0] = opt.getKeyStorePassword();
			keypassword[0] = opt.getKeyPassword();
			alias[0] = opt.getAlias();
		}

		args.add("--ks");
		args.add(keystorepath[0].toString());

		if (alias != null) {
			args.add("--ks-key-alias");
			args.add(alias[0]);
		}
		if (storepassword != null) {
			args.add("--ks-pass");
			args.add("pass:" + storepassword[0]);
		}
		if (keypassword != null) {
			args.add("--key-pass");
			args.add("pass:" + keypassword[0]);
		}
		Boolean v1signingenabled = opt.getV1SigningEnabled();
		String v1signername = opt.getV1SignerName();
		Boolean v2signingenabled = opt.getV2SigningEnabled();
		Boolean v3signingenabled = opt.getV3SigningEnabled();
		String v4signing = opt.getV4SigningEnabled();
		boolean v4nomerkletree = opt.getV4NoMerkleTree();
		if (v1signingenabled != null) {
			args.add("--v1-signing-enabled");
			args.add(v1signingenabled.toString());
		}
		if (v2signingenabled != null) {
			args.add("--v2-signing-enabled");
			args.add(v2signingenabled.toString());
		}
		if (v3signingenabled != null) {
			args.add("--v3-signing-enabled");
			args.add(v3signingenabled.toString());
		}
		if (v4signing != null) {
			args.add("--v4-signing-enabled");
			args.add(v4signing);
		}
		if (v4nomerkletree) {
			args.add("--v4-no-merkle-tree");
		}
		if (v1signername != null) {
			args.add("--v1-signer-name");
			args.add(v1signername);
		}
	}

	private static void generateDebugKeyStoreToPath(Path path) throws Exception {
		//partially based on
		//https://stackoverflow.com/questions/1615871/creating-an-x509-certificate-in-java-without-bouncycastle

		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, DEBUG_KEY_PASSWORD_CHARS);

		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		KeyPair pair = keyPairGenerator.generateKeyPair();

		X509CertInfo info = new X509CertInfo();
		Date from = new Date();
		CertificateValidity interval = new CertificateValidity(from, new Date(from.getTime() + 365 * 100 * 86400000L));
		BigInteger sn = new BigInteger(64, new SecureRandom());
		X500Name owner = new X500Name("CN=Android Debug, OU=, O=Android, L=, S=, C=US");

		info.set(X509CertInfo.VALIDITY, interval);
		info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
		info.set(X509CertInfo.SUBJECT, owner);
		info.set(X509CertInfo.ISSUER, owner);
		info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
		info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
		AlgorithmId algo = new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid);
		info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

		PrivateKey privkey = pair.getPrivate();

		String algorithm = "SHA256withRSA";

		X509CertImpl cert = new X509CertImpl(info);
		cert.sign(privkey, algorithm);

		algo = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
		info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
		cert = new X509CertImpl(info);
		cert.sign(privkey, algorithm);

		ks.setKeyEntry(DEBUG_KEY_ALIAS, privkey, DEBUG_KEY_PASSWORD_CHARS, new Certificate[] { cert });

		try (OutputStream os = Files.newOutputStream(path)) {
			ks.store(os, DEBUG_KEY_PASSWORD_CHARS);
		}
	}
}
