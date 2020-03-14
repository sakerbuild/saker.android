package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.android.impl.AndroidUtils;
import saker.build.file.path.SakerPath;
import saker.sdk.support.api.SDKReference;

public class AndroidBuildToolsSDKReference implements SDKReference, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String PATH_D8_JAR = "lib.jar.d8";
	public static final String PATH_APKSIGNER_JAR = "lib.jar.apksigner";
	public static final String PATH_CORE_LAMBDA_STUBS_JAR = "jar.core_lambda_stubs";

	public static final String PATH_AAPT2_EXECUTABLE = "exe.aapt2";
	public static final String PATH_ZIPALIGN_EXECUTABLE = "exe.zipalign";
	public static final String PATH_AIDL_EXECUTABLE = "exe.aidl";
	
	public static final String PATH_LIB_JNI_AAPT2 = "lib.jni.aapt2";
	public static final String PATH_LIB64_JNI_AAPT2 = "lib.jni64.aapt2";

	public static final String PROPERTY_VERSION = "version";

	private static final SakerPath LIB_D8JAR_PATH = SakerPath.valueOf("lib/d8.jar");
	private static final SakerPath LIB_APKSIGNERJAR_PATH = SakerPath.valueOf("lib/apksigner.jar");

	private static final SakerPath WINDOWS_EXE_AAPT2_PATH = SakerPath.valueOf("aapt2.exe");
	private static final SakerPath MACOS_EXE_AAPT2_PATH = SakerPath.valueOf("aapt2");
	private static final SakerPath LINUX_EXE_AAPT2_PATH = SakerPath.valueOf("aapt2");

	private static final SakerPath WINDOWS_EXE_ZIPALIGN_PATH = SakerPath.valueOf("zipalign.exe");
	private static final SakerPath MACOS_EXE_ZIPALIGN_PATH = SakerPath.valueOf("zipalign");
	private static final SakerPath LINUX_EXE_ZIPALIGN_PATH = SakerPath.valueOf("zipalign");

	private static final SakerPath WINDOWS_EXE_AIDL_PATH = SakerPath.valueOf("aidl.exe");
	private static final SakerPath MACOS_EXE_AIDL_PATH = SakerPath.valueOf("aidl");
	private static final SakerPath LINUX_EXE_AIDL_PATH = SakerPath.valueOf("aidl");

	private static final SakerPath CORE_LAMBDA_STUBS_PATH = SakerPath.valueOf("core-lambda-stubs.jar");

	public static final String SDK_NAME = "AndroidBuildTools";

	private String version;
	/**
	 * Points to the build-tools/{@link #version}/ directory
	 */
	//the base path is transient, as the SDK contents are uniquely identified by the version 
	private transient SakerPath basePath;

	private transient int osType;

	/**
	 * For {@link Externalizable}.
	 */
	public AndroidBuildToolsSDKReference() {
	}

	public AndroidBuildToolsSDKReference(String version, SakerPath basePath, int osType) {
		Objects.requireNonNull(version, "android sdk version");
		Objects.requireNonNull(basePath, "android sdk base path");
		this.version = version;
		this.basePath = basePath;
		this.osType = osType;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public SakerPath getPath(String identifier) throws Exception {
		if (identifier == null) {
			return null;
		}
		switch (identifier) {
			case PATH_D8_JAR: {
				return basePath.resolve(LIB_D8JAR_PATH);
			}
			case PATH_APKSIGNER_JAR: {
				return basePath.resolve(LIB_APKSIGNERJAR_PATH);
			}
			case PATH_AAPT2_EXECUTABLE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(WINDOWS_EXE_AAPT2_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(LINUX_EXE_AAPT2_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(MACOS_EXE_AAPT2_PATH);
					}
					default: {
						return null;
					}
				}
			}
			case PATH_ZIPALIGN_EXECUTABLE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(WINDOWS_EXE_ZIPALIGN_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(LINUX_EXE_ZIPALIGN_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(MACOS_EXE_ZIPALIGN_PATH);
					}
					default: {
						return null;
					}
				}
			}
			case PATH_AIDL_EXECUTABLE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(WINDOWS_EXE_AIDL_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(LINUX_EXE_AIDL_PATH);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(MACOS_EXE_AIDL_PATH);
					}
					default: {
						return null;
					}
				}
			}
			case PATH_CORE_LAMBDA_STUBS_JAR: {
				return basePath.resolve(CORE_LAMBDA_STUBS_PATH);
			}
			case PATH_LIB_JNI_AAPT2: {
				String libfn = getAAPT2LibraryFileName();
				if (libfn != null) {
					return basePath.resolve(libfn);
				}
				break;
			}
			case PATH_LIB64_JNI_AAPT2: {
				String libfn = getAAPT2LibraryFileName();
				if (libfn != null) {
					return basePath.resolve("lib64").resolve(libfn);
				}
				break;
			}
			default: {
				break;
			}
		}
		return null;
	}

	private String getAAPT2LibraryFileName() {
		switch (osType) {
			case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
				return "libaapt2_jni.dll";
			}
			case AndroidUtils.SDK_OS_TYPE_LINUX: {
				return "libaapt2_jni.so";
			}
			case AndroidUtils.SDK_OS_TYPE_MACOS: {
				return "libaapt2_jni.dylib";
			}
			default: {
				return null;
			}
		}
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
		out.writeInt(osType);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		version = (String) in.readObject();
		basePath = (SakerPath) in.readObject();
		osType = in.readInt();
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
		AndroidBuildToolsSDKReference other = (AndroidBuildToolsSDKReference) obj;
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
