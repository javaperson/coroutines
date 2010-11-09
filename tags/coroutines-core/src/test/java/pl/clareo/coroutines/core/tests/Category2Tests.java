package pl.clareo.coroutines.core.tests;

import static org.junit.Assert.assertEquals;
import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;

import org.junit.Test;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;

public class Category2Tests extends TestsBase {

    @Coroutine(generator = false)
    private CoIterator<Long, Long> longOnStackTest() {
        long l1 = 0;
        long l2 = 10;
        while (true) {
            l1 += l2 + (Long) yield(l1);
        }
    }

    @Coroutine()
    private CoIterator<Long, Void> longTest() {
        long l1 = 10;
        for (int i = 0; i < 3; i++) {
            yield(l1 + i);
        }
        long l2 = 10;
        for (int k = 0; k < 3; k++) {
            yield(l2 + k);
        }
        return _();
    }

    @Coroutine(generator = false)
    private CoIterator<Void, Long> longTestResults() {
        long l1 = 10;
        for (int i = 0; i < 3; i++) {
            assertEquals(l1 + i, yield());
        }
        long l2 = 10;
        for (int k = 0; k < 3; k++) {
            assertEquals(l2 + k, yield());
        }
        return _();
    }

    @Test
    public void runLongOnStackTest() {
        Long[] input = new Long[] { 0L, 1L, 2L, 3L };
        runCoroutine(longOnStackTest(), input, new Long[] { 10L, 21L, 33L });
    }

    @Test
    public void runLongTest() {
        runCoroutine(longTest(), longTestResults());
    }
}
