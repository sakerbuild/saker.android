package saker.android.d8support;

import java.util.Set;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;

public final class SakerClassFileProgramResource extends SakerFileProgramResource {
	public SakerClassFileProgramResource(SakerPath filePath, SakerFile file, Set<String> classDescriptors) {
		super(filePath, file, classDescriptors);
	}

	@Override
	public Kind getKind() {
		return Kind.CF;
	}
}