package saker.android.api.aapt2.compile;

import java.util.Collection;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;

public interface AAPT2CompileFrontendTaskOutput {
	public TaskIdentifier getWorkerTaskIdentifier();

	public Collection<StructuredTaskResult> getAarCompilations();
}
