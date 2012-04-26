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

package org.apache.bcel.generic;

/**
 * Utility class used to obtain the position of an instruction handle,
 * regardless of the handle type.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
public final class SUtility {
    /**
     * Obtains the position of an instruction handle, insensitive to
     * the actual type of the instruction handle.
     * 
     * <p>This yields an invariant behavior for all instruction handles,
     * as the behavior of the <code>getPosition</code> method is
     * inconsistent for some subtypes of <code>InstructionHandle</code>,
     * notably <code>BranchHandle</code>. Such inconsistency violates
     * the expected contract when an instruction handle is being
     * viewed through the <code>InstructionHandle</code> interface.
     * In particular, the position returned by this method will not
     * actually reflect changes until
     * <code>InstructionList.setPositions</code> is called.</p>
     * 
     * @param ih Instruction handle for which to get the bytecode
     * offset.
     * 
     * @return The bytecode offset of the instruction after the last
     * call to <code>setPositions</code> on the containing instruction
     * list, <strong>not</strong> any intermediate or eagerly
     * computed offset.
     */
    public static final int getRealPosition(InstructionHandle ih) {
        return ih.i_position;
    }
}
