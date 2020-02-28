package saker.android.impl.d8;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.util.file.IgnoreCaseExtensionDirectoryVisitPredicate;

public class D8InputFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final IgnoreCaseExtensionDirectoryVisitPredicate DOTCLASS_EXTENSION_DIR_VISIT_PREDICATE = new IgnoreCaseExtensionDirectoryVisitPredicate(
			".class");

	protected SakerPath directory;

	/**
	 * For {@link Externalizable}.
	 */
	public D8InputFileCollectionStrategy() {
	}

	public D8InputFileCollectionStrategy(SakerPath directory) {
		this.directory = directory;
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext directorycontext) {
		SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
		SakerFile dir = getActualBaseFile(executiondirectorycontext, workingdir, this.directory);
		if (dir == null) {
			return Collections.emptyNavigableMap();
		}
		SakerPath dirbasepath = dir.getSakerPath();
		if (dir instanceof SakerDirectory) {
			return ((SakerDirectory) dir).getFilesRecursiveByPath(dirbasepath, DOTCLASS_EXTENSION_DIR_VISIT_PREDICATE);
		}
		return ImmutableUtils.singletonNavigableMap(dirbasepath, dir);
	}

	private static SakerFile getActualBaseFile(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory workingdir, SakerPath directory) {
		SakerFile dir;
		if (directory == null) {
			dir = workingdir;
		} else if (directory.isAbsolute()) {
			dir = SakerPathFiles.resolveAtAbsolutePath(executiondirectorycontext, directory);
		} else {
			if (workingdir == null) {
				dir = null;
			} else {
				dir = SakerPathFiles.resolveAtRelativePath(workingdir, directory);
			}
		}
		return dir;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(directory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		directory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directory == null) ? 0 : directory.hashCode());
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
		D8InputFileCollectionStrategy other = (D8InputFileCollectionStrategy) obj;
		if (directory == null) {
			if (other.directory != null)
				return false;
		} else if (!directory.equals(other.directory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + directory + "]";
	}

}
