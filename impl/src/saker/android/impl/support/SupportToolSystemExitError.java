package saker.android.impl.support;

public class SupportToolSystemExitError extends Error {
	private static final long serialVersionUID = 1L;
	private int exitCode;

	public SupportToolSystemExitError(int exitCode) {
		this.exitCode = exitCode;
	}

	public int getExitCode() {
		return exitCode;
	}
}
