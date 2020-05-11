package saker.android.api.aapt2.link;

import saker.std.api.file.location.FileLocation;

/**
 * Input library for aapt2 linking.
 */
public interface Aapt2LinkInputLibrary {
	/**
	 * Gets the file location for the input AAR file.
	 * 
	 * @return The file location.
	 */
	public FileLocation getAarFile();
}
