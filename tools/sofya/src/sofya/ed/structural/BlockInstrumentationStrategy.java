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
 * <p>Specifies that a component uses or depends on a basic block instrumentation
 * strategy. Defines methods to specify which types of basic blocks are selected
 * for instrumentation or observation.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public interface BlockInstrumentationStrategy {
    /*************************************************************************
     * Reports whether general basic blocks are selected; general basic
     * blocks are any basic blocks corresponding to actual program code other
     * than method call blocks.
     *
     * @return <code>true</code> if general basic blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCodeBlocksActive();

    /*************************************************************************
     * Sets whether general basic blocks are selected; general basic
     * blocks are any basic blocks corresponding to actual program code other
     * than method call blocks.
     *
     * @param enable <code>true</code> to select general basic blocks,
     * <code>false</code> to ignore.
     */
    public void setCodeBlocksActive(boolean enable);

    /*************************************************************************
     * Reports whether entry blocks are selected.
     *
     * @return <code>true</code> if entry blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areEntryBlocksActive();

    /*************************************************************************
     * Sets whether entry blocks are selected.
     *
     * @param enable <code>true</code> to select entry blocks,
     * <code>false</code> to ignore.
     */
    public void setEntryBlocksActive(boolean enable);

    /*************************************************************************
     * Reports whether exit blocks are selected.
     *
     * @return <code>true</code> if exit blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areExitBlocksActive();

    /*************************************************************************
     * Sets whether exit blocks are selected.
     *
     * @param enable <code>true</code> to select exit blocks,
     * <code>false</code> to ignore.
     */
    public void setExitBlocksActive(boolean enable);

    /*************************************************************************
     * Reports whether call blocks are selected.
     *
     * @return <code>true</code> if call blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCallBlocksActive();

    /*************************************************************************
     * Sets whether call blocks are selected.
     *
     * @param enable <code>true</code> to select call blocks,
     * <code>false</code> to ignore.
     */
    public void setCallBlocksActive(boolean enable);

    /*************************************************************************
     * Gets the bitmask corresponding to the types of basic blocks currently
     * selected.
     *
     * <p>Used for communicating configuration information to certain other
     * components. To be phased out at a future date.</p>
     *
     * @return The bitmask indicating which basic block types have been selected
     * in this configuration.
     */
    public int getTypeFlags();
}
