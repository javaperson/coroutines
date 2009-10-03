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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates method as coroutine.
 * 
 * @author Marcin Rzeźnicki
 * 
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Coroutine {

	/**
	 * If <code>false</code> {@link CoIterator#next() next} is called once
	 * before passing <code>CoIterator</code> to caller
	 * 
	 * @return
	 */
	boolean generator() default true;

	/**
	 * Marks coroutine frame data as thread local which enables to share
	 * {@link CoIterator} instance between many threads with each invocation
	 * having its own state. Default is <code>false</code>
	 * 
	 * @return
	 */
	boolean threadLocal() default false;
}
