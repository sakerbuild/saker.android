package saker.android.d8support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;

import saker.android.impl.d8.ArchiveClassDescriptorsCacheKey.ArchiveClassDescriptorsData;

public final class ArchiveFileClassFileResourceProvider implements ClassFileResourceProvider {
	private final NavigableMap<String, ZipEntry> descriptorEntries;
	private final ArchiveClassDescriptorsData archiveData;
	private final Origin archiveOrigin;

	public ArchiveFileClassFileResourceProvider(ArchiveClassDescriptorsData archivedata) {
		NavigableMap<String, ZipEntry> descriptorentries = archivedata.getDescriptorEntries();
		this.descriptorEntries = descriptorentries;
		this.archiveData = archivedata;
		this.archiveOrigin = new PathOrigin(Paths.get(archivedata.getZipFile().getName()));
	}

	@Override
	public ProgramResource getProgramResource(String descriptor) {
		ZipEntry entry = descriptorEntries.get(descriptor);
		if (entry == null) {
			return null;
		}
		Origin archiveorigin = archiveOrigin;
		ZipFile zipfile = archiveData.getZipFile();
		return new ArchiveEntryProgramResource(descriptor, zipfile, archiveorigin, entry);
	}

	@Override
	public Set<String> getClassDescriptors() {
		return descriptorEntries.navigableKeySet();
	}

	public final static class ArchiveEntryProgramResource implements ProgramResource {
		private final String descriptor;
		private final ZipFile zipfile;
		private final Origin archiveorigin;
		private final ZipEntry entry;

		public ArchiveEntryProgramResource(String descriptor, ZipFile zipfile, Origin archiveorigin, ZipEntry entry) {
			this.descriptor = descriptor;
			this.zipfile = zipfile;
			this.archiveorigin = archiveorigin;
			this.entry = entry;
		}

		@Override
		public Origin getOrigin() {
			return new ArchiveEntryOrigin(entry.getName(), archiveorigin);
		}

		@Override
		public Kind getKind() {
			return Kind.CF;
		}

		@Override
		public Set<String> getClassDescriptors() {
			return Collections.singleton(descriptor);
		}

		@Override
		public InputStream getByteStream() throws ResourceException {
			try {
				return zipfile.getInputStream(entry);
			} catch (IOException e) {
				throw new ResourceException(getOrigin(), e);
			}
		}
	}
}