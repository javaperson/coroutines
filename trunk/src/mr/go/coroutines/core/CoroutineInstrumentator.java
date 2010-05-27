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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

final class CoroutineInstrumentator implements ClassFileTransformer {

	public static ClassVisitor createTracer(
			String classInternalName,
			ClassVisitor cv) throws FileNotFoundException {
		File outputFile = new File(printPath, classInternalName + ".trace");
		outputFile.getParentFile().mkdirs();
		PrintWriter writer = new PrintWriter(outputFile);
		TraceClassVisitor tracer = new ClosingTraceClassVisitor(cv, writer);
		return tracer;
	}

	public static ClassVisitor createVerifier(ClassVisitor cv) {
		CheckClassAdapter verifier = new CheckClassAdapter(cv, true);
		return verifier;
	}

	public static void dumpClass(String classInternalName, byte[] classContents)
			throws IOException {
		String fileName = classInternalName + ".class";
		if (logger.isLoggable(Level.FINEST)) {
			logger.finest("Writing class file " + fileName);
		}
		File classFile = new File(classgenPath, fileName);
		if (classFile.exists()) {
			classFile.delete();
		} else {
			classFile.getParentFile().mkdirs();
		}
		FileOutputStream writer = new FileOutputStream(classFile);
		BufferedOutputStream buffer = null;
		try {
			buffer = new BufferedOutputStream(writer);
			buffer.write(classContents);
		} finally {
			if (buffer != null) {
				buffer.close();
			}
		}
	}

	public static void verifyClass(
			String className,
			ClassReader classReader,
			boolean dump) throws CoroutineGenerationException {
		try {
			String filename = className + ".ver";
			if (logger.isLoggable(Level.FINEST)) {
				logger
						.finest("Running verification process. If verification fails (or printing is turned on) file "
								+ filename
								+ " will contain detailed information");
			}
			File outputFile = new File(printPath, filename);
			PrintWriter writer = new PrintWriter(outputFile);
			CheckClassAdapter.verify(classReader, dump, writer);
		} catch (FileNotFoundException e) {
			throw new CoroutineGenerationException(
					"Verification could not be dumped to disk",
					e);
		}
	}

	private String[]	coroutineEnabledClassnames;

	private boolean[]	debugMode;

	private boolean		detectCoroutineClasses;

	private boolean		generateBinaryOutput;

	private boolean		generateDebugCode;

	private boolean[]	outputBinMode;

	private boolean		overrideFrames;

	private boolean[]	overrideFramesMode;

	private boolean[]	postVerifyMode;

	private boolean[]	preVerifyMode;

	private boolean		printCode;

	private boolean[]	printMode;

	private boolean		runPostVerification;

	private boolean		runPreVerification;

	CoroutineInstrumentator() {
		this(false, false, false, false, false, false);
	}

	CoroutineInstrumentator(
			boolean generateDebugCode,
			boolean printCode,
			boolean preVerify,
			boolean postVerify,
			boolean outputBin,
			boolean overrideFrames) {
		detectCoroutineClasses = true;
		this.generateDebugCode = generateDebugCode;
		this.printCode = printCode;
		this.runPreVerification = preVerify;
		this.runPostVerification = postVerify;
		this.generateBinaryOutput = outputBin;
		this.overrideFrames = overrideFrames;
	}

	CoroutineInstrumentator(
			String[] coroutineEnabledClassnames) {
		this(
				coroutineEnabledClassnames,
				false,
				false,
				false,
				false,
				false,
				false);
	}

	CoroutineInstrumentator(
			String[] coroutineEnabledClassnames,
			boolean generateDebugCode,
			boolean printCode,
			boolean preVerify,
			boolean postVerify,
			boolean outputBin,
			boolean overrideFrames) {
		int classesLength = coroutineEnabledClassnames.length;
		if (coroutinePackage != null) {
			String[] classnamesWithPackage = new String[classesLength];
			for (int i = 0; i < classesLength; i++) {
				classnamesWithPackage[i] = coroutinePackage + '.'
											+ coroutineEnabledClassnames[i];
			}
			coroutineEnabledClassnames = Arrays.copyOf(
					coroutineEnabledClassnames,
					2 * classesLength);
			System.arraycopy(
					classnamesWithPackage,
					0,
					coroutineEnabledClassnames,
					classesLength,
					classesLength);
			classesLength *= 2;
		}
		Arrays.sort(coroutineEnabledClassnames);
		this.coroutineEnabledClassnames = coroutineEnabledClassnames;
		this.debugMode = new boolean[classesLength];
		this.printMode = new boolean[classesLength];
		this.preVerifyMode = new boolean[classesLength];
		this.postVerifyMode = new boolean[classesLength];
		this.outputBinMode = new boolean[classesLength];
		this.overrideFramesMode = new boolean[classesLength];
		if (generateDebugCode) {
			for (int i = 0; i < classesLength; i++) {
				debugMode[i] = true;
			}
		}
		if (printCode) {
			for (int i = 0; i < classesLength; i++) {
				printMode[i] = true;
			}
		}
		if (preVerify) {
			for (int i = 0; i < classesLength; i++) {
				preVerifyMode[i] = true;
			}
		}
		if (postVerify) {
			for (int i = 0; i < classesLength; i++) {
				postVerifyMode[i] = true;
			}
		}
		if (outputBin) {
			for (int i = 0; i < classesLength; i++) {
				outputBinMode[i] = true;
			}
		}
		if (overrideFrames) {
			for (int i = 0; i < classesLength; i++) {
				overrideFramesMode[i] = true;
			}
		}
		for (int i = 0; i < classesLength; i++) {
			String classname = coroutineEnabledClassnames[i];
			int indexOfOptionSeparator = classname.lastIndexOf('-');
			if (indexOfOptionSeparator != -1) {
				String[] options = classname.substring(
						indexOfOptionSeparator + 1).split(",");
				classname = classname.substring(0, indexOfOptionSeparator);
				for (String option : options) {
					if (option.equals("debug")) {
						debugMode[i] = true;
					} else if (option.equals("overrideframes")) {
						overrideFramesMode[i] = true;
					} else if (option.equals("print")) {
						printMode[i] = true;
					} else if (option.equals("outputbin")) {
						outputBinMode[i] = true;
					} else if (option.equals("preverify")) {
						preVerifyMode[i] = true;
					} else if (option.equals("postverify")) {
						postVerifyMode[i] = true;
					}
				}
			}
			this.coroutineEnabledClassnames[i] = classname.replace('.', '/');
		}
	}

