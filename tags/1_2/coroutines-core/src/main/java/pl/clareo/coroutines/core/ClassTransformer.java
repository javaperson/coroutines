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

import static java.lang.Math.abs;
import static pl.clareo.coroutines.core.CodeGenerationUtils.EMPTY_LOCALS;
import static pl.clareo.coroutines.core.CodeGenerationUtils.EMPTY_STACK;
import static pl.clareo.coroutines.core.CodeGenerationUtils.JAVA_LANG_OBJECT;
import static pl.clareo.coroutines.core.CodeGenerationUtils.box_int;
import static pl.clareo.coroutines.core.CodeGenerationUtils.makeInt;
import static pl.clareo.coroutines.core.CodeGenerationUtils.saveloc;
import static pl.clareo.coroutines.core.CodeGenerationUtils.savelocs;
import static pl.clareo.coroutines.core.StringConstants.CALL_METHOD_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.COROUTINE_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.COROUTINE_METHOD_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.FRAME_NAME;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class ClassTransformer {

    @SuppressWarnings("unchecked")
    private static InsnList createDebugFrame(MethodNode coroutine) {
        InsnList insn = new InsnList();
        int nLocals = coroutine.maxLocals;
        String[] names = new String[nLocals];
        List<LocalVariableNode> locals = coroutine.localVariables;
        fillVariableNames(names, locals);
        insn.add(new TypeInsnNode(Opcodes.NEW, FRAME_NAME));
        insn.add(new InsnNode(Opcodes.DUP));
        insn.add(makeInt(nLocals));
        insn.add(makeInt(nLocals));
        insn.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        for (LocalVariableNode local : locals) {
            int i = local.index;
            String name = names[i];
            insn.add(new InsnNode(Opcodes.DUP));
            insn.add(makeInt(i));
            if (name != null) {
                insn.add(new LdcInsnNode(name));
            } else {
                insn.add(new InsnNode(Opcodes.ACONST_NULL));
            }
            insn.add(new InsnNode(Opcodes.AASTORE));
        }
        insn.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, FRAME_NAME, "<init>", "(I[Ljava/lang/String;)V"));
        return insn;
    }

    private static InsnList createFrame(MethodNode coroutine) {
        InsnList insn = new InsnList();
        insn.add(new TypeInsnNode(Opcodes.NEW, FRAME_NAME));
        insn.add(new InsnNode(Opcodes.DUP));
        insn.add(makeInt(coroutine.maxLocals));
        insn.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, FRAME_NAME, "<init>", "(I)V"));
        return insn;
    }

    private static void fillVariableNames(String[] names, List<LocalVariableNode> locals) {
        for (LocalVariableNode local : locals) {
            String name = local.name;
            int index = local.index;
            String oldName = names[index];
            if (oldName == null) {
                names[index] = name;
            } else {
                names[index] = oldName + '/' + name;
            }
        }
    }

    private static boolean getBoolean(Map<String, Object> values, String name) {
        return getBoolean(values, name, false);
    }

    private static boolean getBoolean(Map<String, Object> values, String name, boolean defaultBoolean) {
        if (values.containsKey(name)) {
            return (Boolean) values.get(name);
        }
        return defaultBoolean;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getCoroutineAnnotationValues(MethodNode coroutine) {
        Iterator<AnnotationNode> i = coroutine.invisibleAnnotations.iterator();
        while (i.hasNext()) {
            final AnnotationNode an = i.next();
            if (an.desc.equals(COROUTINE_DESCRIPTOR)) {
                Map<String, Object> result = new HashMap<String, Object>();
                if (an.values != null) {
                    Iterator<Object> j = an.values.iterator();
                    while (j.hasNext()) {
                        Object name = j.next();
                        Object value = j.next();
                        result.put((String) name, value);
                    }
                }
                return result;
            }
        }
        return null;
    }

    private static String getCoroutineName(MethodNode method) {
        // little name mangling, let's feel like we were writing C++ compiler
        // :-)
        StringBuilder sb = new StringBuilder(method.name);
        int descHash = abs(Type.getArgumentsAndReturnSizes(method.desc));
        sb.append(descHash);
        Type[] argsTypes = Type.getArgumentTypes(method.desc);
        for (Type t : argsTypes) {
            if (t.getSort() == Type.OBJECT) {
                String argClassname = t.getClassName();
                sb.append('_').append(abs(argClassname.hashCode()));
            } else {
                sb.append('_').append(t.getSort());
            }
        }
        return sb.toString();
    }

    private static InsnList loggingInstructions(String ownerName, String loggerField, Level level, Object... messages) {
        InsnList insn = new InsnList();
        insn.add(new FieldInsnNode(Opcodes.GETSTATIC, ownerName, loggerField, "Ljava/util/logging/Logger;"));
        insn.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/logging/Level", level.getName(),
                                   "Ljava/util/logging/Level;"));
        // stack: * *
        insn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/logging/Logger", "isLoggable",
                                    "(Ljava/util/logging/Level;)Z"));
        // stack: *
        LabelNode exitBranch = new LabelNode();
        insn.add(new JumpInsnNode(Opcodes.IFEQ, exitBranch));
        // stack:
        insn.add(new FieldInsnNode(Opcodes.GETSTATIC, ownerName, loggerField, "Ljava/util/logging/Logger;"));
        insn.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/logging/Level", level.getName(),
                                   "Ljava/util/logging/Level;"));
        // stack: * *
        insn.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        insn.add(new InsnNode(Opcodes.DUP));
        // stack: * * * *
        String message0 = messages[0].toString();
        insn.add(new LdcInsnNode(message0));
        // stack: * * * * *
        insn.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
        // stack: * * *
        for (int m = 1; m < messages.length; m++) {
            Object message = messages[m];
            if (message instanceof Number) {
                insn.add(new VarInsnNode(Opcodes.ALOAD, ((Number) message).intValue()));
            } else {
                insn.add(new LdcInsnNode(message.toString()));
            }
            // stack: * * * *
            insn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
            // stack: * * *
        }
        insn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                                    "()Ljava/lang/String;"));
        insn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/logging/Logger", "log",
                                    "(Ljava/util/logging/Level;Ljava/lang/String;)V"));
        // stack:
        insn.add(exitBranch);
        return insn;
    }

    private final List<MethodNode> coroutines;
    private final boolean          generateDebugCode;
    private final ClassNode        thisNode;
    private final Type             thisType;

    ClassTransformer(ClassNode node, List<MethodNode> coroutines, boolean generateDebugCode) {
        this.coroutines = coroutines;
        this.generateDebugCode = generateDebugCode;
        this.thisNode = node;
        this.thisType = Type.getObjectType(node.name);
    }

    @SuppressWarnings("unchecked")
    void transform() {
        for (MethodNode coroutine : coroutines) {
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Generating method for coroutine " + coroutine.name + coroutine.desc);
            }
            String coroutineName = getCoroutineName(coroutine);
            MethodTransformer methodTransformer = new MethodTransformer(coroutine, thisType);
            MethodNode coroutineImpl = methodTransformer.transform(coroutineName, generateDebugCode);
            thisNode.methods.add(coroutineImpl);
            /*
             * generate co iterators and method stubs
             */
            log.finest("Generating CoIterator implementation and method stubs");
            String baseCoIteratorName;
            Map<String, Object> annotation = getCoroutineAnnotationValues(coroutine);
            if (getBoolean(annotation, "threadLocal")) {
                baseCoIteratorName = Type.getInternalName(ThreadLocalCoIterator.class);
            } else {
                baseCoIteratorName = Type.getInternalName(SingleThreadedCoIterator.class);
            }
            String coIteratorClassName = "pl/clareo/coroutines/core/CoIterator" + num;
            ClassNode coIteratorClass = new ClassNode();
            coIteratorClass.version = Opcodes.V1_6;
            coIteratorClass.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
            coIteratorClass.name = coIteratorClassName;
            coIteratorClass.superName = baseCoIteratorName;
            if (generateDebugCode) {
                /*
                 * If debugging code is emitted create field keeping JDK logger
                 */
                FieldNode loggerField =
                                        new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC,
                                                      "logger", "Ljava/util/logging/Logger;", null, null);
                coIteratorClass.fields.add(loggerField);
                MethodNode clinit = new MethodNode();
                clinit.access = Opcodes.ACC_STATIC;
                clinit.name = "<clinit>";
                clinit.desc = "()V";
                clinit.exceptions = Collections.EMPTY_LIST;
                String loggerName = thisType.getClassName();
                InsnList clinitCode = clinit.instructions;
                clinitCode.add(new LdcInsnNode(loggerName));
                clinitCode.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/logging/Logger", "getLogger",
                                                  "(Ljava/lang/String;)Ljava/util/logging/Logger;"));
                clinitCode.add(new FieldInsnNode(Opcodes.PUTSTATIC, coIteratorClassName, "logger",
                                                 "Ljava/util/logging/Logger;"));
                clinitCode.add(new InsnNode(Opcodes.RETURN));
                clinit.maxStack = 1;
                clinit.maxLocals = 0;
                coIteratorClass.methods.add(clinit);
            }
            /*
             * Generate constructor
             */
            MethodNode init = new MethodNode();
            init.access = Opcodes.ACC_PUBLIC;
            init.name = "<init>";
            init.desc = CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR;
            init.exceptions = Collections.EMPTY_LIST;
            InsnList initCode = init.instructions;
            initCode.add(new VarInsnNode(Opcodes.ALOAD, 0));
            initCode.add(new VarInsnNode(Opcodes.ALOAD, 1));
            initCode.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, baseCoIteratorName, "<init>",
                                            CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR));
            initCode.add(new InsnNode(Opcodes.RETURN));
            init.maxStack = 2;
            init.maxLocals = 2;
            coIteratorClass.methods.add(init);
            /*
             * Generate overriden call to coroutine
             */
            MethodNode call = new MethodNode();
            call.access = Opcodes.ACC_PROTECTED;
            call.name = "call";
            call.desc = CALL_METHOD_DESCRIPTOR;
            call.exceptions = Collections.EMPTY_LIST;
            InsnList callCode = call.instructions;
            /*
             * if debug needed generate call details
             */
            if (generateDebugCode) {
                String coroutineId = "Coroutine " + coroutine.name;
                callCode.add(loggingInstructions(coIteratorClassName, "logger", Level.FINER, coroutineId
                                                                                             + " call. Caller sent: ",
                                                 2));
                callCode.add(new FrameNode(Opcodes.F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
                callCode.add(loggingInstructions(coIteratorClassName, "logger", Level.FINEST, coroutineId + " state ",
                                                 1));
                callCode.add(new FrameNode(Opcodes.F_SAME, 0, EMPTY_LOCALS, 0, EMPTY_STACK));
            }
            /*
             * push call arguments: this (if not static), frame, input, output
             */
            boolean isStatic = (coroutine.access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                callCode.add(new VarInsnNode(Opcodes.ALOAD, 1));
                callCode.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FRAME_NAME, "getThis", "()Ljava/lang/Object;"));
                callCode.add(new TypeInsnNode(Opcodes.CHECKCAST, thisType.getInternalName()));
            }
            callCode.add(new VarInsnNode(Opcodes.ALOAD, 1));
            callCode.add(new InsnNode(Opcodes.ACONST_NULL));
            callCode.add(new VarInsnNode(Opcodes.ALOAD, 2));
            callCode.add(new MethodInsnNode(isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                                            thisType.getInternalName(), coroutineName, COROUTINE_METHOD_DESCRIPTOR));
            // stack: *
            if (!generateDebugCode) {
                callCode.add(new InsnNode(Opcodes.ARETURN));
            } else {
                // save result display suspension point (two more locals
                // needed)
                callCode.add(new VarInsnNode(Opcodes.ASTORE, 3));
                callCode.add(new VarInsnNode(Opcodes.ALOAD, 1));
                callCode.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FRAME_NAME, "getLineOfCode", "()I"));
                callCode.add(box_int(Type.INT));
                callCode.add(new VarInsnNode(Opcodes.ASTORE, 4));
                callCode.add(loggingInstructions(coIteratorClassName, "logger", Level.FINER,
                                                 "Coroutine suspended at line ", 4, ". Yielded:", 3));
                callCode.add(new FrameNode(Opcodes.F_APPEND, 2,
                                           new Object[] { "java/lang/Object", "java/lang/Integer" }, 0, EMPTY_STACK));
                callCode.add(new VarInsnNode(Opcodes.ALOAD, 3));
                callCode.add(new InsnNode(Opcodes.ARETURN));
            }
            coIteratorClass.methods.add(call);
            // if debugging code is emitted it needs space for two
            // additional locals and 5 stack operand
            if (generateDebugCode) {
                call.maxStack = 5;
                call.maxLocals = 5;
            } else {
                if (isStatic) {
                    call.maxStack = 3;
                } else {
                    call.maxStack = 4;
                }
                call.maxLocals = 3;
            }
            /*
             * CoIterator created - define it in the runtime and verify if
             * needed
             */
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Generated class " + coIteratorClassName);
            }
            ClassWriter cw = new ClassWriter(0);
            coIteratorClass.accept(cw);
            byte[] classBytes = cw.toByteArray();
            try {
                CoroutineInstrumentator.dumpClass(coIteratorClassName, classBytes);
            } catch (IOException e) {
                throw new CoroutineGenerationException("Unable to write class " + coIteratorClassName, e);
            }
            /*
             * start generating method - new method is named as the method in
             * user code, it: returns instance of appropriate CoIterator (see
             * above), saves arguments of call
             */
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Instrumenting method " + coroutine.name);
            }
            InsnList code = coroutine.instructions;
            code.clear();
            /*
             * create new Frame
             */
            boolean isDebugFramePossible = generateDebugCode && coroutine.localVariables != null;
            if (isDebugFramePossible) {
                code.add(createDebugFrame(coroutine));
            } else {
                code.add(createFrame(coroutine));
            }
            /*
             * save frame in the first, and locals array in the second local
             * variable
             */
            int argsSize = Type.getArgumentsAndReturnSizes(coroutine.desc) >> 2;
            if (isStatic) {
                argsSize -= 1;
            }
            code.add(new VarInsnNode(Opcodes.ASTORE, argsSize));
            code.add(new VarInsnNode(Opcodes.ALOAD, argsSize));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FRAME_NAME, "getLocals", "()[Ljava/lang/Object;"));
            int localsArrayIndex = argsSize + 1;
            code.add(new VarInsnNode(Opcodes.ASTORE, localsArrayIndex));
            /*
             * save all call arguments (along with this if this method is not
             * static) into locals array
             */
            Type[] argsTypes = Type.getArgumentTypes(coroutine.desc);
            if (!isStatic) {
                code.add(saveloc(localsArrayIndex, 0, 0, JAVA_LANG_OBJECT));
                code.add(savelocs(localsArrayIndex, 1, 1, argsTypes));
            } else {
                code.add(savelocs(localsArrayIndex, 0, 0, argsTypes));
            }
            /*
             * create CoIterator instance with saved frame, make initial call to
             * next if needed and return to caller
             */
            code.add(new TypeInsnNode(Opcodes.NEW, coIteratorClassName));
            code.add(new InsnNode(Opcodes.DUP));
            code.add(new VarInsnNode(Opcodes.ALOAD, argsSize));
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, coIteratorClassName, "<init>",
                                        CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR));
            if (!getBoolean(annotation, "generator", true)) {
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, coIteratorClassName, "next", "()Ljava/lang/Object;"));
                code.add(new InsnNode(Opcodes.POP));
            }
            code.add(new InsnNode(Opcodes.ARETURN));
            /*
             * end method generation; maxs can be statically determined 3
             * operands on stack (call to frame setLocals and CoIterator
             * constructor) + 1 if any argument is long or double (debug frame
             * needs 7 operands for variable names creation); locals = argsSize
             * + 1 reference to frame + 1 array of locals
             */
            if (isDebugFramePossible) {
                coroutine.maxStack = 7;
            } else {
                boolean isCategory2ArgumentPresent = false;
                for (Type argType : argsTypes) {
                    int sort = argType.getSort();
                    if (sort == Type.LONG || sort == Type.DOUBLE) {
                        isCategory2ArgumentPresent = true;
                        break;
                    }
                }
                coroutine.maxStack = isCategory2ArgumentPresent ? 4 : 3;
            }
            coroutine.maxLocals = localsArrayIndex + 1;
            coroutine.localVariables.clear();
            coroutine.tryCatchBlocks.clear();
            num++;
        }
    }

    private static final Logger log = Logger.getLogger("pl.clareo.coroutines.ClassTransformer");
    private static int          num;
}
