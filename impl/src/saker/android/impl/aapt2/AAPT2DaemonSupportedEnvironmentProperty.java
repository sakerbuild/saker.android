package saker.android.impl.aapt2;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class AAPT2DaemonSupportedEnvironmentProperty implements EnvironmentProperty<Boolean>, Externalizable {
	private static final byte[] QUIT_COMMAND_BYTES = "quit\n\n".getBytes();

	private static final long serialVersionUID = 1L;

	private SakerPath aaptExePath;

	/**
	 * For {@link Externalizable}.
	 */
	public AAPT2DaemonSupportedEnvironmentProperty() {
	}

	public AAPT2DaemonSupportedEnvironmentProperty(SakerPath aaptExePath) {
		this.aaptExePath = aaptExePath;
	}

	@Override
	public Boolean getCurrentValue(SakerEnvironment environment) throws Exception {
		try {
			ProcessBuilder pb = new ProcessBuilder(aaptExePath.toString(), "daemon");
			pb.redirectErrorStream(true);

			Process proc = pb.start();
			try (OutputStream os = proc.getOutputStream()) {
				os.write(QUIT_COMMAND_BYTES);
			}
			StreamUtils.consumeStream(proc.getInputStream());
			if (!proc.waitFor(10, TimeUnit.SECONDS)) {
				// specify a timeout so we exit eventually if the detection doesn't work.
				proc.destroyForcibly();
				throw new IOException("Failed to wait for aapt2 daemon process.");
			}
			int res = proc.exitValue();
			return res == 0;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(aaptExePath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		aaptExePath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((aaptExePath == null) ? 0 : aaptExePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AAPT2DaemonSupportedEnvironmentProperty other = (AAPT2DaemonSupportedEnvironmentProperty) obj;
		if (aaptExePath == null) {
			if (other.aaptExePath != null)
				return false;
		} else if (!aaptExePath.equals(other.aaptExePath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + aaptExePath + "]";
	}

}
