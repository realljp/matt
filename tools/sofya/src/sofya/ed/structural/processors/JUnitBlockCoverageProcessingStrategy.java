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
import sofya.ed.BlockCoverageListener;
import sofya.ed.CoverageListenerManager;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BlockInstrumentationStrategy;
import sofya.ed.structural.BlockInstrumentationConfiguration;
import sofya.ed.structural.EventDispatcherConfiguration;
import sofya.ed.structural.TraceHandler;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.OrderedMap;

/**
 * <p>Processing strategy to receive JUnit basic block coverage probes and
 * dispatch basic block coverage events.</p>
 *
 * @author Alex Kinneer
 * @version 11/17/2006
 */
public class JUnitBlockCoverageProcessingStrategy
        extends AbstractJUnitCoverageProcessingStrategy
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

    /**
     * Creates a new instance of the processing strategy with a trace
     * handler as the default coverage listener manager.
     */
    public JUnitBlockCoverageProcessingStrategy() {
        super();
        setCoverageListenerManager(new TraceHandler());
    }

    /**
     * Creates a new instance of the processing strategy.
     *
     * @param clm Coverage listener manager to be used to retrieve coverage
     * listeners to which events will be dispatched.
     */
    public JUnitBlockCoverageProcessingStrategy(CoverageListenerManager clm) {
        super();
        setCoverageListenerManager(clm);
    }

    public void register(EventDispatcherConfiguration edConfig) {
        super.register(edConfig);

        blockConfig.register(edConfig);

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).register(edConfig);
        }
    }

    public List<String> configure(List<String> params) {
        params = super.configure(params);

        params = blockConfig.configure(params);

        if (listenerManager instanceof ActiveComponent) {
            return ((ActiveComponent) listenerManager).configure(params);
        }
        else {
            return params;
        }
    }

    public boolean isReady() {
        boolean ready = blockConfig.isReady();

        if (listenerManager == null) {
            return ready;
        }
        else {
            if (listenerManager instanceof ActiveComponent) {
                return ((ActiveComponent) listenerManager).isReady() && ready;
            }
            else {
                return ready;
            }
        }
    }

    public void release() {
        super.release();

        blockConfig.release();

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).release();
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

        listenerManager.initialize();
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

        listenerManager.initializeBlockListener(mSignature, objCount);
    }

    public void writeTraceMessage(int objData, String mSignature) {
        try {  // Trap exceptions
    
            BlockCoverageListener listener =
                listenerManager.getBlockCoverageListener(mSignature);
            
            int nodeType = objData >>> 26;
            int blockId = objData & 0x03FFFFFF;
    
            switch (nodeType) {
            case BlockType.IBLOCK:
                if (codeBlocksOn) {
                    listener.blockCovered(blockId, nodeType);
                }
                break;
            case BlockType.IENTRY:
                if (entryBlocksOn) {
                    listener.blockCovered(blockId, nodeType);
                }
                break;
            case BlockType.IEXIT:
                if (exitBlocksOn) {
                    listener.blockCovered(blockId, nodeType);
                }
                break;
            case BlockType.ICALL:
                if (callBlocksOn) {
                    listener.blockCovered(blockId, nodeType);
                }
                break;
            case BlockType.IRETURN:
                if (returnBlocksOn) {
                    listener.blockCovered(blockId, nodeType);
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
    
    @Override
    protected void processCoverageData(OrderedMap covArrays) {
        try {  // Trap exceptions
            int arrCount = covArrays.size();
            MapIterator iter = covArrays.orderedMapIterator();
            for (int i = arrCount; i-- > 0; ) {
                String mSignature = (String) iter.next();
                byte[] covArray = (byte[]) iter.getValue();
                int objCount = covArray.length;
                
                listenerManager.initializeBlockListener(
                    mSignature, objCount);
                BlockCoverageListener listener =
                    listenerManager.getBlockCoverageListener(mSignature);
                
                for (int blkId = 1; blkId <= objCount; blkId++) {
                    int nodeType = covArray[blkId - 1];
    
                    switch (nodeType) {
                    case BlockType.IBLOCK:
                        if (codeBlocksOn) {
                            listener.blockCovered(blkId, nodeType);
                        }
                        break;
                    case BlockType.IENTRY:
                        if (entryBlocksOn) {
                            listener.blockCovered(blkId, nodeType);
                        }
                        break;
                    case BlockType.IEXIT:
                        if (exitBlocksOn) {
                            listener.blockCovered(blkId, nodeType);
                        }
                        break;
                    case BlockType.ICALL:
                        if (callBlocksOn) {
                            listener.blockCovered(blkId, nodeType);
                        }
                        break;
                    case BlockType.IRETURN:
                        if (returnBlocksOn) {
                            listener.blockCovered(blkId, nodeType);
                        }
                        break;
                    case 0:
                        // Not witnessed
                        break;
                    default:
                        throw new ExecException("Invalid block type code " +
                            "received from instrumented class: " +
                            nodeType);
                    }
                }
                
                iter.remove();
            }
        }
        catch (Exception e) {
            // Catch all unexpected exceptions and store them
            e.printStackTrace();
            err = e;
            System.err.println("Error writing trace message");
        }
    }
    
    public void markMethodInSequence(String mSignature, int objCount) {
        listenerManager.initializeBlockListener(mSignature, objCount);
        super.markMethodInSequence(mSignature, objCount);
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

                    BlockCoverageListener listener =
                        listenerManager.getBlockCoverageListener(activeMethod);
    
                    switch (nodeType) {
                    case BlockType.IBLOCK:
                        if (codeBlocksOn) {
                            listener.blockCovered(blockId, nodeType);
                        }
                        break;
                    case BlockType.IENTRY:
                        if (entryBlocksOn) {
                            listener.blockCovered(blockId, nodeType);
                        }
                        break;
                    case BlockType.IEXIT:
                        if (exitBlocksOn) {
                            listener.blockCovered(blockId, nodeType);
                        }
                        activeMethod = methodStack.remove(
                                methodStack.size() - 1);
                        break;
                    case BlockType.ICALL:
                        if (callBlocksOn) {
                            listener.blockCovered(blockId, nodeType);
                        }
                        break;
                    case BlockType.IRETURN:
                        if (returnBlocksOn) {
                            listener.blockCovered(blockId, nodeType);
                        }
                        break;
                    default:
                        throw new ExecException("Invalid block type " +
                            "code received from instrumented\nclass: " +
                            nodeType);
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
