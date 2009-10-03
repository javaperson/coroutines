package mr.go.coroutines.core;

import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.Label;
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

final class InstructionGenerationUtils {

	public static void callNext(String coIteratorInternalName, MethodVisitor mv) {
		// locals +0, stack 1
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				coIteratorInternalName,
				"next",
				CO_ITERATOR_NEXT_DESCRIPTOR);
		mv.visitInsn(Opcodes.POP);
	}

	public static void chkclosed(MethodVisitor mv) {
		// locals +0, stack 2
		mv.visitInsn(Opcodes.ICONST_M1);
		Label exitBranch = new Label();
		mv.visitJumpInsn(Opcodes.IF_ICMPNE, exitBranch);
		throwex(EXCEPTION_CLOSED, mv);
		mv.visitLabel(exitBranch);
	}

	public static void coIterator(
			int frameIndex,
			String coIteratorInternalName,
			MethodVisitor mv) {
		// locals +0, stack 3
		mv.visitTypeInsn(Opcodes.NEW, coIteratorInternalName);
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				coIteratorInternalName,
				"<init>",
				CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR);
	}

	public static void createDebugFrame(
			int maxVariables,
			String[] variableNames,
			MethodVisitor mv) {
		// locals +0, stack 7
		mv.visitTypeInsn(Opcodes.NEW, FRAME_NAME);
		mv.visitInsn(Opcodes.DUP);
		makeInt(maxVariables, mv);
		makeInt(variableNames.length, mv);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, STRING);
		for (int i = 0; i < variableNames.length; i++) {
			mv.visitInsn(Opcodes.DUP);
			makeInt(i, mv);
			String name = variableNames[i];
			if (name != null) {
				mv.visitLdcInsn(name);
			} else {
				mv.visitInsn(Opcodes.ACONST_NULL);
			}
			mv.visitInsn(Opcodes.AASTORE);
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				FRAME_NAME,
				"<init>",
				DEBUG_FRAME_CONSTRUCTOR_DESCRIPTOR);
	}

	public static void createFrame(int maxVariables, MethodVisitor mv) {
		// locals +0, stack 3
		mv.visitTypeInsn(Opcodes.NEW, FRAME_NAME);
		mv.visitInsn(Opcodes.DUP);
		makeInt(maxVariables, mv);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				FRAME_NAME,
				"<init>",
				FRAME_CONSTRUCTOR_DESCRIPTOR);
	}

	public static int getloc(
			int frameArrayIndex,
			int varIndex,
			int logicalVarIndex,
			Type type,
			MethodVisitor mv) {
		// locals +1, stack 2
		int nextVarIndex = varIndex;
		int typeSort = type.getSort();
		if (typeSort == Type.VOID) {
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitVarInsn(Opcodes.ASTORE, varIndex);
		} else {
			mv.visitVarInsn(Opcodes.ALOAD, frameArrayIndex);
			makeInt(logicalVarIndex, mv);
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
				if (!type.equals(_JAVA_LANG_OBJECT)) {
					mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
				}
				mv.visitVarInsn(Opcodes.ASTORE, varIndex);
				break;
			}
		}
		nextVarIndex += type.getSize();
		return nextVarIndex;
	}

	public static void getlocs(
			int frameArrayIndex,
			int startIndex,
			int logicalStartIndex,
			int varsLength,
			List<VariableInfo> variables,
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
			variables.add(varIndex, new VariableInfo(
					logicalStartIndex,
					type,
					true));
			varIndex = getloc(
					frameArrayIndex,
					varIndex,
					logicalStartIndex++,
					type,
					mv);
			i++;
		}
	}

	public static void getlocs(
			int frameArrayIndex,
			ListIterator<VariableInfo> varIterator,
			MethodVisitor mv) {
		int varIndex;
		while (varIterator.hasNext()) {
			varIndex = varIterator.nextIndex();
			VariableInfo varInfo = varIterator.next();
			if (varInfo != null && varInfo.isInitialized()) {
				getloc(
						frameArrayIndex,
						varIndex,
						varInfo.getLogicalIndex(),
						varInfo.getType(),
						mv);
			}
		}
	}

	public static void input(int inIndex, Type type, MethodVisitor mv) {
		// locals +0, stack 0
		int typeSort = type.getSort();
		switch (typeSort) {
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			box_int(typeSort, mv);
			break;
		case Type.FLOAT:
			box_float(typeSort, mv);
			break;
		case Type.LONG:
			box_long(typeSort, mv);
			break;
		case Type.DOUBLE:
			box_double(typeSort, mv);
			break;
		case Type.ARRAY:
		case Type.OBJECT:
		case Type.VOID:
			break;
		}
		mv.visitVarInsn(Opcodes.ASTORE, inIndex);
	}

	public static void loadexitstate(int frameIndex, MethodVisitor mv) {
		// locals +0, stack 1
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"isCoroutineClosed",
				"()Z");
	}

	public static void loadline(int frameIndex, MethodVisitor mv) {
		// locals +0, stack: 2
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"getLineOfCode",
				"()I");
		box_int(Type.INT, mv);
	}

	public static void loadlocs(int frameIndex, MethodVisitor mv) {
		// locals +0, stack: 1
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"getLocals",
				FRAME_GET_LOCALS_DESCRIPTOR);
	}

	public static void loadstack(
			int frameIndex,
			Type[] stackTypes,
			MethodVisitor mv) {
		int top = stackTypes.length - 1;
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"getOperands",
				FRAME_GET_OPERANDS_DESCRIPTOR);
		for (int i = top; i >= 0; i--) {
			Type stackType = stackTypes[i];
			int typeSort = stackType.getSort();
			if (typeSort == Type.VOID) {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.SWAP);
				continue;
			}
			mv.visitInsn(Opcodes.DUP);
			// stack: array array
			makeInt(i, mv);
			mv.visitInsn(Opcodes.AALOAD);
			// stack: array element
			switch (typeSort) {
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				unbox_int(typeSort, mv);
				mv.visitInsn(Opcodes.SWAP);
				break;
			case Type.FLOAT:
				unbox_float(typeSort, mv);
				mv.visitInsn(Opcodes.SWAP);
				break;
			case Type.LONG:
				unbox_long(typeSort, mv);
				mv.visitInsn(Opcodes.DUP2_X1);
				mv.visitInsn(Opcodes.POP2);
				break;
			case Type.DOUBLE:
				unbox_double(typeSort, mv);
				mv.visitInsn(Opcodes.DUP2_X1);
				mv.visitInsn(Opcodes.POP2);
				break;
			case Type.ARRAY:
			case Type.OBJECT:
				mv
						.visitTypeInsn(Opcodes.CHECKCAST, stackType
								.getInternalName());
				mv.visitInsn(Opcodes.SWAP);
				break;
			}
			// stack: element array
		}
		mv.visitInsn(Opcodes.POP);
	}

	public static void loadstate(int frameIndex, MethodVisitor mv) {
		// locals +0, stack 1
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv
				.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						FRAME_NAME,
						"getState",
						"()I");
	}

	public static void loadthis(int frameIndex, Type thistype, MethodVisitor mv) {
		// locals +0, stack 1
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"getThis",
				FRAME_GET_THIS_DESCRIPTOR);
		mv.visitTypeInsn(Opcodes.CHECKCAST, thistype.getInternalName());
	}

	public static void logfiner(
			String ownerName,
			String loggerField,
			MethodVisitor mv,
			Object... messages) {
		// locals +0, stack 4
		log(ownerName, loggerField, "FINER", "finer", mv, messages);
	}

	public static void logfinest(
			String ownerName,
			String loggerField,
			MethodVisitor mv,
			Object... messages) {
		log(ownerName, loggerField, "FINEST", "finest", mv, messages);
	}

	public static void makeInt(int val, MethodVisitor mv) {
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

	public static void saveline(int frameIndex, int lineNumber, MethodVisitor mv) {
		// locals +0, stack: 2
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		makeInt(lineNumber, mv);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"setLineOfCode",
				"(I)V");
	}

	public static int saveloc(
			int frameArrayIndex,
			int varIndex,
			int logicalVarIndex,
			Type type,
			MethodVisitor mv) {
		// locals +0, stack 3(4)
		mv.visitVarInsn(Opcodes.ALOAD, frameArrayIndex);
		makeInt(logicalVarIndex, mv);
		int nextVarIndex = varIndex;
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
		nextVarIndex += type.getSize();
		return nextVarIndex;
	}

	public static void savelocs(
			int frameArrayIndex,
			int startIndex,
			int logicalStartIndex,
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
			varIndex = saveloc(
					frameArrayIndex,
					varIndex,
					logicalStartIndex++,
					types[i],
					mv);
			i++;
		}
	}

	public static void savelocs(
			int frameArrayIndex,
			ListIterator<VariableInfo> varIterator,
			MethodVisitor mv) {
		int varIndex;
		while (varIterator.hasNext()) {
			varIndex = varIterator.nextIndex();
			VariableInfo varInfo = varIterator.next();
			if (varInfo != null && varInfo.isInitialized()
				&& !varInfo.isFinal()) {
				saveloc(
						frameArrayIndex,
						varIndex,
						varInfo.getLogicalIndex(),
						varInfo.getType(),
						mv);
				if (!varInfo.isTemporary()) {
					varInfo.setFinal(true);
				}
			}
		}
	}

	public static int savestack(int frameIndex, Type[] stack, MethodVisitor mv) {
		int stackSize = 0;
		int top = stack.length - 1;
		int stackGrowth = 1 + stack[0].getSize();
		makeInt(stack.length, mv);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT);
		// stack: element array
		for (int i = 0; i <= top; i++) {
			Type stackType = stack[i];
			int typeSort = stackType.getSort();
			switch (typeSort) {
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				box_int(typeSort, mv);
				stackSize += 1;
				break;
			case Type.FLOAT:
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				box_float(typeSort, mv);
				stackSize += 1;
				break;
			case Type.LONG:
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
				box_long(typeSort, mv);
				stackSize += 2;
				break;
			case Type.DOUBLE:
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
				box_double(typeSort, mv);
				stackSize += 2;
				break;
			case Type.ARRAY:
			case Type.OBJECT:
			case Type.VOID:
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				stackSize += 1;
				break;
			}
			// stack: array array element
			makeInt(i, mv);
			mv.visitInsn(Opcodes.SWAP);
			// stack: array array int element
			mv.visitInsn(Opcodes.AASTORE);
		}
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitInsn(Opcodes.SWAP);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"setOperands",
				FRAME_SAVE_OPERANDS_DESCRIPTOR);
		return stackSize + stackGrowth;
	}

	public static void savestate(int frameIndex, int state, MethodVisitor mv) {
		// locals +0, stack 2
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		makeInt(state, mv);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"setState",
				"(I)V");
	}

	public static Label[] switchstate(int states, MethodVisitor mv) {
		// locals +0, stack 2
		Label defaultBranch = new Label();
		Label[] labels = new Label[states];
		Label[] gotos = new Label[states];
		for (int i = 0; i < states; i++) {
			labels[i] = new Label();
			gotos[i] = new Label();
		}
		mv.visitTableSwitchInsn(0, states - 1, defaultBranch, labels);
		for (int i = 0; i < states; i++) {
			Label li = labels[i];
			Label gotoi = gotos[i];
			mv.visitLabel(li);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			mv.visitJumpInsn(Opcodes.GOTO, gotoi);
		}
		mv.visitLabel(defaultBranch);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		throwex(EXCEPTION_INVALID, mv);
		mv.visitLabel(gotos[0]);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		return gotos;
	}

	public static void throwex(String ex, MethodVisitor mv) {
		// locals +0, stack 2
		mv.visitTypeInsn(Opcodes.NEW, ex);
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ex, "<init>", "()V");
		mv.visitInsn(Opcodes.ATHROW);
	}

	private static void box_double(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.DLOAD, varIndex);
		}
		if (typeSort != Type.DOUBLE) {
			throw new CoroutineGenerationException("box_double:Not a double");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				DOUBLE,
				"valueOf",
				"(D)Ljava/lang/Double;");
	}

	private static void box_double(int typeSort, MethodVisitor mv) {
		box_double(-1, typeSort, mv);
	}

	private static void box_float(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.FLOAD, varIndex);
		}
		if (typeSort != Type.FLOAT) {
			throw new CoroutineGenerationException("box_float:Not a float");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				FLOAT,
				"valueOf",
				"(F)Ljava/lang/Float;");
	}

	private static void box_float(int typeSort, MethodVisitor mv) {
		box_float(-1, typeSort, mv);
	}

	private static void box_int(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.ILOAD, varIndex);
		}
		switch (typeSort) {
		case Type.BOOLEAN:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					BOOLEAN,
					"valueOf",
					"(Z)Ljava/lang/Boolean;");
			break;
		case Type.CHAR:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					CHARACTER,
					"valueOf",
					"(C)Ljava/lang/Character;");
			break;
		case Type.BYTE:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					BYTE,
					"valueOf",
					"(B)Ljava/lang/Byte;");
			break;
		case Type.SHORT:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					SHORT,
					"valueOf",
					"(S)Ljava/lang/Short;");
			break;
		case Type.INT:
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					INTEGER,
					"valueOf",
					"(I)Ljava/lang/Integer;");
			break;
		default:
			throw new CoroutineGenerationException("box_int:Not an int");
		}
	}

	private static void box_int(int typeSort, MethodVisitor mv) {
		box_int(-1, typeSort, mv);
	}

	private static void box_long(int varIndex, int typeSort, MethodVisitor mv) {
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.LLOAD, varIndex);
		}
		if (typeSort != Type.LONG) {
			throw new CoroutineGenerationException("box_long:Not a long");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				LONG,
				"valueOf",
				"(J)Ljava/lang/Long;");
	}

	private static void box_long(int typeSort, MethodVisitor mv) {
		box_long(-1, typeSort, mv);
	}

	private static Label isLoggable(
			String ownerName,
			String loggerField,
			String level,
			MethodVisitor mv) {
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				ownerName,
				loggerField,
				LOGGER_DESCRIPTOR);
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				LOGGER_LEVEL_NAME,
				level,
				LOGGER_LEVEL_DESCRIPTOR);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				LOGGER_NAME,
				"isLoggable",
				IS_LOGABLE_CHECK_DESCRIPTOR);
		Label exitBranch = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, exitBranch);
		return exitBranch;
	}

	private static void log(
			String ownerName,
			String loggerField,
			String level,
			String loggingMethod,
			MethodVisitor mv,
			Object... messages) {
		Label exitBranch = isLoggable(ownerName, loggerField, level, mv);
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				ownerName,
				loggerField,
				LOGGER_DESCRIPTOR);
		makelog(mv, messages);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				LOGGER_NAME,
				loggingMethod,
				LOG_METHOD_DESCRIPTOR);
		mv.visitLabel(exitBranch);
	}

	private static void makelog(MethodVisitor mv, Object... messages) {
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		mv.visitInsn(Opcodes.DUP);
		String message0 = messages[0].toString();
		mv.visitLdcInsn(message0);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				"java/lang/StringBuilder",
				"<init>",
				"(Ljava/lang/String;)V");
		for (int m = 1; m < messages.length; m++) {
			Object message = messages[m];
			if (message instanceof Number) {
				mv.visitVarInsn(Opcodes.ALOAD, ((Number) message).intValue());
			} else {
				mv.visitLdcInsn(message.toString());
			}
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder",
					"append",
					"(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
		}
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/StringBuilder",
				"toString",
				"()Ljava/lang/String;");
	}

	private static void unbox_double(
			int varIndex,
			int typeSort,
			MethodVisitor mv) {
		if (typeSort != Type.DOUBLE) {
			throw new CoroutineGenerationException("unbox_double:Not a double");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, DOUBLE);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DOUBLE, "doubleValue", "()D");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.DSTORE, varIndex);
		}
	}

	private static void unbox_double(int typeSort, MethodVisitor mv) {
		unbox_double(-1, typeSort, mv);
	}

	private static void unbox_float(int varIndex, int typeSort, MethodVisitor mv) {
		if (typeSort != Type.FLOAT) {
			throw new CoroutineGenerationException("unbox_float:Not a float");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, FLOAT);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FLOAT, "floatValue", "()F");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.FSTORE, varIndex);
		}
	}

	private static void unbox_float(int typeSort, MethodVisitor mv) {
		unbox_float(-1, typeSort, mv);
	}

	private static void unbox_int(int varIndex, int typeSort, MethodVisitor mv) {
		switch (typeSort) {
		case Type.BOOLEAN:
			mv.visitTypeInsn(Opcodes.CHECKCAST, BOOLEAN);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					BOOLEAN,
					"booleanValue",
					"()Z");
			break;
		case Type.CHAR:
			mv.visitTypeInsn(Opcodes.CHECKCAST, CHARACTER);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					CHARACTER,
					"charValue",
					"()C");
			break;
		case Type.BYTE:
			mv.visitTypeInsn(Opcodes.CHECKCAST, BYTE);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BYTE, "byteValue", "()B");
			break;
		case Type.SHORT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, SHORT);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					SHORT,
					"shortValue",
					"()S");
			break;
		case Type.INT:
			mv.visitTypeInsn(Opcodes.CHECKCAST, INTEGER);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					INTEGER,
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

	private static void unbox_int(int typeSort, MethodVisitor mv) {
		unbox_int(-1, typeSort, mv);
	}

	private static void unbox_long(int varIndex, int typeSort, MethodVisitor mv) {
		if (typeSort != Type.LONG) {
			throw new CoroutineGenerationException("unbox_long:Not a long");
		}
		mv.visitTypeInsn(Opcodes.CHECKCAST, LONG);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LONG, "longValue", "()J");
		if (varIndex >= 0) {
			mv.visitVarInsn(Opcodes.LSTORE, varIndex);
		}
	}

	private static void unbox_long(int typeSort, MethodVisitor mv) {
		unbox_long(-1, typeSort, mv);
	}

	public static final Type	_FRAME_TYPE							= Type
																			.getType(Frame.class);

	public static final Type	_JAVA_LANG_BOOLEAN					= Type
																			.getType(Boolean.class);

	public static final Type	_JAVA_LANG_BYTE						= Type
																			.getType(Byte.class);

	public static final Type	_JAVA_LANG_CHARACTER				= Type
																			.getType(Character.class);

	public static final Type	_JAVA_LANG_DOUBLE					= Type
																			.getType(Double.class);

	public static final Type	_JAVA_LANG_FLOAT					= Type
																			.getType(Float.class);

	public static final Type	_JAVA_LANG_INTEGER					= Type
																			.getType(Integer.class);

	public static final Type	_JAVA_LANG_LONG						= Type
																			.getType(Long.class);

	public static final Type	_JAVA_LANG_OBJECT					= Type
																			.getType(Object.class);

	public static final Type	_JAVA_LANG_SHORT					= Type
																			.getType(Short.class);

	public static final Type	_JAVA_LANG_STRING					= Type
																			.getType(String.class);

	public static final Type	_LOGGER_LEVEL_TYPE					= Type
																			.getType(Level.class);

	public static final Type	_LOGGER_TYPE						= Type
																			.getType(Logger.class);

	public static final Type	_OBJECT_ARRAY						= Type
																			.getType(Object[].class);

	public static final Type	_STRING_ARRAY						= Type
																			.getType(String[].class);

	public static final String	BOOLEAN								= "java/lang/Boolean";

	public static final String	BYTE								= "java/lang/Byte";

	public static final String	CHARACTER							= "java/lang/Character";

	public static final String	CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR	= "(Lmr/go/coroutines/core/Frame;)V";

	public static final String	CO_ITERATOR_NEXT_DESCRIPTOR			= "()Ljava/lang/Object;";

	public static final String	DEBUG_FRAME_CONSTRUCTOR_DESCRIPTOR	= "(I[Ljava/lang/String;)V";

	public static final String	DOUBLE								= "java/lang/Double";

	public static final String	EXCEPTION_CLOSED					= "mr/go/coroutines/user/CoroutineClosedException";

	public static final String	EXCEPTION_EXIT						= "mr/go/coroutines/user/CoroutineExitException";

	public static final String	EXCEPTION_INVALID					= "mr/go/coroutines/user/InvalidCoroutineException";

	public static final String	EXCEPTION_RETURN					= "java/util/NoSuchElementException";

	public static final String	FLOAT								= "java/lang/Float";

	public static final String	FRAME_CONSTRUCTOR_DESCRIPTOR		= "(I)V";

	public static final String	FRAME_GET_LOCALS_DESCRIPTOR			= "()[Ljava/lang/Object;";

	public static final String	FRAME_GET_OPERANDS_DESCRIPTOR		= "()[Ljava/lang/Object;";

	public static final String	FRAME_GET_THIS_DESCRIPTOR			= "()Ljava/lang/Object;";

	public static final String	FRAME_NAME							= "mr/go/coroutines/core/Frame";

	public static final String	FRAME_SAVE_OPERANDS_DESCRIPTOR		= "([Ljava/lang/Object;)V";

	public static final String	INTEGER								= "java/lang/Integer";

	public static final String	IS_LOGABLE_CHECK_DESCRIPTOR			= "(Ljava/util/logging/Level;)Z";

	public static final String	LOG_METHOD_DESCRIPTOR				= "(Ljava/lang/String;)V";

	public static final String	LOGGER_DESCRIPTOR					= "Ljava/util/logging/Logger;";

	public static final String	LOGGER_LEVEL_DESCRIPTOR				= "Ljava/util/logging/Level;";

	public static final String	LOGGER_LEVEL_NAME					= "java/util/logging/Level";

	public static final String	LOGGER_NAME							= "java/util/logging/Logger";

	public static final String	LONG								= "java/lang/Long";

	public static final String	OBJECT								= "java/lang/Object";

	public static final String	OBJECT_ARRAY						= "[Ljava/lang/Object;";

	public static final String	SHORT								= "java/lang/Short";

	public static final String	STRING								= "java/lang/String";

}
