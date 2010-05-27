package mr.go.coroutines.core.testsuite;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Controler;
import mr.go.coroutines.user.Coroutine;
import mr.go.coroutines.user.CoroutineClosedException;

/**
 * 
 * @author rzeznik
 */
public class ExceptionHandlersTest {

	@Coroutine
	private static CoIterator<String, Void> finallyTest() {
		List<String> stringList = new ArrayList<String>(Arrays.asList(strings));
		try {
			for (String str : stringList) {
				yield(str);
			}
		} finally {
			yield(null);
		}
		return _();
	}

	@Coroutine
	private static CoIterator<String, String> nestedTryCatchTest() {
		List<String> stringList = new ArrayList<String>(Arrays.asList(strings));
		for (String str : stringList) {
			yield(str);
		}
		try {
			final String str = yield().toString();
			int index = stringList.indexOf(str);
			try {
				strings[index] = null;
			} catch (IndexOutOfBoundsException e) {
				return _();
			}
		} catch (NullPointerException npe) {
		}
		return _();
	}

	@Coroutine
	private static CoIterator<String, String> nestedTryCatchWithYieldTest() {
		List<String> stringList = new ArrayList<String>(Arrays.asList(strings));
		for (String str : stringList) {
			yield(str);
		}
		String str = null;
		try {
			str = yield().toString();
			int index = stringList.indexOf(str);
			strings[index] = null;
		} catch (NullPointerException npe) {
			return _();
		} catch (IndexOutOfBoundsException e) {
			if (str != null) {
				yield(str);
			}
			return _();
		}
		return _();
	}

	@Coroutine
	private static CoIterator<String, Void> singleTryCatchTest() {
		int i = 0;
		try {
			while (true) {
				yield(strings[i++]);
			}
		} catch (IndexOutOfBoundsException e) {
		}
		return _();
	}

	@Coroutine
	private static CoIterator<String, Integer> tryCatchFinallyTest() {
		String str = null;
		Integer index = yield();
		while (true) {
			try {
				str = strings[index];
				index = yield(str);
			} catch (CoroutineClosedException e) {
			} catch (IndexOutOfBoundsException e) {
				index -= 1;
			} finally {
				strings = Arrays.copyOf(strings, strings.length + 1);
				strings[strings.length - 1] = "a";
			}
		}
	}

	public CoIterator<String, Void> runFinallyTest() {
		return finallyTest();
	}

	public Iterable<String> runNestedTryCatchTest() {
		return nestedTryCatchTest().with(new Controler<String, String>() {

			@Override
			public void closed() {
			}

			@Override
			public String init() {
				return null;
			}

			@Override
			public void noNextElement() {
			}

			@Override
			public String respondTo(String str) {
				if (str == null) {
					return "g";
				}
				return null;
			}
		});
	}

	public Iterable<String> runNestedTryCatchWithYieldTest() {
		return nestedTryCatchWithYieldTest().with(
				new Controler<String, String>() {

					@Override
					public void closed() {
					}

					@Override
					public String init() {
						return null;
					}

					@Override
					public void noNextElement() {
					}

					@Override
					public String respondTo(String str) {
						if (str == null) {
							return "g";
						}
						return null;
					}
				});
	}

	public CoIterator<String, Void> runSingleTryCatchTest() {
		return singleTryCatchTest();
	}

	public Iterable<String> runTryCatchFinallyTest() {
		return tryCatchFinallyTest().withPattern(0, 1, 2);
	}

	private static String[]	strings	= new String[]
									{ "a", "b", "c", "d", "e" };
}
