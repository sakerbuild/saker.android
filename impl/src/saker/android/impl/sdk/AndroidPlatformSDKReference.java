package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.file.path.SakerPath;
import saker.sdk.support.api.SDKReference;

public class AndroidPlatformSDKReference implements SDKReference, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String PROPERTY_VERSION = "version";

	public static final String PATH_ANDROID_JAR = "jar.android";

	private static final SakerPath ANDROID_JAR_PATH = SakerPath.valueOf("android.jar");

	public static final String SDK_NAME = "AndroidPlatform";

	private String version;
	/**
	 * Points to the platforms/{@link #version}/ directory
	 */
	//the base path is transient, as the SDK contents are uniquely identified by the version 
	private transient SakerPath basePath;

	/**
	 * For {@link Externalizable}.
	 */
	public AndroidPlatformSDKReference() {
	}

	public AndroidPlatformSDKReference(String version, SakerPath basePath) {
		Objects.requireNonNull(version, "android sdk version");
		Objects.requireNonNull(basePath, "android sdk base path");
		this.version = version;
		this.basePath = basePath;
	}

	@Override
	public SakerPath getPath(String identifier) throws Exception {
		if (identifier == null) {
			return null;
		}
		switch (identifier) {
			case PATH_ANDROID_JAR: {
				return basePath.resolve(ANDROID_JAR_PATH);
			}
			default: {
				break;
			}
		}
		return null;
	}

	@Override
	public String getProperty(String identifier) throws Exception {
		if (identifier == null) {
			return null;
		}
		switch (identifier) {
			case PROPERTY_VERSION: {
				return version;
			}
			default: {
				break;
			}
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(version);
		out.writeObject(basePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		version = (String) in.readObject();
		basePath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		AndroidPlatformSDKReference other = (AndroidPlatformSDKReference) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + basePath + "]";
	}
}
