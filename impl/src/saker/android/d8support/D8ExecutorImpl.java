package saker.android.d8support;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.StringDiagnostic;

import saker.android.api.d8.D8TaskOutput;
import saker.android.d8support.ArchiveFileClassFileResourceProvider.ArchiveEntryProgramResource;
import saker.android.impl.aar.AarEntryExtractWorkerTaskFactory;
import saker.android.impl.d8.ArchiveClassDescriptorsCacheKey;
import saker.android.impl.d8.ArchiveClassDescriptorsCacheKey.ArchiveClassDescriptorsData;
import saker.android.impl.d8.D8Executor;
import saker.android.impl.d8.D8InputFileCollectionStrategy;
import saker.android.impl.d8.D8LocalInputFileContentsExecutionProperty;
import saker.android.impl.d8.D8TaskOutputImpl;
import saker.android.impl.d8.D8TaskTags;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.D8WorkerTaskIdentifier;
import saker.android.impl.d8.incremental.D8InputArchiveInformation;
import saker.android.impl.d8.incremental.D8InputFileInformation;
import saker.android.impl.d8.incremental.D8OutputFileInformation;
import saker.android.impl.d8.incremental.IncrementalD8State;
import saker.android.impl.d8.option.D8InputOption;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.d8.D8TaskFactory;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.delta.DeltaType;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.java.compiler.api.classpath.ClassPathEntry;
import saker.java.compiler.api.classpath.ClassPathEntryInputFile;
import saker.java.compiler.api.classpath.ClassPathEntryInputFileVisitor;
import saker.java.compiler.api.classpath.ClassPathReference;
import saker.java.compiler.api.classpath.ClassPathVisitor;
import saker.java.compiler.api.classpath.CompilationClassPath;
import saker.java.compiler.api.classpath.FileClassPath;
import saker.java.compiler.api.classpath.JavaClassPath;
import saker.java.compiler.api.classpath.SDKClassPath;
import saker.java.compiler.api.compile.JavaCompilationWorkerTaskIdentifier;
import saker.java.compiler.api.compile.JavaCompilerWorkerTaskOutput;
import saker.java.compiler.api.compile.SakerJavaCompilerUtils;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;
import saker.std.api.file.location.LocalFileLocation;

public class D8ExecutorImpl implements D8Executor {
	public static D8Executor createD8Executor() {
		return new D8ExecutorImpl();
	}

	private static class InputSakerFileInfo {
		protected SakerFile file;
		protected String descriptor;

		public InputSakerFileInfo(SakerFile file, String descriptor) {
			this.file = file;
			this.descriptor = descriptor;
		}
	}

	@Override
	public D8TaskOutput run(TaskContext taskcontext, D8WorkerTaskFactory workertask,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		IncrementalD8State prevstate = taskcontext.getPreviousTaskOutput(IncrementalD8State.class,
				IncrementalD8State.class);
		Integer minapi = workertask.getMinApi();
		boolean nodesugar = workertask.isNoDesugaring();
		boolean releasemode = workertask.isRelease();
		if (prevstate != null) {
			if (!Objects.equals(prevstate.minApi, minapi) || prevstate.noDesugaring != nodesugar
					|| prevstate.release != releasemode
					|| !Objects.equals(prevstate.buildToolsSDK,
							sdkreferences.get(AndroidBuildToolsSDKReference.SDK_NAME))
					|| !Objects.equals(prevstate.platformsSDK,
							sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME))) {
				//clean
				prevstate = null;
			}
		}

