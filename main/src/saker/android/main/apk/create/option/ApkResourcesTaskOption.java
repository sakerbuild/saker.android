package saker.android.main.apk.create.option;

import saker.android.api.aapt2.link.Aapt2LinkWorkerTaskOutput;
import saker.build.file.path.SakerPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;

@NestInformation("Input resources for APK creation.")
public abstract class ApkResourcesTaskOption {
	public abstract void accept(Visitor visitor);

	public interface Visitor {
		public void visit(SakerPath path);

		public void visit(Aapt2LinkWorkerTaskOutput linkoutput);
	}

	public static ApkResourcesTaskOption valueOf(Aapt2LinkWorkerTaskOutput aaptoutput) {
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
