package pl.clareo.coroutines.examples;

import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;
import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;

public class Decoder {

    @Coroutine(generator = false)
    public static CoIterator<byte[], Byte> decode() {
        Byte b = yield();
        while (true) {
            byte[] frame;
            if (b == HEADER) {
                Byte length = yield();
                frame = new byte[length];
                int i = 0;
                while (true) {
                    b = yield();
                    if (b == FOOTER) {
                        break;
                    }
                    if (b == EOT) {
                        return _();
                    }
                    frame[i++] = b;
                }
                b = yield(frame);
            } else if (b == EOT) {
                return _();
            } else {
                throw new IllegalStateException("Invalid byte " + b);
            }
        }
    }

    public static void main(String[] args) {
        String str = "Hello world!";
        final byte[] bytes = str.getBytes();
        CoIterator<byte[], Byte> decoder = Decoder.decode();
        decoder.send(Decoder.HEADER);
        decoder.send((byte) bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            decoder.send(bytes[i]);
        }
        decoder.send(Decoder.FOOTER);
    }

    public static final byte EOT    = 0x01;
    public static final byte FOOTER = 0x02;
    public static final byte HEADER = 0x03;
}
