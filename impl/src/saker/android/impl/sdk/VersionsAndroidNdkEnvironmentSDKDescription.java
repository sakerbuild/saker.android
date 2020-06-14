package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.EnvironmentSDKDescription;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;

public class VersionsAndroidNdkEnvironmentSDKDescription implements EnvironmentSDKDescription, Externalizable {
	private static final long serialVersionUID = 1L;

	//this class is a subclass of EnvironmentSDKDescription instead of using EnvironmentSDKDescription.create
	//as it needs to have additional methods (getClangSDK, getClangXXSDK) for allowing their retrieval through reflection

	private Set<String> versions;

	/**
	 * For {@link Externalizable}.
	 */
	public VersionsAndroidNdkEnvironmentSDKDescription() {
	}

	public VersionsAndroidNdkEnvironmentSDKDescription(Set<String> versions) {
		this.versions = versions;
	}

	@Override
	public SDKReference getSDK(SakerEnvironment environment) throws Exception {
		return environment
				.getEnvironmentPropertyCurrentValue(new VersionsAndroidNdkSDKReferenceEnvironmentProperty(versions));
	}

	public SDKDescription getClangSDK() {
		return VersionsAndroidNdkSDKDescription.getClangSdk(AndroidNdkSDKReference.PATH_CLANG_EXE, versions);
	}

	public SDKDescription getClangXXSDK() {
		return VersionsAndroidNdkSDKDescription.getClangSdk(AndroidNdkSDKReference.PATH_CLANGXX_EXE, versions);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, versions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		versions = SerialUtils.readExternalImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		VersionsAndroidNdkEnvironmentSDKDescription other = (VersionsAndroidNdkEnvironmentSDKDescription) obj;
		if (versions == null) {
			if (other.versions != null)
				return false;
		} else if (!versions.equals(other.versions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + versions + "]";
	}
}
