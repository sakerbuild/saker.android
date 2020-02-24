package saker.android.impl.apk.sign;

import java.nio.file.Path;
import java.util.NavigableMap;

import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKReference;

public interface ApkSignExecutor {
	public void run(TaskContext taskcontext, SignApkWorkerTaskFactory workertask,
			NavigableMap<String, SDKReference> sdkrefs, Path inputfilelocalpath, Path outputfilelocalpath)
			throws Exception;

}
