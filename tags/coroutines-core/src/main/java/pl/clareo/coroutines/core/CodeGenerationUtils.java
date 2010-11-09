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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class CodeGenerationUtils implements Opcodes {

    static InsnList box_double(int typeSort) {
        return box_double(-1, typeSort);
    }

    static InsnList box_double(int varIndex, int typeSort) {
        InsnList insn = new InsnList();
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(DLOAD, varIndex));
        }
        if (typeSort != Type.DOUBLE) {
            throw new CoroutineGenerationException("box_double:Not a double");
        }
        insn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;"));
        return insn;
    }

    static InsnList box_float(int typeSort) {
        return box_float(-1, typeSort);
    }

    static InsnList box_float(int varIndex, int typeSort) {
        InsnList insn = new InsnList();
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(FLOAD, varIndex));
        }
        if (typeSort != Type.FLOAT) {
            throw new CoroutineGenerationException("box_float:Not a float");
        }
        insn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;"));
        return insn;
    }

    static InsnList box_int(int typeSort) {
        return box_int(-1, typeSort);
    }

    static InsnList box_int(int varIndex, int typeSort) {
        InsnList result = new InsnList();
        if (varIndex >= 0) {
            result.add(new VarInsnNode(ILOAD, varIndex));
        }
        switch (typeSort) {
            case Type.BOOLEAN:
                result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"));
            break;
            case Type.CHAR:
                result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf",
                                              "(C)Ljava/lang/Character;"));
            break;
            case Type.BYTE:
                result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;"));
            break;
            case Type.SHORT:
                result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;"));
            break;
            case Type.INT:
                result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"));
            break;
            default:
                throw new CoroutineGenerationException("box_int:Not an int");
        }
        return result;
    }

    static InsnList box_long(int typeSort) {
        return box_long(-1, typeSort);
    }

    static InsnList box_long(int varIndex, int typeSort) {
        InsnList insn = new InsnList();
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(LLOAD, varIndex));
        }
        if (typeSort != Type.LONG) {
            throw new CoroutineGenerationException("box_long:Not a long");
        }
        insn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;"));
        return insn;
    }

    static InsnList getloc(int frameArrayIndex, int varIndex, int fromIndex, Type type) {
        InsnList insn = new InsnList();
        int typeSort = type.getSort();
        if (typeSort == Type.VOID) {
            insn.add(new InsnNode(ACONST_NULL));
            insn.add(new VarInsnNode(ASTORE, varIndex));
        } else {
            insn.add(new VarInsnNode(ALOAD, frameArrayIndex));
            insn.add(makeInt(fromIndex));
            insn.add(new InsnNode(AALOAD));
            switch (typeSort) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    insn.add(unbox_int(varIndex, typeSort));
                break;
                case Type.FLOAT:
                    insn.add(unbox_float(varIndex, typeSort));
                break;
                case Type.LONG:
                    insn.add(unbox_long(varIndex, typeSort));
                break;
                case Type.DOUBLE:
                    insn.add(unbox_double(varIndex, typeSort));
                break;
                case Type.ARRAY:
                case Type.OBJECT:
                    if (!type.equals(JAVA_LANG_OBJECT)) {
                        insn.add(new TypeInsnNode(CHECKCAST, type.getInternalName()));
                    }
                    insn.add(new VarInsnNode(ASTORE, varIndex));
                break;
            }
        }
        return insn;
    }

    static InsnList getlocs(int frameArrayIndex, int startIndex, int fromIndex, Type... types) {
        int varIndex = startIndex;
        int i = 0;
        InsnList insn = new InsnList();
        while (i < types.length) {
            Type type = types[i];
            insn.add(getloc(frameArrayIndex, varIndex, fromIndex, type));
            varIndex += type.getSize();
            fromIndex += type.getSize();
            i++;
        }
        return insn;
    }

    static AbstractInsnNode makeInt(int val) {
        AbstractInsnNode result;
        switch (val) {
            case 0:
                result = new InsnNode(ICONST_0);
            break;
            case 1:
                result = new InsnNode(ICONST_1);
            break;
            case 2:
                result = new InsnNode(ICONST_2);
            break;
            case 3:
                result = new InsnNode(ICONST_3);
            break;
            case 4:
                result = new InsnNode(ICONST_4);
            break;
            case 5:
                result = new InsnNode(ICONST_5);
            break;
            default:
                if (val <= Byte.MAX_VALUE) {
                    result = new IntInsnNode(BIPUSH, val);
                } else if (val <= Short.MAX_VALUE) {
                    result = new IntInsnNode(SIPUSH, val);
                } else {
                    result = new LdcInsnNode(Integer.valueOf(val));
                }
        }
        return result;
    }

    static void newInstance(Type type, Type asType, MethodVisitor mv) {
        mv.visitLdcInsn(type.getClassName());
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "newInstance", "()Ljava/lang/Object;");
        mv.visitTypeInsn(CHECKCAST, asType.getInternalName());
    }

    static InsnList saveloc(int frameArrayIndex, int varIndex, int toIndex, Type type) {
        InsnList insn = new InsnList();
        insn.add(new VarInsnNode(ALOAD, frameArrayIndex));
        insn.add(makeInt(toIndex));
        int typeSort = type.getSort();
        switch (typeSort) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                insn.add(box_int(varIndex, typeSort));
            break;
            case Type.FLOAT:
                insn.add(box_float(varIndex, typeSort));
            break;
            case Type.LONG:
                insn.add(box_long(varIndex, typeSort));
            break;
            case Type.DOUBLE:
                insn.add(box_double(varIndex, typeSort));
            break;
            case Type.ARRAY:
            case Type.OBJECT:
                insn.add(new VarInsnNode(ALOAD, varIndex));
            break;
            case Type.VOID:
                insn.add(new InsnNode(ACONST_NULL));
            break;
        }
        insn.add(new InsnNode(AASTORE));
        return insn;
    }

    static InsnList savelocs(int frameArrayIndex, int startIndex, int toIndex, Type... types) {
        InsnList insn = new InsnList();
        int varIndex = startIndex;
        int i = 0;
        while (i < types.length) {
            Type type = types[i];
            insn.add(saveloc(frameArrayIndex, varIndex, toIndex, type));
            varIndex += type.getSize();
            toIndex += type.getSize();
            i++;
        }
        return insn;
    }

    static InsnList throwex(String ex) {
        InsnList insn = new InsnList();
        insn.add(new TypeInsnNode(NEW, ex));
        insn.add(new InsnNode(DUP));
        insn.add(new MethodInsnNode(INVOKESPECIAL, ex, "<init>", "()V"));
        insn.add(new InsnNode(ATHROW));
        return insn;
    }

    static InsnList unbox_double(int typeSort) {
        return unbox_double(-1, typeSort);
    }

    static InsnList unbox_double(int varIndex, int typeSort) {
        if (typeSort != Type.DOUBLE) {
            throw new CoroutineGenerationException("unbox_double:Not a double");
        }
        InsnList insn = new InsnList();
        insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Double"));
        insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D"));
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(DSTORE, varIndex));
        }
        return insn;
    }

    static InsnList unbox_float(int typeSort) {
        return unbox_float(-1, typeSort);
    }

    static InsnList unbox_float(int varIndex, int typeSort) {
        if (typeSort != Type.FLOAT) {
            throw new CoroutineGenerationException("unbox_float:Not a float");
        }
        InsnList insn = new InsnList();
        insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
        insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F"));
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(FSTORE, varIndex));
        }
        return insn;
    }

    static InsnList unbox_int(int typeSort) {
        return unbox_int(-1, typeSort);
    }

    static InsnList unbox_int(int varIndex, int typeSort) {
        InsnList insn = new InsnList();
        switch (typeSort) {
            case Type.BOOLEAN:
                insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Boolean"));
                insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z"));
            break;
            case Type.CHAR:
                insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Character"));
                insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C"));
            break;
            case Type.BYTE:
                insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Byte"));
                insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B"));
            break;
            case Type.SHORT:
                insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Short"));
                insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S"));
            break;
            case Type.INT:
                insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I"));
            break;
            default:
                throw new CoroutineGenerationException("unbox_int:Not an int");
        }
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(ISTORE, varIndex));
        }
        return insn;
    }

    static InsnList unbox_long(int typeSort) {
        return unbox_long(-1, typeSort);
    }

    static InsnList unbox_long(int varIndex, int typeSort) {
        if (typeSort != Type.LONG) {
            throw new CoroutineGenerationException("unbox_long:Not a long");
        }
        InsnList insn = new InsnList();
        insn.add(new TypeInsnNode(CHECKCAST, "java/lang/Long"));
        insn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J"));
        if (varIndex >= 0) {
            insn.add(new VarInsnNode(LSTORE, varIndex));
        }
        return insn;
    }

    static Object[] EMPTY_LOCALS     = null;
    static Object[] EMPTY_STACK      = null;
    static Type     JAVA_LANG_OBJECT = Type.getType(Object.class);
}
