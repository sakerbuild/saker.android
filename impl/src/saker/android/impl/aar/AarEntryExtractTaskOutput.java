package saker.android.impl.aar;

import saker.std.api.file.location.FileLocation;

public interface AarEntryExtractTaskOutput {
	public FileLocation getFileLocation() throws AarEntryNotFoundException;

	//for supporting automatic conversion to FileLocation
	public default FileLocation toFileLocation() {
		return getFileLocation();
	}
}
