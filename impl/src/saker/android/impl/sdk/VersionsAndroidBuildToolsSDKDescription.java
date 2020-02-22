package saker.android.impl.sdk;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Set;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.sdk.support.api.EnvironmentSDKDescription;
import saker.sdk.support.api.IndeterminateSDKDescription;
import saker.sdk.support.api.SDKDescription;
import saker.sdk.support.api.SDKReference;

public class VersionsAndroidBuildToolsSDKDescription implements IndeterminateSDKDescription, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<String> versions;

	/**
	 * For {@link Externalizable}.
	 */
	public VersionsAndroidBuildToolsSDKDescription() {
	}

	private VersionsAndroidBuildToolsSDKDescription(Set<String> versions) {
		this.versions = versions;
	}

	public static SDKDescription create(Set<String> versions) {
		if (versions != null && versions.size() == 1) {
			//only a single version possible, we don't need indeterminate sdk description
			return EnvironmentSDKDescription
					.create(new VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty(versions));
		}
		return new VersionsAndroidBuildToolsSDKDescription(versions);
	}

	@Override
	public SDKDescription getBaseSDKDescription() {
		return EnvironmentSDKDescription.create(new VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty(versions));
	}

	@Override
	public SDKDescription pinSDKDescription(SDKReference sdkreference) {
		if (sdkreference instanceof AndroidBuildToolsSDKReference) {
			return EnvironmentSDKDescription.create(new VersionsAndroidBuildToolsSDKReferenceEnvironmentProperty(
					Collections.singleton(((AndroidBuildToolsSDKReference) sdkreference).getVersion())));
		}
		return getBaseSDKDescription();
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
		VersionsAndroidBuildToolsSDKDescription other = (VersionsAndroidBuildToolsSDKDescription) obj;
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
