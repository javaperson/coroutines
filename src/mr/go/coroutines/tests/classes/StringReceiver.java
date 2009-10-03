package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines.yield;

import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public class StringReceiver {

	@Coroutine(generator = false)
	public CoIterator<Void, byte[]> receive(List<String> strings) {
		while (true) {
			byte[] content = yield();
			strings.add(new String(content));
		}
	}

}
