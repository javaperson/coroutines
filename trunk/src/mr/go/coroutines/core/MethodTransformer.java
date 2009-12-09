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

import static java.lang.Math.max;
import static mr.go.coroutines.core.InstructionGenerationUtils.EXCEPTION_CLOSED;
import static mr.go.coroutines.core.InstructionGenerationUtils.EXCEPTION_EXIT;
import static mr.go.coroutines.core.InstructionGenerationUtils.EXCEPTION_INVALID;
import static mr.go.coroutines.core.InstructionGenerationUtils.EXCEPTION_RETURN;
import static mr.go.coroutines.core.InstructionGenerationUtils.FRAME_NAME;
import static mr.go.coroutines.core.InstructionGenerationUtils.OBJECT;
import static mr.go.coroutines.core.InstructionGenerationUtils.OBJECT_ARRAY;
import static mr.go.coroutines.core.InstructionGenerationUtils._JAVA_LANG_OBJECT;
import static mr.go.coroutines.core.InstructionGenerationUtils._JAVA_LANG_STRING;
import static mr.go.coroutines.core.InstructionGenerationUtils.chkclosed;
import static mr.go.coroutines.core.InstructionGenerationUtils.getloc;
import static mr.go.coroutines.core.InstructionGenerationUtils.getlocs;
import static mr.go.coroutines.core.InstructionGenerationUtils.input;
import static mr.go.coroutines.core.InstructionGenerationUtils.loadexitstate;
import static mr.go.coroutines.core.InstructionGenerationUtils.loadlocs;
import static mr.go.coroutines.core.InstructionGenerationUtils.loadstack;
import static mr.go.coroutines.core.InstructionGenerationUtils.loadstate;
import static mr.go.coroutines.core.InstructionGenerationUtils.saveline;
import static mr.go.coroutines.core.InstructionGenerationUtils.savelocs;
import static mr.go.coroutines.core.InstructionGenerationUtils.savestack;
import static mr.go.coroutines.core.InstructionGenerationUtils.savestate;
import static mr.go.coroutines.core.InstructionGenerationUtils.switchstate;
import static mr.go.coroutines.core.InstructionGenerationUtils.throwex;

import java.util.*;

import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.collections.list.GrowthList;
import org.apache.commons.collections.map.MultiValueMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

final class MethodTransformer extends InstructionAdapter {

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

	@SuppressWarnings("unchecked")
	private final MultiValueMap			blockLabels		= MultiValueMap
																.decorate(new HashMap(
																		6,
																		0.95f));

	private TryCatchBlock				currentTryCatchBlock;

	private final int					frame;

	private Object[]					fullFramePrefix;

	private final boolean				generateDebugCode;

	private Label[]						gotos;

	private final int					in;

	private boolean						incomingExceptionHandler;

	private int							lineNumber;

	private final int					localsArray;

	private final int					localsStartIndex;

	private int							maxVariables;

	private boolean						mergeFrames;

	private final MethodProperties		methodProperties;

	private final int					out;

	private int							seenVariables;

	private final Deque<Type>			stack			= new LinkedList<Type>();

	private int							stackSize;

	private final int					state;

	private final Deque<TryCatchBlock>	tryCatchBlocks	= new ArrayDeque<TryCatchBlock>(
																2);

