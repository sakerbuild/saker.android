package saker.android.api.aapt2.link;

import java.util.NavigableMap;

import saker.build.file.path.SakerPath;

public interface AAPT2LinkTaskOutput {
	public SakerPath getAPKPath();

	public SakerPath getRJavaSourceDirectory();

	public SakerPath getProguardPath();

	public SakerPath getProguardMainDexPath();

	//doc: for --emit-ids
	public SakerPath getIDMappingsPath();

	public SakerPath getTextSymbolsPath();

	public NavigableMap<String, SakerPath> getSplitPaths();
}
