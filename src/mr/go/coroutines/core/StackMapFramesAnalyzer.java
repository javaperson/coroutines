/*
 * Copyright [2010] [Marcin Rzeźnicki]

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
package mr.go.coroutines.core;

import static mr.go.coroutines.core.StringConstants.FRAME_NAME;

import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class StackMapFramesAnalyzer {

	private static Object getFrameOpcode(Type t) {
		if (t == null) {
			return new Label();
		}
		int typeSort = t.getSort();
		switch (typeSort) {
		case Type.ARRAY:
		case Type.OBJECT:
			return t.getInternalName();
		case Type.BOOLEAN:
		case Type.BYTE:
		case Type.CHAR:
		case Type.INT:
		case Type.SHORT:
			return Opcodes.INTEGER;
		case Type.DOUBLE:
			return Opcodes.DOUBLE;
		case Type.FLOAT:
			return Opcodes.FLOAT;
		case Type.LONG:
			return Opcodes.LONG;
		case Type.VOID:
			return Opcodes.NULL;
		default:
			throw new CoroutineGenerationException("Invalid frame opcode");
		}
	}

	private static Type getTypeFromFrameOpcode(Object opcode) {
		if (opcode instanceof String) {
			return Type.getObjectType((String) opcode);
		}
		if (opcode instanceof Integer) {
			if (opcode == Opcodes.INTEGER) {
				return Type.INT_TYPE;
			}
			if (opcode == Opcodes.FLOAT) {
				return Type.FLOAT_TYPE;
			}
			if (opcode == Opcodes.LONG) {
				return Type.LONG_TYPE;
			}
			if (opcode == Opcodes.DOUBLE) {
				return Type.DOUBLE_TYPE;
			}
			if (opcode == Opcodes.NULL) {
				return Type.VOID_TYPE;
			}
		}
		if (opcode instanceof Label) {
			return null;
		}
		throw new CoroutineGenerationException("Invalid frame opcode");
	}

	private final Object[]				firstStackMap;

	private Object[]					oldLocals;

	private final Deque<Type>			stack;

	private int							stackMapTop;

	private final boolean				staticMethod;

	private final List<VariableInfo>	variables;

	StackMapFramesAnalyzer(
			List<VariableInfo> variables,
			Deque<Type> stack,
			String methodOwner,
			boolean staticMethod,
			int argsLength) {
		this.staticMethod = staticMethod;
		this.variables = variables;
		this.stack = stack;
		stackMapTop = variables.size();
		int localsStartIndex;
		if (staticMethod) {
			firstStackMap = new Object[5];
			firstStackMap[0] = FRAME_NAME;
			firstStackMap[1] = "java/lang/Object";
			firstStackMap[2] = "java/lang/Object";
			firstStackMap[3] = Opcodes.INTEGER;
			firstStackMap[4] = "[Ljava/lang/Object;";
			localsStartIndex = 0;
		} else {
			firstStackMap = new Object[6];
			firstStackMap[0] = methodOwner;
			firstStackMap[1] = FRAME_NAME;
			firstStackMap[2] = "java/lang/Object";
			firstStackMap[3] = "java/lang/Object";
			firstStackMap[4] = Opcodes.INTEGER;
			firstStackMap[5] = "[Ljava/lang/Object;";
			localsStartIndex = 1;
		}
		oldLocals = new Object[argsLength];
		for (int index = 0, varIndex = localsStartIndex; index < argsLength; index++, varIndex++) {
			VariableInfo varInfo = variables.get(varIndex);
			Object opcode = getFrameOpcode(varInfo.getType());
			oldLocals[index] = opcode;
			if (opcode == Opcodes.LONG || opcode == Opcodes.DOUBLE) {
				varIndex += 1;
			}
		}
	}

	void emitCatchFrame(String exception, MethodVisitor mv) {
		if (oldLocals.length == 0) {
			mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]
			{ exception });
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					firstStackMap.length,
					firstStackMap,
					1,
					new Object[]
					{ exception });
		}
	}

	void emitCleanFrame(MethodVisitor mv) {
		int nLocals = oldLocals.length;
		if (nLocals == 0) {
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		} else if (nLocals <= 3) {
			mv.visitFrame(Opcodes.F_CHOP, nLocals, null, 0, null);
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					firstStackMap.length,
					firstStackMap,
					0,
					null);
		}
	}

	void emitFullFrame(MethodVisitor mv) {
		if (oldLocals.length == 0 && stack.isEmpty()) {
			mv.visitFrame(
					Opcodes.F_FULL,
					firstStackMap.length,
					firstStackMap,
					0,
					null);
		} else {
			mergeFrames(mv);
		}
	}

	void emitOldFrame(MethodVisitor mv) {
		int nLocals = oldLocals.length;
		if (nLocals <= 3 && stack.isEmpty()) {
			if (nLocals == 0) {
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			} else {
				mv.visitFrame(Opcodes.F_APPEND, nLocals, oldLocals, 0, null);
			}
			return;
		}
		if (nLocals == 0 && stack.size() == 1) {
			mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]
			{ getFrameOpcode(stack.peek()) });
		}
		mergeFrames(mv);
	}

	int getStackMapTop() {
		return stackMapTop;
	}

	void nextFrame(
			int type,
			int nLocals,
			Object[] locals,
			int nStack,
			Object[] stack) {
		switch (type) {
		case Opcodes.F_FULL:
			// seek uninitialized variables in block marked by frame to
			// reduce copying effort and mark remaining variables as final
			int varIndex = 0;
			for (int localIndex = 0; localIndex < nLocals; localIndex++, varIndex++) {
				Object opcode = locals[localIndex];
				boolean uninitialized = opcode == Opcodes.TOP;
				VariableInfo varInfo = variables.get(varIndex);
				if (varInfo != null) {
					if (uninitialized) {
						varInfo.setInitialized(false);
					}
				} else if (!uninitialized) {
					// set temporary variable
					variables.set(varIndex, new VariableInfo(
							getTypeFromFrameOpcode(opcode)));
				}
				// move through category 2 variables
				if (opcode == Opcodes.LONG || opcode == Opcodes.DOUBLE) {
					varIndex += 1;
				}
			}
			// next we recreate stack for this block
			this.stack.clear();
			for (int i = nStack - 1; i >= 0; i--) {
				Object stackOpcode = stack[i];
				this.stack.push(getTypeFromFrameOpcode(stackOpcode));
			}
			stackMapTop = varIndex;
			if (staticMethod) {
				oldLocals = Arrays.copyOf(locals, nLocals);
			} else {
				oldLocals = Arrays.copyOfRange(locals, 1, nLocals);
			}
			// variables not introduced in full frame are marked uninitialized
			dropLocals();
			break;
		case Opcodes.F_CHOP:
			// we mark chopped variables as uninitialized to reduce copying
			// effort
			for (int i = 0, j = oldLocals.length - 1; i < nLocals; i++, j--) {
				Object old = oldLocals[j];
				if (old == Opcodes.LONG || old == Opcodes.DOUBLE) {
					stackMapTop -= 2;
				} else {
					stackMapTop -= 1;
				}
			}
			oldLocals = Arrays.copyOf(oldLocals, oldLocals.length - nLocals);
			dropLocals();
			break;
		case Opcodes.F_SAME1:
			this.stack.clear();
			this.stack.push(getTypeFromFrameOpcode(stack[0]));
			dropLocals();
			break;
		case Opcodes.F_NEW:
			throw new CoroutineGenerationException(
					"Expanded frames not allowed");
		case Opcodes.F_APPEND:
			oldLocals = Arrays.copyOf(oldLocals, oldLocals.length + nLocals);
			for (int i = 0, j = oldLocals.length - nLocals; i < nLocals; i++, j++) {
				Object newLocal = locals[i];
				if (newLocal == Opcodes.LONG || newLocal == Opcodes.DOUBLE) {
					stackMapTop += 2;
				} else {
					stackMapTop += 1;
				}
				oldLocals[j] = newLocal;
			}
			dropLocals();
			break;
		case Opcodes.F_SAME:
			dropLocals();
			break;
		}
	}

	private void dropLocals() {
		if (stackMapTop >= variables.size()) {
			return;
		}
		Iterator<VariableInfo> varIterator = variables
				.listIterator(stackMapTop);
		while (varIterator.hasNext()) {
			VariableInfo var = varIterator.next();
			if (var != null) {
				var.setInitialized(false);
			}
		}
	}

	private void mergeFrames(MethodVisitor mv) {
		int nLocals = oldLocals.length + firstStackMap.length;
		Object[] newLocals = new Object[nLocals];
		System.arraycopy(firstStackMap, 0, newLocals, 0, firstStackMap.length);
		System.arraycopy(
				oldLocals,
				0,
				newLocals,
				firstStackMap.length,
				oldLocals.length);
		if (stack.isEmpty()) {
			mv.visitFrame(Opcodes.F_FULL, nLocals, newLocals, 0, null);
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					nLocals,
					newLocals,
					stack.size(),
					stackContents());
		}
	}

	private Object[] stackContents() {
		Object[] result = stack.toArray();
		int top = result.length;
		for (int i = 0; i < top; i++) {
			result[i] = getFrameOpcode((Type) result[i]);
		}
		// reverse result (java verifier expects this)
		for (int i = 0, j = top - 1; i < top / 2; i++, j--) {
			Object tmp = result[i];
			result[i] = result[j];
			result[j] = tmp;
		}
		return result;
	}
}
