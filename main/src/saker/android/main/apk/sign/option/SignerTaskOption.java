package saker.android.main.apk.sign.option;

import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.std.main.file.option.FileLocationTaskOption;

@NestFieldInformation(value = "KeyStore",
		type = @NestTypeUsage(FileLocationTaskOption.class),
		info = @NestInformation("The keystore to be used when signing the APK.\n"
				+ "Corresponds to the --ks argument of the apksigner tool.\n"
				+ "If the KeyStore is not specified, or set to null, the automatically generated debug key is used "
				+ "for signing."))
@NestFieldInformation(value = "Alias",
		type = @NestTypeUsage(FileLocationTaskOption.class),
		info = @NestInformation("The name of the alias that represents the signer's private key and certificate data within the KeyStore. "
				+ "If the KeyStore associated with the signer contains multiple keys, you must specify this option.\n"
				+ "Corresponds to the --ks-key-alias argument of the apksigner tool."))
@NestFieldInformation(value = "StorePassword",
		type = @NestTypeUsage(FileLocationTaskOption.class),
		info = @NestInformation("The password for the KeyStore that contains the signer's private key and certificate.\n"
				+ "Corresponds to the --ks-pass parameter with pass: argument of the apksigner tool."))
@NestFieldInformation(value = "KeyPassword",
		type = @NestTypeUsage(FileLocationTaskOption.class),
		info = @NestInformation("The password for the signer's private key, which is needed if the private key is password-protected.\n"
				+ "Corresponds to the --key-pass parameter with pass: argument of the apksigner tool."))

@NestFieldInformation(value = "V1SigningEnabled",
		type = @NestTypeUsage(Boolean.class),
		info = @NestInformation("Determines whether apksigner signs the given APK package using the traditional, JAR-based signing scheme.\n"
				+ "Corresponds to the --v1-signing-enabled argument of the apksigner tool."))
@NestFieldInformation(value = "V1SignerName",
		type = @NestTypeUsage(String.class),
		info = @NestInformation("The base name for the files that comprise the JAR-based signature for the current signer.\n"
				+ "Corresponds to the --v1-signer-name argument of the apksigner tool."))
@NestFieldInformation(value = "V2SigningEnabled",
		type = @NestTypeUsage(Boolean.class),
		info = @NestInformation("Determines whether apksigner signs the given APK package using the APK Signature Scheme v2.\n"
				+ "Corresponds to the --v2-signing-enabled argument of the apksigner tool."))
@NestFieldInformation(value = "V3SigningEnabled",
		type = @NestTypeUsage(Boolean.class),
		info = @NestInformation("Determines whether apksigner signs the given APK package using the APK Signature Scheme v3.\n"
				+ "Corresponds to the --v3-signing-enabled argument of the apksigner tool."))
@NestFieldInformation(value = "V4SigningEnabled",
		type = @NestTypeUsage(V4SigningEnabledInputTaskOption.class),
		info = @NestInformation("Determines whether apksigner signs the given APK package using the APK Signature Scheme v4.\n"
				+ "Corresponds to the --v4-signing-enabled argument of the apksigner tool."))
@NestFieldInformation(value = "V4NoMerkleTree",
		type = @NestTypeUsage(V4SigningEnabledInputTaskOption.class),
		info = @NestInformation("With this flag, apksigner produces an APK Signature Scheme v4 .idsig file without the full Merkle tree embedded.\n"
				+ "Corresponds to the --v4-no-merkle-tree flag of the apksigner tool."))

public interface SignerTaskOption {
	public FileLocationTaskOption getKeyStore();

	public String getAlias();

	public String getStorePassword();

	public String getKeyPassword();

	public Boolean getV1SigningEnabled();

	public String getV1SignerName();

	public Boolean getV2SigningEnabled();

	public Boolean getV3SigningEnabled();

	public V4SigningEnabledInputTaskOption getV4SigningEnabled();

	public Boolean getV4NoMerkleTree();
}
