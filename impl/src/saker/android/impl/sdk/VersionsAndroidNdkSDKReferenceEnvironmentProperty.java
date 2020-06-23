package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.function.Predicate;

import saker.android.impl.AndroidUtils;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.build.trace.TraceContributorEnvironmentProperty;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.exc.SDKNotFoundException;

public class VersionsAndroidNdkSDKReferenceEnvironmentProperty
		implements TraceContributorEnvironmentProperty<SDKReference>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<String> versions;

	/**
	 * For {@link Externalizable}.
	 */
	public VersionsAndroidNdkSDKReferenceEnvironmentProperty() {
	}

	public VersionsAndroidNdkSDKReferenceEnvironmentProperty(Set<String> versions) {
		this.versions = versions;
	}

	@Override
	public SDKReference getCurrentValue(SakerEnvironment environment) throws Exception {
		Predicate<? super String> versionpredicate = AndroidUtils
				.getSetContainsOrBaseVersionElseAlwaysPredicate(versions);

		List<Exception> causes = new ArrayList<>();

		for (String loc : AndroidUtils.getEnvironmentUserParameterNDKLocations(environment)) {
			try {
				SDKReference foundsdk = tryGetSDKFromInstallLocation(loc, versionpredicate);
				if (foundsdk != null) {
					return foundsdk;
				}
			} catch (Exception e) {
				causes.add(e);
			}
		}

		SDKReference foundsdk;
		try {
			foundsdk = tryGetSDKFromInstallLocation(
					System.getenv(AndroidUtils.SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_NDK_ROOT), versionpredicate);
			if (foundsdk != null) {
				return foundsdk;
			}
		} catch (Exception e) {
			causes.add(e);
		}

		//try to search for NDK under Android SDK installations
		foundsdk = AndroidUtils.searchInAndroidSDKLocations(environment, loc -> {
			try {
				SakerPath path = SakerPath.valueOf(loc);
				SakerPathFiles.requireAbsolutePath(path);
				//search directly in android-sdk/ndk-bundle
				SDKReference foundndk;
				try {
					foundndk = tryGetSDKFromInstallLocation(path.resolve("ndk-bundle"), versionpredicate);
					if (foundndk != null) {
						return foundndk;
					}
				} catch (Exception e) {
					causes.add(e);
				}
				//search in android-sdk/ndk/<version>/ directories
				SakerPath ndkdir = path.resolve("ndk");
				//check the direct subdir just in case
				try {
					foundndk = tryGetSDKFromInstallLocation(ndkdir, versionpredicate);
					if (foundndk != null) {
						return foundndk;
					}
				} catch (Exception e) {
					causes.add(e);
				}
				NavigableMap<String, ? extends FileEntry> direntries;
				try {
					direntries = LocalFileProvider.getInstance().getDirectoryEntries(ndkdir);
				} catch (IOException e) {
					causes.add(e);
					return null;
				}
				for (Entry<String, ? extends FileEntry> entry : direntries.entrySet()) {
					if (!entry.getValue().isDirectory()) {
						continue;
					}
					try {
						foundndk = tryGetSDKFromInstallLocation(ndkdir.resolve(entry.getKey()), versionpredicate);
						if (foundndk != null) {
							return foundndk;
						}
					} catch (Exception e) {
						causes.add(e);
					}
				}
			} catch (Exception e) {
				causes.add(e);
			}
			return null;
		});
		if (foundsdk != null) {
			return foundsdk;
		}

		SDKNotFoundException ex = new SDKNotFoundException("Android NDK not found for versions: "
				+ (versions == null ? "any" : StringUtils.toStringJoin(", ", versions)));
		for (Exception e : causes) {
			ex.addSuppressed(e);
		}
		throw ex;
	}

	@Override
	public void contributeBuildTraceInformation(SDKReference propertyvalue,
			PropertyComputationFailedException thrownexception) {
		if (propertyvalue == null) {
			if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_014) {
				if (thrownexception != null) {
					BuildTrace.ignoredException(thrownexception.getCause());
				}
			}
			return;
		}
		Map<Object, Object> values = new LinkedHashMap<>();
		try {
			String version = propertyvalue.getProperty(AndroidNdkSDKReference.PROPERTY_VERSION);
			SakerPath home = propertyvalue.getPath(AndroidNdkSDKReference.PATH_HOME);
			Map<Object, Object> ndkprops = new LinkedHashMap<>();
			values.put("Android NDK v" + version, ndkprops);
			if (home != null) {
				ndkprops.put("Install location", home.toString());
			}
			BuildTrace.setValues(values, BuildTrace.VALUE_CATEGORY_ENVIRONMENT);
		} catch (Exception e) {
			if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_014) {
				BuildTrace.ignoredException(e);
			}
		}
	}

	private static SDKReference tryGetSDKFromInstallLocation(String location,
			Predicate<? super String> versionpredicate) {
		if (ObjectUtils.isNullOrEmpty(location)) {
			return null;
		}
		SakerPath installpath = SakerPath.valueOf(location);
		return tryGetSDKFromInstallLocation(installpath, versionpredicate);
	}

	private static SDKReference tryGetSDKFromInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) {
		if (installpath == null) {
			return null;
		}
		SakerPathFiles.requireAbsolutePath(installpath);
		SDKReference foundsdk = AndroidUtils.searchNdkInInstallLocation(installpath, versionpredicate);
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
		VersionsAndroidNdkSDKReferenceEnvironmentProperty other = (VersionsAndroidNdkSDKReferenceEnvironmentProperty) obj;
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
