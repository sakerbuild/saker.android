package saker.android.api.aapt2.compile;

import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;

/**
 * Output of the aapt2 compilation operation.
 */
public interface Aapt2CompileWorkerTaskOutput {
	/**
	 * Gets the compilation identifier associated with this operation.
	 * 
	 * @return The compilation identifier.
	 */
	public CompilationIdentifier getIdentifier();

	/**
	 * Gets the paths to the compiled output resources.
	 * 
	 * @return A set of absolute output execution paths.
	 */
	public NavigableSet<SakerPath> getOutputPaths();

	/**
	 * Gets the SDKs that were used as part of the operation.
	 * 
	 * @return The SDKs.
	 */
	public NavigableMap<String, ? extends SDKDescription> getSDKs();
}
