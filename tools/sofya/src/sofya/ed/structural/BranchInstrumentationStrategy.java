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
 * <p>Specifies that a component uses or depends on a branch instrumentation
 * strategy. Defines methods to specify which types of branches are selected
 * for instrumentation or observation.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public interface BranchInstrumentationStrategy {
    /*************************************************************************
     * Reports whether <code>if</code> branches are selected.
     *
     * @return <code>true</code> if <code>if</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areIfBranchesActive();

    /*************************************************************************
     * Sets whether <code>if</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>if</code> branches,
     * <code>false</code> to ignore.
     */
    public void setIfBranchesActive(boolean enable);

    /*************************************************************************
     * Reports whether <code>switch</code> branches are selected.
     *
     * @return <code>true</code> if <code>switch</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areSwitchBranchesActive();

    /*************************************************************************
     * Sets whether <code>switch</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>switch</code> branches,
     * <code>false</code> to ignore.
     */
    public void setSwitchBranchesActive(boolean enable);

    /*************************************************************************
     * Reports whether <code>throws</code> branches are selected.
     *
     * @return <code>true</code> if <code>throws</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areThrowsBranchesActive();

    /*************************************************************************
     * Sets whether <code>throws</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>throws</code> branches,
     * <code>false</code> to ignore.
     */
    public void setThrowsBranchesActive(boolean enable);

    /*************************************************************************
     * Reports whether call branches are selected.
     *
     * @return <code>true</code> if call branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCallBranchesActive();

    /*************************************************************************
     * Sets whether call branches are selected.
     *
     * @param enable <code>true</code> to select call branches,
     * <code>false</code> to ignore.
     */
    public void setCallBranchesActive(boolean enable);

    /*************************************************************************
     * Reports whether entry branches are selected.
     *
     * @return <code>true</code> if entry branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areEntryBranchesActive();

    /*************************************************************************
     * Sets whether entry branches are selected.
     *
     * @param enable <code>true</code> to select entry branches,
     * <code>false</code> to ignore.
     */
    public void setEntryBranchesActive(boolean enable);

    /*************************************************************************
     * Reports whether summary branches are selected.
     *
     * @return <code>true</code> if summary branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areSummaryBranchesActive();

    /*************************************************************************
     * Sets whether summary branches are selected.
     *
     * @param enable <code>true</code> to select summary branches,
     * <code>false</code> to ignore.
     */
    public void setSummaryBranchesActive(boolean enable);

    /*************************************************************************
     * Gets the bitmask corresponding to the types of branches currently
     * selected.
     *
     * <p>Used for communicating configuration information to certain other
     * components. To be phased out at a future date.</p>
     *
     * @return The bitmask indicating which branch types have been selected
     * in this configuration.
     */
    public int getTypeFlags();
}
