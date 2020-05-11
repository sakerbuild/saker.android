package saker.android.impl.d8;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.android.api.d8.D8WorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public final class D8TaskOutputImpl implements D8WorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<SakerPath> dexFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public D8TaskOutputImpl() {
	}

	public D8TaskOutputImpl(NavigableSet<SakerPath> dexfiles) {
		this.dexFiles = dexfiles;
	}

	@Override
	public NavigableSet<SakerPath> getDexFiles() {
		return dexFiles;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, dexFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		dexFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}
}