/*
 * Copyright [2010] [Marcin Rzeźnicki]

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
package mr.go.coroutines.core;

import org.objectweb.asm.Type;

final class Types {

	static final Type	BOOLEAN_ARRAY		= Type.getType(boolean[].class);

	static final Type	BYTE_ARRAY			= Type.getType(byte[].class);

	static final Type	CHAR_ARRAY			= Type.getType(char[].class);

	static final Type	CLASS_TYPE			= Type.getType(Class.class);

	static final Type	DOUBLE_ARRAY		= Type.getType(double[].class);

	static final Type	FLOAT_ARRAY			= Type.getType(float[].class);

	static final Type	INT_ARRAY			= Type.getType(int[].class);

	static final Type	JAVA_LANG_OBJECT	= Type.getType(Object.class);

	static final Type	JAVA_LANG_STRING	= Type.getType(String.class);

	static final Type	LONG_ARRAY			= Type.getType(long[].class);

	static final Type	OBJECT_ARRAY		= Type.getType(Object[].class);

	static final Type	SHORT_ARRAY			= Type.getType(short[].class);
}
