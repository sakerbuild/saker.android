package saker.android.main.apk.create;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;

import saker.android.api.aapt2.link.Aapt2LinkInputLibrary;
import saker.android.api.aapt2.link.Aapt2LinkWorkerTaskOutput;
import saker.android.main.AndroidFrontendUtils;
import saker.android.main.TaskDocs.DocApkCreatorTaskOutput;
import saker.android.main.TaskDocs.DocAssetsDirectory;
import saker.android.main.TaskDocs.DocLibraryEntryOption;
import saker.android.main.aapt2.Aapt2LinkTaskFactory;
import saker.android.main.apk.create.option.ApkClassesTaskOption;
import saker.android.main.apk.create.option.ApkLibraryLocationTaskOption;
import saker.android.main.apk.create.option.ApkResourcesTaskOption;
import saker.android.main.d8.D8TaskFactory;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.main.file.option.FileLocationTaskOption;
import saker.std.main.file.utils.TaskOptionUtils;
import saker.zip.api.create.IncludeResourceMapping;
import saker.zip.api.create.ZipCreationTaskBuilder;
import saker.zip.api.create.ZipResourceEntry;

@NestTaskInformation(returnType = @NestTypeUsage(DocApkCreatorTaskOutput.class))
@NestInformation("Creates an Android APK based on the specified inputs.\n"
		+ "The build task generates an APK that contains the spcified resources. The task doesn't performs ZIP alignment, "
		+ "and doesn't sign the created APK.")
@NestParameterInformation(value = "Resources",
		aliases = { "" },
		required = true,
		type = @NestTypeUsage(value = Collection.class, elementTypes = { ApkResourcesTaskOption.class }),
		info = @NestInformation("Specifies the resources to be included in the created APK.\n"
				+ "The parameter takes one or more resource APKs that are directly included in the output.\n"
				+ "The parameter accepts the output of the " + Aapt2LinkTaskFactory.TASK_NAME
				+ "() task in which case all the linked resources will be part of the output. "
				+ "The task will also include the assets and JNI libraries from referenced AARs."))
@NestParameterInformation(value = "Classes",
		type = @NestTypeUsage(ApkClassesTaskOption.class),
		info = @NestInformation("Specifies the Java classes that should be part of the APK.\n"
				+ "The parameter accepts the output of the " + D8TaskFactory.TASK_NAME + "() task."))

@NestParameterInformation(value = "Assets",
		type = @NestTypeUsage(value = Collection.class, elementTypes = { DocAssetsDirectory.class }),
		info = @NestInformation("One or more paths to assets directories for the APK.\n"
				+ "All files in each specified directory will be added to the assets/ directory in the output APK."))

@NestParameterInformation(value = "Libraries",
		aliases = { "Libs", "Lib" },
		type = @NestTypeUsage(value = Map.class, elementTypes = { SakerPath.class, DocLibraryEntryOption.class }),
		info = @NestInformation("Specifies the libraries that should be added to the APK in the lib directory.\n"
				+ "The parameter accepts a map with keys that correspond to the library name. The values are "
				+ "maps that contain the library ABI as keys and the location of the library to put in the APK."))
@NestParameterInformation(value = "CompressLibraries",
		type = @NestTypeUsage(boolean.class),
		info = @NestInformation("Turns on the compression of shared libraries.\n"
				+ "By default, the shared libraries (e.g. lib/*/libsomething.so) will be placed in the APK without compression. "
				+ "This is to allow page aligning them (by zipalign) and loading them using mmap.\n"
				+ "Set this to true to enable compressing the shared libraries."))

@NestParameterInformation(value = "Output",
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("Specifies the name of the output APK.\n"
				+ "The specified path must be forward relative and will be used to place it in the build directory.\n"
				+ "If not specified, default.apk is used."))
public class ApkCreateTaskFactory extends FrontendTaskFactory<Object> {
	private static final IncludeResourceMapping INCLUDE_RESOURCE_MAPPING_ASSETS = IncludeResourceMapping
			.wildcardIncludeFilter(WildcardPath.valueOf("assets/**"));
	private static final IncludeResourceMapping INCLUDE_RESOURCE_MAPPING_JNI_TO_LIB = new JniToLibIncludeResourceMapping();
	private static final SakerPath PATH_JNI = SakerPath.valueOf("jni");
	private static final SakerPath PATH_LIB = SakerPath.valueOf("lib");

	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "saker.apk.create";

	private static final SakerPath DEFAULT_BUILD_SUBDIRECTORY_PATH = SakerPath.valueOf(TASK_NAME);
	private static final SakerPath PATH_APK_ASSETS_DIRECTORY = SakerPath.valueOf("assets");

