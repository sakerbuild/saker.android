package saker.android.api.aar;

public class AarEntryNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AarEntryNotFoundException() {
		super();
	}

	public AarEntryNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public AarEntryNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public AarEntryNotFoundException(String message) {
		super(message);
	}

	public AarEntryNotFoundException(Throwable cause) {
		super(cause);
	}
}
