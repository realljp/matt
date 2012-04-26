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
import java.io.IOException;
import java.io.EOFException;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import sofya.base.SConstants;
import sofya.base.SConstants.*;
import sofya.ed.BlockCoverageListener;
import sofya.ed.CoverageListenerManager;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.BlockInstrumentationStrategy;
import sofya.ed.structural.BlockInstrumentationConfiguration;
import sofya.ed.structural.ControlData;
import sofya.ed.structural.TraceHandler;
import sofya.ed.structural.AbstractEventDispatcher.ExecException;
import sofya.ed.structural.EventDispatcherConfiguration;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.NEW_METHOD_MARKER;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.BRANCH_EXIT_MARKER;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntArrayList;

/**
 * <p>Processing strategy to receive basic block coverage probes and
 * dispatch basic block coverage events.</p>
 *
 * @author Alex Kinneer
 * @version 11/10/2006
 */
public class BlockCoverageProcessingStrategy
        extends AbstractSocketProcessingStrategy
        implements BlockInstrumentationStrategy {
    /** Configuration specifying selected basic blocks. */
    private BlockInstrumentationConfiguration blockConfig =
            new BlockInstrumentationConfiguration();
    
    private boolean codeBlocksOn;
    private boolean entryBlocksOn;
    private boolean exitBlocksOn;
    private boolean callBlocksOn;
    private boolean returnBlocksOn;

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
    public BlockCoverageProcessingStrategy() {
        super();
        this.listenerManager = new TraceHandler();
    }

    /**
     * Creates a new instance of the processing strategy.
     *
     * @param clm Coverage listener manager to be used to retrieve coverage
     * listeners to which events will be dispatched.
     */
    public BlockCoverageProcessingStrategy(CoverageListenerManager clm) {
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

    public void reset() {
        super.reset();

        blockConfig.reset();

        if (listenerManager instanceof ActiveComponent) {
            ((ActiveComponent) listenerManager).reset();
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
        
        if (objType != TraceObjectType.BASIC_BLOCK) {
            recvBuffer.clear();
            recvBuffer.put((byte) 1); // Failure code
            recvBuffer.put((byte) 1); // Expected by receiver, but ignored
            recvBuffer.flip();
            int written = 0;
            while ((written += commChannel.write(recvBuffer)) < 2);
            throw new ExecException("Subject is not instrumented for basic " +
                "block tracing");
        }
        recvBuffer.clear();
        recvBuffer.put((byte) 0); // Success code
        
        // Check if the declared type of instrumentation is one we can handle
        ExecException err = null;
        switch (instMode) {
        case SConstants.INST_OPT_SEQUENCE:
            // Optimized sequence mode is okay, but print an
            // efficiency warning
            stdout.println("INFO: Subject is instrumented for sequence " +
                "tracing. Running it through");
            stdout.println("this tracer will be inefficient (and " +
                "will not provide sequence");
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
        codeBlocksOn = blockConfig.areCodeBlocksActive();
        entryBlocksOn = blockConfig.areEntryBlocksActive();
        exitBlocksOn = blockConfig.areExitBlocksActive();
        callBlocksOn = blockConfig.areCallBlocksActive();
        returnBlocksOn = false;
        
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

                    listenerManager.initializeBlockListener(classAndSig,
                            maxBlockId);
                    continue;
                
                }
                // else this is a probe data payload
                
                int traceInt = recvBuffer.getInt();
                int sigLen = (int) recvBuffer.getChar();
                
                if (rawBuffer.length < sigLen) {
                    rawBuffer = new byte[sigLen];
                }
                recvBuffer.get(rawBuffer, 0, sigLen);
                
                classAndSig = new String(rawBuffer, 0, sigLen);
                
                int nodeType = traceInt >>> 26;
                int blockId = traceInt & 0x03FFFFFF;

                BlockCoverageListener listener =
                    listenerManager.getBlockCoverageListener(classAndSig);

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
                    stderr.println("Invalid block type code received " +
                        "from instrumented\nclass: " + nodeType);
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
                    
                    listenerManager.initializeBlockListener(
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
                    
                    BlockCoverageListener listener =
                        listenerManager.getBlockCoverageListener(activeMethod);

                    if (DEBUG) {
                        System.out.println("activeMethod=" + activeMethod);
                        System.out.println("objCount=" + objCount);
                        System.out.print("[ ");
                        for (int n = 0; n < objCount; n++) {
                            System.out.print(rawBuffer[n] + " ");
                        }
                        System.out.println("]");
                    }
                    
                    for (int blockId = 1; blockId <= objCount; blockId++) {
                        int nodeType = rawBuffer[blockId - 1];

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
                        case 0:
                            // Not witnessed
                            break;
                        default:
                            stderr.println("Invalid block type code " +
                                "received from instrumented class: " +
                                nodeType);
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
                    
                    listenerManager.initializeBlockListener(
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
                    throw new ExecException("Branch instrumentation " +
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
                        stderr.println("Invalid block type code received " +
                            "from instrumented\nclass: " + nodeType);
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
        codeBlocksOn = blockConfig.areCodeBlocksActive();
        entryBlocksOn = blockConfig.areEntryBlocksActive();
        exitBlocksOn = blockConfig.areExitBlocksActive();
        callBlocksOn = blockConfig.areCallBlocksActive();
        returnBlocksOn = false;
        
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
