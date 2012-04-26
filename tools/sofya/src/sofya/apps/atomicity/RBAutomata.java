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

import sofya.base.MethodSignature;
import sofya.ed.semantic.EventSelectionFilter;
import sofya.apps.atomicity.EventClassifier.EventClass;

/**
 * Atomicity checking automata implementing the simple reduction-based
 * algorithm for a single method invocation on a single thread.
 *
 * @author Alex Kinneer
 * @version 08/15/2006
 */
public class RBAutomata extends EventSelectionFilter {
    /** The current state of the automata. */
    protected int state = ACCEPT_R;

    /** Constant indicating that the automata is in the rejecting state
        (the regular expression R*N&#63;L* has been violated). */
    protected static final int REJECT   = 0;
    /** Constant indicating the automata is in the right-mover or
        non-mover event accepting state. */
    protected static final int ACCEPT_R =  1;
    /** Constant indicating the automata is the left-mover accepting state. */
    protected static final int ACCEPT_L =  2;

    /** Reference to the global event classifier, so that global
        classification can be read if necessary. */
    protected final EventClassifier classifier;
    /** Signature of the method for which this automata is checking for
        atomicity. */
    public final MethodSignature method;

    /**
     * Use of this constructor is not permitted.
     */
    private RBAutomata() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new reduction-based atomicity checking automata.
     *
     * @param classifier Reference to the global event classifier, so that the
     * published global classifications can be accessed if necessary.
     * @param method Signature of the method for which this automata is
     * to check for atomicity.
     */
    public RBAutomata(EventClassifier classifier, MethodSignature method) {
        this.classifier = classifier;
        this.method = method;
    }

    /**
     * Reports whether the automata accepts the current method invocation
     * as atomic.
     *
     * @return <code>true</code> if the automata found this invocation of
     * the method to be atomic, <code>false</code> otherwise.
     */
    public boolean isAccepting() {
        return !(state == REJECT);
    }

    /**
     * Updates the state of the automata based on the current event.
     *
     * @param eventClass Classification of the event as a left-mover,
     * right-mover, non-mover, or both-mover.
     */
    protected void update(EventClass eventClass) {
        switch (state) {
        case ACCEPT_R:
            switch (eventClass.toInt()) {
            case EventClass.IBOTH_MOVER:
            case EventClass.IRIGHT_MOVER:
                break;
            case EventClass.INON_MOVER:
            case EventClass.ILEFT_MOVER:
                state = ACCEPT_L;
                break;
            default:
                throw new AssertionError();
            }
            break;
        case ACCEPT_L:
            switch (eventClass.toInt()) {
            case EventClass.IBOTH_MOVER:
            case EventClass.ILEFT_MOVER:
                break;
            case EventClass.INON_MOVER:
            case EventClass.IRIGHT_MOVER:
                state = REJECT;
                break;
            default:
                throw new AssertionError();
            }
            break;
        case REJECT:
            break;
        default:
            throw new AssertionError();
        }
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        update(EventClass.BOTH_MOVER);
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        update(EventClass.RIGHT_MOVER);
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
       update(EventClass.BOTH_MOVER);
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        update(EventClass.LEFT_MOVER);
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        update(classifier.eventClass);
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        update(classifier.eventClass);
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        update(classifier.eventClass);
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        update(classifier.eventClass);
    }
}
