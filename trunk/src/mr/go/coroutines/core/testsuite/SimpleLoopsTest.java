package mr.go.coroutines.core.testsuite;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

/**
 * 
 * @author rzeznik
 */
public class SimpleLoopsTest {

	@Coroutine
	public CoIterator<Integer, Void> doTest(int end) {
		int i = 0;
		do {
			yield(i);
		} while (++i < end);
		return _();
	}

	@Coroutine
	public CoIterator<Integer, Void> nestedForTest(int a, int b, int c) {
		for (int k = 0; k < a; k++) {
			for (int j = 0; j < b; j++) {
				for (int i = 0; i < c; i++) {
					yield(i);
				}
				yield(j);
			}
			yield(k);
		}
		return _();
	}

	public CoIterator<Integer, Void> runDoLoopTest() {
		return doTest(10);
	}

	public CoIterator<Integer, Void> runNestedForTest() {
		return nestedForTest(3, 3, 2);
	}

	public CoIterator<Integer, Void> runWhileTest() {
		return whileTest();
	}

	@Coroutine
	private CoIterator<Integer, Void> whileTest() {
		int i = 0;
		while (i < 10) {
			yield(i);
			i++;
		}
		return _();
	}
}
