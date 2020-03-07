package saker.android.main.apk.create.option;

import saker.android.api.aapt2.link.AAPT2LinkTaskOutput;
import saker.build.file.path.SakerPath;

public abstract class ApkResourcesTaskOption {
	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(SakerPath path);

		public void visit(AAPT2LinkTaskOutput linkoutput);
	}

	public static ApkResourcesTaskOption valueOf(AAPT2LinkTaskOutput aaptoutput) {
		return new ApkResourcesTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(aaptoutput);
			}
		};
	}

	public static ApkResourcesTaskOption valueOf(SakerPath resapkpath) {
		return new ApkResourcesTaskOption() {
			@Override
			public void accept(Visitor visitor) {
				visitor.visit(resapkpath);
			}
		};
	}
}
