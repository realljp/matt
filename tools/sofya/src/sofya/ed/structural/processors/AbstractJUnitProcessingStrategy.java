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

package sofya.ed.structural.processors;

import java.util.List;
import java.util.ArrayList;

import sofya.base.SConstants;
import sofya.ed.structural.JUnitProcessingStrategy;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;

/**
 * <p>Base class for strategies that receive and process probes for a
 * JUnit event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 11/21/2006
 */
public abstract class AbstractJUnitProcessingStrategy
        extends AbstractProcessingStrategy
        implements JUnitProcessingStrategy {
    /** Conditional compilation flag to control whether asserts are
        present in the class file. */
    private static final boolean ASSERTS = true;
    
    /** Maps indices following new method markers to the signature
        string for that method. */
    protected TIntObjectHashMap indexToNameMap;
    /** Maps signature strings for a method to an already assigned
        index, if any. */
    protected TObjectIntHashMap nameToIndexMap;
    /** Holds the next value available for use as an index to a
        method signature string. */
    protected int nextMethodIndex = 0;
    
    /** Current method stack, used during processing of the sequence
        array for sequence instrumentation. */
    protected List<String> methodStack = new ArrayList<String>(30);
    /** The name of the method currently at the top of the method stack,
        used during processing of the sequence array for sequence
        instrumentation. */
    protected String activeMethod = "#BOOTSTRAP#";
    
    /** Flag indicating whether any instrumentation was detected for
        the current test case. */
    protected boolean isInstrumented = false;
    
    /** Flag which indicates if an instrumentation error (wrong mode of
        instrumentation) has been detected. It is used to suppress repeat
        messages. */
    protected boolean instError = false;

    /** Stores the exception that stopped probe processing, if any. */
    protected Exception err;

    protected AbstractJUnitProcessingStrategy() {
        super();
    }

    public List<String> configure(List<String> parameters) {
        return parameters;
    }

    public void reset() {
        throw new IllegalStateException("Once configured, a JUnit event " +
            "dispatcher cannot be reset");
    }

    public boolean isReady() {
        return true;
    }

    public void release() {
        instMode = -1;
        err = null;
    }

    public void checkError() throws Exception {
        if (err != null) throw err;
    }
    
    public int getInstrumentationMode() {
        return instMode;
    }
    
    protected void checkInstrumentationMode(int instMode)
            throws IllegalStateException {
        if ((this.instMode != -1) && (this.instMode != instMode)) {
            throw new IllegalStateException("Subject contains " +
                "inconsistent instrumentation");
        }
    }
    
    /*************************************************************************
     * Initializes the data structures necessary to process the
     * instrumentation detected in the subject. All invocations subsequent
     * to the first must specify the same form of instrumentation, or the
     * method will fail. In practice it should only be called once.
     *
     * @param instMode Type of instrumentation detected in the subject.
     *
     * @throws IllegalStateException If this method has previously been called
     * with a different mode of instrumentation.
     */
    protected abstract void setup(int instMode);
    
    /*************************************************************************
     * Checks that the type of instrumentation can be handled by the
     * this processing strategy.
     *
     * <p><strong>Note: It is not necessary for a subject to call this
     * method for the event dispatcher to work</strong> (assuming the
     * instrumentation is appropriate). This method is merely provided
     * to reduce special-casing in the instrumentor.</p>
     *
     * @param port <i>Ignored.</i>
     * @param instMode Flag indicating the type of instrumentation
     * present in the subject; All types are supported.
     * @param doTimestamps <i>Ignored.</i>
     * @param useSignalSocket <i>Ignored.</i>
     * @param entityType <i>Ignored</i>
     */
    public void start(int port, int instMode, boolean doTimestamps,
                      boolean useSignalSocket, int entityType) {
        if ((instMode < 1) || (instMode > 4)) {
            if (!instError) {
                System.err.println("Cannot process type of instrumentation " +
                    "present in subject!");
                instError = true;
            }
            return;
        }
    }
    
    public void finish() {
        // We want to ignore calls to this method. They are spurious
        // in this context because the lifecycle of the trace data
        // is determined by the execution of test cases within the
        // JUnit framework. The event dispatcher framework will
        // guarantee the proper capture of all trace data.
    }

    // This method is common to all JUnit processing strategies, since
    // they can all handle optimized sequence instrumentation
    public void markMethodInSequence(String mSignature, int objCount) {
        if (instMode != SConstants.INST_OPT_SEQUENCE) {
            if (instMode == -1) {
                setup(SConstants.INST_OPT_SEQUENCE);
            }
            else {
                throw new IllegalStateException("Subject contains " +
                    "inconsistent instrumentation");
            }
        }
        
        isInstrumented = true;

        if (sequenceIndex > sequenceArray.length - 2) {
            // The array is full, so transmit the data. This also resets the
            // index..
            writeSequenceData();
        }
        sequenceArray[sequenceIndex++] = NEW_METHOD_MARKER;
        if (nameToIndexMap.containsKey(mSignature)) {
            // We've already linked this method to an index, so use that value
            sequenceArray[sequenceIndex++] = nameToIndexMap.get(mSignature);
        }
        else {
            // Need to create a new index to correspond to this method
            sequenceArray[sequenceIndex++] = nextMethodIndex;

            nameToIndexMap.put(mSignature, nextMethodIndex);
            if (!indexToNameMap.containsKey(nextMethodIndex)) {
                indexToNameMap.put(nextMethodIndex, mSignature);
            }
            // We will not attempt to detect overflow -- what is the
            // likelihood of encountering a program with over 4
            // billion methods?
            nextMethodIndex++;
            
            if (ASSERTS) {
                // Overflow check, for debugging. (we would reach zero
                // after rolling over (2^31 - 1) and back through the
                // negative numbers)
                assert nextMethodIndex != 0;
            }
        }
    }
}
