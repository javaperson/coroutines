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
package mr.go.coroutines.core;

import org.objectweb.asm.Type;

final class VariableInfo {

	private boolean	initialized;

	private boolean	isFinal;

	private String	name;

	private Type	type;

	VariableInfo(
			Type type) {
		this(type, false);
	}

	VariableInfo(
			Type type,
			boolean isFinal) {
		this.type = type;
		this.initialized = true;
		this.isFinal = isFinal;
	}

	@Override
	public String toString() {
		return type.toString();
	}

	String getName() {
		return name;
	}

	Type getType() {
		return type;
	}

	boolean isFinal() {
		return isFinal;
	}

	boolean isInitialized() {
		return initialized;
	}

	void setFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}

	void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	void setName(String name) {
		this.name = name;
	}

	void setType(Type type) {
		this.type = type;
	}
}
