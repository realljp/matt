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

package sofya.ed;

/**
 * <p>A branch coverage listener is issued notifications by an event
 * dispatcher that are sufficient to guarantee that all branches that
 * were executed are reported as covered.</p>
 *
 * <p>For any given branch, the event dispatcher offers no guarantee
 * that the number of times a coverage event is raised is equal to the number
 * of times the branch was actually executed. It is only guaranteed that
 * if a branch is executed, the coverage event will be raised at least
 * once for that branch. This design is intentional and enables the best
 * performance.</p>
 *
 * @author Alex Kinneer
 * @version 03/17/2006
 */
public interface BranchCoverageListener {
    /**
     * Notification that a branch was covered.
     *
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     * @param branchType The type of the branch that was executed. See
     * {@link sofya.base.SConstants.BranchType}.
     */
    void branchCovered(int id, int branchType);
}
