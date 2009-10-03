package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public class Decoder {

	@Coroutine(generator = false)
	public static CoIterator<Void, Byte> decode(
			CoIterator<?, byte[]>... receivers) {
		while (true) {
			Byte b = yield();
			byte[] frame;
			if (b == HEADER) {
				Byte length = yield();
				frame = new byte[length];
				int i = 0;
				while (true) {
					b = yield();
					if (b == FOOTER) {
						for (CoIterator<?, byte[]> receiver : receivers) {
							receiver.send(frame);
						}
						break;
					} else {
						frame[i++] = b;
					}
				}
			} else if (b == EOT) {
				for (CoIterator<?, byte[]> receiver : receivers) {
					receiver.close();
				}
				break;
			}
		}
		return _();
	}

	public static final byte	EOT		= 0x01;

	public static final byte	FOOTER	= 0x02;

	public static final byte	HEADER	= 0x03;
}
