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
package pl.clareo.coroutines.core;

final class StringConstants {

    static final String CALL_METHOD_DESCRIPTOR             =
                                                             "(Lpl/clareo/coroutines/core/Frame;Ljava/lang/Object;)Ljava/lang/Object;";
    static final String CO_ITERATOR_CONSTRUCTOR_DESCRIPTOR = "(Lpl/clareo/coroutines/core/Frame;)V";
    static final String CO_ITERATOR_DESCRIPTOR             = "Lpl/clareo/coroutines/user/CoIterator;";
    static final String CO_ITERATOR_NAME                   = "pl/clareo/coroutines/user/CoIterator";
    static final String COROUTINE_CLOSED_EXCEPTION         = "pl/clareo/coroutines/user/CoroutineClosedException";
    static final String COROUTINE_DESCRIPTOR               = "Lpl/clareo/coroutines/user/Coroutine;";
    static final String COROUTINE_EXIT_EXCEPTION           = "pl/clareo/coroutines/user/CoroutineExitException";
    static final String COROUTINE_METHOD_DESCRIPTOR        =
                                                             "(Lpl/clareo/coroutines/core/Frame;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    static final String COROUTINES_NAME                    = "pl/clareo/coroutines/user/Coroutines";
    static final String FRAME_NAME                         = "pl/clareo/coroutines/core/Frame";
    static final String INVALID_COROUTINE_EXCEPTION        = "pl/clareo/coroutines/user/InvalidCoroutineException";
}
