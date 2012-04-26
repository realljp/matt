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

/**
 * A constants class that records the names of probe fields and
 * trigger method names needed by the instrumentor and event dispatcher
 * components, with mappings to constant integers.
 *
 * <p>The event dispatcher needs to know the names of trigger methods
 * used to signal the JDI so that it can set breakpoints, and the
 * names of certain probe fields so that it can determine what processing
 * action to take based on the name of the field on which the probe
 * event occurred. The naive approach is to perform an if/elseif search
 * to determine what action to take based on the field name. However,
 * experimental evidence shows that it is more efficient to hash the
 * string for the field name to retrieve a numeric constant associated
 * with the field, and then switch on that constant. This is because
 * the hash code is presumably cached for (immutable) strings, so it
 * is a fast O(C) operation to hash the string to a matching constant,
 * and then an O(C) operation to perform the (jump-table-based) switch
 * operation. The more costly character-by-character comparison only
 * has to be performed in the case of a hash collision, rather than
 * on every non-matching string that precedes the match, as in the
 * case of an if/elseif approach. The experimental data showed that
 * the hash-and-switch technique was consistently 3 to 4 times faster.</p>
 *
 * <p>This class uses no arrays or collections to actually store the
 * string or integer constants. This is to ensure that components such
 * as the instrumentor can statically propagate the constants at compile
 * time, for efficiency reasons. The lookup table for fast string-
 * to-constant correlation is populated on demand.</p>
 *
 * @author Alex Kinneer
 * @version 08/09/2007
 */
final class InstrumentationMetadata {
    /**
     * Key object used to correlate breakpoints to the type of event
     * they are capturing. This is required for the event dispatcher to
     * determine how to process a breakpoint corresponding to an
     * instrumentation probe.
     */
    static final class TriggerKey {
        final int keyIndex;
        
        static final int ISTATIC_PACKET          = 1;
        static final int IOBJECT_PACKET          = 2;
        static final int IMONITOR_PACKET         = 3;
        static final int ICATCH_PACKET           = 4;
        static final int IFIELD_BREAKPOINT       = 5;
        static final int IARRAY_LOAD_BREAKPOINT  = 6;
        static final int IARRAY_STORE_BREAKPOINT = 7;
        
        static final TriggerKey STATIC_PACKET =
            new TriggerKey(ISTATIC_PACKET);
        static final TriggerKey OBJECT_PACKET =
            new TriggerKey(IOBJECT_PACKET);
        static final TriggerKey MONITOR_PACKET =
            new TriggerKey(IMONITOR_PACKET);
        static final TriggerKey CATCH_PACKET =
            new TriggerKey(ICATCH_PACKET);
        static final TriggerKey FIELD_BREAKPOINT =
            new TriggerKey(IFIELD_BREAKPOINT);
        static final TriggerKey ARRAY_LOAD_BREAKPOINT =
            new TriggerKey(IARRAY_LOAD_BREAKPOINT);
        static final TriggerKey ARRAY_STORE_BREAKPOINT =
            new TriggerKey(IARRAY_STORE_BREAKPOINT);
        
        private TriggerKey() {
            throw new AssertionError("Illegal constructor");
        }
        
        private TriggerKey(int keyIndex) {
            this.keyIndex = keyIndex;
        }
    }
    
    /** Name of the flag field, inserted into each class, that used by the
        event dispatcher to signal when processing of a class prepare event
        has completed. This is a workaround for a JVMTI back-end timing
        bug that sometimes allows user code to execute before the thread
        is suspended and the event is posted. */
    public static final String CPE_FLAG_FIELD_NAME = "_inst$$zk183_$flag$";
    /** Name of the field used by the instrumentation manager to
        communicate information about the classloader when processing
        adaptive instrumentation requests. */
    public static final String CL_LOAD_FIELD_NAME = "_inst$$zk183_$cld$";
    /** Name of the probe field used to insert special signal events
        into the event queue of the event dispatcher. */
    public static final String SIGNAL_FIELD_NAME = "_inst$$zk183_$sig$";
    /** Name of the probe flag field used to ensure that only one user
        code start event is dispatched. */
    public static final String START_FLAG_FIELD_NAME =
        "_inst$$zk183_$user_start$";
    
    /** Constant associated with the name string for the field used to
        insert signal events into the event queue. */
    public static final int SIGNAL_FIELD = 13;

    public static final String TRIGGER_PREFIX = "_inst$$trigger_jdi$";

    public static final String TRIGGER_STATIC_PACKET_NAME =
        TRIGGER_PREFIX + "static$packet";
    public static final String TRIGGER_STATIC_PACKET_SIG = "(I)V";

    public static final String TRIGGER_OBJ_PACKET_NAME =
        TRIGGER_PREFIX + "packet$";
    public static final String TRIGGER_OBJ_PACKET_SIG =
        "(Ljava/lang/Object;I)V";

    public static final String TRIGGER_MON_PACKET_NAME =
        TRIGGER_PREFIX + "mon$";
    public static final String TRIGGER_MON_PACKET_SIG =
        "(Ljava/lang/Object;B)V";

    public static final String TRIGGER_CATCH_PACKET_NAME =
        TRIGGER_PREFIX + "catch$";
    public static final String TRIGGER_CATCH_PACKET_SIG =
        "(Ljava/lang/Class;)V";
    

    public static final String FIELD_READ_INTERCEPTOR_NAME =
        TRIGGER_PREFIX + "field$read$";
    public static final String FIELD_WRITE_INTERCEPTOR_NAME =
        TRIGGER_PREFIX + "field$write$";
    
    
    public static final String TRIGGER_ARRAY_ELEM_LOAD_NAME =
        TRIGGER_PREFIX + "array$load$";
    public static final String TRIGGER_ARRAY_ELEM_STORE_NAME =
        TRIGGER_PREFIX + "array$store$";

    /** A constants class. */
    private InstrumentationMetadata() {
        throw new AssertionError("Illegal constructor");
    }
}