	@Override
	public byte[] transform(
			ClassLoader loader,
			String className,
			Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		if (classBeingRedefined != null) {
			return null;
		}
		List<MethodId> coroutineMethodsInCurrentClass;
		int classnameIndex = -1;
		if (!detectCoroutineClasses) {
			classnameIndex = Arrays.binarySearch(
					coroutineEnabledClassnames,
					className);
			if (classnameIndex < 0) {
				return null;
			}
		}
		ClassReader asmClassReader = new ClassReader(classfileBuffer);
		ClassAnalyzer analyzer = new ClassAnalyzer();
		asmClassReader.accept(analyzer, DETECT_STRUCTURE);
		coroutineMethodsInCurrentClass = analyzer.getCoroutineMethods();
		if (coroutineMethodsInCurrentClass.isEmpty()) {
			return null;
		}
		if (logger.isLoggable(Level.FINEST)) {
			logger.finest(className + ": Instrumenting coroutines "
							+ coroutineMethodsInCurrentClass.toString());
		}
		boolean debug;
		boolean print;
		boolean postVerify;
		boolean preVerify;
		boolean outputBin;
		boolean asmComputeFrames;
		if (detectCoroutineClasses) {
			debug = generateDebugCode;
			print = printCode;
			postVerify = runPostVerification;
			preVerify = runPreVerification;
			outputBin = generateBinaryOutput;
			asmComputeFrames = overrideFrames;
		} else {
			debug = debugMode[classnameIndex];
			print = printMode[classnameIndex];
			postVerify = postVerifyMode[classnameIndex];
			preVerify = preVerifyMode[classnameIndex];
			outputBin = outputBinMode[classnameIndex];
			asmComputeFrames = overrideFramesMode[classnameIndex];
		}
		ClassWriter asmClassWriter = new ClassWriter(
				asmClassReader,
				((asmComputeFrames) ? ClassWriter.COMPUTE_FRAMES : 0)
						| ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv;
		byte[] instrumentedClassContents;
		try {
			if (preVerify) {
				cv = createVerifier(asmClassWriter);
			} else {
				cv = asmClassWriter;
			}
			if (print) {
				try {
					cv = createTracer(className, cv);
				} catch (FileNotFoundException e) {
					throw new CoroutineGenerationException(
							"Unable to write trace file ",
							e);
				}
			}
			asmClassReader.accept(new ClassTransformer(
					cv,
					coroutineMethodsInCurrentClass,
					debug,
					print,
					preVerify,
					postVerify), 0);
			instrumentedClassContents = asmClassWriter.toByteArray();
			if (postVerify) {
				verifyClass(className, new ClassReader(
						instrumentedClassContents), print);
			}
			if (outputBin) {
				dumpClass(className + "Instrumented", instrumentedClassContents);
			}
		} catch (IllegalStateException e) {
			logger.log(Level.WARNING, "Verification failed", e);
			return null;
		} catch (IllegalArgumentException e) {
			logger.log(Level.WARNING, "Verification failed", e);
			return null;
		} catch (CoroutineGenerationException e) {
			logger.warning(e.getMessage());
			return null;
		} catch (Throwable t) {
			logger
					.log(
							Level.SEVERE,
							"Coroutine generation ended abruptly. This may be bug in the package itself. Details below:",
							t);
			return null;
		}
		return instrumentedClassContents;
	}

	private static class ClosingTraceClassVisitor extends TraceClassVisitor {

		public ClosingTraceClassVisitor(
				ClassVisitor cv,
				PrintWriter pw) {
			super(cv, pw);
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
			pw.close();
		}
	}

	private static final String	classgenPath		= System
															.getProperty(
																	"mr.go.coroutines.ClassgenPath",
																	".");

	private static final String	coroutinePackage	= System
															.getProperty("mr.go.coroutines.DefaultPackage");

	private static final int	DETECT_STRUCTURE	= ClassReader.SKIP_CODE
														| ClassReader.SKIP_DEBUG
														| ClassReader.SKIP_FRAMES;

	private static final Logger	logger				= Logger
															.getLogger("mr.go.coroutines.CoroutineInstrumentator");

	private static final String	printPath			= System
															.getProperty(
																	"mr.go.coroutines.PrintPath",
																	".");
}
