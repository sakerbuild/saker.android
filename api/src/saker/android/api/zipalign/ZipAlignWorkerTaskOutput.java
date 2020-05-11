package saker.android.api.zipalign;

import saker.build.file.path.SakerPath;

/**
 * Output of the zipalign task.
 */
public interface ZipAlignWorkerTaskOutput {
	/**
	 * Gets the path to the aligned ZIP archive.
	 * 
	 * @return The absolute execution path.
	 */
	public SakerPath getPath();
}
