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
import sofya.ed.BlockEventListener;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.EventDispatcherConfiguration;
import sofya.ed.structural.BlockInstrumentationStrategy;
import sofya.ed.structural.BlockInstrumentationConfiguration;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;

/**
 * <p>Processing strategy to receive JUnit basic block sequence probes and
 * dispatch basic block sequence events.</p>
 *
 * @author Alex Kinneer
 * @version 11/20/2006
 */
public class JUnitBlockSequenceProcessingStrategy
        extends AbstractJUnitProcessingStrategy
        implements BlockInstrumentationStrategy {
    /** Configuration specifying selected basic blocks. */
    private BlockInstrumentationConfiguration blockConfig =
            new BlockInstrumentationConfiguration();

    // Local copies for efficiency
    private boolean codeBlocksOn;
    private boolean entryBlocksOn;
    private boolean exitBlocksOn;
    private boolean callBlocksOn;
    private boolean returnBlocksOn;

    /** Registered event listeners. An array is used because events are
        dispatched to all listeners, and this tool will normally observe
        a **lot** of events. In any case, we don't expect listeners to
        be added or removed mid-run in normal use, so the overhead associated
        with managing the array manually is considered to be mitigated. */
    private BlockEventListener[] listeners = new BlockEventListener[4];
    /** Number of listeners currently registered. */
    private int listenerCount = 0;

    /**
     * Creates a new instance of the processing strategy.
     */
    public JUnitBlockSequenceProcessingStrategy() {
        super();
    }

    /**
     * Registers a listener for observable events.
     *
     * @param listener Observer that wishes to receive basic block events from
     * the event dispatcher.
     */
    public void addEventListener(BlockEventListener listener) {
        if (listenerCount == listeners.length) {
            BlockEventListener[] temp =
                    new BlockEventListener[listeners.length + 4];
            System.arraycopy(listeners, 0, temp, 0, listeners.length);
            listeners = temp;
        }
        listeners[listenerCount++] = listener;
    }

    /**
     * Unregisters a listener for observable events.
     *
     * @param listener Object that no longer wishes to receive basic block
     * events from the event dispatcher.
     */
    public void removeEventListener(BlockEventListener listener) {
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

        blockConfig.register(edConfig);

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).register(edConfig);
            }
        }
    }

    public List<String> configure(List<String> params) {
        params = super.configure(params);

        params = blockConfig.configure(params);

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                params = ((ActiveComponent) listeners[n]).configure(params);
            }
        }

        return params;
    }

    public boolean isReady() {
        if (!blockConfig.isReady()) return false;

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

        blockConfig.release();

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).release();
            }
        }
    }

    public TraceObjectType getObjectType() {
        return TraceObjectType.BASIC_BLOCK;
    }

    public void initialize() {
        codeBlocksOn = blockConfig.areCodeBlocksActive();
        entryBlocksOn = blockConfig.areEntryBlocksActive();
        exitBlocksOn = blockConfig.areExitBlocksActive();
        callBlocksOn = blockConfig.areCallBlocksActive();
        returnBlocksOn = false;

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
            
            int nodeType = objData >>> 26;
            int blockId = objData & 0x03FFFFFF;
    
            switch (nodeType) {
            case BlockType.IBLOCK:
                if (codeBlocksOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].codeBlockExecuteEvent(
                            mSignature, blockId);
                    }
                }
                break;
            case BlockType.IENTRY:
                if (entryBlocksOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].entryBlockExecuteEvent(
                            mSignature, blockId);
                    }
                }
                break;
            case BlockType.IEXIT:
                if (exitBlocksOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].exitBlockExecuteEvent(
                            mSignature, blockId);
                    }
                }
                break;
            case BlockType.ICALL:
                if (callBlocksOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].callBlockExecuteEvent(
                            mSignature, blockId);
                    }
                }
                break;
            case BlockType.IRETURN:
                if (returnBlocksOn) {
                    for (int n = 0; n < listenerCount; n++) {
                        listeners[n].returnBlockExecuteEvent(
                            mSignature, blockId);
                    }
                }
                break;
            default:
                throw new ExecException("Invalid block type code " +
                    "received from instrumented class: " + nodeType);
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
                    throw new IllegalStateException("Branch instrumentation " +
                        "encountered where block instrumentation " +
                        "was expected");
                default:
                    int nodeType = traceInt >>> 26;
                    int blockId = traceInt & 0x03FFFFFF;
    
                    switch (nodeType) {
                    case BlockType.IBLOCK:
                        if (codeBlocksOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].codeBlockExecuteEvent(
                                    activeMethod, blockId);
                            }
                        }
                        break;
                    case BlockType.IENTRY:
                        if (entryBlocksOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].entryBlockExecuteEvent(
                                    activeMethod, blockId);
                            }
                        }
                        break;
                    case BlockType.IEXIT:
                        if (exitBlocksOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].exitBlockExecuteEvent(
                                    activeMethod, blockId);
                            }
                        }
                        activeMethod = methodStack.remove(
                                methodStack.size() - 1);
                        break;
                    case BlockType.ICALL:
                        if (callBlocksOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].callBlockExecuteEvent(
                                    activeMethod, blockId);
                            }
                        }
                        break;
                    case BlockType.IRETURN:
                        if (returnBlocksOn) {
                            for (int n = 0; n < listenerCount; n++) {
                                listeners[n].returnBlockExecuteEvent(
                                    activeMethod, blockId);
                            }
                        }
                        break;
                    default:
                        throw new ExecException("Invalid block type code " +
                            "received from instrumented class: " + nodeType);
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

    public boolean areCodeBlocksActive() {
        return blockConfig.areCodeBlocksActive();
    }

    public void setCodeBlocksActive(boolean enable) {
        blockConfig.setCodeBlocksActive(enable);
    }

    public boolean areEntryBlocksActive() {
        return blockConfig.areEntryBlocksActive();
    }

    public void setEntryBlocksActive(boolean enable) {
        blockConfig.setEntryBlocksActive(enable);
    }

    public boolean areExitBlocksActive() {
        return blockConfig.areExitBlocksActive();
    }

    public void setExitBlocksActive(boolean enable) {
        blockConfig.setExitBlocksActive(enable);
    }

    public boolean areCallBlocksActive() {
        return blockConfig.areCallBlocksActive();
    }

    public void setCallBlocksActive(boolean enable) {
        blockConfig.setCallBlocksActive(enable);
    }

    public int getTypeFlags() {
        return blockConfig.getTypeFlags();
    }
}
