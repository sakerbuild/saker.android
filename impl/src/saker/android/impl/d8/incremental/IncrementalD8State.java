package saker.android.impl.d8.incremental;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.SDKReference;
import saker.std.api.file.location.FileLocation;

public class IncrementalD8State implements Externalizable {
	private static final long serialVersionUID = 1L;

	//XXX make these private
	public SDKReference buildToolsSDK;
	public SDKReference platformsSDK;
	public NavigableMap<SakerPath, D8InputFileInformation> inputPathInformations;
	public NavigableMap<String, D8InputFileInformation> inputDescriptorInformations;
	public Integer minApi;
	public boolean noDesugaring;
	public boolean release;

	public NavigableMap<String, D8OutputFileInformation> outputDescriptorInformations;
	public NavigableMap<SakerPath, D8OutputFileInformation> outputPathInformations;

	public NavigableMap<SakerPath, D8OutputFileInformation> outputClassIndexInformations;

	public Map<FileLocation, D8InputArchiveInformation> archiveInformations;

	/**
	 * For {@link Externalizable}.
	 */
	public IncrementalD8State() {
	}

	public IncrementalD8State(IncrementalD8State copy) {
		this.buildToolsSDK = copy.buildToolsSDK;
		this.platformsSDK = copy.platformsSDK;
		this.inputPathInformations = new ConcurrentSkipListMap<>(copy.inputPathInformations);
		this.inputDescriptorInformations = new ConcurrentSkipListMap<>(copy.inputDescriptorInformations);
		this.minApi = copy.minApi;
		this.noDesugaring = copy.noDesugaring;
		this.release = copy.release;
		this.outputDescriptorInformations = new ConcurrentSkipListMap<>(copy.outputDescriptorInformations);
		this.outputPathInformations = new ConcurrentSkipListMap<>(copy.outputPathInformations);
		this.outputClassIndexInformations = new ConcurrentSkipListMap<>(copy.outputClassIndexInformations);
		this.archiveInformations = new ConcurrentHashMap<>(copy.archiveInformations);
	}

	public SDKReference getBuildToolsSDK() {
		return buildToolsSDK;
	}

	public SDKReference getPlatformsSDK() {
		return platformsSDK;
	}

	public NavigableMap<SakerPath, D8InputFileInformation> getInputPathInformations() {
		return inputPathInformations;
	}

	public NavigableMap<String, D8InputFileInformation> getInputDescriptorInformations() {
		return inputDescriptorInformations;
	}

	public Integer getMinApi() {
		return minApi;
	}

	public boolean isNoDesugaring() {
		return noDesugaring;
	}

	public boolean isRelease() {
		return release;
	}

	public void putInput(D8InputFileInformation info) {
		this.inputPathInformations.put(info.getPath(), info);
		this.inputDescriptorInformations.put(info.getDescriptor(), info);
	}

	public void putOutput(D8OutputFileInformation info) {
		this.outputPathInformations.put(info.getPath(), info);
		this.outputDescriptorInformations.put(info.getDescriptor(), info);
	}

	public D8OutputFileInformation removeOutputForPath(SakerPath path) {
		D8OutputFileInformation outinfo = outputPathInformations.remove(path);
		if (outinfo != null) {
			outputDescriptorInformations.remove(outinfo.getDescriptor());
		}
		return outinfo;
	}

	public D8OutputFileInformation removeOutputForDescriptor(String descriptor) {
		D8OutputFileInformation outinfo = outputDescriptorInformations.remove(descriptor);
		if (outinfo != null) {
			outputPathInformations.remove(outinfo.getPath());
		}
		return outinfo;
	}

	public D8InputFileInformation removeInputForPath(SakerPath path) {
		D8InputFileInformation info = inputPathInformations.remove(path);
		if (info != null) {
			inputDescriptorInformations.remove(info.getDescriptor());
		}
		return info;
	}

	public D8InputFileInformation removeInputForDescriptor(String descriptor) {
		D8InputFileInformation info = inputDescriptorInformations.remove(descriptor);
		if (info != null) {
			inputPathInformations.remove(info.getPath());
		}
		return info;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(buildToolsSDK);
		out.writeObject(platformsSDK);
		SerialUtils.writeExternalMap(out, inputPathInformations);
		SerialUtils.writeExternalMap(out, inputDescriptorInformations);
		SerialUtils.writeExternalMap(out, outputDescriptorInformations);
		SerialUtils.writeExternalMap(out, outputPathInformations);
		out.writeObject(minApi);
		out.writeBoolean(noDesugaring);
		out.writeBoolean(release);
		SerialUtils.writeExternalMap(out, outputClassIndexInformations);
		SerialUtils.writeExternalMap(out, archiveInformations);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		buildToolsSDK = (SDKReference) in.readObject();
		platformsSDK = (SDKReference) in.readObject();
		inputPathInformations = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		inputDescriptorInformations = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		outputDescriptorInformations = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		outputPathInformations = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		minApi = (Integer) in.readObject();
		noDesugaring = in.readBoolean();
		release = in.readBoolean();
		outputClassIndexInformations = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		archiveInformations = SerialUtils.readExternalImmutableHashMap(in);
	}

}