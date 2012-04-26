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

package sofya.ed.structural;

import sofya.base.SConstants.TraceObjectType;

/**
 * <p>A JUnit processing strategy implements a strategy to be used by a
 * {@link JUnitEventDispatcher} to receive probes received from a subject
 * instrumented for execution in a JUnit test runner and dispatch them
 * as events.</p>
 *
 * @author Alex Kinneer
 * @version 11/17/2006
 */
public interface JUnitProcessingStrategy
        extends ActiveComponent, Sofya$$Probe$10_36_3676__ {
    /**
     * Notifies the processor to perform any setup required to receive
     * probes from the subject.
     */
    void initialize();

    /**
     * Notifies the processor that a new test case is about to execute;
     * this is normally relayed to attached listeners.
     *
     * @param testNum The number of the test case about to execute.
     */
    void newTest(int testNum);

    /**
     * Notifies the processor that a test case has finished executing;
     * this is normally relayed to attached listeners.
     *
     * @param testNum The number of the test case that finished executing.
     */
    void endTest(int testNum);

    /**
     * Gets the type of structural object for which this processor can receive
     * probes.
     *
     * <p>This is used by the JUnit event dispatcher for certain configuration
     * activities.</p>
     *
     * @return The type of structural object for which this processor knows how
     * to receive probes.
     */
    TraceObjectType getObjectType();

    /**
     * Checks whether any exceptions have been raised during processing, and
     * rethrows the exception for handling if so. This method returns without
     * action if there are no errors.
     *
     * @throws Exception For any exception that was raised and stored.
     */
    void checkError() throws Exception;
    
    /**
     * Checks whether the last test case was instrumented.
     *
     * <p>If the test case executed instrumented code, this method will
     * return <code>true</code> until the next call to {@link #newTest},
     * otherwise it returns <code>false</code>.</p>
     *
     * @return <code>true</code> if the last test case executed instrumented
     * code and <code>newTraceFile</code> has not yet been called,
     * <code>false</code> otherwise.
     */
    boolean checkInstrumented();
    
    /**
     * Reports the instrumentation mode detected in the subject.
     *
     * @return The numeric constant for the instrumentation mode detected in
     * the subject (e.g. coverage, sequence, compatibility), or -1 if
     * no instrumentation has yet been encountered in the subject.
     */
    int getInstrumentationMode();
}
