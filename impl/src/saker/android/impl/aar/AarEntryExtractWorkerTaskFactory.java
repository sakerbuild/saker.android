package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.classpath.LiteralStructuredTaskResult;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.trace.BuildTrace;
import saker.nest.bundle.NestBundleClassLoader;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AarEntryExtractWorkerTaskFactory
		implements TaskFactory<AarExtractTaskOutput>, Task<AarExtractTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String ENTRY_NAME_CLASSES_JAR = "classes.jar";
	public static final String ENTRY_NAME_LIBRARIES_DIRECTORY = "libs";

	public static final int OUTPUT_KIND_EXECUTION = 1;
	public static final int OUTPUT_KIND_BUNDLE_STORAGE = 2;

	protected StructuredTaskResult inputFile;
	protected SakerPath outputRelativePath;
	protected String entryName;

	protected int outPathKind;

	/**
	 * For {@link Externalizable}.
	 */
	public AarEntryExtractWorkerTaskFactory() {
	}

	public AarEntryExtractWorkerTaskFactory(FileLocation inputFile, String entry) {
		this(new LiteralStructuredTaskResult(inputFile), createGeneralAarExtractOutputRelativePath(inputFile), entry,
				inferOutPathKind(inputFile));
	}

	public AarEntryExtractWorkerTaskFactory(FileLocation inputFile, SakerPath outputRelativePath, String entry) {
		this(new LiteralStructuredTaskResult(inputFile), outputRelativePath, entry, inferOutPathKind(inputFile));
	}

	public AarEntryExtractWorkerTaskFactory(StructuredTaskResult inputFile, SakerPath outputRelativePath, String entry,
			int outPathKind) {
		this.inputFile = inputFile;
		this.outputRelativePath = outputRelativePath;
		this.entryName = entry;
		this.outPathKind = outPathKind;
	}

	public TaskIdentifier createTaskId() {
		return new AarEntryExtractWorkerTaskIdentifier(outputRelativePath, outPathKind, entryName);
	}

	public static SakerPath createGeneralAarExtractOutputRelativePath(FileLocation fl) {
		return createGeneralAarExtractOutputRelativePath(getFileLocationPath(fl));
	}

	public static SakerPath createGeneralAarExtractOutputRelativePath(SakerPath filelocationpath) {
		return SakerPath.valueOf("saker.aar.extract")
				.resolve(StringUtils.toHexString(FileUtils.hashString(filelocationpath.toString())))
				.resolve(filelocationpath.getFileName());
	}

	public static SakerPath getFileLocationPath(FileLocation fl) {
		SakerPath[] result = { null };
		fl.accept(new FileLocationVisitor() {
			@Override
			public void visit(LocalFileLocation loc) {
				result[0] = loc.getLocalPath();
			}

			@Override
			public void visit(ExecutionFileLocation loc) {
				result[0] = loc.getPath();
			}
		});
		return result[0];
	}

	private static int inferOutPathKind(FileLocation fl) {
		int[] result = { 0 };
		fl.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				result[0] = OUTPUT_KIND_EXECUTION;
			}

			@Override
			public void visit(LocalFileLocation loc) {
				result[0] = OUTPUT_KIND_BUNDLE_STORAGE;
			}
		});
		return result[0];
	}

	public SakerPath getOutputRelativePath() {
		return outputRelativePath;
	}

	@Override
	public AarExtractTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			//CLASSIFICATION_TRANSFORMATION is available from 0.8.10, however as it is a constant, it can be inlined without runtime errors
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_TRANSFORMATION);
		}

		FileLocation inputfile = (FileLocation) inputFile.toResult(taskcontext);

		AarExtractTaskOutput[] result = { null };
		inputfile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				try {
					SakerPath inputFile = loc.getPath();

					SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(inputFile);
					if (f == null) {
						taskcontext.reportInputFileDependency(null, inputFile,
								CommonTaskContentDescriptors.IS_NOT_FILE);
						result[0] = new AarNotFoundAarExtractTaskOutput(inputFile);
					}
					taskcontext.reportInputFileDependency(null, inputFile, f.getContentDescriptor());

					result[0] = handleExecutionFile(taskcontext, f);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				try {
					SakerPath localpath = loc.getLocalPath();
					ContentDescriptor cd = taskcontext.getTaskUtilities().getReportExecutionDependency(
							SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(localpath,
									taskcontext.getTaskId()));
					if (cd == null || DirectoryContentDescriptor.INSTANCE.equals(cd)) {
						result[0] = new AarNotFoundAarExtractTaskOutput(localpath);
					}

					result[0] = handleLocalFile(taskcontext, localpath);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

		});
		return result[0];
	}

	protected AarExtractTaskOutput addResultFolder(TaskContext taskcontext,
			NavigableMap<SakerPath, ByteArrayRegion> folderentrybytes, FileHashResult archivehash) throws Exception {
		switch (outPathKind) {
			case OUTPUT_KIND_EXECUTION: {
				return addExecutionResultFolder(taskcontext, folderentrybytes);
			}
			case OUTPUT_KIND_BUNDLE_STORAGE: {
				return addBundleStorageResultFolder(taskcontext, folderentrybytes, archivehash);
			}
			default: {
				throw new UnsupportedOperationException("Unrecognized output kind: " + outPathKind);
			}
		}
	}

	private AarExtractTaskOutput addBundleStorageResultFolder(TaskContext taskcontext,
			NavigableMap<SakerPath, ByteArrayRegion> folderentrybytes, FileHashResult archivehash) throws IOException {
		NestBundleClassLoader cl = (NestBundleClassLoader) this.getClass().getClassLoader();
		Path storagedir = cl.getBundle().getBundleStoragePath();
		Path tempdir = storagedir.resolve("temp");
		Files.createDirectories(tempdir);

		Path outputdirpath = storagedir.resolve(outputRelativePath.toString())
				.resolve(StringUtils.toHexString(archivehash.getHash())).resolve(entryName);

		//XXX should check and delete already existing, non-archive files in place

		UUID dependencyuniqueness = UUID.randomUUID();

		LocalFileProvider localfp = LocalFileProvider.getInstance();

		Set<FileLocation> dirfilelocations = new LinkedHashSet<>();
		Collection<PathKey> invalidatepathkeys = new ArrayList<>(folderentrybytes.size());
		NavigableSet<SakerPath> outputlocalsakerpaths = new TreeSet<>();
		for (Entry<SakerPath, ByteArrayRegion> entry : folderentrybytes.entrySet()) {
			Path entryoutpath = outputdirpath.resolve(entry.getKey().toString());
			SakerPath entryoutsakerpath = SakerPath.valueOf(entryoutpath);
			outputlocalsakerpaths.add(entryoutsakerpath);
			ByteArrayRegion entrybytes = entry.getValue();
			if (isLocalOutputRegularFileUpToDate(entryoutpath, entrybytes.getLength())) {
				continue;
			}

			Path outputtempfilepath = tempdir.resolve(entryoutpath.getFileName() + "." + dependencyuniqueness);

			//create parent dirs
			localfp.ensureWriteRequest(entryoutpath.getParent(), FileEntry.TYPE_DIRECTORY,
					SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
			Files.createDirectories(outputdirpath);
			try (OutputStream fos = Files.newOutputStream(outputtempfilepath)) {
				entrybytes.writeTo(fos);
			}
			try {
				//no REPLACE_EXISTING
				Files.move(outputtempfilepath, entryoutpath);
			} catch (IOException e) {
				//file probably already exists, as others moved to it concurrently
				if (!isLocalOutputRegularFileUpToDate(entryoutpath, entrybytes.getLength())) {
					//hard fail the task
					throw e;
				}
			} finally {
				Files.deleteIfExists(outputtempfilepath);
			}
			invalidatepathkeys.add(new SimplePathKey(entryoutsakerpath, LocalFileProvider.getProviderKeyStatic()));
		}
		TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
		taskutils.invalidate(invalidatepathkeys);
		for (SakerPath outputlocalsakerpath : outputlocalsakerpaths) {
			taskutils.getReportExecutionDependency(
					SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(outputlocalsakerpath,
							ImmutableUtils.asUnmodifiableArrayList(taskcontext.getTaskId(), dependencyuniqueness)));
			dirfilelocations.add(LocalFileLocation.create(outputlocalsakerpath));
		}
		return new LocalAarExtractTaskOutput(SakerPath.valueOf(outputdirpath), dirfilelocations);
	}

	private AarExtractTaskOutput addExecutionResultFolder(TaskContext taskcontext,
			NavigableMap<SakerPath, ByteArrayRegion> folderentrybytes) throws IOException {
		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputRelativePath.resolve(entryName));
		outdir.clear();

		Set<FileLocation> dirfilelocations = new LinkedHashSet<>();
		NavigableMap<SakerPath, ContentDescriptor> dependencies = new TreeMap<>();

		for (Entry<SakerPath, ByteArrayRegion> entry : folderentrybytes.entrySet()) {
			SakerPath respath = entry.getKey();
			ByteArrayRegion entrybytes = entry.getValue();

			ByteArraySakerFile resfile = new ByteArraySakerFile(respath.getFileName(), entrybytes);
			taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(outdir, respath.getParent()).add(resfile);
			SakerPath resfilepath = resfile.getSakerPath();

			dependencies.put(resfilepath, resfile.getContentDescriptor());
			dirfilelocations.add(ExecutionFileLocation.create(resfilepath));
		}
		outdir.synchronize();

		taskcontext.getTaskUtilities().reportOutputFileDependency(null, dependencies);
		return new ExecutionAarExtractTaskOutput(outdir.getSakerPath(), dirfilelocations);
	}

	protected AarExtractTaskOutput addResultFile(TaskContext taskcontext, ByteArrayRegion bytes,
			FileHashResult archivehash) throws Exception {
		switch (outPathKind) {
			case OUTPUT_KIND_EXECUTION: {
				return addExecutionResultFile(taskcontext, bytes);
			}
			case OUTPUT_KIND_BUNDLE_STORAGE: {
				return addBundleStorageResultFile(taskcontext, bytes, archivehash);
			}
			default: {
				throw new UnsupportedOperationException("Unrecognized output kind: " + outPathKind);
			}
		}
	}

	private static boolean isLocalOutputRegularFileUpToDate(Path filepath, long expectedsize) {
		try {
			BasicFileAttributes presentattrs = Files.readAttributes(filepath, BasicFileAttributes.class);
			if (presentattrs.isRegularFile() && presentattrs.size() == expectedsize) {
				return true;
			}
		} catch (IOException e) {
		}
		return false;
	}

	private AarExtractTaskOutput addBundleStorageResultFile(TaskContext taskcontext, ByteArrayRegion bytes,
			FileHashResult archivehash) throws AssertionError, IOException {
		NestBundleClassLoader cl = (NestBundleClassLoader) this.getClass().getClassLoader();
		Path storagedir = cl.getBundle().getBundleStoragePath();
		Path tempdir = storagedir.resolve("temp");
		Files.createDirectories(tempdir);

		Path outputdirpath = storagedir.resolve(outputRelativePath.toString())
				.resolve(StringUtils.toHexString(archivehash.getHash()));
		Path outputfilepath = outputdirpath.resolve(entryName);

		if (isLocalOutputRegularFileUpToDate(outputfilepath, bytes.getLength())) {
			//up to date
			return handleLocalFileResult(taskcontext, outputfilepath);
		}

		Path outputtempfilepath = tempdir.resolve(outputfilepath.getFileName() + "." + UUID.randomUUID());

		//create parent dirs
		LocalFileProvider.getInstance().ensureWriteRequest(outputdirpath, FileEntry.TYPE_DIRECTORY,
				SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);
		try (OutputStream fos = Files.newOutputStream(outputtempfilepath)) {
			bytes.writeTo(fos);
		}
		try {
			//no REPLACE_EXISTING
			Files.move(outputtempfilepath, outputfilepath);

			return handleLocalFileResult(taskcontext, outputfilepath);
		} catch (IOException e) {
			//file probably already exists, as others moved to it concurrently
			if (isLocalOutputRegularFileUpToDate(outputfilepath, bytes.getLength())) {
				//up to date
				return handleLocalFileResult(taskcontext, outputfilepath);
			}
			//hard fail the task
			throw e;
		} finally {
			Files.deleteIfExists(outputtempfilepath);
		}
	}

	private AarExtractTaskOutput addExecutionResultFile(TaskContext taskcontext, ByteArrayRegion bytes)
			throws IOException {
		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerPath outputentrynamerelativepath = outputRelativePath.resolve(entryName);
		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				outputentrynamerelativepath.getParent());
		ByteArraySakerFile outfile = new ByteArraySakerFile(outputentrynamerelativepath.getFileName(), bytes);
		outdir.add(outfile);
		outfile.synchronize();
		SakerPath outfilepath = outfile.getSakerPath();
		taskcontext.reportOutputFileDependency(null, outfilepath, outfile.getContentDescriptor());
		return new ExecutionAarExtractTaskOutput(outfilepath);
	}

	private static AarExtractTaskOutput handleLocalFileResult(TaskContext taskcontext, Path outputfilepath) {
		SakerPath outputlocalsakerpath = SakerPath.valueOf(outputfilepath);

		taskcontext.invalidate(LocalFileProvider.getPathKeyStatic(outputlocalsakerpath));
		taskcontext.getTaskUtilities().getReportExecutionDependency(
				SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(outputlocalsakerpath,
						ImmutableUtils.asUnmodifiableArrayList(taskcontext.getTaskId(), UUID.randomUUID())));
		return new LocalAarExtractTaskOutput(outputlocalsakerpath);
	}

	protected AarExtractTaskOutput handleLocalFile(TaskContext taskcontext, SakerPath localpath) throws Exception {
		NavigableMap<SakerPath, ByteArrayRegion> folderentrybytes;

		FileHashResult archivehash = null;
		if (outPathKind == OUTPUT_KIND_BUNDLE_STORAGE) {
			archivehash = LocalFileProvider.getInstance().hash(localpath, FileUtils.DEFAULT_FILE_HASH_ALGORITHM);
		}

		try (ZipFile zf = new ZipFile(LocalFileProvider.toRealPath(localpath).toFile())) {
			//maps relative paths to the zip entries
			NavigableMap<SakerPath, ZipEntry> direntries = null;

			ZipEntry foundentry = zf.getEntry(entryName);
			if (foundentry != null) {
				if (!foundentry.isDirectory()) {
					ByteArrayRegion entrybytes;
					try (InputStream zipin = zf.getInputStream(foundentry)) {
						entrybytes = StreamUtils.readStreamFully(zipin);
					}
					return addResultFile(taskcontext, entrybytes, archivehash);
				} else {
					direntries = new TreeMap<>();
				}
			}
			String direntryname = entryName + "/";

			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = (ZipEntry) entries.nextElement();
				String zename = ze.getName();
				if (!zename.startsWith(direntryname)) {
					continue;
				}
				if (zename.length() == direntryname.length()) {
					//found the directory
					if (direntries == null) {
						direntries = new TreeMap<>();
					}
				} else {
					if (!ze.isDirectory()) {
						direntries.put(SakerPath.valueOf(zename).subPath(1), ze);
					}
				}
			}
			if (direntries == null) {
				return new EntryNotFoundAarExtractTaskOutput(localpath, entryName);
			}
			//the directory was found.
			//extract the dir entries

			folderentrybytes = new TreeMap<>();

			for (Entry<SakerPath, ZipEntry> entry : direntries.entrySet()) {
				try (InputStream in = zf.getInputStream(entry.getValue())) {
					folderentrybytes.put(entry.getKey(), StreamUtils.readStreamFully(in));
				}
			}
		}
		return addResultFolder(taskcontext, folderentrybytes, archivehash);
	}

	protected AarExtractTaskOutput handleExecutionFile(TaskContext taskcontext, SakerFile f) throws Exception {
		String direntryname = entryName + "/";
		NavigableMap<SakerPath, ByteArrayRegion> direntries = null;
		ByteArrayRegion entrybytes = null;

		FileHashResult archivehash = null;

		try (InputStream instream = f.openInputStream()) {
			HashingInputStream hashingin;
			if (outPathKind == OUTPUT_KIND_BUNDLE_STORAGE) {
				hashingin = new HashingInputStream(instream);
			} else {
				hashingin = null;
			}
			try (ZipInputStream zipin = new ZipInputStream(ObjectUtils.nullDefault(hashingin, instream))) {
				for (ZipEntry ze; (ze = zipin.getNextEntry()) != null;) {
					String zename = ze.getName();
					if (zename.equals(entryName)) {
						if (!ze.isDirectory()) {

							//null it out just in case
							direntries = null;
							entrybytes = StreamUtils.readStreamFully(zipin);
							break;
						} else if (direntries == null) {
							direntries = new TreeMap<>();
						}
					}

					if (zename.startsWith(direntryname)) {
						if (zename.length() == direntryname.length()) {
							//found the directory
							if (direntries == null) {
								direntries = new TreeMap<>();
							}
						} else {
							if (!ze.isDirectory()) {
								direntries.put(SakerPath.valueOf(zename).subPath(1),
										StreamUtils.readStreamFully(zipin));
							}
						}
					}
				}
			}
			if (hashingin != null) {
				StreamUtils.consumeStream(hashingin);
				archivehash = new FileHashResult(hashingin.count, hashingin.digest.digest());
			}
		}
		if (entrybytes != null) {
			return addResultFile(taskcontext, entrybytes, archivehash);
		}
		if (direntries == null) {
			return new EntryNotFoundAarExtractTaskOutput(f.getSakerPath(), entryName);
		}
		//there were some dir entries
		return addResultFolder(taskcontext, direntries, archivehash);
	}

	@Override
	public Task<? extends AarExtractTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputFile);
		out.writeObject(outputRelativePath);
		out.writeObject(entryName);
		out.writeInt(outPathKind);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = SerialUtils.readExternalObject(in);
		outputRelativePath = SerialUtils.readExternalObject(in);
		entryName = SerialUtils.readExternalObject(in);
		outPathKind = in.readInt();
	}

	public static ByteArrayRegion getFileClassesJarBytes(SakerFile f) throws IOException {
		return getZipEntryBytes(f, ENTRY_NAME_CLASSES_JAR);
	}

	public static ByteArrayRegion getZipEntryBytes(SakerFile f, String entryname) throws IOException {
		ByteArrayRegion classesbytes = null;
		try (InputStream instream = f.openInputStream();
				ZipInputStream zipin = new ZipInputStream(instream)) {
			for (ZipEntry ze; (ze = zipin.getNextEntry()) != null;) {
				if (!entryname.equals(ze.getName())) {
					continue;
				}
				//found classes.jar in aar
				classesbytes = StreamUtils.readStreamFully(zipin);
			}
		}
		return classesbytes;
	}

	public static ByteArrayRegion getLocalFileClassesJarBytes(SakerPath localpath) throws IOException, ZipException {
		return getLocalZipEntryBytes(localpath, ENTRY_NAME_CLASSES_JAR);
	}

	public static ByteArrayRegion getLocalZipEntryBytes(SakerPath localpath, String entryname)
			throws IOException, ZipException {
		ByteArrayRegion classesbytes;
		try (ZipFile zf = new ZipFile(LocalFileProvider.toRealPath(localpath).toFile())) {
			ZipEntry classesentry = zf.getEntry(entryname);
			if (classesentry == null) {
				return null;
			}
			try (InputStream zipin = zf.getInputStream(classesentry)) {
				classesbytes = StreamUtils.readStreamFully(zipin);
			}
		}
		return classesbytes;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entryName == null) ? 0 : entryName.hashCode());
		result = prime * result + ((inputFile == null) ? 0 : inputFile.hashCode());
		result = prime * result + outPathKind;
		result = prime * result + ((outputRelativePath == null) ? 0 : outputRelativePath.hashCode());
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
		AarEntryExtractWorkerTaskFactory other = (AarEntryExtractWorkerTaskFactory) obj;
		if (entryName == null) {
			if (other.entryName != null)
				return false;
		} else if (!entryName.equals(other.entryName))
			return false;
		if (inputFile == null) {
			if (other.inputFile != null)
				return false;
		} else if (!inputFile.equals(other.inputFile))
			return false;
		if (outPathKind != other.outPathKind)
			return false;
		if (outputRelativePath == null) {
			if (other.outputRelativePath != null)
				return false;
		} else if (!outputRelativePath.equals(other.outputRelativePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + inputFile + " -> " + outputRelativePath + "]";
	}

	private static class HashingInputStream extends InputStream {
		protected final MessageDigest digest = FileUtils.getDefaultFileHasher();
		protected InputStream is;
		protected long count;

		public HashingInputStream(InputStream is) {
			this.is = is;
		}

		@Override
		public int read() throws IOException {
			int r = is.read();
			if (r >= 0) {
				digest.update((byte) r);
				++count;
			}
			return r;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int result = is.read(b, off, len);
			if (result > 0) {
				digest.update(b, off, result);
				count += result;
			}
			return result;
		}
	}
}
