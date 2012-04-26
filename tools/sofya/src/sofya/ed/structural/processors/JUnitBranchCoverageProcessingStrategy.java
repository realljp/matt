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

import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.OrderedMap;

import sofya.base.SConstants;
import sofya.base.SConstants.*;
import sofya.ed.BranchCoverageListener;
import sofya.ed.CoverageListenerManager;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BranchInstrumentationStrategy;
import sofya.ed.structural.BranchInstrumentationConfiguration;
import sofya.ed.structural.EventDispatcherConfiguration;
import sofya.ed.structural.TraceHandler;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

/**
 * <p>Processing strategy to receive JUnit branch coverage probes and
 * dispatch branch coverage events.</p>
 *
 * @author Alex Kinneer
 * @version 11/17/2006
 */
public class JUnitBranchCoverageProcessingStrategy
        extends AbstractJUnitCoverageProcessingStrategy
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

    /**
     * Creates a new instance of the processing strategy with a trace
     * handler as the default coverage listener manager.
     */
    public JUnitBranchCoverageProcessingStrategy() {
        super();
        setCoverageListenerManager(new TraceHandler());
    }

    /**
     * Creates a new instance of the processing strategy.
     *
     * @param clm Coverage listener manager to be used to retrieve coverage
     * listeners to which events will be dispatched.
     */
    public JUnitBranchCoverageProcessingStrategy(CoverageListenerManager clm) {
        super();
        setCoverageListenerManager(clm);
    }

    public void register(EventDispatcherConfiguration edConfig) {
        super.register(edConfig);

        branchConfig.register(edConfig);

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).register(edConfig);
        }
    }

    public List<String> configure(List<String> params) {
        params = super.configure(params);

        params = branchConfig.configure(params);

        if (listenerManager instanceof ActiveComponent) {
            return ((ActiveComponent) listenerManager).configure(params);
        }
        else {
            return params;
        }
    }

    public boolean isReady() {
        boolean ready = branchConfig.isReady();

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

        branchConfig.release();

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).release();
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

        listenerManager.initializeBranchListener(mSignature, objCount);
    }

    public void writeTraceMessage(int objData, String mSignature) {
        try {  // Trap exceptions
            
            BranchCoverageListener listener =
                listenerManager.getBranchCoverageListener(mSignature);
            
            int branchType = objData >>> 26;
            int branchId = objData & 0x03FFFFFF;
    
            switch (branchType) {
            case BranchType.IIF:
                if (ifBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            case BranchType.ISWITCH:
                if (switchBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            case BranchType.ITHROW:
                if (throwsBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            case BranchType.ICALL:
                if (callBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            case BranchType.IENTRY:
                if (entryBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            case BranchType.IOTHER:
                if (summaryBranchesOn) {
                    listener.branchCovered(branchId, branchType);
                }
                break;
            default:
                throw new ExecException("Invalid branch type code received " +
                    "from instrumented\nclass: " + branchType);
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
                
                listenerManager.initializeBranchListener(
                        mSignature, objCount);
                BranchCoverageListener listener =
                    listenerManager.getBranchCoverageListener(mSignature);
                
                for (int branchId = 1; branchId <= objCount; branchId++) {
                    int branchType = covArray[branchId - 1];

                    switch (branchType) {
                    case BranchType.IIF:
                        if (ifBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ISWITCH:
                        if (switchBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ITHROW:
                        if (throwsBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ICALL:
                        if (callBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.IENTRY:
                        if (entryBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.IOTHER:
                        if (summaryBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case 0:
                        // Not witnessed
                        break;
                    default:
                        throw new ExecException("Invalid branch type code " +
                            "received from instrumented\nclass: " +
                            branchType);
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
        listenerManager.initializeBranchListener(mSignature, objCount);
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
                    activeMethod = methodStack.remove(
                            methodStack.size() - 1);
                    break;
                default:
                    int branchType = traceInt >>> 26;
                    int branchId = traceInt & 0x03FFFFFF;

                    BranchCoverageListener listener =
                        listenerManager.getBranchCoverageListener(
                            activeMethod);

                    switch (branchType) {
                    case BranchType.IIF:
                        if (ifBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ISWITCH:
                        if (switchBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ITHROW:
                        if (throwsBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.ICALL:
                        if (callBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.IENTRY:
                        if (entryBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    case BranchType.IOTHER:
                        if (summaryBranchesOn) {
                            listener.branchCovered(branchId, branchType);
                        }
                        break;
                    default:
                        throw new ExecException("Invalid branch type code " +
                            "received from instrumented\nclass: " +
                            branchType);
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
