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

import sofya.base.SConstants;

/**
 * Placeholder adapter class to interface with legacy instrumentation.
 * 
 * <p>Currently, only compatible mode instrumentation is supported.</p>
 *
 * @author Alex Kinneer
 * @version 11/16/2006
 */
public final class SocketProbe {
    /** Array which stores the hit object IDs sequentially, using
        special marker/index pairs to indicate entry into new methods.
        It is public so that the instrumentation is not required
        to make a method call to retrieve a reference to it. */
    public static int[] sequenceArray;
    /** Index pointing to the next open entry in the sequence array.
        Instrumentation is responsible for updating this pointer when
        recording an object ID, and calling
        <code>writeSequenceData</code> when the array is filled. */
    public static int sequenceIndex = 0;

    /*************************************************************************
     * Direct instantiation is not permitted, use
     * {@link start(int, int, boolean, boolean, int)} instead.
     */
    private SocketProbe() {
        throw new AssertionError("Illegal constructor");
    }

    public static final void start(int port, int instMode,
            boolean doTimestamps, boolean useSignalSocket, int objType) {
        if (instMode != SConstants.INST_COMPATIBLE) {
            // May relax this constraint in the near future, 11/16/06
            System.err.println("Cannot run this type of old " +
                    "instrumentation, please re-instrument");
            System.exit(1);
        }
        
        Sofya$$Probe$10_36_3676__.probe.start(port, instMode, doTimestamps,
            useSignalSocket, objType);
    }

    public static final void finish() {
        Sofya$$Probe$10_36_3676__.probe.finish();
    }

    public static final void writeObjectCount(String mSignature,
            int objCount) {
        Sofya$$Probe$10_36_3676__.probe.writeObjectCount(mSignature, objCount);
    }

    public static final void writeTraceMessage(int bId, String mSignature) {
        Sofya$$Probe$10_36_3676__.probe.writeTraceMessage(bId, mSignature);
    }
    
    public static final byte[] getObjectArray(String mSignature,
            int objCount) {
        return Sofya$$Probe$10_36_3676__.probe.getObjectArray(
            mSignature, objCount);
    }

    public static final void markMethodInSequence(String mSignature,
            int objCount) {
        Sofya$$Probe$10_36_3676__.probe.markMethodInSequence(
            mSignature, objCount);
    }
    
    public static final void writeSequenceData() {
        Sofya$$Probe$10_36_3676__.probe.writeSequenceData();
    }
}
