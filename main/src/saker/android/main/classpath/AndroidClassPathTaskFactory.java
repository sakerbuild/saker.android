package saker.android.main.classpath;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import saker.android.api.aapt2.aar.AAPT2AarCompileTaskOutput;
import saker.android.api.aapt2.compile.AAPT2CompileFrontendTaskOutput;
import saker.android.api.aar.AarExtractTaskOutput;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.aar.AarEntryNotFoundException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.ComposedStructuredTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.maven.classpath.api.MavenClassPathTaskBuilder;
import saker.maven.classpath.api.MavenClassPathTaskBuilder.EntryBuilder;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.MavenUtils;
import saker.maven.support.api.dependency.MavenDependencyResolutionTaskOutput;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationUtils;
import saker.maven.support.api.localize.ArtifactLocalizationWorkerTaskOutput;
import saker.nest.utils.FrontendTaskFactory;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;
import saker.std.api.util.SakerStandardUtils;

public class AndroidClassPathTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.classpath";

	//TODO support api.jar in aar bundles

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {
			@SakerInput(value = { "", "AAR", "AARs", "Artifact", "Artifacts" }, required = true)
			public Object artifacts;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				MavenClassPathTaskBuilder cpbuilder = MavenClassPathTaskBuilder.newBuilder();

				handleInputElement(taskcontext, cpbuilder, artifacts);

				TaskIdentifier workertaskid = cpbuilder.buildTaskIdentifier();
				taskcontext.startTask(workertaskid, cpbuilder.buildTask(), null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
	}

	private static void handleInputElement(TaskContext taskcontext, MavenClassPathTaskBuilder cpbuilder, Object elem)
			throws FileNotFoundException {
		while (true) {
			if (elem instanceof MavenDependencyResolutionTaskOutput) {
				MavenDependencyResolutionTaskOutput depoutput = (MavenDependencyResolutionTaskOutput) elem;
				MavenOperationConfiguration depoutmavenconfig = depoutput.getConfiguration();
				cpbuilder.setConfiguration(depoutmavenconfig);
				Collection<ArtifactCoordinates> departifactcoordinates = depoutput.getArtifactCoordinates();

				handleArtifactCoordinates(taskcontext, cpbuilder, depoutmavenconfig, departifactcoordinates);
				return;
			}
			if (elem instanceof ResolvedDependencyArtifact) {
				ResolvedDependencyArtifact departifact = (ResolvedDependencyArtifact) elem;
				MavenOperationConfiguration depoutmavenconfig = departifact.getConfiguration();

				cpbuilder.setConfiguration(depoutmavenconfig);
				Collection<ArtifactCoordinates> departifactcoordinates = ImmutableUtils
						.singletonSet(departifact.getCoordinates());

				handleArtifactCoordinates(taskcontext, cpbuilder, depoutmavenconfig, departifactcoordinates);
				return;
			}
			if (elem instanceof FileLocation) {
				FileLocation fl = (FileLocation) elem;
				handleInputFileLocation(taskcontext, cpbuilder, fl);
				return;
			}
			if (elem instanceof FileCollection) {
				FileCollection filecollection = (FileCollection) elem;
				for (FileLocation fl : filecollection) {
					handleInputFileLocation(taskcontext, cpbuilder, fl);
				}
				return;
			}
			if (elem instanceof AAPT2CompileFrontendTaskOutput) {
				AAPT2CompileFrontendTaskOutput aaptcompileout = (AAPT2CompileFrontendTaskOutput) elem;
				handleInputElement(taskcontext, cpbuilder, aaptcompileout);
				return;
			}
			if (elem instanceof StructuredTaskResult) {
				if (elem instanceof ComposedStructuredTaskResult) {
					StructuredTaskResult nres = ((ComposedStructuredTaskResult) elem)
							.getIntermediateTaskResult(taskcontext);
					if (!elem.equals(nres)) {
						elem = nres;
						continue;
					}
					elem = ((StructuredTaskResult) elem).toResult(taskcontext);
					continue;
				}
				if (elem instanceof StructuredObjectTaskResult) {
					elem = taskcontext.getTaskResult(((StructuredObjectTaskResult) elem).getTaskIdentifier());
					continue;
				}
				if (elem instanceof StructuredMapTaskResult) {
					throw new IllegalArgumentException("Unsupported android classpath input: " + elem);
				}
				if (elem instanceof StructuredListTaskResult) {
					StructuredListTaskResult lres = (StructuredListTaskResult) elem;
					Iterator<? extends StructuredTaskResult> rit = lres.resultIterator();
					while (rit.hasNext()) {
						handleInputElement(taskcontext, cpbuilder, rit.next());
					}
					return;
				}
				elem = ((StructuredTaskResult) elem).toResult(taskcontext);
				continue;
			}
			String elemstr = elem.toString();
			SakerPath execpath;
			try {
				execpath = taskcontext.getTaskWorkingDirectoryPath().tryResolve(SakerPath.valueOf(elemstr));
			} catch (InvalidPathFormatException e) {
				throw new IllegalArgumentException("Failed to parse input classpath path: " + elemstr);
			}
			// check for existence as it may be an invalid path
			SakerFile infile = taskcontext.getTaskUtilities().resolveAtPath(execpath);
			if (infile == null) {
				throw new FileNotFoundException("Input classpath not found: " + execpath);
			}
			taskcontext.reportInputFileDependency(null, execpath, CommonTaskContentDescriptors.PRESENT);
			handleInputFileLocation(taskcontext, cpbuilder, ExecutionFileLocation.create(execpath));
			return;
		}
	}

	private static void handleInputElement(TaskContext taskcontext, MavenClassPathTaskBuilder cpbuilder,
			AAPT2CompileFrontendTaskOutput aaptcompileout) {
		Collection<StructuredTaskResult> aarcompilations = aaptcompileout.getAarCompilations();
		for (StructuredTaskResult aarctaskout : aarcompilations) {
			//TODO apply task change detector for the aar file location
			AAPT2AarCompileTaskOutput aarcompileres = (AAPT2AarCompileTaskOutput) aarctaskout.toResult(taskcontext);
			handleInputFileLocation(taskcontext, cpbuilder, aarcompileres.getAarFile());
		}
	}

	private static void handleAarLibraries(TaskContext taskcontext,
			AarEntryExtractWorkerTaskFactory classesextractworker, FileLocation fl,
			MavenClassPathTaskBuilder cpbuilder) {
		AarEntryExtractWorkerTaskFactory extracttask = new AarEntryExtractWorkerTaskFactory(fl,
				classesextractworker.getOutputRelativePath(),
				AarEntryExtractWorkerTaskFactory.ENTRY_NAME_LIBRARIES_DIRECTORY);
		TaskIdentifier entryExtractTaskId = extracttask.createTaskId();

		//TODO run libs extractions in parallel 
		AarExtractTaskOutput libextractout = taskcontext.getTaskUtilities().runTaskResult(entryExtractTaskId,
				extracttask);
		//TODO install an appropriate task output change detector
		try {
			Set<FileLocation> libfilelocations = libextractout.getDirectoryFileLocations();
			if (libfilelocations != null) {
				for (FileLocation libjarfilelocation : libfilelocations) {
					cpbuilder.add(EntryBuilder.newBuilder().setInput(libjarfilelocation));
				}
			}
		} catch (AarEntryNotFoundException e) {
			//ignore
		}
	}

	private static void handleInputFileLocation(TaskContext taskcontext, MavenClassPathTaskBuilder cpbuilder,
			FileLocation fl) {
		if (FileUtils.hasExtensionIgnoreCase(SakerStandardUtils.getFileLocationFileName(fl), "aar")) {
			EntryBuilder entrybuilder = MavenClassPathTaskBuilder.EntryBuilder.newBuilder();
			AarEntryExtractWorkerTaskFactory extracttask = new AarEntryExtractWorkerTaskFactory(fl,
					AarEntryExtractWorkerTaskFactory.ENTRY_NAME_CLASSES_JAR);
			TaskIdentifier entryExtractTaskId = extracttask.createTaskId();
			taskcontext.startTask(entryExtractTaskId, extracttask, null);

			handleAarLibraries(taskcontext, extracttask, fl, cpbuilder);

			//TODO set implementation version key
			entrybuilder.setInput(new ClassesJarFileLocationStructuredTaskResult(entryExtractTaskId));
			cpbuilder.add(entrybuilder);
		} else {
			cpbuilder.add(EntryBuilder.newBuilder().setInput(fl));
		}
	}

	private static void handleArtifactCoordinates(TaskContext taskcontext, MavenClassPathTaskBuilder cpbuilder,
			MavenOperationConfiguration depoutmavenconfig, Collection<ArtifactCoordinates> departifactcoordinates) {
		Set<ArtifactCoordinates> tolocalizeartifacts = new HashSet<>();

		for (ArtifactCoordinates coords : departifactcoordinates) {
			if ("aar".equals(coords.getExtension())) {
				tolocalizeartifacts.add(coords);
			}
		}

		TaskIdentifier localizationtaskid = ArtifactLocalizationUtils
				.createLocalizeArtifactsTaskIdentifier(depoutmavenconfig, tolocalizeartifacts);
		ArtifactLocalizationTaskOutput localizationout = taskcontext.getTaskUtilities().runTaskResult(
				localizationtaskid,
				ArtifactLocalizationUtils.createLocalizeArtifactsTaskFactory(depoutmavenconfig, tolocalizeartifacts),
				null);

		for (ArtifactCoordinates coords : departifactcoordinates) {
			EntryBuilder entrybuilder = MavenClassPathTaskBuilder.EntryBuilder.newBuilder();
			if ("aar".equals(coords.getExtension())) {
				StructuredTaskResult localizationresult = localizationout.getLocalizationResult(coords);
				if (localizationresult == null) {
					throw new RuntimeException("Artifact localization results not found: " + coords);
				}
				TaskResultDependencyHandle artlocdephandle = localizationresult.toResultDependencyHandle(taskcontext);
				ArtifactLocalizationWorkerTaskOutput artifactlocalizationresult = (ArtifactLocalizationWorkerTaskOutput) artlocdephandle
						.get();

				LocalFileLocation artifactlocalfilelocation = LocalFileLocation
						.create(artifactlocalizationresult.getLocalPath());

				SakerPath configmavenlocalrepositorypath = depoutmavenconfig.getLocalRepositoryPath();
				if (configmavenlocalrepositorypath == null) {
					configmavenlocalrepositorypath = MavenUtils.getDefaultMavenLocalRepositoryLocation(taskcontext);
				}
				String repohash = StringUtils
						.toHexString(FileUtils.hashString(configmavenlocalrepositorypath.toString()));
				SakerPath extractoutputrelativepath = SakerPath.valueOf(TASK_NAME).resolve(repohash)
						.resolve(coords.getGroupId(), coords.getArtifactId(), coords.getVersion());

				AarEntryExtractWorkerTaskFactory extracttask = new AarEntryExtractWorkerTaskFactory(
						artifactlocalfilelocation, extractoutputrelativepath,
						AarEntryExtractWorkerTaskFactory.ENTRY_NAME_CLASSES_JAR);
				TaskIdentifier entryExtractTaskId = extracttask.createTaskId();
				taskcontext.startTask(entryExtractTaskId, extracttask, null);

				handleAarLibraries(taskcontext, extracttask, artifactlocalfilelocation, cpbuilder);

				//TODO set implementation version key

				entrybuilder.setInput(new ClassesJarFileLocationStructuredTaskResult(entryExtractTaskId));
			} else {
				entrybuilder.setInput(coords);
			}
			entrybuilder.setSourceAttachment(createSourceArtifactCoordinates(coords));
			cpbuilder.add(entrybuilder);
		}
	}

	public static ArtifactCoordinates createSourceArtifactCoordinates(ArtifactCoordinates acoords) {
		//always expect the sources to be in an artifact with "jar" extension
		//    e.g. for aar (android libs) artifacts, the sources are still in a "jar" artifact, so using the same 
		//         extension will fail
		ArtifactCoordinates sourceacoords = new ArtifactCoordinates(acoords.getGroupId(), acoords.getArtifactId(),
				"sources", "jar", acoords.getVersion());
		return sourceacoords;
	}

	private static class AarExtractTaskOutputFileLocationEqualityChangeDetector
			implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private FileLocation fileLocation;

		/**
		 * For {@link Externalizable}.
		 */
		public AarExtractTaskOutputFileLocationEqualityChangeDetector() {
		}

		public AarExtractTaskOutputFileLocationEqualityChangeDetector(FileLocation fileLocation) {
			this.fileLocation = fileLocation;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (!(taskoutput instanceof AarExtractTaskOutput)) {
				return true;
			}
			return !Objects.equals(this.fileLocation, ((AarExtractTaskOutput) taskoutput).getFileLocation());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(fileLocation);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			fileLocation = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fileLocation == null) ? 0 : fileLocation.hashCode());
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
			AarExtractTaskOutputFileLocationEqualityChangeDetector other = (AarExtractTaskOutputFileLocationEqualityChangeDetector) obj;
			if (fileLocation == null) {
				if (other.fileLocation != null)
					return false;
			} else if (!fileLocation.equals(other.fileLocation))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + fileLocation + "]";
		}

	}

	public static class ClassesJarFileLocationStructuredTaskResult implements StructuredTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier entryExtractTaskId;

		/**
		 * For {@link Externalizable}.
		 */
		public ClassesJarFileLocationStructuredTaskResult() {
		}

		public ClassesJarFileLocationStructuredTaskResult(TaskIdentifier entryExtractTaskId) {
			this.entryExtractTaskId = entryExtractTaskId;
		}

		@Override
		public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
			TaskResultDependencyHandle dephandle = results.getTaskResultDependencyHandle(entryExtractTaskId);
			AarExtractTaskOutput out = (AarExtractTaskOutput) dephandle.get();
			FileLocation result = out.getFileLocation();
			dephandle.setTaskOutputChangeDetector(new AarExtractTaskOutputFileLocationEqualityChangeDetector(result));
			return result;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(entryExtractTaskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			entryExtractTaskId = (TaskIdentifier) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((entryExtractTaskId == null) ? 0 : entryExtractTaskId.hashCode());
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
			ClassesJarFileLocationStructuredTaskResult other = (ClassesJarFileLocationStructuredTaskResult) obj;
			if (entryExtractTaskId == null) {
				if (other.entryExtractTaskId != null)
					return false;
			} else if (!entryExtractTaskId.equals(other.entryExtractTaskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + entryExtractTaskId + "]";
		}
	}

}
