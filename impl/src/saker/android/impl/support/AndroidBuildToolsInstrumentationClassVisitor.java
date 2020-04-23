/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.android.impl.support;

import static saker.build.thirdparty.org.objectweb.asm.Opcodes.CHECKCAST;

import saker.build.thirdparty.org.objectweb.asm.ClassVisitor;
import saker.build.thirdparty.org.objectweb.asm.MethodVisitor;
import saker.build.thirdparty.org.objectweb.asm.Opcodes;
import saker.build.thirdparty.org.objectweb.asm.Type;
import saker.build.util.java.JavaTools;

public class AndroidBuildToolsInstrumentationClassVisitor extends ClassVisitor {
	private static final String EXITREQUEST_METHOD_ENCLOSING_CLASS_INTERNAL_NAME = Type
			.getInternalName(AndroidBuildToolsInstrumentationClassVisitor.class);

	private static final int CURRENT_JVM_MAJOR_VERSION;
	static {
		int v;
		switch (JavaTools.getCurrentJavaMajorVersion()) {
			case 1: {
				v = Opcodes.V1_1;
				break;
			}
			case 2: {
				v = Opcodes.V1_2;
				break;
			}
			case 3: {
				v = Opcodes.V1_3;
				break;
			}
			case 4: {
				v = Opcodes.V1_4;
				break;
			}
			case 5: {
				v = Opcodes.V1_5;
				break;
			}
			case 6: {
				v = Opcodes.V1_6;
				break;
			}
			case 7: {
				v = Opcodes.V1_7;
				break;
			}
			case 8: {
				v = Opcodes.V1_8;
				break;
			}
			case 9: {
				v = Opcodes.V9;
				break;
			}
			case 10: {
				v = Opcodes.V10;
				break;
			}
			case 11: {
				v = Opcodes.V11;
				break;
			}
			case 12: {
				v = Opcodes.V12;
				break;
			}
			case 13: {
				v = Opcodes.V13;
				break;
			}
			case 14: {
				v = Opcodes.V14;
				break;
			}
			default: {
				v = Opcodes.V14;
				break;
			}
		}
		CURRENT_JVM_MAJOR_VERSION = v;
	}

	private boolean appliedTransformation = false;

	public AndroidBuildToolsInstrumentationClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM7, cv);
	}

	public boolean isAppliedTransformation() {
		return appliedTransformation;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if (version > CURRENT_JVM_MAJOR_VERSION) {
			//the class file version that is being transformed is not supported by the current JVM
			//this can cause UnsupportedClassVersionErrors to be thrown
			//transform the class to use the latest supported version. any errors that this can cause will surface later
			appliedTransformation = true;
			version = CURRENT_JVM_MAJOR_VERSION;
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		return new ExitDisablerMethodVisitor(api, mv);
	}

	public static void exitRequest(int exitcode) {
		throw new SupportToolSystemExitError(exitcode);
	}

	private class ExitDisablerMethodVisitor extends MethodVisitor {

		public ExitDisablerMethodVisitor(int api, MethodVisitor methodVisitor) {
			super(api, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if ("(I)V".equals(descriptor)) {
				if ("java/lang/System".equals(owner) || "java/lang/Runtime".equals(owner)) {
					if ("halt".equals(name) || "exit".equals(name)) {
						appliedTransformation = true;
						super.visitMethodInsn(Opcodes.INVOKESTATIC, EXITREQUEST_METHOD_ENCLOSING_CLASS_INTERNAL_NAME,
								"exitRequest", "(I)V", false);

						//wrap the exit code into an InternalError exception
//						//stack: CallRef?, int
//						super.visitTypeInsn(Opcodes.NEW, "java/lang/InternalError");
//						//stack: CallRef?, int, Error
//						super.visitInsn(Opcodes.SWAP);
//						//stack: CallRef?, Error, int
//						super.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
//						//stack: CallRef?, Error, int, SB
//						super.visitInsn(Opcodes.DUP);
//						//stack: CallRef?, Error, int, SB, SB
//						super.visitLdcInsn(MESSAGE_STARTER);
//						//stack: CallRef?, Error, int, SB, SB, str
//						super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/CharSequence;)V", false);
//						//stack: CallRef?, Error, int, SB
//						super.visitInsn(Opcodes.SWAP);
//						//stack: CallRef?, Error, SB, int
//						super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
//						//stack: CallRef?, Error, SB
//						super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
//						//stack: CallRef?, Error, str
//						super.visitInsn(Opcodes.DUP2);
//						//stack: CallRef?, Error, str, Error, str
//						super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/InternalError", "<init>", "(Ljava/lang/String;)V", false);
//						//stack: CallRef?, Error, str
//						super.visitInsn(Opcodes.POP);
//						//stack: CallRef?, Error
//						super.visitInsn(Opcodes.ATHROW);
//						//stack: CallRef?

						if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
							//pop the unused instance of Runtime if present
							super.visitInsn(Opcodes.POP);
						}
						return;
					}
				}
			}
			if ("java/nio/ByteBuffer".equals(owner)) {
				//some methods were overridden in Java 9 to return ByteBuffer
				//the Java 8 version returns Buffer
				//replace the calls if necessary

				//call the method, and downcast it after
				if (CURRENT_JVM_MAJOR_VERSION <= Opcodes.V1_8) {
					if (("limit".equals(name) || "position".equals(name))
							&& "(I)Ljava/nio/ByteBuffer;".equals(descriptor)) {
						appliedTransformation = true;
						super.visitMethodInsn(opcode, owner, name, "(I)Ljava/nio/Buffer;", isInterface);
						super.visitTypeInsn(CHECKCAST, "java/nio/ByteBuffer");
						return;
					}
					if (("flip".equals(name) || "rewind".equals(name) || "clear".equals(name) || "reset".equals(name)
							|| "mark".equals(name)) && "()Ljava/nio/ByteBuffer;".equals(descriptor)) {
						appliedTransformation = true;
						super.visitMethodInsn(opcode, owner, name, "()Ljava/nio/Buffer;", isInterface);
						super.visitTypeInsn(CHECKCAST, "java/nio/ByteBuffer");
						return;
					}
				}
			}
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

	}
}