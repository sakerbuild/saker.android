package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;

public class AndroidResourcesFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	protected SakerPath directory;

	/**
	 * For {@link Externalizable}.
	 */
	public AndroidResourcesFileCollectionStrategy() {
	}

	public AndroidResourcesFileCollectionStrategy(SakerPath directory) {
		this.directory = directory;
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext taskdirectorycontext) {
		SakerDirectory workingdir = taskdirectorycontext.getTaskWorkingDirectory();
		SakerDirectory reldir = getActualDirectory(executiondirectorycontext, workingdir, this.directory);
		return reldir.getFilesRecursiveByPath(reldir.getSakerPath(), new AndroidResourcesDirectoryVisitPredicate());
	}

	private static SakerDirectory getActualDirectory(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory workingdir, SakerPath directory) {
		SakerDirectory dir;
		if (directory == null) {
			dir = workingdir;
		} else if (directory.isAbsolute()) {
			dir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, directory);
		} else {
			if (workingdir == null) {
				dir = null;
			} else {
				dir = SakerPathFiles.resolveDirectoryAtRelativePath(workingdir, directory);
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
		AndroidResourcesFileCollectionStrategy other = (AndroidResourcesFileCollectionStrategy) obj;
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

	private static final class AndroidResourcesDirectoryVisitPredicate implements DirectoryVisitPredicate {
		@Override
		public boolean visitFile(String name, SakerFile file) {
			if (name.charAt(0) == '.') {
				return false;
			}
			return true;
		}

		@Override
		public boolean visitDirectory(String name, SakerDirectory directory) {
			return false;
		}

		@Override
		public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
			return this;
		}
	}
}
