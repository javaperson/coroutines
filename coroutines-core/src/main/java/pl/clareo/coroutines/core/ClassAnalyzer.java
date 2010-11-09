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

import static pl.clareo.coroutines.core.StringConstants.COROUTINE_DESCRIPTOR;
import static pl.clareo.coroutines.core.StringConstants.CO_ITERATOR_DESCRIPTOR;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

final class ClassAnalyzer {

    private final ClassNode  classNode;
    private List<MethodNode> coroutineMethods = new LinkedList<MethodNode>();

    ClassAnalyzer(ClassNode classNode) {
        this.classNode = classNode;
    }

    @SuppressWarnings("unchecked")
    void analyze() {
        List<MethodNode> methods = classNode.methods;
        for (MethodNode methodNode : methods) {
            List<AnnotationNode> annotations = methodNode.invisibleAnnotations;
            if (annotations != null) {
                int accessFlags = methodNode.access;
                for (AnnotationNode annotationNode : annotations) {
                    if (annotationNode.desc.equals(COROUTINE_DESCRIPTOR)) {
                        boolean isAbstract = (accessFlags & Opcodes.ACC_ABSTRACT) != 0;
                        boolean isStatic = (accessFlags & Opcodes.ACC_STATIC) != 0;
                        boolean isConstructor = methodNode.name.equals(isStatic ? "<clinit>" : "<init>");
                        boolean returnsCoIterator =
                                                    Type.getReturnType(methodNode.desc).getDescriptor()
                                                        .equals(CO_ITERATOR_DESCRIPTOR);
                        boolean isCorrect = !isAbstract & returnsCoIterator & !isConstructor;
                        if (isAbstract) {
                            logger.warning("Abstract method " + methodNode.name + " annotated as coroutine");
                        }
                        if (!returnsCoIterator) {
                            logger.warning("Method " + methodNode.name + " does not return CoIterator");
                        }
                        if (isConstructor) {
                            logger.warning("Cannot make a coroutine from constructor");
                        }
                        if (isCorrect) {
                            coroutineMethods.add(methodNode);
                        }
                    }
                }
            }
        }
    }

    public List<MethodNode> getCoroutineMethods() {
        return coroutineMethods;
    }

    private static final Logger logger = Logger.getLogger("pl.clareo.coroutines.ClassAnalyzer");
}
