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

import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public class CoroutineAgent {

	public static void premain(
			String args,
			Instrumentation instrumentationInstance) {
		if (args.isEmpty()) {
			return;
		}
		if (args.equals("runtime")) {
			instrumentationInstance
					.addTransformer(new CoroutineInstrumentator());
		} else if (args.startsWith("runtime-")) {
			String[] options = args.substring(8).split(",");
			boolean debug = false;
			boolean print = false;
			boolean preVerify = false;
			boolean postVerify = false;
			boolean outputBin = false;
			boolean overrideFrames = false;
			for (String option : options) {
				if (option.equals("debug")) {
					debug = true;
				} else if (option.equals("overrideframes")) {
					overrideFrames = true;
				} else if (option.equals("print")) {
					print = true;
				} else if (option.equals("outputbin")) {
					outputBin = true;
				} else if (option.equals("preverify")) {
					preVerify = true;
				} else if (option.equals("postverify")) {
					postVerify = true;
				}
			}
			instrumentationInstance.addTransformer(new CoroutineInstrumentator(
					debug,
					print,
					preVerify,
					postVerify,
					outputBin,
					overrideFrames));
		} else {
			String[] coroutineClasses = args.split(";");
			String lastString = coroutineClasses[coroutineClasses.length - 1];
			if (!lastString.startsWith("-")) {
				instrumentationInstance
						.addTransformer(new CoroutineInstrumentator(
								coroutineClasses));
			} else {
				String[] options = lastString.substring(1).split(",");
				boolean hasDebugOption = false;
				boolean hasPrintOption = false;
				boolean hasPreVerifyOption = false;
				boolean hasPostVerifyOption = false;
				boolean hasOutputBinaryOption = false;
				boolean overrideFrames = false;
				for (String option : options) {
					if (option.equals("debug")) {
						hasDebugOption = true;
					} else if (option.equals("overrideframes")) {
						overrideFrames = true;
					} else if (option.equals("outputbin")) {
						hasOutputBinaryOption = true;
					} else if (option.equals("print")) {
						hasPrintOption = true;
					} else if (option.equals("preverify")) {
						hasPreVerifyOption = true;
					} else if (option.equals("postverify")) {
						hasPostVerifyOption = true;
					}
				}
				coroutineClasses = Arrays.copyOfRange(
						coroutineClasses,
						0,
						coroutineClasses.length - 1);
				instrumentationInstance
						.addTransformer(new CoroutineInstrumentator(
								coroutineClasses,
								hasDebugOption,
								hasPrintOption,
								hasPreVerifyOption,
								hasPostVerifyOption,
								hasOutputBinaryOption,
								overrideFrames));
			}

		}
	}
}
