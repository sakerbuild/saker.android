package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.SDKReference;

public class NdkBasedClangSDKReference implements SDKReference, Externalizable {
	private static final long serialVersionUID = 1L;

	private SDKReference ndk;

	private String exePathIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public NdkBasedClangSDKReference() {
	}

	public NdkBasedClangSDKReference(SDKReference ndk, String exePathIdentifier) {
		this.ndk = ndk;
		this.exePathIdentifier = exePathIdentifier;
	}

	public SDKReference getNdk() {
		return ndk;
	}

	@Override
	public SakerPath getPath(String identifier) throws Exception {
		if (identifier == null) {
			return null;
		}
		switch (identifier) {
			case "exe": {
				return ndk.getPath(exePathIdentifier);
			}
			default: {
				break;
			}
		}
		return null;
	}

	@Override
	public String getProperty(String identifier) throws Exception {
		if (identifier == null) {
			return null;
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(ndk);
		out.writeObject(exePathIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		ndk = SerialUtils.readExternalObject(in);
		exePathIdentifier = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exePathIdentifier == null) ? 0 : exePathIdentifier.hashCode());
		result = prime * result + ((ndk == null) ? 0 : ndk.hashCode());
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
		NdkBasedClangSDKReference other = (NdkBasedClangSDKReference) obj;
		if (exePathIdentifier == null) {
			if (other.exePathIdentifier != null)
				return false;
		} else if (!exePathIdentifier.equals(other.exePathIdentifier))
			return false;
		if (ndk == null) {
			if (other.ndk != null)
				return false;
		} else if (!ndk.equals(other.ndk))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NdkBasedClangSDKReference[" + (ndk != null ? "ndk=" + ndk + ", " : "")
				+ (exePathIdentifier != null ? "exePathIdentifier=" + exePathIdentifier : "") + "]";
	}

}
