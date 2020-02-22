package saker.android.impl.d8;

import java.util.Enumeration;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.util.cache.CacheKey;

public class AndroidJarDescriptorsCacheKey implements CacheKey<AndroidJarDescriptorsCacheKey.AndroidJarData, JarFile> {
	public static class AndroidJarData {
		private NavigableMap<String, ZipEntry> descriptorEntries;
		private JarFile jar;

		public AndroidJarData(NavigableMap<String, ZipEntry> descriptorEntries, JarFile jar) {
			this.descriptorEntries = descriptorEntries;
			this.jar = jar;
		}

		public NavigableMap<String, ZipEntry> getDescriptorEntries() {
			return descriptorEntries;
		}

		public JarFile getJarFile() {
			return jar;
		}
	}

	private SakerPath jarPath;

	public AndroidJarDescriptorsCacheKey(SakerPath jarPath) {
		this.jarPath = jarPath;
	}

	@Override
	public JarFile allocate() throws Exception {
		return new JarFile(LocalFileProvider.toRealPath(jarPath).toFile());
	}

	@Override
	public AndroidJarData generate(JarFile resource) throws Exception {
		NavigableMap<String, ZipEntry> descriptorentries = new TreeMap<>();
		Enumeration<JarEntry> entries = resource.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) entries.nextElement();
			String name = entry.getName();
			if (!name.endsWith(".class")) {
				continue;
			}
			String descriptor = 'L' + name.substring(0, name.length() - 6) + ';';
			descriptorentries.put(descriptor, entry);
		}
		return new AndroidJarData(descriptorentries, resource);
	}

	@Override
	public boolean validate(AndroidJarData data, JarFile resource) {
		return true;
	}

	@Override
	public long getExpiry() {
		return 5 * DateUtils.MS_PER_MINUTE;
	}

	@Override
	public void close(AndroidJarData data, JarFile resource) throws Exception {
		resource.close();
	}
}
