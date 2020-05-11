package saker.android.impl.aapt2;

import java.io.OutputStream;
import java.util.List;

public interface Aapt2Executor {
	public int invokeAapt2WithArguments(List<String> args, OutputStream output) throws Exception;
}