	public static final Set<String> KNOWN_ABIS = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { "arm64-v8a", "armeabi-v7a", "x86", "x86_64",
					//deprecated:
					"armeabi", "mips", "mips64", });

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Resources" }, required = true)
			public Collection<ApkResourcesTaskOption> resourcesOption;

			@SakerInput(value = { "Classes" })
			public ApkClassesTaskOption classesOption;

			@SakerInput(value = { "Assets" })
			public Collection<SakerPath> assetsOption;

			@SakerInput(value = { "Output" })
			public SakerPath outputOption;

			/**
			 * Maps library relative paths to abi to file locations.
			 * <p>
			 * E.g.
			 * 
			 * <pre>
			 * {
			 * 	libmain.so: {
			 * 		x86: build/.../x86/libmain.so,
			 * 		arm64-v84: build/.../arm64-v84/libmain.so
			 * 	}
			 * }
			 * </pre>
			 */
			@SakerInput(value = { "Libraries", "Libs", "Lib" })
			public Map<SakerPath, Map<String, ApkLibraryLocationTaskOption>> librariesOption;

			@SakerInput(value = { "CompressLibraries" })
			public boolean compressLibraries = false;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				final SakerPath outputpath;
				if (outputOption != null) {
					try {
						outputpath = AndroidFrontendUtils.requireFormwardRelativeWithFileName(outputOption,
								"APK output path");
					} catch (Exception e) {
						taskcontext.abortExecution(e);
						return null;
					}

				} else {
					outputpath = SakerPath.valueOf("default.apk");
				}

				librariesOption = ObjectUtils.cloneTreeMap(librariesOption, Functionals.identityFunction(),
						m -> ObjectUtils.cloneTreeMap(m, Functionals.identityFunction(),
								ApkLibraryLocationTaskOption::clone));

				SakerPath builddirpath = SakerPathFiles.requireBuildDirectoryPath(taskcontext)
						.resolve(DEFAULT_BUILD_SUBDIRECTORY_PATH);

				ZipCreationTaskBuilder taskbuilder = ZipCreationTaskBuilder.newBuilder();
				taskbuilder.setOutputPath(builddirpath.resolve(outputpath));
				if (!ObjectUtils.isNullOrEmpty(assetsOption)) {
					for (SakerPath dirpath : assetsOption) {
						SakerPath assetsabsdir = taskcontext.getTaskWorkingDirectoryPath().tryResolve(dirpath);

						NavigableMap<SakerPath, SakerFile> assetfiles = taskcontext.getTaskUtilities()
								.collectFilesReportAdditionDependency(null,
										new AssetFilesFileCollectionStrategy(assetsabsdir));
						taskcontext.getTaskUtilities().reportInputFileDependency(null, ObjectUtils
								.singleValueMap(assetfiles.navigableKeySet(), CommonTaskContentDescriptors.IS_FILE));
						for (SakerPath filepath : assetfiles.keySet()) {
							SakerPath archivepath = PATH_APK_ASSETS_DIRECTORY
									.resolve(assetsabsdir.relativize(filepath));
							taskbuilder.addResource(ExecutionFileLocation.create(filepath), archivepath);
						}
					}
				}
				for (ApkResourcesTaskOption resoption : resourcesOption) {
					if (resoption == null) {
						continue;
					}
					resoption.accept(new ApkResourcesTaskOption.Visitor() {
						@Override
						public void visit(Aapt2LinkWorkerTaskOutput linkoutput) {
							taskbuilder.addIncludeArchive(ExecutionFileLocation.create(linkoutput.getAPKPath()), null);
							Collection<Aapt2LinkInputLibrary> inputlibs = linkoutput.getInputLibraries();
							if (!ObjectUtils.isNullOrEmpty(inputlibs)) {
								for (Aapt2LinkInputLibrary ilib : inputlibs) {
									FileLocation aar = ilib.getAarFile();
									//include all assets
									taskbuilder.addIncludeArchive(aar, INCLUDE_RESOURCE_MAPPING_ASSETS);
									//include all libs
									IncludeResourceMapping libincluder = INCLUDE_RESOURCE_MAPPING_JNI_TO_LIB;
									if (!compressLibraries) {
										//explicitly make them uncompressed
										libincluder = IncludeResourceMapping.chain(libincluder,
												IncludeResourceMapping.storedCompressionMethod());
									}
									taskbuilder.addIncludeArchive(aar, libincluder);
								}
							}
						}

						@Override
						public void visit(SakerPath path) {
							taskbuilder.addIncludeArchive(ExecutionFileLocation
									.create(taskcontext.getTaskWorkingDirectoryPath().tryResolve(path)), null);
						}
					});
				}
				if (classesOption != null) {
					classesOption.applyTo(taskbuilder);
				}
				if (!ObjectUtils.isNullOrEmpty(librariesOption)) {
					for (Entry<SakerPath, Map<String, ApkLibraryLocationTaskOption>> entry : librariesOption
							.entrySet()) {
						SakerPath libpath = entry.getKey();
						Map<String, ApkLibraryLocationTaskOption> val = entry.getValue();
						if (libpath == null) {
							if (val == null) {
								continue;
							}
							throw new NullPointerException("Null library name.");
						}

						AndroidFrontendUtils.requireFormwardRelativeWithFileName(libpath, "Library name");
						if (ObjectUtils.isNullOrEmpty(val)) {
							//no libs
							continue;
						}
						for (Entry<String, ApkLibraryLocationTaskOption> archentry : val.entrySet()) {
							String abi = archentry.getKey();
							FileLocationTaskOption filelocation = archentry.getValue().getFileLocationTaskOption();
							if (abi == null) {
								if (filelocation == null) {
									//ignore instead of throwing
									continue;
								}
								throw new NullPointerException("Null ABI: " + libpath);
							}
							if (abi.isEmpty()) {
								throw new IllegalArgumentException("Empty string specified as ABI for: " + libpath);
							}
							FileLocation fl = TaskOptionUtils.toFileLocation(filelocation, taskcontext);
							if (fl == null) {
								throw new NullPointerException(
										"No file location specified for library ABI: " + libpath + " - " + abi);
							}
							if (!KNOWN_ABIS.contains(abi)) {
								//print a warning
								SakerLog.warning().taskScriptPosition(taskcontext)
										.println("Unrecognized Android ABI: " + abi);
							}
							SakerPath libentrypath = SakerPath.valueOf("lib").resolve(abi).append(libpath);
							if (!compressLibraries) {
								taskbuilder.addResource(fl, ZipResourceEntry.stored(libentrypath));
							} else {
								taskbuilder.addResource(fl, libentrypath);
							}
						}
					}
				}
				TaskIdentifier workertaskid = taskbuilder.buildTaskIdentifier();
				TaskFactory<?> workertask = taskbuilder.buildTaskFactory();

				taskcontext.startTask(workertaskid, workertask, null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

	private static final class JniToLibIncludeResourceMapping implements IncludeResourceMapping, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public JniToLibIncludeResourceMapping() {
		}

		@Override
		@SuppressWarnings("deprecation")
		public Set<SakerPath> mapResourcePath(SakerPath archivepath, boolean directory) {
			if (archivepath.getNameCount() < 2 || !archivepath.startsWith(PATH_JNI)) {
				return null;
			}
			return ImmutableUtils.singletonNavigableSet(PATH_LIB.append(archivepath.subPath(1)));
		}

		@Override
		public Collection<? extends ZipResourceEntry> mapResource(ZipResourceEntry resourceentry, boolean directory) {
			SakerPath archivepath = resourceentry.getEntryPath();
			if (archivepath.getNameCount() < 2 || !archivepath.startsWith(PATH_JNI)) {
				return null;
			}
			return ImmutableUtils
					.singletonNavigableSet(resourceentry.withEntryPath(PATH_LIB.append(archivepath.subPath(1))));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	public static class AssetFilesFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath directory;

		/**
		 * For {@link Externalizable}.
		 */
		public AssetFilesFileCollectionStrategy() {
		}

		AssetFilesFileCollectionStrategy(SakerPath directory) {
			this.directory = directory;
		}

		@Override
		public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
				TaskDirectoryContext directorycontext) {
			SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
			SakerDirectory dir = getActualDirectory(executiondirectorycontext, workingdir, this.directory);
			if (dir == null) {
				return Collections.emptyNavigableMap();
			}
			SakerPath dirbasepath = dir.getSakerPath();
			NavigableMap<SakerPath, SakerFile> result = dir.getFilesRecursiveByPath(dirbasepath,
					DirectoryVisitPredicate.subFiles());
			return result;
		}

		private static SakerDirectory getActualDirectory(ExecutionDirectoryContext executiondirectorycontext,
				SakerDirectory workingdir, SakerPath directory) {
			SakerDirectory dir;
			if (directory == null) {
				dir = workingdir;
			} else if (directory.isAbsolute()) {
				dir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, directory);
			} else {
				if (workingdir == null) {
					dir = null;
				} else {
					dir = SakerPathFiles.resolveDirectoryAtRelativePath(workingdir, directory);
				}
			}
			return dir;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(directory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			directory = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((directory == null) ? 0 : directory.hashCode());
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
			AssetFilesFileCollectionStrategy other = (AssetFilesFileCollectionStrategy) obj;
			if (directory == null) {
				if (other.directory != null)
					return false;
			} else if (!directory.equals(other.directory))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " [" + directory + "]";
		}

	}

}
