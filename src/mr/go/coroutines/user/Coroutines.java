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

import java.util.NoSuchElementException;

/**
 * This class contains methods used when creating coroutine
 * 
 * @author Marcin Rzeźnicki
 * 
 */
public final class Coroutines {

	/**
	 * Exit point of coroutine. Coroutine creator may use it whenever
	 * <code>return</code> statement is needed. This statement throws
	 * {@link NoSuchElementException} in runtime or
	 * <code>InvalidCoroutineException</code> if used in non-coroutine method
	 * (it may be thrown if coroutine instrumentation fails)
	 * 
	 * @param <E>
	 *            anything
	 * @param <A>
	 *            anything
	 * @return useless, used only to satisfy <code>return</code> statements
	 */
	public static <E, A> CoIterator<E, A> _() {
		throw new InvalidCoroutineException();
	}

	/**
	 * Yield point of coroutine. In runtime it suspends coroutine execution and
	 * returns to caller.
	 * 
	 * @param <A>
	 *            type of <code>yield</code>'s result
	 * @return if caller used {@link CoIterator#send(Object) send} to resume
	 *         coroutine execution it becomes result of <code>yield</code>
	 */
	public static <A> A yield() {
		throw new InvalidCoroutineException();
	}

	/**
	 * Yield point of coroutine. In runtime it suspends coroutine execution and
	 * returns <code>e</code> to caller
	 * 
	 * @param <E>
	 *            type of yielded value
	 * @param <A>
	 *            type of <code>yield</code>'s result
	 * @param e
	 *            passed to caller
	 * @return if caller used {@link CoIterator#send(Object) send} to resume
	 *         coroutine execution it becomes result of <code>yield</code>
	 */
	public static <E, A> A yield(E e) {
		throw new InvalidCoroutineException();
	}
}
