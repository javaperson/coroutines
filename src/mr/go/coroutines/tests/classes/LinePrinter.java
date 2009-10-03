package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines.yield;

import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;
import mr.go.coroutines.user.CoroutineExitException;

public class LinePrinter {

	private boolean	isClosed;

	@Coroutine(generator = false)
	public CoIterator<Void, String> greppedLines(List<String> strings) {
		try {
			while (true) {
				strings.add((String) yield());
			}
		} catch (CoroutineExitException e) {
			isClosed = true;
			throw e;
		}
	}

	public boolean isClosed() {
		return isClosed;
	}
}
