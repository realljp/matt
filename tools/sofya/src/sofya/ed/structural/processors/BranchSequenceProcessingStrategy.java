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

import java.net.Socket;
import java.net.SocketException;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Channels;

import sofya.base.SConstants;
import sofya.base.SConstants.*;
import sofya.ed.BranchEventListener;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BranchInstrumentationStrategy;
import sofya.ed.structural.BranchInstrumentationConfiguration;
import sofya.ed.structural.ControlData;
import sofya.ed.structural.EventDispatcherConfiguration;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SEQUENCE_ARRAY_SIZE;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.NEW_METHOD_MARKER;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.BRANCH_EXIT_MARKER;

import gnu.trove.TIntObjectHashMap;

/**
 * <p>Processing strategy to receive branch sequence probes and
 * dispatch branch sequence events.</p>
 *
 * @author Alex Kinneer
 * @version 11/10/2006
 */
public class BranchSequenceProcessingStrategy
        extends AbstractSocketProcessingStrategy
        implements BranchInstrumentationStrategy {
    /** Configuration specifying selected branches. */
    private BranchInstrumentationConfiguration branchConfig =
            new BranchInstrumentationConfiguration();
    
    private boolean ifBranchesOn = branchConfig.areIfBranchesActive();
    private boolean switchBranchesOn = branchConfig.areSwitchBranchesActive();
    private boolean throwsBranchesOn = branchConfig.areThrowsBranchesActive();
    private boolean callBranchesOn = branchConfig.areCallBranchesActive();
    private boolean entryBranchesOn = branchConfig.areEntryBranchesActive();
    private boolean summaryBranchesOn =
        branchConfig.areSummaryBranchesActive();
    
    private TIntObjectHashMap indexSigMap = new TIntObjectHashMap();

    /** Last timestamp that forced a synchronization, used when the subject
        is an event dispatcher. */
    protected static volatile long pendingTimeStamp = 0;
    /** Synchronizes access to <code>pendingTimeStamp</code> and controls
        notifications between threads. */
    protected static Object timeLock = new Object();

    /** Registered event listeners. An array is used because events are
        dispatched to all listeners, and this tool will normally observe
        a **lot** of events. In any case, we don't expect listeners to
        be added or removed mid-run in normal use, so the overhead associated
        with managing the array manually is considered to be mitigated. */
    private BranchEventListener[] listeners = new BranchEventListener[4];
    /** Number of listeners currently registered. */
    private int listenerCount = 0;
    
    /** Conditional compilation debug flag. */
    private static final boolean DEBUG = false;

    /**
     * Creates a new instance of the processing strategy.
     */
    public BranchSequenceProcessingStrategy() {
        super();
        recvBufferSize = (SEQUENCE_ARRAY_SIZE * 4) + 4;
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

    public void reset() {
        super.reset();

        pendingTimeStamp = 0;
        branchConfig.reset();

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).reset();
            }
        }
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

        pendingTimeStamp = 0;
        branchConfig.release();

        for (int n = 0; n < listenerCount; n++) {
            if (listeners[n] instanceof ActiveComponent) {
                ((ActiveComponent) listeners[n]).release();
            }
        }
    }

    public void dispatcherStarting() {
        for (int n = 0; n < listenerCount; n++) {
            listeners[n].newEventStream(0);
        }
    }

    public void dispatcherStopped() {
        for (int n = 0; n < listenerCount; n++) {
            listeners[n].commitEventStream(0);
        }
    }

    public void doHandshake(SocketChannel commChannel)
            throws IOException, ExecException {
        ByteBuffer recvBuffer = ByteBuffer.allocateDirect(8);
        ByteChannelBufferFiller recvFiller = new ByteChannelBufferFiller(
            commChannel, recvBuffer);
        recvBuffer.limit(0); // Ready for controlled reading
        
        recvFiller.ensureAvailableOnly(8);
        // Get trace object type
        TraceObjectType objType = TraceObjectType.fromInt(recvBuffer.getInt());
        // Get instrumentation type
        instMode = recvBuffer.getInt();
        
        if (objType != TraceObjectType.BRANCH_EDGE) {
            recvBuffer.clear();
            recvBuffer.put((byte) 1); // Failure code
            recvBuffer.put((byte) 1); // Expected by receiver, but ignored
            recvBuffer.flip();
            int written = 0;
            while ((written += commChannel.write(recvBuffer)) < 2);
            throw new ExecException("Subject is not instrumented for branch " +
                "tracing");
        }
        recvBuffer.clear();
        recvBuffer.put((byte) 0); // Success code

        // Check if the declared type of instrumentation is one we can handle
        ExecException err = null;
        switch (instMode) {
        case SConstants.INST_COMPATIBLE:
            // Compatible-mode instrumentation is always okay
            recvBuffer.put((byte) 0);
            break;
        case SConstants.INST_OPT_NORMAL:
            // Regular-mode-optimized is never okay
            recvBuffer.put((byte) 1);
            err = new ExecException("Subject is not instrumented for " +
                                    "sequence traces!");
        case SConstants.INST_OPT_SEQUENCE:
            if (isSbjDispatcher) {
                // Optimized-sequence-mode instrumentation will not work
                // on filter-as-subject
                recvBuffer.put((byte) 1);
                err = new ExecException("Cannot use optimized sequence " +
                    "filter instrumentation when the subject is another " +
                    "filter!");
            }
            else {
                // Other subjects are fine
                recvBuffer.put((byte) 0);
            }
            break;
        case SConstants.INST_OLD_UNSUPPORTED:
            recvBuffer.put((byte) 1);
            err = new ExecException("Subject instrumentation is of old " +
                                    "form that is no longer supported");
        default:
            // Don't know what kind of instrumentation it is
            recvBuffer.put((byte) 1);
            err = new ExecException("Subject is not instrumented for " +
                                    "sequence traces!");
        }
        
        // Write responses
        recvBuffer.flip();
        int written = 0;
        while ((written += commChannel.write(recvBuffer)) < 2);
        recvBuffer.clear();
        recvBuffer.limit(0); // Ready for controlled reading
        
        if (err != null) throw err;
        
        // Get sending buffer size
        recvFiller.ensureAvailableOnly(4);
        sendBufferSize = recvBuffer.getInt();
        recvBuffer.clear();
        
        if (DEBUG) {
            stdout.println("sendBufferSize=" + sendBufferSize);
        }
    }

    public void processProbes(SocketChannel recvChannel, ControlData cntrl) {
        ByteBuffer recvBuffer = ByteBuffer.allocateDirect(recvBufferSize);
        recvBuffer.limit(0); // Ready for controlled reading
        
        // Local copies for efficiency
        ifBranchesOn = branchConfig.areIfBranchesActive();
        switchBranchesOn = branchConfig.areSwitchBranchesActive();
        throwsBranchesOn = branchConfig.areThrowsBranchesActive();
        callBranchesOn = branchConfig.areCallBranchesActive();
        entryBranchesOn = branchConfig.areEntryBranchesActive();
        summaryBranchesOn = branchConfig.areSummaryBranchesActive();

        int threadID = cntrl.getThreadID();
        boolean[] threadConnected = cntrl.getConnectionFlags();
        
        threadConnected[threadID] = true;

        try {
            if (recvBuffer.capacity() >= sendBufferSize) {
                // Fast path: we know we can always fit all of the
                // data sent in a block by the probe into the
                // buffer, so we don't have to guard each read to
                // the buffer with checks to make sure the data
                // is actually there
                ByteChannelBufferFiller recvFiller =
                    new ByteChannelBufferFiller(recvChannel, recvBuffer);
                if (instMode == SConstants.INST_COMPATIBLE) {
                    processBasicProbesFast(recvFiller, cntrl);
                }
                else {
                    processOptProbesFast(recvFiller, cntrl);
                }
            }
            else {
                // Slow path: the receive buffer is smaller than the
                // send buffer, so before each data read, we must make
                // sure there is actually enough data in the buffer,
                // and draw in an additional data if not
                VirtualLimitBufferFiller recvFiller =
                    new VirtualLimitBufferFiller(recvChannel, recvBuffer);
                if (instMode == SConstants.INST_COMPATIBLE) {
                    processBasicProbes(recvFiller, cntrl);
                }
                else {
                    processOptProbes(recvFiller, cntrl);
                }
            }
        }
        catch (EOFException e) {
            // Intentionally ignored
        }
        catch (Exception e) {
            throw new ExecException("Processing exception", e);
        }
        finally {
            if (DEBUG) stdout.println("Processing loop exited");

            try {
                recvChannel.close();
            }
            catch (IOException e) {}

            threadConnected[threadID] = false;
        }
    }
    
    // Compatible mode instrumentation, w/o timestamps
    private final void processBasicProbesFast(
            ByteChannelBufferFiller recvFiller, ControlData cntrl)
            throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();
        
        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID]) {
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.ensureAvailableOnly((int) dataLen);
            
            while (recvBuffer.hasRemaining()) {
                // Check message code
                if (recvBuffer.get() == 2) {
                    // This is the trace creation message. Sequence
                    // filter just consumes it since it doesn't need
                    // to know the block count for the method
                    int sigLen = (int) recvBuffer.getChar();
                    
                    if (!DEBUG) {
                        recvBuffer.position(recvBuffer.position()
                            + sigLen + 4);
                    }
                    else {
                        if (rawBuffer.length < sigLen) {
                            rawBuffer = new byte[sigLen];
                        }
                        recvBuffer.get(rawBuffer, 0, sigLen);
                        recvBuffer.getInt();
                        stdout.println("Received create trace " +
                            "message for: " +
                            new String(rawBuffer, 0, sigLen));
                    }
                    continue;
                }
                // else this is a probe data payload
                
                int branchId = recvBuffer.getInt();
                int sigLen = (int) recvBuffer.getChar();
                if (rawBuffer.length < sigLen) {
                    rawBuffer = new byte[sigLen];
                }
                recvBuffer.get(rawBuffer, 0, sigLen);
                
                String classAndSig = new String(rawBuffer, 0, sigLen);
                
                int edgeType = branchId >>> 26;
                branchId &= 0x03FFFFFF;

                switch (edgeType) {
                case BranchType.IIF:
                    if (ifBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].ifBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ISWITCH:
                    if (switchBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].switchBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ITHROW:
                    if (throwsBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].throwBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ICALL:
                    if (callBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].callBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.IENTRY:
                    if (entryBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].entryBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.IOTHER:
                    if (summaryBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].otherBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                default:
                    stderr.println("Invalid branch type code received " +
                        "from instrumented\nclass: " + edgeType);
                    break;
                }
            }
            
            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }
    }
    
    // Compatible mode instrumentation, w/o timestamps
    private final void processBasicProbes(
            VirtualLimitBufferFiller recvFiller, ControlData cntrl)
            throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();
        
        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID]) {
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.virtualLimit(dataLen);
            while (recvFiller.hasRemaining()) {
                recvFiller.ensureAvailable(3);
                // Check message code
                if (recvBuffer.get() == 2) {
                    // This is the trace creation message. Sequence
                    // filter just consumes it since it doesn't
                    // need to know the block count for the method
                    int sigLen = (int) recvBuffer.getChar();
                    
                    recvFiller.ensureAvailable(sigLen + 4);
                    if (!DEBUG) {
                        recvBuffer.position(recvBuffer.position()
                            + sigLen + 4);
                    }
                    else {
                        if (rawBuffer.length < sigLen) {
                            rawBuffer = new byte[sigLen];
                        }
                        recvBuffer.get(rawBuffer, 0, sigLen);
                        recvBuffer.getInt();
                        stdout.println("Received create trace " +
                            "message for: " +
                            new String(rawBuffer, 0, sigLen));
                    }
                    continue;
                }
                // else this is a probe data payload
                
                recvFiller.ensureAvailable(4);
                int branchId = recvBuffer.getInt();
                int sigLen = (int) recvBuffer.getChar();
                
                recvFiller.ensureAvailable(sigLen);
                if (rawBuffer.length < sigLen) {
                    rawBuffer = new byte[sigLen];
                }
                recvBuffer.get(rawBuffer, 0, sigLen);
                
                String classAndSig = new String(rawBuffer, 0, sigLen);
                
                int edgeType = branchId >>> 26;
                branchId &= 0x03FFFFFF;

                switch (edgeType) {
                case BranchType.IIF:
                    if (ifBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].ifBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ISWITCH:
                    if (switchBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].switchBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ITHROW:
                    if (throwsBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].throwBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.ICALL:
                    if (callBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].callBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.IENTRY:
                    if (entryBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].entryBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                case BranchType.IOTHER:
                    if (summaryBranchesOn) {
                        for (int n = 0; n < listenerCount; n++) {
                            listeners[n].otherBranchExecuteEvent(
                                    classAndSig, branchId);
                        }
                    }
                    break;
                default:
                    stderr.println("Invalid branch type code received " +
                        "from instrumented\nclass: " + edgeType);
                    break;
                }
            }
            
            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }
    }
    
    private final void processOptProbesFast(
            ByteChannelBufferFiller recvFiller, ControlData cntrl)
            throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        List<String> methodStack = new ArrayList<String>(30);
        String activeMethod = "#BOOTSTRAP#";
        
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();

        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID]) {
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.ensureAvailableOnly((int) dataLen);
            while (recvBuffer.hasRemaining()) {
                // Check message code
                if (recvBuffer.get() != 1) {
                    // Currently no other message codes are defined
                    // for optimized sequence probe processing
                    throw new ExecException("Unexpected data received " +
                        "from probe");
                }
                
                int sigMapSize = recvBuffer.getInt();
                for (int i = sigMapSize; i-- > 0; ) {
                    // We don't need to know the total object count
                    // for the method, so just consume it
                    recvBuffer.getChar();
                    
                    int idx = recvBuffer.getInt();
                    int sigLen = (int) recvBuffer.getChar();
                    if (rawBuffer.length < sigLen) {
                        rawBuffer = new byte[sigLen];
                    }
                    recvBuffer.get(rawBuffer, 0, sigLen);
                    
                    indexSigMap.put(idx, new String(rawBuffer, 0, sigLen));
                    
                    // Socket probe guarantees that index/signature
                    // pairs are transmitted completely, so either
                    // more are available, or the buffer is empty
                    if (!recvBuffer.hasRemaining() && (i > 0)) {
                        recvFiller.ensureAvailableOnly(8);
                        dataLen = recvBuffer.getLong();
                        recvFiller.ensureAvailableOnly((int) dataLen);
                    }
                }
            }

            recvFiller.ensureAvailableOnly(4);
            int sequenceIndex = recvBuffer.getInt();
            dataLen = sequenceIndex * 4;
            recvFiller.ensureAvailableOnly((int) dataLen);
            
            //System.out.println("sequenceIndex=" + sequenceIndex);
            //System.out.println("pos=" + recvBuffer.position());
            //System.out.println("limit=" + recvBuffer.limit());
            
            for (int i = sequenceIndex; i > 0; i--) {
                int traceInt = recvBuffer.getInt();
               
                switch (traceInt) {
                case NEW_METHOD_MARKER:
                    traceInt = recvBuffer.getInt();
                    i -= 1;
                    methodStack.add(activeMethod);
                    activeMethod = (String) indexSigMap.get(traceInt);
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
                        stderr.println("Invalid branch type code received " +
                            "from instrumented\nclass: " + edgeType);
                        break;
                    }
                }
            }
            
            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }      
    }
    
    private final void processOptProbes(
            VirtualLimitBufferFiller recvFiller, ControlData cntrl)
            throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        List<String> methodStack = new ArrayList<String>(30);
        String activeMethod = "#BOOTSTRAP#";
        
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();

        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID]) {
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.virtualLimit(dataLen);
            while (recvFiller.hasRemaining()) {
                recvFiller.ensureAvailable(3);
                // Check message code
                if (recvBuffer.get() != 1) {
                    // Currently no other message codes are defined
                    // for optimized sequence probe processing
                    throw new ExecException("Unexpected data received " +
                        "from probe");
                }
                
                recvFiller.ensureAvailable(2);
                int sigMapSize = recvBuffer.getInt();
                for (int i = sigMapSize; i-- > 0; ) {
                    recvFiller.ensureAvailable(8);
                    
                    // We don't need to know the total object count
                    // for the method, so just consume it
                    recvBuffer.getChar();
                    
                    int idx = recvBuffer.getInt();
                    int sigLen = (int) recvBuffer.getChar();
                    recvFiller.ensureAvailable(sigLen);
                    if (rawBuffer.length < sigLen) {
                        rawBuffer = new byte[sigLen];
                    }
                    recvBuffer.get(rawBuffer, 0, sigLen);
                    
                    indexSigMap.put(idx, new String(rawBuffer, 0, sigLen));
                    
                    // Socket probe guarantees that index/signature
                    // pairs are transmitted completely, so either
                    // more are available, or the buffer is empty
                    if (!recvFiller.hasRemaining() && (i > 0)) {
                        recvFiller.ensureAvailableOnly(8);
                        dataLen = recvBuffer.getLong();
                        recvFiller.virtualLimit(dataLen);
                    }
                }
            }
            
            recvFiller.ensureAvailableOnly(4);
            int sequenceIndex = recvBuffer.getInt();
            dataLen = sequenceIndex * 4;
            
            recvFiller.virtualLimit(dataLen);
            for (int i = sequenceIndex; i-- > 0; ) {
                recvFiller.ensureAvailable(4);
                int traceInt = recvBuffer.getInt();
                
                switch (traceInt) {
                case NEW_METHOD_MARKER:
                    recvFiller.ensureAvailable(4);
                    traceInt = recvBuffer.getInt();
                    i -= 1;
                    methodStack.add(activeMethod);
                    activeMethod = (String) indexSigMap.get(traceInt);
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
                        stderr.println("Invalid branch type code received " +
                            "from instrumented\nclass: " + edgeType);
                        break;
                    }
                }
            }
            
            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }
    }
    
    // FIXME: I no longer work -- need new strategy for handling
    // and synchronizing buffers! (This is an edge case that does
    // not affect general use).
    public void processProbesSynchronized(SocketChannel recvChannel,
            ControlData cntrl) {
        // Local copies for efficiency
        boolean ifBranchesOn = branchConfig.areIfBranchesActive();
        boolean switchBranchesOn = branchConfig.areSwitchBranchesActive();
        boolean throwsBranchesOn = branchConfig.areThrowsBranchesActive();
        boolean callBranchesOn = branchConfig.areCallBranchesActive();
        boolean entryBranchesOn = branchConfig.areEntryBranchesActive();
        boolean summaryBranchesOn = branchConfig.areSummaryBranchesActive();

        Socket signalSocket = null;
        DataInputStream connectionIn = null;
        DataInputStream signalIn = null;
        DataOutputStream signalOut = null;
        long timeStamp = 0;
        int branchID;

        int threadID = cntrl.getThreadID();
        boolean[] threadConnected = cntrl.getConnectionFlags();
        boolean[] forceStop = cntrl.getStopFlags();
        Throwable[] err = cntrl.getExceptionStorage();

        try {
            connectionIn = new DataInputStream(
                new BufferedInputStream(Channels.newInputStream(recvChannel)));
            // Connect the signal socket
            if (instMode == SConstants.INST_COMPATIBLE) {
                signalSocket = openSignalSocket(recvChannel, ByteBuffer.allocateDirect(1024));
                signalIn = new DataInputStream(signalSocket.getInputStream());
                signalOut =
                    new DataOutputStream(signalSocket.getOutputStream());
            }
            // Indicate that this thread is now connected and processing.
            synchronized(ControlData.stateLock) {
                threadConnected[threadID] = true;
            }
        }
        catch (IOException e) {
            // This may happen if either the subject dispatcher or SocketProbe
            // was instrumented but not the other. Thus it is considered part of
            // orderly shutdown.
            if (e.getMessage().toLowerCase().startsWith("socket closed")) {
                return;
            }
            throw new ExecException("Error accepting instrumentation " +
                                    "connection!", e);
        }

        @SuppressWarnings("unused")
        byte[] buffer = new byte[1024];
        try {
            runLoop:
            while (!forceStop[threadID] && (err[(threadID + 1) % 2] == null)) {
                // If we're about to block reading, notify the other thread so
                // it can continue processing (its trace message must be oldest
                // at this point).
                synchronized(timeLock) {
                    if (!(connectionIn.available() > 0)
                            && threadConnected[(threadID + 1) % 2]) {
                        try {
                            // Force a synchronization with the subject. By
                            // requiring a response, we ensure that  any old
                            // messages still waiting from being blocked when
                            // the socket buffer became full will be written
                            // before we decide whether to grant the the other
                            // thread permission to begin processing.
                            signalOut.writeInt(SConstants.SIG_ECHO);
                            signalOut.flush();
                            signalIn.readInt();
                        }
                        catch (IOException e) {
                            // If this fails, socket has been closed on other
                            // end
                            break runLoop;
                        }
                    }
                    // Continue to check for new messages until we are signaled
                    // to stop, the subject terminates (closes the socket), or
                    // the other thread finishes processing (at which point this
                    // thread continues to run as if it were a regular
                    // unsynchronized processing thread).
                    while (!(connectionIn.available() > 0)
                            && threadConnected[(threadID + 1) % 2]) {
                        if (forceStop[threadID]) break runLoop;
                        // Since ready() returns false both when there is no
                        // data to be read and when the socket is closed, the
                        // only way to determine whether the subject has
                        // terminated is to actually attempt to write something
                        // to its control socket. If the subject is still
                        // alive, its SocketProbe simply consumes this signal.
                        try {
                            signalOut.writeInt(SConstants.SIG_CHKALIVE);
                            signalOut.flush();
                        }
                        catch (IOException e) {
                            break runLoop;
                        }
                        // Everything in the other thread's queue must be older
                        // than whatever will come here next, so let it process
                        // them as a block before checking again.
                        pendingTimeStamp = System.currentTimeMillis();
                        timeLock.notify();
                        timeLock.wait();
                    }
                }

                // Check message code
                if (connectionIn.readByte() == 2) {
                    // This is the trace creation message. Sequence filter
                    // just consumes it since it doesn't need to know
                    // the block count for the method
                    
                    //parseMethodSignature(connectionIn, buffer);
                    connectionIn.readInt();
                }

                // Read timestamp, we may block here
                if (connectionIn.readByte() == 1) {
                    timeStamp = connectionIn.readLong();
                }
                else {
                    throw new ExecException("Malformed trace " +
                        "message: timestamp was not provided");
                }

                // Now check the timestamp against the last one the other thread
                // blocked on. If ours is larger (the trace message is more
                // recent), sleep and allow the other thread to process and
                // register its message. Otherwise, continue processing trace
                // messages until we get one that is younger than the one
                // currently held by the other thread. This check is not
                // performed at all if the other thread is determined to be
                // unconnected.
                synchronized(timeLock) {
                    if ((timeStamp >= pendingTimeStamp)
                            && threadConnected[(threadID + 1) % 2]) {
                        pendingTimeStamp = timeStamp;
                        try {
                            timeLock.notify();
                            timeLock.wait();
                        }
                        catch (InterruptedException e) {
                            throw new ExecException(
                                "Sequence processing thread " + threadID +
                                "was interrupted!");
                        }
                    }
                    if (DEBUG) {
                        stdout.println("Thread " + threadID +
                            " processing message, timestamp: " + timeStamp);
                    }
                }

                // Read the method signature information
                String classAndSig = null;
                    //parseMethodSignature(connectionIn, buffer);

                // Now mark the block
                int dataLength = connectionIn.readInt();  // Number of branches
                if (dataLength > 0) {
                    synchronized(traceLock) {
                        branchID = connectionIn.readInt();
                        int edgeType = branchID >>> 26;
                        branchID &= 0x03FFFFFF;

                        switch (edgeType) {
                        case BranchType.IIF:
                            if (ifBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].ifBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        case BranchType.ISWITCH:
                            if (switchBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].switchBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        case BranchType.ITHROW:
                            if (throwsBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].throwBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        case BranchType.ICALL:
                            if (callBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].callBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        case BranchType.IENTRY:
                            if (entryBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].entryBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        case BranchType.IOTHER:
                            if (summaryBranchesOn) {
                                for (int n = 0; n < listenerCount; n++) {
                                    listeners[n].otherBranchExecuteEvent(
                                            classAndSig, branchID);
                                }
                            }
                            break;
                        default:
                            stderr.println("Invalid branch type code " +
                                "received from instrumented\nclass: " +
                                edgeType);
                            break;
                        }

                        if (dataLength > 1) {
                            stderr.println("WARNING: Multiple branch " +
                                "IDs encoded in trace message, only the " +
                                "first will be recorded");
                            // Now eat the rest (so the input stream is still
                            // in synch)
                            for (int i = 0; i < dataLength - 1; i++) {
                                connectionIn.readInt();
                            }
                        }
                    }
                }
                else {
                    stderr.println("WARNING: No branch ID encoded in " +
                        "trace message");
                }
            }

            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }
        catch (EOFException e) {
            // Intentionally ignored
        }
        catch (SocketException e) {
            if (!e.getMessage().toLowerCase()
                    .startsWith("connection reset")) {
                throw new ExecException(
                        "Processing exception (socket error)", e);
            }
        }
        catch (InterruptedException e) {
            throw new ExecException("Unrecoverable " +
                "interrupt received by thread " + threadID);
        }
        catch (Exception e) {
            throw new ExecException("Processing exception", e);
        }
        finally {
            if (DEBUG) stdout.println("Filter loop exited");

            try {
                signalOut.close();
                signalIn.close();
                signalSocket.close();
                connectionIn.close();
            }
            catch (IOException e) {}

            // Set this thread's flag to indicate that it has finished processing
            // and retrieve the flag containing the other thread's state.
            boolean otherConnected;
            synchronized (ControlData.stateLock) {
                threadConnected[threadID] = false;
                otherConnected = threadConnected[(threadID + 1) % 2];
            }

            // If the other thread is still connected, notify it to continue
            // processing. With this thread's connected flag set to false, the
            // other thread will continue to process until completion without
            // blocking. If the other thread is not still connected, we are
            // completely finished, so stop the server socket.
            if (otherConnected) {
                if (DEBUG) stdout.println(threadID + " notifying");
                synchronized (timeLock) {
                    timeLock.notify();
                }
            }
            else {
                if (DEBUG) stdout.println(threadID + " shutting down");
            }
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
