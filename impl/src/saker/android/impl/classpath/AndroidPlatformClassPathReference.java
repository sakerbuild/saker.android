package saker.android.impl.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import saker.android.impl.sdk.AndroidBuildToolsSDKReference;
import saker.android.impl.sdk.AndroidPlatformSDKReference;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.java.compiler.api.classpath.ClassPathEntry;
import saker.java.compiler.api.classpath.ClassPathEntryInputFile;
import saker.java.compiler.api.classpath.ClassPathReference;
import saker.java.compiler.api.classpath.JavaSourceDirectory;
import saker.sdk.support.api.SDKPathReference;
import saker.std.api.file.location.FileLocation;

public class AndroidPlatformClassPathReference implements ClassPathReference, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<ClassPathEntry> entries;

	/**
	 * For {@link Externalizable}.
	 */
	public AndroidPlatformClassPathReference() {
	}

	public AndroidPlatformClassPathReference(boolean withlambdas) {
		entries = new ArrayList<>();
		entries.add(new SimpleSDKPathClassPathEntry(
				SDKPathReference.create(AndroidPlatformSDKReference.SDK_NAME,
						AndroidPlatformSDKReference.PATH_ANDROID_JAR),
				SDKPathReference.create(AndroidPlatformSDKReference.SDK_NAME,
						AndroidPlatformSDKReference.PATH_SOURCES)));
		if (withlambdas) {
			entries.add(new SimpleSDKPathClassPathEntry(SDKPathReference.create(AndroidBuildToolsSDKReference.SDK_NAME,
					AndroidBuildToolsSDKReference.PATH_CORE_LAMBDA_STUBS_JAR), null));
		}
	}

	@Override
	public Collection<? extends ClassPathEntry> getEntries() {
		return ImmutableUtils.unmodifiableList(entries);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, entries);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		entries = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
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
		AndroidPlatformClassPathReference other = (AndroidPlatformClassPathReference) obj;
		if (entries == null) {
			if (other.entries != null)
				return false;
		} else if (!entries.equals(other.entries))
			return false;
		return true;
	}

	private static class SimpleSDKPathClassPathEntry implements ClassPathEntry, Externalizable {
		private static final long serialVersionUID = 1L;

		private SDKPathReference inputFileSDKPathReference;
		private SDKPathReference sourceAttachmentSDKPathReference;

		/**
		 * For {@link Externalizable}.
		 */
		public SimpleSDKPathClassPathEntry() {
		}

		public SimpleSDKPathClassPathEntry(SDKPathReference inputFileSDKPathReference,
				SDKPathReference sourceAttachmentSDKPathReference) {
			this.inputFileSDKPathReference = inputFileSDKPathReference;
			this.sourceAttachmentSDKPathReference = sourceAttachmentSDKPathReference;
		}

		@SuppressWarnings("deprecation")
		@Override
		public FileLocation getFileLocation() {
			//unsupported, just return null
			return null;
		}

		@Override
		public ClassPathEntryInputFile getInputFile() {
			return ClassPathEntryInputFile.create(inputFileSDKPathReference);
		}

		@Override
		public Collection<? extends ClassPathReference> getAdditionalClassPathReferences() {
			return Collections.emptyList();
		}

		@Override
		public Collection<? extends JavaSourceDirectory> getSourceDirectories() {
			return Collections.emptyList();
		}

		@Override
		public StructuredTaskResult getSourceAttachment() {
			if (sourceAttachmentSDKPathReference == null) {
				return null;
			}
			return LiteralStructuredTaskResult.create(ClassPathEntryInputFile.create(sourceAttachmentSDKPathReference));
		}

		@Override
		public Object getAbiVersionKey() {
			return null;
		}

		@Override
		public Object getImplementationVersionKey() {
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(inputFileSDKPathReference);
			out.writeObject(sourceAttachmentSDKPathReference);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			inputFileSDKPathReference = (SDKPathReference) in.readObject();
			sourceAttachmentSDKPathReference = (SDKPathReference) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((inputFileSDKPathReference == null) ? 0 : inputFileSDKPathReference.hashCode());
			result = prime * result
					+ ((sourceAttachmentSDKPathReference == null) ? 0 : sourceAttachmentSDKPathReference.hashCode());
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
			SimpleSDKPathClassPathEntry other = (SimpleSDKPathClassPathEntry) obj;
			if (inputFileSDKPathReference == null) {
				if (other.inputFileSDKPathReference != null)
					return false;
			} else if (!inputFileSDKPathReference.equals(other.inputFileSDKPathReference))
				return false;
			if (sourceAttachmentSDKPathReference == null) {
				if (other.sourceAttachmentSDKPathReference != null)
					return false;
			} else if (!sourceAttachmentSDKPathReference.equals(other.sourceAttachmentSDKPathReference))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[inputFileSDKPathReference=" + inputFileSDKPathReference
					+ ", sourceAttachmentSDKPathReference=" + sourceAttachmentSDKPathReference + "]";
		}
	}
}
