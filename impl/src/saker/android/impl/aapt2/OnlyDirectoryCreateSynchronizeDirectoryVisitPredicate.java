package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;

public final class OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate
		implements DirectoryVisitPredicate, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate INSTANCE = new OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate();

	/**
	 * For {@link Externalizable}.
	 */
	public OnlyDirectoryCreateSynchronizeDirectoryVisitPredicate() {
	}

	@Override
	public boolean visitFile(String name, SakerFile file) {
		return false;
	}

	@Override
	public boolean visitDirectory(String name, SakerDirectory directory) {
		return false;
	}

	@Override
	public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
		return null;
	}

	@Override
	public NavigableSet<String> getSynchronizeFilesToKeep() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}