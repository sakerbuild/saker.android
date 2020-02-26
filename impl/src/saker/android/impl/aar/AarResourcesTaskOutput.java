package saker.android.impl.aar;

import java.util.Set;

import saker.std.api.file.location.FileLocation;

public interface AarResourcesTaskOutput {
	//file location to the resource directory
	public FileLocation getFileLocation() throws AarEntryNotFoundException;

	public Set<FileLocation> getResourceFiles() throws AarEntryNotFoundException;

	//for supporting automatic conversion to FileLocation
	public default FileLocation toFileLocation() {
		return getFileLocation();
	}
}
