package saker.android.api.aapt2.link;

import saker.build.file.path.SakerPath;

public interface AAPT2LinkTaskOutput {
	public SakerPath getResourceAPK();

	public SakerPath getRJavaSourceDirectory();
}
