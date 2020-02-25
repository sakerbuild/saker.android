package saker.android.d8support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.utils.StringDiagnostic;

import saker.android.api.d8.D8TaskOutput;
import saker.android.impl.d8.AndroidJarDescriptorsCacheKey;
import saker.android.impl.d8.AndroidJarDescriptorsCacheKey.AndroidJarData;
import saker.android.impl.d8.D8Executor;
import saker.android.impl.d8.D8TaskOutputImpl;
import saker.android.impl.d8.D8TaskTags;
import saker.android.impl.d8.D8WorkerTaskFactory;
import saker.android.impl.d8.D8WorkerTaskIdentifier;
import saker.android.impl.d8.incremental.D8InputFileInformation;
import saker.android.impl.d8.incremental.IncrementalD8State;
import saker.android.impl.d8.incremental.OutputFileInformation;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.d8.D8TaskFactory;
import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.task.delta.DeltaType;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.TaskUtils;
import saker.build.task.utils.dependencies.RecursiveIgnoreCaseExtensionFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.sdk.support.api.SDKReference;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.std.api.file.location.FileLocation;
import saker.std.api.file.location.FileLocationVisitor;

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
		SakerDirectory intermediateoutputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(
				builddir,
				SakerPath.valueOf(D8TaskFactory.TASK_NAME + "/" + taskid.getCompilationIdentifier() + "/intermediate"));
		SakerDirectory classesoutputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
				SakerPath.valueOf(D8TaskFactory.TASK_NAME + "/" + taskid.getCompilationIdentifier() + "/classes"));
		SakerPath intermediateoutputdirpath = intermediateoutputdir.getSakerPath();
		SakerPath classesoutputdirpath = classesoutputdir.getSakerPath();

		if (prevstate == null) {
			intermediateoutputdir.clear();
			classesoutputdir.clear();
		}

		NavigableMap<SakerPath, InputSakerFileInfo> collectedinputfileinfos = new TreeMap<>();
		for (FileLocation in : workertask.getInputs()) {
			in.accept(new FileLocationVisitor() {
				@Override
				public void visit(ExecutionFileLocation loc) {
					SakerPath classdirectory = loc.getPath();
					FileCollectionStrategy strategy = RecursiveIgnoreCaseExtensionFileCollectionStrategy
							.create(classdirectory, ".class");
					NavigableMap<SakerPath, SakerFile> collectedfiles = taskcontext.getTaskUtilities()
							.collectFilesReportInputFileAndAdditionDependency(D8TaskTags.INPUT_FILE, strategy);

					for (Entry<SakerPath, SakerFile> entry : collectedfiles.entrySet()) {
						SakerPath infilepath = entry.getKey();
						String cdescriptor = D8ExecutorImpl
								.getDescriptorFromClassFileRelativePath(classdirectory.relativize(infilepath));
						collectedinputfileinfos.put(infilepath, new InputSakerFileInfo(entry.getValue(), cdescriptor));
					}
				}
			});
		}

		Collection<ProgramResource> intermediateprogramresources = new ArrayList<>();

		ConcurrentSkipListMap<SakerPath, ContentDescriptor> outputintermediatefiles = new ConcurrentSkipListMap<>();
		NavigableMap<SakerPath, InputSakerFileInfo> inputfiles;
		IncrementalD8State nstate;
		boolean anychange[] = { prevstate == null };
		if (prevstate != null) {
			nstate = new IncrementalD8State(prevstate);

			NavigableMap<SakerPath, SakerFile> changedinputfiles = TaskUtils
					.collectFilesForTag(taskcontext.getFileDeltas(DeltaType.INPUT_FILE_CHANGE), D8TaskTags.INPUT_FILE);
			NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
					taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), D8TaskTags.OUTPUT_INTERMEDIATE_DEX_FILE);

			inputfiles = new TreeMap<>();

			ObjectUtils.iterateSortedMapEntriesDual(prevstate.outputPathInformations, changedoutputfiles,
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
			//XXX make more efficient
			for (Entry<SakerPath, OutputFileInformation> entry : nstate.outputPathInformations.entrySet()) {
				outputintermediatefiles.put(entry.getKey(), entry.getValue().getContents());
			}
		} else {
			nstate = new IncrementalD8State();
			nstate.buildToolsSDK = sdkreferences.get(AndroidBuildToolsSDKReference.SDK_NAME);
			nstate.platformsSDK = sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME);
			nstate.inputPathInformations = new ConcurrentSkipListMap<>();
			nstate.inputDescriptorInformations = new ConcurrentSkipListMap<>();
			nstate.outputDescriptorInformations = new ConcurrentSkipListMap<>();
			nstate.outputPathInformations = new ConcurrentSkipListMap<>();
			nstate.outputClassIndexInformations = new ConcurrentSkipListMap<>();
			nstate.minApi = minapi;
			nstate.noDesugaring = nodesugar;
			nstate.release = releasemode;
			inputfiles = collectedinputfileinfos;
		}
		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		if (!inputfiles.isEmpty()) {
			anychange[0] = true;
			for (Entry<SakerPath, InputSakerFileInfo> entry : collectedinputfileinfos.entrySet()) {
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
			D8ExecutorImpl.setD8BuilderCommonConfigurations(intermediatebuilder, workertask, environment,
					sdkreferences);

			intermediatebuilder.setIntermediate(true);
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
					outputintermediatefiles.put(dexoutabspath, outputfiledescriptor);

					nstate.putOutput(new OutputFileInformation(outputfiledescriptor, dexoutabspath,
							primaryClassDescriptor, descriptors));
				}
			});
			D8.run(intermediatebuilder.build());
			if (intermediatediaghandler.hadError()) {
				throw new IOException("D8 failed with errors.");
			}
		}
		if (anychange[0]) {
			intermediateoutputdir.synchronize();
		}

		ConcurrentSkipListMap<SakerPath, ContentDescriptor> outputclassesfiles = new ConcurrentSkipListMap<>();
		d8_classes_runner:
		{
			if (!anychange[0] && prevstate != null) {
				NavigableMap<SakerPath, SakerFile> changedoutputfiles = TaskUtils.collectFilesForTag(
						taskcontext.getFileDeltas(DeltaType.OUTPUT_FILE_CHANGE), D8TaskTags.OUTPUT_CLASSES_DEX_FILE);
				if (changedoutputfiles.isEmpty()) {
					//nothing changed
					break d8_classes_runner;
				}
				//an output file changed. perform dexing
			}
			classesoutputdir.clear();

			D8ExecutionDiagnosticsHandler classesdiaghandler = new D8ExecutionDiagnosticsHandler(taskcontext);

			D8Command.Builder classesbuilder = D8Command.builder(classesdiaghandler);

			Collection<ProgramResource> classesprogramresources = new ArrayList<>();
			for (Entry<SakerPath, OutputFileInformation> entry : nstate.outputPathInformations.entrySet()) {
				SakerPath filepath = entry.getKey();
				//TODO be more efficient than resolving the file path
				SakerDexFileProgramResource programres = new SakerDexFileProgramResource(filepath,
						taskcontext.getTaskUtilities().resolveFileAtPath(filepath), entry.getValue().getDescriptors());
				classesprogramresources.add(programres);
			}
			classesbuilder.addProgramResourceProvider(new ProgramResourceProvider() {
				@Override
				public Collection<ProgramResource> getProgramResources() throws ResourceException {
					return classesprogramresources;
				}
			});
			D8ExecutorImpl.setD8BuilderCommonConfigurations(classesbuilder, workertask, environment, sdkreferences);
			classesbuilder.setIntermediate(false);
			classesbuilder.setProgramConsumer(new DexIndexedConsumer() {
				@Override
				public void finished(DiagnosticsHandler diagnostics) {
				}

				@Override
				public void accept(int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
					String idxfilename = D8ExecutorImpl.getDefaultDexFileName(fileIndex);
					ByteArraySakerFile nfile = new ByteArraySakerFile(idxfilename, data.clone());
					classesoutputdir.add(nfile);
					outputclassesfiles.put(classesoutputdirpath.resolve(idxfilename), nfile.getContentDescriptor());
				}
			});

			D8.run(classesbuilder.build());
			if (classesdiaghandler.hadError()) {
				throw new IOException("D8 failed with errors.");
			}
			classesoutputdir.synchronize();
		}

		taskcontext.getTaskUtilities().reportOutputFileDependency(D8TaskTags.OUTPUT_INTERMEDIATE_DEX_FILE,
				outputintermediatefiles);
		taskcontext.getTaskUtilities().reportOutputFileDependency(D8TaskTags.OUTPUT_CLASSES_DEX_FILE,
				outputclassesfiles);

		taskcontext.setTaskOutput(IncrementalD8State.class, nstate);

		NavigableSet<SakerPath> dexfiles = ImmutableUtils
				.makeImmutableNavigableSet(outputclassesfiles.navigableKeySet());
		return new D8TaskOutputImpl(dexfiles);
	}

	private static OutputFileInformation removeOutputFileForDescriptor(TaskContext taskcontext,
			IncrementalD8State nstate, String descriptor) {
		OutputFileInformation outputinfo = nstate.removeOutputForDescriptor(descriptor);
		deleteOutputFile(taskcontext, outputinfo);
		return outputinfo;
	}

	private static void deleteOutputFile(TaskContext taskcontext, OutputFileInformation outputinfo) {
		if (outputinfo != null) {
			SakerPath outputfilepath = outputinfo.getPath();
			SakerFile outputfile = taskcontext.getTaskUtilities().resolveFileAtPath(outputfilepath);
			if (outputfile != null) {
				//delete the corresponding output file
				outputfile.remove();
			}
		}
	}

	public static String getDefaultDexFileName(int fileIndex) {
		return fileIndex == 0 ? "classes" + ".dex" : ("classes" + (fileIndex + 1) + ".dex");
	}

	public static void setD8BuilderCommonConfigurations(D8Command.Builder builder, D8WorkerTaskFactory workertask,
			SakerEnvironment environment, NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		setD8BuilderAndroidJar(builder, environment, sdkreferences);
		setD8BuilderMinApi(builder, workertask);
		setD8BuilderEnableDesugaring(builder, workertask);
		setD8BuilderMode(builder, workertask);
		setD8BuilderMainDexClasses(builder, workertask);
	}

	public static void setD8BuilderMode(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setMode(workertask.isRelease() ? CompilationMode.RELEASE : CompilationMode.DEBUG);
	}

	public static void setD8BuilderEnableDesugaring(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		builder.setDisableDesugaring(workertask.isNoDesugaring());
	}

	public static void setD8BuilderMinApi(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		Integer minapi = workertask.getMinApi();
		if (minapi != null) {
			builder.setMinApiLevel(minapi);
		}
	}

	public static void setD8BuilderAndroidJar(D8Command.Builder builder, SakerEnvironment environment,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception {
		SDKReference platformsdk = sdkreferences.get(AndroidPlatformSDKReference.SDK_NAME);
		if (platformsdk == null) {
			return;
		}
		SakerPath androidjarpath = platformsdk.getPath(AndroidPlatformSDKReference.PATH_ANDROID_JAR);
		if (androidjarpath == null) {
			return;
		}
		AndroidJarData jardata = environment.getCachedData(new AndroidJarDescriptorsCacheKey(androidjarpath));
		builder.addLibraryResourceProvider(new JarFileClassFileResourceProvider(jardata));
	}

	public static void setD8BuilderMainDexClasses(D8Command.Builder builder, D8WorkerTaskFactory workertask) {
		NavigableSet<String> maindexclasses = workertask.getMainDexClasses();
		if (!ObjectUtils.isNullOrEmpty(maindexclasses)) {
			builder.addMainDexClasses(maindexclasses);
		}
	}

	public static String getDescriptorFromClassFileRelativePath(SakerPath cfpath) {
		StringBuilder sb = new StringBuilder();
		sb.append('L');
		String pathstr = cfpath.toString();
		//remove .class extension
		sb.append(pathstr.substring(0, pathstr.length() - 6));
		sb.append(';');
		return sb.toString();
	}

}
