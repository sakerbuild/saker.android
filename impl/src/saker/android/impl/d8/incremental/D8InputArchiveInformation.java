package saker.android.impl.d8.incremental;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.file.location.FileLocation;

public class D8InputArchiveInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	private FileLocation fileLocation;
	private ContentDescriptor contents;
	private NavigableSet<String> descriptors;

	private SakerPath outputDir;
	private NavigableMap<String, ContentDescriptor> outputDexFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public D8InputArchiveInformation() {
	}

	public D8InputArchiveInformation(FileLocation fileLocation, ContentDescriptor contents) {
		this.fileLocation = fileLocation;
		this.contents = contents;
	}

	public D8InputArchiveInformation(FileLocation fileLocation, ContentDescriptor contents,
			NavigableSet<String> descriptors) {
		this.fileLocation = fileLocation;
		this.contents = contents;
		this.descriptors = descriptors;
	}

	public void setOutputDexFiles(SakerPath outputdir, NavigableMap<String, ContentDescriptor> outputDexFiles) {
		this.outputDir = outputdir;
		this.outputDexFiles = outputDexFiles;
	}

	public void setDescriptors(NavigableSet<String> descriptors) {
		this.descriptors = descriptors;
	}

	public FileLocation getFileLocation() {
		return fileLocation;
	}

	public ContentDescriptor getContents() {
		return contents;
	}

	public NavigableSet<String> getDescriptors() {
		return descriptors;
	}

	public SakerPath getOutputDirectoryPath() {
		return outputDir;
	}

	public NavigableMap<String, ContentDescriptor> getOutputDexFiles() {
		return outputDexFiles;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(fileLocation);
		out.writeObject(contents);
		SerialUtils.writeExternalCollection(out, descriptors);
		out.writeObject(outputDir);
		SerialUtils.writeExternalMap(out, outputDexFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		fileLocation = SerialUtils.readExternalObject(in);
		contents = SerialUtils.readExternalObject(in);
		descriptors = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		outputDir = SerialUtils.readExternalObject(in);
		outputDexFiles = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

}
