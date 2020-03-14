package saker.android.impl.aapt2;

import java.io.OutputStream;
import java.util.List;

public interface AAPT2Executor {
	public int invokeAAPT2WithArguments(List<String> args, OutputStream output) throws Exception;
}
