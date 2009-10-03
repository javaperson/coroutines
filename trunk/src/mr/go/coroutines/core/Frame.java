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

public final class Frame {

	private boolean			coroutineClosed;

	private int				lineOfCode;

	private Object[]		operands;

	private int				state;

	private final String[]	variableNames;

	private final Object[]	variables;

	public Frame(
			Frame frame) {
		this.variables = frame.variables.clone();
		this.operands = frame.operands.clone();
		this.variableNames = frame.variableNames.clone();
	}

	public Frame(
			int nMaxVariables) {
		this(nMaxVariables, null);
	}

	public Frame(
			int maxVariables,
			String[] variableNames) {
		this.variables = new Object[maxVariables];
		this.operands = new Object[0];
		this.variableNames = variableNames;
	}

	public int getLineOfCode() {
		return lineOfCode;
	}

	public Object[] getLocals() {
		return variables;
	}

	public Object[] getOperands() {
		return operands;
	}

	public int getState() {
		return state;
	}

	public Object getThis() {
		return variables[0];
	}

	public String[] getVariableNames() {
		return variableNames;
	}

	public boolean isCoroutineClosed() {
		return coroutineClosed;
	}

	public void markCoroutineClosed() {
		coroutineClosed = true;
	}

	public void setLineOfCode(int lineOfCode) {
		this.lineOfCode = lineOfCode;
	}

	public void setOperands(Object[] stack) {
		operands = stack;
	}

	public void setState(int state) {
		this.state = state;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("\n[Frame:\nLocal variables:\n");
		int varIndex = 0;
		if (variableNames == null) {
			for (Object variable : variables) {
				sb.append(varIndex++).append(':').append('\t');
				if (variable == null) {
					sb.append("null");
				} else {
					sb.append(variable.toString());
				}
				sb.append('\n');
			}
		} else {
			for (Object variable : variables) {
				String varName = variableNames[varIndex++];
				sb.append(varName).append(':').append('\t');
				if (variable == null) {
					sb.append("null");
				} else {
					sb.append(variable.toString());
				}
				sb.append('\n');
			}
		}
		sb.append("\nStack:\n");
		for (Object operand : operands) {
			if (operand == null) {
				sb.append("null");
			} else {
				sb.append(operand.toString());
			}
			sb.append('\n');
		}
		sb.append("\nState: ").append(state);
		sb.append("]\n");
		return sb.toString();
	}

	public static final int	CLOSED_STATE	= -1;

}