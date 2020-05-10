package saker.android.main.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedHashSet;
import java.util.Set;

import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.ExecutionAarExtractTaskOutput;
import saker.android.impl.aar.LocalAarExtractTaskOutput;
import saker.android.main.TaskDocs.DocAarExtractTaskOutput;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;

@NestTaskInformation(returnType = @NestTypeUsage(DocAarExtractTaskOutput.class))
@NestInformation("Extracts a given entry from .aar files.\n"
		+ "The task will take an AAR archive file as input and extract the specified entries. The extracted files can be "
		+ "used as an input to other tasks.")
@NestParameterInformation(value = "AAR",
		aliases = { "", "Input" },
		required = true,
		type = @NestTypeUsage(FileLocationTaskOption.class),
		info = @NestInformation("The input AAR to extract the entries from."))
@NestParameterInformation(value = "Entry",
		required = true,
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("Path of the entry that should be extracted from the specified AAR.\n"
				+ "The path should be a forward relative and represent a file or directory in the archive.\n"
				+ "If it's a directory, all entries under that will be extracted."))
@NestParameterInformation(value = "Output",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("A forward relative output path that specifies the output location of the extracted files.\n"
				+ "It can be used to have a better output location than the automatically generated one."))
public class AarExtractTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.aar.extract";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "AAR", "Input" }, required = true)
			public FileLocationTaskOption inputOption;

			@SakerInput(value = { "Entry" }, required = true)
			public SakerPath entryOption;

			@SakerInput(value = { "Output" })
			public SakerPath outputPathOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				if (outputPathOption != null) {
					if (!outputPathOption.isForwardRelative()) {
						taskcontext.abortExecution(new InvalidPathFormatException(
								"Output path must be forward relative: " + outputPathOption));
						return null;
					}
					if (outputPathOption.getFileName() == null) {
						taskcontext.abortExecution(
								new InvalidPathFormatException("Output path has no file name: " + outputPathOption));
						return null;
					}
				}
				if (!entryOption.isForwardRelative()) {
					taskcontext.abortExecution(
							new InvalidPathFormatException("Entry path must be forward relative: " + entryOption));
					return null;
				}
				if (entryOption.getFileName() == null) {
					taskcontext
							.abortExecution(new InvalidPathFormatException("Entry has no file name: " + entryOption));
					return null;
				}

				FileLocation filelocation = TaskOptionUtils.toFileLocation(inputOption, taskcontext);
				String firstname = entryOption.getName(0);
				SakerPath outputresolverelative = entryOption.getNameCount() == 1 ? SakerPath.EMPTY
						: entryOption.subPath(1);

				AarEntryExtractWorkerTaskFactory workertask;
				if (outputPathOption != null) {
					workertask = new AarEntryExtractWorkerTaskFactory(filelocation, outputPathOption, firstname);
				} else {
					workertask = new AarEntryExtractWorkerTaskFactory(filelocation, firstname);
				}

				TaskIdentifier workertaskid = workertask.createTaskId();
				taskcontext.startTask(workertaskid, workertask, null);

				StructuredTaskResult result;
				if (!SakerPath.EMPTY.equals(outputresolverelative)) {
					result = new AarRelativeResolvingStructuredTaskResult(workertaskid, outputresolverelative);
				} else {
					result = new SimpleStructuredObjectTaskResult(workertaskid);
				}

				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
	}

	private static Set<FileLocation> filterFileLocationsForBaseDirectory(Set<? extends FileLocation> files,
			SakerPath path) {
		if (files == null) {
			return null;
		}
		int pathnamecount = path.getNameCount();
		LinkedHashSet<FileLocation> result = new LinkedHashSet<>();
		FileLocationVisitor visitor = new FileLocationVisitor() {
			@Override
			public void visit(LocalFileLocation loc) {
				SakerPath p = loc.getLocalPath();
				if (p.startsWith(path) && p.getNameCount() > pathnamecount) {
					result.add(loc);
				}
			}

			@Override
			public void visit(ExecutionFileLocation loc) {
				SakerPath p = loc.getPath();
				if (p.startsWith(path) && p.getNameCount() > pathnamecount) {
					result.add(loc);
				}
			}
		};

		for (FileLocation f : files) {
			f.accept(visitor);
		}
		return result;
	}

	private static class AarRelativeResolvingStructuredTaskResult implements StructuredTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier taskId;
		private SakerPath relativeResolve;

		/**
		 * For {@link Externalizable}.
		 */
		public AarRelativeResolvingStructuredTaskResult() {
		}

		public AarRelativeResolvingStructuredTaskResult(TaskIdentifier taskId, SakerPath relativeResolve) {
			this.taskId = taskId;
			this.relativeResolve = relativeResolve;
		}

		@Override
		public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
			TaskResultDependencyHandle dephandle = results.getTaskResultDependencyHandle(taskId);
			//TODO add dependency
			AarExtractTaskOutput extractout = (AarExtractTaskOutput) dephandle.get();
			SakerPath relresolve = relativeResolve;

			AarExtractTaskOutput[] result = { null };
			extractout.getFileLocation().accept(new FileLocationVisitor() {
				@Override
				public void visit(LocalFileLocation loc) {
					SakerPath resolvedpath = loc.getLocalPath().resolve(relresolve);
					result[0] = new LocalAarExtractTaskOutput(resolvedpath,
							filterFileLocationsForBaseDirectory(extractout.getDirectoryFileLocations(), resolvedpath));
				}

				@Override
				public void visit(ExecutionFileLocation loc) {
					SakerPath resolvedpath = loc.getPath().resolve(relresolve);
					result[0] = new ExecutionAarExtractTaskOutput(resolvedpath,
							filterFileLocationsForBaseDirectory(extractout.getDirectoryFileLocations(), resolvedpath));
				}
			});
			return result[0];
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(taskId);
			out.writeObject(relativeResolve);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			taskId = SerialUtils.readExternalObject(in);
			relativeResolve = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((relativeResolve == null) ? 0 : relativeResolve.hashCode());
			result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
			AarRelativeResolvingStructuredTaskResult other = (AarRelativeResolvingStructuredTaskResult) obj;
			if (relativeResolve == null) {
				if (other.relativeResolve != null)
					return false;
			} else if (!relativeResolve.equals(other.relativeResolve))
				return false;
			if (taskId == null) {
				if (other.taskId != null)
					return false;
			} else if (!taskId.equals(other.taskId))
				return false;
			return true;
		}

	}
}
