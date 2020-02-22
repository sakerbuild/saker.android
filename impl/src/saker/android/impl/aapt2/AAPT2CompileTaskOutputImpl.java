package saker.android.impl.aapt2;

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
}