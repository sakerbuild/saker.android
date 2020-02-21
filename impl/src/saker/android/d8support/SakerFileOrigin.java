package saker.android.d8support;

import com.android.tools.r8.origin.Origin;

import saker.build.file.path.SakerPath;

public final class SakerFileOrigin extends Origin {
	private SakerPath filePath;

	public SakerFileOrigin(SakerPath filePath) {
		super(Origin.root());
		this.filePath = filePath;
	}

	@Override
	public String part() {
		return filePath.toString();
	}

	public SakerPath getFilePath() {
		return filePath;
	}
}