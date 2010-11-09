/*
 * Copyright 2010 Marcin Rzeźnicki

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

public class StaticPattern<E, A> implements Controler<E, A> {

    private int       callCount;
    private final A   initial;
    private final A[] toSend;

    public StaticPattern(A initial, A[] toSend) {
        this.toSend = toSend;
        this.initial = initial;
    }

    public StaticPattern(A[] toSend) {
        this(toSend[0], toSend);
        callCount = 1;
    }

    @Override
    public void closed() {
    }

    @Override
    public A init() {
        return initial;
    }

    @Override
    public void noNextElement() {
    }

    @Override
    public A respondTo(E produced) throws IllegalStateException {
        if (callCount >= toSend.length) {
            throw new IllegalStateException();
        }
        return toSend[callCount++];
    }
}
