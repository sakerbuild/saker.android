package saker.android.d8support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Set;
import java.util.zip.ZipEntry;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;

import saker.android.impl.d8.AndroidJarDescriptorsCacheKey.AndroidJarData;

public final class JarFileClassFileResourceProvider implements ClassFileResourceProvider {
	private final NavigableMap<String, ZipEntry> descriptorentries;
	private final AndroidJarData jardata;
	private final Origin jarOrigin;

	public JarFileClassFileResourceProvider(AndroidJarData jardata) {
		NavigableMap<String, ZipEntry> descriptorentries = jardata.getDescriptorEntries();
		this.descriptorentries = descriptorentries;
		this.jardata = jardata;
		this.jarOrigin = new PathOrigin(Paths.get(jardata.getJarFile().getName()));
	}

	@Override
	public ProgramResource getProgramResource(String descriptor) {
		ZipEntry entry = descriptorentries.get(descriptor);
		if (entry == null) {
			return null;
		}
		return new ProgramResource() {
			@Override
			public Origin getOrigin() {
				return new ArchiveEntryOrigin(entry.getName(), jarOrigin);
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
					return jardata.getJarFile().getInputStream(entry);
				} catch (IOException e) {
					throw new ResourceException(getOrigin(), e);
				}
			}
		};
	}

	@Override
	public Set<String> getClassDescriptors() {
		return descriptorentries.navigableKeySet();
	}
}