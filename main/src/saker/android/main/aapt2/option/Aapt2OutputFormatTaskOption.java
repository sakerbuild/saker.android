package saker.android.main.aapt2.option;

import java.util.Locale;

import saker.android.impl.aapt2.link.Aapt2LinkerFlag;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;

@NestInformation("Represents the output format of the aapt2 link operation.")
@NestTypeInformation(enumValues = {

		@NestFieldInformation(value = "SharedLibrary",
				info = @NestInformation("Generates a shared Android runtime library.\n"
						+ "Corresponds to the --shared-lib flag of aapt2.")),
		@NestFieldInformation(value = "StaticLibrary",
				info = @NestInformation("Generate a static Android library.\n"
						+ "Corresponds to the --shared-lib flag of aapt2.")),
		@NestFieldInformation(value = "ProtoBuf",
				info = @NestInformation("Generates compiled resources in protocol buffer format.\n"
						+ "Suitable as input to the bundle tool for generating an App Bundle.\n"
						+ "Corresponds to the --proto-format flag of aapt2.")),

})
public class Aapt2OutputFormatTaskOption {
	private Aapt2LinkerFlag flag;

	public Aapt2OutputFormatTaskOption(Aapt2LinkerFlag flag) {
		this.flag = flag;
	}

	public Aapt2LinkerFlag getFlag() {
		return flag;
	}

	public static Aapt2OutputFormatTaskOption valueOf(String s) {
		String lc = s.toLowerCase(Locale.ENGLISH);
		switch (lc) {
			case "sharedlibrary": {
				return new Aapt2OutputFormatTaskOption(Aapt2LinkerFlag.SHARED_LIB);
			}
			case "staticlibrary": {
				return new Aapt2OutputFormatTaskOption(Aapt2LinkerFlag.STATIC_LIB);
			}
			case "protobuf": {
				return new Aapt2OutputFormatTaskOption(Aapt2LinkerFlag.PROTO_FORMAT);
			}
			default: {
				throw new IllegalArgumentException("Unrecognized AAPT2 output format: " + s);
			}
		}
	}
}
