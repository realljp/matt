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

import sofya.base.SConstants.BlockType;
import sofya.ed.BadParameterValueException;

import gnu.trove.TIntHashSet;

/**
 * <p>A basic block instrumentation configuration stores the types of basic
 * blocks that have been selected for instrumentation or observation by an
 * event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public class BlockInstrumentationConfiguration implements ActiveComponent {
    /** Flag to activate general basic blocks (all real code blocks except for
        call blocks). */
    private boolean codeBlocksOn;
    /** Flag to activate entry blocks. */
    private boolean entryBlocksOn;
    /** Flag to activate exit blocks. */
    private boolean exitBlocksOn;
    /** Flag to activate call blocks. */
    private boolean callBlocksOn;
    /** Flag to activate return blocks. */
    private boolean returnBlocksOn;

    /** Flag indicating whether the configuration is considered in a ready
        state. */
    private boolean ready;

    /** Set of characters used to heuristically guess the command line
        parameter specifying the basic block types to activate. */
    private static TIntHashSet matchChars = new TIntHashSet();

    static {
        matchChars.add('B');
        matchChars.add('E');
        matchChars.add('X');
        matchChars.add('C');
        matchChars.add('R');
    }

    /**
     * Creates a new basic block configuration.
     *
     * <p>No basic blocks are activated by default.</p>
     */
    public BlockInstrumentationConfiguration() {
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

            if (param.startsWith("-") && (length <= 6)) {
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
                throw new IllegalArgumentException("Block types not specified");
            }
        }

        this.ready = true;

        return params;
    }

    /**
     * Parses basic block types from a command line parameter.
     *
     * @param param The command line parameter to attempt to parse.
     */
    private void parseParameter(String param) {
        int length = param.length();
        for (int i = 1; i < length; i++) {
            switch(param.charAt(i)) {
            case 'B':
                codeBlocksOn = true;
                break;
            case 'E':
                entryBlocksOn = true;
                break;
            case 'X':
                exitBlocksOn = true;
                break;
            case 'C':
                callBlocksOn = true;
                break;
            case 'R':
                returnBlocksOn = true;
                break;
            default:
                throw new BadParameterValueException(
                        "Invalid basic block type: " + param.charAt(i));
            }
        }
    }
    
    public void reset() {
        this.codeBlocksOn = false;
        this.entryBlocksOn = false;
        this.exitBlocksOn = false;
        this.callBlocksOn = false;
        this.returnBlocksOn = false;
        this.ready = false;
    }

    public boolean isReady() {
        return ready;
    }

    public void release() {
    }

    /*************************************************************************
     * Reports whether general basic blocks are selected; general basic
     * blocks are any basic blocks corresponding to actual program code other
     * than method call blocks.
     *
     * @return <code>true</code> if general basic blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCodeBlocksActive() {
        return codeBlocksOn;
    }

    /*************************************************************************
     * Sets whether general basic blocks are selected; general basic
     * blocks are any basic blocks corresponding to actual program code other
     * than method call blocks.
     *
     * @param enable <code>true</code> to select general basic blocks,
     * <code>false</code> to ignore.
     */
    public void setCodeBlocksActive(boolean enable) {
        codeBlocksOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether entry blocks are selected.
     *
     * @return <code>true</code> if entry blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areEntryBlocksActive() {
        return entryBlocksOn;
    }

    /*************************************************************************
     * Sets whether entry blocks are selected.
     *
     * @param enable <code>true</code> to select entry blocks,
     * <code>false</code> to ignore.
     */
    public void setEntryBlocksActive(boolean enable) {
        entryBlocksOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether exit blocks are selected.
     *
     * @return <code>true</code> if exit blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areExitBlocksActive() {
        return exitBlocksOn;
    }

    /*************************************************************************
     * Sets whether exit blocks are selected.
     *
     * @param enable <code>true</code> to select exit blocks,
     * <code>false</code> to ignore.
     */
    public void setExitBlocksActive(boolean enable) {
        exitBlocksOn = enable;
        ready = true;
    }

    /*************************************************************************
     * Reports whether call blocks are selected.
     *
     * @return <code>true</code> if call blocks are selected,
     * <code>false</code> otherwise.
     */
    public boolean areCallBlocksActive() {
        return callBlocksOn;
    }

    /*************************************************************************
     * Sets whether call blocks are selected.
     *
     * @param enable <code>true</code> to select call blocks,
     * <code>false</code> to ignore.
     */
    public void setCallBlocksActive(boolean enable) {
        callBlocksOn = enable;
        ready = true;
    }

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
    public int getTypeFlags() {
        int typeFlags = 0x00000000;
        if (codeBlocksOn) typeFlags   |= BlockType.MASK_BASIC;
        if (entryBlocksOn) typeFlags  |= BlockType.MASK_ENTRY;
        if (exitBlocksOn) typeFlags   |= BlockType.MASK_EXIT;
        if (callBlocksOn) typeFlags   |= BlockType.MASK_CALL;
        if (returnBlocksOn) typeFlags |= BlockType.MASK_RETURN;
        return typeFlags;
    }
}
