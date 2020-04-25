package saker.android.main.apk.sign.option;

import java.util.Locale;

import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;

@NestInformation("Value for the --v4-signing-enabled parameter for the apksigner tool.\n"
		+ "See the apksigner documentation at https://developer.android.com/studio/command-line/apksigner for more information.")
@NestTypeInformation(enumValues = {

		@NestFieldInformation(value = "true",
				info = @NestInformation("The APK should be signed using the APK Signature Scheme v4.")),
		@NestFieldInformation(value = "false",
				info = @NestInformation("The APK should not be signed using the APK Signature Scheme v4.")),
		@NestFieldInformation(value = "only",
				info = @NestInformation("The APK should be signed only with the APK Signature Scheme v4.")),

})
public class V4SigningEnabledInputTaskOption {
	private String value;

	public V4SigningEnabledInputTaskOption(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static V4SigningEnabledInputTaskOption valueOf(boolean input) {
		return new V4SigningEnabledInputTaskOption(Boolean.toString(input));
	}

	public static V4SigningEnabledInputTaskOption valueOf(Boolean input) {
		return new V4SigningEnabledInputTaskOption(input.toString());
	}

	public static V4SigningEnabledInputTaskOption valueOf(String input) {
		String lowerinput = input.toLowerCase(Locale.ENGLISH);
		switch (lowerinput) {
			case "true":
			case "false":
			case "only": {
				return new V4SigningEnabledInputTaskOption(lowerinput);
			}
			default: {
				throw new IllegalArgumentException(
						"Invalid V4 signing option: " + input + " expected: true, false, only");
			}
		}
	}
}
