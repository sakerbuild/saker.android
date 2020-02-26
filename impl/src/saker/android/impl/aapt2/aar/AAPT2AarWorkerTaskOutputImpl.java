package saker.android.impl.aapt2.aar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class AAPT2AarWorkerTaskOutputImpl implements AAPT2AarWorkerTaskOutput, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<SakerPath> outputFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2AarWorkerTaskOutputImpl() {
	}

	AAPT2AarWorkerTaskOutputImpl(NavigableSet<SakerPath> outputFiles) {
		this.outputFiles = outputFiles;
	}

	@Override
	public NavigableSet<SakerPath> getOutputFiles() {
		return outputFiles;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, outputFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		outputFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((outputFiles == null) ? 0 : outputFiles.hashCode());
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
		AAPT2AarWorkerTaskOutputImpl other = (AAPT2AarWorkerTaskOutputImpl) obj;
		if (outputFiles == null) {
			if (other.outputFiles != null)
				return false;
		} else if (!outputFiles.equals(other.outputFiles))
			return false;
		return true;
	}

}