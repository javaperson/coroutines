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

import static java.lang.Math.abs;
import static mr.go.coroutines.core.CodeGenerationUtils.box_int;
import static mr.go.coroutines.core.CodeGenerationUtils.makeInt;
import static mr.go.coroutines.core.CodeGenerationUtils.saveloc;
import static mr.go.coroutines.core.CodeGenerationUtils.savelocs;
import static mr.go.coroutines.core.StringConstants.CALL_METHOD_DESCRIPTOR;
import static mr.go.coroutines.core.StringConstants.COROUTINE_METHOD_DESCRIPTOR;
import static mr.go.coroutines.core.StringConstants.CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR;
import static mr.go.coroutines.core.StringConstants.FRAME_NAME;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.*;

final class ClassTransformer extends ClassAdapter {

	private static void createDebugFrame(
			int maxVariables,
			String[] variableNames,
			MethodVisitor mv) {
		mv.visitTypeInsn(Opcodes.NEW, FRAME_NAME);
		mv.visitInsn(Opcodes.DUP);
		makeInt(maxVariables, mv);
		makeInt(variableNames.length, mv);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
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
				"(I[Ljava/lang/String;)V");
	}

	private static void createFrame(int maxVariables, MethodVisitor mv) {
		mv.visitTypeInsn(Opcodes.NEW, FRAME_NAME);
		mv.visitInsn(Opcodes.DUP);
		makeInt(maxVariables, mv);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, FRAME_NAME, "<init>", "(I)V");
	}

	private static String getCoroutineName(MethodId methodId) {
		// little name mangling, let's feel like we were writing C++ compiler
		// :-)
		StringBuilder sb = new StringBuilder(methodId.name);
		int descHash = abs(Type.getArgumentsAndReturnSizes(methodId.descriptor));
		sb.append(descHash);
		Type[] argsTypes = Type.getArgumentTypes(methodId.descriptor);
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

	private static void log(
			String ownerName,
			String loggerField,
			Level level,
			MethodVisitor mv,
			Object... messages) {
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				ownerName,
				loggerField,
				"Ljava/util/logging/Logger;");
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/util/logging/Level", level
				.getName(), "Ljava/util/logging/Level;");
		// stack: * *
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/util/logging/Logger",
				"isLoggable",
				"(Ljava/util/logging/Level;)Z");
		// stack: *
		Label exitBranch = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, exitBranch);
		// stack:
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				ownerName,
				loggerField,
				"Ljava/util/logging/Logger;");
		mv.visitFieldInsn(Opcodes.GETSTATIC, "java/util/logging/Level", level
				.getName(), "Ljava/util/logging/Level;");
		// stack: * *
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		mv.visitInsn(Opcodes.DUP);
		// stack: * * * *
		String message0 = messages[0].toString();
		mv.visitLdcInsn(message0);
		// stack: * * * * *
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				"java/lang/StringBuilder",
				"<init>",
				"(Ljava/lang/String;)V");
		// stack: * * *
		for (int m = 1; m < messages.length; m++) {
			Object message = messages[m];
			if (message instanceof Number) {
				mv.visitVarInsn(Opcodes.ALOAD, ((Number) message).intValue());
			} else {
				mv.visitLdcInsn(message.toString());
			}
			// stack: * * * *
			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					"java/lang/StringBuilder",
					"append",
					"(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
			// stack: * * *
		}
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/StringBuilder",
				"toString",
				"()Ljava/lang/String;");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/util/logging/Logger",
				"log",
				"(Ljava/util/logging/Level;Ljava/lang/String;)V");
		// stack:
		mv.visitLabel(exitBranch);
	}

	private final List<MethodId>					coroutines;

	private final boolean							generateDebugCode;

	private final Map<MethodId, MethodProperties>	methodProperties	= new HashMap<MethodId, MethodProperties>();

	private final boolean							postVerifyCode;

	private final boolean							preVerifyCode;

	private final boolean							printCode;

	private Type									thisType;

	ClassTransformer(
			ClassVisitor writer,
			List<MethodId> coroutines,
			boolean generateDebugCode,
			boolean printCode,
			boolean preVerifyCode,
			boolean postVerifyCode) {
		// by default every bit goes to writer
		super(writer);
		this.coroutines = coroutines;
		this.generateDebugCode = generateDebugCode;
		this.printCode = printCode;
		this.preVerifyCode = preVerifyCode;
		this.postVerifyCode = postVerifyCode;
	}

	@Override
	public void visit(
			int version,
			int access,
			String name,
			String signature,
			String superName,
			String[] interfaces) {
		thisType = Type.getObjectType(name);
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public void visitEnd() {
		/*
		 * generate co iterators and method stubs
		 */
		log.finest("Generating CoIterator implementation and method stubs");
		// num is used for generating unamibigous names
		for (MethodId methodId : methodProperties.keySet()) {
			MethodProperties properties = methodProperties.get(methodId);
			boolean isStatic = properties.isStatic();
			String coroutineName = properties.getCoroutineName();
			/*
			 * Generate CoIterator class
			 */
			String coIteratorName = "mr/go/coroutines/core/CoIterator" + num;
			String baseCoIteratorName;
			if (properties.isThreadLocal()) {
				baseCoIteratorName = Type
						.getInternalName(ThreadLocalCoIterator.class);
			} else {
				baseCoIteratorName = Type
						.getInternalName(SingleThreadedCoIterator.class);
			}
			ClassVisitor cv;
			ClassWriter cw = new ClassWriter(0);
			if (printCode) {
				try {
					cv = CoroutineInstrumentator.createTracer(
							coIteratorName,
							cw);
				} catch (FileNotFoundException e) {
					throw new CoroutineGenerationException(
							"Unable to create trace file",
							e);
				}
			} else {
				cv = cw;
			}
			if (preVerifyCode) {
				cv = CoroutineInstrumentator.createVerifier(cv);
			}
			cv.visit(
					Opcodes.V1_6,
					Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
					coIteratorName,
					null,
					baseCoIteratorName,
					null);
			MethodVisitor mv;
			/*
			 * If debugging code is emitted create field keeping JDK logger
			 */
			if (generateDebugCode) {
				FieldVisitor fv = cv.visitField(
						Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL
								| Opcodes.ACC_STATIC,
						"logger",
						"Ljava/util/logging/Logger;",
						null,
						null);
				fv.visitEnd();
				// logger init
				mv = cv.visitMethod(
						Opcodes.ACC_STATIC,
						"<clinit>",
						"()V",
						null,
						null);
				mv.visitCode();
				String loggerName = thisType.getClassName();
				mv.visitLdcInsn(loggerName);
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						"java/util/logging/Logger",
						"getLogger",
						"(Ljava/lang/String;)Ljava/util/logging/Logger;");
				mv.visitFieldInsn(
						Opcodes.PUTSTATIC,
						coIteratorName,
						"logger",
						"Ljava/util/logging/Logger;");
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(1, 0);
				mv.visitEnd();
			}
			/*
			 * Generate constructor
			 */
			{
				mv = cv.visitMethod(
						Opcodes.ACC_PUBLIC,
						"<init>",
						CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR,
						null,
						null);
				mv.visitCode();
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitMethodInsn(
						Opcodes.INVOKESPECIAL,
						baseCoIteratorName,
						"<init>",
						CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(2, 2);
				mv.visitEnd();
			}
			/*
			 * Generate overriden call to coroutine
			 */
			{
				mv = cv.visitMethod(
						Opcodes.ACC_PROTECTED,
						"call",
						CALL_METHOD_DESCRIPTOR,
						null,
						null);
				mv.visitCode();
				/*
				 * if debug needed generate call details
				 */
				if (generateDebugCode) {
					String coroutineId = "Coroutine " + methodId;
					log(
							coIteratorName,
							"logger",
							Level.FINER,
							mv,
							coroutineId + " call. Caller sent: ",
							2);
					mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
					log(
							coIteratorName,
							"logger",
							Level.FINEST,
							mv,
							coroutineId + " state ",
							1);
					mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				}
				/*
				 * push call arguments: this (if not static), frame, input,
				 * output
				 */
				if (!isStatic) {
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL,
							FRAME_NAME,
							"getThis",
							"()Ljava/lang/Object;");
					mv.visitTypeInsn(Opcodes.CHECKCAST, thisType
							.getInternalName());
				}
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(
						(isStatic)	? Opcodes.INVOKESTATIC
									: Opcodes.INVOKEVIRTUAL,
						thisType.getInternalName(),
						coroutineName,
						COROUTINE_METHOD_DESCRIPTOR);
				// stack: *
				if (!generateDebugCode) {
					mv.visitInsn(Opcodes.ARETURN);
				} else {
					// save result display suspension point (two more locals
					// needed)
					mv.visitVarInsn(Opcodes.ASTORE, 3);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL,
							FRAME_NAME,
							"getLineOfCode",
							"()I");
					box_int(Type.INT, mv);
					mv.visitVarInsn(Opcodes.ASTORE, 4);
					log(
							coIteratorName,
							"logger",
							Level.FINER,
							mv,
							"Coroutine suspended at line ",
							4,
							". Yielded:",
							3);
					mv.visitFrame(Opcodes.F_APPEND, 2, new Object[]
					{ "java/lang/Object", "java/lang/Integer" }, 0, null);
					mv.visitVarInsn(Opcodes.ALOAD, 3);
					mv.visitInsn(Opcodes.ARETURN);
				}
				// if debugging code is emitted it needs space for two
				// additional locals and 5 stack operand
				if (generateDebugCode) {
					mv.visitMaxs(5, 5);
				} else {
					if (isStatic) {
						mv.visitMaxs(3, 3);
					} else {
						mv.visitMaxs(4, 3);
					}
				}
				mv.visitEnd();
			}
			cv.visitEnd();
			/*
			 * CoIterator created - define it in the runtime and verify if
			 * needed
			 */
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Generated class " + coIteratorName);
			}
			byte[] classBytes = cw.toByteArray();
			if (postVerifyCode) {
				CoroutineInstrumentator.verifyClass(
						coIteratorName,
						new ClassReader(classBytes),
						printCode);
			}
			try {
				CoroutineInstrumentator.dumpClass(coIteratorName, classBytes);
			} catch (IOException e) {
				throw new CoroutineGenerationException("Unable to write class "
														+ coIteratorName, e);
			}
			int access = properties.getAccess();
			String signature = properties.getSignature();
			String[] exceptions = properties.getExceptions();
			/*
			 * start generating method - new method is named as the method in
			 * user code, it: returns instance of appropriate CoIterator (see
			 * above), saves arguments of call
			 */
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Instrumenting method " + methodId);
			}
			{
				MethodVisitor methodWriter = super.visitMethod(
						access,
						methodId.name,
						methodId.descriptor,
						signature,
						exceptions);
				methodWriter.visitCode();
				/*
				 * create new Frame
				 */
				String[] variableNames = properties.getVariableNames();
				boolean isDebugFramePossible = generateDebugCode
												&& variableNames != null;
				if (isDebugFramePossible) {
					createDebugFrame(
							properties.getMaxVariables(),
							variableNames,
							methodWriter);
				} else {
					createFrame(properties.getMaxVariables(), methodWriter);
				}
				/*
				 * save frame in the first, and locals array in the second local
				 * variable
				 */
				int argsSize = properties.getArgsSize();
				methodWriter.visitVarInsn(Opcodes.ASTORE, argsSize);
				methodWriter.visitVarInsn(Opcodes.ALOAD, argsSize);
				methodWriter.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						FRAME_NAME,
						"getLocals",
						"()[Ljava/lang/Object;");
				int localsArrayIndex = argsSize + 1;
				methodWriter.visitVarInsn(Opcodes.ASTORE, localsArrayIndex);
				/*
				 * save all call arguments (along with this if this method is
				 * not static) into locals array
				 */
				int argsLength = properties.getArgsLength();
				Type[] argsTypes = properties.getArgsTypes();
				if (!isStatic) {
					saveloc(
							localsArrayIndex,
							0,
							0,
							Types.JAVA_LANG_OBJECT,
							methodWriter);
					savelocs(
							localsArrayIndex,
							1,
							1,
							argsLength,
							methodWriter,
							argsTypes);
				} else {
					savelocs(
							localsArrayIndex,
							0,
							0,
							argsLength,
							methodWriter,
							argsTypes);
				}
				/*
				 * create CoIterator instance with saved frame, make initial
				 * call to next if needed and return to caller
				 */
				methodWriter.visitTypeInsn(Opcodes.NEW, coIteratorName);
				methodWriter.visitInsn(Opcodes.DUP);
				methodWriter.visitVarInsn(Opcodes.ALOAD, argsSize);
				methodWriter.visitMethodInsn(
						Opcodes.INVOKESPECIAL,
						coIteratorName,
						"<init>",
						CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR);
				if (properties.isRequestNext()) {
					methodWriter.visitInsn(Opcodes.DUP);
					methodWriter.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL,
							coIteratorName,
							"next",
							"()Ljava/lang/Object;");
					methodWriter.visitInsn(Opcodes.POP);
				}
				methodWriter.visitInsn(Opcodes.ARETURN);
				/*
				 * end method generation; maxs can be statically determined 3
				 * operands on stack (call to frame setLocals and CoIterator
				 * constructor) + 1 if any argument is long or double (debug
				 * frame needs 7 operands for variable names creation); locals =
				 * argsSize + 1 reference to frame + 1 array of locals
				 */
				if (isDebugFramePossible) {
					methodWriter.visitMaxs(7, localsArrayIndex + 1);
				} else {
					methodWriter.visitMaxs(
							properties.isCategory2ArgumentPresent() ? 4 : 3,
							localsArrayIndex + 1);
				}
				methodWriter.visitEnd();
			}
			num++;
		}
		super.visitEnd();
	}

	@Override
	public MethodVisitor visitMethod(
			int access,
			String name,
			String desc,
			String signature,
			String[] exceptions) {
		for (MethodId coroutine : coroutines) {
			if (coroutine.name.equals(name)
				&& coroutine.descriptor.equals(desc)) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Generating method stub for coroutine "
								+ coroutine);
				}
				String coroutineName = getCoroutineName(coroutine);
				boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
				Type[] argsTypes = Type.getArgumentTypes(desc);
				int argsSize = Type.getArgumentsAndReturnSizes(desc) >> 2;
				MethodProperties properties = new MethodProperties();
				properties.setAccess(access);
				properties.setOwner(thisType);
				properties.setMethod(name);
				properties.setCoroutineName(coroutineName);
				properties.setSignature(signature);
				properties.setExceptions(exceptions);
				properties.setStatic(isStatic);
				properties.setArgsLength(argsTypes.length);
				properties.setArgsTypes(argsTypes);
				properties.setArgsSize((isStatic) ? argsSize - 1 : argsSize);
				this.methodProperties.put(coroutine, properties);
				return new MethodTransformer(super.visitMethod(
						Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL
								| (access & Opcodes.ACC_STATIC),
						coroutineName,
						COROUTINE_METHOD_DESCRIPTOR,
						null,
						exceptions), properties, generateDebugCode);
			}
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	private static final Logger	log	= Logger
											.getLogger("mr.go.coroutines.ClassTransformer");

	private static int			num;
}
