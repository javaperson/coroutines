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
package mr.go.coroutines.core;

import static mr.go.coroutines.core.CodeGenerationUtils.*;
import static mr.go.coroutines.core.StringConstants.COROUTINES_NAME;
import static mr.go.coroutines.core.StringConstants.COROUTINE_DESCRIPTOR;
import static mr.go.coroutines.core.StringConstants.FRAME_NAME;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.commons.collections.list.GrowthList;
import org.apache.commons.collections.map.MultiValueMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

final class MethodTransformer extends InstructionAdapter {

	private static void loadstack(
			int frameIndex,
			Type[] stackTypes,
			MethodVisitor mv) {
		int top = stackTypes.length - 1;
		mv.visitVarInsn(Opcodes.ALOAD, frameIndex);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				FRAME_NAME,
				"getOperands",
				"()[Ljava/lang/Object;");
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

	private static void savestack(int frameIndex, Type[] stack, MethodVisitor mv) {
		int top = stack.length - 1;
		makeInt(stack.length, mv);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
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
				break;
			case Type.FLOAT:
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				box_float(typeSort, mv);
				break;
			case Type.LONG:
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
				box_long(typeSort, mv);
				break;
			case Type.DOUBLE:
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
				box_double(typeSort, mv);
				break;
			case Type.ARRAY:
			case Type.OBJECT:
			case Type.VOID:
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
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
				"([Ljava/lang/Object;)V");
	}

	@SuppressWarnings("unchecked")
	private final MultiValueMap			blockLabels		= MultiValueMap
																.decorate(new HashMap(
																		6,
																		0.95f));

	private TryCatchBlock				currentTryCatchBlock;

	private final int					frame;

	private final boolean				generateDebugCode;

	private Label[]						gotos;

	private final int					in;

	private boolean						incomingExceptionHandler;

	private int							lineNumber;

	private final int					localsArray;

	private final int					localsStartIndex;

	private final MethodProperties		methodProperties;

	private final int					out;

	private final Deque<Type>			stack			= new LinkedList<Type>();

	private final int					state;

	private StackMapFramesAnalyzer		stm;

	private final Deque<TryCatchBlock>	tryCatchBlocks	= new ArrayDeque<TryCatchBlock>(
																2);

	private final GrowthList			variables;

	private int							yieldIndex;

	private final Label					yieldLabel		= new Label();

	public MethodTransformer(
			MethodVisitor writer,
			MethodProperties methodProperties,
			boolean generateDebugCode) {
		// by default everything is delegated to writer
		super(writer);
		int argsLength = methodProperties.getArgsLength();
		int initialSize = argsLength + 1;
		this.methodProperties = methodProperties;
		this.variables = new GrowthList(initialSize);
		this.generateDebugCode = generateDebugCode;
		if (methodProperties.isStatic()) {
			this.frame = 0;
			this.in = 1;
			this.out = 2;
			this.state = 3;
			this.localsArray = 4;
		} else {
			this.frame = 1;
			this.in = 2;
			this.out = 3;
			this.state = 4;
			this.localsArray = 5;
			variables.add(new VariableInfo(methodProperties.getOwner(), true));
		}
		this.localsStartIndex = localsArray + 1;
	}

	@Override
	public void aconst(Object cst) {
		if (cst == null) {
			stack.push(Type.VOID_TYPE);
		} else {
			stack.push(Types.JAVA_LANG_STRING);
		}
		super.aconst(cst);
	}

	@Override
	public void add(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.add(type);
	}

	@Override
	public void aload(Type type) {
		stack.pop();
		Type arrayType = stack.pop();
		stack.push(arrayType.getElementType());
		super.aload(type);
	}

	@Override
	public void and(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.and(type);
	}

	@Override
	public void anew(Type type) {
		stack.push(null);
		super.anew(type);
	}

	@Override
	public void areturn(Type t) {
		// transform to no such element exception
		// first - pop original value
		mv.visitInsn(Opcodes.POP);
		stack.pop();
		// next create exception and throw
		throwex("java/util/NoSuchElementException", mv);
	}

	@Override
	public void arraylength() {
		stack.pop();
		stack.push(Type.INT_TYPE);
		super.arraylength();
	}

	@Override
	public void astore(Type type) {
		stack.pop();
		stack.pop();
		stack.pop();
		super.astore(type);
	}

	@Override
	public void athrow() {
		stack.pop();
		super.athrow();
	}

	@Override
	public void cast(Type from, Type to) {
		stack.pop();
		stack.push(to);
		super.cast(from, to);
	}

	@Override
	public void checkcast(Type type) {
		stack.pop();
		stack.push(type);
		super.checkcast(type);
	}

	@Override
	public void cmpg(Type type) {
		stack.pop();
		stack.pop();
		stack.push(Type.INT_TYPE);
		super.cmpg(type);
	}

	@Override
	public void cmpl(Type type) {
		stack.pop();
		stack.pop();
		stack.push(Type.INT_TYPE);
		super.cmpl(type);
	}

	@Override
	public void dconst(double cst) {
		stack.push(Type.DOUBLE_TYPE);
		super.dconst(cst);
	}

	@Override
	public void div(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.div(type);
	}

	@Override
	public void dup() {
		Type type = stack.peek();
		stack.push(type);
		super.dup();
	}

	@Override
	public void dup2() {
		// we represent category 2 types as one element
		Type type1 = stack.peek();
		if (type1.getSize() == 1) {
			type1 = stack.pop();
			Type type2 = stack.pop();
			stack.push(type1);
			stack.push(type2);
			stack.push(type1);
		} else {
			stack.push(type1);
		}
		super.dup2();
	}

	@Override
	public void dup2X1() {
		Type type1 = stack.pop();
		Type type2 = stack.pop();
		if (type1.getSize() == 2) {
			stack.push(type1);
			stack.push(type2);
			stack.push(type1);
		} else {
			Type type3 = stack.pop();
			stack.push(type2);
			stack.push(type1);
			stack.push(type3);
			stack.push(type2);
			stack.push(type1);
		}
		super.dup2X1();
	}

	@Override
	public void dup2X2() {
		Type type1 = stack.pop();
		Type type2 = stack.pop();
		if (type1.getSize() == 2 && type2.getSize() == 2) {
			// form 4
			stack.push(type1);
			stack.push(type2);
			stack.push(type1);
		} else if (type1.getSize() == 1 && type2.getSize() == 1) {
			Type type3 = stack.pop();
			if (type3.getSize() == 2) {
				// form 3
				stack.push(type2);
				stack.push(type1);
				stack.push(type3);
				stack.push(type2);
				stack.push(type1);
			} else {
				// form1
				Type type4 = stack.pop();
				stack.push(type2);
				stack.push(type1);
				stack.push(type4);
				stack.push(type3);
				stack.push(type2);
				stack.push(type1);
			}
		} else if (type1.getSize() == 2 && type2.getSize() == 1) {
			// form2
			Type type3 = stack.pop();
			stack.push(type1);
			stack.push(type3);
			stack.push(type2);
			stack.push(type1);
		}
		super.dup2X2();
	}

	@Override
	public void dupX1() {
		Type type1 = stack.pop();
		Type type2 = stack.pop();
		stack.push(type1);
		stack.push(type2);
		stack.push(type1);
		super.dupX1();
	}

	@Override
	public void dupX2() {
		Type type1 = stack.pop();
		Type type2 = stack.pop();
		if (type2.getSize() == 2) {
			stack.push(type1);
			stack.push(type2);
			stack.push(type1);
		} else {
			Type type3 = stack.pop();
			stack.push(type1);
			stack.push(type3);
			stack.push(type2);
			stack.push(type1);
		}
		super.dupX2();
	}

	@Override
	public void fconst(float cst) {
		stack.push(Type.FLOAT_TYPE);
		super.fconst(cst);
	}

	@Override
	public void getfield(String owner, String name, String desc) {
		stack.pop();
		stack.push(Type.getType(desc));
		super.getfield(owner, name, desc);
	}

	@Override
	public void getstatic(String owner, String name, String desc) {
		stack.push(Type.getType(desc));
		super.getstatic(owner, name, desc);
	}

	@Override
	public void iconst(int cst) {
		stack.push(Type.INT_TYPE);
		super.iconst(cst);
	}

	@Override
	public void ifacmpeq(Label label) {
		stack.pop();
		stack.pop();
		super.ifacmpeq(label);
	}

	@Override
	public void ifacmpne(Label label) {
		stack.pop();
		stack.pop();
		super.ifacmpne(label);
	}

	@Override
	public void ifeq(Label label) {
		stack.pop();
		super.ifeq(label);
	}

	@Override
	public void ifge(Label label) {
		stack.pop();
		super.ifge(label);
	}

	@Override
	public void ifgt(Label label) {
		stack.pop();
		super.ifgt(label);
	}

	@Override
	public void ificmpeq(Label label) {
		stack.pop();
		stack.pop();
		super.ificmpeq(label);
	}

	@Override
	public void ificmpge(Label label) {
		stack.pop();
		stack.pop();
		super.ificmpge(label);
	}

	@Override
	public void ificmpgt(Label label) {
		stack.pop();
		stack.pop();
		super.ificmpgt(label);
	}

	@Override
	public void ificmple(Label label) {
		stack.pop();
		stack.pop();
		super.ificmple(label);
	}

	@Override
	public void ificmplt(Label label) {
		stack.pop();
		stack.pop();
		super.ificmplt(label);
	}

	@Override
	public void ificmpne(Label label) {
		stack.pop();
		stack.pop();
		super.ificmpne(label);
	}

	@Override
	public void ifle(Label label) {
		stack.pop();
		super.ifle(label);
	}

	@Override
	public void iflt(Label label) {
		stack.pop();
		super.iflt(label);
	}

	@Override
	public void ifne(Label label) {
		stack.pop();
		super.ifne(label);
	}

	@Override
	public void ifnonnull(Label label) {
		stack.pop();
		super.ifnonnull(label);
	}

	@Override
	public void ifnull(Label label) {
		stack.pop();
		super.ifnull(label);
	}

	@Override
	public void iinc(int var, int increment) {
		var += variableIndexOffset;
		super.iinc(var, increment);
	}

	@Override
	public void instanceOf(Type type) {
		stack.pop();
		stack.push(Type.BOOLEAN_TYPE);
		super.instanceOf(type);
	}

	@Override
	public void invokeinterface(String owner, String name, String desc) {
		int argsLength = Type.getArgumentTypes(desc).length + 1;
		methodCall(argsLength, desc);
		super.invokeinterface(owner, name, desc);
	}

	@Override
	public void invokespecial(String owner, String name, String desc) {
		int argsLength = Type.getArgumentTypes(desc).length + 1;
		boolean isConstructor = name.equals("<init>");
		methodCall(argsLength, desc);
		// if it is constructor we replace uninitialized memory reference with
		// correct type
		if (isConstructor) {
			if (stack.peek() == null) {
				stack.pop();
			}
			stack.push(Type.getObjectType(owner));
		}
		super.invokespecial(owner, name, desc);
	}

	@Override
	public void invokestatic(String owner, String name, String desc) {
		boolean coroutineCall = owner.equals(COROUTINES_NAME);
		if (!coroutineCall) {
			/*
			 * 1. ordinary static call
			 */
			int argsLength = Type.getArgumentTypes(desc).length;
			methodCall(argsLength, desc);
		} else {
			if (name.equals("_")) {
				/*
				 * 2.call to artificial CoIterator, since it is not needed after
				 * instrumentation we replace it with null. This null will be
				 * popped by corresponding ARETURN. Stack is not changed
				 */
				mv.visitInsn(Opcodes.ACONST_NULL);
				stack.push(Type.VOID_TYPE);
			} else if (name.equals("yield")) {
				/*
				 * 3.call to yield - core of coroutine processing
				 */
				if (++yieldIndex >= gotos.length) {
					throw new CoroutineGenerationException(
							"Too many yield statements. Please increse system property 'mr.go.coroutines.MaxYields'");
				}
				/*
				 * a) operand on top of stack is passed to the caller, we will
				 * save it in 'in' parameter OR there is nothing to be passed to
				 * the caller in case of yield() overload
				 */
				if (Type.getArgumentTypes(desc).length != 0) {
					input();
				}
				/*
				 * b) save remaining stack
				 */
				boolean nonemptyStack = !stack.isEmpty();
				Type[] stackContents = null;
				if (nonemptyStack) {
					stackContents = new Type[stack.size()];
					stack.toArray(stackContents);
					// sanitize stack
					for (Type t : stackContents) {
						if (t == null) {
							throw new CoroutineGenerationException(
									"It is not possible to yield with uninitialized memory on the stack. Probably you use construct such as: new A(..,yield,..). Please move yield call out of constructor");
						}
					}
					savestack(frame, stackContents, mv);
				}
				/*
				 * c) save locals and state
				 */
				saveLocals();
				mv.visitVarInsn(Opcodes.ALOAD, frame);
				makeInt(yieldIndex, mv);
				mv.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						FRAME_NAME,
						"setState",
						"(I)V");
				/*
				 * d) jump to exit - in debug mode save line number
				 */
				if (generateDebugCode) {
					mv.visitVarInsn(Opcodes.ALOAD, frame);
					makeInt(lineNumber, mv);
					mv.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL,
							FRAME_NAME,
							"setLineOfCode",
							"(I)V");
				}
				mv.visitJumpInsn(Opcodes.GOTO, yieldLabel);
				/*
				 * e) fix jump from switch statement
				 */
				mv.visitLabel(gotos[yieldIndex]);
				stm.emitCleanFrame(mv);
				/*
				 * f) check if exit condition occurs, load locals, restore stack
				 * and stack map
				 */
				mv.visitVarInsn(Opcodes.ALOAD, frame);
				mv.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						FRAME_NAME,
						"isCoroutineClosed",
						"()Z");
				Label continueHere = new Label();
				mv.visitJumpInsn(Opcodes.IFEQ, continueHere);
				throwex("mr/go/coroutines/user/CoroutineExitException", mv);
				mv.visitLabel(continueHere);
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				restoreFrameState();
				if (nonemptyStack) {
					loadstack(frame, stackContents, mv);
				}
				if (!nonemptyStack && stm.getStackMapTop() != 0) {
					// bug fix - when no insn is emitted in restoreFrameState()
					// and stack is empty two frames are collapsed
					stm.emitOldFrame(mv);
				}
				restoreTemporaryState();
				// push "sent" value
				mv.visitVarInsn(Opcodes.ALOAD, out);
				stack.push(Types.JAVA_LANG_OBJECT);
				/*
				 * g) mark try catch block, if any
				 */
				if (currentTryCatchBlock != null) {
					currentTryCatchBlock.hasYield = true;
				}
			}
			return;
		}
		super.invokestatic(owner, name, desc);
	}

	@Override
	public void invokevirtual(String owner, String name, String desc) {
		int argsLength = Type.getArgumentTypes(desc).length + 1;
		methodCall(argsLength, desc);
		super.invokevirtual(owner, name, desc);
	}

	@Override
	public void jsr(Label label) {
		throw new CoroutineGenerationException("<jsr> not allowed");
	}

	@Override
	public void lcmp() {
		stack.pop();
		stack.pop();
		stack.push(Type.INT_TYPE);
		super.lcmp();
	}

	@Override
	public void lconst(long cst) {
		stack.push(Type.LONG_TYPE);
		super.lconst(cst);
	}

	@Override
	public void load(int var, Type type) {
		// load type on stack
		Type realType;
		VariableInfo v = (VariableInfo) variables.get(var);
		if (v != null) {
			realType = v.getType();
		} else {
			// we might have a load instruction before we saw a variable.
			realType = type;
		}
		stack.push(realType);
		// seek updated variable index
		if (var != 0 || methodProperties.isStatic()) {
			var += variableIndexOffset;
		}
		super.load(var, type);
	}

	@Override
	public void lookupswitch(Label dflt, int[] keys, Label[] labels) {
		stack.pop();
		super.lookupswitch(dflt, keys, labels);
	}

	@Override
	public void monitorenter() {
		stack.pop();
		super.monitorenter();
	}

	@Override
	public void monitorexit() {
		stack.pop();
		super.monitorexit();
	}

	@Override
	public void mul(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.mul(type);
	}

	@Override
	public void multianewarray(String desc, int dims) {
		while (dims-- > 0) {
			stack.pop();
		}
		stack.push(Type.getType(desc));
		super.multianewarray(desc, dims);
	}

	@Override
	public void neg(Type type) {
		stack.pop();
		stack.push(type);
		super.neg(type);
	}

	@Override
	public void newarray(Type type) {
		stack.pop();
		int typeSort = type.getSort();
		switch (typeSort) {
		case Type.INT:
			stack.push(Types.INT_ARRAY);
			break;
		case Type.BOOLEAN:
			stack.push(Types.BOOLEAN_ARRAY);
			break;
		case Type.BYTE:
			stack.push(Types.BYTE_ARRAY);
			break;
		case Type.CHAR:
			stack.push(Types.CHAR_ARRAY);
			break;
		case Type.DOUBLE:
			stack.push(Types.DOUBLE_ARRAY);
			break;
		case Type.FLOAT:
			stack.push(Types.FLOAT_ARRAY);
			break;
		case Type.LONG:
			stack.push(Types.LONG_ARRAY);
			break;
		case Type.SHORT:
			stack.push(Types.SHORT_ARRAY);
			break;
		default:
			String elementDescriptor = type.getDescriptor();
			Type arrayType = Type.getType('[' + elementDescriptor);
			stack.push(arrayType);
		}
		super.newarray(type);
	}

	@Override
	public void or(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.or(type);
	}

	@Override
	public void pop() {
		stack.pop();
		super.pop();
	}

	@Override
	public void pop2() {
		// we represent category 2 types as one element
		Type type = stack.pop();
		if (type.getSize() == 1) {
			stack.pop();
		}
		super.pop2();
	}

	@Override
	public void putfield(String owner, String name, String desc) {
		stack.pop();
		stack.pop();
		super.putfield(owner, name, desc);
	}

	@Override
	public void putstatic(String owner, String name, String desc) {
		stack.pop();
		super.putstatic(owner, name, desc);
	}

	@Override
	public void rem(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.rem(type);
	}

	@Override
	public void ret(int var) {
		throw new CoroutineGenerationException("<ret> not allowed");
	}

	@Override
	public void shl(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.shl(type);
	}

	@Override
	public void shr(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.shr(type);
	}

	@Override
	public void store(int var, Type type) {
		Type realType = stack.pop();
		// check for long/double as the last variable
		if (realType.getSize() == 2) {
			variables.set(var + 1, null);
		}
		if (var >= variables.size()) {
			variables.set(var, new VariableInfo(realType));
		} else {
			VariableInfo varInfo = (VariableInfo) variables.get(var);
			if (varInfo == null) {
				varInfo = new VariableInfo(realType);
				variables.set(var, varInfo);
			} else {
				varInfo.setType(realType);
				varInfo.setInitialized(true);
				varInfo.setFinal(false);
			}
		}
		// translate var index
		var += variableIndexOffset;
		super.store(var, type);
	}

	@Override
	public void sub(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.sub(type);
	}

	@Override
	public void swap() {
		Type type1 = stack.pop();
		Type type2 = stack.pop();
		stack.push(type1);
		stack.push(type2);
		super.swap();
	}

	@Override
	public void tableswitch(int min, int max, Label dflt, Label[] labels) {
		stack.pop();
		super.tableswitch(min, max, dflt, labels);
	}

	@Override
	public void tconst(Type type) {
		stack.push(Types.CLASS_TYPE);
		super.tconst(type);
	}

	@Override
	public void ushr(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.ushr(type);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (desc.equals(COROUTINE_DESCRIPTOR)) {
			return new CoroutineAnnotationVisitor();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void visitCode() {
		super.visitCode();
		/*
		 * int state = frame.getState(); Object[] locals = frame.getLocals();
		 */
		{
			mv.visitVarInsn(Opcodes.ALOAD, frame);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					FRAME_NAME,
					"getState",
					"()I");
			mv.visitVarInsn(Opcodes.ISTORE, state);
			mv.visitVarInsn(Opcodes.ALOAD, frame);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					FRAME_NAME,
					"getLocals",
					"()[Ljava/lang/Object;");
			mv.visitVarInsn(Opcodes.ASTORE, localsArray);
		}
		/*
		 * if (chkclosed) throw closed;
		 */
		{
			mv.visitVarInsn(Opcodes.ILOAD, state);
			mv.visitInsn(Opcodes.ICONST_M1);
			Label exitBranch = new Label();
			mv.visitJumpInsn(Opcodes.IF_ICMPNE, exitBranch);
			throwex("mr/go/coroutines/user/CoroutineClosedException", mv);
			mv.visitLabel(exitBranch);
			// frame: [(this)?, frame, in, out, state, locals]
			mv.visitFrame(Opcodes.F_APPEND, 2, new Object[]
			{ Opcodes.INTEGER, "[Ljava/lang/Object;" }, 0, null);
		}
		Type[] argsTypes = methodProperties.getArgsTypes();
		int argsLength = argsTypes.length;
		/*
		 * switch(state){case 0: break; case n: goto yield; case _wrong_: throw
		 * invalid;} arg1, arg2, arg3 = locals[i..j] if (chkexit) throw closed;
		 */
		{
			mv.visitVarInsn(Opcodes.ILOAD, state);
			Label defaultBranch = new Label();
			Label[] labels = new Label[maxYieldStatements + 1];
			gotos = new Label[maxYieldStatements + 1];
			for (int i = 0; i <= maxYieldStatements; i++) {
				labels[i] = new Label();
				gotos[i] = new Label();
			}
			mv.visitTableSwitchInsn(
					0,
					maxYieldStatements,
					defaultBranch,
					labels);
			for (int i = 0; i <= maxYieldStatements; i++) {
				Label li = labels[i];
				Label gotoi = gotos[i];
				mv.visitLabel(li);
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				mv.visitJumpInsn(Opcodes.GOTO, gotoi);
			}
			mv.visitLabel(defaultBranch);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			throwex("mr/go/coroutines/user/InvalidCoroutineException", mv);
			mv.visitLabel(gotos[0]);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			getlocs(
					localsArray,
					localsStartIndex,
					(methodProperties.isStatic()) ? 0 : 1,
					argsLength,
					mv,
					argsTypes);
			// we can make all variables (arguments and this) final to avoid
			// copying
			// them
			for (int i = 0; i < argsLength; i++) {
				Type argType = argsTypes[i];
				variables.add(new VariableInfo(argType, true));
				if (argType.getSize() == 2) {
					variables.add(null);
				}
			}
			stm = new StackMapFramesAnalyzer(variables, stack, methodProperties
					.getOwner()
					.getInternalName(), methodProperties.isStatic(), argsLength);
			mv.visitVarInsn(Opcodes.ALOAD, frame);
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					FRAME_NAME,
					"isCoroutineClosed",
					"()Z");
			Label continueHere = new Label();
			mv.visitJumpInsn(Opcodes.IFEQ, continueHere);
			throwex("mr/go/coroutines/user/CoroutineClosedException", mv);
			mv.visitLabel(continueHere);
			stm.emitOldFrame(mv);
			// add NOP in case that user code contains stack map here
			mv.visitInsn(Opcodes.NOP);
		}
	}

	@Override
	public void visitEnd() {
		super.visitEnd();
		methodProperties.setMaxVariables(variables.size());
		if (generateDebugCode) {
			String[] names = new String[variables.size()];
			ListIterator<VariableInfo> varIt = varIterator();
			while (varIt.hasNext()) {
				VariableInfo varInfo = varIt.next();
				if (varInfo != null) {
					names[varIt.previousIndex()] = varInfo.getName();
				}
			}
			if (!methodProperties.isStatic()) {
				names[0] = ((VariableInfo) variables.get(0)).getName();
			}
			methodProperties.setVariableNames(names);
		}
	}

	@Override
	public void visitFrame(
			int type,
			int local,
			Object[] local2,
			int stack,
			Object[] stack2) {
		if (incomingExceptionHandler) {
			stm.emitCatchFrame((String) stack2[0], mv);
			stm.nextFrame(type, local, local2, stack, stack2);
			restoreFrameState();
			stm.emitOldFrame(mv);
			// restoreTemporaryState();
			incomingExceptionHandler = false;
			return;
		}
		stm.nextFrame(type, local, local2, stack, stack2);
		if (type == Opcodes.F_FULL) {
			stm.emitFullFrame(mv);
			return;
		}
		super.visitFrame(type, local, local2, stack, stack2);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void visitLabel(Label label) {
		TryCatchBlock currentBlock;
		Collection<TryCatchBlock> allBlocksAtLabel;
		/*
		 * see if it is label of current block's end
		 */
		if (currentTryCatchBlock != null && currentTryCatchBlock.end == label) {
			/*
			 * check if it is nested block, if it is then make outer block
			 * current block again. Also mark outer blocks as "yielded"
			 */
			currentBlock = currentTryCatchBlock;
			TryCatchBlock outerBlock = tryCatchBlocks.peek();
			if (outerBlock != null) {
				do {
					outerBlock = tryCatchBlocks.removeLast();
					outerBlock.hasYield = currentBlock.hasYield;
					if (outerBlock.end == label) {
						outerBlock = null;
					} else {
						break;
					}
				} while (!tryCatchBlocks.isEmpty());
				currentTryCatchBlock = outerBlock;
			} else {
				currentTryCatchBlock = null;
			}
			/*
			 * if there is chance we will jump into this block check whether
			 * handler is about to begin
			 */
			incomingExceptionHandler = currentBlock.hasYield
										& currentBlock.handler == label;
		} else if ((allBlocksAtLabel = blockLabels.getCollection(label)) != null) {
			Iterator<TryCatchBlock> tryCatchIterator = allBlocksAtLabel
					.iterator();
			currentBlock = tryCatchIterator.next();
			/*
			 * see if it is beginning or handler of a block
			 */
			if (currentBlock.handler == label && currentBlock.hasYield) {
				incomingExceptionHandler = true;
			} else {
				if (currentTryCatchBlock == null) {
					currentTryCatchBlock = currentBlock;
				}
				while (tryCatchIterator.hasNext()) {
					tryCatchBlocks.push(tryCatchIterator.next());
				}
			}
		}
		super.visitLabel(label);
	}

	@Override
	public void visitLineNumber(int line, Label start) {
		lineNumber = line;
		super.visitLineNumber(line, start);
	}

	@Override
	public void visitLocalVariable(
			String name,
			String desc,
			String signature,
			Label start,
			Label end,
			int index) {
		if (generateDebugCode) {
			VariableInfo variable = (VariableInfo) variables.get(index);
			String oldName = variable.getName();
			if (oldName == null) {
				variable.setName(name);
			} else {
				variable.setName(oldName + '/' + name);
			}
		}
		if (index != 0 || methodProperties.isStatic()) {
			index += variableIndexOffset;
		}
		super.visitLocalVariable(name, desc, signature, start, end, index);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		// seems to be the only way to be sure that bytecode ended
		mv.visitLabel(yieldLabel);
		stm.emitCleanFrame(mv);
		mv.visitVarInsn(Opcodes.ALOAD, in);
		mv.visitInsn(Opcodes.ARETURN);
		for (int i = yieldIndex + 1; i < gotos.length; i++) {
			mv.visitLabel(gotos[i]);
		}
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		throwex("mr/go/coroutines/user/InvalidCoroutineException", mv);
		// stack is either what we need, or what they need, whichever is bigger
		// locals are what we added to theirs
		// UPDATE: it is better to compute maxs using ASM
		super.visitMaxs(maxStack, maxLocals);
	}

	@Override
	public void visitTryCatchBlock(
			Label start,
			Label end,
			Label handler,
			String type) {
		// we have to remember try-catch blocks in a queue because if yield()
		// occurs inside try-catch block we must reload variables used in it
		// from locals array. If we hadn't, there could have been situation
		// where
		// exception prevented us from 'initializng' variables which are used in
		// cacth
		if (blockLabels.containsKey(handler)) {
			// this exception block has the same handler as some other try-catch
			// block
			// it is probably possible only with javac finally clause
			// implementation
			// the frame for these blocks will be shared and yield HOPEFULLY
			// cannot occur in this one
			return;
		}
		TryCatchBlock block = new TryCatchBlock(start, end, handler);
		blockLabels.put(start, block);
		blockLabels.put(handler, block);
		super.visitTryCatchBlock(start, end, handler, type);
	}

	@Override
	public void xor(Type type) {
		stack.pop();
		stack.pop();
		stack.push(type);
		super.xor(type);
	}

	private void getSingleLocal(int index) {
		Object v = variables.get(index);
		if (v != null) {
			VariableInfo varInfo = (VariableInfo) v;
			if (varInfo.isInitialized()) {
				getloc(localsArray, index + variableIndexOffset, index, varInfo
						.getType(), mv);
			}
		}
	}

	private void input() {
		Type type = stack.pop();
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
		mv.visitVarInsn(Opcodes.ASTORE, in);
	}

	private void methodCall(int argsLength, String desc) {
		Type returnType = Type.getReturnType(desc);
		while (argsLength-- > 0) {
			stack.pop();
		}
		if (returnType.getSort() != Type.VOID) {
			stack.push(returnType);
		}
	}

	private void restoreFrameState() {
		// restore saved locals
		int top = stm.getStackMapTop();
		int varIndex = methodProperties.isStatic() ? 0 : 1;
		while (varIndex < top) {
			getSingleLocal(varIndex++);
		}
	}

	private void restoreTemporaryState() {
		// restore temporary locals - that is, not present in the frame yet
		// initialized
		int top = variables.size();
		int varIndex = stm.getStackMapTop();
		while (varIndex < top) {
			getSingleLocal(varIndex++);
		}
	}

	private void saveLocals() {
		int varIndex;
		ListIterator<VariableInfo> varIterator = varIterator();
		while (varIterator.hasNext()) {
			VariableInfo varInfo = varIterator.next();
			if (varInfo != null && varInfo.isInitialized()
				&& !varInfo.isFinal()) {
				varIndex = varIterator.previousIndex();
				saveloc(
						localsArray,
						varIndex + variableIndexOffset,
						varIndex,
						varInfo.getType(),
						mv);
				varInfo.setFinal(true);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private ListIterator<VariableInfo> varIterator() {
		return variables.listIterator(methodProperties.isStatic() ? 0 : 1);
	}

	private class CoroutineAnnotationVisitor implements AnnotationVisitor {

		public CoroutineAnnotationVisitor() {
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals("generator")) {
				methodProperties.setRequestNext(!((Boolean) value)
						.booleanValue());
			} else if (name.equals("threadLocal")) {
				methodProperties.setThreadLocal((Boolean) value);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String desc) {
			// none defined
			return null;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			// none defined
			return null;
		}

		@Override
		public void visitEnd() {
		}

		@Override
		public void visitEnum(String name, String desc, String value) {
			// none defined
		}
	}

	private static class TryCatchBlock {

		private final Label	end;

		private final Label	handler;

		private boolean		hasYield;

		private final Label	start;

		public TryCatchBlock(
				Label start,
				Label end,
				Label handler) {
			this.start = start;
			this.end = end;
			this.handler = handler;
		}
	}

	private static final int	maxYieldStatements	= Integer
															.parseInt(System
																	.getProperty(
																			"mr.go.coroutines.MaxYields",
																			"8"));

	private static final int	variableIndexOffset	= 5;
}
