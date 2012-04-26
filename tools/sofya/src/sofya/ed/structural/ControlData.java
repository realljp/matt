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

/**
 * <p>This class provides thread identity and synchronization variables that
 * are used by a {@link ProgramEventDispatcher} when it is executed with
 * another <code>ProgramEventDispatcher</code> as its subject. In such cases,
 * the invoking <code>ProgramEventDispatcher</code> must create two processing
 * threads, one to receive information from the subject event dispatcher,
 * and another to receive information from the subject {@link SocketProbe},
 * which is running in the subject of the subject event dispatcher.</p>
 *
 * <p>Much of the data in this class is static and thus is shared between
 * all instances. Separate instances are allocated only to assign independent
 * thread identifiers to each processing thread. This class cannot be used
 * to synchronize more than two threads, as there should be no need for
 * that capability.</p>
 *
 * @author Alex Kinneer
 * @version 11/07/2006
 */
public final class ControlData {
    /** Thread ID to be associated with the processing thread to which this
        control object will be passed; the only variable allocated on a
        per-instance basis. */
    private int threadID;
    /** Flags used to indicate whether processing threads are (still)
        connected and processing probes. */
    static volatile boolean[] threadConnected = new boolean[2];
    /** Flags used to force processing threads to stop. */
    static volatile boolean[] forceStop = new boolean[2];
    /** Holding locations for exceptions thrown during processing thread
        execution. */
    static Throwable[] err = new Throwable[2];
    /** Lock object used to synchronize access to the connection flags. */
    public final static Object stateLock = new Object();

    private ControlData() {
    }

    /**
     * Creates a new thread control object.
     *
     * @param threadID Identifier to be associated with the processing thread to
     * which this control object will be passed.
     */
    ControlData(int threadID) {
        this.threadID = threadID;
    }

    /**
     * Gets the ID associated with the processing thread that owns this
     * control object.
     *
     * @return The numeric identifier to be used by the thread using this
     * control object.
     */
    public int getThreadID() {
        return threadID;
    }

    /**
     * Gets the thread connection state flags for the threads coordinated
     * by this control object.
     *
     * @return The shared thread connection state flags used by the processing
     * threads to indicated whether they are (still) connected and processing
     * probes.
     */
    public boolean[] getConnectionFlags() {
        return threadConnected;
    }

    /**
     * Gets the thread stop flags for the threads coordinated by this control
     * object.
     *
     * @return The shared thread stop flags used to signal the processing
     * threads to halt processing.
     */
    public boolean[] getStopFlags() {
        return forceStop;
    }

    /**
     * Gets the exceptions caught by the processing threads during probe
     * processing.
     *
     * @return The last exception caught by each processing thread during
     * probe processing, if any. This method is guaranteed to return an
     * array of throwables, but the contents of any element may be
     * <code>null</code>, in which case processing completed normally.
     */
    public Throwable[] getExceptionStorage() {
        return err;
    }
}
