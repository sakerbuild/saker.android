package saker.android.api.aar;

import java.util.Set;

import saker.android.impl.aar.AarEntryNotFoundException;
import saker.std.api.file.location.FileLocation;

/**
 * Output of the AAR entry extraction task.
 * <p>
 * The interface provides access to the location to where the files were extracted from the input AAR and the list of
 * files that were extracted.
 */
public interface AarExtractWorkerTaskOutput {
	/**
	 * Gets the file location to where the specified entry was extracted.
	 * 
	 * @return The file location.
	 * @throws AarEntryNotFoundException
	 *             If the given entry was not found in the AAR.
	 */
	public FileLocation getFileLocation() throws AarEntryNotFoundException;

	/**
	 * Gets the file location to where the specified entry was extracted.
	 * <p>
	 * Same as {@link #getFileLocation()} to support automatic conversion to {@link FileLocation}.
	 * 
	 * @return The file location.
	 * @throws AarEntryNotFoundException
	 *             If the given entry was not found in the AAR.
	 */
	public default FileLocation toFileLocation() throws AarEntryNotFoundException {
		return getFileLocation();
	}

	/**
	 * Gets the file locations that were found under the specified extracted directory entry.
	 * <p>
	 * If the entry to be extracted is a directory, this method returns the file locations for all the files that were
	 * recursively extracted from the directory entry.
	 * 
	 * @return The set of file locations if the specified entry was a directory. <code>null</code> if it was not a
	 *             directory.
	 */
	public default Set<FileLocation> getDirectoryFileLocations() {
		return null;
	}
}
