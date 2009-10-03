package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;
import mr.go.coroutines.user.CoroutineExitException;

public final class LineWriter {

	private final File	file;

	public LineWriter(
			String file) {
		this.file = new File(file);
	}

	@Coroutine(generator = false)
	public CoIterator<Void, String> greppedLines() throws IOException {
		FileWriter fw = null;
		if (file.exists()) {
			file.delete();
		}
		try {
			fw = new FileWriter(file);
			try {
				while (true) {
					fw.write((String) yield());
					fw.write('\n');
				}
			} catch (CoroutineExitException e) {
				fw.write("END");
			}
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e1) {
				}
			}
		}
		return _();
	}
}
