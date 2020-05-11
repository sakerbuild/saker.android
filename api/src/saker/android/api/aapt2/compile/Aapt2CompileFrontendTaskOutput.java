package saker.android.api.aapt2.compile;

import java.util.Collection;

import saker.android.api.aapt2.aar.Aapt2AarCompileWorkerTaskOutput;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;

/**
 * Output of the aapt2 compile frontend task.
 * <p>
 * This is returned directly by the frontent aapt2 compilation task. It provides access to the additionally compiled AAR
 * libraries in addition to the worker task identifier of the compiler task.
 * <p>
 * The implementations of this interface will also implement {@link StructuredTaskResult}.
 */
public interface Aapt2CompileFrontendTaskOutput {
	/**
	 * Gets the task identifier of the worker task.
	 * <p>
	 * The worker task has a result type of {@link Aapt2CompileWorkerTaskOutput}.
	 * 
	 * @return The task id.
	 */
	public TaskIdentifier getWorkerTaskIdentifier();

	/**
	 * Gets the structured task results of the automatically compiled AAR libraries.
	 * <p>
	 * The result type of the tasks are {@link Aapt2AarCompileWorkerTaskOutput}.
	 * 
	 * @return The collection of structured task results.
	 */
	public Collection<StructuredTaskResult> getAarCompilations();
}
