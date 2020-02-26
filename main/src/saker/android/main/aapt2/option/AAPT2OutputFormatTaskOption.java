package saker.android.main.aapt2.option;

import java.util.Locale;

import saker.android.impl.aapt2.link.AAPT2LinkerFlag;

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
