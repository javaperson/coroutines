package mr.go.coroutines.core.testsuite;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

/**
 * 
 * @author rzeznik
 */
public class Category2Test {

	public Iterable<Long> runLongOnStackTest() {
		return longOnStackTest().withPattern(10l, 10l, 10l, 10l);
	}

	public CoIterator<Long, Void> runLongTest() {
		return longTest();
	}

	@Coroutine(generator = false)
	private CoIterator<Long, Long> longOnStackTest() {
		long l1 = 0;
		long l2 = 10;
		while (true) {
			l1 += l2 + (Long) yield(l1);
		}
	}

	@Coroutine()
	private CoIterator<Long, Void> longTest() {
		long l1 = 10;
		for (int i = 0; i < 3; i++) {
			yield(l1);
		}
		long l2 = 10;
		for (int k = 0; k < 3; k++) {
			yield(l2);
		}
		return _();
	}
}
