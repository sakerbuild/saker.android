package saker.android.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidNdkSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.impl.sdk.VersionsAndroidBuildToolsSDKDescription;
import saker.android.impl.sdk.VersionsAndroidNdkSDKDescription;
import saker.android.impl.sdk.VersionsAndroidPlatformSDKDescription;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.version.BaseVersionVersionRange;
import saker.nest.version.UnionVersionRange;
import saker.nest.version.VersionRange;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.exc.SDKNotFoundException;

public class AndroidUtils {
	public static final String ENVIRONMENT_USER_PARAMETER_ANDROID_SDK_LOCATIONS = "saker.android.sdk.install.location";
	public static final String ENVIRONMENT_USER_PARAMETER_ANDROID_NDK_LOCATIONS = "saker.android.ndk.install.location";

	//environment variable descriptions: https://developer.android.com/studio/command-line/variables
	public static final String SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_HOME = "ANDROID_HOME";
	public static final String SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_SDK_ROOT = "ANDROID_SDK_ROOT";

	public static final String SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_NDK_ROOT = "ANDROID_NDK_ROOT";

	public static final SDKDescription DEFAULT_BUILD_TOOLS_SDK = VersionsAndroidBuildToolsSDKDescription.create(null);
	public static final SDKDescription DEFAULT_PLATFORM_SDK = VersionsAndroidPlatformSDKDescription.create(null);

	public static final VersionsAndroidNdkSDKDescription DEFAULT_NDK_SDK = VersionsAndroidNdkSDKDescription
			.create(null);

	private static final Pattern PATTERN_ANDROID_PLATFORM_DIRECTORY_NAME = Pattern.compile("android-([0-9]+)");
	private static final Pattern PATTERN_SEMICOLON_SPLIT = Pattern.compile(";+");

	public static final int SDK_OS_TYPE_UNKNOWN = 0;
	public static final int SDK_OS_TYPE_WINDOWS = 1;
	public static final int SDK_OS_TYPE_LINUX = 2;
	public static final int SDK_OS_TYPE_MACOS = 3;

	private AndroidUtils() {
		throw new UnsupportedOperationException();
	}

	public static Predicate<? super String> getSetContainsElseAlwaysPredicate(Set<String> versions) {
		if (versions == null) {
			return Functionals.alwaysPredicate();
		}
		return versions::contains;
	}

	/**
	 * Creates a predicate that determines if a given SDK version is deemed suitable by the argument versions set.
	 * <p>
	 * If the argument is <code>null</code>, all versions are accepted.
	 * <p>
	 * Otherwise, the semantics of {@link VersionRange} apply.
	 * 
	 * @param versions
	 *            The versions.
	 * @return The predicate.
	 */
	public static Predicate<? super String> getSetContainsOrBaseVersionElseAlwaysPredicate(Set<String> versions) {
		if (versions == null) {
			return Functionals.alwaysPredicate();
		}
		Set<VersionRange> ranges = new HashSet<>();
		for (String v : versions) {
			if (BundleIdentifier.isValidVersionNumber(v)) {
				ranges.add(BaseVersionVersionRange.create(v));
			}
		}
		if (!ranges.isEmpty()) {
			VersionRange union = UnionVersionRange.create(ranges);
			return v -> {
				if (BundleIdentifier.isValidVersionNumber(v)) {
					if (union.includes(v)) {
						return true;
					}
				}
				return versions.contains(v);
			};
		}
		return versions::contains;
	}

