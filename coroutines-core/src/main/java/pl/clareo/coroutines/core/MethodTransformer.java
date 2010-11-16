/*
 * Copyright 2009-2010 Marcin Rzeźnicki

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
package pl.clareo.coroutines.core;

import static pl.clareo.coroutines.core.CodeGenerationUtils.*;
import static pl.clareo.coroutines.core.StringConstants.COROUTINES_NAME;
import static pl.clareo.coroutines.core.StringConstants.COROUTINE_CLOSED_EXCEPTION;
import static pl.clareo.coroutines.core.StringConstants.COROUTINE_EXIT_EXCEPTION;
import static pl.clareo.coroutines.core.StringConstants.COROUTINE_METHOD_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.FRAME_NAME;
import static pl.clareo.coroutines.core.StringConstants.INVALID_COROUTINE_EXCEPTION;

import java.util.*;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

import pl.clareo.coroutines.core.asm.Analyzer;

final class MethodTransformer implements Opcodes {

    private static Type[] diff(Type[] t1, Type[] t2) {
        // it assumes that t1 is at least as long as t2
        Type[] diff = new Type[t1.length];
        for (int i = 0; i < t2.length; i++) {
            if (t1[i] != t2[i]) {
                diff[i] = t1[i];
            }
        }
        if (t1.length > t2.length) {
            System.arraycopy(t1, t2.length, diff, t2.length, t1.length - t2.length);
        }
        return diff;
    }

    private static FrameNode findNextFrame(AbstractInsnNode insn) {
        AbstractInsnNode nextInsn = insn.getNext();
        while (nextInsn != null) {
            if (nextInsn.getType() == AbstractInsnNode.FRAME) {
                return (FrameNode) nextInsn;
            }
            nextInsn = nextInsn.getNext();
        }
        return null;
    }

    private static FrameNode findPreviousFrame(AbstractInsnNode insn) {
        AbstractInsnNode prevInsn = insn.getPrevious();
        while (prevInsn != null) {
            if (prevInsn.getType() == AbstractInsnNode.FRAME) {
                return (FrameNode) prevInsn;
            }
            prevInsn = prevInsn.getPrevious();
        }
        return null;
    }

    private static Class<?> getClass(BasicValue v) throws ClassNotFoundException {
        Type t = v.getType();
        ClassLoader loader = MethodTransformer.class.getClassLoader();
        if (t.getSort() == Type.ARRAY) {
            return Class.forName(t.getDescriptor().replace('/', '.'), false, loader);
        }
        return Class.forName(t.getClassName(), false, loader);
    }

    private static Object getFrameOpcode(Type t) {
        if (t == null) {
            return TOP;
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
                return INTEGER;
            case Type.DOUBLE:
                return DOUBLE;
            case Type.FLOAT:
                return FLOAT;
            case Type.LONG:
                return LONG;
            case Type.VOID:
                return NULL;
            default:
                throw new CoroutineGenerationException("Invalid frame opcode");
        }
    }

    private static Object[] getFrameOpcodes(Type[] ts) {
        Object[] opcodes = new Object[ts.length];
        for (int i = 0; i < ts.length; i++) {
            opcodes[i] = getFrameOpcode(ts[i]);
        }
        return opcodes;
    }

    private static Type[] getLocals(Frame f) {
        int nLocals = f.getLocals();
        Type[] locals = new Type[nLocals];
        for (int i = 0; i < locals.length; i++) {
            locals[i] = ((BasicValue) f.getLocal(i)).getType();
        }
        return locals;
    }

    private static Type[] getStackContents(Frame f) {
        int nStack = f.getStackSize();
        Type[] stackContents = new Type[nStack];
        int top = 0;
        for (int i = nStack - 1; i >= 0; i--, top++) {
            stackContents[top] = getTypeFromStack(f, i);
        }
        return stackContents;
    }

    private static Type getStackTop(Frame f) {
        return getTypeFromStack(f, f.getStackSize() - 1);
    }

    private static Type getTypeFromStack(Frame f, int i) {
        return ((BasicValue) f.getStack(i)).getType();
    }

    private static InsnList loadstack(int frameIndex, Type[] stackTypes, int stackTop) {
        InsnList insn = new InsnList();
        int top = stackTypes.length - 1;
        insn.add(new VarInsnNode(ALOAD, frameIndex));
        insn.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "getOperands", "()[Ljava/lang/Object;"));
        for (int i = top; i >= stackTop; i--) {
            Type stackType = stackTypes[i];
            int typeSort = stackType.getSort();
            if (typeSort == Type.VOID) {
                insn.add(new InsnNode(ACONST_NULL));
                insn.add(new InsnNode(SWAP));
                continue;
            }
            insn.add(new InsnNode(DUP));
            // stack: array array
            insn.add(makeInt(i - stackTop));
            insn.add(new InsnNode(AALOAD));
            // stack: array element
            switch (typeSort) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    insn.add(unbox_int(typeSort));
                    insn.add(new InsnNode(SWAP));
                break;
                case Type.FLOAT:
                    insn.add(unbox_float(typeSort));
                    insn.add(new InsnNode(SWAP));
                break;
                case Type.LONG:
                    insn.add(unbox_long(typeSort));
                    insn.add(new InsnNode(DUP2_X1));
                    insn.add(new InsnNode(POP2));
                break;
                case Type.DOUBLE:
                    insn.add(unbox_double(typeSort));
                    insn.add(new InsnNode(DUP2_X1));
                    insn.add(new InsnNode(POP2));
                break;
                case Type.ARRAY:
                case Type.OBJECT:
                    insn.add(new TypeInsnNode(CHECKCAST, stackType.getInternalName()));
                    insn.add(new InsnNode(SWAP));
                break;
            }
            // stack: element array
        }
        insn.add(new InsnNode(POP));
        return insn;
    }

    private static InsnList savestack(int frameIndex, Type[] stack, int stackTop) {
        InsnList insn = new InsnList();
        int top = stack.length - 1;
        insn.add(makeInt(stack.length - stackTop));
        insn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        // stack: element array
        for (int i = stackTop; i <= top; i++) {
            Type stackType = stack[i];
            int typeSort = stackType.getSort();
            switch (typeSort) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    insn.add(new InsnNode(DUP_X1));
                    insn.add(new InsnNode(SWAP));
                    insn.add(box_int(typeSort));
                break;
                case Type.FLOAT:
                    insn.add(new InsnNode(DUP_X1));
                    insn.add(new InsnNode(SWAP));
                    insn.add(box_float(typeSort));
                break;
                case Type.LONG:
                    insn.add(new InsnNode(DUP_X2));
                    insn.add(new InsnNode(DUP_X2));
                    insn.add(new InsnNode(POP));
                    insn.add(box_long(typeSort));
                break;
                case Type.DOUBLE:
                    insn.add(new InsnNode(DUP_X2));
                    insn.add(new InsnNode(DUP_X2));
                    insn.add(new InsnNode(POP));
                    insn.add(box_double(typeSort));
                break;
                case Type.ARRAY:
                case Type.OBJECT:
                case Type.VOID:
                    insn.add(new InsnNode(DUP_X1));
                    insn.add(new InsnNode(SWAP));
                break;
            }
            // stack: array array element
            insn.add(makeInt(i - stackTop));
            insn.add(new InsnNode(SWAP));
            // stack: array array int element
            insn.add(new InsnNode(AASTORE));
        }
        insn.add(new VarInsnNode(ALOAD, frameIndex));
        insn.add(new InsnNode(SWAP));
        insn.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "setOperands", "([Ljava/lang/Object;)V"));
        return insn;
    }

    private final Object[]                  argsStackMapWithThis;
    private final boolean[]                 finals;
    private final int                       frame;
    private final int                       in;
    private final boolean                   isStatic;
    private final Map<LabelNode, LabelNode> labelsMap  = new HashMap<LabelNode, LabelNode>();
    private int                             lineNumber;
    private final int                       localsArray;
    private final int                       localsStartIndex;
    private final MethodNode                method;
    private final Type[]                    methodArguments;
    private final String                    methodOwner;
    private final int                       out;
    private final int                       state;
    private final LabelNode                 yieldLabel = new LabelNode();

    MethodTransformer(MethodNode method, Type owner) {
        this.method = method;
        this.methodOwner = owner.getInternalName();
        this.finals = new boolean[method.maxLocals];
        isStatic = (method.access & ACC_STATIC) != 0;
        methodArguments = Type.getArgumentTypes(method.desc);
        int localIndex;
        if (isStatic) {
            this.frame = 0;
            this.in = 1;
            this.out = 2;
            this.state = 3;
            this.localsArray = 4;
            localIndex = 0;
            argsStackMapWithThis = null;
        } else {
            this.frame = 1;
            this.in = 2;
            this.out = 3;
            this.state = 4;
            this.localsArray = 5;
            finals[0] = true;
            localIndex = 1;
            List<Object> argsStackMapWithThis = new ArrayList<Object>(argsStackMapList);
            argsStackMapWithThis.add(0, methodOwner);
            this.argsStackMapWithThis = argsStackMapWithThis.toArray();
        }
        for (int i = 0; i < methodArguments.length; i++) {
            finals[localIndex] = true;
            localIndex += methodArguments[i].getSize();
        }
        this.localsStartIndex = localsArray + 1;
    }

    private InsnList codeAfter() {
        InsnList insn = new InsnList();
        insn.add(yieldLabel);
        insn.add(emitCleanFrame());
        insn.add(new VarInsnNode(ALOAD, in));
        insn.add(new InsnNode(ARETURN));
        insn.add(new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
        insn.add(throwex(INVALID_COROUTINE_EXCEPTION));
        return insn;
    }

    private InsnList codeBefore(List<LabelNode> gotos) {
        InsnList insn = new InsnList();
        /*
         * int state = frame.getState(); Object[] locals = frame.getLocals();
         */
        {
            insn.add(new VarInsnNode(ALOAD, frame));
            insn.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "getState", "()I"));
            insn.add(new VarInsnNode(ISTORE, state));
            insn.add(new VarInsnNode(ALOAD, frame));
            insn.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "getLocals", "()[Ljava/lang/Object;"));
            insn.add(new VarInsnNode(ASTORE, localsArray));
        }
        /*
         * if (chkclosed) throw closed;
         */
        {
            insn.add(new VarInsnNode(ILOAD, state));
            insn.add(new InsnNode(ICONST_M1));
            LabelNode exitBranch = new LabelNode();
            insn.add(new JumpInsnNode(IF_ICMPNE, exitBranch));
            insn.add(throwex(COROUTINE_CLOSED_EXCEPTION));
            insn.add(exitBranch);
            // frame: [(this)?, frame, in, out, state, locals]
            insn.add(new FrameNode(F_APPEND, 2, new Object[] { INTEGER, "[Ljava/lang/Object;" }, 0, EMPTY_STACK));
        }
        /*
         * switch(state){case 0: break; case n: goto yield; case _wrong_: throw
         * invalid;} arg1, arg2, arg3 = locals[i..j] if (chkexit) throw closed;
         */
        {
            insn.add(new VarInsnNode(ILOAD, state));
            LabelNode defaultBranch = new LabelNode();
            gotos.add(0, new LabelNode());
            int sz = gotos.size();
            LabelNode[] handlers = new LabelNode[sz];
            for (int i = 0; i < sz; i++) {
                handlers[i] = new LabelNode();
            }
            insn.add(new TableSwitchInsnNode(0, sz - 1, defaultBranch, handlers));
            for (int i = 0; i < sz; i++) {
                LabelNode li = handlers[i];
                LabelNode gotoi = gotos.get(i);
                insn.add(li);
                insn.add(new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
                insn.add(new JumpInsnNode(GOTO, gotoi));
            }
            insn.add(defaultBranch);
            insn.add(new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
            insn.add(throwex(INVALID_COROUTINE_EXCEPTION));
            insn.add(gotos.get(0));
            insn.add(new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
            insn.add(getlocs(localsArray, localsStartIndex, isStatic ? 0 : 1, methodArguments));
            insn.add(new VarInsnNode(ALOAD, frame));
            insn.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "isCoroutineClosed", "()Z"));
            LabelNode continueHere = new LabelNode();
            insn.add(new JumpInsnNode(IFEQ, continueHere));
            insn.add(throwex(COROUTINE_CLOSED_EXCEPTION));
            insn.add(continueHere);
            insn.add(mergeFrames(methodArguments));
            // add NOP in case that user code contains stack map here
            insn.add(new InsnNode(NOP));
            gotos.remove(0);
        }
        return insn;
    }

    private FrameNode emitCatchFrame(String exceptionType) {
        FrameNode frame = emitCleanFrame();
        frame.stack = Arrays.asList(exceptionType);
        return frame;
    }

    private FrameNode emitCleanFrame() {
        // emit clean frame
        if (isStatic) {
            return new FrameNode(F_FULL, argsStackMapSize, argsStackMapArray, 0, new Object[0]);
        }
        return new FrameNode(F_FULL, argsStackMapWithThis.length, argsStackMapWithThis, 0, new Object[0]);
    }

    private Type[] getFrameTypes(Type[] locals) {
        int nLocals = locals.length;
        List<Type> types = new ArrayList<Type>(nLocals);
        int i = isStatic ? 0 : 1;
        while (i < nLocals) {
            Type t = locals[i];
            types.add(t);
            if (t != null) {
                i += t.getSize();
            } else {
                i += 1;
            }
        }
        // remove all uninitialized types from the end of this array
        ListIterator<Type> j = types.listIterator(types.size());
        while (j.hasPrevious()) {
            if (j.previous() == null) {
                j.remove();
            } else {
                break;
            }
        }
        Type[] result = new Type[types.size()];
        return types.toArray(result);
    }

    private InsnList input(Type type) {
        InsnList insn = new InsnList();
        if (type != null) {
            int typeSort = type.getSort();
            switch (typeSort) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    insn.add(box_int(typeSort));
                break;
                case Type.FLOAT:
                    insn.add(box_float(typeSort));
                break;
                case Type.LONG:
                    insn.add(box_long(typeSort));
                break;
                case Type.DOUBLE:
                    insn.add(box_double(typeSort));
                break;
                case Type.ARRAY:
                case Type.OBJECT:
                case Type.VOID:
                break;
            }
        }
        insn.add(new VarInsnNode(ASTORE, in));
        return insn;
    }

    private FrameNode mergeFrames(Type[] locals) {
        int nLocals = locals.length;
        if (nLocals == 0) {
            return new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK);
        }
        if (nLocals <= 3) {
            return new FrameNode(F_APPEND, nLocals, getFrameOpcodes(locals), 0, EMPTY_STACK);
        }
        Object[] fullLocals = mergeLocals(locals, nLocals);
        return new FrameNode(F_FULL, fullLocals.length, fullLocals, 0, new Object[0]);
    }

    private FrameNode mergeFrames(Type[] locals, Type[] stack) {
        int nLocals = locals.length;
        int nStack = stack != null ? stack.length : 0;
        if (nLocals <= 3 && nStack == 0) {
            if (nLocals == 0) {
                return new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK);
            }
            return new FrameNode(F_APPEND, nLocals, getFrameOpcodes(locals), 0, EMPTY_STACK);
        }
        if (nLocals == 0 && nStack == 1) {
            return new FrameNode(F_SAME1, 0, EMPTY_LOCALS, 1, new Object[] { getFrameOpcode(stack[0]) });
        }
        Object[] mergedLocals = mergeLocals(locals, nLocals);
        if (nStack > 0) {
            return new FrameNode(F_FULL, mergedLocals.length, mergedLocals, nStack, getFrameOpcodes(stack));
        }
        return new FrameNode(F_FULL, mergedLocals.length, mergedLocals, 0, new Object[0]);
    }

    private Object[] mergeLocals(Type[] locals, int nLocals) {
        Object[] fullLocals;
        if (isStatic) {
            fullLocals = new Object[nLocals + argsStackMapSize];
            System.arraycopy(argsStackMapArray, 0, fullLocals, 0, argsStackMapSize);
            System.arraycopy(getFrameOpcodes(locals), 0, fullLocals, argsStackMapSize, nLocals);
        } else {
            fullLocals = new Object[nLocals + argsStackMapSize + 1];
            fullLocals[0] = methodOwner;
            System.arraycopy(argsStackMapArray, 0, fullLocals, 1, argsStackMapSize);
            System.arraycopy(getFrameOpcodes(locals), 0, fullLocals, 1 + argsStackMapSize, nLocals);
        }
        return fullLocals;
    }

    private InsnList restoreLocals(Type[] locals) {
        // restore saved locals
        int varIndex = isStatic ? 0 : 1;
        int nLocals = locals.length;
        InsnList insn = new InsnList();
        while (varIndex < nLocals) {
            Type t = locals[varIndex];
            if (t != null) {
                insn.add(getloc(localsArray, varIndex + variableIndexOffset, varIndex, t));
                varIndex += t.getSize();
            } else {
                varIndex += 1;
            }
        }
        return insn;
    }

    private InsnList saveLocals(Type[] locals) {
        InsnList insn = new InsnList();
        int nLocals = locals.length;
        for (int i = 0; i < nLocals; i++) {
            Type local = locals[i];
            if (local != null && !finals[i]) {
                insn.add(saveloc(localsArray, i + variableIndexOffset, i, local));
                finals[i] = true;
            }
        }
        return insn;
    }

    @SuppressWarnings("unchecked")
    MethodNode transform(String coroutineName, boolean generateDebugCode) {
        MethodNode transformedMethod = new MethodNode();
        transformedMethod.access = ACC_PUBLIC | ACC_FINAL | (method.access & ACC_STATIC);
        transformedMethod.name = coroutineName;
        transformedMethod.desc = COROUTINE_METHOD_DESCRIPTOR;
        transformedMethod.exceptions = method.exceptions;
        final InsnList newCode = transformedMethod.instructions;
        Analyzer analyzer = new Analyzer(new BasicInterpreter() {

            @Override
            public Value binaryOperation(AbstractInsnNode insn, Value value1, Value value2) throws AnalyzerException {
                if (insn.getOpcode() == AALOAD) {
                    return new BasicValue(((BasicValue) value1).getType().getElementType());
                }
                return super.binaryOperation(insn, value1, value2);
            };

            @Override
            public Value merge(Value v, Value w) {
                if (v == NULL_VALUE) {
                    BasicValue w1 = (BasicValue) w;
                    if (w1.isReference()) {
                        return w1;
                    }
                }
                if (w == NULL_VALUE) {
                    BasicValue v1 = (BasicValue) v;
                    if (v1.isReference())
                        return v1;
                }
                if (!v.equals(w)) {
                    BasicValue v1 = (BasicValue) v;
                    BasicValue w1 = (BasicValue) w;
                    if (v1.isReference() & w1.isReference()) {
                        Class<?> c1;
                        Class<?> c2;
                        try {
                            c1 = MethodTransformer.getClass(v1);
                            c2 = MethodTransformer.getClass(w1);
                        } catch (ClassNotFoundException e) {
                            throw new CoroutineGenerationException(
                                                                   "It was needed to load a class during transformation process but loading unexpectedly failed",
                                                                   e);
                        }
                        if (c1.isAssignableFrom(c2)) {
                            return v;
                        }
                        if (c2.isAssignableFrom(c1)) {
                            return w;
                        }
                    }
                } else {
                    return v;
                }
                return BasicValue.UNINITIALIZED_VALUE;
            }

            @Override
            public Value newValue(Type type) {
                if (type != null) {
                    int typeSort = type.getSort();
                    switch (typeSort) {
                        case Type.VOID:
                            return NULL_VALUE;
                        case Type.BOOLEAN:
                        case Type.CHAR:
                        case Type.BYTE:
                        case Type.SHORT:
                        case Type.INT:
                            return BasicValue.INT_VALUE;
                        case Type.FLOAT:
                            return BasicValue.FLOAT_VALUE;
                        case Type.LONG:
                            return BasicValue.LONG_VALUE;
                        case Type.DOUBLE:
                            return BasicValue.DOUBLE_VALUE;
                        case Type.ARRAY:
                        case Type.OBJECT:
                            if (type.getInternalName().equals("null")) {
                                return NULL_VALUE;
                            }
                            return new BasicValue(type);
                        default:
                            throw new Error("Internal error");
                    }
                }
                return BasicValue.UNINITIALIZED_VALUE;
            }
        });
        Frame[] frames;
        try {
            frames = analyzer.analyze(methodOwner, method);
        } catch (AnalyzerException e) {
            throw new CoroutineGenerationException(e);
        }
        InsnList code = method.instructions;
        final List<Integer> yields = new ArrayList<Integer>(8);
        /*
         * Copy instructions patching variable indexes and frames, remember
         * yield indexes
         */
        int ic = 0;
        Iterator<AbstractInsnNode> i = code.iterator();
        while (i.hasNext()) {
            AbstractInsnNode insn = i.next();
            switch (insn.getType()) {
                case AbstractInsnNode.FRAME:
                    FrameNode frame = (FrameNode) insn.clone(labelsMap);
                    // update values
                    if (frame.type == F_FULL) {
                        if (isStatic) {
                            frame.local.addAll(0, argsStackMapList);
                        } else {
                            frame.local.addAll(1, argsStackMapList);
                        }
                    }
                    newCode.add(frame);
                break;
                case AbstractInsnNode.IINC_INSN:
                    IincInsnNode iinc = (IincInsnNode) insn.clone(labelsMap);
                    iinc.var += variableIndexOffset;
                    newCode.add(iinc);
                break;
                case AbstractInsnNode.INSN:
                    switch (insn.getOpcode()) {
                        case DRETURN:
                        case FRETURN:
                        case IRETURN:
                        case LRETURN:
                        case ARETURN:
                        case RETURN:
                            newCode.add(new InsnNode(POP));
                            newCode.add(throwex("java/util/NoSuchElementException"));
                        break;
                        default:
                            newCode.add(insn.clone(labelsMap));
                    }
                break;
                case AbstractInsnNode.JUMP_INSN:
                    if (insn.getOpcode() == JSR) {
                        throw new CoroutineGenerationException("<jsr> not allowed");
                    }
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    LabelNode target = jump.label;
                    if (!labelsMap.containsKey(target)) {
                        labelsMap.put(target, new LabelNode());
                    }
                    newCode.add(jump.clone(labelsMap));
                break;
                case AbstractInsnNode.LABEL:
                    if (!labelsMap.containsKey(insn)) {
                        labelsMap.put((LabelNode) insn, new LabelNode());
                    }
                    newCode.add(insn.clone(labelsMap));
                break;
                case AbstractInsnNode.METHOD_INSN:
                    if (insn.getOpcode() == INVOKESTATIC) {
                        MethodInsnNode method = (MethodInsnNode) insn;
                        if (method.owner.equals(COROUTINES_NAME)) {
                            String methodName = method.name;
                            if (methodName.equals("_")) {
                                /*
                                 * a call to artificial CoIterator, since it is
                                 * not needed after instrumentation we replace
                                 * it with null. This null will be popped by
                                 * corresponding ARETURN. Stack is not changed
                                 */
                                newCode.add(new InsnNode(ACONST_NULL));
                                break;
                            }
                            if (methodName.equals("yield")) {
                                /*
                                 * a call to yield - core of coroutine
                                 * processing
                                 */
                                yields.add(ic);
                            }
                        }
                    }
                    newCode.add(insn.clone(labelsMap));
                break;
                case AbstractInsnNode.VAR_INSN:
                    if (insn.getOpcode() == RET) {
                        throw new CoroutineGenerationException("<ret> not allowed");
                    }
                    VarInsnNode var = (VarInsnNode) insn.clone(labelsMap);
                    if (var.var != 0 || isStatic) {
                        var.var += variableIndexOffset;
                    }
                    newCode.add(var);
                break;
                default:
                    newCode.add(insn.clone(labelsMap));
                break;
            }
            ic += 1;
        }
        /*
         * patch yields in transformed code
         */
        final List<LabelNode> gotos = new ArrayList<LabelNode>(9);
        final Set<TryCatchBlockNode> patchedTryCatchBlocks = new HashSet<TryCatchBlockNode>();
        int yieldIndex = 0;
        i = newCode.iterator();
        while (i.hasNext()) {
            AbstractInsnNode insn = i.next();
            /*
             * track locals
             */
            int insnType = insn.getType();
            if (insnType == AbstractInsnNode.VAR_INSN) {
                int opcode = insn.getOpcode();
                if (opcode == ASTORE || opcode == DSTORE || opcode == LSTORE || opcode == ISTORE || opcode == FSTORE) {
                    int varIndex = ((VarInsnNode) insn).var - variableIndexOffset;
                    finals[varIndex] = false;
                }
                continue;
            }
            /*
             * track line numbers
             */
            if (insnType == AbstractInsnNode.LINE) {
                lineNumber = ((LineNumberNode) insn).line;
                continue;
            }
            if (insnType != AbstractInsnNode.METHOD_INSN) {
                continue;
            }
            MethodInsnNode method = (MethodInsnNode) insn;
            if (!method.owner.equals(COROUTINES_NAME) || !method.name.equals("yield")) {
                continue;
            }
            InsnList yieldCode = new InsnList();
            int index = yields.get(yieldIndex);
            Frame f = frames[index];
            /*
             * a) operand on the top of stack is passed to the caller, we will
             * save it in 'in' parameter OR there is nothing to be passed to the
             * caller in case of yield() overload
             */
            boolean yieldWithArgument = Type.getArgumentTypes(method.desc).length != 0;
            boolean nonemptyStack;
            Type[] stackContents = null;
            int stackTop = 0;
            if (yieldWithArgument) {
                yieldCode.add(input(getStackTop(f)));
                nonemptyStack = f.getStackSize() > 1;
                stackTop = 1;
            } else {
                nonemptyStack = f.getStackSize() > 0;
            }
            /*
             * b) save remaining stack
             */
            if (nonemptyStack) {
                stackContents = getStackContents(f);
                // sanitize stack
                for (Type t : stackContents) {
                    if (t == null) {
                        throw new CoroutineGenerationException(
                                                               "It is not possible to yield with uninitialized memory on the stack. Probably you use construct such as: new A(..,yield,..). Please move this yield call out of constructor");
                    }
                }
                yieldCode.add(savestack(frame, stackContents, stackTop));
            }
            /*
             * c) save locals and state
             */
            Type[] locals = getLocals(f);
            yieldCode.add(saveLocals(locals));
            yieldCode.add(new VarInsnNode(ALOAD, frame));
            yieldCode.add(makeInt(++yieldIndex));
            yieldCode.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "setState", "(I)V"));
            /*
             * d) jump to exit - in debug mode save line number
             */
            if (generateDebugCode) {
                yieldCode.add(new VarInsnNode(ALOAD, frame));
                yieldCode.add(makeInt(lineNumber));
                yieldCode.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "setLineOfCode", "(I)V"));
            }
            yieldCode.add(new JumpInsnNode(GOTO, yieldLabel));
            /*
             * e) fix jump from switch statement
             */
            LabelNode jump = new LabelNode();
            gotos.add(jump);
            yieldCode.add(jump);
            yieldCode.add(emitCleanFrame());
            /*
             * f) check if exit condition occurs, load locals, restore stack and
             * stack map
             */
            yieldCode.add(new VarInsnNode(ALOAD, frame));
            yieldCode.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_NAME, "isCoroutineClosed", "()Z"));
            LabelNode continueHere = new LabelNode();
            yieldCode.add(new JumpInsnNode(IFEQ, continueHere));
            yieldCode.add(throwex(COROUTINE_EXIT_EXCEPTION));
            yieldCode.add(continueHere);
            yieldCode.add(new FrameNode(F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
            /*
             * find previous frame node, load locals then emit new frame node
             * and load rest
             */
            FrameNode prevFrame = findPreviousFrame(code.get(index));
            Type[] prevLocals;
            if (prevFrame != null) {
                Frame oldFrame = frames[code.indexOf(prevFrame)];
                prevLocals = getLocals(oldFrame);
            } else {
                prevLocals = getLocals(frames[0]);
            }
            yieldCode.add(restoreLocals(prevLocals));
            FrameNode frameNode = mergeFrames(getFrameTypes(prevLocals), null);
            if (frameNode.type != F_SAME) {
                // bug fix - when no locals are restored and the stack is empty
                // two frames are collapsed
                yieldCode.add(frameNode);
            }
            if (nonemptyStack) {
                yieldCode.add(loadstack(frame, stackContents, stackTop));
            }
            // restore temp locals in scope
            yieldCode.add(restoreLocals(diff(locals, prevLocals)));
            /*
             * push "sent" value
             */
            yieldCode.add(new VarInsnNode(ALOAD, out));
            newCode.insertBefore(method, yieldCode);
            /*
             * patch try catch blocks
             */
            List<TryCatchBlockNode> tryCatchBlocks = analyzer.getHandlers(index);
            if (tryCatchBlocks != null) {
                for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks) {
                    if (!patchedTryCatchBlocks.contains(tryCatchBlock)) {
                        LabelNode handler = tryCatchBlock.handler;
                        InsnList handlerPatch = new InsnList();
                        String exceptionType = tryCatchBlock.type == null ? "java/lang/Throwable" : tryCatchBlock.type;
                        FrameNode catchFrame = emitCatchFrame(exceptionType);
                        handlerPatch.add(catchFrame);
                        Type[] ls = getLocals(frames[code.indexOf(handler)]);
                        handlerPatch.add(restoreLocals(ls));
                        handlerPatch.add(mergeFrames(getFrameTypes(ls),
                                                     new Type[] { Type.getObjectType(exceptionType) }));
                        patchedTryCatchBlocks.add(tryCatchBlock);
                        AbstractInsnNode newHandler = labelsMap.get(handler);
                        // remove "real" frame since it is not needed now
                        newCode.remove(findNextFrame(newHandler));
                        newCode.insert(newHandler, handlerPatch);
                    }
                }
            }
            newCode.remove(method);
        }
        /*
         * copy local variables (wath out for indices change) and try catch
         * blocks to new method (clone)
         */
        List<TryCatchBlockNode> tryCatchBlocks = method.tryCatchBlocks;
        if (!tryCatchBlocks.isEmpty()) {
            transformedMethod.tryCatchBlocks = new ArrayList<TryCatchBlockNode>(tryCatchBlocks.size());
            for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks) {
                transformedMethod.tryCatchBlocks.add(new TryCatchBlockNode(labelsMap.get(tryCatchBlock.start),
                                                                           labelsMap.get(tryCatchBlock.end),
                                                                           labelsMap.get(tryCatchBlock.handler),
                                                                           tryCatchBlock.type));
            }
        }
        if (method.localVariables != null) {
            List<LocalVariableNode> localVariables = method.localVariables;
            List<LocalVariableNode> newLocalVariables = new ArrayList<LocalVariableNode>(localVariables.size());
            for (LocalVariableNode localVariable : localVariables) {
                int newIndex = localVariable.index;
                if (newIndex != 0 || isStatic) {
                    newIndex += variableIndexOffset;
                }
                newLocalVariables.add(new LocalVariableNode(localVariable.name, localVariable.desc,
                                                            localVariable.signature,
                                                            labelsMap.get(localVariable.start),
                                                            labelsMap.get(localVariable.end), newIndex));
            }
            transformedMethod.localVariables = newLocalVariables;
        }
        newCode.insert(codeBefore(gotos));
        newCode.add(codeAfter());
        return transformedMethod;
    }

    private static final Object[]     argsStackMapArray   = new Object[] { FRAME_NAME, "java/lang/Object",
            "java/lang/Object", INTEGER, "[Ljava/lang/Object;" };
    private static final List<Object> argsStackMapList    = Arrays.asList(argsStackMapArray);
    private static final int          argsStackMapSize    = 5;
    private static final Value        BOOLEAN_VALUE       = new BasicValue(Type.BOOLEAN_TYPE);
    private static final Value        BYTE_VALUE          = new BasicValue(Type.BYTE_TYPE);
    private static final Value        CHAR_VALUE          = new BasicValue(Type.CHAR_TYPE);
    private static final Value        NULL_VALUE          = new BasicValue(Type.VOID_TYPE);
    private static final Value        SHORT_VALUE         = new BasicValue(Type.SHORT_TYPE);
    private static final int          variableIndexOffset = 5;
}