	private TryCatchBlock				tryCatchHandler;

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
		this.seenVariables = argsLength;
		if (methodProperties.isStatic()) {
			this.frame = this.maxVariables = 0;
			this.in = 1;
			this.out = 2;
			this.state = 3;
			this.localsArray = 4;
		} else {
			this.frame = this.maxVariables = 1;
			this.in = 2;
			this.out = 3;
			this.state = 4;
			this.localsArray = 5;
			variables.add(0, new VariableInfo(
					0,
					methodProperties.getOwner(),
					true));
		}
		this.localsStartIndex = localsArray + 1;
	}

	@Override
	public void aconst(Object cst) {
		if (cst == null) {
			stack.push(Type.VOID_TYPE);
		} else {
			stack.push(_JAVA_LANG_STRING);
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
		throwex(EXCEPTION_RETURN, mv);
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
			int maxPop = stack.size();
			while (stack.peek() == null && maxPop-- > 0) {
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
					input(in, stack.pop(), mv);
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
					int stackSize = savestack(frame, stackContents, mv);
					this.stackSize = max(this.stackSize, stackSize);
				}
				/*
				 * c) save locals and state
				 */
				savelocs(localsArray, varIterator(), mv);
				savestate(frame, yieldIndex, mv);
				this.stackSize = max(this.stackSize, 4);
				/*
				 * d) jump to exit - in debug mode save line number
				 */
				if (generateDebugCode) {
					saveline(frame, lineNumber, mv);
				}
				mv.visitJumpInsn(Opcodes.GOTO, yieldLabel);
				/*
				 * e) fix jump from switch statement
				 */
				mv.visitLabel(gotos[yieldIndex]);
				makeYieldFrame();
				/*
				 * f) check if exit condition occurs, load locals, restore stack
				 */
				loadexitstate(frame, mv);
				Label continueHere = new Label();
				mv.visitJumpInsn(Opcodes.IFEQ, continueHere);
				throwex(EXCEPTION_EXIT, mv);
				mv.visitLabel(continueHere);
				mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				getlocs(localsArray, varIterator(), mv);
				if (nonemptyStack) {
					loadstack(frame, stackContents, mv);
				}
				// push "sent" value
				mv.visitVarInsn(Opcodes.ALOAD, out);
				stack.push(_JAVA_LANG_OBJECT);
				/*
				 * g) mark try catch block, if any
				 */
				if (currentTryCatchBlock != null) {
					currentTryCatchBlock.hasYield = true;
				}
				/*
				 * h) mark that future frame might need to be merged with yield
				 * state
				 */
				mergeFrames = seenVariables != 0;
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
		// first seek updated variable index
		if (var != 0 || methodProperties.isStatic()) {
			var += variableIndexOffset;
		}
		// then load type on stack
		Type realType;
		Object v = variables.get(var);
		if (v != null) {
			realType = ((VariableInfo) v).getType();
		} else {
			// we might have a load instruction before we saw a variable.
			realType = type;
			variables.set(var, new VariableInfo(maxVariables, realType));
			maxVariables += 1;
		}
		stack.push(realType);
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
			stack.push(INT_ARRAY);
			break;
		case Type.BOOLEAN:
			stack.push(BOOLEAN_ARRAY);
			break;
		case Type.BYTE:
			stack.push(BYTE_ARRAY);
			break;
		case Type.CHAR:
			stack.push(CHAR_ARRAY);
			break;
		case Type.DOUBLE:
			stack.push(DOUBLE_ARRAY);
			break;
		case Type.FLOAT:
			stack.push(FLOAT_ARRAY);
			break;
		case Type.LONG:
			stack.push(LONG_ARRAY);
			break;
		case Type.SHORT:
			stack.push(SHORT_ARRAY);
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
		// translate var index
		var += variableIndexOffset;
		Type realType = stack.pop();
		if (var >= variables.size()) {
			variables.add(var, new VariableInfo(maxVariables, realType, true));
			maxVariables += 1;
		} else {
			VariableInfo varInfo = (VariableInfo) variables.get(var);
			if (varInfo == null) {
				varInfo = new VariableInfo(maxVariables, realType, true);
				variables.set(var, varInfo);
				maxVariables += 1;
			} else {
				varInfo.setType(realType);
				varInfo.setInitialized(true);
				varInfo.setFinal(false);
			}
		}
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
		stack.push(CLASS_TYPE);
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
		if (desc.equals(ClassAnalyzer.COROUTINE_DESCRIPTOR)) {
			return new CoroutineAnnotationVisitor();
		}
		return super.visitAnnotation(desc, visible);
	}

	@Override
	public void visitCode() {
		super.visitCode();
		/*
		 * int state = frame.getState(); Object[] locals = frame.getLocals();
		 */
		{
			loadstate(frame, mv);
			mv.visitVarInsn(Opcodes.ISTORE, state);
			loadlocs(frame, mv);
			mv.visitVarInsn(Opcodes.ASTORE, localsArray);
			// locals: +2, stack: 1
		}
		/*
		 * if (chkclosed) throw closed;
		 */
		{
			mv.visitVarInsn(Opcodes.ILOAD, state);
			chkclosed(mv);
			// frame: [(this)?, frame, in, out, state, locals]
			mv.visitFrame(Opcodes.F_APPEND, 2, new Object[]
			{ Opcodes.INTEGER, OBJECT_ARRAY }, 0, null);
			// locals: +0, stack: 2
		}
		Type[] argsTypes = methodProperties.getArgsTypes();
		int argsLength = argsTypes.length;
		/*
		 * switch(state){case 0: break; case n: goto yield; case _wrong_: throw
		 * invalid;} arg1, arg2, arg3 = locals[i..j] if (chkexit) throw closed;
		 */
		{
			mv.visitVarInsn(Opcodes.ILOAD, state);
			gotos = switchstate(maxYieldStatements + 1, mv);
			// locals +0, stack : 2
			getlocs(
					localsArray,
					localsStartIndex,
					maxVariables,
					argsLength,
					variables,
					mv,
					argsTypes);
			loadexitstate(frame, mv);
			Label continueHere = new Label();
			mv.visitJumpInsn(Opcodes.IFEQ, continueHere);
			throwex(EXCEPTION_CLOSED, mv);
			mv.visitLabel(continueHere);
			makeInitialFrame(argsLength, argsTypes);
			// add NOP in case that user code contains stack map here
			mv.visitInsn(Opcodes.NOP);
			// locals +args.length, stack : 2
		}
		maxVariables += argsLength;
		stackSize = 2;
		// we can make all variables (arguments and this) final to avoid copying
		// them
		for (Object v : variables) {
			if (v != null) {
				VariableInfo varInfo = (VariableInfo) v;
				varInfo.setFinal(true);
				varInfo.setTemporary(false);
			}
		}
	}

	@Override
	public void visitEnd() {
		super.visitEnd();
		methodProperties.setMaxVariables(maxVariables);
		if (generateDebugCode) {
			String[] names = new String[maxVariables];
			Iterator<VariableInfo> varIt = varIterator();
			while (varIt.hasNext()) {
				VariableInfo varInfo = varIt.next();
				if (varInfo != null) {
					names[varInfo.getLogicalIndex()] = varInfo.getName();
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
		Object[] locals = local2;
		int nLocal = local;
		ListIterator<VariableInfo> varIterator;
		VariableInfo varInfo;
		switch (type) {
		case Opcodes.F_FULL:
			// we have to append our changes to locals
			locals = appendFrame(local2, nLocal);
			nLocal = locals.length;
			seenVariables = nLocal - fullFramePrefix.length;
			// then seek uninitialized variables in block marked by frame to
			// reduce copying effort and mark remaining variables as final
			varIterator = varIterator();
			int localIndex = (methodProperties.isStatic()) ? 0 : 1;
			for (; localIndex < local; localIndex++) {
				Object opcode = local2[localIndex];
				boolean uninitialized = opcode == Opcodes.TOP;
				varInfo = varIterator.next();
				if (varInfo != null) {
					if (uninitialized) {
						varInfo.setInitialized(false);
					} else {
						varInfo.setTemporary(false);
					}
				} else if (uninitialized) {
					// set temporary variable
					varIterator.set(new VariableInfo(maxVariables));
					maxVariables += 1;
				}
				// move through category 2 variables
				if (opcode == Opcodes.LONG || opcode == Opcodes.DOUBLE) {
					if (varIterator.hasNext()) {
						varIterator.next();
					}
				}
			}
			// variables not introduced in full frame are marked uninitialized
			while (varIterator.hasNext()) {
				varInfo = varIterator.next();
				if (varInfo != null) {
					varInfo.setInitialized(false);
				}
			}
			// next we recreate stack for this block
			this.stack.clear();
			for (int i = 0; i < stack; i++) {
				Object stackOpcode = stack2[i];
				this.stack.push(getTypeFromFrameOpcode(stackOpcode));
			}
			// if it is yield exception handler, handle it
			if (incomingExceptionHandler) {
				incomingExceptionHandler = false;
				makeExceptionFrame(stack2[0]);
				return;
			}
			// since we are recreating frame anyway, merge is taken care of
			// (unless it is a handler)
			mergeFrames = false;
			break;
		case Opcodes.F_CHOP:
			// we mark chopped variables as uninitialized to reduce copying
			// effort
			seenVariables -= nLocal;
			varIterator = new ReverseListIterator(variables);
			while (local > 0) {
				Object v = varIterator.next();
				if (v != null) {
					varInfo = (VariableInfo) v;
					varInfo.setInitialized(false);
					if (!varInfo.isTemporary()) {
						local--;
					}
				}
			}
			if (mergeFrames) {
				makePostYieldFrame();
				mergeFrames = false;
				return;
			}
			break;
		case Opcodes.F_SAME1:
			dropTemporaryVariables();
			this.stack.clear();
			this.stack.push(getTypeFromFrameOpcode(stack2[0]));
			// if this signals start of exception handler we will reload
			// variables
			if (incomingExceptionHandler) {
				incomingExceptionHandler = false;
				makeExceptionFrame(stack2[0]);
				return;
			}
			if (mergeFrames) {
				mergeFrames = false;
				locals = createCoroutineFrameLocals();
				if (locals.length != 0) {
					locals = mergeFrame(locals);
					super.visitFrame(
							Opcodes.F_FULL,
							locals.length,
							locals,
							1,
							stack2);
				} else {
					super.visitFrame(Opcodes.F_SAME1, 0, null, 1, stack2);
				}
				return;
			}
			break;
		case Opcodes.F_NEW:
			throw new CoroutineGenerationException(
					"Expanded frames not allowed");
		case Opcodes.F_APPEND:
			varIterator = varIterator();
			int i = 0;
			// move to the last seen variable
			while (i < seenVariables) {
				Object v = varIterator.next();
				if (v != null) {
					i++;
				}
			}
			// append
			while (local > 0) {
				Object v = varIterator.next();
				if (v != null) {
					varInfo = (VariableInfo) v;
					varInfo.setTemporary(false);
					local--;
				}
			}
			seenVariables += nLocal;
			if (mergeFrames) {
				makePostYieldFrame();
				mergeFrames = false;
				return;
			}
			break;
		case Opcodes.F_SAME:
			dropTemporaryVariables();
			if (mergeFrames) {
				makePostYieldFrame();
				mergeFrames = false;
				return;
			}
		}
		super.visitFrame(type, nLocal, locals, stack, stack2);
	}

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
			if (currentBlock.hasYield && currentBlock.handler == label) {
				incomingExceptionHandler = true;
				tryCatchHandler = currentBlock;
			}
		} else if ((allBlocksAtLabel = blockLabels.getCollection(label)) != null) {
			Iterator<TryCatchBlock> tryCatchIterator = allBlocksAtLabel
					.iterator();
			currentBlock = tryCatchIterator.next();
			/*
			 * see if it is beginning or handler of a block
			 */
			if (currentBlock.handler == label && currentBlock.hasYield) {
				incomingExceptionHandler = true;
				tryCatchHandler = currentBlock;
			} else if (currentBlock.start == label) {
				if (currentTryCatchBlock == null) {
					currentTryCatchBlock = currentBlock;
				} else {
					tryCatchBlocks.push(currentTryCatchBlock);
					currentTryCatchBlock = currentBlock;
				}
				/*
				 * it is beginning of block, remember active variables and let
				 * it wait for its end
				 */
				List<VariableInfo> blockVariables = currentTryCatchBlock.variables;
				Iterator<VariableInfo> varIterator = varIterator();
				while (varIterator.hasNext()) {
					Object v = varIterator.next();
					if (v != null) {
						VariableInfo varInfo = (VariableInfo) v;
						if (varInfo.isInitialized()) {
							blockVariables.add(varInfo);
						}
					}
				}
				/*
				 * see if it is try with many catch clauses
				 */
				while (tryCatchIterator.hasNext()) {
					TryCatchBlock outerBlock = tryCatchIterator.next();
					outerBlock.variables.addAll(blockVariables);
					tryCatchBlocks.push(outerBlock);
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
		if (index != 0 || methodProperties.isStatic()) {
			index += variableIndexOffset;
		}
		if (generateDebugCode) {
			VariableInfo variable = (VariableInfo) variables.get(index);
			String oldName = variable.getName();
			if (oldName == null) {
				variable.setName(name);
			} else {
				variable.setName(oldName + '/' + name);
			}
		}
		super.visitLocalVariable(name, desc, signature, start, end, index);

	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		// seems to be the only way to be sure that bytecode ended
		mv.visitLabel(yieldLabel);
		makeFrameAfterUserCode();
		mv.visitVarInsn(Opcodes.ALOAD, in);
		mv.visitInsn(Opcodes.ARETURN);
		for (int i = yieldIndex + 1; i < gotos.length; i++) {
			mv.visitLabel(gotos[i]);
		}
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		throwex(EXCEPTION_INVALID, mv);
		// stack is either what we need, or what they need, whichever is bigger
		// locals are what we added to theirs
		super.visitMaxs(max(stackSize, maxStack), 5 + maxLocals);
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

	private Object[] appendFrame(Object[] frameLocals, int nLocals) {
		int fullLocals = fullFramePrefix.length;
		int frameLength;
		int offset;
		if (methodProperties.isStatic()) {
			frameLength = nLocals + fullLocals;
			offset = 0;
		} else {
			frameLength = nLocals + fullLocals - 1;
			offset = 1;
		}
		Object[] locals = new Object[frameLength];
		System.arraycopy(fullFramePrefix, 0, locals, 0, fullLocals);
		System.arraycopy(frameLocals, offset, locals, fullLocals, nLocals
																	- offset);
		return locals;
	}

	private Object[] createCoroutineFrameLocals() {
		if (seenVariables == 0) {
			return new Object[0];
		}
		Object[] coroutineFramePart = new Object[seenVariables];
		Iterator<VariableInfo> varIterator = varIterator();
		VariableInfo varInfo;
		Object v;
		int varIndex = 0;
		while (varIndex < seenVariables) {
			v = varIterator.next();
			if (v == null) {
				continue;
			}
			varInfo = (VariableInfo) v;
			if (varInfo.isInitialized() && !varInfo.isTemporary()) {
				coroutineFramePart[varIndex] = getFrameOpcode(varInfo.getType());
			} else {
				coroutineFramePart[varIndex] = Opcodes.TOP;
			}
			varIndex++;
		}
		return coroutineFramePart;
	}

	private void dropTemporaryVariables() {
		int count = seenVariables;
		if (count == 0) {
			return;
		}
		Iterator<VariableInfo> iterator = varIterator();
		VariableInfo varInfo;
		while (count > 0) {
			varInfo = iterator.next();
			if (varInfo != null) {
				if (varInfo.isTemporary()) {
					varInfo.setInitialized(false);
				}
				count--;
			}
		}
	}

	private void makeExceptionFrame(Object exception) {
		List<VariableInfo> blockVariables = tryCatchHandler.variables;
		int varsLength = blockVariables.size();
		if (varsLength == 0) {
			mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]
			{ exception });
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					fullFramePrefix.length,
					fullFramePrefix,
					1,
					new Object[]
					{ exception });
			for (VariableInfo varInfo : blockVariables) {
				getloc(localsArray, variables.indexOf(varInfo) /*
																 * TODO: this
																 * might be
																 * slow, see if
																 * we can
																 * optimize
																 */, varInfo
						.getLogicalIndex(), varInfo.getType(), mv);
			}
		}
		tryCatchHandler = null;
		mergeFrames = true;
	}

	private void makeFrameAfterUserCode() {
		mv.visitFrame(
				Opcodes.F_FULL,
				fullFramePrefix.length,
				fullFramePrefix,
				0,
				new Object[0]);
	}

	private void makeInitialFrame(int argsLength, Type[] argsTypes) {
		int frameSize = argsLength;
		int coroutineLocalsLength;
		Object[] locals;
		if (methodProperties.isStatic()) {
			locals = new Object[frameSize + 5];
			locals[0] = FRAME_NAME;
			locals[1] = OBJECT;
			locals[2] = OBJECT;
			locals[3] = Opcodes.INTEGER;
			locals[4] = OBJECT_ARRAY;
			coroutineLocalsLength = 5;
		} else {
			locals = new Object[frameSize + 6];
			locals[0] = methodProperties.getOwner().getInternalName();
			locals[1] = FRAME_NAME;
			locals[2] = OBJECT;
			locals[3] = OBJECT;
			locals[4] = Opcodes.INTEGER;
			locals[5] = OBJECT_ARRAY;
			coroutineLocalsLength = 6;
		}
		for (int i = 0; i < argsLength; i++) {
			locals[i + coroutineLocalsLength] = getFrameOpcode(argsTypes[i]);
		}
		if (frameSize == 0) {
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		} else if (frameSize <= 3) {
			Object[] appendedPart = Arrays.copyOfRange(
					locals,
					coroutineLocalsLength,
					locals.length);
			mv.visitFrame(Opcodes.F_APPEND, frameSize, appendedPart, 0, null);
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					locals.length,
					locals,
					0,
					new Object[0]);
		}
		if (argsLength == 0) {
			fullFramePrefix = locals;
		} else {
			fullFramePrefix = Arrays.copyOf(locals, coroutineLocalsLength);
		}
	}

	private void makePostYieldFrame() {
		Object[] coroutineFramePart = createCoroutineFrameLocals();
		int varCount = coroutineFramePart.length;
		if (varCount == 0) {
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		} else if (varCount <= 3) {
			mv.visitFrame(
					Opcodes.F_APPEND,
					varCount,
					coroutineFramePart,
					0,
					new Object[0]);
		} else {
			Object[] locals = mergeFrame(coroutineFramePart);
			mv.visitFrame(
					Opcodes.F_FULL,
					locals.length,
					locals,
					0,
					new Object[0]);
		}
	}

	private void makeYieldFrame() {
		if (mergeFrames) {
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			return;
		}
		if (seenVariables == 0) {
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		} else if (seenVariables <= 3) {
			mv.visitFrame(Opcodes.F_CHOP, seenVariables, null, 0, null);
		} else {
			mv.visitFrame(
					Opcodes.F_FULL,
					fullFramePrefix.length,
					fullFramePrefix,
					0,
					new Object[0]);
		}
	}

	private Object[] mergeFrame(Object[] frame) {
		int nLocals = frame.length;
		Object[] locals = new Object[fullFramePrefix.length + nLocals];
		System.arraycopy(fullFramePrefix, 0, locals, 0, fullFramePrefix.length);
		System.arraycopy(frame, 0, locals, fullFramePrefix.length, nLocals);
		return locals;
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

	private ListIterator<VariableInfo> varIterator() {
		// iterate over variables after "locals array"
		int variablesSize = variables.size();
		if (localsStartIndex > variablesSize) {
			return variables.listIterator(variablesSize);
		}
		return variables.listIterator(localsStartIndex);
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

		private final Label					end;

		private final Label					handler;

		private boolean						hasYield;

		private final Label					start;

		private final List<VariableInfo>	variables	= new LinkedList<VariableInfo>();

		public TryCatchBlock(
				Label start,
				Label end,
				Label handler) {
			this.start = start;
			this.end = end;
			this.handler = handler;
		}

	}

	public static final Type	BOOLEAN_ARRAY		= Type
															.getType(boolean[].class);

	public static final Type	BYTE_ARRAY			= Type
															.getType(byte[].class);

	public static final Type	CHAR_ARRAY			= Type
															.getType(char[].class);

	public static final Type	CLASS_TYPE			= Type.getType(Class.class);

	public static final String	COROUTINES_NAME		= "mr/go/coroutines/user/Coroutines";

	public static final Type	DOUBLE_ARRAY		= Type
															.getType(double[].class);

	public static final Type	FLOAT_ARRAY			= Type
															.getType(float[].class);

	public static final Type	INT_ARRAY			= Type.getType(int[].class);

	public static final Type	LONG_ARRAY			= Type
															.getType(long[].class);

	public static final Type	SHORT_ARRAY			= Type
															.getType(short[].class);

	private static final int	maxYieldStatements	= Integer
															.parseInt(System
																	.getProperty(
																			"mr.go.coroutines.MaxYields",
																			"8"));

	private static final int	variableIndexOffset	= 5;

}
