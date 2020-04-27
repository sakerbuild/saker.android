package saker.android.main.apk.create.option;

import saker.android.api.d8.D8TaskOutput;
import saker.build.file.path.SakerPath;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.std.api.file.location.ExecutionFileLocation;
import saker.zip.api.create.ZipCreationTaskBuilder;

@NestInformation("Input Java classes in dex format.")
public abstract class ApkClassesTaskOption {
	public abstract void applyTo(ZipCreationTaskBuilder zipbuilder);

	public static ApkClassesTaskOption valueOf(D8TaskOutput d8output) {
		return new ApkClassesTaskOption() {
			@Override
			public void applyTo(ZipCreationTaskBuilder zipbuilder) {
				for (SakerPath dexfile : d8output.getDexFiles()) {
					zipbuilder.addResource(ExecutionFileLocation.create(dexfile),
							SakerPath.valueOf(dexfile.getFileName()));
				}
			}
		};
	}
}
