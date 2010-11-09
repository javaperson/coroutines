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
 * This exception indicates operation on closed coroutine
 * 
 * @author Marcin Rzeźnicki
 * 
 */
public class CoroutineClosedException extends RuntimeException {

    public CoroutineClosedException() {
    }

    public CoroutineClosedException(String message) {
        super(message);
    }

    private static final long serialVersionUID = -7721333642446899927L;
}
