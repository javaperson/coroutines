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

	private Type		tester;

	private boolean		threadLocal;

	private String[]	variableNames;

	public Type getTester() {
		return tester;
	}

	int getAccess() {
		return access;
	}

	int getArgsLength() {
		return argsLength;
	}

	int getArgsSize() {
		return argsSize;
	}

	Type[] getArgsTypes() {
		return argsTypes;
	}

	String getCoroutineName() {
		return coroutineName;
	}

	String[] getExceptions() {
		return exceptions;
	}

	int getMaxVariables() {
		return maxVariables;
	}

	String getMethod() {
		return method;
	}

	Type getOwner() {
		return owner;
	}

	String getSignature() {
		return signature;
	}

	String[] getVariableNames() {
		return variableNames;
	}

	boolean isCategory2ArgumentPresent() {
		return isCategory2ArgumentPresent;
	}

	boolean isJUnitTest() {
		return tester != null;
	}

	boolean isRequestNext() {
		return requestNext;
	}

	boolean isStatic() {
		return isStatic;
	}

	boolean isThreadLocal() {
		return threadLocal;
	}

	void setAccess(int access) {
		this.access = access;
	}

	void setArgsLength(int argsLength) {
		this.argsLength = argsLength;
	}

	void setArgsSize(int argsSize) {
		this.argsSize = argsSize;
	}

	void setArgsTypes(Type[] argsTypes) {
		this.argsTypes = argsTypes;
		for (int i = 0; i < argsTypes.length; i++) {
			int sort = argsTypes[i].getSort();
			if (sort == Type.LONG || sort == Type.DOUBLE) {
				isCategory2ArgumentPresent = true;
			}
		}
	}

	void setCoroutineName(String coroutineName) {
		this.coroutineName = coroutineName;
	}

	void setExceptions(String[] exceptions) {
		this.exceptions = exceptions;
	}

	void setMaxVariables(int maxVariables) {
		this.maxVariables = maxVariables;
	}

	void setMethod(String method) {
		this.method = method;
	}

	void setOwner(Type owner) {
		this.owner = owner;
	}

	void setRequestNext(boolean requestNext) {
		this.requestNext = requestNext;
	}

	void setSignature(String signature) {
		this.signature = signature;
	}

	void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	void setTester(Type value) {
		this.tester = value;
	}

	void setThreadLocal(boolean threadLocal) {
		this.threadLocal = threadLocal;
	}

	void setVariableNames(String[] variableNames) {
		this.variableNames = variableNames;
	}
}
