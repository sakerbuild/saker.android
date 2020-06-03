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

public class VersionsAndroidNdkSDKDescription implements IndeterminateSDKDescription, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<String> versions;

	/**
	 * For {@link Externalizable}.
	 */
	public VersionsAndroidNdkSDKDescription() {
	}

	public VersionsAndroidNdkSDKDescription(Set<String> versions) {
		this.versions = versions;
	}

	public static VersionsAndroidNdkSDKDescription create(Set<String> versions) {
		return new VersionsAndroidNdkSDKDescription(versions);
	}

	@Override
	public SDKDescription getBaseSDKDescription() {
		return EnvironmentSDKDescription.create(new VersionsAndroidNdkSDKReferenceEnvironmentProperty(versions));
	}

	@Override
	public SDKDescription pinSDKDescription(SDKReference sdkreference) {
		if (sdkreference instanceof AndroidNdkSDKReference) {
			return EnvironmentSDKDescription.create(new VersionsAndroidNdkSDKReferenceEnvironmentProperty(
					Collections.singleton(((AndroidNdkSDKReference) sdkreference).getVersion())));
		}
		return getBaseSDKDescription();
	}

	public SDKDescription getClangSDK() {
		return getClangSdk(AndroidNdkSDKReference.PATH_CLANG_EXE, versions);
	}

	public SDKDescription getClangXXSDK() {
		return getClangSdk(AndroidNdkSDKReference.PATH_CLANGXX_EXE, versions);
	}

	private static SDKDescription getClangSdk(String exename, Set<String> versions) {
		return new AndrodNdkEmbeddedClangSDKDescription(exename, versions);
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
		VersionsAndroidNdkSDKDescription other = (VersionsAndroidNdkSDKDescription) obj;
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

	private static final class AndrodNdkEmbeddedClangSDKDescription
			implements IndeterminateSDKDescription, Externalizable {
		private static final long serialVersionUID = 1L;

		private String exename;
		private Set<String> versions;

		/**
		 * For {@link Externalizable}.
		 */
		public AndrodNdkEmbeddedClangSDKDescription() {
		}

		private AndrodNdkEmbeddedClangSDKDescription(String exename, Set<String> versions) {
			this.exename = exename;
			this.versions = versions;
		}

		@Override
		public SDKDescription pinSDKDescription(SDKReference sdkreference) {
			if (sdkreference instanceof NdkBasedClangSDKReference) {
				AndroidNdkSDKReference ndk = (AndroidNdkSDKReference) ((NdkBasedClangSDKReference) sdkreference)
						.getNdk();
				return EnvironmentSDKDescription.create(new NdkBasedClangSDKReferenceEnvironmentProperty(
						Collections.singleton(ndk.getVersion()), exename));
			}
			return getBaseSDKDescription();
		}

		@Override
		public SDKDescription getBaseSDKDescription() {
			return EnvironmentSDKDescription
					.create(new NdkBasedClangSDKReferenceEnvironmentProperty(versions, exename));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(exename);
			SerialUtils.writeExternalCollection(out, versions);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			exename = SerialUtils.readExternalObject(in);
			versions = SerialUtils.readExternalImmutableLinkedHashSet(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((exename == null) ? 0 : exename.hashCode());
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
			AndrodNdkEmbeddedClangSDKDescription other = (AndrodNdkEmbeddedClangSDKDescription) obj;
			if (exename == null) {
				if (other.exename != null)
					return false;
			} else if (!exename.equals(other.exename))
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
			return getClass().getSimpleName() + "[" + (exename != null ? "exename=" + exename + ", " : "")
					+ (versions != null ? "versions=" + versions : "") + "]";
		}
	}
}
