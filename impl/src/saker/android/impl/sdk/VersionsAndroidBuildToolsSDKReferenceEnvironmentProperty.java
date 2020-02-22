package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;
import java.util.function.Predicate;

import saker.android.impl.AndroidUtils;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.exc.SDKNotFoundException;

public class VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty
		implements EnvironmentProperty<SDKReference>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<String> versions;

	/**
	 * For {@link Externalizable}.
	 */
	public VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty() {
	}

	public VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty(Set<String> versions) {
		this.versions = versions;
	}

	@Override
	public SDKReference getCurrentValue(SakerEnvironment environment) throws Exception {
		Predicate<? super String> versionpredicate = AndroidUtils
				.getSetContainsOrBaseVersionElseAlwaysPredicate(versions);

		for (String loc : AndroidUtils.getEnvironmentUserParameterSDKLocations(environment)) {
			SDKReference foundsdk = tryGetSDKFromInstallLocation(loc, versionpredicate);
			if (foundsdk != null) {
				return foundsdk;
			}
		}

		SDKReference foundsdk;
		foundsdk = tryGetSDKFromInstallLocation(System.getenv(AndroidUtils.SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_HOME),
				versionpredicate);
		if (foundsdk != null) {
			return foundsdk;
		}
		foundsdk = tryGetSDKFromInstallLocation(
				System.getenv(AndroidUtils.SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_SDK_ROOT), versionpredicate);
		if (foundsdk != null) {
			return foundsdk;
		}

		throw new SDKNotFoundException("Android build tools SDK not found for versions: " + versions);
	}

	private static SDKReference tryGetSDKFromInstallLocation(String location,
			Predicate<? super String> versionpredicate) {
		if (ObjectUtils.isNullOrEmpty(location)) {
			return null;
		}
		SakerPath installpath;
		try {
			installpath = SakerPath.valueOf(location);
		} catch (InvalidPathFormatException e) {
			//ignore
			return null;
		}
		if (!installpath.isAbsolute()) {
			return null;
		}
		SDKReference foundsdk = AndroidUtils.searchBuildToolsInAndroidSDKInstallLocation(installpath, versionpredicate);
		if (foundsdk != null) {
			return foundsdk;
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, versions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		versions = SerialUtils.readExternalImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((versions == null) ? 0 : versions.hashCode());
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
		VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty other = (VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty) obj;
		if (versions == null) {
			if (other.versions != null)
				return false;
		} else if (!versions.equals(other.versions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + versions + "]";
	}
}
