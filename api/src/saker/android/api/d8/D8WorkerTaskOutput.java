package saker.android.api.d8;

import java.util.NavigableSet;

import saker.build.file.path.SakerPath;

/**
 * Output of the d8 task.
 */
public interface D8WorkerTaskOutput {
	/**
	 * Gets the paths to the created dex files.
	 * <p>
	 * The method may return one or more paths based on the settings if multidexing was enabled.
	 * 
	 * @return The absolute paths to the created dex files.
	 */
	public NavigableSet<SakerPath> getDexFiles();
}
