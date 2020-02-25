package saker.android.impl;

import java.io.IOException;
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
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.impl.sdk.VersionsAndroidBuildToolsSDKDescription;
import saker.android.impl.sdk.VersionsAndroidPlatformSDKDescription;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.version.BaseVersionVersionRange;
import saker.nest.version.UnionVersionRange;
import saker.nest.version.VersionRange;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;

public class AndroidUtils {
	public static final String ENVIRONMENT_USER_PARAMETER_ANDROID_SDK_LOCATIONS = "saker.android.sdk.install.location";

	//environment variable descriptions: https://developer.android.com/studio/command-line/variables
	public static final String SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_HOME = "ANDROID_HOME";
	public static final String SYSTEM_ENVIRONMENT_VARIABLE_ANDROID_SDK_ROOT = "ANDROID_SDK_ROOT";

	public static final SDKDescription DEFAULT_BUILD_TOOLS_SDK = VersionsAndroidBuildToolsSDKDescription.create(null);
	public static final SDKDescription DEFAULT_PLATFORM_SDK = VersionsAndroidPlatformSDKDescription.create(null);

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
				try {
					if (union.includes(v)) {
						return true;
					}
				} catch (IllegalArgumentException e) {
					//not a valid version number or something
					//ignore, and don't include
				}
				return versions.contains(v);
			};
		}
		return versions::contains;
	}

	public static SDKReference searchBuildToolsInAndroidSDKInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) {
		LocalFileProvider fp = LocalFileProvider.getInstance();

		SakerPath buildtoolspath = installpath.resolve("build-tools");
		NavigableSet<String> descendingverdirectories = new TreeSet<>(
				Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
		try {
			NavigableMap<String, ? extends FileEntry> buildtoolsentries = fp.getDirectoryEntries(buildtoolspath);
			descendingverdirectories.addAll(buildtoolsentries.keySet());
		} catch (IOException e) {
			return null;
		}
		for (String verdirname : descendingverdirectories) {
			if (versionpredicate.test(verdirname)) {
				return new AndroidBuildToolsSDKReference(verdirname, buildtoolspath.resolve(verdirname),
						getSdkOsType());
			}
		}
		return null;
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
		Map<String, String> userparams = environment.getUserParameters();
		String installlocationsparam = userparams.get(AndroidUtils.ENVIRONMENT_USER_PARAMETER_ANDROID_SDK_LOCATIONS);
		if (ObjectUtils.isNullOrEmpty(installlocationsparam)) {
			return ObjectUtils.EMPTY_STRING_ARRAY;
		}
		return PATTERN_SEMICOLON_SPLIT.split(installlocationsparam);
	}

	public static SDKReference searchPlatformInAndroidSDKInstallLocation(SakerPath installpath,
			Predicate<? super String> versionpredicate) {
		LocalFileProvider fp = LocalFileProvider.getInstance();

		SakerPath platformspath = installpath.resolve("platforms");
		//the elements are dirname-version entries, where version is the number from android-<num> formatted names
		List<Entry<String, Integer>> verdirectories = new ArrayList<>();
		try {
			NavigableMap<String, ? extends FileEntry> buildtoolsentries = fp.getDirectoryEntries(platformspath);
			Iterator<String> it = buildtoolsentries.keySet().iterator();
			if (it.hasNext()) {
				String dirname = it.next();
				Matcher matcher = PATTERN_ANDROID_PLATFORM_DIRECTORY_NAME.matcher(dirname);
				while (true) {
					if (matcher.matches()) {
						verdirectories
								.add(ImmutableUtils.makeImmutableMapEntry(dirname, Integer.parseInt(matcher.group(1))));
					} else {
						verdirectories.add(ImmutableUtils.makeImmutableMapEntry(dirname, null));
					}
					if (!it.hasNext()) {
						break;
					}
					dirname = it.next();
					matcher.reset(dirname);
				}
			}
			verdirectories.sort((l, r) -> {
				Integer lver = l.getValue();
				Integer rver = r.getValue();
				if (lver == null) {
					if (rver == null) {
						return l.getKey().compareTo(r.getKey());
					}
					return 1;
				}
				if (rver == null) {
					return -1;
				}
				return -Integer.compare(lver, rver);
			});

		} catch (IOException e) {
			return null;
		}
		for (Entry<String, Integer> verdirentry : verdirectories) {
			String verdirname = verdirentry.getKey();
			if (versionpredicate.test(verdirname)) {
				return new AndroidPlatformSDKReference(verdirname, platformspath.resolve(verdirname));
			}
		}
		return null;
	}
}
