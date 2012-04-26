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

import java.io.PrintStream;

/**
 * An event dispatcher configuration stores global configuration parameters
 * and resources that may be required by various components attached to an
 * event dispatcher.
 *
 * @author Alex Kinneer
 * @version 03/10/2006
 */
public class EventDispatcherConfiguration {
    /** Stream to which standard outputs will be directed. */
    private PrintStream stdout = System.out;
    /** Stream to which standard error outputs will be directed. */
    private PrintStream stderr = System.err;
    /** Stream to which subject program outputs will be directed. */
    private PrintStream sbjout = System.out;

    /** Flag indicating whether the subject program is itself a
        <code>ProgramEventDispatcher</code>. */
    private boolean isSbjDispatcher = false;

    /**
     * Creates a new event dispatcher configuration set to defaults.
     */
    public EventDispatcherConfiguration() {
    }

    /**
     * Resets this configuration to defaults.
     */
    public void clear() {
        this.stdout = System.out;
        this.stderr = System.err;
        this.sbjout = System.out;
        this.isSbjDispatcher = false;
    }

    /**
     * Gets the output stream to which standard outputs will be printed.
     *
     * @return The output stream on which standard outputs produced by the
     * event dispatcher and any registered components will be printed.
     */
    public PrintStream getStandardOutput() {
        return stdout;
    }

    /**
     * Sets the output stream to which standard outputs will be printed.
     *
     * @param stdout The output stream on which standard outputs produced
     * by the event dispatcher and any registered components should be
     * printed.
     */
    public void setStandardOutput(PrintStream stdout) {
        this.stdout = stdout;
    }

    /**
     * Gets the output stream to which standard error outputs will be printed.
     *
     * @return The output stream on which standard error outputs produced by the
     * event dispatcher and any registered components will be printed.
     */
    public PrintStream getStandardError() {
        return stderr;
    }

    /**
     * Sets the output stream to which standard error outputs will be printed.
     *
     * @param stderr The output stream on which standard error outputs produced
     * by the event dispatcher and any registered components should be
     * printed.
     */
    public void setStandardError(PrintStream stderr) {
        this.stderr = stderr;
    }

    /**
     * Gets the output stream to which subject outputs will be printed.
     *
     * @return The output stream on which outputs produced by the
     * subject program will be printed.
     */
    public PrintStream getSubjectOutput() {
        return sbjout;
    }

    /**
     * Sets the output stream to which subject outputs will be printed.
     *
     * @param sbjout The output stream on which outputs produced
     * by the subject program should be printed.
     */
    public void setSubjectOutput(PrintStream sbjout) {
        this.sbjout = sbjout;
    }

    /**
     * Reports whether the subject program is itself an event dispatcher.
     *
     * @return <code>true</code> if the subject program has been identified
     * as being an event dispatcher.
     */
    public boolean isSubjectDispatcher() {
        return isSbjDispatcher;
    }

    /**
     * Specifies whether the subject program is itself an event dispatcher.
     *
     * @param status <code>true</code> to indicate that the subject program
     * is itself an event dispatcher, <code>false</code> otherwise.
     */
    public void setSubjectIsDispatcher(boolean status) {
        this.isSbjDispatcher = status;
    }
}
