package saker.android.api.aidl;

import saker.build.file.path.SakerPath;

/**
 * Output of the aidl task.
 */
public interface AidlWorkerTaskOutput {
	/**
	 * Gets the path to the source directory that contains the generated Java sources.
	 * 
	 * @return The absolute execution path.
	 */
	public SakerPath getJavaSourceDirectory();
}
