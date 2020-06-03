package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.android.impl.AndroidUtils;
import saker.build.file.path.SakerPath;
import saker.sdk.support.api.SDKReference;

public class AndroidNdkSDKReference implements SDKReference, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String SDK_NAME = "AndroidNDK";

	public static final String PROPERTY_VERSION = "version";

	public static final String PATH_HOME = "home";

	public static final String PATH_CLANG_EXE = "exe.clang";
	public static final String PATH_CLANGXX_EXE = "exe.clang++";
	public static final String PATH_CLANGPP_EXE = "exe.clangpp";

	public static final String PATH_STRIP_EXE = "exe.strip";

	private static final SakerPath SP_CLANG_EXE_LINUX = SakerPath
			.valueOf("toolchains/llvm/prebuilt/linux-x86_64/bin/clang");
	private static final SakerPath SP_CLANG_EXE_WINDOWS = SakerPath
			.valueOf("toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe");
	private static final SakerPath SP_CLANG_EXE_DARWIN = SakerPath
			.valueOf("toolchains/llvm/prebuilt/darwin-x86_64/bin/clang");

	private static final SakerPath SP_CLANGXX_EXE_LINUX = SakerPath
			.valueOf("toolchains/llvm/prebuilt/linux-x86_64/bin/clang++");
	private static final SakerPath SP_CLANGXX_EXE_WINDOWS = SakerPath
			.valueOf("toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe");
	private static final SakerPath SP_CLANGXX_EXE_DARWIN = SakerPath
			.valueOf("toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++");

	private static final SakerPath SP_STRIP_EXE_LINUX = SakerPath
			.valueOf("toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip");
	private static final SakerPath SP_STRIP_EXE_WINDOWS = SakerPath
			.valueOf("toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe");
	private static final SakerPath SP_STRIP_EXE_DARWIN = SakerPath
			.valueOf("toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip");

	/**
	 * The value of the Pkg.Revision value in the source.properties file.
	 */
	private String version;

	private transient SakerPath basePath;
	private transient int osType;

	/**
	 * For {@link Externalizable}.
	 */
	public AndroidNdkSDKReference() {
	}

	public AndroidNdkSDKReference(String version, SakerPath basePath, int osType) {
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
			case PATH_HOME: {
				return basePath;
			}
			case PATH_CLANG_EXE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(SP_CLANG_EXE_LINUX);
					}
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(SP_CLANG_EXE_WINDOWS);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(SP_CLANG_EXE_DARWIN);
					}
					default: {
						throw new UnsupportedOperationException("Unrecognized OS type: " + osType);
					}
				}
			}
			case PATH_CLANGPP_EXE:
			case PATH_CLANGXX_EXE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(SP_CLANGXX_EXE_LINUX);
					}
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(SP_CLANGXX_EXE_WINDOWS);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(SP_CLANGXX_EXE_DARWIN);
					}
					default: {
						throw new UnsupportedOperationException("Unrecognized OS type: " + osType);
					}
				}
			}
			case PATH_STRIP_EXE: {
				switch (osType) {
					case AndroidUtils.SDK_OS_TYPE_LINUX: {
						return basePath.resolve(SP_STRIP_EXE_LINUX);
					}
					case AndroidUtils.SDK_OS_TYPE_WINDOWS: {
						return basePath.resolve(SP_STRIP_EXE_WINDOWS);
					}
					case AndroidUtils.SDK_OS_TYPE_MACOS: {
						return basePath.resolve(SP_STRIP_EXE_DARWIN);
					}
					default: {
						throw new UnsupportedOperationException("Unrecognized OS type: " + osType);
					}
				}
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
				return null;
			}
		}
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
		AndroidNdkSDKReference other = (AndroidNdkSDKReference) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + version + "(" + basePath + ")]";
	}
}
