package saker.android.d8support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;

public abstract class SakerFileProgramResource implements ProgramResource {
	protected final SakerPath filePath;
	protected final SakerFile file;
	protected final Set<String> classDescriptors;

	public SakerFileProgramResource(SakerPath filePath, SakerFile file, Set<String> classDescriptors) {
		this.filePath = filePath;
		this.file = file;
		this.classDescriptors = classDescriptors;
	}

	@Override
	public Origin getOrigin() {
		return new SakerFileOrigin(filePath);
	}

	@Override
	public Set<String> getClassDescriptors() {
		return classDescriptors;
	}

	@Override
	public InputStream getByteStream() throws ResourceException {
		try {
			return file.openInputStream();
		} catch (IOException e) {
			throw new ResourceException(getOrigin(), e);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + filePath + "]";
	}

}