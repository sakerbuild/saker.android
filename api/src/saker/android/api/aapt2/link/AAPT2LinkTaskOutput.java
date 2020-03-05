package saker.android.api.aapt2.link;

import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;

import saker.build.file.path.SakerPath;
import saker.compiler.utils.api.CompilationIdentifier;

public interface AAPT2LinkTaskOutput {
	public CompilationIdentifier getIdentifier();

	public SakerPath getAPKPath();

	public List<SakerPath> getJavaSourceDirectories();

	public SakerPath getProguardPath();

	public SakerPath getProguardMainDexPath();

	//doc: for --emit-ids
	public SakerPath getIDMappingsPath();

	public SakerPath getTextSymbolsPath();

	public NavigableMap<String, SakerPath> getSplitPaths();

	//these are passed as the input to aapt2
	public Collection<AAPT2LinkInputLibrary> getInputLibraries();
}
