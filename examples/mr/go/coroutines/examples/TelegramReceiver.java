package mr.go.coroutines.examples;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.util.LinkedList;
import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public class TelegramReceiver {

	public static void main(String[] args) {
		TelegramReceiver tg = new TelegramReceiver(12);
		List<Telegram> telegrams = new LinkedList<Telegram>();
		for (Telegram t : tg.receiver(telegrams).withPattern(
				"Hello",
				"STOP",
				"How",
				"STOP",
				"do",
				"STOP",
				"you",
				"STOP",
				"do?",
				DELIMITER,
				"Hello",
				"STOP",
				"Constantinopole",
				"STOP",
				"is",
				"STOP",
				"lost!!!",
				DELIMITER,
				DELIMITER)) {
			System.out.println(t);
		}
		System.out.println(telegrams.toString());
	}

	private final int	maxLength;

	public TelegramReceiver(
			int maxLength) {
		this.maxLength = maxLength;
	}

	@Coroutine(generator = false)
	public CoIterator<Telegram, String> receiver(List<Telegram> telegrams) {
		Telegram t = null;
		int wordCount = 0;
		boolean hasOverlengthWord = false;
		StringBuilder msg = new StringBuilder();
		while (true) {
			String part = yield(t);
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
					part = part.substring(0, maxLength);
				}
				wordCount += 1;
				msg.append(part);
			}
		}
	}

	private static final String	DELIMITER	= "ZZZZZ";
}
