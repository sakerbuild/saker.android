package saker.android.impl.aapt2.link;

public enum AAPT2LinkerFlag {
	ALLOW_RESERVED_PACKAGE_ID("--allow-reserved-package-id"),
	PROGUARD_CONDITIONAL_KEEP_RULES("--proguard-conditional-keep-rules"),
	PROGUARD_MINIMAL_KEEP_RULES("--proguard-minimal-keep-rules"),
	NO_AUTO_VERSION("--no-auto-version"),
	NO_VERSION_VECTORS("--no-version-vectors"),
	NO_VERSION_TRANSITIONS("--no-version-transitions"),
	NO_RESOURCE_DEDUPING("--no-resource-deduping"),
	NO_RESOURCE_REMOVAL("--no-resource-removal"),
	ENABLE_SPARSE_ENCODING("--enable-sparse-encoding"),
	REQUIRE_SUGGESTED_LOCALIZATION("-z"),
	NO_XML_NAMESPACES("--no-xml-namespaces"),
	REPLACE_VERSION("--replace-version"),
	SHARED_LIB("--shared-lib"),
	STATIC_LIB("--static-lib"),
	PROTO_FORMAT("--proto-format"),
	NO_STATIC_LIB_PACKAGES("--no-static-lib-packages"),
	NON_FINAL_IDS("--non-final-ids"),
	AUTO_ADD_OVERLAY("--auto-add-overlay"),
	NO_COMPRESS("--no-compress"),
	KEEP_RAW_VALUES("--keep-raw-values"),
	WARN_MANIFEST_VALIDATION("--warn-manifest-validation"),
	DEBUG_MODE("--debug-mode"),
	STRICT_VISIBILITY("--strict-visibility"),

	;

	public final String argument;

	private AAPT2LinkerFlag(String argument) {
		this.argument = argument;
	}

	@Override
	public String toString() {
		return name() + "(" + argument + ")";
	}
}
