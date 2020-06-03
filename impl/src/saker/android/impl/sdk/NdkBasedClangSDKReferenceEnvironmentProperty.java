package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.SDKReference;

public class NdkBasedClangSDKReferenceEnvironmentProperty implements EnvironmentProperty<SDKReference>, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<String> versions;

	private String exePathIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public NdkBasedClangSDKReferenceEnvironmentProperty() {
	}

	public NdkBasedClangSDKReferenceEnvironmentProperty(Set<String> versions, String exePathIdentifier) {
		this.versions = versions;
		this.exePathIdentifier = exePathIdentifier;
	}

	@Override
	public SDKReference getCurrentValue(SakerEnvironment environment) throws Exception {
		SDKReference ndksdkref = environment
				.getEnvironmentPropertyCurrentValue(new VersionsAndroidNdkSDKReferenceEnvironmentProperty(versions));
		return new NdkBasedClangSDKReference(ndksdkref, exePathIdentifier);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, versions);
		out.writeObject(exePathIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		versions = SerialUtils.readExternalImmutableNavigableSet(in);
		exePathIdentifier = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exePathIdentifier == null) ? 0 : exePathIdentifier.hashCode());
		result = prime * result + ((versions == null) ? 0 : versions.hashCode());
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
		NdkBasedClangSDKReferenceEnvironmentProperty other = (NdkBasedClangSDKReferenceEnvironmentProperty) obj;
		if (exePathIdentifier == null) {
			if (other.exePathIdentifier != null)
				return false;
		} else if (!exePathIdentifier.equals(other.exePathIdentifier))
			return false;
		if (versions == null) {
			if (other.versions != null)
				return false;
		} else if (!versions.equals(other.versions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NdkBasedClangSDKReferenceEnvironmentProperty[" + (versions != null ? "versions=" + versions + ", " : "")
				+ (exePathIdentifier != null ? "exePathIdentifier=" + exePathIdentifier : "") + "]";
	}
}
