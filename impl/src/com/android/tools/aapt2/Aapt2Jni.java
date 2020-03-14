// This class is commented out, as it is not used due to aapt2_jni library crashing when loaded into the JVM.
//    See AAPT2Utils.AAPT2_JNI_LIB_USAGE_ENABLED for more information.

//package com.android.tools.aapt2;
//
//import java.io.OutputStream;
//import java.nio.file.Path;
//import java.util.List;
//
//import saker.android.impl.aapt2.AAPT2Executor;
//
//public class Aapt2Jni {
//	public static native void ping();
//
//	public static AAPT2Executor init(Path dllpath) {
//		System.load(dllpath.toString());
//		ping();
//		return new AAPT2Executor() {
//			@Override
//			public int invokeAAPT2WithArguments(List<String> args, OutputStream output) throws Exception {
//				throw new UnsupportedOperationException();
//			}
//		};
//	}
//}
