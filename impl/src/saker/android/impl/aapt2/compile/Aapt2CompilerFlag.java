package saker.android.impl.aapt2.compile;

public enum Aapt2CompilerFlag {
	PSEUDO_LOCALIZE("--pseudo-localize"),
	NO_CRUNCH("--no-crunch"),
	LEGACY("--legacy"),

	;
	public final String argument;

	private Aapt2CompilerFlag(String argument) {
		this.argument = argument;
	}

	@Override
	public String toString() {
		return name() + "(" + argument + ")";
	}
}