		D8WorkerTaskIdentifier taskid = (D8WorkerTaskIdentifier) taskcontext.getTaskId();

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);

		SakerDirectory basebuilddir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				SakerPath.valueOf(D8TaskFactory.TASK_NAME + "/" + taskid.getCompilationIdentifier()));

		if (prevstate == null) {
			basebuilddir.clear();
		}
		SakerDirectory intermediateoutputdir = taskcontext.getTaskUtilities()
				.resolveDirectoryAtRelativePathCreate(basebuilddir, SakerPath.valueOf("intermediate"));
		SakerDirectory classesoutputdir = taskcontext.getTaskUtilities()
				.resolveDirectoryAtRelativePathCreate(basebuilddir, SakerPath.valueOf("classes"));
		SakerPath intermediateoutputdirpath = intermediateoutputdir.getSakerPath();
		SakerPath classesoutputdirpath = classesoutputdir.getSakerPath();

		D8InputCollectingVisitor inputvisitor = new D8InputCollectingVisitor(taskcontext, sdkreferences);

		for (D8InputOption in : workertask.getInputs()) {
			in.accept(inputvisitor);
		}
		NavigableMap<SakerPath, InputSakerFileInfo> collectedinputfileinfos = inputvisitor.collectedInputFileInfos;

		ConcurrentSkipListMap<SakerPath, ContentDescriptor> outputintermediatedependencies = new ConcurrentSkipListMap<>();
		ConcurrentSkipListMap<SakerPath, ContentDescriptor> outputintermediatearchivedependencies = new ConcurrentSkipListMap<>();
		NavigableMap<SakerPath, InputSakerFileInfo> inputfiles;
		Set<D8InputArchiveInformation> inputarchives;
		IncrementalD8State nstate;
		boolean anychange[] = { prevstate == null };

		NavigableMap<String, D8InputFileInformation> previnputforclasspath;

		if (prevstate != null) {
			nstate = new IncrementalD8State(prevstate);

			NavigableMap<SakerPath, SakerFile> changedinputfiles = TaskUtils
					.collectFilesForTag(taskcontext.getFileDeltas(DeltaType.INPUT_FILE_CHANGE), D8TaskTags.INPUT_FILE);
			NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), D8TaskTags.OUTPUT_INTERMEDIATE_DEX_FILE);
			NavigableMap<SakerPath, SakerFile> changedarchiveoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE),
					D8TaskTags.OUTPUT_INTERMEDIATE_ARCHIVE_DEX_FILE);

			inputfiles = new TreeMap<>();
			inputarchives = new HashSet<>();

			ObjectUtils.iterateSortedMapEntries(prevstate.outputPathInformations, changedoutputfiles,
					(filepath, prevoutput, file) -> {
						if (file == null) {
							//the output file was removed
						} else {
							//an output file was modified
							deleteOutputFile(taskcontext, prevoutput);
						}
						nstate.removeOutputForPath(filepath);
						D8InputFileInformation inputinfo = nstate.inputDescriptorInformations
								.get(prevoutput.getDescriptor());
						if (inputinfo != null) {
							//add the input to dex	
							InputSakerFileInfo infile = collectedinputfileinfos.get(inputinfo.getPath());
							if (infile != null) {
								inputfiles.put(inputinfo.getPath(), infile);
							}
						}
					});

			ObjectUtils.iterateSortedMapEntries(prevstate.inputPathInformations, collectedinputfileinfos,
					(filepath, previnput, infile) -> {
						if (infile != null) {
							if (previnput == null) {
								//an input file was added
							} else if (changedinputfiles.containsKey(filepath)) {
								//an input file was changed
								nstate.removeInputForPath(filepath);
								removeOutputFileForDescriptor(taskcontext, nstate, previnput.getDescriptor());
							} else {
								//unchanged input
								return;
							}
						} else {
							//an input file was removed
							nstate.removeInputForPath(filepath);
							removeOutputFileForDescriptor(taskcontext, nstate, previnput.getDescriptor());
							anychange[0] = true;
							return;
						}
						//add the file as input

						inputfiles.put(filepath, infile);
					});
			//remove any previously generated dex files for archives that are no longer present, or different
			for (Entry<FileLocation, D8InputArchiveInformation> entry : prevstate.archiveInformations.entrySet()) {
				FileLocation archivefileloc = entry.getKey();
				ContentDescriptor currentinputcd = inputvisitor.inputArchives.get(archivefileloc);

				//if the input archive contents changed
				//    or
				//any of the output dex files changed for the archive
				if (!entry.getValue().getContents().equals(currentinputcd) || SakerPathFiles.hasPathOrSubPath(
						changedarchiveoutputfiles.navigableKeySet(), entry.getValue().getOutputDirectoryPath())) {
					//remove
					D8InputArchiveInformation prevoutputarchiveinfo = nstate.archiveInformations.remove(archivefileloc);
					if (prevoutputarchiveinfo != null) {
						//execute the removal
						SakerFile outdir = taskcontext.getTaskUtilities()
								.resolveAtPath(entry.getValue().getOutputDirectoryPath());
						if (outdir != null) {
							outdir.remove();
						}
					}
				} else {
					//the archive is up to date. add the dependencies to be reported
					SakerPath archiveoutdirpath = entry.getValue().getOutputDirectoryPath();
					for (Entry<String, ContentDescriptor> dexentry : entry.getValue().getOutputDexFiles().entrySet()) {
						outputintermediatearchivedependencies.put(archiveoutdirpath.resolve(dexentry.getKey()),
								dexentry.getValue());
					}
				}
			}
			for (Entry<FileLocation, ContentDescriptor> entry : inputvisitor.inputArchives.entrySet()) {
				if (!nstate.archiveInformations.containsKey(entry.getKey())) {
					inputarchives.add(new D8InputArchiveInformation(entry.getKey(), entry.getValue()));
				}
			}
			//XXX make more efficient
			for (Entry<SakerPath, D8OutputFileInformation> entry : nstate.outputPathInformations.entrySet()) {
				outputintermediatedependencies.put(entry.getKey(), entry.getValue().getContents());
			}

			previnputforclasspath = ImmutableUtils.makeImmutableNavigableMap(nstate.inputDescriptorInformations);
		} else {
			nstate = new IncrementalD8State();
			nstate.buildToolsSDK = sdkreferences.get(AndroidBuildToolsSDKReference.SDK_NAME);
			nstate.platformsSDK = sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME);
			nstate.inputPathInformations = new ConcurrentSkipListMap<>();
			nstate.inputDescriptorInformations = new ConcurrentSkipListMap<>();
			nstate.outputDescriptorInformations = new ConcurrentSkipListMap<>();
			nstate.outputPathInformations = new ConcurrentSkipListMap<>();
			nstate.outputClassIndexInformations = new ConcurrentSkipListMap<>();
			nstate.archiveInformations = new ConcurrentHashMap<>();
			nstate.minApi = minapi;
			nstate.noDesugaring = nodesugar;
			nstate.release = releasemode;

			inputfiles = collectedinputfileinfos;
			previnputforclasspath = Collections.emptyNavigableMap();
			inputarchives = new HashSet<>();
			for (Entry<FileLocation, ContentDescriptor> entry : inputvisitor.inputArchives.entrySet()) {
				inputarchives.add(new D8InputArchiveInformation(entry.getKey(), entry.getValue()));
			}
		}
		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		try (ResourceCloser closer = new ResourceCloser()) {

			//TODO parallelize the archives and class files calls
			Map<FileLocation, ArchiveClassDescriptorsData> openedarchives = new ConcurrentHashMap<>();
			Map<FileLocation, Object> openarchivelocks = new ConcurrentHashMap<>();

			if (!inputarchives.isEmpty()) {
				anychange[0] = true;

				SakerDirectory archiveintermediateoutputdir = taskcontext.getTaskUtilities()
						.resolveDirectoryAtRelativePathCreate(basebuilddir, SakerPath.valueOf("archive_intermediate"));

				for (D8InputArchiveInformation archiveinfo : inputarchives) {
					Collection<ProgramResource> intermediateprogramresources = new ArrayList<>();
					D8ExecutionDiagnosticsHandler intermediatediaghandler = new D8ExecutionDiagnosticsHandler(
							taskcontext);

					FileLocation archivefilelocation = archiveinfo.getFileLocation();
					ArchiveClassDescriptorsData openedarchive = openInputArchive(taskcontext, closer,
							archivefilelocation, openedarchives, openarchivelocks);
					ZipFile zipfile = openedarchive.getZipFile();
					Origin archiveorigin = new PathOrigin(Paths.get(zipfile.getName()));
					for (Entry<String, ZipEntry> entry : openedarchive.getDescriptorEntries().entrySet()) {
						intermediateprogramresources.add(new ArchiveEntryProgramResource(entry.getKey(), zipfile,
								archiveorigin, entry.getValue()));
					}

					D8Command.Builder intermediatebuilder = D8Command.builder(intermediatediaghandler);
					intermediatebuilder.addProgramResourceProvider(new ProgramResourceProvider() {
						@Override
						public Collection<ProgramResource> getProgramResources() throws ResourceException {
							return intermediateprogramresources;
						}
					});
					setD8BuilderCommonConfigurations(intermediatebuilder, workertask, environment, sdkreferences);
					intermediatebuilder.setIntermediate(true);
					for (FileLocation archivecplocation : inputvisitor.inputArchives.keySet()) {
						if (archivefilelocation.equals(archivecplocation)) {
							continue;
						}
						ArchiveClassDescriptorsData archivedata = openInputArchive(taskcontext, closer,
								archivecplocation, openedarchives, openarchivelocks);
						intermediatebuilder
								.addClasspathResourceProvider(new ArchiveFileClassFileResourceProvider(archivedata));
					}

					String archiveoutputdirname = StringUtils.toHexString(FileUtils.hashString(
							AarEntryExtractWorkerTaskFactory.getFileLocationPath(archivefilelocation).toString()));
					SakerDirectory archiveoutdir = archiveintermediateoutputdir
							.getDirectoryCreate(archiveoutputdirname);

					NavigableSet<String> archivedescriptors = new ConcurrentSkipListSet<>();

					NavigableMap<String, ContentDescriptor> outputfiles = new ConcurrentSkipListMap<>();
					intermediatebuilder.setProgramConsumer(new DexIndexedConsumer() {
						@Override
						public void finished(DiagnosticsHandler diagnostics) {
						}

						@Override
						public void accept(int fileIndex, byte[] data, Set<String> descriptors,
								DiagnosticsHandler handler) {
							archivedescriptors.addAll(descriptors);
							String idxfilename = D8ExecutorImpl.getDefaultDexFileName(fileIndex);
							ByteArraySakerFile nfile = new ByteArraySakerFile(idxfilename, data.clone());
							archiveoutdir.add(nfile);
							ContentDescriptor dexoutcd = nfile.getContentDescriptor();
							outputfiles.put(idxfilename, dexoutcd);
							outputintermediatearchivedependencies.put(nfile.getSakerPath(), dexoutcd);
						}
					});

					D8.run(intermediatebuilder.build());
					if (intermediatediaghandler.hadError()) {
						throw new IOException("D8 failed with errors.");
					}
					archiveinfo.setDescriptors(archivedescriptors);
					archiveinfo.setOutputDexFiles(archiveoutdir.getSakerPath(), outputfiles);
					nstate.archiveInformations.put(archivefilelocation, archiveinfo);
				}
			}

			if (!inputfiles.isEmpty()) {
				anychange[0] = true;
				Collection<ProgramResource> intermediateprogramresources = new ArrayList<>();
				for (Entry<SakerPath, InputSakerFileInfo> entry : inputfiles.entrySet()) {
					SakerPath filepath = entry.getKey();
					InputSakerFileInfo inputinfo = entry.getValue();
					SakerFile file = inputinfo.file;

					String cdescriptor = inputinfo.descriptor;

					nstate.putInput(new D8InputFileInformation(filepath, cdescriptor));
					intermediateprogramresources.add(new SakerClassFileProgramResource(filepath, file,
							ImmutableUtils.singletonNavigableSet(cdescriptor)));
				}

				D8ExecutionDiagnosticsHandler intermediatediaghandler = new D8ExecutionDiagnosticsHandler(taskcontext);

				D8Command.Builder intermediatebuilder = D8Command.builder(intermediatediaghandler);
				intermediatebuilder.addProgramResourceProvider(new ProgramResourceProvider() {
					@Override
					public Collection<ProgramResource> getProgramResources() throws ResourceException {
						return intermediateprogramresources;
					}
				});
				setD8BuilderCommonConfigurations(intermediatebuilder, workertask, environment, sdkreferences);

				intermediatebuilder.setIntermediate(true);
				if (prevstate != null) {
					NavigableMap<SakerPath, ? extends SakerFile> previnputfiles = taskcontext
							.getPreviousInputDependencies(D8TaskTags.INPUT_FILE);
					intermediatebuilder.addClasspathResourceProvider(
							new PreviousClasspathClassFileResourceProvider(previnputforclasspath, previnputfiles));
				}
				for (FileLocation archivecplocation : inputvisitor.inputArchives.keySet()) {
					ArchiveClassDescriptorsData archivedata = openInputArchive(taskcontext, closer, archivecplocation,
							openedarchives, openarchivelocks);
					intermediatebuilder
							.addClasspathResourceProvider(new ArchiveFileClassFileResourceProvider(archivedata));
				}

				intermediatebuilder.setProgramConsumer(new DexFilePerClassFileConsumer() {
					@Override
					public void finished(DiagnosticsHandler handler) {
					}

					@Override
					public void accept(String primaryClassDescriptor, byte[] data, Set<String> descriptors,
							DiagnosticsHandler handler) {
						if (primaryClassDescriptor.isEmpty() || primaryClassDescriptor.charAt(0) != 'L'
								|| primaryClassDescriptor.charAt(primaryClassDescriptor.length() - 1) != ';') {
							handler.error(new StringDiagnostic(
									"Unrecognized class descriptor format: " + primaryClassDescriptor));
							return;
						}
						String binarypath = primaryClassDescriptor.substring(1, primaryClassDescriptor.length() - 1);
						SakerPath dexoutpath = SakerPath.valueOf(binarypath + ".dex");
						ByteArraySakerFile nfile = new ByteArraySakerFile(dexoutpath.getFileName(), data.clone());
						taskcontext.getTaskUtilities()
								.resolveDirectoryAtRelativePathCreate(intermediateoutputdir, dexoutpath.getParent())
								.add(nfile);

						ContentDescriptor outputfiledescriptor = nfile.getContentDescriptor();
						SakerPath dexoutabspath = intermediateoutputdirpath.resolve(dexoutpath);
						outputintermediatedependencies.put(dexoutabspath, outputfiledescriptor);

						nstate.putOutput(new D8OutputFileInformation(outputfiledescriptor, dexoutabspath,
								primaryClassDescriptor, descriptors));
					}
				});
				D8.run(intermediatebuilder.build());
				if (intermediatediaghandler.hadError()) {
					throw new IOException("D8 failed with errors.");
				}
			}

			ConcurrentSkipListMap<SakerPath, ContentDescriptor> outputclassesfiledependencies = new ConcurrentSkipListMap<>();

			if (!isUnchangedForClasses(taskcontext, prevstate, anychange[0])) {
				//something changed, perform dexing
				classesoutputdir.clear();

				D8ExecutionDiagnosticsHandler classesdiaghandler = new D8ExecutionDiagnosticsHandler(taskcontext);

				D8Command.Builder classesbuilder = D8Command.builder(classesdiaghandler);

				Collection<ProgramResource> classesprogramresources = new ArrayList<>();
				for (Entry<SakerPath, D8OutputFileInformation> entry : nstate.outputPathInformations.entrySet()) {
					SakerPath filepath = entry.getKey();
					//TODO be more efficient than resolving the file path
					SakerDexFileProgramResource programres = new SakerDexFileProgramResource(filepath,
							taskcontext.getTaskUtilities().resolveFileAtPath(filepath),
							entry.getValue().getDescriptors());
					classesprogramresources.add(programres);
				}
				for (D8InputArchiveInformation archiveinfo : nstate.archiveInformations.values()) {
					SakerPath archiveoutdir = archiveinfo.getOutputDirectoryPath();
					for (String outdexfname : archiveinfo.getOutputDexFiles().keySet()) {
						SakerPath filepath = archiveoutdir.resolve(outdexfname);

						//TODO be more efficient than resolving the file path
						SakerDexFileProgramResource programres = new SakerDexFileProgramResource(filepath,
								taskcontext.getTaskUtilities().resolveFileAtPath(filepath),
								archiveinfo.getDescriptors());
						classesprogramresources.add(programres);
					}
				}
				classesbuilder.addProgramResourceProvider(new ProgramResourceProvider() {
					@Override
					public Collection<ProgramResource> getProgramResources() throws ResourceException {
						return classesprogramresources;
					}
				});
				setD8BuilderCommonConfigurations(classesbuilder, workertask, environment, sdkreferences);
				setD8BuilderMainDexClasses(classesbuilder, workertask);
				classesbuilder.setIntermediate(false);
				classesbuilder.setProgramConsumer(new DexIndexedConsumer() {
					@Override
					public void finished(DiagnosticsHandler diagnostics) {
					}

					@Override
					public void accept(int fileIndex, byte[] data, Set<String> descriptors,
							DiagnosticsHandler handler) {
						String idxfilename = D8ExecutorImpl.getDefaultDexFileName(fileIndex);
						ByteArraySakerFile nfile = new ByteArraySakerFile(idxfilename, data.clone());
						classesoutputdir.add(nfile);
						outputclassesfiledependencies.put(classesoutputdirpath.resolve(idxfilename),
								nfile.getContentDescriptor());
					}
				});

				D8.run(classesbuilder.build());
				if (classesdiaghandler.hadError()) {
					throw new IOException("D8 failed with errors.");
				}
			}

			basebuilddir.synchronize();

			taskcontext.getTaskUtilities().reportOutputFileDependency(D8TaskTags.OUTPUT_INTERMEDIATE_DEX_FILE,
					outputintermediatedependencies);
			taskcontext.getTaskUtilities().reportOutputFileDependency(D8TaskTags.OUTPUT_INTERMEDIATE_ARCHIVE_DEX_FILE,
					outputintermediatearchivedependencies);
			taskcontext.getTaskUtilities().reportOutputFileDependency(D8TaskTags.OUTPUT_CLASSES_DEX_FILE,
					outputclassesfiledependencies);

			taskcontext.setTaskOutput(IncrementalD8State.class, nstate);

			NavigableSet<SakerPath> dexfiles = ImmutableUtils
					.makeImmutableNavigableSet(outputclassesfiledependencies.navigableKeySet());
			return new D8TaskOutputImpl(dexfiles);
		}
	}

	private static boolean isUnchangedForClasses(TaskContext taskcontext, IncrementalD8State prevstate,
			boolean anychange) {
		return !anychange && prevstate != null
				&& TaskUtils.collectFilesForTag(taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE),
						D8TaskTags.OUTPUT_CLASSES_DEX_FILE).isEmpty();
	}

	private static ArchiveClassDescriptorsData openInputArchive(TaskContext taskcontext, ResourceCloser closer,
			FileLocation fl, Map<FileLocation, ArchiveClassDescriptorsData> openedarchives,
			Map<FileLocation, Object> openarchivelocks) {
		ArchiveClassDescriptorsData present = openedarchives.get(fl);
		if (present != null) {
			return present;
		}
		synchronized (openarchivelocks.computeIfAbsent(fl, Functionals.objectComputer())) {
			present = openedarchives.get(fl);
			if (present != null) {
				return present;
			}
			ArchiveClassDescriptorsData[] result = { null };
			fl.accept(new FileLocationVisitor() {
				@Override
				public void visit(LocalFileLocation loc) {
					try {
						ZipFile zip = new ZipFile(LocalFileProvider.toRealPath(loc.getLocalPath()).toFile());
						closer.add(zip);
						result[0] = ArchiveClassDescriptorsData.create(zip);
					} catch (IOException e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				}

				@Override
				public void visit(ExecutionFileLocation loc) {
					try {
						ZipFile zip = new ZipFile(
								taskcontext.getTaskUtilities().mirrorFileAtPath(loc.getPath()).toFile());
						closer.add(zip);
						result[0] = ArchiveClassDescriptorsData.create(zip);
					} catch (IOException e) {
						throw ObjectUtils.sneakyThrow(e);
					}
				}
			});
			openedarchives.put(fl, result[0]);
			return result[0];
		}
	}

	private static D8OutputFileInformation removeOutputFileForDescriptor(TaskContext taskcontext,
			IncrementalD8State nstate, String descriptor) {
		D8OutputFileInformation outputinfo = nstate.removeOutputForDescriptor(descriptor);
		deleteOutputFile(taskcontext, outputinfo);
		return outputinfo;
	}

	private static void deleteOutputFile(TaskContext taskcontext, D8OutputFileInformation outputinfo) {
		if (outputinfo != null) {
			SakerPath outputfilepath = outputinfo.getPath();
			SakerFile outputfile = taskcontext.getTaskUtilities().resolveFileAtPath(outputfilepath);
			if (outputfile != null) {
				//delete the corresponding output file
				outputfile.remove();
			}
		}
	}

	private static String getDefaultDexFileName(int fileIndex) {
		return fileIndex == 0 ? "classes" + ".dex" : ("classes" + (fileIndex + 1) + ".dex");
	}

	private static void setD8BuilderCommonConfigurations(D8Command.Builder builder, D8WorkerTaskFactory workertask,
			SakerEnvironment environment, NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		setD8BuilderAndroidJar(builder, environment, sdkreferences);
		setD8BuilderMinApi(builder, workertask);
		setD8BuilderDisableDesugaring(builder, workertask);
		setD8BuilderMode(builder, workertask);
	}

	private static void setD8BuilderMode(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setMode(workertask.isRelease() ? CompilationMode.RELEASE : CompilationMode.DEBUG);
	}

	private static void setD8BuilderDisableDesugaring(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setDisableDesugaring(workertask.isNoDesugaring());
	}

	private static void setD8BuilderMinApi(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		Integer minapi = workertask.getMinApi();
		if (minapi != null) {
			builder.setMinApiLevel(minapi);
		}
	}

	private static void setD8BuilderAndroidJar(D8Command.Builder builder, SakerEnvironment environment,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		SDKReference platformsdk = sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME);
		if (platformsdk == null) {
			return;
		}
		SakerPath androidjarpath = platformsdk.getPath(AndroidPlatformSDKReference.PATH_ANDROID_JAR);
		if (androidjarpath == null) {
			return;
		}
		ArchiveClassDescriptorsData jardata = environment
				.getCachedData(new ArchiveClassDescriptorsCacheKey(androidjarpath));
		builder.addLibraryResourceProvider(new ArchiveFileClassFileResourceProvider(jardata));
	}

	private static void setD8BuilderMainDexClasses(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		NavigableSet<String> maindexclasses = workertask.getMainDexClasses();
		if (!ObjectUtils.isNullOrEmpty(maindexclasses)) {
			builder.addMainDexClasses(maindexclasses);
		}
	}

	private static String getDescriptorFromClassFileRelativePath(SakerPath cfpath) {
		StringBuilder sb = new StringBuilder();
		sb.append('L');
		String pathstr = cfpath.toString();
		//remove .class extension
		sb.append(pathstr.substring(0, pathstr.length() - 6));
		sb.append(';');
		return sb.toString();
	}

	private static final class PreviousClasspathClassFileResourceProvider implements ClassFileResourceProvider {
		private final NavigableMap<String, D8InputFileInformation> previnputforclasspath;
		private final NavigableMap<SakerPath, ? extends SakerFile> previnputfiles;

		private PreviousClasspathClassFileResourceProvider(
				NavigableMap<String, D8InputFileInformation> previnputforclasspath,
				NavigableMap<SakerPath, ? extends SakerFile> previnputfiles) {
			this.previnputforclasspath = previnputforclasspath;
			this.previnputfiles = previnputfiles;
		}

		@Override
		public ProgramResource getProgramResource(String descriptor) {
			D8InputFileInformation ininfo = previnputforclasspath.get(descriptor);
			if (ininfo == null) {
				return null;
			}
			SakerPath fpath = ininfo.getPath();
			SakerFile pfile = previnputfiles.get(fpath);
			if (pfile == null) {
				return null;
			}
			return new SakerClassFileProgramResource(fpath, pfile, ImmutableUtils.singletonNavigableSet(descriptor));
		}

		@Override
		public Set<String> getClassDescriptors() {
			return previnputforclasspath.keySet();
		}
	}

	private static final class D8InputCollectingVisitor implements D8InputOption.Visitor, ClassPathVisitor {
		private final TaskContext taskContext;
		private final NavigableMap<String, SDKReference> sdkReferences;

		protected final NavigableMap<SakerPath, InputSakerFileInfo> collectedInputFileInfos = new TreeMap<>();
		protected final Map<FileLocation, ContentDescriptor> inputArchives = new HashMap<>();

		private transient Set<FileLocation> handledFileLocations = new HashSet<>();
		private transient Set<ClassPathEntry> handledClassPathEntries = new HashSet<>();
		private transient Set<JavaCompilationWorkerTaskIdentifier> handledWorkerTaskIds = new HashSet<>();

		private D8InputCollectingVisitor(TaskContext taskcontext, NavigableMap<String, SDKReference> sdkreferences) {
			this.taskContext = taskcontext;
			this.sdkReferences = sdkreferences;
		}

		@Override
		public void visit(FileLocation file) {
			if (!handledFileLocations.add(file)) {
				return;
			}
			file.accept(new FileLocationVisitor() {
				@Override
				public void visit(ExecutionFileLocation loc) {
					SakerPath inputpath = loc.getPath();
					visitExecutionFile(inputpath);
				}

				@Override
				public void visit(LocalFileLocation loc) {
					visitLocalFile(loc.getLocalPath());
				}

				private void visitLocalFile(SakerPath inputpath) {
					NavigableMap<SakerPath, ContentDescriptor> localcontents = taskContext.getTaskUtilities()
							.getReportExecutionDependency(
									new D8LocalInputFileContentsExecutionProperty(inputpath, taskContext.getTaskId()));

					ContentDescriptor cdforinputpath = localcontents.get(inputpath);
					if (cdforinputpath != null) {
						inputArchives.put(LocalFileLocation.create(inputpath), cdforinputpath);
					} else {
						// TODO support local class files
						throw new UnsupportedOperationException("local class files not yet supported: " + inputpath);
					}
				}

				private void visitExecutionFile(SakerPath inputpath) {
					FileCollectionStrategy strategy = new D8InputFileCollectionStrategy(inputpath);
					NavigableMap<SakerPath, SakerFile> collectedfiles = taskContext.getTaskUtilities()
							.collectFilesReportInputFileAndAdditionDependency(D8TaskTags.INPUT_FILE, strategy);

					SakerFile fileforinputpath = collectedfiles.get(inputpath);
					if (fileforinputpath != null) {
						//the path denotes a file, not a directory
						inputArchives.put(ExecutionFileLocation.create(inputpath),
								fileforinputpath.getContentDescriptor());
					} else {
						for (Entry<SakerPath, SakerFile> entry : collectedfiles.entrySet()) {
							SakerPath infilepath = entry.getKey();
							String cdescriptor = D8ExecutorImpl
									.getDescriptorFromClassFileRelativePath(inputpath.relativize(infilepath));
							collectedInputFileInfos.put(infilepath,
									new InputSakerFileInfo(entry.getValue(), cdescriptor));
						}
					}
				}
			});
		}

		@Override
		public void visit(JavaClassPath classpath) {
			if (classpath.isEmpty()) {
				return;
			}
			classpath.accept(this);
		}

		@Override
		public void visit(JavaCompilationWorkerTaskIdentifier javactaskid) {
			if (!handledWorkerTaskIds.add(javactaskid)) {
				//don't get the task result to not install another dependency
				return;
			}
			TaskDependencyFuture<?> depresult = taskContext.getTaskDependencyFuture(javactaskid);
			JavaCompilerWorkerTaskOutput output = (JavaCompilerWorkerTaskOutput) depresult.getFinished();
			ExecutionFileLocation filelocation = ExecutionFileLocation.create(output.getClassDirectory());
			visit(filelocation);
			JavaClassPath cp = output.getClassPath();
			depresult.setTaskOutputChangeDetector(
					SakerJavaCompilerUtils.getCompilerOutputClassPathTaskOutputChangeDetector(cp));
			if (cp != null) {
				visit(cp);
			}
		}

		@Override
		public void visit(ClassPathReference classpath) {
			for (ClassPathEntry entry : classpath.getEntries()) {
				visit(entry);
			}
		}

		@Override
		public void visit(CompilationClassPath classpath) {
			visit(classpath.getCompilationWorkerTaskIdentifier());
		}

		@Override
		public void visit(FileClassPath classpath) {
			visit(classpath.getFileLocation());
		}

		@Override
		public void visit(SDKClassPath classpath) {
			SakerPath path = SDKSupportUtils.getSDKPathReferencePath(classpath.getSDKPathReference(), sdkReferences);
			visit(LocalFileLocation.create(path));
		}

		private void visit(ClassPathEntry entry) {
			if (!handledClassPathEntries.add(entry)) {
				//already seen this
				return;
			}
			ClassPathEntryInputFile infile = entry.getInputFile();
			infile.accept(new ClassPathEntryInputFileVisitor() {

				@Override
				public void visit(FileClassPath classpath) {
					D8InputCollectingVisitor.this.visit(classpath);
				}

				@Override
				public void visit(SDKClassPath classpath) {
					D8InputCollectingVisitor.this.visit(classpath);
				}
			});
			Collection<? extends ClassPathReference> additional = entry.getAdditionalClassPathReferences();
			if (!ObjectUtils.isNullOrEmpty(additional)) {
				additional.forEach(this::visit);
			}
		}

	}
}
