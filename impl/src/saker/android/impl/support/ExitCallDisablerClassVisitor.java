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

import saker.build.thirdparty.org.objectweb.asm.ClassVisitor;
import saker.build.thirdparty.org.objectweb.asm.MethodVisitor;
import saker.build.thirdparty.org.objectweb.asm.Opcodes;
import saker.build.thirdparty.org.objectweb.asm.Type;

public class ExitCallDisablerClassVisitor extends ClassVisitor {
	private static final String EXITREQUEST_METHOD_ENCLOSING_CLASS_INTERNAL_NAME = Type
			.getInternalName(ExitCallDisablerClassVisitor.class);

	public ExitCallDisablerClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM7, cv);
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
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, EXITREQUEST_METHOD_ENCLOSING_CLASS_INTERNAL_NAME,
								"exitRequest", "(I)V", false);

						//wrap the exit code into an InternalError exception
//						//stack: CallRef?, int
//						mv.visitTypeInsn(Opcodes.NEW, "java/lang/InternalError");
//						//stack: CallRef?, int, Error
//						mv.visitInsn(Opcodes.SWAP);
//						//stack: CallRef?, Error, int
//						mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
//						//stack: CallRef?, Error, int, SB
//						mv.visitInsn(Opcodes.DUP);
//						//stack: CallRef?, Error, int, SB, SB
//						mv.visitLdcInsn(MESSAGE_STARTER);
//						//stack: CallRef?, Error, int, SB, SB, str
//						mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/CharSequence;)V", false);
//						//stack: CallRef?, Error, int, SB
//						mv.visitInsn(Opcodes.SWAP);
//						//stack: CallRef?, Error, SB, int
//						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
//						//stack: CallRef?, Error, SB
//						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
//						//stack: CallRef?, Error, str
//						mv.visitInsn(Opcodes.DUP2);
//						//stack: CallRef?, Error, str, Error, str
//						mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/InternalError", "<init>", "(Ljava/lang/String;)V", false);
//						//stack: CallRef?, Error, str
//						mv.visitInsn(Opcodes.POP);
//						//stack: CallRef?, Error
//						mv.visitInsn(Opcodes.ATHROW);
//						//stack: CallRef?

						if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
							//pop the unused instance of Runtime if present
							mv.visitInsn(Opcodes.POP);
						}
						return;
					}
				}
			}
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

	}
}