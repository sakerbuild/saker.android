package saker.android.impl.d8;

import java.util.Enumeration;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.util.cache.CacheKey;

public class ArchiveClassDescriptorsCacheKey
		implements CacheKey<ArchiveClassDescriptorsCacheKey.ArchiveClassDescriptorsData, ZipFile> {
	public static class ArchiveClassDescriptorsData {
		private NavigableMap<String, ZipEntry> descriptorEntries;
		private ZipFile zipFile;

		public static ArchiveClassDescriptorsData create(ZipFile zip) {
			NavigableMap<String, ZipEntry> descriptorentries = new TreeMap<>();
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".class")) {
					continue;
				}
				if (name.startsWith("META-INF/")) {
					//may be multi-release, or anything other. don't take classes in the META-INF directory into account
					continue;
				}
				String descriptor = 'L' + name.substring(0, name.length() - 6) + ';';
				descriptorentries.put(descriptor, entry);
			}
			return new ArchiveClassDescriptorsData(descriptorentries, zip);
		}

		public ArchiveClassDescriptorsData(NavigableMap<String, ZipEntry> descriptorEntries, ZipFile zip) {
			this.descriptorEntries = descriptorEntries;
			this.zipFile = zip;
		}

		public NavigableMap<String, ZipEntry> getDescriptorEntries() {
			return descriptorEntries;
		}

		public ZipFile getZipFile() {
			return zipFile;
		}
	}

	private SakerPath archivePath;

	public ArchiveClassDescriptorsCacheKey(SakerPath archivePath) {
		this.archivePath = archivePath;
	}

	@Override
	public ZipFile allocate() throws Exception {
		return new ZipFile(LocalFileProvider.toRealPath(archivePath).toFile());
	}

	@Override
	public ArchiveClassDescriptorsData generate(ZipFile resource) throws Exception {
		return ArchiveClassDescriptorsData.create(resource);
	}

	@Override
	public boolean validate(ArchiveClassDescriptorsData data, ZipFile resource) {
		return true;
	}

	@Override
	public long getExpiry() {
		return 5 * DateUtils.MS_PER_MINUTE;
	}

	@Override
	public void close(ArchiveClassDescriptorsData data, ZipFile resource) throws Exception {
		resource.close();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((archivePath == null) ? 0 : archivePath.hashCode());
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
		ArchiveClassDescriptorsCacheKey other = (ArchiveClassDescriptorsCacheKey) obj;
		if (archivePath == null) {
			if (other.archivePath != null)
				return false;
		} else if (!archivePath.equals(other.archivePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + archivePath + "]";
	}

}
