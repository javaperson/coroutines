package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public class NumberGenerator {

	@Coroutine
	public static CoIterator<Double, Void> fareySequence(int n) {
		int a = 0;
		int b = 1;
		int c = 1;
		int d = n;
		yield(((double) a) / b);
		while (c < n) {
			int k = (n + b) / d;
			int _a = a;
			int _b = b;
			a = c;
			b = d;
			c = k * c - _a;
			d = k * d - _b;
			yield(((double) a) / b);
		}
		return _();
	}

	@Coroutine
	public static CoIterator<Integer, Void> fibonacciNumbers() {
		int a = 0;
		int b = 1;
		while (true) {
			yield(a);
			int next = a + b;
			a = b;
			b = next;
		}
	}

	@Coroutine
	public static CoIterator<Integer, Void> integers() {
		int i = 0;
		while (true) {
			yield(i++);
		}
	}

	private final int	startingNumber;

	public NumberGenerator(
			int startingNumber) {
		this.startingNumber = startingNumber;
	}

	@Coroutine
	public CoIterator<Integer, Boolean> getNumbers() {
		int i = startingNumber;
		while ((Boolean) yield(i)) {
			i++;
		}
		return _();
	}

	@Coroutine
	public CoIterator<Integer, Void> getNumbers(int howMany) {
		for (int i = 0; i < howMany; i++) {
			yield(startingNumber + i);
		}
		return _();
	}

	public int getStartingNumber() {
		return startingNumber;
	}
}
