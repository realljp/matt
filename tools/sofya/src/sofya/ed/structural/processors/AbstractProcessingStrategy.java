/*
 * Copyright 2003-2007, Regents of the University of Nebraska
 *
 *  Licensed under the University of Nebraska Open Academic License,
 *  Version 1.0 (the "License"); you may not use this file except in
 *  compliance with the License. The License must be provided with
 *  the distribution of this software; if the license is absent from
 *  the distribution, please report immediately to galileo@cse.unl.edu
 *  and indicate where you obtained this software.
 *
 *  You may also obtain a copy of the License at:
 *
 *      http://sofya.unl.edu/LICENSE-1.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package sofya.ed.structural.processors;

import java.io.PrintStream;

import sofya.ed.structural.EventDispatcherConfiguration;

/**
 * <p>Base class for strategies to receive and process probes for a
 * structural event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 03/15/2006
 */
public abstract class AbstractProcessingStrategy {
    /** The instrumentation mode detected in the subject. */
    protected int instMode = -1;

    /** Stream to which filter's normal outputs should be written. */
    protected static PrintStream stdout;
    /** Stream to which filter's error outputs should be written. */
    protected static PrintStream stderr;
    /** Stream to which subject's output should be written. */
    protected static PrintStream sbjout;

    protected AbstractProcessingStrategy() {
    }

    /**
     * <p>Registers this component with the event dispatcher.</p>
     *
     * @param edConfig The current configuration of system global resources
     * and settings that the component will use as appropriate.
     */
    public void register(EventDispatcherConfiguration edConfig) {
        stdout = edConfig.getStandardOutput();
        stderr = edConfig.getStandardError();
        sbjout = edConfig.getSubjectOutput();
    }
}
