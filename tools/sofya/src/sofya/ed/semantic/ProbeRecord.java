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

package sofya.ed.semantic;

import java.util.Set;

import sofya.base.MethodSignature;

/**
 * Maintains the information necessary to identify and track the location
 * of a bytecode probe, and track the clients still actively requesting the
 * event raised by the probe.
 * 
 * @author Alex Kinneer
 * @version 09/28/2006
 */
final class ProbeRecord {
    /** Numeric identifier associated with the probe, for tracking. Also
        used to correlate multiple bytecode changes associated with the
        same logical probe. */
    public final int id;
    /** Signature of the method containing the bytecode probe. */
    public final MethodSignature location;
    /** Set of keys associated with clients that actively require the
        event produced by this probe. */
    public Set<String> liveKeys;
    /** Maintains a count of the number of individual byte code changes
        associated with this logical probe. Used by the probe logger
        to ensure that the logical probe record is not removed until
        all actual bytecode changes have been removed. */
    public short changeCount;

    private ProbeRecord() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new probe record.
     * 
     * @param id Identifier to be associated with this probe record.
     * @param location Method containing the actual bytecode change(s).
     * @param liveKeys Set of keys associated with clients requesting
     * that this probe be active.
     */
    public ProbeRecord(int id, MethodSignature location,
            Set<String> liveKeys) {
        this.id = id;
        this.location = location;
        this.liveKeys = liveKeys;
    }

    /**
     * Creates a new probe record; used primarily for deserialization
     * of probe records.
     * 
     * @param id Identifier to be associated with this probe record.
     * @param location Method containing the actual bytecode change(s).
     * @param liveKeys Set of keys associated with clients requesting
     * @param changeCount Number of actual bytecode changes associated
     * with this logical probe.
     */
    ProbeRecord(int id, MethodSignature location, Set<String> liveKeys,
            short changeCount) {
        this(id, location, liveKeys);
        this.changeCount = changeCount;
    }

    /**
     * Produces a string representation of this probe record.
     * 
     * @return The resulting string reports its ID, location, and set
     * of live client keys.
     */
    public String toString() {
        return "[ " + id + ", " + location + ", " + liveKeys.size() + " ]";
    }
    
    String toStringExt() {
        StringBuilder str = new StringBuilder(toString());
        str.append("\n");
        str.append("[ ");
        java.util.Iterator<String> iter = liveKeys.iterator();
        while (iter.hasNext()) {
            str.append(iter.next());
            str.append(" ");
        }
        str.append("]");
        return str.toString();
    }
}
