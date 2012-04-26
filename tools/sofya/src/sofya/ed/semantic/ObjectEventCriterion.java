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

import sofya.ed.semantic.EventListener.*;

/**
 * Defines a filtering criterion used to filter events from an
 * object-correlated event stream using an {@link ObjectEventFilter}.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 * 
 * @deprecated This is a prototype solution that will likely be superceded
 * by a better design at a future time.
 */
public interface ObjectEventCriterion {
    // TODO: Consider alternative solutions to this problem. This solution
    // does not fit well into the design philosophy of Sofya; a significant
    // aspect of this is that it undermines the method-dispatch based
    // type safety of event processing.

    /**
     * Checks whether an object-correlated method event satisfies
     * this criterion.
     * 
     * @param eventCode Constant indicating the type of method event
     * on which the criterion is being evaluated.
     * @param td Information about the thread in which the event occurred.
     * @param od Information about the object on which the event occurred.
     * @param md Information about the method in which the event occurred.
     * 
     * @return <code>true</code> if the event is to be retained,
     * <code>false</code> if the event is to be filtered (removed).
     */
    boolean isMatch(byte eventCode, ThreadData td, ObjectData od,
            MethodData md);

    /**
     * Checks whether an object-correlated field event satisfies
     * this criterion.
     * 
     * @param eventCode Constant indicating the type of field event
     * on which the criterion is being evaluated.
     * @param td Information about the thread in which the event occurred.
     * @param od Information about the object on which the event occurred.
     * @param fd Information about the field on which the event occurred.
     * 
     * @return <code>true</code> if the event is to be retained,
     * <code>false</code> if the event is to be filtered (removed).
     */
    boolean isMatch(byte eventCode, ThreadData td, ObjectData od,
            FieldData fd);

    /**
     * Checks whether an object-correlated monitor (lock) event satisfies
     * this criterion.
     * 
     * @param eventCode Constant indicating the type of monitor event
     * on which the criterion is being evaluated.
     * @param td Information about the thread in which the event occurred.
     * @param od Information about the object on which the event occurred.
     * @param md Information about the monitor on which the event occurred.
     * 
     * @return <code>true</code> if the event is to be retained,
     * <code>false</code> if the event is to be filtered (removed).
     */
    boolean isMatch(byte eventCode, ThreadData td, ObjectData od,
            MonitorData md);
}
