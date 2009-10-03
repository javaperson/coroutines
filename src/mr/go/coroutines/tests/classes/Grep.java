package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;
import mr.go.coroutines.user.CoroutineExitException;

public final class Grep {

	@Coroutine
	public static CoIterator<String, Void> grep(String pattern, String file)
			throws IOException {
		FileReader freader = new FileReader(file);
		BufferedReader reader = new BufferedReader(freader);
		String line;
		Pattern regex = Pattern.compile(pattern);
		try {
			while ((line = reader.readLine()) != null) {
				boolean isFound = regex.matcher(line).find();
				if (isFound) {
					yield(line);
				}
			}
		} catch (CoroutineExitException e) {
			reader.close();
			throw e;
		}
		yield(null);
		reader.close();
		return _();
	}

	private final String	pattern;

	public Grep(
			String pattern) {
		this.pattern = pattern;
	}

	public String getPattern() {
		return pattern;
	}

	@Coroutine
	public CoIterator<Void, String> grep(CoIterator<?, String> next) {
		Pattern regex = Pattern.compile(pattern);
		try {
			while (true) {
				String line = yield();
				if (regex.matcher(line).find()) {
					next.send(line);
				}
			}
		} catch (CoroutineExitException e) {
			next.close();
			throw e;
		}

	}
}
