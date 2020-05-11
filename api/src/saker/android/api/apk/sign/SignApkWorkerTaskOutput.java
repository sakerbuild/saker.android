package saker.android.api.apk.sign;

import saker.build.file.path.SakerPath;

/**
 * Output of the APK signer task.
 */
public interface SignApkWorkerTaskOutput {
	/**
	 * Gets the path to the output signed APK.
	 * 
	 * @return The absolute execution path.
	 */
	public SakerPath getPath();
}
