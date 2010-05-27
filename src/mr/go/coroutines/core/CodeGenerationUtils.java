package mr.go.coroutines.core;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/*
 * Copyright [2009] [Marcin Rzeźnicki]

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
final class CodeGenerationUtils {

	static void box_double(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.DLOAD, varIndex);
		}
		if (typeSort != Type.DOUBLE) {
			throw new CoroutineGenerationException("box_double:Not a double");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Double",
				"valueOf",
				"(D)Ljava/lang/Double;");
	}

	static void box_double(int typeSort, MethodVisitor mv) {
		box_double(-1, typeSort, mv);
	}

	static void box_float(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.FLOAD, varIndex);
		}
		if (typeSort != Type.FLOAT) {
			throw new CoroutineGenerationException("box_float:Not a float");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Float",
				"valueOf",
				"(F)Ljava/lang/Float;");
	}

	static void box_float(int typeSort, MethodVisitor mv) {
		box_float(-1, typeSort, mv);
	}

	static void box_int(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.ILOAD, varIndex);
		}
		switch (typeSort) {
		case Type.BOOLEAN:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					"java/lang/Boolean",
					"valueOf",
					"(Z)Ljava/lang/Boolean;");
			break;
		case Type.CHAR:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					"java/lang/Character",
					"valueOf",
					"(C)Ljava/lang/Character;");
			break;
		case Type.BYTE:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					"java/lang/Byte",
					"valueOf",
					"(B)Ljava/lang/Byte;");
			break;
		case Type.SHORT:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					"java/lang/Short",
					"valueOf",
					"(S)Ljava/lang/Short;");
			break;
		case Type.INT:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					"java/lang/Integer",
					"valueOf",
					"(I)Ljava/lang/Integer;");
			break;
		default:
			throw new CoroutineGenerationException("box_int:Not an int");
		}
	}

	static void box_int(int typeSort, MethodVisitor mv) {
		box_int(-1, typeSort, mv);
	}

	static void box_long(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.LLOAD, varIndex);
		}
		if (typeSort != Type.LONG) {
			throw new CoroutineGenerationException("box_long:Not a long");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Long",
				"valueOf",
				"(J)Ljava/lang/Long;");
	}

	static void box_long(int typeSort, MethodVisitor mv) {
		box_long(-1, typeSort, mv);
	}

	static void getloc(
			int frameArrayIndex,
			int varIndex,
			int fromIndex,
			Type type,
			MethodVisitor mv) {
		int typeSort = type.getSort();
		if (typeSort == Type.VOID) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitVarInsn(Opcodes.ASTORE, varIndex);
		} else {
			mv.visitVarInsn(Opcodes.ALOAD, frameArrayIndex);
			makeInt(fromIndex, mv);
			mv.visitInsn(Opcodes.AALOAD);
			switch (typeSort) {
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				unbox_int(varIndex, typeSort, mv);
				break;
			case Type.FLOAT:
				unbox_float(varIndex, typeSort, mv);
				break;
			case Type.LONG:
				unbox_long(varIndex, typeSort, mv);
				break;
			case Type.DOUBLE:
				unbox_double(varIndex, typeSort, mv);
				break;
			case Type.ARRAY:
			case Type.OBJECT:
				if (!type.equals(Types.JAVA_LANG_OBJECT)) {
					mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
				}
				mv.visitVarInsn(Opcodes.ASTORE, varIndex);
				break;
			}
		}
	}

	static void getlocs(
			int frameArrayIndex,
			int startIndex,
			int fromIndex,
			int varsLength,
			MethodVisitor mv,
			Type... types) {
		int varIndex = startIndex;
		int i = 0;
		while (i < varsLength) {
			if (i >= types.length) {
				throw new CoroutineGenerationException(
						"Invalid call to get locals (types do not match)");
			}
			Type type = types[i];
			getloc(frameArrayIndex, varIndex, fromIndex, type, mv);
			varIndex += type.getSize();
			fromIndex += type.getSize();
			i++;
		}
	}

	static void makeInt(int val, MethodVisitor mv) {
		switch (val) {
		case 0:
			mv.visitInsn(Opcodes.ICONST_0);
			break;
		case 1:
			mv.visitInsn(Opcodes.ICONST_1);
			break;
		case 2:
			mv.visitInsn(Opcodes.ICONST_2);
			break;
		case 3:
			mv.visitInsn(Opcodes.ICONST_3);
			break;
		case 4:
			mv.visitInsn(Opcodes.ICONST_4);
			break;
		case 5:
			mv.visitInsn(Opcodes.ICONST_5);
			break;
		default:
			if (val <= Byte.MAX_VALUE) {
				mv.visitIntInsn(Opcodes.BIPUSH, val);
			} else if (val <= Short.MAX_VALUE) {
				mv.visitIntInsn(Opcodes.SIPUSH, val);
			} else {
				mv.visitLdcInsn(Integer.valueOf(val));
			}
		}
	}

	static void newInstance(Type type, Type asType, MethodVisitor mv) {
		mv.visitLdcInsn(type.getClassName());
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Class",
				"forName",
				"(Ljava/lang/String;)Ljava/lang/Class;");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Class",
				"newInstance",
				"()Ljava/lang/Object;");
		mv.visitTypeInsn(Opcodes.CHECKCAST, asType.getInternalName());
	}

	static void saveloc(
			int frameArrayIndex,
			int varIndex,
			int toIndex,
			Type type,
			MethodVisitor mv) {
		mv.visitVarInsn(Opcodes.ALOAD, frameArrayIndex);
		makeInt(toIndex, mv);
		int typeSort = type.getSort();
		switch (typeSort) {
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			box_int(varIndex, typeSort, mv);
			break;
		case Type.FLOAT:
			box_float(varIndex, typeSort, mv);
			break;
		case Type.LONG:
			box_long(varIndex, typeSort, mv);
			break;
		case Type.DOUBLE:
			box_double(varIndex, typeSort, mv);
			break;
		case Type.ARRAY:
		case Type.OBJECT:
			mv.visitVarInsn(Opcodes.ALOAD, varIndex);
			break;
		case Type.VOID:
			mv.visitInsn(Opcodes.ACONST_NULL);
			break;
		}
		mv.visitInsn(Opcodes.AASTORE);
	}

	static void savelocs(
			int frameArrayIndex,
			int startIndex,
			int toIndex,
			int varsLength,
			MethodVisitor mv,
			Type... types) {
		int varIndex = startIndex;
		int i = 0;
		while (i < varsLength) {
			if (i >= types.length) {
				throw new CoroutineGenerationException(
						"Invalid call to save locals (types do not match)");
			}
			Type type = types[i];
			saveloc(frameArrayIndex, varIndex, toIndex, type, mv);
			varIndex += type.getSize();
			toIndex += type.getSize();
			i++;
		}
	}

	static void throwex(String ex, MethodVisitor mv) {
		mv.visitTypeInsn(Opcodes.NEW, ex);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ex, "<init>", "()V");
		mv.visitInsn(Opcodes.ATHROW);
	}

	static void unbox_double(int varIndex, int typeSort, MethodVisitor mv) {
		if (typeSort != Type.DOUBLE) {
			throw new CoroutineGenerationException("unbox_double:Not a double");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Double",
				"doubleValue",
				"()D");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.DSTORE, varIndex);
		}
	}

	static void unbox_double(int typeSort, MethodVisitor mv) {
		unbox_double(-1, typeSort, mv);
	}

	static void unbox_float(int varIndex, int typeSort, MethodVisitor mv) {
		if (typeSort != Type.FLOAT) {
			throw new CoroutineGenerationException("unbox_float:Not a float");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Float",
				"floatValue",
				"()F");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.FSTORE, varIndex);
		}
	}

	static void unbox_float(int typeSort, MethodVisitor mv) {
		unbox_float(-1, typeSort, mv);
	}

	static void unbox_int(int varIndex, int typeSort, MethodVisitor mv) {
		switch (typeSort) {
		case Type.BOOLEAN:
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/Boolean",
					"booleanValue",
					"()Z");
			break;
		case Type.CHAR:
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/Character",
					"charValue",
					"()C");
			break;
		case Type.BYTE:
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/Byte",
					"byteValue",
					"()B");
			break;
		case Type.SHORT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/Short",
					"shortValue",
					"()S");
			break;
		case Type.INT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/Integer",
					"intValue",
					"()I");
			break;
		default:
			throw new CoroutineGenerationException("unbox_int:Not an int");
		}
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.ISTORE, varIndex);
		}
	}

	static void unbox_int(int typeSort, MethodVisitor mv) {
		unbox_int(-1, typeSort, mv);
	}

	static void unbox_long(int varIndex, int typeSort, MethodVisitor mv) {
		if (typeSort != Type.LONG) {
			throw new CoroutineGenerationException("unbox_long:Not a long");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Long",
				"longValue",
				"()J");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.LSTORE, varIndex);
		}
	}

	static void unbox_long(int typeSort, MethodVisitor mv) {
		unbox_long(-1, typeSort, mv);
	}
}
