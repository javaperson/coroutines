package pl.clareo.coroutines.core.tests;

import static org.junit.Assert.assertEquals;
import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;

import org.junit.Test;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;

public class LoopsTests extends TestsBase {

    @Coroutine
    private CoIterator<Integer, Void> doTest(int end) {
        int i = 0;
        do {
            yield(i);
        } while (++i < end);
        return _();
    }

    @Coroutine(generator = false)
    private CoIterator<Void, Integer> doTestResults(int end) {
        int i = 0;
        do {
            assertEquals(i, yield());
        } while (++i < end);
        return _();
    }

    @Coroutine
    private CoIterator<Integer, Void> nestedForTest(int a, int b, int c) {
        for (int k = 0; k < a; k++) {
            for (int j = 0; j < b; j++) {
                for (int i = 0; i < c; i++) {
                    yield(i);
                }
                yield(j);
            }
            yield(k);
        }
        return _();
    }

    @Coroutine(generator = false)
    private CoIterator<Void, Integer> nestedForTestResults(int a, int b, int c) {
        for (int k = 0; k < a; k++) {
            for (int j = 0; j < b; j++) {
                for (int i = 0; i < c; i++) {
                    assertEquals(i, yield());
                }
                assertEquals(j, yield());
            }
            assertEquals(k, yield());
        }
        return _();
    }

    @Test
    public void runDoLoopTest() {
        runCoroutine(doTest(5), doTestResults(5));
    }

    @Test
    public void runNestedForTest() {
        runCoroutine(nestedForTest(3, 3, 2), nestedForTestResults(3, 3, 2));
    }

    @Test
    public void runWhileTest() {
        runCoroutine(whileTest(), 5, whileTestResults());
    }

    @Coroutine
    private CoIterator<Integer, Void> whileTest() {
        int i = 0;
        while (true) {
            yield(i);
            i++;
        }
    }

    @Coroutine(generator = false)
    private CoIterator<Void, Integer> whileTestResults() {
        int i = 0;
        while (true) {
            assertEquals(i, yield());
            i++;
        }
    }
}
