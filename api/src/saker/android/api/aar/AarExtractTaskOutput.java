package saker.android.api.aar;

import java.util.Set;

import saker.android.impl.aar.AarEntryNotFoundException;
import saker.std.api.file.location.FileLocation;

public interface AarExtractTaskOutput {
	public FileLocation getFileLocation() throws AarEntryNotFoundException;

	//for supporting automatic conversion to FileLocation
	public default FileLocation toFileLocation() {
		return getFileLocation();
	}

	public default Set<FileLocation> getDirectoryFileLocations() {
		return null;
	}
}
