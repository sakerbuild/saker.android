//package saker.android.d8support;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.Map.Entry;
//import java.util.NavigableMap;
//import java.util.Set;
//import java.util.concurrent.ConcurrentSkipListMap;
//
//import com.android.tools.r8.ByteDataView;
//import com.android.tools.r8.D8;
//import com.android.tools.r8.D8Command;
//import com.android.tools.r8.DataDirectoryResource;
//import com.android.tools.r8.DataEntryResource;
//import com.android.tools.r8.DataResourceConsumer;
//import com.android.tools.r8.DexFilePerClassFileConsumer;
//import com.android.tools.r8.DiagnosticsHandler;
//import com.android.tools.r8.ProgramResource;
//import com.android.tools.r8.ProgramResourceProvider;
//import com.android.tools.r8.ResourceException;
//import com.android.tools.r8.origin.Origin;
//
//import saker.android.impl.d8.D8Executor;
//import saker.android.impl.d8.D8WorkerTaskFactory;
//import saker.android.impl.d8.D8WorkerTaskIdentifier;
//import saker.android.main.d8.D8TaskFactory;
//import saker.build.file.SakerDirectory;
//import saker.build.file.SakerFile;
//import saker.build.file.path.SakerPath;
//import saker.build.file.path.WildcardPath;
//import saker.build.file.provider.SakerPathFiles;
//import saker.build.runtime.environment.SakerEnvironment;
//import saker.build.task.TaskContext;
//import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
//import saker.sdk.support.api.SDKReference;
//
//final class IncrementalD8Executor implements D8Executor {
//
//	@Override
//	public Object run(TaskContext taskcontext, D8WorkerTaskFactory workertask,
//			NavigableMap<String, SDKReference> sdkreferences) throws Exception {
//		// TODO Auto-generated method stub
//
//		IncrementalD8State prevstate = taskcontext.getPreviousTaskOutput(IncrementalD8State.class,
//				IncrementalD8State.class);
//		int minapi = workertask.getMinApi();
//		boolean nodesugar = workertask.isNoDesugaring();
//		boolean releasemode = workertask.isRelease();
//		if (prevstate != null) {
//			if (prevstate.minApi != minapi || prevstate.noDesugaring != nodesugar || prevstate.release != releasemode) {
//				//clean
//				prevstate = null;
//			}
//		}
//
//		D8WorkerTaskIdentifier taskid = (D8WorkerTaskIdentifier) taskcontext.getTaskId();
//
//		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
//		SakerDirectory outputdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(builddir,
//				SakerPath.valueOf(D8TaskFactory.TASK_NAME + "/" + taskid.getCompilationIdentifier()));
//		SakerPath outputdirpath = outputdir.getSakerPath();
//
//		if (prevstate == null) {
//			outputdir.clear();
//		}
//
//		NavigableMap<SakerPath, SakerFile> collectedfiles = taskcontext.getTaskUtilities()
//				.collectFilesReportInputFileAndAdditionDependency(null, WildcardFileCollectionStrategy
//						.create(workertask.getClassDirectory(), WildcardPath.valueOf("**/*.class")));
//
//		IncrementalD8State nstate = new IncrementalD8State();
//		nstate.inputFileContents = new ConcurrentSkipListMap<>();
//		nstate.minApi = minapi;
//		nstate.noDesugaring = nodesugar;
//		nstate.release = releasemode;
//
//		D8ExecutionDiagnosticsHandler diaghandler = new D8ExecutionDiagnosticsHandler(taskcontext);
//
//		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();
//
//		D8Command.Builder builder = D8Command.builder(diaghandler);
//		D8ExecutorImpl.setD8BuilderCommonConfigurations(builder, workertask, environment, sdkreferences);
//
//		Collection<ProgramResource> programresources = new ArrayList<>();
//		for (Entry<SakerPath, SakerFile> entry : collectedfiles.entrySet()) {
//			SakerPath filepath = entry.getKey();
//			SakerFile file = entry.getValue();
//			programresources.add(new SakerClassFileProgramResource(filepath, file));
//		}
//		builder.addProgramResourceProvider(new ProgramResourceProvider() {
//			@Override
//			public Collection<ProgramResource> getProgramResources() throws ResourceException {
//				return programresources;
//			}
//		});
//
////		builder.setProgramConsumer(new DexFilePerClassFileConsumer() {
////		@Override
////		public void finished(DiagnosticsHandler handler) {
////		}
////
////		@Override
////		public void accept(String primaryClassDescriptor, byte[] data, Set<String> descriptors,
////				DiagnosticsHandler handler) {
////			if (primaryClassDescriptor.isEmpty() || primaryClassDescriptor.charAt(0) != 'L'
////					|| primaryClassDescriptor.charAt(primaryClassDescriptor.length() - 1) != ';') {
////				handler.error(
////						new StringDiagnostic("Unrecognized class descriptor format: " + primaryClassDescriptor));
////				return;
////			}
////			if (!descriptors.contains(primaryClassDescriptor)) {
////				handler.error(new StringDiagnostic(
////						"Invalid descriptors for " + primaryClassDescriptor + " with " + descriptors));
////				return;
////			}
////			String binarypath = primaryClassDescriptor.substring(1, primaryClassDescriptor.length() - 1);
////			SakerPath cfpath = SakerPath.valueOf(binarypath + ".class");
////			SakerPath dexoutpath = SakerPath.valueOf(binarypath + ".dex");
////			ByteArraySakerFile nfile = new ByteArraySakerFile(dexoutpath.getFileName(), data.clone());
////			taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(outputdir, dexoutpath.getParent())
////					.add(nfile);
////
////			outputfiles.put(outputdirpath.resolve(dexoutpath), nfile.getContentDescriptor());
////		}
////	});
//		builder.setIntermediate(true);
//		builder.setProgramConsumer(new DexFilePerClassFileConsumer() {
//			@Override
//			public void accept(String arg0, byte[] arg1, Set<String> arg2, DiagnosticsHandler arg3) {
//				// TODO Auto-generated method stub
//				System.out.println(
//						"IncrementalD8Executor.run(...).new DexFilePerClassFileConsumer() {...}.accept(legacy) "
//								+ arg0);
//			}
//
//			@Override
//			public void accept(String arg0, ByteDataView arg1, Set<String> arg2, DiagnosticsHandler arg3) {
//				// TODO Auto-generated method stub
//				System.out.println(
//						"IncrementalD8Executor.run(...).new DexFilePerClassFileConsumer() {...}.accept(new) " + arg0);
//			}
//
//			@Override
//			public void finished(DiagnosticsHandler handler) {
//			}
//
//			@Override
//			public DataResourceConsumer getDataResourceConsumer() {
//				System.out.println(
//						"IncrementalD8Executor.run(...).new DexFilePerClassFileConsumer() {...}.getDataResourceConsumer()");
//				return new DataResourceConsumer() {
//
//					@Override
//					public void finished(DiagnosticsHandler handler) {
//					}
//
//					@Override
//					public void accept(DataEntryResource resource, DiagnosticsHandler handler) {
//						// TODO Auto-generated method stub
//						Origin origin = resource.getOrigin();
//						System.out.println(origin + " -> " + resource.getName());
//					}
//
//					@Override
//					public void accept(DataDirectoryResource resource, DiagnosticsHandler handler) {
//						// don't care about output directories
//					}
//				};
//			}
//		});
//
//		taskcontext.setTaskOutput(IncrementalD8State.class, nstate);
//
//		D8.run(builder.build());
//		if (diaghandler.hadError()) {
//			throw new IOException("D8 failed with errors.");
//		}
//		outputdir.synchronize();
//
//		return null;
////		throw new UnsupportedOperationException();
//	}
//}