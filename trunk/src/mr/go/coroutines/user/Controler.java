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

/**
 * Interface used to control coroutine's execution. Its purpose is to further
 * decouple coroutine's caller (client of {@link CoIterator}) from the
 * coroutine's logic. Caller may use {@link CoIterator#with(Controler)} method
 * and consume results without burden of sending appropriate values to
 * underlying coroutine - this task being imposed on this <code>Controler</code>
 * 
 * @author Marcin Rzeźnicki
 */
public interface Controler<E, A> {

	/**
	 * Called when coroutine has been closed
	 */
	void closed();

	/**
	 * Generate value sent to coroutine on the first
	 * {@link Coroutines#yield(Object) yield}
	 * 
	 * @return value to be sent to coroutine
	 */
	A init();

	/**
	 * Called when coroutine has been closed because it was unable to generate
	 * more results
	 */
	void noNextElement();

	/**
	 * Generates the next value sent to coroutine after some
	 * {@link Coroutines#yield() yield} has generated <code>produced</code>
	 * 
	 * @param produced
	 *            last yielded value
	 * @return value to be sent to coroutine
	 * @throws IllegalStateException
	 *             <code>Controler</code> may throw this exception to forcibly
	 *             close underlying coroutine
	 */
	A respondTo(E produced) throws IllegalStateException;
}
