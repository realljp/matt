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

import java.util.BitSet;

import sofya.ed.BranchCoverageListener;

/**
 * <p>A branch coverage trace records the coverage of branches in a method.</p>
 *
 * @author Alex Kinneer
 * @version 03/13/2006
 */
public class BranchCoverageTrace extends CoverageTrace
        implements BranchCoverageListener {
    /**
     * Creates a new branch coverage trace.
     *
     * @param highestId The highest identifier associated with any branch
     * in the method (effectively the number of branches in the method.
     */
    public BranchCoverageTrace(int highestId) {
        super(highestId);
    }

    /**
     * Records a branch as covered.
     *
     * @param id Identifier associated with the covered branch.
     * @param branchType Numeric constant encoding the type of branch
     * covered (see {@link sofya.base.SConstants.BranchType}).
     */
    public void branchCovered(int id, int branchType) {
        if ((id - 1 > highestId) || (id < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                    "block or branch " + id);
        }
        traceVector.set(id - 1);
    }

    /**************************************************************************
     * Creates a deep clone of this trace object.
     *
     * @return A new trace object with the same number of branches
     * and the same branches marked as covered.
     */
    public CoverageTrace copy() {
        BranchCoverageTrace traceClone = new BranchCoverageTrace(highestId);
        traceClone.traceVector = (BitSet) this.traceVector.clone();

        return traceClone;
    }
}
