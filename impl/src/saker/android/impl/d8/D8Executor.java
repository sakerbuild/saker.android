package saker.android.impl.d8;

import java.util.NavigableMap;

import saker.android.api.d8.D8TaskOutput;
import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKReference;

public interface D8Executor {
	public D8TaskOutput run(TaskContext taskcontext, D8WorkerTaskFactory d8WorkerTaskFactory,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception;
}