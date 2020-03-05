package saker.android.api.aapt2.aar;

import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.std.api.file.location.FileLocation;

public interface AAPT2AarCompileTaskOutput {
	public FileLocation getAarFile();

	public NavigableSet<SakerPath> getOutputPaths();

	public FileLocation getRTxtFile();

	public FileLocation getAndroidManifestXmlFile();
}
