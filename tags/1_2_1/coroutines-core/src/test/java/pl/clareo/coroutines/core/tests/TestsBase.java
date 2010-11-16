package pl.clareo.coroutines.core.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import pl.clareo.coroutines.user.CoIterator;

public abstract class TestsBase {

    protected final <U> void runCoroutine(CoIterator<U, ?> coroutine, CoIterator<?, U> test) {
        runIterable(coroutine.each(), test);
    }

    protected final <U> void runCoroutine(CoIterator<U, ?> coroutine, Collection<U> expecteds, Class<U> componentType) {
        U[] us = (U[]) Array.newInstance(componentType, expecteds.size());
        runCoroutine(coroutine, expecteds.toArray(us));
    }

    protected final <U> void runCoroutine(CoIterator<U, ?> coroutine, int iterations, CoIterator<?, U> test) {
        runIterable(coroutine.till(iterations), test);
    }

    protected final <U> void runCoroutine(CoIterator<U, ?> coroutine, U[] expecteds) {
        runIterable(coroutine.each(), expecteds);
    }

    protected final <U, V> void runCoroutine(CoIterator<U, V> coroutine, V[] input, U[] expecteds) {
        runIterable(coroutine.withPattern(input), expecteds);
    }

    private <U> void runIterable(Iterable<U> results, CoIterator<?, U> test) {
        Iterator<U> i = results.iterator();
        try {
            while (i.hasNext()) {
                test.send(i.next());
            }
        } catch (NoSuchElementException nse) {
            assertFalse(i.hasNext());
        } finally {
            test.close();
        }
    }

    private <U> void runIterable(Iterable<U> results, U[] expecteds) {
        Iterator<U> i = results.iterator();
        for (U expected : expecteds) {
            assertEquals(expected, i.next());
        }
    }
}
