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

	private boolean		initialized;

	private boolean		isFinal;

	private final int	logicalIndex;

	private String		name;

	private boolean		temporary	= true;

	private Type		type;

	public VariableInfo(
			int logicalIndex) {
		this(logicalIndex, null, false);
	}

	public VariableInfo(
			int logicalIndex,
			Type type) {
		this(logicalIndex, type, false);
	}

	public VariableInfo(
			int logicalIndex,
			Type type,
			boolean initialized) {
		this.logicalIndex = logicalIndex;
		this.type = type;
		this.initialized = initialized;
	}

	public int getLogicalIndex() {
		return logicalIndex;
	}

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	public boolean isFinal() {
		return isFinal;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public boolean isTemporary() {
		return temporary;
	}

	public void setFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setTemporary(boolean temporary) {
		this.temporary = temporary;
	}

	public void setType(Type type) {
		this.type = type;
	}
}
