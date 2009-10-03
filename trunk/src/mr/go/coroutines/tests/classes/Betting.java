package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.io.IOException;
import java.io.PrintWriter;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;
import mr.go.coroutines.user.CoroutineExitException;

public final class Betting {

	@Coroutine(generator = false)
	public CoIterator<Boolean, Double> accept() {
		Double threshold = yield(null);
		if (threshold < 0 || threshold > 1) {
			throw new IllegalArgumentException("threshold < 0 || threshold > 1");
		}
		while (true) {
			double rnd = Math.random();
			threshold = yield(rnd < threshold);
			if (threshold < 0 || threshold > 1) {
				throw new IllegalArgumentException(
						"threshold < 0 || threshold > 1");
			}
		}
	}

	@Coroutine(generator = false)
	public CoIterator<Boolean, Double> accept(double delta) {
		Double threshold = yield(null);
		if (threshold < 0 || threshold > 1) {
			throw new IllegalArgumentException("threshold < 0 || threshold > 1");
		}
		while (true) {
			double rnd = Math.random();
			threshold = threshold - delta * (Double) yield(rnd < threshold);
			if (threshold < 0 || threshold > 1) {
				throw new IllegalArgumentException(
						"threshold < 0 || threshold > 1");
			}
		}
	}

	@Coroutine(generator = false)
	public CoIterator<Boolean, Double> accept(String file) {
		Double threshold = yield(null);
		if (threshold < 0 || threshold > 1) {
			throw new IllegalArgumentException("threshold < 0 || threshold > 1");
		}
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(file);
			writer.write(Betting.class.getSimpleName());
			while (true) {
				double rnd = Math.random();
				boolean win = rnd < threshold;
				threshold = yield(win);
				if (threshold < 0 || threshold > 1) {
					throw new IllegalArgumentException(
							"threshold < 0 || threshold > 1");
				}
				if (win) {
					writer.write("Win");
				} else {
					writer.write("Lose");
				}
			}
		} catch (CoroutineExitException e) {
			writer.write("END");
		} catch (IOException e) {
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
		return _();

	}
}
