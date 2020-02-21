package saker.android.impl.d8;

import java.util.NavigableMap;

import saker.build.task.TaskContext;
import saker.sdk.support.api.SDKReference;

public interface D8Executor {
	public Object run(TaskContext taskcontext, D8WorkerTaskFactory d8WorkerTaskFactory,
			NavigableMap<String, SDKReference> sdkreferences) throws Exception;
}