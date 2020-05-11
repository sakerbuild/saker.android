package saker.android.impl.aapt2.link;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;

import saker.android.api.aapt2.link.Aapt2LinkInputLibrary;
import saker.android.api.aapt2.link.Aapt2LinkTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.compiler.utils.api.CompilationIdentifier;

final class Aapt2LinkTaskOutputImpl implements Aapt2LinkTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private CompilationIdentifier identifier;
	private SakerPath apkPath;
	private List<SakerPath> javaSourceDirectories;

	private SakerPath proguardPath;
	private SakerPath proguardMainDexPath;
	private SakerPath idMappingsPath;
	private SakerPath textSymbolsPath;
	private NavigableMap<String, SakerPath> splits;

	private List<Aapt2LinkInputLibrary> inputLibraries;

	/**
	 * For {@link Externalizable}.
	 */
	public Aapt2LinkTaskOutputImpl() {
	}

	public Aapt2LinkTaskOutputImpl(CompilationIdentifier identifier, SakerPath apkPath) {
		this.identifier = identifier;
		this.apkPath = apkPath;
	}

	public void setJavaSourceDirectories(List<SakerPath> javaSourceDirectories) {
		this.javaSourceDirectories = javaSourceDirectories;
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

	public void setInputLibraries(List<Aapt2LinkInputLibrary> inputLibraries) {
		this.inputLibraries = inputLibraries;
	}

	@Override
	public Collection<Aapt2LinkInputLibrary> getInputLibraries() {
		return inputLibraries;
	}

	@Override
	public CompilationIdentifier getIdentifier() {
		return identifier;
	}

	@Override
	public SakerPath getAPKPath() {
		return apkPath;
	}

	@Override
	public List<SakerPath> getJavaSourceDirectories() {
		return javaSourceDirectories;
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
		out.writeObject(identifier);
		out.writeObject(apkPath);
		out.writeObject(proguardPath);
		out.writeObject(proguardMainDexPath);
		out.writeObject(idMappingsPath);
		out.writeObject(textSymbolsPath);
		SerialUtils.writeExternalMap(out, splits);
		SerialUtils.writeExternalCollection(out, javaSourceDirectories);
		SerialUtils.writeExternalCollection(out, inputLibraries);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		identifier = (CompilationIdentifier) in.readObject();
		apkPath = (SakerPath) in.readObject();
		proguardPath = (SakerPath) in.readObject();
		proguardMainDexPath = (SakerPath) in.readObject();
		idMappingsPath = (SakerPath) in.readObject();
		textSymbolsPath = (SakerPath) in.readObject();
		splits = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		javaSourceDirectories = SerialUtils.readExternalImmutableList(in);
		inputLibraries = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
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
		Aapt2LinkTaskOutputImpl other = (Aapt2LinkTaskOutputImpl) obj;
		if (apkPath == null) {
			if (other.apkPath != null)
				return false;
		} else if (!apkPath.equals(other.apkPath))
			return false;
		if (idMappingsPath == null) {
			if (other.idMappingsPath != null)
				return false;
		} else if (!idMappingsPath.equals(other.idMappingsPath))
			return false;
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		if (inputLibraries == null) {
			if (other.inputLibraries != null)
				return false;
		} else if (!inputLibraries.equals(other.inputLibraries))
			return false;
		if (javaSourceDirectories == null) {
			if (other.javaSourceDirectories != null)
				return false;
		} else if (!javaSourceDirectories.equals(other.javaSourceDirectories))
			return false;
		if (proguardMainDexPath == null) {
			if (other.proguardMainDexPath != null)
				return false;
		} else if (!proguardMainDexPath.equals(other.proguardMainDexPath))
			return false;
		if (proguardPath == null) {
			if (other.proguardPath != null)
				return false;
		} else if (!proguardPath.equals(other.proguardPath))
			return false;
		if (splits == null) {
			if (other.splits != null)
				return false;
		} else if (!splits.equals(other.splits))
			return false;
		if (textSymbolsPath == null) {
			if (other.textSymbolsPath != null)
				return false;
		} else if (!textSymbolsPath.equals(other.textSymbolsPath))
			return false;
		return true;
	}

}