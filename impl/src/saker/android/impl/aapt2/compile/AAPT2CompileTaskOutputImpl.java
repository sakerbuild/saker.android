package saker.android.impl.aapt2.compile;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.android.api.aapt2.compile.AAPT2CompileTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.compiler.utils.api.CompilationIdentifier;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKSupportUtils;

final class AAPT2CompileTaskOutputImpl implements AAPT2CompileTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private CompilationIdentifier compilationId;
	private SakerPath outputDirectoryPath;
	private NavigableSet<SakerPath> outputFilePaths;
	private NavigableMap<String, SDKDescription> sdks;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2CompileTaskOutputImpl() {
	}

	public AAPT2CompileTaskOutputImpl(SakerPath outputDirectoryPath, NavigableSet<SakerPath> outputfiles,
			CompilationIdentifier compilationid, NavigableMap<String, SDKDescription> sdks) {
		this.outputDirectoryPath = outputDirectoryPath;
		this.outputFilePaths = outputfiles;
		this.compilationId = compilationid;
		this.sdks = sdks;
	}

	@Override
	public NavigableSet<SakerPath> getOutputPaths() {
		return outputFilePaths;
	}

	@Override
	public CompilationIdentifier getIdentifier() {
		return compilationId;
	}

	@Override
	public NavigableMap<String, SDKDescription> getSDKs() {
		return sdks;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(outputDirectoryPath);
		SerialUtils.writeExternalCollection(out, outputFilePaths);
		out.writeObject(compilationId);
		SerialUtils.writeExternalMap(out, sdks);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputDirectoryPath = (SakerPath) in.readObject();
		outputFilePaths = SerialUtils.readExternalImmutableNavigableSet(in);
		compilationId = (CompilationIdentifier) in.readObject();
		sdks = SerialUtils.readExternalSortedImmutableNavigableMap(in, SDKSupportUtils.getSDKNameComparator());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((compilationId == null) ? 0 : compilationId.hashCode());
		result = prime * result + ((outputDirectoryPath == null) ? 0 : outputDirectoryPath.hashCode());
		result = prime * result + ((outputFilePaths == null) ? 0 : outputFilePaths.hashCode());
		result = prime * result + ((sdks == null) ? 0 : sdks.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AAPT2CompileTaskOutputImpl other = (AAPT2CompileTaskOutputImpl) obj;
		if (compilationId == null) {
			if (other.compilationId != null)
				return false;
		} else if (!compilationId.equals(other.compilationId))
			return false;
		if (outputDirectoryPath == null) {
			if (other.outputDirectoryPath != null)
				return false;
		} else if (!outputDirectoryPath.equals(other.outputDirectoryPath))
			return false;
		if (outputFilePaths == null) {
			if (other.outputFilePaths != null)
				return false;
		} else if (!outputFilePaths.equals(other.outputFilePaths))
			return false;
		if (sdks == null) {
			if (other.sdks != null)
				return false;
		} else if (!sdks.equals(other.sdks))
			return false;
		return true;
	}

}