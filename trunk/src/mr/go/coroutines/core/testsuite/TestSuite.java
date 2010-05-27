package mr.go.coroutines.core.testsuite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import mr.go.coroutines.user.CoIterator;

/**
 * 
 * @author rzeznik
 */
public final class TestSuite {

	public static void main(String[] args) {
		String[] coroutineClasses = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			coroutineClasses[i] = TEST_PACKAGE + args[i];
		}
		int tests = 0;
		int failures = 0;
		int skipped = 0;
		for (int i = 0; i < coroutineClasses.length; i++) {
			final String classname = coroutineClasses[i];
			logger.info("Loading class " + classname);
			try {
				Class<?> coroutineClass = Class.forName(classname);
				logger.info("[OK] Class loaded and verified");
				Method[] methods = coroutineClass.getMethods();
				for (int j = 0; j < methods.length; j++) {
					final Method method = methods[j];
					if (method.getName().startsWith("run")) {
						tests += 1;
						final String testName = method.getName().substring(3);
						logger.info("---------------------------");
						logger.info("Running '" + testName + "'");
						logger.info("---------------------------");
						boolean returnsIterable;
						final Class<?> returnType = method.getReturnType();
						if (returnType.equals(Iterable.class)) {
							returnsIterable = true;
						} else {
							returnsIterable = false;
							if (!returnType.equals(CoIterator.class)) {
								logger
										.warning("[FAIL] Skipped - not returning CoIterator or Iterable");
								skipped += 1;
								continue;
							}
						}
						if (method.getParameterTypes().length != 0) {
							logger
									.warning("[FAIL] Skipped - taking parameters");
							skipped += 1;
							continue;
						}
						try {
							Iterable<?> results;
							if (!returnsIterable) {
								CoIterator<?, ?> coIterator = (CoIterator<?, ?>) method
										.invoke(coroutineClass.newInstance());
								results = coIterator.each();
							} else {
								results = (Iterable<?>) method
										.invoke(coroutineClass.newInstance());
							}
							for (Object result : results) {
								logger.info("Next ..." + result);
							}
							logger.info("[OK] Hurray !!!");
						} catch (IllegalArgumentException ex) {
							logger
									.log(
											Level.SEVERE,
											"[FAIL] Cannot invoke",
											ex);
							failures += 1;
						} catch (InvocationTargetException ex) {
							logger.log(
									Level.SEVERE,
									"[FAIL] exception in test method",
									ex);
							failures += 1;
						} catch (InstantiationException ex) {
							logger
									.log(
											Level.SEVERE,
											"[FAIL] Cannot invoke",
											ex);
							failures += 1;
						} catch (IllegalAccessException ex) {
							logger
									.log(
											Level.SEVERE,
											"[FAIL] Cannot invoke",
											ex);
							failures += 1;
						} catch (Throwable t) {
							logger.log(
									Level.SEVERE,
									"[FAIL] execption in coroutine",
									t);
							failures += 1;
						}
					}
				}
			} catch (ClassNotFoundException ex) {
				logger.log(Level.SEVERE, "[FAIL] Class not found", ex);
			} catch (VerifyError ex) {
				logger.log(Level.SEVERE, "[FAIL] " + ex.getMessage());
			}
			logger.info("Tests: " + tests + ", skipped: " + skipped
						+ ", failures: " + failures);
		}
	}

	private static final Logger	logger			= Logger
														.getLogger("mr.go.coroutines.core.testsuite");

	private static final String	TEST_PACKAGE	= "mr.go.coroutines.core.testsuite.";
}
