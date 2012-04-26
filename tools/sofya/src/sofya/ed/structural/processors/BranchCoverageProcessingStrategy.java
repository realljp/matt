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
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import sofya.base.SConstants;
import sofya.base.SConstants.*;
import sofya.ed.BranchCoverageListener;
import sofya.ed.CoverageListenerManager;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BranchInstrumentationStrategy;
import sofya.ed.structural.BranchInstrumentationConfiguration;
import sofya.ed.structural.ControlData;
import sofya.ed.structural.TraceHandler;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import sofya.ed.structural.EventDispatcherConfiguration;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.NEW_METHOD_MARKER;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.BRANCH_EXIT_MARKER;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntArrayList;

/**
 * <p>Processing strategy to receive branch coverage probes and
 * dispatch branch coverage events.</p>
 *
 * @author Alex Kinneer
 * @version 11/10/2006
 */
public class BranchCoverageProcessingStrategy
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

    /** Listener manager that serves the listeners to which the coverage
        events will be dispatched. */
    private CoverageListenerManager listenerManager;

    /** Conditional compilation flag to enable debug outputs. */
    private static final boolean DEBUG = false;
    
    private static final boolean ASSERTS = true;

    /**
     * Creates a new instance of the processing strategy with a trace
     * handler as the default coverage listener manager.
     */
    public BranchCoverageProcessingStrategy() {
        super();
        this.listenerManager = new TraceHandler();
    }

    /**
     * Creates a new instance of the processing strategy.
     *
     * @param clm Coverage listener manager to be used to retrieve coverage
     * listeners to which events will be dispatched.
     */
    public BranchCoverageProcessingStrategy(CoverageListenerManager clm) {
        super();
        setCoverageListenerManager(clm);
    }

    /**
     * Gets the coverage listener manager to be used.
     *
     * @return The coverage listener manager being used used to retrieve
     * coverage listeners to which events will be dispatched.
     */
    public CoverageListenerManager getCoverageListenerManager() {
        return listenerManager;
    }

    /**
     * Sets the coverage listener manager to be used.
     *
     * @param clm Coverage listener manager to be used to retrieve coverage
     * listeners to which events will be dispatched.
     */
    public void setCoverageListenerManager(CoverageListenerManager clm) {
        this.listenerManager = clm;
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

    public void reset() {
        super.reset();

        branchConfig.reset();

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).reset();
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

    public void dispatcherStarting() {
        listenerManager.newEventStream(0);
    }

    public void dispatcherStopped() {
        listenerManager.commitCoverageResults(0);
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

        // Check if the declared type of instrumentation is one we can handle.
        // Coverage filters can handle all types except JUnit - some will just
        // be slower than others.
        ExecException err = null;
        switch (instMode) {
        case SConstants.INST_OPT_SEQUENCE:
            // Optimized sequence mode is okay, but print an
            // efficiency warning
            stdout.println("INFO: Subject is instrumented for sequence " +
                "tracing. Running it through");
            stdout.println("this tracer will be inefficient (and will not " +
                "provide sequence");
            stdout.println("information)");
        case SConstants.INST_COMPATIBLE:
            // Compatible-mode instrumentation is fine
        case SConstants.INST_OPT_NORMAL:
            // The preferred mode
            recvBuffer.put((byte) 0);
            break;
        case SConstants.INST_OLD_UNSUPPORTED:
            recvBuffer.put((byte) 1);
            err = new ExecException("Subject instrumentation is of old " +
                                    "form that is no longer supported");
            break;
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
        
        // Create main receive buffer
        if (instMode == SConstants.INST_OPT_NORMAL) {
            recvBufferSize = 65535;
        }
        else {
            // Synchronize with sending buffer size. This should yield
            // optimal performance. THIS MUST BE GUARANTEED, because for
            // compatible and sequence instrumentation we use the
            // "fast" receive strategy that requires the receive
            // buffer to be at least as large as the send buffer!
            recvBufferSize = sendBufferSize;
        }
        
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
            switch (instMode) {
            case SConstants.INST_COMPATIBLE: {
                ByteChannelBufferFiller recvFiller =
                    new ByteChannelBufferFiller(recvChannel, recvBuffer);
                processBasicProbes(recvFiller, cntrl);
                break;
            }
            case SConstants.INST_OPT_NORMAL: {
                VirtualLimitBufferFiller recvFiller =
                    new VirtualLimitBufferFiller(recvChannel, recvBuffer);
                processOptProbes(recvFiller, cntrl);
                break;
            }
            case SConstants.INST_OPT_SEQUENCE: {
                ByteChannelBufferFiller recvFiller =
                    new ByteChannelBufferFiller(recvChannel, recvBuffer);
                processSeqProbes(recvFiller, cntrl);
                break;
            }
            default:
                throw new ExecException("Unknown instrumentation type");
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
    
    // Compatible mode instrumentation (w/o timestamps)
    private final void processBasicProbes(ByteChannelBufferFiller recvFiller,
            ControlData cntrl) throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();
        Throwable[] err = cntrl.getExceptionStorage();
        
        String classAndSig;
        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID] && (!isSbjDispatcher ||
                (err[(threadID + 1) % 2] == null))) {
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.ensureAvailableOnly((int) dataLen);
            
            while (recvBuffer.hasRemaining()) {
                // Check message code
                if (recvBuffer.get() == 2) {
                    // This is the trace creation message.
                    int sigLen = (int) recvBuffer.getChar();
                    if (rawBuffer.length < sigLen) {
                        rawBuffer = new byte[sigLen];
                    }
                    recvBuffer.get(rawBuffer, 0, sigLen);
                    
                    classAndSig = new String(rawBuffer, 0, sigLen);
                    int maxBlockId = recvBuffer.getInt();
                        
                    if (DEBUG) {
                        stdout.println("Received create trace " +
                            "message for: " + classAndSig);
                    }

                    listenerManager.initializeBranchListener(classAndSig,
                            maxBlockId);
                    continue;
                
                }
                // else this is a probe data payload
                
                int branchId = recvBuffer.getInt();
                int sigLen = (int) recvBuffer.getChar();
                
                if (rawBuffer.length < sigLen) {
                    rawBuffer = new byte[sigLen];
                }
                recvBuffer.get(rawBuffer, 0, sigLen);
                
                classAndSig = new String(rawBuffer, 0, sigLen);
                
                int edgeType = branchId >>> 26;
                branchId &= 0x03FFFFFF;

                BranchCoverageListener listener =
                    listenerManager.getBranchCoverageListener(classAndSig);

                switch (edgeType) {
                case BranchType.IIF:
                    if (ifBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
                    }
                    break;
                case BranchType.ISWITCH:
                    if (switchBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
                    }
                    break;
                case BranchType.ITHROW:
                    if (throwsBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
                    }
                    break;
                case BranchType.ICALL:
                    if (callBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
                    }
                    break;
                case BranchType.IENTRY:
                    if (entryBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
                    }
                    break;
                case BranchType.IOTHER:
                    if (summaryBranchesOn) {
                        listener.branchCovered(branchId, edgeType);
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
    
    private final void processOptProbes(VirtualLimitBufferFiller recvFiller,
            ControlData cntrl) throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        
        List<String> methodStack = new ArrayList<String>(30);
        TIntArrayList objCountStack = new TIntArrayList();
        String activeMethod;
        
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();
        Throwable[] err = cntrl.getExceptionStorage();

        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID] && (!isSbjDispatcher ||
                (err[(threadID + 1) % 2] == null))) {
            if (ASSERTS) {
                assert methodStack.size() == 0;
                assert objCountStack.size() == 0;
            }
            
            recvFiller.ensureAvailableOnly(8);
            long dataLen = recvBuffer.getLong();
            if (dataLen <= 0) continue;
            
            recvFiller.virtualLimit(dataLen);
            while (recvFiller.hasRemaining()) {
                recvFiller.ensureAvailable(5);
                if (recvBuffer.get() != 1) {
                    // Currently no other message codes are defined
                    // for optimized coverage probe processing
                    throw new ExecException("Unexpected data received " +
                        "from probe");
                }
                
                int methodCount = recvBuffer.getInt();
                for (int i = methodCount; i-- > 0; ) {
                    recvFiller.ensureAvailable(4);
                    
                    int objCount = (int) recvBuffer.getChar();
                    objCountStack.add(objCount);
                    
                    int sigLen = (int) recvBuffer.getChar();
                    recvFiller.ensureAvailable(sigLen);
                    if (rawBuffer.length < sigLen) {
                        rawBuffer = new byte[sigLen];
                    }
                    recvBuffer.get(rawBuffer, 0, sigLen);
                    String classAndSig = new String(rawBuffer, 0, sigLen);
                    methodStack.add(classAndSig);
                    
                    listenerManager.initializeBranchListener(
                        classAndSig, objCount);
                }
                
                for (int i = 0; i < methodCount; i++) {
                    // Get method signature from head of queue and clear
                    // it (support GC)
                    activeMethod = methodStack.get(i);
                    // Get number of blocks; don't need to worry about
                    // clearing since it is a primitive array
                    int objCount = objCountStack.get(i);
                    
                    recvFiller.ensureAvailable(objCount);
                    if (rawBuffer.length < objCount) {
                        rawBuffer = new byte[objCount];
                    }
                    recvBuffer.get(rawBuffer, 0, objCount);
                    
                    BranchCoverageListener listener =
                        listenerManager.getBranchCoverageListener(
                            activeMethod);

                    if (DEBUG) {
                        System.out.println("activeMethod=" + activeMethod);
                        System.out.println("objCount=" + objCount);
                        System.out.print("[ ");
                        for (int n = 0; n < objCount; n++) {
                            System.out.print(rawBuffer[n] + " ");
                        }
                        System.out.println("]");
                    }
                    
                    for (int branchId = 1; branchId <= objCount; branchId++) {
                        int edgeType = rawBuffer[branchId - 1];

                        switch (edgeType) {
                        case BranchType.IIF:
                            if (ifBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case BranchType.ISWITCH:
                            if (switchBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case BranchType.ITHROW:
                            if (throwsBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case BranchType.ICALL:
                            if (callBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case BranchType.IENTRY:
                            if (entryBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case BranchType.IOTHER:
                            if (summaryBranchesOn) {
                                listener.branchCovered(branchId, edgeType);
                            }
                            break;
                        case 0:
                            // Not witnessed
                            break;
                        default:
                            stderr.println("Invalid branch type code " +
                                "received from instrumented\nclass: " +
                                edgeType);
                            break;
                        }
                    }
                }
                
                methodStack.clear();
                objCountStack.clear();
            }
            
            // For non-preemptive JVMs
            if (!PREEMPTIVE) {
                Thread.yield();
            }
        }
    }
    
    private final void processSeqProbes(ByteChannelBufferFiller recvFiller,
            ControlData cntrl) throws EOFException, IOException {
        ByteBuffer recvBuffer = recvFiller.getBuffer();
        
        TIntObjectHashMap indexSigMap = new TIntObjectHashMap();
        List<String> methodStack = new ArrayList<String>(30);
        String activeMethod = "#BOOTSTRAP#";
        
        int threadID = cntrl.getThreadID();
        boolean[] forceStop = cntrl.getStopFlags();
        Throwable[] err = cntrl.getExceptionStorage();

        byte[] rawBuffer = new byte[1024];
        while (!forceStop[threadID] && (!isSbjDispatcher ||
                (err[(threadID + 1) % 2] == null))) {
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
                    int objCount = recvBuffer.getChar();
                    
                    int idx = recvBuffer.getInt();
                    int sigLen = (int) recvBuffer.getChar();
                    if (rawBuffer.length < sigLen) {
                        rawBuffer = new byte[sigLen];
                    }
                    recvBuffer.get(rawBuffer, 0, sigLen);
                    String classAndSig = new String(rawBuffer, 0, sigLen);
                    indexSigMap.put(idx, classAndSig);
                    
                    listenerManager.initializeBranchListener(
                            classAndSig, objCount);

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

                    BranchCoverageListener listener =
                        listenerManager.getBranchCoverageListener(
                            activeMethod);

                    switch (edgeType) {
                    case BranchType.IIF:
                        if (ifBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    case BranchType.ISWITCH:
                        if (switchBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    case BranchType.ITHROW:
                        if (throwsBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    case BranchType.ICALL:
                        if (callBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    case BranchType.IENTRY:
                        if (entryBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    case BranchType.IOTHER:
                        if (summaryBranchesOn) {
                            listener.branchCovered(branchId, edgeType);
                        }
                        break;
                    default:
                        stderr.println("Invalid branch type code " +
                            "received from instrumented\nclass: " +
                            edgeType);
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
    
    public void processProbesSynchronized(SocketChannel recvChannel,
            ControlData cntrl) {
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

        Socket signalSocket = null;
        
        if (instMode == SConstants.INST_COMPATIBLE) {
            // Connect the signal socket. We won't do anything with it,
            // but the alternative is to require the user to instrument
            // Sofya/Galileo as an object differently when they intend
            // to run it with a SequenceTracer as opposed to a
            // coverage tracer
            try {
                signalSocket = openSignalSocket(recvChannel, recvBuffer);
                if (DEBUG) {
                    stdout.println(threadID + ": connected signal socket");
                }
                // Indicate that this thread is now connected and processing.
                synchronized (ControlData.stateLock) {
                    threadConnected[threadID] = true;
                }
                if (DEBUG) stdout.println(threadID + ": is connected");
            }
            catch (IOException e) {
                // This may happen if either the subject dispatcher or
                // SocketProbe was instrumented but not the other. Thus it
                // is considered part of orderly shutdown.
                if (e.getMessage().toLowerCase().startsWith("socket closed")) {
                    return;
                }
                throw new ExecException("Error accepting signal socket " +
                                        "connection!", e);
            }
        }
        
        // This will synchronize the requests to create new trace
        // objects for methods
        listenerManager.setSynchronized(true);
        
        // We don't need any additional synchronization; threads can
        // simply compete to record coverage of the same blocks when
        // it happens. We just send the threads to the same
        // processing methods (attached to their respective sockets,
        // though).
        try {
            switch (instMode) {
            case SConstants.INST_COMPATIBLE: {
                ByteChannelBufferFiller recvFiller =
                    new ByteChannelBufferFiller(recvChannel, recvBuffer);
                processBasicProbes(recvFiller, cntrl);
                break;
            }
            case SConstants.INST_OPT_NORMAL: {
                VirtualLimitBufferFiller recvFiller =
                    new VirtualLimitBufferFiller(recvChannel, recvBuffer);
                processOptProbes(recvFiller, cntrl);
                break;
            }
            case SConstants.INST_OPT_SEQUENCE: {
                ByteChannelBufferFiller recvFiller =
                    new ByteChannelBufferFiller(recvChannel, recvBuffer);
                processSeqProbes(recvFiller, cntrl);
                break;
            }
            default:
                throw new ExecException("Unknown instrumentation type");
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
                if (signalSocket != null) signalSocket.close();
            }
            catch (IOException e) {}
    
            try {
                recvChannel.close();
            }
            catch (IOException e) {}
    
            synchronized (ControlData.stateLock) {
                threadConnected[threadID] = false;
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
