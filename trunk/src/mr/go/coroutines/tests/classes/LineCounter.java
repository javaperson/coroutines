package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines.yield;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public final class LineCounter {

	private int		count;

	private boolean	isClosed;

	public int getCount() {
		return count;
	}

	@Coroutine(generator = false)
	public CoIterator<Void, String> greppedLines() {
		int count = 0;
		try {
			while (true) {
				yield();
				count++;
			}
		} finally {
			this.count = count;
			isClosed = true;
		}
	}

	public boolean isClosed() {
		return isClosed;
	}
}
