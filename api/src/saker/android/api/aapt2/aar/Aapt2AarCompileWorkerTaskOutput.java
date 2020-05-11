package saker.android.api.aapt2.aar;

import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

/**
 * Output of the AAR aapt2 compilation worker task.
 */
public interface Aapt2AarCompileWorkerTaskOutput {
	/**
	 * Gets the file location of the input AAR library that was compiled.
	 * 
	 * @return The file location.
	 */
	public FileLocation getAarFile();

	/**
	 * Gets the paths to the output compiled resource files.
	 * 
	 * @return A set of absolute execution paths.
	 */
	public NavigableSet<SakerPath> getOutputPaths();

	/**
	 * Gets the path to the extracted R.txt file of the AAR.
	 * 
	 * @return The file location or <code>null</code> if the AAR library didn't contain an R.txt file.
	 */
	public FileLocation getRTxtFile();

	/**
	 * Gets the file location of the AndroidManifest.xml file of the AAR.
	 * 
	 * @return The file location
	 */
	public FileLocation getAndroidManifestXmlFile();
}
