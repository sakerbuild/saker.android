package saker.android.impl.aapt2.link;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class AAPT2LinkTaskOutputImpl implements AAPT2LinkTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath apkPath;
	private SakerPath rJavaSourceDirectory;

	private SakerPath proguardPath;
	private SakerPath proguardMainDexPath;
	private SakerPath idMappingsPath;
	private SakerPath textSymbolsPath;
	private NavigableMap<String, SakerPath> splits;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2LinkTaskOutputImpl() {
	}

	public AAPT2LinkTaskOutputImpl(SakerPath apkPath, SakerPath rJavaSourceDirectory) {
		this.apkPath = apkPath;
		this.rJavaSourceDirectory = rJavaSourceDirectory;
	}

	public void setProguardPath(SakerPath proguardPath) {
		this.proguardPath = proguardPath;
	}

	public void setProguardMainDexPath(SakerPath proguardMainDexPath) {
		this.proguardMainDexPath = proguardMainDexPath;
	}

	public void setIDMappingsPath(SakerPath idMappingsPath) {
		this.idMappingsPath = idMappingsPath;
	}

	public void setTextSymbolsPath(SakerPath textSymbolsPath) {
		this.textSymbolsPath = textSymbolsPath;
	}

	public void setSplits(NavigableMap<String, SakerPath> splits) {
		this.splits = splits;
	}

	@Override
	public SakerPath getAPKPath() {
		return apkPath;
	}

	@Override
	public SakerPath getRJavaSourceDirectory() {
		return rJavaSourceDirectory;
	}

	@Override
	public SakerPath getProguardPath() {
		return proguardPath;
	}

	@Override
	public SakerPath getProguardMainDexPath() {
		return proguardMainDexPath;
	}

	@Override
	public SakerPath getIDMappingsPath() {
		return idMappingsPath;
	}

	@Override
	public SakerPath getTextSymbolsPath() {
		return textSymbolsPath;
	}

	@Override
	public NavigableMap<String, SakerPath> getSplitPaths() {
		return splits;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(apkPath);
		out.writeObject(rJavaSourceDirectory);
		out.writeObject(proguardPath);
		out.writeObject(proguardMainDexPath);
		out.writeObject(idMappingsPath);
		out.writeObject(textSymbolsPath);
		SerialUtils.writeExternalMap(out, splits);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		apkPath = (SakerPath) in.readObject();
		rJavaSourceDirectory = (SakerPath) in.readObject();
		proguardPath = (SakerPath) in.readObject();
		proguardMainDexPath = (SakerPath) in.readObject();
		idMappingsPath = (SakerPath) in.readObject();
		textSymbolsPath = (SakerPath) in.readObject();
		splits = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

}