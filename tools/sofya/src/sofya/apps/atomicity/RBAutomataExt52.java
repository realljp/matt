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
import sofya.base.exceptions.SofyaError;
import sofya.apps.atomicity.EventClassifier.EventClass;

/**
 * Extension of the atomicity checking automata that implements the regular
 * expression described in Lemma 5.2 by Wang and Stoller.
 *
 * @author Alex Kinneer
 * @version 12/20/2005
 */
public class RBAutomataExt52 extends RBAutomata {
    /** ID of the object whose monitor was acquired last. */
    private long lastAcquireId = -1;
    /** ID of the object whose monitor was released last. */
    private long releaseId = -1;
    /** Maintains a count of re-entered locks, so that the AcqRel components
        of the regular expression will accept re-entrant lock acquires
        (they are indistinguishable anyway). */
    private short reenterCount = 0;
    
    /** Constant indicating the automata is in the state where it will only
        accept a left-mover that matches the previous right mover (the
        two events are on the same lock). This implements the
        (L + AcqRel)* component of the extended regular expression. */
    protected static final int ACCEPT_MATCH_R = 3;
    
    /**
     * Creates a new reduction-based atomicity checking automata.
     *
     * @param classifier Reference to the global event classifier, so that the
     * published global classifications can be accessed if necessary.
     * @param method Signature of the method for which this automata is
     * to check for atomicity.
     */
    public RBAutomataExt52(EventClassifier classifier, MethodSignature method) {
        super(classifier, method);
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
                state = ACCEPT_L;
                break;
            case EventClass.ILEFT_MOVER:
                if (releaseId != lastAcquireId) {
                    state = ACCEPT_L;
                }
                break;
            default:
                throw new SofyaError();
            }
            break;
        case ACCEPT_L:
            switch (eventClass.toInt()) {
            case EventClass.IBOTH_MOVER:
            case EventClass.ILEFT_MOVER:
                break;
            case EventClass.INON_MOVER:
                state = REJECT;
                break;
            case EventClass.IRIGHT_MOVER:
                state = ACCEPT_MATCH_R;
                break;
            default:
                throw new SofyaError();
            }
            break;
        case ACCEPT_MATCH_R:
            switch (eventClass.toInt()) {
            case EventClass.IBOTH_MOVER:
                break;
            case EventClass.INON_MOVER:
            case EventClass.IRIGHT_MOVER:
                state = REJECT;
                break;
            case EventClass.ILEFT_MOVER:
                if (releaseId == lastAcquireId) {
                    state = ACCEPT_L;
                }
                else {
                    state = REJECT;
                }
            default:
                throw new SofyaError();
            }
            break;
        case REJECT:
            break;
        default:
            throw new SofyaError();
        }
    }
    
    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        long curId = od.getId();
        
        if (curId == lastAcquireId) {
            reenterCount += 1;
        }
        else {
            lastAcquireId = curId;
        }
        
        update(EventClass.RIGHT_MOVER);
    }
    
    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        releaseId = od.getId();
        
        update(EventClass.LEFT_MOVER);
        
        if ((releaseId == lastAcquireId) && (reenterCount > 0)) {
            reenterCount -= 1;
        }
        else {
            lastAcquireId = -1;
        }
    }
}
