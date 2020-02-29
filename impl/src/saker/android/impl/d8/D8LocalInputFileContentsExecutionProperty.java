package saker.android.impl.d8;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.std.api.util.SakerStandardUtils;

public class D8LocalInputFileContentsExecutionProperty
		implements ExecutionProperty<NavigableMap<SakerPath, ContentDescriptor>>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;
	private Object taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public D8LocalInputFileContentsExecutionProperty() {
	}

	public D8LocalInputFileContentsExecutionProperty(SakerPath path, Object taskId) {
		this.path = path;
		this.taskId = taskId;
	}

	@Override
	public NavigableMap<SakerPath, ContentDescriptor> getCurrentValue(ExecutionContext executioncontext)
			throws Exception {
		ContentDescriptor filecd = executioncontext.getExecutionPropertyCurrentValue(
				SakerStandardUtils.createLocalFileContentDescriptorExecutionProperty(path, taskId));
		if (filecd == null) {
			return null;
		}
		if (!DirectoryContentDescriptor.INSTANCE.equals(filecd)) {
			return ImmutableUtils.singletonNavigableMap(path, filecd);
		}
		LocalFileProvider fp = LocalFileProvider.getInstance();
		NavigableMap<SakerPath, ? extends FileEntry> direntries = fp.getDirectoryEntriesRecursively(path);
		NavigableMap<SakerPath, ContentDescriptor> result = new TreeMap<>();
		//XXX the content descriptor querying should be performed in bulk!
		for (Entry<SakerPath, ? extends FileEntry> entry : direntries.entrySet()) {
			if (entry.getValue().isDirectory()) {
				continue;
			}
			SakerPath relpath = entry.getKey();
			if (!FileUtils.hasExtensionIgnoreCase(relpath.getFileName(), "class")) {
				continue;
			}
			SakerPath fullpath = path.resolve(relpath);
			ContentDescriptor cd = executioncontext.getContentDescriptor(fp.getPathKey(relpath));
			result.put(fullpath, cd);
		}
		return result;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = SerialUtils.readExternalObject(in);
		taskId = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		D8LocalInputFileContentsExecutionProperty other = (D8LocalInputFileContentsExecutionProperty) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + path + "]";
	}

}
