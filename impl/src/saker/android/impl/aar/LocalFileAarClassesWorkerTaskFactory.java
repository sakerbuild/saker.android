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

import saker.android.api.aar.AarClassesTaskOutput;
import saker.android.main.aar.AarClassesTaskFactory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
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

public class LocalFileAarClassesWorkerTaskFactory
		implements TaskFactory<AarClassesTaskOutput>, Task<AarClassesTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation inputFile;
	private SakerPath outputRelativePath;

	/**
	 * For {@link Externalizable}.
	 */
	public LocalFileAarClassesWorkerTaskFactory() {
	}

	public LocalFileAarClassesWorkerTaskFactory(FileLocation inputFile, SakerPath outputRelativePath) {
		this.inputFile = inputFile;
		this.outputRelativePath = outputRelativePath;
	}

	@Override
	public AarClassesTaskOutput run(TaskContext taskcontext) throws Exception {
		System.out.println("LocalFileAarClassesWorkerTaskFactory.run() " + inputFile);
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}
		taskcontext.setStandardOutDisplayIdentifier(AarClassesTaskFactory.TASK_NAME);

		AarClassesTaskOutput[] result = { null };
		inputFile.accept(new FileLocationVisitor() {
			@Override
			public void visit(ExecutionFileLocation loc) {
				try {
					result[0] = handleExecutionFile(taskcontext, loc.getPath());
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

			@Override
			public void visit(LocalFileLocation loc) {
				try {
					result[0] = handleLocalFile(taskcontext, loc.getLocalPath());
				} catch (Exception e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			}

		});
		return result[0];
	}

	private AarClassesTaskOutput handleLocalFile(TaskContext taskcontext, SakerPath localpath) throws Exception {
		ContentDescriptor cd = taskcontext.getTaskUtilities().getReportExecutionDependency(SakerStandardUtils
				.createLocalFileContentDescriptorExecutionProperty(localpath, taskcontext.getTaskId()));
		if (cd == null || DirectoryContentDescriptor.INSTANCE.equals(cd)) {
			return new AarNotFoundAarClassesTaskOutput(localpath);
		}

		ByteArrayRegion classesbytes = getLocalFileClassesJarBytes(localpath);
		if (classesbytes == null) {
			return new ClassesNotFoundAarClassesTaskOutput(localpath);
		}
		return handleClassesJarBytes(taskcontext, classesbytes);
	}

	private AarClassesTaskOutput handleClassesJarBytes(TaskContext taskcontext, ByteArrayRegion classesbytes)
			throws AssertionError, IOException {
		MessageDigest digest = FileUtils.getDefaultFileHasher();
		digest.update(classesbytes.getArray(), classesbytes.getOffset(), classesbytes.getLength());
		byte[] contenthash = digest.digest();

		NestBundleClassLoader cl = (NestBundleClassLoader) this.getClass().getClassLoader();
		Path outputdirpath = cl.getBundle().getBundleStoragePath().resolve(AarClassesTaskFactory.TASK_NAME)
				.resolve(outputRelativePath.getParent().toString()).resolve(StringUtils.toHexString(contenthash));
		String outfname = outputRelativePath.getFileName() + ".classes.jar";
		Path outputfilepath = outputdirpath.resolve(outfname);
		Path outputtempfilepath = outputdirpath.resolve(outfname + "." + UUID.randomUUID());

		try {
			BasicFileAttributes presentattrs = Files.readAttributes(outputfilepath, BasicFileAttributes.class);
			if (presentattrs.isRegularFile() && presentattrs.size() == classesbytes.getLength()) {
				//up to date
				return createTaskResultReportOutputDependency(taskcontext, outputfilepath);
			}
		} catch (IOException e) {
		}

		Files.createDirectories(outputdirpath);
		try (OutputStream fos = Files.newOutputStream(outputtempfilepath)) {
			classesbytes.writeTo(fos);
		}
		try {
			//no REPLACE_EXISTING
			Files.move(outputtempfilepath, outputfilepath);
			return createTaskResultReportOutputDependency(taskcontext, outputfilepath);
		} catch (IOException e) {
			//file probably already exists, as others moved to it concurrently
			try {
				BasicFileAttributes presentattrs = Files.readAttributes(outputfilepath, BasicFileAttributes.class);
				if (presentattrs.isRegularFile() && presentattrs.size() == classesbytes.getLength()) {
					//up to date
					return createTaskResultReportOutputDependency(taskcontext, outputfilepath);
				}
			} catch (IOException e2) {
				e.addSuppressed(e2);
			}
			//hard fail the task
			throw e;
		}
	}

	public static ByteArrayRegion getLocalFileClassesJarBytes(SakerPath localpath) throws IOException, ZipException {
		ByteArrayRegion classesbytes;
		try (ZipFile zf = new ZipFile(LocalFileProvider.toRealPath(localpath).toFile())) {
			ZipEntry classesentry = zf.getEntry("classes.jar");
			if (classesentry == null) {
				return null;
			}
			try (InputStream zipin = zf.getInputStream(classesentry)) {
				classesbytes = StreamUtils.readStreamFully(zipin);
			}
		}
		return classesbytes;
	}

	private static AarClassesTaskOutput createTaskResultReportOutputDependency(TaskContext taskcontext,
			Path outputfilepath) {
		SakerPath outputlocalsakerpath = SakerPath.valueOf(outputfilepath);

		taskcontext.invalidate(LocalFileProvider.getPathKeyStatic(outputlocalsakerpath));
		taskcontext.getTaskUtilities().getReportExecutionDependency(
				SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(outputlocalsakerpath,
						ImmutableUtils.asUnmodifiableArrayList(taskcontext.getTaskId(), UUID.randomUUID())));
		return new LocalAarClassesTaskOutput(outputlocalsakerpath);
	}

	private AarClassesTaskOutput handleExecutionFile(TaskContext taskcontext, SakerPath inputFile) throws Exception {
		SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(inputFile);
		if (f == null) {
			taskcontext.reportInputFileDependency(null, inputFile, CommonTaskContentDescriptors.IS_NOT_FILE);
			return new AarNotFoundAarClassesTaskOutput(inputFile);
		}
		taskcontext.reportInputFileDependency(null, inputFile, f.getContentDescriptor());

		ByteArrayRegion classesbytes = ExecutionFileAarClassesWorkerTaskFactory.getFileClassesJarBytes(f);
		if (classesbytes == null) {
			return new ClassesNotFoundAarClassesTaskOutput(inputFile);
		}
		return handleClassesJarBytes(taskcontext, classesbytes);
	}

	@Override
	public Task<? extends AarClassesTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(inputFile);
		out.writeObject(outputRelativePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFile = (FileLocation) in.readObject();
		outputRelativePath = (SakerPath) in.readObject();
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
		LocalFileAarClassesWorkerTaskFactory other = (LocalFileAarClassesWorkerTaskFactory) obj;
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
