package saker.android.impl.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import saker.build.thirdparty.org.objectweb.asm.ClassReader;
import saker.build.thirdparty.org.objectweb.asm.ClassWriter;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.JarFileUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;

public class InstrumentingJarClassLoaderDataFinder implements ClassLoaderDataFinder {
	protected final JarFile jar;

	public InstrumentingJarClassLoaderDataFinder(Path jar) throws IOException, NullPointerException {
		this.jar = JarFileUtils.createMultiReleaseJarFile(jar);
	}

	private static ByteArrayRegion instrument(ByteArrayRegion classbytes) {
		ClassReader cr = new ClassReader(classbytes.getArray(), classbytes.getOffset(), classbytes.getLength());
		ClassWriter cw = new ClassWriter(cr, 0);
		AndroidBuildToolsInstrumentationClassVisitor visitor = new AndroidBuildToolsInstrumentationClassVisitor(cw);
		cr.accept(visitor, 0);
		if (!visitor.isAppliedTransformation()) {
			return classbytes;
		}
		return ByteArrayRegion.wrap(cw.toByteArray());
	}

	@Override
	public ByteArrayRegion getResourceBytes(String name) {
		ZipEntry entry = jar.getEntry(name);
		if (entry == null) {
			return null;
		}
		return getEntryBytesImpl(entry);
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		ZipEntry entry = jar.getEntry(name);
		if (entry == null) {
			return null;
		}
		return getEntryAsStreamImpl(entry);
	}

	@Override
	public Supplier<? extends ByteSource> getResource(String name) {
		ZipEntry entry = jar.getEntry(name);
		if (entry == null) {
			return null;
		}
		return () -> getEntryAsStreamImpl(entry);
	}

	@Override
	public void close() throws IOException {
		jar.close();
	}

	private ByteArrayRegion getEntryBytesImpl(ZipEntry entry) {
		ByteArrayRegion resbytes;
		try (InputStream is = jar.getInputStream(entry)) {
			resbytes = StreamUtils.readStreamFully(is);
		} catch (IOException e) {
			return null;
		}
		if (entry.getName().endsWith(".class")) {
			return instrument(resbytes);
		}
		return resbytes;
	}

	private ByteSource getEntryAsStreamImpl(ZipEntry entry) {
		if (entry.getName().endsWith(".class")) {
			ByteArrayRegion entrybytes = getEntryBytesImpl(entry);
			if (entrybytes == null) {
				return null;
			}
			return new UnsyncByteArrayInputStream(entrybytes);
		}
		try {
			return ByteSource.valueOf(jar.getInputStream(entry));
		} catch (IOException e) {
			return null;
		}
	}
}
