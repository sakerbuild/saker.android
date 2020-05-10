package saker.android.main.d8.option;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.impl.d8.option.D8InputOption;
import saker.android.impl.d8.option.FileD8InputOption;
import saker.android.impl.d8.option.JavaClassPathD8InputOption;
import saker.android.impl.d8.option.JavaCompilationD8InputOption;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ReducedWildcardPath;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.java.compiler.api.classpath.ClassPathReference;
import saker.java.compiler.api.classpath.JavaClassPath;
import saker.java.compiler.api.classpath.JavaClassPathBuilder;
import saker.java.compiler.api.compile.JavaCompilationWorkerTaskIdentifier;
import saker.java.compiler.api.compile.JavaCompilerTaskFrontendOutput;
import saker.java.compiler.api.compile.JavaCompilerWorkerTaskOutput;
import saker.maven.support.api.ArtifactCoordinates;
import saker.maven.support.api.MavenOperationConfiguration;
import saker.maven.support.api.dependency.MavenDependencyResolutionTaskOutput;
import saker.maven.support.api.dependency.ResolvedDependencyArtifact;
import saker.maven.support.api.download.ArtifactDownloadTaskOutput;
import saker.maven.support.api.download.ArtifactDownloadWorkerTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationTaskOutput;
import saker.maven.support.api.localize.ArtifactLocalizationUtils;
import saker.maven.support.api.localize.ArtifactLocalizationWorkerTaskOutput;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.sdk.support.api.SDKPathReference;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileCollection;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.LocalFileLocation;

@NestInformation("Input for D8 operation.")
public abstract class D8InputTaskOption {
	public abstract Set<D8InputOption> toInputOption(TaskContext taskcontext);

	public CompilationIdentifier inferCompilationIdentifier() {
		return null;
	}

	public static D8InputTaskOption valueOf(SakerPath path) {
		return valueOf(WildcardPath.valueOf(path));
	}

