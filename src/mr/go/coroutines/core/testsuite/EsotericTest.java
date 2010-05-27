package mr.go.coroutines.core.testsuite;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.util.ArrayList;
import java.util.Collections;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

/**
 * 
 * @author rzeznik
 */
public class EsotericTest {

	private static boolean stackStresstestHelpermethod(
			Object[] a,
			Long[] b,
			long l) {
		boolean bool = false;
		for (Object _a : a) {
			for (Long _l : b) {
				if (_a.equals(_l) && _l.longValue() == l) {
					bool = true;
				} else {
					bool = false;
				}
			}
		}
		return bool;
	}

	public CoIterator<Double, Void> runStackMapStressTest() {
		return stackMapFrameStressTest(5.05);
	}

	public Iterable<Long> runStackStressTest() {
		return stackStressTest().withPattern(1l, 0l);
	}

	@Coroutine()
	public CoIterator<Double, Void> stackMapFrameStressTest(double d) {
		int k = 0;
		do {
			ArrayList<Integer> list = new ArrayList<Integer>();
			list.addAll(Collections.nCopies(50, 10));
			ArrayList<Long> list1 = new ArrayList<Long>();
			for (Integer i : list) {
				list1.add(new Long(i));
			}
			ArrayList<Double> list2 = new ArrayList<Double>();
			for (Integer i : list) {
				Double aDouble = new Double(i);
				yield(aDouble);
				list2.add(aDouble);
			}
		} while (++k < 2);
		yield(d);
		return _();
	}

	@Coroutine(generator = false)
	public CoIterator<Long, Long> stackStressTest() {
		while (true) {
			if (!stackStresstestHelpermethod(new Object[]
			{ 1l, 1l, 1l, 1l }, new Long[]
			{ 1l, 1l, 1l, yield() }, (Long) yield())) {
				break;
			}
		}
		return _();
	}
}
