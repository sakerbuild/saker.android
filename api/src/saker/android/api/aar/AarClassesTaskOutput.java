package saker.android.api.aar;

import saker.std.api.file.location.FileLocation;

public interface AarClassesTaskOutput {
	public FileLocation getFileLocation() throws AarEntryNotFoundException;

	//for supporting automatic conversion to FileLocation
	public default FileLocation toFileLocation() {
		return getFileLocation();
	}
}
