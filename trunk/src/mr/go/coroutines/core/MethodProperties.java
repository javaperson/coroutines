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

final class MethodProperties {

	private int			access;

	private int			argsLength;

	private int			argsSize;

	private Type[]		argsTypes;

	private String		coroutineName;

	private String[]	exceptions;

	private boolean		isCategory2ArgumentPresent;

	private boolean		isStatic;

	private int			maxVariables;

	private String		method;

	private Type		owner;

	private boolean		requestNext;

	private String		signature;

	private boolean		threadLocal;

	private String[]	variableNames;

	public int getAccess() {
		return access;
	}

	public int getArgsLength() {
		return argsLength;
	}

	public int getArgsSize() {
		return argsSize;
	}

	public Type[] getArgsTypes() {
		return argsTypes;
	}

	public String getCoroutineName() {
		return coroutineName;
	}

	public String[] getExceptions() {
		return exceptions;
	}

	public int getMaxVariables() {
		return maxVariables;
	}

	public String getMethod() {
		return method;
	}

	public Type getOwner() {
		return owner;
	}

	public String getSignature() {
		return signature;
	}

	public String[] getVariableNames() {
		return variableNames;
	}

	public boolean isCategory2ArgumentPresent() {
		return isCategory2ArgumentPresent;
	}

	public boolean isRequestNext() {
		return requestNext;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isThreadLocal() {
		return threadLocal;
	}

	public void setAccess(int access) {
		this.access = access;
	}

	public void setArgsLength(int argsLength) {
		this.argsLength = argsLength;
	}

	public void setArgsSize(int argsSize) {
		this.argsSize = argsSize;
	}

	public void setArgsTypes(Type[] argsTypes) {
		this.argsTypes = argsTypes;
		for (int i = 0; i < this.argsTypes.length; i++) {
			int sort = this.argsTypes[i].getSort();
			if (sort == Type.LONG || sort == Type.DOUBLE) {
				isCategory2ArgumentPresent = true;
			}
		}
	}

	public void setCoroutineName(String coroutineName) {
		this.coroutineName = coroutineName;
	}

	public void setExceptions(String[] exceptions) {
		this.exceptions = exceptions;
	}

	public void setMaxVariables(int maxVariables) {
		this.maxVariables = maxVariables;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public void setOwner(Type owner) {
		this.owner = owner;
	}

	public void setRequestNext(boolean requestNext) {
		this.requestNext = requestNext;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	public void setThreadLocal(boolean threadLocal) {
		this.threadLocal = threadLocal;
	}

	public void setVariableNames(String[] variableNames) {
		this.variableNames = variableNames;
	}

}