	public static SDKReference searchBuildToolsInAndroidSDKInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) {
		Exception[] causes = {};
		LocalFileProvider fp = LocalFileProvider.getInstance();

		SakerPath buildtoolspath = installpath.resolve("build-tools");
		NavigableSet<String> descendingverdirectories = new TreeSet<>(
				Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
		NavigableSet<String> remainingdirs = new TreeSet<>();
		NavigableMap<String, ? extends FileEntry> buildtoolsentries;
		try {
			buildtoolsentries = fp.getDirectoryEntries(buildtoolspath);
		} catch (IOException e) {
			throw new SDKNotFoundException("Failed to list build-tools in Android SDK: " + buildtoolspath, e);
		}
		for (String direntry : buildtoolsentries.keySet()) {
			if (BundleIdentifier.isValidVersionNumber(direntry)) {
				descendingverdirectories.add(direntry);
			} else {
				remainingdirs.add(direntry);
			}
		}
		for (String verdirname : descendingverdirectories) {
			if (versionpredicate.test(verdirname)) {
				return new AndroidBuildToolsSDKReference(verdirname, buildtoolspath.resolve(verdirname),
						getSdkOsType());
			}
			causes = ArrayUtils.appended(causes, new SDKNotFoundException(
					"Android build-tools not suitable: " + verdirname + " in " + buildtoolspath));

		}
		for (String verdirname : remainingdirs) {
			if (versionpredicate.test(verdirname)) {
				return new AndroidBuildToolsSDKReference(verdirname, buildtoolspath.resolve(verdirname),
						getSdkOsType());
			}
			causes = ArrayUtils.appended(causes, new SDKNotFoundException(
					"Android build-tools not suitable: " + verdirname + " in " + buildtoolspath));
		}
		SDKNotFoundException ex = new SDKNotFoundException("Android build-tools not found.");
		for (Exception e : causes) {
			ex.addSuppressed(e);
		}
		throw ex;
	}

	private static Map<String, String> readSourceProperties(Path props) throws IOException {
		List<String> proplines = Files.readAllLines(props);
		Map<String, String> result = new TreeMap<>();
		for (String line : proplines) {
			int idx = line.indexOf('=');
			if (idx < 0) {
				continue;
			}
			result.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
		}
		return result;
	}

	public static SDKReference searchNdkInInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) throws SDKNotFoundException {
		SakerPath propspath = installpath.resolve("source.properties");
		Map<String, String> properties;
		try {
			properties = readSourceProperties(LocalFileProvider.toRealPath(propspath));
		} catch (Exception e) {
			throw new SDKNotFoundException("Failed to read source.properties in: " + installpath, e);
		}
		if (!"Android NDK".equals(properties.get("Pkg.Desc"))) {
			throw new SDKNotFoundException("Pkg.Desc in source properties doesn't equal Android NDK in: " + propspath);
		}
		String rev = properties.get("Pkg.Revision");
		if (rev == null) {
			throw new SDKNotFoundException("Pkg.Revision property missing: " + propspath);
		}
		if (!versionpredicate.test(rev)) {
			throw new SDKNotFoundException("Unsuitable Android NDK version: " + rev + " at " + installpath);
		}
		int revdotidx = rev.indexOf('.');
		try {
			//test that the major version is parseable
			if (revdotidx < 0) {
				Integer.parseInt(rev);
			} else {
				Integer.parseInt(rev.substring(0, revdotidx));
			}
		} catch (NumberFormatException e) {
			throw new SDKNotFoundException("Failed to parse Android NDK Pkg.Revision: " + rev, e);
		}
		return new AndroidNdkSDKReference(rev, installpath, getSdkOsType());
	}

	public static SDKReference searchPlatformInAndroidSDKInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) {
		Exception[] causes = {};
		LocalFileProvider fp = LocalFileProvider.getInstance();

		SakerPath platformspath = installpath.resolve("platforms");
		//the elements are dirname-version entries, where version is the number from android-<num> formatted names
		List<Entry<String, Integer>> verdirectories = new ArrayList<>();
		NavigableMap<String, ? extends FileEntry> platformsentries;
		try {
			platformsentries = fp.getDirectoryEntries(platformspath);
		} catch (IOException e) {
			throw new SDKNotFoundException("Failed to list platforms in Android SDK: " + platformspath, e);
		}
		Iterator<String> it = platformsentries.keySet().iterator();
		if (!it.hasNext()) {
			throw new SDKNotFoundException("No platforms found in Android SDK: " + platformspath);
		}
		String dirname = it.next();
		Matcher matcher = PATTERN_ANDROID_PLATFORM_DIRECTORY_NAME.matcher(dirname);
		while (true) {
			if (matcher.matches()) {
				verdirectories.add(ImmutableUtils.makeImmutableMapEntry(dirname, Integer.parseInt(matcher.group(1))));
			} else {
				verdirectories.add(ImmutableUtils.makeImmutableMapEntry(dirname, null));
			}
			if (!it.hasNext()) {
				break;
			}
			dirname = it.next();
			matcher.reset(dirname);
		}

		verdirectories.sort((l, r) -> {
			Integer lver = l.getValue();
			Integer rver = r.getValue();
			if (lver == null) {
				if (rver == null) {
					//descending order for directories that are not in "android-<num>" format
					return -l.getKey().compareTo(r.getKey());
				}
				return 1;
			}
			if (rver == null) {
				return -1;
			}
			return -Integer.compare(lver, rver);
		});

		for (Entry<String, Integer> verdirentry : verdirectories) {
			String verdirname = verdirentry.getKey();
			Integer ver = verdirentry.getValue();
			if (versionpredicate.test(verdirname) || (ver != null && versionpredicate.test(ver.toString()))) {
				return new AndroidPlatformSDKReference(verdirname, platformspath.resolve(verdirname));
			}
			causes = ArrayUtils.appended(causes,
					new SDKNotFoundException("Android platform not suitable: " + verdirname + " in " + platformspath));
		}
		SDKNotFoundException ex = new SDKNotFoundException("Android platform not found.");
		for (Exception e : causes) {
			ex.addSuppressed(e);
		}
		throw ex;
	}

	public static int getSdkOsType() {
		String mappedname = System.mapLibraryName("test");
		if ("test.dll".equals(mappedname)) {
			return SDK_OS_TYPE_WINDOWS;
		}
		if ("libtest.so".equals(mappedname)) {
			return SDK_OS_TYPE_LINUX;
		}
		if ("libtest.dylib".equals(mappedname)) {
			return SDK_OS_TYPE_MACOS;
		}
		return SDK_OS_TYPE_UNKNOWN;
	}

	public static String[] getEnvironmentUserParameterSDKLocations(SakerEnvironment environment) {
		return getSDKLocationParameterValue(environment, AndroidUtils.ENVIRONMENT_USER_PARAMETER_ANDROID_SDK_LOCATIONS);
	}

	public static String[] getEnvironmentUserParameterNDKLocations(SakerEnvironment environment) {
		return getSDKLocationParameterValue(environment, AndroidUtils.ENVIRONMENT_USER_PARAMETER_ANDROID_NDK_LOCATIONS);
	}

	public static <T> T searchInAndroidSDKLocations(SakerEnvironment environment,
			Function<? super String, T> consumer) {
		for (String loc : AndroidUtils.getEnvironmentUserParameterSDKLocations(environment)) {
			T result = consumer.apply(loc);
			if (result != null) {
				return result;
			}
		}

		T found;
		found = consumer.apply(System.getenv(AndroidUtils.SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_HOME));
		if (found != null) {
			return found;
		}
		found = consumer.apply(System.getenv(AndroidUtils.SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_SDK_ROOT));
		if (found != null) {
			return found;
		}
		return null;
	}

	private static String[] getSDKLocationParameterValue(SakerEnvironment environment, String paramname) {
		Map<String, String> userparams = environment.getUserParameters();
		String installlocationsparam = userparams.get(paramname);
		if (ObjectUtils.isNullOrEmpty(installlocationsparam)) {
			return ObjectUtils.EMPTY_STRING_ARRAY;
		}
		return PATTERN_SEMICOLON_SPLIT.split(installlocationsparam);
	}

}
