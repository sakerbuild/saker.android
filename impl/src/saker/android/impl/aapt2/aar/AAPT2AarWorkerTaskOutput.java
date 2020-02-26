package saker.android.impl.aapt2.aar;

import java.util.NavigableSet;

import saker.build.file.path.SakerPath;

public interface AAPT2AarWorkerTaskOutput {
	public NavigableSet<SakerPath> getOutputFiles();
}