	public static D8InputTaskOption valueOf(FileLocation input) {
		FileD8InputOption fileoption = new FileD8InputOption(input);
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				return ImmutableUtils.singletonSet(fileoption);
			}
		};
	}

	public static D8InputTaskOption valueOf(FileCollection input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				Set<D8InputOption> result = new LinkedHashSet<>();
				input.forEach(fl -> result.add(new FileD8InputOption(fl)));
				return result;
			}
		};
	}

	public static D8InputTaskOption valueOf(WildcardPath input) {
		ReducedWildcardPath reduced = input.reduce();
		if (reduced.getWildcard() == null) {
			SakerPath path = reduced.getFile();
			if (path.isAbsolute()) {
				return valueOf(ExecutionFileLocation.create(path));
			}
			return new D8InputTaskOption() {
				@Override
				public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
					return ImmutableUtils.singletonSet(new FileD8InputOption(
							ExecutionFileLocation.create(taskcontext.getTaskWorkingDirectoryPath().resolve(path))));
				}
			};
		}
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				FileCollectionStrategy collectionstrategy = WildcardFileCollectionStrategy
						.create(taskcontext.getTaskWorkingDirectoryPath(), input);
				NavigableMap<SakerPath, SakerFile> cpfiles = taskcontext.getTaskUtilities()
						.collectFilesReportAdditionDependency(null, collectionstrategy);
				taskcontext.getTaskUtilities().reportInputFileDependency(null,
						ObjectUtils.singleValueMap(cpfiles.navigableKeySet(), CommonTaskContentDescriptors.PRESENT));

				Set<D8InputOption> result = new LinkedHashSet<>();
				cpfiles.keySet().forEach(path -> result.add(new FileD8InputOption(ExecutionFileLocation.create(path))));
				return result;
			}
		};
	}

	public static D8InputTaskOption valueOf(String input) {
		return valueOf(WildcardPath.valueOf(input));
	}

	public static D8InputTaskOption valueOf(ResolvedDependencyArtifact input) {
		return createForResolvedArtifacts(input.getConfiguration(), ImmutableUtils.singletonSet(input));
	}

	public static D8InputTaskOption valueOf(MavenDependencyResolutionTaskOutput input) {
		Collection<ResolvedDependencyArtifact> resolvedartifacts = input.getResolvedArtifacts();
		return createForResolvedArtifacts(input.getConfiguration(), resolvedartifacts);
	}

	private static D8InputTaskOption createForResolvedArtifacts(MavenOperationConfiguration config,
			Collection<? extends ResolvedDependencyArtifact> input) {
		Set<ArtifactCoordinates> coordinates = new LinkedHashSet<>();
		for (ResolvedDependencyArtifact in : input) {
			coordinates.add(in.getCoordinates());
		}
		TaskFactory<? extends ArtifactLocalizationTaskOutput> taskfactory = ArtifactLocalizationUtils
				.createLocalizeArtifactsTaskFactory(config, coordinates);
		TaskIdentifier taskid = ArtifactLocalizationUtils.createLocalizeArtifactsTaskIdentifier(config, coordinates);
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				ArtifactLocalizationTaskOutput localizationresult = taskcontext.getTaskUtilities().runTaskResult(taskid,
						taskfactory);
				@SuppressWarnings("unchecked")
				Collection<SakerPath> paths = (Collection<SakerPath>) localizationresult.getArtifactLocalPaths()
						.toResult(taskcontext);
				Set<D8InputOption> result = new LinkedHashSet<>();
				paths.forEach(path -> result.add(new FileD8InputOption(LocalFileLocation.create(path))));
				return result;
			}
		};
	}

	public static D8InputTaskOption valueOf(ArtifactDownloadTaskOutput input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				StructuredListTaskResult artpathstaskresult = input.getArtifactPaths();
				@SuppressWarnings("unchecked")
				Collection<SakerPath> paths = (Collection<SakerPath>) artpathstaskresult.toResult(taskcontext);
				Set<D8InputOption> result = new LinkedHashSet<>();
				paths.forEach(path -> result.add(new FileD8InputOption(ExecutionFileLocation.create(path))));
				return result;
			}
		};
	}

	public static D8InputTaskOption valueOf(ArtifactDownloadWorkerTaskOutput input) {
		return valueOf(ExecutionFileLocation.create(input.getPath()));
	}

	public static D8InputTaskOption valueOf(ArtifactLocalizationTaskOutput input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				StructuredListTaskResult artpathstaskresult = input.getArtifactLocalPaths();
				@SuppressWarnings("unchecked")
				Collection<SakerPath> paths = (Collection<SakerPath>) artpathstaskresult.toResult(taskcontext);
				Set<D8InputOption> result = new LinkedHashSet<>();
				paths.forEach(path -> result.add(new FileD8InputOption(LocalFileLocation.create(path))));
				return result;
			}
		};
	}

	public static D8InputTaskOption valueOf(ArtifactLocalizationWorkerTaskOutput input) {
		return valueOf(LocalFileLocation.create(input.getLocalPath()));
	}

	public static D8InputTaskOption valueOf(JavaCompilerTaskFrontendOutput input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				JavaCompilationWorkerTaskIdentifier compiletaskid = input.getTaskIdentifier();
				TaskDependencyFuture<?> depres = taskcontext.getTaskDependencyFuture(compiletaskid);
				//wait for the task in order for the consumer to be able to use getFinished
				depres.get();
				depres.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.isInstanceOf(JavaCompilerWorkerTaskOutput.class));

				return ImmutableUtils.singletonSet(new JavaCompilationD8InputOption(compiletaskid));
			}

			@Override
			public CompilationIdentifier inferCompilationIdentifier() {
				try {
					return CompilationIdentifier.valueOf(input.getTaskIdentifier().getPassIdentifier());
				} catch (Exception e) {
					// the identifier may have an illegal format.
					// not under normal circumstances, but handle anyway
					return null;
				}
			}
		};
	}

	public static D8InputTaskOption valueOf(JavaCompilerWorkerTaskOutput input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				return ImmutableUtils
						.singletonSet(new JavaCompilationD8InputOption(input.getCompilationTaskIdentifier()));
			}

			@Override
			public CompilationIdentifier inferCompilationIdentifier() {
				try {
					return CompilationIdentifier.valueOf(input.getCompilationTaskIdentifier().getPassIdentifier());
				} catch (Exception e) {
					// the identifier may have an illegal format.
					// not under normal circumstances, but handle anyway
					return null;
				}
			}
		};
	}

	public static D8InputTaskOption valueOf(JavaClassPath input) {
		return new D8InputTaskOption() {
			@Override
			public Set<D8InputOption> toInputOption(TaskContext taskcontext) {
				return ImmutableUtils.singletonSet(new JavaClassPathD8InputOption(input));
			}
		};
	}

	public static D8InputTaskOption valueOf(ClassPathReference input) {
		JavaClassPathBuilder builder = JavaClassPathBuilder.newBuilder();
		builder.addClassPath(input);
		return valueOf(builder.build());
	}

	public static D8InputTaskOption valueOf(SDKPathReference input) {
		JavaClassPathBuilder builder = JavaClassPathBuilder.newBuilder();
		builder.addSDKClassPath(input);
		return valueOf(builder.build());
	}
}
