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

import java.util.List;
import java.util.Iterator;

import sofya.base.SConstants.BranchType;
import sofya.ed.BadParameterValueException;

import gnu.trove.TIntHashSet;

/**
 * <p>A branch instrumentation configuration stores the types of branches
 * that have been selected for instrumentation or observation by an
 * event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public class BranchInstrumentationConfiguration implements ActiveComponent {
    /** Flag to activate if branches. */
    private boolean ifBranchesOn;
    /** Flag to activate switch branches. */
    private boolean switchBranchesOn;
    /** Flag to activate throws branches. */
    private boolean throwsBranchesOn;
    /** Flag to activate call branches. */
    private boolean callBranchesOn;
    /** Flag to activate entry branches. */
    private boolean entryBranchesOn;
    /** Flag to activate summary branches. */
    private boolean summaryBranchesOn;

    /** Flag indicating whether the configuration is considered in a ready
        state. */
    protected boolean ready;

    /** Set of characters used to heuristically guess the command line
        parameter specifying the branch types to activate. */
    private static TIntHashSet matchChars = new TIntHashSet();

    static {
        matchChars.add('I');
        matchChars.add('S');
        matchChars.add('T');
        matchChars.add('C');
        matchChars.add('E');
        matchChars.add('O');
    }

    /**
     * Creates a new branch configuration.
     *
     * <p>No branches are activated by default.</p>
     */
    public BranchInstrumentationConfiguration() {
    }

    public void register(EventDispatcherConfiguration edConfig) {
    }

    public List<String> configure(List<String> params) {
        boolean typeParamFound = false;
        String bestMatch = null;
        int bestMatchCount = 0;

        Iterator<String> li = params.iterator();
        paramLoop:
        while (li.hasNext()) {
            String param = li.next();
            int length = param.length();

            if (param.startsWith("-") && (length <= 7)) {
                int curMatchCount = 0;

                for (int j = 1; j < length; j++) {
                    if (!matchChars.contains(param.charAt(j))) {
                        if (curMatchCount >= bestMatchCount) {
                            bestMatch = param;
                            bestMatchCount = curMatchCount;
                        }
                        continue paramLoop;
                    }
                    curMatchCount++;
                }

                parseParameter(param);
                typeParamFound = true;

                li.remove();
                break;
            }
        }

        if (!typeParamFound) {
            if (bestMatchCount > 0) {
                parseParameter(bestMatch);
            }
            else {
                throw new IllegalArgumentException("Branch types not " +
                        "specified");
            }
        }

        this.ready = true;

        return params;
    }

    /**
     * Parses branch types from a command line parameter.
     *
     * @param param The command line parameter to attempt to parse.
     */
    private void parseParameter(String param) {
        int length = param.length();
        for (int i = 1; i < length; i++) {
            switch(param.charAt(i)) {
            case 'I':
                ifBranchesOn = true;
                break;
            case 'S':
                switchBranchesOn = true;
                break;
            case 'T':
                throwsBranchesOn = true;
                break;
            case 'C':
                callBranchesOn = true;
                break;
            case 'E':
                entryBranchesOn = true;
                break;
            case 'O':
                summaryBranchesOn = true;
                break;
            default:
                throw new BadParameterValueException(
                        "Invalid branch type: " + param.charAt(i));
            }
        }
    }
    
    public void reset() {
        this.ifBranchesOn = false;
        this.switchBranchesOn = false;
        this.throwsBranchesOn = false;
        this.callBranchesOn = false;
        this.entryBranchesOn = false;
        this.summaryBranchesOn = false;
        this.ready = false;
    }

    public boolean isReady() {
        return ready;
    }

    public void release() {
    }

    /*************************************************************************
     * Reports whether <code>if</code> branches are selected.
     *
     * @return <code>true</code> if <code>if</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areIfBranchesActive() {
        return ifBranchesOn;
    }
    
    /*************************************************************************
     * Sets whether <code>if</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>if</code> branches,
     * <code>false</code> to ignore.
     */
    public void setIfBranchesActive(boolean enable) {
        this.ifBranchesOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether <code>switch</code> branches are selected.
     *
     * @return <code>true</code> if <code>switch</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areSwitchBranchesActive() {
        return switchBranchesOn;
    }

    /*************************************************************************
     * Sets whether <code>switch</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>switch</code> branches,
     * <code>false</code> to ignore.
     */
    public void setSwitchBranchesActive(boolean enable) {
        this.switchBranchesOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether <code>throws</code> branches are selected.
     *
     * @return <code>true</code> if <code>throws</code> branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areThrowsBranchesActive() {
        return throwsBranchesOn;
    }

    /*************************************************************************
     * Sets whether <code>throws</code> branches are selected.
     *
     * @param enable <code>true</code> to select <code>throws</code> branches,
     * <code>false</code> to ignore.
     */
    public void setThrowsBranchesActive(boolean enable) {
        this.throwsBranchesOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether call branches are selected.
     *
     * @return <code>true</code> if call branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCallBranchesActive() {
        return callBranchesOn;
    }

    /*************************************************************************
     * Sets whether call branches are selected.
     *
     * @param enable <code>true</code> to select call branches,
     * <code>false</code> to ignore.
     */
    public void setCallBranchesActive(boolean enable) {
        this.callBranchesOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether entry branches are selected.
     *
     * @return <code>true</code> if entry branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areEntryBranchesActive() {
        return entryBranchesOn;
    }

    /*************************************************************************
     * Sets whether entry branches are selected.
     *
     * @param enable <code>true</code> to select entry branches,
     * <code>false</code> to ignore.
     */
    public void setEntryBranchesActive(boolean enable) {
        this.entryBranchesOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether summary branches are selected.
     *
     * @return <code>true</code> if summary branches are selected,
     * <code>false</code> otherwise.
     */
    public boolean areSummaryBranchesActive() {
        return summaryBranchesOn;
    }

    /*************************************************************************
     * Sets whether summary branches are selected.
     *
     * @param enable <code>true</code> to select summary branches,
     * <code>false</code> to ignore.
     */
    public void setSummaryBranchesActive(boolean enable) {
        this.summaryBranchesOn = enable;
        ready = true;
    }

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
    public int getTypeFlags() {
        int typeFlags = 0x00000000;
        if (ifBranchesOn) typeFlags      |= BranchType.MASK_IF;
        if (switchBranchesOn) typeFlags  |= BranchType.MASK_SWITCH;
        if (throwsBranchesOn) typeFlags  |= BranchType.MASK_THROW;
        if (callBranchesOn) typeFlags    |= BranchType.MASK_CALL;
        if (entryBranchesOn) typeFlags   |= BranchType.MASK_ENTRY;
        if (summaryBranchesOn) typeFlags |= BranchType.MASK_OTHER;
        return typeFlags;
    }
}
