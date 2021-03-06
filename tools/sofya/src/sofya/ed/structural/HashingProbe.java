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
 * Specialized probe class used to generate a hashed &quot;fingerprint&quot;
 * of the trace of execution of a program.
 * 
 * @author Alex Kinneer
 * @version 02/28/2007
 */
public final class HashingProbe {
    static int hashCode = 0;
    private static volatile boolean initialized = false;

    // Do not instantiate...
    private HashingProbe() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Initialize the probe; called by instrumentation.
     */
    public static final void initialize() {
        if (!initialized) {
            initialized = true;
            Runtime.getRuntime().addShutdownHook(new ShutdownHook());
       }
    }

    /**
     * Update the hash based with the globally unique index of an
     * executed basic block.
     * 
     * @param globalIndex Globally unique index for the witnessed basic
     * block; generated by the instrumentor.
     */
    public static final synchronized void blockEvent(int globalIndex) {
        // Hashing function adapted from version attributed to P.J. Weinberger:
        // http://www.cs.berkeley.edu/~jrs/61bf06/lec/23

        hashCode = (hashCode << 16) + globalIndex;
        hashCode = (hashCode & 0x0ffffffff) ^ ((hashCode & 0xf0000000) >> 24);
    }

    /**
     * Shutdown hook to ensure that the hash result is printed.
     * 
     * <p>Even this cannot save the result if the VM is killed by
     * SIGTERM (kill -9).</p>
     */
    static final class ShutdownHook extends Thread {
        ShutdownHook() {
        }

        public void run() {
            System.out.println();
            System.out.println("Hash result: " + hashCode);
            System.out.println();
        }
    }

}
