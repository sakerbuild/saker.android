package saker.android.main.aapt2.option;

import java.util.Locale;

import saker.android.impl.aapt2.link.AAPT2LinkerFlag;
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
public class AAPT2OutputFormatTaskOption {
	private AAPT2LinkerFlag flag;

	public AAPT2OutputFormatTaskOption(AAPT2LinkerFlag flag) {
		this.flag = flag;
	}

	public AAPT2LinkerFlag getFlag() {
		return flag;
	}

	public static AAPT2OutputFormatTaskOption valueOf(String s) {
		String lc = s.toLowerCase(Locale.ENGLISH);
		switch (lc) {
			case "sharedlibrary": {
				return new AAPT2OutputFormatTaskOption(AAPT2LinkerFlag.SHARED_LIB);
			}
			case "staticlibrary": {
				return new AAPT2OutputFormatTaskOption(AAPT2LinkerFlag.STATIC_LIB);
			}
			case "protobuf": {
				return new AAPT2OutputFormatTaskOption(AAPT2LinkerFlag.PROTO_FORMAT);
			}
			default: {
				throw new IllegalArgumentException("Unrecognized AAPT2 output format: " + s);
			}
		}
	}
}
