/*
 * Copyright 2009-2010 Marcin Rzeźnicki

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package pl.clareo.coroutines.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Controler;
import pl.clareo.coroutines.user.CoroutineClosedException;
import pl.clareo.coroutines.user.CoroutineExitException;
import pl.clareo.coroutines.user.ExitCondition;
import pl.clareo.coroutines.user.ExitOnYieldedEqualsTo;
import pl.clareo.coroutines.user.StaticPattern;

abstract class CoIteratorInternal<E, A> implements CoIterator<E, A> {

    protected abstract E call(Frame frame, A a);

    @Override
    public void callWithPattern(A... toSend) {
        Iterator<E> it = withPattern(toSend).iterator();
        while (it.hasNext()) {
            it.next();
        }
    }

    @Override
    public void close() {
        final Frame frame = getFrame();
        if (frame.isCoroutineClosed()) {
            return;
        }
        frame.markCoroutineClosed();
        try {
            call(frame, null);
        } catch (CoroutineExitException e) {
        } catch (NoSuchElementException e) {
        } finally {
            frame.setState(Frame.CLOSED_STATE);
        }
    }

    @Override
    public Iterable<E> each() {
        return new CoIterable();
    }

    protected abstract Frame getFrame();

    @Override
    public E next() {
        return call(getFrame(), null);
    }

    @Override
    public E send(A a) {
        return call(getFrame(), a);
    }

    @Override
    public java.lang.Iterable<E> till(E e) {
        return new CoIterableWithExitCondition(new ExitOnYieldedEqualsTo<E>(e));
    }

    @Override
    public Iterable<E> till(ExitCondition<E> condition) {
        return new CoIterableWithExitCondition(condition);
    }

    @Override
    public Iterable<E> till(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        return new CountingCoIterable(count);
    }

    @Override
    public Iterable<E> with(Controler<E, A> controler) {
        return new ControlingCoIterable(controler);
    }

    @Override
    public Iterable<E> withPattern(A... toSend) {
        return new ControlingCoIterable(new StaticPattern<E, A>(toSend));
    }

    private class CoIterable implements Iterable<E> {

        protected E       e;
        protected boolean hasNext = true;

        protected void fetchNext() {
            try {
                e = next();
            } catch (NoSuchElementException e) {
                hasNext = false;
            } catch (CoroutineClosedException e) {
                hasNext = false;
            }
        }

        @Override
        public Iterator<E> iterator() {
            fetchNext();
            return new Iterator<E>() {

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public E next() {
                    if (!hasNext) {
                        throw new NoSuchElementException();
                    }
                    E next = e;
                    fetchNext();
                    return next;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private class CoIterableWithExitCondition extends CoIterable {

        private final ExitCondition<E> cond;

        public CoIterableWithExitCondition(ExitCondition<E> cond) {
            this.cond = cond;
        }

        @Override
        protected void fetchNext() {
            super.fetchNext();
            if (hasNext) {
                hasNext = !cond.exit(e);
                if (!hasNext) {
                    close();
                }
            }
        }
    }

    private class ControlingCoIterable extends CoIterable {

        private final Controler<E, A> controler;
        private A                     toSend;

        public ControlingCoIterable(Controler<E, A> controler) {
            this.controler = controler;
            toSend = controler.init();
        }

        @Override
        protected void fetchNext() {
            try {
                e = send(toSend);
                toSend = controler.respondTo(e);
            } catch (NoSuchElementException e) {
                controler.noNextElement();
                hasNext = false;
            } catch (CoroutineClosedException e) {
                controler.closed();
                hasNext = false;
            } catch (IllegalStateException e) {
                hasNext = false;
            }
        }
    }

    private class CountingCoIterable extends CoIterable {

        private int       calls;
        private final int count;

        public CountingCoIterable(int count) {
            this.count = count;
        }

        @Override
        protected void fetchNext() {
            if (++calls > count) {
                close();
                hasNext = false;
            } else {
                super.fetchNext();
            }
        }
    }
}
