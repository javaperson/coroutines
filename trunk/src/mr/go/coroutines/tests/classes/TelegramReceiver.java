package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public class TelegramReceiver {

	private final int	maxLength;

	public TelegramReceiver(
			int maxLength) {
		this.maxLength = maxLength;
	}

	@Coroutine(generator = false)
	public CoIterator<Void, String> receiver(List<Telegram> telegrams) {
		Telegram t;
		int wordCount = 0;
		boolean hasOverlengthWord = false;
		StringBuilder msg = new StringBuilder();
		while (true) {
			String part = yield();
			if (part.equals(DELIMITER)) {
				if (wordCount == 0) {
					return _();
				}
				t = new Telegram(msg.toString(), wordCount, hasOverlengthWord);
				telegrams.add(t);
				msg = new StringBuilder();
				wordCount = 0;
				hasOverlengthWord = false;
			} else if (part.equals("STOP")) {
				msg.append(' ');
			} else {
				if (part.length() > maxLength) {
					hasOverlengthWord = true;
					part = part.substring(0, 11);
				}
				wordCount += 1;
				msg.append(part);
			}
		}
	}

	private static final String	DELIMITER	= "ZZZZZ";

}
