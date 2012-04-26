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

package sofya.apps.atomicity;

import sofya.ed.semantic.EventSelectionFilter;

/**
 * Abstract base class for all classes which implement a global event
 * classification policy for atomicity checking.
 *
 * <p>The classification determined for the event is published in a public
 * field before events are demultiplexed by thread and forwarded to the
 * automata which are checking the atomicity property for individual
 * method invocations. The automata may choose to use the published
 * classification if global information permits a better classification
 * for the event than is possible based on local information available to the
 * automata.</p>
 *
 * @author Alex Kinneer
 * @version 10/25/2005
 */
public abstract class EventClassifier extends EventSelectionFilter {
    /** Published classification determined by the global classifier
        for the most recent event. */
    public EventClass eventClass = EventClass.BOTH_MOVER;
    
    /**
     * Type-safe enumeration of the possible classifications defined for
     * events by the reduction-based algorithm.
     */
    public static final class EventClass {
        /** Integer constant of the enumeration element, sometimes useful for
            switch statements. */
        public final int code;
        
        /** Integer constant for right-mover. */
        public static final int IRIGHT_MOVER = 1;
        /** Integer constant for left-mover. */
        public static final int ILEFT_MOVER  = 2;
        /** Integer constant for non-mover. */
        public static final int INON_MOVER   = 3;
        /** Integer constant for both-mover. */
        public static final int IBOTH_MOVER  = 4;
        
        /** Typed constant for a right-mover event. */
        public static final EventClass RIGHT_MOVER =
            new EventClass(IRIGHT_MOVER);
        /** Typed constant for a left-mover event. */
        public static final EventClass LEFT_MOVER =
            new EventClass(ILEFT_MOVER);
        /** Typed constant for a non-mover event. */
        public static final EventClass NON_MOVER =
            new EventClass(INON_MOVER);
        /** Typed constant for a both-mover event. */
        public static final EventClass BOTH_MOVER =
            new EventClass(IBOTH_MOVER);
            
        private EventClass() {
            throw new AssertionError("Illegal constructor");
        }
        
        private EventClass(int code) {
            this.code = code;
        }
        
        public int toInt() {
            return code;
        }
        
        public String toString() {
            switch (code) {
            case IRIGHT_MOVER: return "R";
            case ILEFT_MOVER: return "L";
            case INON_MOVER: return "N";
            case IBOTH_MOVER: return "B";
            default:
                throw new AssertionError();
            }
        }
    }
}
