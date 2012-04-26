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

import org.apache.bcel.Constants;
import org.apache.bcel.generic.Type;

/**
 * Constants relevant to the semantic event dispatch classes.
 *
 * @author Alex Kinneer
 * @version 01/09/2007
 */
public final class SemanticConstants {
    private SemanticConstants() { }

    /** Probe bitmask for an observable event. */
    public static final int TYPE_EVENT  = 0x00000000;
    /** Probe bitmask for an assertion. */
    public static final int TYPE_ASSERT = 0x40000000;

    // final static int <TYPE_UNUSED_1> = 0x80000000;
    // final static int <TYPE_UNUSED_2> = 0xC0000000;

    public static final int SIGNAL_CONNECTED = 1;

    /** User code execution started event. */
    public static final byte EVENT_START          = 1;
    /** Thread started event. */
    public static final byte EVENT_THREAD_START   = 2;
    /** Thread terminated event. */
    public static final byte EVENT_THREAD_DEATH   = 3;

    /** NEW instruction event (object is not yet initialized). */
    public static final byte EVENT_NEW_OBJ        = 4;
    /** Static field access event. */
    public static final byte EVENT_GETSTATIC      = 5;
    /** Static field write event. */
    public static final byte EVENT_PUTSTATIC      = 6;
    /** Instance field access event. */
    public static final byte EVENT_GETFIELD       = 7;
    /** Instance field write event. */
    public static final byte EVENT_PUTFIELD       = 8;

    /** Thread contending for monitor event. */
    public static final byte EVENT_MONITOR_CONTEND     = 10;
    /** Thread acquired monitor event. */
    public static final byte EVENT_MONITOR_ACQUIRE     = 11;
    /** Thread about to release monitor event. */
    public static final byte EVENT_MONITOR_PRE_RELEASE = 12;
    /** Thread released monitor event. */
    public static final byte EVENT_MONITOR_RELEASE     = 13;

    /** Constructor invocation event. */
    public static final byte EVENT_CONSTRUCTOR    = 20;
    /** Static call event. */
    public static final byte EVENT_STATIC_CALL    = 21;
    /** Virtual call event. */
    public static final byte EVENT_VIRTUAL_CALL   = 22;
    /** Interface call event. */
    public static final byte EVENT_INTERFACE_CALL = 23;
    /** Call return event. */
    public static final byte EVENT_CALL_RETURN    = 24;

    /** Virtual method entered event. */
    public static final byte EVENT_VMETHOD_ENTER     = 30;
    /** Virtual method exited event. */
    public static final byte EVENT_VMETHOD_EXIT      = 31;
    /** Object constructor entered event. */
    public static final byte EVENT_CONSTRUCTOR_ENTER = 32;
    /** Object constructor exited event. */
    public static final byte EVENT_CONSTRUCTOR_EXIT  = 33;
    /** Static initializer entered event. */
    public static final byte EVENT_STATIC_INIT_ENTER = 34;
    /** Static method entered event. */
    public static final byte EVENT_SMETHOD_ENTER     = 36;
    /** Static method exited event. */
    public static final byte EVENT_SMETHOD_EXIT      = 37;

    /** Exception thrown event. */
    public static final byte EVENT_THROW          = 40;
    /** Exception caught event. */
    public static final byte EVENT_CATCH          = 41;
    
    /** A special type placeholder signifying any legal Java type (used
        primarily to support wildcards).  */
    public static final Type TYPE_ANY = new AnyType(
        org.apache.bcel.Constants.T_UNKNOWN, "<Any Type>");
    
    /**
     * Utility method to map a field instruction opcode to its matching
     * Sofya event constant.
     * 
     * @param opcode Opcode of the field instruction to be mapped.
     * 
     * @return The Sofya event code that indicates a field event of the
     * type matching the given opcode.
     * 
     * @throws IllegalArgumentException If the given opcode does not
     * correlate to a field instruction.
     */
    public static final byte fieldOpcodeToConstant(int opcode) {
        switch (opcode) {
        case Constants.GETSTATIC:
            return EVENT_GETSTATIC;
        case Constants.PUTSTATIC:
            return EVENT_PUTSTATIC;
        case Constants.GETFIELD:
            return EVENT_GETFIELD;
        case Constants.PUTFIELD:
            return EVENT_PUTFIELD;
        default:
            throw new IllegalArgumentException("Opcode " + opcode +
                " is not a field instruction");
        }
    }
    
    /**
     * A special placeholder to signify any legal Java type, in a
     * type-compatible manner.
     * 
     * <p>Among other uses, this can support type &quot;wildcards&quot;,
     * where a particular field can indicate either a constraint to a
     * specific Java type, or the set of all possible types
     * (the wildcard).</p>
     */
    private static final class AnyType extends Type {
        private static final long serialVersionUID = -165508938556096207L;

        private AnyType(byte t, String s) {
            super(t, s);
        }
        
        public String getSignature() {
            return "@ANY";
        }
        
        public boolean equals(Object obj) {
            return (this == obj); // A singleton
        }
        
        public int hashCode() {
            return 478321513;  // Random
        }
        
        public String toString() {
            return signature;
        }
    }
}
