package saker.android.main.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import saker.android.impl.aar.AarEntryExporterWorkerTaskFactoryBase;
import saker.android.impl.aar.AarEntryExtractTaskOutput;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
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
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;
import saker.maven.classpath.api.MavenClassPathTaskBuilder;
import saker.maven.classpath.api.MavenClassPathTaskBuilder.EntryBuilder;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.dependency.MavenDependencyResolutionTaskOutput;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationUtils;
import saker.maven.support.api.localize.ArtifactLocalizationWorkerTaskOutput;
import saker.maven.support.main.configuration.option.MavenConfigurationTaskOption;
import saker.nest.utils.FrontendTaskFactory;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;

public class AndroidMavenClassPathTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.android.classpath.maven";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {
			@SakerInput(value = { "", "Artifact", "Artifacts" }, required = true)
			public Object artifacts;

			@SakerInput(value = { "Configuration" })
			public MavenConfigurationTaskOption configuration;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}

				if (artifacts instanceof StructuredTaskResult) {
					artifacts = ((StructuredTaskResult) artifacts).toResult(taskcontext);
				}

				MavenClassPathTaskBuilder cpbuilder = MavenClassPathTaskBuilder.newBuilder();
				if (configuration != null) {
					cpbuilder.setConfiguration(configuration.createConfiguration());
				}

				if (artifacts instanceof MavenDependencyResolutionTaskOutput) {
					MavenDependencyResolutionTaskOutput depoutput = (MavenDependencyResolutionTaskOutput) artifacts;
					MavenOperationConfiguration depoutmavenconfig = depoutput.getConfiguration();
					cpbuilder.setConfiguration(depoutmavenconfig);
					Collection<ArtifactCoordinates> departifactcoordinates = depoutput.getArtifactCoordinates();

					handleArtifactCoordinates(taskcontext, cpbuilder, depoutmavenconfig, departifactcoordinates);
				} else if (artifacts instanceof ResolvedDependencyArtifact) {
					ResolvedDependencyArtifact departifact = (ResolvedDependencyArtifact) artifacts;
					MavenOperationConfiguration depoutmavenconfig = departifact.getConfiguration();

					cpbuilder.setConfiguration(depoutmavenconfig);
					Collection<ArtifactCoordinates> departifactcoordinates = ImmutableUtils
							.singletonSet(departifact.getCoordinates());

					handleArtifactCoordinates(taskcontext, cpbuilder, depoutmavenconfig, departifactcoordinates);
				} else {
					throw new UnsupportedOperationException("Unrecognized input: " + artifacts);
				}

				TaskIdentifier workertaskid = cpbuilder.buildTaskIdentifier();
				taskcontext.startTask(workertaskid, cpbuilder.buildTask(), null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}

		};
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
		taskcontext.startTask(localizationtaskid,
				ArtifactLocalizationUtils.createLocalizeArtifactsTaskFactory(depoutmavenconfig, tolocalizeartifacts),
				null);

		for (ArtifactCoordinates coords : departifactcoordinates) {
			EntryBuilder entrybuilder = MavenClassPathTaskBuilder.EntryBuilder.newBuilder();
			if ("aar".equals(coords.getExtension())) {
				ArtifactLocalizationFileLocationStructuredTaskResult artifactfilelocationtaskresult = new ArtifactLocalizationFileLocationStructuredTaskResult(
						localizationtaskid, coords);
				//TODO this user.dir/.m2/repository should be depended on an environment property or something
				String repohash = StringUtils
						.toHexString(FileUtils.hashString(Objects.toString(depoutmavenconfig.getLocalRepositoryPath(),
								System.getProperty("user.dir") + "/.m2/repository")));
				SakerPath extractoutputrelativepath = SakerPath.valueOf(TASK_NAME)
						.resolve(coords.getGroupId(), coords.getArtifactId(), coords.getVersion()).resolve(repohash);

				AarEntryExtractWorkerTaskFactory extracttask = new AarEntryExtractWorkerTaskFactory(
						artifactfilelocationtaskresult, extractoutputrelativepath,
						AarEntryExporterWorkerTaskFactoryBase.OUTPUT_KIND_BUNDLE_STORAGE,
						AarEntryExtractWorkerTaskFactory.ENTRY_NAME_CLASSES_JAR);
				TaskIdentifier entryExtractTaskId = extracttask.createTaskId();
				taskcontext.startTask(entryExtractTaskId, extracttask, null);

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
			AarEntryExtractTaskOutput out = (AarEntryExtractTaskOutput) dephandle.get();
			FileLocation result = out.getFileLocation();
			dephandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
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

	public static class ArtifactLocalizationFileLocationStructuredTaskResult
			implements StructuredTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier localizationTaskId;
		private ArtifactCoordinates coordinates;

		/**
		 * For {@link Externalizable}.
		 */
		public ArtifactLocalizationFileLocationStructuredTaskResult() {
		}

		public ArtifactLocalizationFileLocationStructuredTaskResult(TaskIdentifier localizationTaskId,
				ArtifactCoordinates coordinates) {
			this.localizationTaskId = localizationTaskId;
			this.coordinates = coordinates;
		}

		@Override
		public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
			//TODO report dependencies for the handles

			TaskResultDependencyHandle localizedephandle = results.getTaskResultDependencyHandle(localizationTaskId);
			ArtifactLocalizationTaskOutput localizationout = (ArtifactLocalizationTaskOutput) localizedephandle.get();

			StructuredTaskResult localizationresult = localizationout.getLocalizationResult(coordinates);
			if (localizationresult == null) {
				throw new RuntimeException("Artifact localization results not found: " + coordinates);
			}
			TaskResultDependencyHandle artlocdephandle = localizationresult.toResultDependencyHandle(results);
			ArtifactLocalizationWorkerTaskOutput artifactlocalizationresult = (ArtifactLocalizationWorkerTaskOutput) artlocdephandle
					.get();

			return LocalFileLocation.create(artifactlocalizationresult.getLocalPath());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(localizationTaskId);
			out.writeObject(coordinates);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			localizationTaskId = SerialUtils.readExternalObject(in);
			coordinates = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((coordinates == null) ? 0 : coordinates.hashCode());
			result = prime * result + ((localizationTaskId == null) ? 0 : localizationTaskId.hashCode());
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
			ArtifactLocalizationFileLocationStructuredTaskResult other = (ArtifactLocalizationFileLocationStructuredTaskResult) obj;
			if (coordinates == null) {
				if (other.coordinates != null)
					return false;
			} else if (!coordinates.equals(other.coordinates))
				return false;
			if (localizationTaskId == null) {
				if (other.localizationTaskId != null)
					return false;
			} else if (!localizationTaskId.equals(other.localizationTaskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[localizationTaskId=" + localizationTaskId + ", coordinates="
					+ coordinates + "]";
		}

	}
}
