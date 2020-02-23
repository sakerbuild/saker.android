package saker.android.api.d8;

import java.util.NavigableSet;

import saker.build.file.path.SakerPath;

public interface D8TaskOutput {
	public NavigableSet<SakerPath> getDexFiles();
}
