/*
 * Copyright [2009] [Marcin Rzeźnicki]

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
package mr.go.coroutines.user;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Interface encapsulating coroutine execution
 * 
 * @author Marcin Rzeźnicki
 * 
 * @param <E>
 *            type of elements returned by coroutine
 * @param <A>
 *            type of elements sent to coroutine
 */
public interface CoIterator<E, A> {

	/**
	 * Informs coroutine that no more operations will be performed. Coroutine
	 * receives {@link CoroutineExitException}. Further calls to coroutine
	 * result in {@link CoroutineClosedException} in caller. All calls to close
	 * after the first one make no effect
	 * 
	 */
	void close();

	/**
	 * results of coroutine as {@link Iterable}. Iterator of result
	 * <code>Iterable</code> will return <code>false</code> from its
	 * {@link Iterator#hasNext() hasNext} method after coroutine throws
	 * {@link NoSuchElementException} or after it has been closed
	 * 
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> each();

	/**
	 * Obtains next result from coroutine
	 * 
	 * @return next result
	 * @throws CoroutineClosedException
	 *             if coroutine has been closed
	 */
	E next();

	/**
	 * Obtains next result from coroutine and sends <code>a</code> to coroutine
	 * (in corutine <code>a</code> will become current
	 * {@link Coroutines#yield() yield} result)
	 * 
	 * @param a
	 *            result of <code>yield</code> in coroutine
	 * @return next result
	 * @throws CoroutineClosedException
	 *             if coroutine has been closed
	 */
	E send(A a);

	/**
	 * results of coroutine as {@link Iterable}. Iterator of result
	 * <code>Iterable</code> will return <code>false</code> from its
	 * {@link Iterator#hasNext() hasNext} method after coroutine throws
	 * {@link NoSuchElementException}, after it has been closed or after last
	 * returned element is equals to <code>e</code> (or <code>null</code> if
	 * <code>e</code> is <code>null</code>). Coroutine is closed when this
	 * <code>Iterable</code> ends
	 * 
	 * 
	 * @param e
	 *            element marking end of iteration (may be <code>null</code>)
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> till(E e);

	/**
	 * results of coroutine as {@link Iterable}. Iterator of result
	 * <code>Iterable</code> will return <code>false</code> from its
	 * {@link Iterator#hasNext() hasNext} method after coroutine throws
	 * {@link NoSuchElementException}, after it has been closed or after
	 * <code>condition</code> evaluates to <code>true</code>. Coroutine is
	 * closed when this <code>Iterable</code> ends
	 * 
	 * @param condition
	 *            condition evaluated whenever coroutine yields
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> till(ExitCondition<E> condition);

	/**
	 * results of coroutine as {@link Iterable}. Iterator of result
	 * <code>Iterable</code> will return <code>false</code> from its
	 * {@link Iterator#hasNext() hasNext} method after coroutine throws
	 * {@link NoSuchElementException}, after it has been closed or after it has
	 * been called <code>count</code> times. Coroutine is closed when this
	 * <code>Iterable</code> ends
	 * 
	 * @param count
	 *            number of desired results from coroutine
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> till(int count);

	/**
	 * results of coroutine as {@link Iterable}. Coroutine is controled by
	 * {@link Controler} instance passed to this method. Coroutine is closed
	 * when this <code>Iterable</code> ends
	 * 
	 * @param controler
	 *            coroutine's controler
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> with(Controler<E, A> controler);

	/**
	 * results of coroutine as {@link Iterable}. Each result is obtained by
	 * calling {@link CoIterator#send(Object) send} method. Parameters passed to
	 * <code>send</code> method are taken from <code>toSend</code> array in
	 * sequence. Coroutine is closed when this <code>Iterable</code> ends
	 * 
	 * @param toSend
	 *            parameters sent to coroutine
	 * @return results of coroutine as <code>Iterable</code>
	 */
	Iterable<E> withPattern(A... toSend);
}
