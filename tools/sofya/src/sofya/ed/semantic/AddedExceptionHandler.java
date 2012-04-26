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

import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.InstructionHandle;

import gnu.trove.TLinkable;

/**
 * Structure that records a change made by the {@link SemanticInstrumentor}
 * to the exception handlers attached to a method.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
@SuppressWarnings("serial") // Inherited under duress from TLinkable
final class AddedExceptionHandler implements TLinkable {
    /** ID of the logical probe with which the synthetic exception handler
        is associated. */
    public final int probeId;
    /** Bytecode offset to the instruction at the start of the protected
        region. */
    public int start_pc;
    /** Bytecode offset to the instruction after the last instruction in
        the protected region. */
    public int end_pc;
    /** Bytecode offset to the first instruction of the synthetic exception
        handler code. */
    public int handler_pc;
    /** Fully qualified name of the throwable type caught by the
        sythethic exception handler. */
    public final String catch_type;

    // Added exception handler records are stored in a directly linked
    // list for maximum efficiency
    private TLinkable prev;
    private TLinkable next;

    private AddedExceptionHandler() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new record for an added exception handler.
     * 
     * @param probeId ID of the logical probe with which the synthetic
     * exception handler is associated.
     * @param handler Synthetic exception handler added to the method.
     */
    AddedExceptionHandler(int probeId, CodeExceptionGen handler) {
        this.probeId = probeId;

        this.start_pc = handler.getStartPC().getPosition();
        InstructionHandle end_ih = handler.getEndPC();
        this.end_pc = end_ih.getPosition();
        this.handler_pc = handler.getHandlerPC().getPosition();

        ObjectType excType = handler.getCatchType();
        if (excType == null) {
            this.catch_type = "\0";
        }
        else {
            this.catch_type = excType.getClassName();
        }
    }

    /**
     * Creates a new record for an added exception handler.
     * 
     * <p>This constructor is intended for use during deserialization
     * of the class log.</p>
     * 
     * @param probeId ID of the logical probe with which the synthetic
     * exception handler is associated.
     * @param start_pc Bytecode offset to the instruction at the start
     * of the protected region.
     * @param end_pc Bytecode offset to the instruction after the last
     * instruction in the protected region.
     * @param handler_pc Bytecode offset to the first instruction of
     * the synthetic exception handler code.
     * @param catch_type Fully qualified name of the throwable type
     * caught by the sythethic exception handler.
     */
    AddedExceptionHandler(int probeId, int start_pc, int end_pc, int handler_pc,
            String catch_type) {
        this.probeId = probeId;
        this.start_pc = start_pc;
        this.end_pc = end_pc;
        this.handler_pc = handler_pc;
        this.catch_type = catch_type;
    }

    public TLinkable getNext() {
        return next;
    }

    public TLinkable getPrevious() {
        return prev;
    }

    public void setNext(TLinkable next) {
        this.next = next;
    }

    public void setPrevious(TLinkable prev) {
        this.prev = prev;
    }
}
