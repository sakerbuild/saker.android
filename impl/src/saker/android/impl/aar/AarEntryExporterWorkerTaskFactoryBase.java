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
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import saker.android.impl.classpath.LiteralStructuredTaskResult;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.trace.BuildTrace;
import saker.nest.bundle.NestBundleClassLoader;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public abstract class AarEntryExporterWorkerTaskFactoryBase<T> implements TaskFactory<T>, Task<T>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String ENTRY_NAME_CLASSES_JAR = "classes.jar";

	public static final int OUTPUT_KIND_EXECUTION = 1;
	public static final int OUTPUT_KIND_BUNDLE_STORAGE = 2;

	protected StructuredTaskResult inputFile;
	protected SakerPath outputRelativePath;

	/**
	 * For {@link Externalizable}.
	 */
	public AarEntryExporterWorkerTaskFactoryBase() {
	}

	public AarEntryExporterWorkerTaskFactoryBase(FileLocation inputFile, SakerPath outputRelativePath) {
		this(new LiteralStructuredTaskResult(inputFile), outputRelativePath);
	}

	public AarEntryExporterWorkerTaskFactoryBase(StructuredTaskResult inputFile, SakerPath outputRelativePath) {
		this.inputFile = inputFile;
		this.outputRelativePath = outputRelativePath;
	}

	public SakerPath getOutputRelativePath() {
		return outputRelativePath;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		Object inputfileres = inputFile.toResult(taskcontext);
		if (!(inputfileres instanceof FileLocation)) {
			taskcontext.abortExecution(
					new ClassCastException("Invalid input type for aar entry extraction: " + inputfileres));
			return null;
		}
		FileLocation inputfile = (FileLocation) inputfileres;

		Object[] result = { null };
		inputfile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				try {
					SakerPath inputFile = loc.getPath();

					SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(inputFile);
					if (f == null) {
						taskcontext.reportInputFileDependency(null, inputFile,
								CommonTaskContentDescriptors.IS_NOT_FILE);
						result[0] = executionAarNotFound(inputFile);
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
						result[0] = localAarNotFound(localpath);
					}

					result[0] = handleLocalFile(taskcontext, localpath);
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

		});
		return (T) result[0];
	}

	protected T addResultFile(TaskContext taskcontext, ByteArrayRegion bytes, String entryname, int outPathKind)
			throws Exception {
		switch (outPathKind) {
			case OUTPUT_KIND_EXECUTION: {
				SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

				SakerPath outputentrynamerelativepath = outputRelativePath.resolve(entryname);
				SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
						outputentrynamerelativepath.getParent());
				ByteArraySakerFile outfile = new ByteArraySakerFile(outputentrynamerelativepath.getFileName(), bytes);
				outdir.add(outfile);
				outfile.synchronize();
				SakerPath outfilepath = outfile.getSakerPath();
				taskcontext.reportOutputFileDependency(null, outfilepath, outfile.getContentDescriptor());
				return createExecutionResult(outfilepath);
			}
			case OUTPUT_KIND_BUNDLE_STORAGE: {
				MessageDigest digest = FileUtils.getDefaultFileHasher();
				digest.update(bytes.getArray(), bytes.getOffset(), bytes.getLength());
				byte[] contenthash = digest.digest();

				NestBundleClassLoader cl = (NestBundleClassLoader) this.getClass().getClassLoader();
				Path outputdirpath = cl.getBundle().getBundleStoragePath().resolve(outputRelativePath.toString())
						.resolve(StringUtils.toHexString(contenthash));
				Path outputfilepath = outputdirpath.resolve(entryname);

				try {
					BasicFileAttributes presentattrs = Files.readAttributes(outputfilepath, BasicFileAttributes.class);
					if (presentattrs.isRegularFile() && presentattrs.size() == bytes.getLength()) {
						//up to date
						return handleLocalFileResult(taskcontext, outputfilepath);
					}
				} catch (IOException e) {
				}

				Path outputtempfilepath = outputdirpath.resolve(outputfilepath.getFileName() + "." + UUID.randomUUID());

				Files.createDirectories(outputdirpath);
				try (OutputStream fos = Files.newOutputStream(outputtempfilepath)) {
					bytes.writeTo(fos);
				}
				try {
					//no REPLACE_EXISTING
					Files.move(outputtempfilepath, outputfilepath);

					return handleLocalFileResult(taskcontext, outputfilepath);
				} catch (IOException e) {
					//file probably already exists, as others moved to it concurrently
					try {
						BasicFileAttributes presentattrs = Files.readAttributes(outputfilepath,
								BasicFileAttributes.class);
						if (presentattrs.isRegularFile() && presentattrs.size() == bytes.getLength()) {
							//up to date

							return handleLocalFileResult(taskcontext, outputfilepath);
						}
					} catch (IOException e2) {
						e.addSuppressed(e2);
					}
					//hard fail the task
					throw e;
				} finally {
					Files.deleteIfExists(outputtempfilepath);
				}
			}
			default: {
				throw new UnsupportedOperationException("Unrecognized output kind: " + outPathKind);
			}
		}
	}

	private T handleLocalFileResult(TaskContext taskcontext, Path outputfilepath) {
		SakerPath outputlocalsakerpath = SakerPath.valueOf(outputfilepath);

		taskcontext.invalidate(LocalFileProvider.getPathKeyStatic(outputlocalsakerpath));
		taskcontext.getTaskUtilities().getReportExecutionDependency(
				SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(outputlocalsakerpath,
						ImmutableUtils.asUnmodifiableArrayList(taskcontext.getTaskId(), UUID.randomUUID())));
		return createLocalResult(outputlocalsakerpath);
	}

	protected T createLocalResult(SakerPath outputlocalsakerpath) {
		throw new UnsupportedOperationException();
	}

	protected T createExecutionResult(SakerPath outfilepath) {
		throw new UnsupportedOperationException();
	}

	protected abstract T handleLocalFile(TaskContext taskcontext, SakerPath localPath) throws Exception;

	protected abstract T handleExecutionFile(TaskContext taskcontext, SakerFile f) throws Exception;

	protected abstract T localAarNotFound(SakerPath localinputpath);

	protected abstract T executionAarNotFound(SakerPath inputpath);

	@Override
	public Task<? extends T> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputFile);
		out.writeObject(outputRelativePath);

	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = (StructuredTaskResult) in.readObject();
		outputRelativePath = (SakerPath) in.readObject();

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
		result = prime * result + ((inputFile == null) ? 0 : inputFile.hashCode());
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
		AarEntryExporterWorkerTaskFactoryBase<?> other = (AarEntryExporterWorkerTaskFactoryBase<?>) obj;
		if (inputFile == null) {
			if (other.inputFile != null)
				return false;
		} else if (!inputFile.equals(other.inputFile))
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
}
