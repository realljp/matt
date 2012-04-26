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

import sofya.base.SConstants;
import sofya.base.SConstants.*;
import sofya.ed.BranchEventListener;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BranchInstrumentationStrategy;
import sofya.ed.structural.BranchInstrumentationConfiguration;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import sofya.ed.structural.EventDispatcherConfiguration;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;

/**
 * <p>Processing strategy to receive JUnit branch sequence probes and
 * dispatch branch sequence events.</p>
 *
 * @author Alex Kinneer
 * @version 11/20/2006
 */
public class JUnitBranchSequenceProcessingStrategy
        extends AbstractJUnitProcessingStrategy
        implements BranchInstrumentationStrategy {
    /** Configuration specifying selected branches. */
    private BranchInstrumentationConfiguration branchConfig =
            new BranchInstrumentationConfiguration();

    // Local copies for efficiency
    private boolean ifBranchesOn;
    private boolean switchBranchesOn;
    private boolean throwsBranchesOn;
    private boolean callBranchesOn;
    private boolean entryBranchesOn;
    private boolean summaryBranchesOn;

    /** Registered event listeners. An array is used because events are
        dispatched to all listeners, and this tool will normally observe
        a **lot** of events. In any case, we don't expect listeners to
        be added or removed mid-run in normal use, so the overhead associated
        with managing the array manually is considered to be mitigated. */
    private BranchEventListener[] listeners = new BranchEventListener[4];
    /** Number of listeners currently registered. */
    private int listenerCount = 0;

    /** Conditional compilation debug flag. */
    @SuppressWarnings("unused")
    private static final boolean DEBUG = false;

    /**
     * Creates a new instance of the processing strategy.
     */
    public JUnitBranchSequenceProcessingStrategy() {
        super();
    }

    /**
     * Registers a listener for observable events.
     *
     * @param listener Observer that wishes to receive branch events from
     * the event dispatcher.
     */
    public void addEventListener(BranchEventListener listener) {
        if (listenerCount == listeners.length) {
            BranchEventListener[] temp =
                    new BranchEventListener[listeners.length + 4];
            System.arraycopy(listeners, 0, temp, 0, listeners.length);
            listeners = temp;
        }
        listeners[listenerCount++] = listener;
    }

    /**
     * Unregisters a listener for observable events.
     *
     * @param listener Object that no longer wishes to receive branch
     * events from the event dispatcher.
     */
    public void removeEventListener(BranchEventListener listener) {
        listenerCount -= 1;
        if (listeners[listenerCount] == listener) {
            return;
        }

        for (int i = listenerCount - 1; i >= 0; i--) {
            if (listeners[listenerCount] == listener) {
                System.arraycopy(listeners, i + 1, listeners, i,
                                 listeners.length - 1 - i);
                return;
            }
        }
    }

    public void register(EventDispatcherConfiguration edConfig) {
        super.register(edConfig);

        branchConfig.register(edConfig);

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).register(edConfig);
            }
        }
    }

    public List<String> configure(List<String> params) {
        params = super.configure(params);

        params = branchConfig.configure(params);

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                params = ((ActiveComponent) listeners[n]).configure(params);
            }
        }

        return params;
    }

    public boolean isReady() {
        if (!branchConfig.isReady()) return false;

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                if (!((ActiveComponent) listeners[n]).isReady()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void release() {
        super.release();

        branchConfig.release();

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).release();
            }
        }
    }

    public TraceObjectType getObjectType() {
        return TraceObjectType.BRANCH_EDGE;
    }

    public void initialize() {
        ifBranchesOn = branchConfig.areIfBranchesActive();
        switchBranchesOn = branchConfig.areSwitchBranchesActive();
        throwsBranchesOn = branchConfig.areThrowsBranchesActive();
        callBranchesOn = branchConfig.areCallBranchesActive();
        entryBranchesOn = branchConfig.areEntryBranchesActive();
        summaryBranchesOn = branchConfig.areSummaryBranchesActive();

        for (int n = 0; n < listenerCount; n++) {
            listeners[n].initialize();
        }
    }
    
    @Override
    protected void setup(int instMode) {
        checkInstrumentationMode(instMode);

        this.instMode = instMode;

        // Initialize data structures
        switch (instMode) {
        case SConstants.INST_COMPATIBLE:
            break;
        case SConstants.INST_OPT_NORMAL:
            throw new IllegalArgumentException("Sequence traces cannot " +
                "be collected from coverage instrumentation");
        case SConstants.INST_OPT_SEQUENCE:
            sequenceArray = new int[SEQUENCE_ARRAY_SIZE];
            indexToNameMap = new TIntObjectHashMap();
            nameToIndexMap = new TObjectIntHashMap();
            break;
        default:
            throw new IllegalArgumentException("Subject contains " +
                "unrecognized type of instrumentation");
        }
    }

    public void newTest(int testNum) {
        for (int n = 0; n < listenerCount; n++) {
            listeners[n].newEventStream(testNum);
        }
    }

    public void endTest(int testNum) {
        // Ensure all pending trace data is captured
        switch (instMode) {
        case SConstants.INST_COMPATIBLE:
            break;
        case SConstants.INST_OPT_NORMAL:
            throw new ExecException("Subject contains optimized coverage " +
                "instrumentation");
        case SConstants.INST_OPT_SEQUENCE:
            writeSequenceData();
            break;
        case -1:
            // No instrumentation type detected yet (uninstrumented?)
            break;
        default:
            throw new IllegalStateException("Unrecognized type of " +
                "instrumentation: " + instMode);
        }

        for (int n = 0; n < listenerCount; n++) {
            listeners[n].commitEventStream(testNum);
        }
        
        isInstrumented = false;
    }
    
    public boolean checkInstrumented() {
        return isInstrumented;
    }

    public void writeObjectCount(String mSignature, int objCount) {
        if (instMode != SConstants.INST_COMPATIBLE) {
            if (instMode == -1) {
                setup(SConstants.INST_COMPATIBLE);
            }
            else {
                throw new IllegalStateException("Subject contains " +
                    "inconsistent instrumentation");
            }
        }
        
        isInstrumented = true;
        
        // Not needed for anything else
    }

    public void writeTraceMessage(int objData, String mSignature) {
        try {  // Trap exceptions
            
            int edgeType = objData >>> 26;
            int branchId = objData & 0x03FFFFFF;

            switch (edgeType) {
            case BranchType.IIF:
                if (ifBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].ifBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            case BranchType.ISWITCH:
                if (switchBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].switchBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            case BranchType.ITHROW:
                if (throwsBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].throwBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            case BranchType.ICALL:
                if (callBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].callBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            case BranchType.IENTRY:
                if (entryBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].entryBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            case BranchType.IOTHER:
                if (summaryBranchesOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].otherBranchExecuteEvent(
                            mSignature, branchId);
                    }
                }
                break;
            default:
                throw new ExecException("Invalid branch type code received " +
                    "from instrumented\nclass: " + edgeType);
            }
        }
        catch (Exception e) {
            // Catch all unexpected exceptions and store them
            e.printStackTrace();
            err = e;
            System.err.println("Error writing trace message");
        }
    }

    public void writeSequenceData() {
        try {  // Trap exceptions
            
            for (int i = 0; i < sequenceIndex; i++) {
                int traceInt = sequenceArray[i];
                
                switch (traceInt) {
                case NEW_METHOD_MARKER:
                    methodStack.add(activeMethod);
                    activeMethod = (String) indexToNameMap.get(
                        sequenceArray[++i]);
                    break;
                case BRANCH_EXIT_MARKER:
                    activeMethod = methodStack.remove(
                            methodStack.size() - 1);
                    break;
                default:
                    int edgeType = traceInt >>> 26;
                    int branchId = traceInt & 0x03FFFFFF;
   
                    switch (edgeType) {
                    case BranchType.IIF:
                        if (ifBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].ifBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    case BranchType.ISWITCH:
                        if (switchBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].switchBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    case BranchType.ITHROW:
                        if (throwsBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].throwBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    case BranchType.ICALL:
                        if (callBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].callBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    case BranchType.IENTRY:
                        if (entryBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].entryBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    case BranchType.IOTHER:
                        if (summaryBranchesOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].otherBranchExecuteEvent(
                                    activeMethod, branchId);
                            }
                        }
                        break;
                    default:
                        throw new ExecException("Invalid branch type code " +
                            "received from instrumented\nclass: " + edgeType);
                    }
                }
            }
            
            sequenceIndex = 0;
        }
        catch (Exception e) {
            // Catch all unexpected exceptions and store them
            e.printStackTrace();
            err = e;
            System.err.println("Error writing trace message");
        }
    }

    public byte[] getObjectArray(String mSignature, int objCount) {
        throw new ExecException("Subject contains optimized coverage " +
            "instrumentation");
    }

    public boolean areIfBranchesActive() {
        return branchConfig.areIfBranchesActive();
    }

    public void setIfBranchesActive(boolean enable) {
        branchConfig.setIfBranchesActive(enable);
    }

    public boolean areSwitchBranchesActive() {
        return branchConfig.areSwitchBranchesActive();
    }

    public void setSwitchBranchesActive(boolean enable) {
        branchConfig.setSwitchBranchesActive(enable);
    }

    public boolean areThrowsBranchesActive() {
        return branchConfig.areThrowsBranchesActive();
    }

    public void setThrowsBranchesActive(boolean enable) {
        branchConfig.setThrowsBranchesActive(enable);
    }

    public boolean areCallBranchesActive() {
        return branchConfig.areCallBranchesActive();
    }

    public void setCallBranchesActive(boolean enable) {
        branchConfig.setCallBranchesActive(enable);
    }

    public boolean areEntryBranchesActive() {
        return branchConfig.areEntryBranchesActive();
    }

    public void setEntryBranchesActive(boolean enable) {
        branchConfig.setEntryBranchesActive(enable);
    }

    public boolean areSummaryBranchesActive() {
        return branchConfig.areSummaryBranchesActive();
    }

    public void setSummaryBranchesActive(boolean enable) {
        branchConfig.setSummaryBranchesActive(enable);
    }

    public int getTypeFlags() {
        return branchConfig.getTypeFlags();
    }
}
