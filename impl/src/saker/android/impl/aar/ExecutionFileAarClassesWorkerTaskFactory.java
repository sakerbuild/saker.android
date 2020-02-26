package saker.android.impl.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import saker.android.api.aar.AarClassesTaskOutput;
import saker.android.main.aar.AarClassesTaskFactory;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.trace.BuildTrace;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public class ExecutionFileAarClassesWorkerTaskFactory
		implements TaskFactory<AarClassesTaskOutput>, Task<AarClassesTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation inputFile;
	private SakerPath outputRelativePath;

	/**
	 * For {@link Externalizable}.
	 */
	public ExecutionFileAarClassesWorkerTaskFactory() {
	}

	public ExecutionFileAarClassesWorkerTaskFactory(FileLocation inputFile, SakerPath outputRelativePath) {
		this.inputFile = inputFile;
		this.outputRelativePath = outputRelativePath;
	}

	@Override
	public AarClassesTaskOutput run(TaskContext taskcontext) throws Exception {
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

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		ByteArrayRegion classesbytes = LocalFileAarClassesWorkerTaskFactory.getLocalFileClassesJarBytes(localpath);
		if (classesbytes == null) {
			return new ClassesNotFoundAarClassesTaskOutput(localpath);
		}
		return createAarForClassesJarBytes(classesbytes, builddir, taskcontext);
	}

	private AarClassesTaskOutput handleExecutionFile(TaskContext taskcontext, SakerPath inputFile) throws Exception {
		SakerFile f = taskcontext.getTaskUtilities().resolveFileAtPath(inputFile);
		if (f == null) {
			taskcontext.reportInputFileDependency(null, inputFile, CommonTaskContentDescriptors.IS_NOT_FILE);
			return new AarNotFoundAarClassesTaskOutput(inputFile);
		}
		taskcontext.reportInputFileDependency(null, inputFile, f.getContentDescriptor());

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		ByteArrayRegion classesbytes = getFileClassesJarBytes(f);
		if (classesbytes == null) {
			return new ClassesNotFoundAarClassesTaskOutput(inputFile);
		}
		return createAarForClassesJarBytes(classesbytes, builddir, taskcontext);
	}

	public static ByteArrayRegion getFileClassesJarBytes(SakerFile f) throws IOException {
		ByteArrayRegion classesbytes = null;
		try (InputStream instream = f.openInputStream();
				ZipInputStream zipin = new ZipInputStream(instream)) {
			for (ZipEntry ze; (ze = zipin.getNextEntry()) != null;) {
				if (!"classes.jar".equals(ze.getName())) {
					continue;
				}
				//found classes.jar in aar
				classesbytes = StreamUtils.readStreamFully(zipin);
			}
		}
		return classesbytes;
	}

	private AarClassesTaskOutput createAarForClassesJarBytes(ByteArrayRegion classesbytes, SakerDirectory builddir,
			TaskContext taskcontext) throws IOException {
		SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				SakerPath.valueOf(AarClassesTaskFactory.TASK_NAME).resolve(outputRelativePath.getParent()));
		ByteArraySakerFile outfile = new ByteArraySakerFile(outputRelativePath.getFileName() + ".classes.jar",
				classesbytes);
		outdir.add(outfile);
		outfile.synchronize();
		SakerPath outfilepath = outfile.getSakerPath();
		taskcontext.reportOutputFileDependency(null, outfilepath, outfile.getContentDescriptor());
		return new ExecutionAarClassesTaskOutput(outfilepath);
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
		ExecutionFileAarClassesWorkerTaskFactory other = (ExecutionFileAarClassesWorkerTaskFactory) obj;
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
