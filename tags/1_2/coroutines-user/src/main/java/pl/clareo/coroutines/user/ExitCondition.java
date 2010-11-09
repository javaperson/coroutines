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
package pl.clareo.coroutines.user;

/**
 * Interface used to automatically close coroutine based on examination of last
 * returned value.
 * 
 * @author Marcin Rzeźnicki
 * 
 * @param <E>
 *            type of value returned by <code>yield</code>
 * @see CoIterator#till(ExitCondition)
 */
public interface ExitCondition<E> {

    /**
     * 
     * @param yielded
     *            last returned value
     * @return <code>true</code> if no more results, produced by coroutine, are
     *         needed, <code>false</code> otherwise
     */
    boolean exit(E yielded);
}
