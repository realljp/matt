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

import gnu.trove.TLinkedList;
import gnu.trove.TIntHashSet;

/**
 * Structure that records the changes made to a method by the
 * {@link SemanticInstrumentor}.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
final class MethodLog {
    /** JNI signature string of the method for which this log
        records changes. */
    public final String methodSig;
    /** Directly-linked list of the bytecode changes made to the method. */
    public final TLinkedList bytecodeLog;
    /** Directly-linked list of the changes made to the exception handlers
        attached to the method. */
    public final TLinkedList handlerLog;
    /** Records bytecode offsets of handlers that should not be instrumented
        for catch events because they were added by the instrumentor. */
    public final TIntHashSet syntheticHandlers;
    /** Records the ID of the method exit probe; used to filter out methods
        already instrumented for the exit event during online instrumentation
        updates. */
    public int exitProbeId = -1;

    private MethodLog() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new method change log.
     * 
     * @param methodSig JNI signature string of the method for which the
     * log records changes.
     */
    MethodLog(String methodSig) {
        this.methodSig = methodSig;
        this.bytecodeLog = new TLinkedList();
        this.handlerLog = new TLinkedList();
        this.syntheticHandlers = new TIntHashSet();
    }
}
