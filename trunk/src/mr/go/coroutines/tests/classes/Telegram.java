package mr.go.coroutines.tests.classes;

public class Telegram {

	private final boolean	hasOverlengthWord;

	private final String	message;

	private final int		wordCount;

	public Telegram(
			String message,
			int wordCount,
			boolean hasOverlengthWord) {
		this.message = message;
		this.wordCount = wordCount;
		this.hasOverlengthWord = hasOverlengthWord;
	}

	public String getMessage() {
		return message;
	}

	public int getWordCount() {
		return wordCount;
	}

	public boolean isHasOverlengthWord() {
		return hasOverlengthWord;
	}

}
