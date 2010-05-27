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

import static mr.go.coroutines.core.StringConstants.COROUTINE_DESCRIPTOR;
import static mr.go.coroutines.core.StringConstants.CO_ITERATOR_DESCRIPTOR;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.EmptyVisitor;

final class ClassAnalyzer extends EmptyVisitor {

	private List<MethodId>	coroutineMethods	= new LinkedList<MethodId>();

	public List<MethodId> getCoroutineMethods() {
		return coroutineMethods;
	}

	@Override
	public MethodVisitor visitMethod(
			int access,
			String name,
			String desc,
			String signature,
			String[] exceptions) {
		return new CoroutineMethodDiscoverer(new MethodId(name, desc), access);
	}

	private class CoroutineMethodDiscoverer extends EmptyVisitor {

		private final int		accessFlags;

		private final MethodId	methodId;

		public CoroutineMethodDiscoverer(
				MethodId methodId,
				int accessFlags) {
			this.methodId = methodId;
			this.accessFlags = accessFlags;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			// check if method (not a constructor) is annotated with @Coroutine,
			// is public and not
			// abstract, if not we ignore it
			// completely
			if (desc.equals(COROUTINE_DESCRIPTOR)) {
				boolean isAbstract = (accessFlags & Opcodes.ACC_ABSTRACT) != 0;
				boolean isStatic = (accessFlags & Opcodes.ACC_STATIC) != 0;
				boolean isConstructor = methodId.name
						.equals((isStatic) ? "<clinit>" : "<init>");
				boolean returnsCoIterator = Type.getReturnType(
						methodId.descriptor).getDescriptor().equals(
						CO_ITERATOR_DESCRIPTOR);
				boolean isCorrect = !isAbstract & returnsCoIterator
									& !isConstructor;
				if (isAbstract) {
					logger.warning("Abstract method " + methodId
									+ " annotated as coroutine");
				}
				if (!returnsCoIterator) {
					logger.warning("Method " + methodId
									+ " does not return CoIterator");
				}
				if (isConstructor) {
					logger.warning("Cannot make a coroutine from constructor");
				}
				if (isCorrect) {
					coroutineMethods.add(methodId);
				}
			}
			return null;
		}
	}

	private static final Logger	logger	= Logger
												.getLogger("mr.go.coroutines.ClassAnalyzer");
}
