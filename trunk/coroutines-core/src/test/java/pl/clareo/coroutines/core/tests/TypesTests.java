package pl.clareo.coroutines.core.tests;

import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;

public class TypesTests extends TestsBase {

    @Coroutine
    private static CoIterator<String, Void> arrayTypesTest(boolean useB, int times) {
        A[] a;
        if (useB) {
            B[] b = new B[times];
            for (int i = 0; i < times; i++) {
                b[i] = new B();
            }
            a = b;
        } else {
            a = new A[times];
            for (int i = 0; i < times; i++) {
                a[i] = new A();
            }
        }
        for (int i = 0; i < times; i++) {
            yield(a[i].doSth());
        }
        return _();
    }

    @Coroutine
    private static CoIterator<String, Void> supertypeTest1(boolean useLinkedList) {
        List<String> aList;
        if (useLinkedList) {
            aList = new LinkedList<String>();
        } else {
            aList = new ArrayList<String>();
        }
        for (int i = 0; i < 5; i++) {
            aList.add(Integer.toString(i));
        }
        for (String s : aList) {
            yield(s);
        }
        return _();
    }

    @Coroutine
    private static CoIterator<String, Void> supertypeTest2(boolean useB, int times) {
        A a;
        if (useB) {
            B b = new B();
            yield(b.doSthElse());
            a = b;
        } else {
            a = new A();
        }
        for (int i = 0; i < times; i++) {
            yield(a.doSth());
        }
        return _();
    }

    @Test
    public void runArrayTypesTest() {
        runCoroutine(arrayTypesTest(false, 1), new String[] { "sth" });
    }

    @Test
    public void runSupertypeTest1() {
        runCoroutine(supertypeTest1(false), new String[] { "0", "1", "2", "3", "4" });
    }

    @Test
    public void runSupertypeTest2() {
        runCoroutine(supertypeTest2(true, 1), new String[] { "sth else", "sth" });
    }

    private static class A {

        public String doSth() {
            return "sth";
        }
    }

    private static class B extends A {

        public String doSthElse() {
            return "sth else";
        }
    }
}
