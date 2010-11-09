package pl.clareo.coroutines.core.tests;

import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;
import pl.clareo.coroutines.user.CoroutineClosedException;

public class ExceptionHandlerTests extends TestsBase {

    @Coroutine
    private static CoIterator<String, Void> finallyTest() {
        List<String> stringList = new ArrayList<String>(Arrays.asList(strings));
        try {
            for (String str : stringList) {
                yield(str);
            }
        } finally {
            yield(null);
        }
        return _();
    }

    @Coroutine(generator = false)
    private static CoIterator<String, Integer> multipleCatchTest() {
        String str = null;
        Integer index = yield();
        while (true) {
            try {
                str = strings[index];
                index = yield(str);
            } catch (CoroutineClosedException e) {
            } catch (IndexOutOfBoundsException e) {
                index -= 1;
            }
        }
    }

    @Coroutine(generator = false)
    private static CoIterator<String, String> nestedTryCatchWithYieldTest() {
        List<String> stringList = new ArrayList<String>(Arrays.asList(strings));
        String str = null;
        while (true) {
            try {
                str = yield().toString();
                int index = stringList.indexOf(str);
                stringList.set(index, null);
            } catch (NullPointerException npe) {
                return _();
            } catch (IndexOutOfBoundsException e) {
                if (str != null) {
                    yield(str);
                }
                return _();
            }
        }
    }

    @Coroutine
    private static CoIterator<String, Void> singleTryCatchTest() {
        int i = 0;
        try {
            while (true) {
                yield(strings[i++]);
            }
        } catch (IndexOutOfBoundsException e) {
        }
        return _();
    }

    @Test
    public void runFinallyTest() {
        List<String> results = new ArrayList<String>(Arrays.asList(strings));
        results.add(null);
        runCoroutine(finallyTest(), results, String.class);
    }

    @Test
    public void runMultipleCatchTest() {
        runCoroutine(multipleCatchTest(), new Integer[] { 0, 2, 4, 6 }, new String[] { strings[0], strings[2],
                strings[4] });
    }

    @Test
    public void runNestedTryCatchWithYieldTest() {
        runCoroutine(nestedTryCatchWithYieldTest(), new String[] { "a", "b", "c", "b" }, new String[] { null, null,
                null });
    }

    @Test
    public void runSingleTryCatchTest() {
        runCoroutine(singleTryCatchTest(), strings);
    }

    private static String[] strings = new String[] { "a", "b", "c", "d", "e" };
}
