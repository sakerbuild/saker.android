package saker.android.impl.apk.sign;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;

public class SignerOption implements Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation keyStoreFile;
	private String alias;
	private String keyStorePassword;
	private String keyPassword;

	private Boolean v1SigningEnabled;
	private String v1SignerName;
	private Boolean v2SigningEnabled;
	private Boolean v3SigningEnabled;
	private String v4SigningEnabled;
	private boolean v4NoMerkleTree;

	/**
	 * For {@link Externalizable}.
	 */
	public SignerOption() {
	}

	public void setV1SigningEnabled(Boolean v1SigningEnabled) {
		this.v1SigningEnabled = v1SigningEnabled;
	}

	public void setV1SignerName(String v1SignerName) {
		this.v1SignerName = v1SignerName;
	}

	public void setV2SigningEnabled(Boolean v2SigningEnabled) {
		this.v2SigningEnabled = v2SigningEnabled;
	}

	public void setV3SigningEnabled(Boolean v3SigningEnabled) {
		this.v3SigningEnabled = v3SigningEnabled;
	}

	public void setV4SigningEnabled(String v4SigningEnabled) {
		this.v4SigningEnabled = v4SigningEnabled;
	}

	public void setV4NoMerkleTree(boolean v4NoMerkleTree) {
		this.v4NoMerkleTree = v4NoMerkleTree;
	}

	public Boolean getV1SigningEnabled() {
		return v1SigningEnabled;
	}

	public String getV1SignerName() {
		return v1SignerName;
	}

	public Boolean getV2SigningEnabled() {
		return v2SigningEnabled;
	}

	public Boolean getV3SigningEnabled() {
		return v3SigningEnabled;
	}

	public String getV4SigningEnabled() {
		return v4SigningEnabled;
	}

	public boolean getV4NoMerkleTree() {
		return v4NoMerkleTree;
	}

	public void setSigning(FileLocation keystore, String keystorepassword, String alias, String keypassword) {
		if (keystore != null) {
			this.keyStoreFile = keystore;
			this.keyStorePassword = keystorepassword;
			this.alias = alias;
			this.keyPassword = keypassword;
		} else {
			this.keyStoreFile = null;
			this.keyStorePassword = null;
			this.alias = null;
			this.keyPassword = null;
		}
	}

	public FileLocation getKeyStoreFile() {
		return keyStoreFile;
	}

	public String getKeyPassword() {
		return keyPassword;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	public String getAlias() {
		return alias;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(v1SigningEnabled);
		out.writeObject(v1SignerName);
		out.writeObject(v2SigningEnabled);
		out.writeObject(v3SigningEnabled);
		out.writeObject(v4SigningEnabled);
		out.writeBoolean(v4NoMerkleTree);
		out.writeObject(keyStoreFile);
		out.writeObject(alias);
		out.writeObject(keyStorePassword);
		out.writeObject(keyPassword);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		v1SigningEnabled = SerialUtils.readExternalObject(in);
		v1SignerName = SerialUtils.readExternalObject(in);
		v2SigningEnabled = SerialUtils.readExternalObject(in);
		v3SigningEnabled = SerialUtils.readExternalObject(in);
		v4SigningEnabled = SerialUtils.readExternalObject(in);
		v4NoMerkleTree = in.readBoolean();
		keyStoreFile = (FileLocation) in.readObject();
		alias = (String) in.readObject();
		keyStorePassword = (String) in.readObject();
		keyPassword = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((alias == null) ? 0 : alias.hashCode());
		result = prime * result + ((keyStoreFile == null) ? 0 : keyStoreFile.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SignerOption other = (SignerOption) obj;
		if (alias == null) {
			if (other.alias != null)
				return false;
		} else if (!alias.equals(other.alias))
			return false;
		if (keyPassword == null) {
			if (other.keyPassword != null)
				return false;
		} else if (!keyPassword.equals(other.keyPassword))
			return false;
		if (keyStoreFile == null) {
			if (other.keyStoreFile != null)
				return false;
		} else if (!keyStoreFile.equals(other.keyStoreFile))
			return false;
		if (keyStorePassword == null) {
			if (other.keyStorePassword != null)
				return false;
		} else if (!keyStorePassword.equals(other.keyStorePassword))
			return false;
		if (v1SignerName == null) {
			if (other.v1SignerName != null)
				return false;
		} else if (!v1SignerName.equals(other.v1SignerName))
			return false;
		if (v1SigningEnabled == null) {
			if (other.v1SigningEnabled != null)
				return false;
		} else if (!v1SigningEnabled.equals(other.v1SigningEnabled))
			return false;
		if (v2SigningEnabled == null) {
			if (other.v2SigningEnabled != null)
				return false;
		} else if (!v2SigningEnabled.equals(other.v2SigningEnabled))
			return false;
		if (v3SigningEnabled == null) {
			if (other.v3SigningEnabled != null)
				return false;
		} else if (!v3SigningEnabled.equals(other.v3SigningEnabled))
			return false;
		if (v4NoMerkleTree != other.v4NoMerkleTree)
			return false;
		if (v4SigningEnabled == null) {
			if (other.v4SigningEnabled != null)
				return false;
		} else if (!v4SigningEnabled.equals(other.v4SigningEnabled))
			return false;
		return true;
	}

}
