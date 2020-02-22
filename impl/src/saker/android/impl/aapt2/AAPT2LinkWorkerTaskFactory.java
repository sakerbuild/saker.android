package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.android.main.aapt2.AAPT2LinkTaskFactory;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskExecutionUtilities.MirroredFileContents;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.trace.BuildTrace;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;
import saker.sdk.support.api.SDKSupportUtils;
import saker.sdk.support.api.exc.SDKNotFoundException;
import saker.sdk.support.api.exc.SDKPathNotFoundException;

public class AAPT2LinkWorkerTaskFactory
		implements TaskFactory<AAPT2LinkTaskOutput>, Task<AAPT2LinkTaskOutput>, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<SakerPath> inputFiles;

	private NavigableMap<String, ? extends SDKDescription> sdkDescriptions;

	private transient TaskExecutionEnvironmentSelector remoteDispatchableEnvironmentSelector;

	private SakerPath manifest;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2LinkWorkerTaskFactory() {
	}

	public void setSDKDescriptions(NavigableMap<String, ? extends SDKDescription> sdkdescriptions) {
		ObjectUtils.requireComparator(sdkdescriptions, SDKSupportUtils.getSDKNameComparator());
		this.sdkDescriptions = sdkdescriptions;
		if (sdkdescriptions.get(AndroidBuildToolsSDKReference.SDK_NAME) == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not specified.");
		}
		remoteDispatchableEnvironmentSelector = SDKSupportUtils
				.getSDKBasedClusterExecutionEnvironmentSelector(sdkdescriptions.values());
	}

	public void setInputFiles(NavigableSet<SakerPath> inputFiles) {
		this.inputFiles = inputFiles;
	}

	public void setManifest(SakerPath manifest) {
		this.manifest = manifest;
	}

	@Override
	public Set<String> getCapabilities() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return ImmutableUtils.singletonNavigableSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}
		return TaskFactory.super.getCapabilities();
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		if (remoteDispatchableEnvironmentSelector != null) {
			return remoteDispatchableEnvironmentSelector;
		}
		return TaskFactory.super.getExecutionEnvironmentSelector();
	}

	@Override
	public int getRequestedComputationTokenCount() {
		return 1;
	}

	@Override
	public Task<? extends AAPT2LinkTaskOutput> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public AAPT2LinkTaskOutput run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
		}

		AAPT2LinkWorkerTaskIdentifier taskid = (AAPT2LinkWorkerTaskIdentifier) taskcontext.getTaskId();

		CompilationIdentifier compilationid = taskid.getCompilationIdentifier();
		taskcontext.setStandardOutDisplayIdentifier(AAPT2LinkTaskFactory.TASK_NAME + ":" + compilationid);

		SakerDirectory builddir = SakerPathFiles.requireBuildDirectory(taskcontext);
		TaskExecutionUtilities taskutils = taskcontext.getTaskUtilities();
		SakerDirectory outputdir = taskutils.resolveDirectoryAtPathCreate(builddir,
				SakerPath.valueOf(AAPT2LinkTaskFactory.TASK_NAME + "/" + compilationid));

		inputFiles.forEach(System.out::println);

		SakerEnvironment environment = taskcontext.getExecutionContext().getEnvironment();

		NavigableMap<String, SDKReference> sdkrefs;
		//if we have an environment selector then the dependencies are reported during selection
		if (remoteDispatchableEnvironmentSelector == null) {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(taskcontext, this.sdkDescriptions);
		} else {
			sdkrefs = SDKSupportUtils.resolveSDKReferences(environment, this.sdkDescriptions);
		}
		SDKReference buildtoolssdkref = sdkrefs.get(AndroidBuildToolsSDKReference.SDK_NAME);
		if (buildtoolssdkref == null) {
			throw new SDKNotFoundException(AndroidBuildToolsSDKReference.SDK_NAME + " SDK not found.");
		}
		SakerPath exepath = buildtoolssdkref.getPath(AndroidBuildToolsSDKReference.PATH_AAPT2_EXECUTABLE);
		if (exepath == null) {
			throw new SDKPathNotFoundException("aapt2 executable not found in " + buildtoolssdkref);
		}

		NavigableMap<SakerPath, ContentDescriptor> inputfilecontents = new ConcurrentSkipListMap<>();

		outputdir.clear();

		SakerDirectory javaoutdir = outputdir.getDirectoryCreate("java");

		//TODO use @argument-file when input files are too many to fit on the command line

		String outputapkfilename = "output.apk";
		Path outputdirmirror = taskcontext.mirror(outputdir);
		Path outputapkfilepath = outputdirmirror.resolve(outputapkfilename);

		Path javaoutputdirpath = taskcontext.mirror(javaoutdir);

		ArrayList<String> cmd = new ArrayList<>();
		cmd.add("link");
		//TODO parallelize if necessary
		for (SakerPath inpath : inputFiles) {
			MirroredFileContents filecontents = taskutils.mirrorFileAtPathContents(inpath);
			cmd.add(filecontents.getPath().toString());
			inputfilecontents.put(inpath, filecontents.getContents());
		}
		cmd.add("-o");
		cmd.add(outputapkfilepath.toString());

		cmd.add("--manifest");
		MirroredFileContents manifestcontents = taskutils.mirrorFileAtPathContents(manifest);
		cmd.add(manifestcontents.getPath().toString());
		inputfilecontents.put(manifest, manifestcontents.getContents());

		cmd.add("--java");
		cmd.add(javaoutputdirpath.toString());

		SDKReference platformsdk = sdkrefs.get(AndroidPlatformSDKReference.SDK_NAME);
		if (platformsdk != null) {
			SakerPath androidjarpath = platformsdk.getPath(AndroidPlatformSDKReference.PATH_ANDROID_JAR);
			if (androidjarpath != null) {
				cmd.add("-I");
				cmd.add(androidjarpath.toString());
			}
		}

		UnsyncByteArrayOutputStream procout = new UnsyncByteArrayOutputStream();

		int res;
		try {
			res = AAPT2Utils.invokeAAPT2WithArguments(environment, exepath, cmd, procout);
		} finally {
			if (!procout.isEmpty()) {
				procout.writeTo(taskcontext.getStandardOut());
			}
		}
		if (res != 0) {
			throw new IOException("aapt2 linking failed.");
		}

		TreeMap<SakerPath, ContentDescriptor> outputfilecontents = new TreeMap<>();

		LocalFileProvider fp = LocalFileProvider.getInstance();
		ProviderHolderPathKey apkpathkey = fp.getPathKey(outputapkfilepath);
		taskcontext.invalidate(apkpathkey);

		SakerFile apkoutfile = taskutils.createProviderPathFile(outputapkfilename, apkpathkey);
		outputdir.add(apkoutfile);

		SakerPath outputapkpath = apkoutfile.getSakerPath();
		outputfilecontents.put(outputapkpath, apkoutfile.getContentDescriptor());

		SakerPath rjavapath = getCreateRJavaFilePathInEntries(fp.getDirectoryEntriesRecursively(javaoutputdirpath));
		if (rjavapath != null) {
			SakerDirectory rjavaparentdir = taskutils.resolveDirectoryAtRelativePathCreate(javaoutdir,
					rjavapath.getParent());
			ProviderHolderPathKey rjavapathkey = fp.getPathKey(SakerPath.valueOf(javaoutputdirpath).resolve(rjavapath));
			taskcontext.invalidate(rjavapathkey);
			SakerFile rjavafile = taskutils.createProviderPathFile(rjavapath.getFileName(), rjavapathkey);
			rjavaparentdir.add(rjavafile);

			outputfilecontents.put(rjavafile.getSakerPath(), rjavafile.getContentDescriptor());
		} else {
			throw new IOException("R.java not found.");
		}

		outputdir.synchronize();

		taskutils.reportOutputFileDependency(null, outputfilecontents);
		taskutils.reportInputFileDependency(null, inputfilecontents);

		SakerPath rjavasourcedirpath = javaoutdir.getSakerPath();

		return new AAPT2LinkTaskOutputImpl(outputapkpath, rjavasourcedirpath);
	}

	private static SakerPath getCreateRJavaFilePathInEntries(NavigableMap<SakerPath, ? extends FileEntry> entries) {
		for (Entry<SakerPath, ? extends FileEntry> entry : entries.entrySet()) {
			if (!entry.getValue().isRegularFile()) {
				continue;
			}
			if ("R.java".equals(entry.getKey().getFileName())) {
				return entry.getKey();
			}
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, inputFiles);
		SerialUtils.writeExternalMap(out, sdkDescriptions);
		out.writeObject(manifest);
		out.writeObject(remoteDispatchableEnvironmentSelector);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		inputFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		sdkDescriptions = SerialUtils.readExternalSortedImmutableNavigableMap(in,
				SDKSupportUtils.getSDKNameComparator());
		manifest = (SakerPath) in.readObject();
		remoteDispatchableEnvironmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((inputFiles == null) ? 0 : inputFiles.hashCode());
		result = prime * result + ((manifest == null) ? 0 : manifest.hashCode());
		result = prime * result + ((sdkDescriptions == null) ? 0 : sdkDescriptions.hashCode());
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
		AAPT2LinkWorkerTaskFactory other = (AAPT2LinkWorkerTaskFactory) obj;
		if (inputFiles == null) {
			if (other.inputFiles != null)
				return false;
		} else if (!inputFiles.equals(other.inputFiles))
			return false;
		if (manifest == null) {
			if (other.manifest != null)
				return false;
		} else if (!manifest.equals(other.manifest))
			return false;
		if (sdkDescriptions == null) {
			if (other.sdkDescriptions != null)
				return false;
		} else if (!sdkDescriptions.equals(other.sdkDescriptions))
			return false;
		return true;
	}
}
