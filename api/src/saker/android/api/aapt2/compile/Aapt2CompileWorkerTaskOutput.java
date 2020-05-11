package saker.android.api.aapt2.compile;

import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;

public interface Aapt2CompileWorkerTaskOutput {
	public NavigableSet<SakerPath> getOutputPaths();

	public CompilationIdentifier getIdentifier();

	public NavigableMap<String, ? extends SDKDescription> getSDKs();
}
