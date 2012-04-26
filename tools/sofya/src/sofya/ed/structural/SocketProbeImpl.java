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

package sofya.ed.structural;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.Runtime;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.BufferOverflowException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import org.apache.commons.collections.OrderedMap;
import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.OrderedMapIterator;
import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.collections.map.LinkedMap;

import sofya.base.SConstants.TraceObjectType;
import static sofya.base.SConstants.*;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.*;

/*************************************************************************
 * Provides the default socket-based probe implementation for
 * structural tracing.
 * 
 * <p>This class establishes a persistent socket connection to the event
 * dispatcher on a specified port and implements the probe methods
 * to send trace packets to the dispatcher over that socket. When
 * {@link #start(int, int, boolean, boolean, int)} is called, the
 * main socket connection is established, the probe runs itself
 * as a daemon thread, and connects and begins listening
 * to the signal socket if appropriate.</p>
 * 
 * @author Alex Kinneer
 * @version 06/29/2007
 */
public final class SocketProbeImpl
        implements Sofya$$Probe$10_36_3676__, Runnable {
    // TODO: Implement adaptive instrumentation features (structural)

    /** Compile-time flag to control whether debug statements are present
        in the class. */
    private static final boolean DEBUG = false;
    /** Compile-time flag to control whether assertions are present in
        the class. */
    private static final boolean ASSERTS = true;

    /** Buffer size to be used for accumulating coverage data from
     *  legacy (compatible) instrumentation. */
    private static final int LEGACY_BUFFER_SIZE = 32768;
    
    /** The default buffer size used for assembling coverage data
        packet headers. */
    private static final int COVERAGE_BUFFER_SIZE = 4096;
    
    /** Flag indicating whether start has already been called on this
     *  probe; used to guard against multiple initialization requests. */
    private volatile boolean started;
    /** Flag indicating whether the probe was initialized
        successfully; acts as a guard in <code>finish()</code>,
        since the constructor calls <code>System.exit</code> on
        failure. */
    private volatile boolean connected = false;
    /** Flag indicating whether the probe is already executing the
        shutdown process; used to guard against a double shutdown
        attempt by the shutdown hook. */
    private volatile boolean finishing = false;

    /** The transmission channel attached to the socket. */
    private SocketChannel sendChannel;
    /** The low-level transmission buffer for the socket. */
    private ByteBuffer sendBuffer;

    /** Socket for receiving/exchanging synchronization signals with the
        event dispatcher, used only when the subject is an event dispatcher. */
    private Socket signalSocket;
    /** Input stream attached to signal socket. */
    private DataInputStream signalIn = null;
    /** Output stream attached to signal socket. */
    private DataOutputStream signalOut = null;
    
    /** Lock used to synchronize output to the socket. */
    private final Semaphore lock = new Semaphore(1, false);
    
    /** Default instrumentation is only incompatible with the JUnit
        event dispatcher. */
    private int instMode = INST_COMPATIBLE;
    /** Indicates the type of program entity traced by the instrumentation
        in the subject. */
    private int objType = TraceObjectType.IBASIC_BLOCK;
    /** Flag specifying whether signal socket is in use. It is set by
        the <code>start</code> method. */
    private boolean useSignalSocket;
    /** Flag indicating whether trace messages are to be timestamped. */
    private boolean doTimestamps;
    
    /** Reference to running thread, if any. If a thread has already been
        started for this SocketProbe, the SocketProbe start method will
        return immediately without taking any action. */
    private Thread ref = null;
    
    /** Set of threads which are ignored when <code>finish()</code>
        waits for all subject threads to exit (no subject threads
        should be placed in this set). */
    private Set<Object> ignoreThreads = null;
    /** Number of threads placed in <code>ignoreThreads</code>. */
    private int ignoreThreadCount = -1;

    /** A thread-local that provides a coverage array cache for each
        application thread. Each per-thread cache uses an LRU policy
        to attempt to remove unneccessary coverage arrays when a
        certain maximum is reached, in an effort to limit overhead. */
    private CoverageArrays<Map<Object, Object>> covArrays;
    /** Maximum number of methods for which coverage arrays can be cached
        for a thread, before the probe begins to attempt removing old
        coverage arrays for that thread. */
    private static final int THREAD_LRU_MAX = 100;
    /** The number of least-recently-used coverage arrays the probe scans,
        to try to find candidates for removal and transmission when the
        per-thread limit is exceeded. */
    private static final int THREAD_LRU_SCAN_SIZE = 15;
    /** The accumulated threshold at which the probe allocates a new,
        larger per-thread coverage array cache because the thread is
        consistently over the maximum (more information on this is
        available in subsequent comments). */
    private static final int THREAD_LRU_OVERMAX_THRESHOLD = 25;
    /** The minimum amount by which the probe will increase the per
        thread cache size on an expansion. */
    private static final int THREAD_LRU_EXPAND_INCREMENT = 20;

    /** Processing thread that detects exited application threads and
        transmits their associated coverage arrays. */
    private ThreadExitHandler threadExitHandler;

    /** Maps indices following new method markers to the signature
        string for that method. */
    private HashMap<Object, Object> indexToNameMap;
    /** Maps indices to records for entirely new methods recorded in
        the sequence trace array; the records contain the signature
        and number of trace objects in the method, for transmission
        in the sequence data packet header. */
    private HashMap<Object, Object> newMethodMap;
    /** Maps signature strings for a method to an already assigned
        index, if any. */
    private HashMap<Object, Object> nameToIndexMap;
    /** Holds the next value available for use as an index to a
        method signature string. */
    private int nextMethodIndex = 0;
    
    // No argument constructor needed to create from bootstrap classloader
    public SocketProbeImpl() {
    }

    public final void start(int port, int instMode, boolean doTimestamps,
            boolean useSignalSocket, int objType) {
        if (!started) {
            this.started = true;
            
            if ((instMode < 1 || instMode > 4)
                    && !(instMode == INST_OLD_UNSUPPORTED)) {
                System.err.println("Unrecognized type of instrumentation: " +
                    instMode);
                System.exit(1);
            }
            
            System.setErr(System.out);
            this.instMode = instMode;
            this.doTimestamps = doTimestamps;
            this.useSignalSocket = useSignalSocket;
            this.objType = objType;
            
            // Contents of constructor here
            connect(port);
            
            ref = new Thread(this, "SofyaSocketProbe");
            ref.setDaemon(true);
            ref.start();

            registerInitThreads();
            
            // A bit of a kludge, but far easier than more instrumentation
            // hacking, and doesn't add any appreciable overhead
            // in any case...
            String callingClass;
            try {
                throw new Exception("stack query");
            }
            catch (Exception e) {
                StackTraceElement[] threadStack = e.getStackTrace();
                callingClass = threadStack[1].getClassName();
            }
            if ("SocketProbe".equals(callingClass)) {
                //System.out.println(Runtime.getRuntime() +
                //    ": legacy SocketProbe");
                if (ignoreThreads.add(threadExitHandler)) {
                    //System.out.println("ignoring thread exit handler");
                    ignoreThreadCount += 1;
                }
                // Current thread should be main
                (new MainWaiter(Thread.currentThread())).start();
            }
            else {
                // We do not want to wait on this thread during shutdown
                // (hello deadlock), so we add it to the ignored
                // threads set
                if (DEBUG) System.out.println("SPI: Adding shutdown hook");
                Thread shutdownThread = new ProbeShutdownHook(this);
                if (ignoreThreads.add(shutdownThread)) {
                    ignoreThreadCount += 1;
                }
                Runtime.getRuntime().addShutdownHook(shutdownThread);
            }
        }
    }
    
    /*************************************************************************
     * <p>Attempts to connect the socket used to send probe data to the
     * event dispatcher. This constructor should be called by
     * {@link SocketProbe#start}, which will be invoked by the subject with
     * the port number embedded by the {@link sofya.ed.Instrumentor}.
     * Naturally, if the subject is instrumented on a different port than the
     * event dispatcher is listening on, this action will fail. If the
     * connection cannot be made, the system exits (an action which will
     * occur before any subject code is executed).</p>
     *
     * @param port Port number to be used to connect the socket. This should
     * be set by the instrumentor and supplied by the subject via a call to
     * {@link SocketProbe#start}.
     */
    @SuppressWarnings("unchecked")
    private final void connect(int port) {
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(8);
            sendChannel = SocketChannel.open(new InetSocketAddress(
                "localhost", port));
            
            // Check if filter is prepared to handle declared type of
            // instrumentation
            if (DEBUG) System.out.println("SPI: initiate handshake");
            try {
                buffer.putInt(objType);
                buffer.putInt(instMode);
                buffer.flip();
                int written = 0;
                while ((written += sendChannel.write(buffer)) < 8);
                if (DEBUG) {
                    System.out.println("SPI: sent entity type and " +
                        "instrumentation type");
                }

                if (DEBUG) {
                    System.out.println("SPI: reading responses");
                }
                buffer.clear();
                buffer.limit(2);
                int read = 0;
                while ((read += sendChannel.read(buffer)) < 2);
                buffer.flip();
                int response = buffer.get();
                if (response == 1) {
                    if (DEBUG) {
                        System.out.println("SPI: entity type rejected");
                    }
                    // Negative response, so die. The filter will print the
                    // error message.
                    System.exit(response);
                }
                if (DEBUG) {
                    System.out.println("SPI: entity type accepted");
                }

                response = buffer.get();
                if (response == 1) {
                    if (DEBUG) {
                        System.out.println("SPI: instrumentation " +
                            "type rejected");
                    }
                    // Negative response, so die. The filter will print the
                    // error message.
                    System.exit(response);
                }
                if (DEBUG) {
                    System.out.println("SPI: instrumentation " +
                        "type accepted");
                }
            }
            catch (Exception e) {
                System.err.println("Could not complete handshake with filter!");
                throw e;
            }
            
            if (DEBUG) System.out.println("SPI: handshake completed");
            if (useSignalSocket) {
                try {
                    // The event dispatcher will put the port for the signal
                    // socket on the main socket. Read it and connect
                    // the signal socket.
                    buffer.clear();
                    buffer.limit(4);
                    int read = 0;
                    while ((read += sendChannel.read(buffer)) < 4);
                    buffer.flip();
                    int signalPort = buffer.getInt();
                    buffer.clear();
                    signalSocket = new Socket("localhost", signalPort);
                    signalIn =
                        new DataInputStream(signalSocket.getInputStream());
                    signalOut =
                        new DataOutputStream(signalSocket.getOutputStream());
                }
                catch (Exception e) {
                    System.err.println("Error opening signal socket!");
                    throw e;
                }
            }
            
            // Set up data structures depending on instrumentation type
            switch (instMode) {
            case INST_COMPATIBLE:
                sendBuffer = ByteBuffer.allocateDirect(LEGACY_BUFFER_SIZE);
                sendBuffer.position(8);
                break;
            case INST_OPT_NORMAL:
                covArrays = new CoverageArrays<Map<Object, Object>>();
                threadExitHandler = new ThreadExitHandler(covArrays);
                threadExitHandler.start();
                // This is just for the headers (message type + method
                // signature); the method byte arrays will be wrapped
                // directly for transfer over the socket channel
                sendBuffer = ByteBuffer.allocateDirect(
                    COVERAGE_BUFFER_SIZE);
                break;
            case INST_OPT_SEQUENCE:
                sequenceArray = new int[SEQUENCE_ARRAY_SIZE];
                Arrays.fill(sequenceArray, 0);
                indexToNameMap = new HashMap<Object, Object>();
                nameToIndexMap = new HashMap<Object, Object>();
                newMethodMap = new HashMap<Object, Object>();
                sendBuffer = ByteBuffer.allocateDirect(
                        (SEQUENCE_ARRAY_SIZE * 4) + 4);
                sendBuffer.position(8);
                break;
            default:
                throw new AssertionError();
            }
            
            buffer.clear();
            buffer.putInt(sendBuffer.capacity());
            buffer.flip();
            int written = 0;
            while ((written += sendChannel.write(buffer)) < 4);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error opening socket for instrumentation! " +
                "(Port " + port + ")");
            System.exit(1);
        }
        connected = true;
    }
    
    /*************************************************************************
     * Registers any initial threads in the JVM in the thread ignore set, so
     * we won't try to wait for those threads when all the subject threads
     * have exited. Note that the socket probe thread itself will also be
     * included in the ignore set, assuming that this method is called at
     * appropriate location in the <code>start</code> method.
     */
    private final void registerInitThreads() {
        ThreadGroup baseTG = ref.getThreadGroup();
        ignoreThreadCount = baseTG.activeCount();
        ignoreThreads = new HashSet<Object>();
        
        Thread[] curThreads = new Thread[ignoreThreadCount];
        int copied = baseTG.enumerate(curThreads);
        for (int i = 0; i < copied; i++) {
            ignoreThreads.add(curThreads[i]);
        }
    }

    public final void finish() {
        finish(null);
    }
    
    /*************************************************************************
     * Notifies the socket probe that subject execution has completed.
     *
     * <p>The method ensures that any data currently cached but not
     * transmitted is sent to the event dispatcher, guaranteeing that the trace
     * will be complete. Any open sockets are then closed.</p>
     */
    final void finish(Thread parentThread) {
        // The patched Runtime calls this method from exit(int) and halt(int).
        // The constructor calls System.exit on failure. So if the constructor
        // failed, we end up here, but we need to not try to clean up
        // resources that never got allocated/created in the first place.
        if (!connected) return;

        if (DEBUG) System.out.println("SPI: Checking finish flag");
        // A shutdown hook is added to handle the case where the
        // instrumented socket probe may not call the finish
        // method on SocketProbeAlt (esp. older Galileo versions)
        synchronized(lock) {
            if (finishing) {
                if (DEBUG) {
                    System.out.println("SPI: Already finished, exiting");
                }
                return;
            }
            finishing = true;
        }
        if (DEBUG) System.out.println("SPI: Finishing...");

        if (parentThread != null) {
            if (ignoreThreads.add(parentThread)) {
                ignoreThreadCount += 1;
            }
        }

        waitOnThreads(parentThread != null);

        if (DEBUG) {
            System.out.println("SPI: Transmitting remaining trace data...");
        }

        // Send any cached data
        switch (instMode) {
            case INST_COMPATIBLE:
                try {
                    lock.acquireUninterruptibly();
                    
                    int pos = sendBuffer.position();
                    sendBuffer.putLong(0, pos - 8);
                    sendBuffer.flip();
                    
                    int written = 0;
                    while ((written += sendChannel.write(sendBuffer)) < pos);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("ERROR: Failed to commit trace data " +
                        "buffer on shutdown, trace information has been lost");
                }
                finally {
                    lock.release();
                }
                break;
            case INST_OPT_NORMAL:
                sendFinalCoverageData();
                break;
            case INST_OPT_SEQUENCE:
                writeSequenceData();
                break;
            default:
                System.err.println("WARNING: finish() called but the " +
                    "instrumentation type is unknown!");
                break;
        }

        if (DEBUG) System.out.println("SPI: Closing sockets...");

        // Close the sockets
        if (signalSocket != null) {
            if (signalIn != null) {
                try {
                    signalIn.close();
                }
                catch (Exception e) { }
            }
            if (signalOut != null) {
                try {
                    signalOut.close();
                }
                catch (Exception e) { }
            }
            try {
                signalSocket.close();
            }
            catch (Exception e) { }
        }
        try {
            sendChannel.close();
        }
        catch (Exception e) { }

        if (DEBUG) System.out.println("SPI: Shutdown complete");
    }

    /*************************************************************************
     * Waits for all subject threads to terminate.
     *
     * <p>This method successively joins to all the threads found in the
     * main thread group except for those threads placed in the thread
     * ignore set by {@link #registerInitThreads}. The application thread
     * group is the top ancestor thread group for all threads created by
     * the subject and thus will always recursively contain all of the
     * subject threads. This process is repeated until no such subject
     * threads remain in the main thread group (which prevents us from
     * failing to account for additional threads created during the
     * run of the subject).</p>
     *
     * <p><strong>Note:</strong> The main thread group is not to be
     * confused with the system thread group, which contains special
     * JVM managed threads.</p>
     *
     * <p><em><strong>WARNING:</strong> Threads allocated but not started
     * by the subject program may be included in the count of active threads,
     * but not returned in the enumeration of active threads. As a consequence,
     * this method will be unable to complete and the subject will hang.
     * <strong>This is a bug in the Sun JVM!</strong> The status
     * of this problem on other JVM implementations is unknown. To prevent
     * this problem, subjects should be modified such that no allocated
     * threads are left unstarted at program exit.</em>
     */
    private void waitOnThreads(boolean fromShutdownHook) {
        ThreadGroup baseTG = ref.getThreadGroup();
        Thread[] activeThreads = new Thread[10];
        int threadCount;

        if (DEBUG) {
            System.out.println("SPI: Ignore threads:\n" + ignoreThreads);
            System.out.println("SPI: Waiting on threads exiting...");
        }

        // We must loop and continue to join threads, since threads that we
        // know about now may create other threads we don't know about
        while ((threadCount = baseTG.activeCount()) > ignoreThreadCount) {
            if (threadCount > activeThreads.length) {
                activeThreads = new Thread[threadCount];
            }

            int copied = baseTG.enumerate(activeThreads);
            for (int i = 0; i < copied; i++) {
                Thread curThread = activeThreads[i];

                if (ignoreThreads.contains(curThread)) {
                    continue;
                }
                else if (curThread.isDaemon()) {
                    if (ignoreThreads.add(curThread)) {
                        ignoreThreadCount += 1;
                    }
                    continue;
                }
                else if (!curThread.isAlive()) {
                    continue;
                }
                else if (curThread == Thread.currentThread()) {
                    if (ignoreThreads.add(curThread)) {
                        ignoreThreadCount += 1;
                    }
                    else {
                        throw new AssertionError();
                    }
                    continue;
                }
                else if ("DestroyJavaVM".equals(curThread.getName())) {
                    // I don't like hardcoding a test against a specific
                    // thread name, but there doesn't seem to be any other
                    // way. This case can only be reached when using the
                    // MainWaiter thread on a legacy Galileo SocketProbe.
                    // An explanation of how the (non-daemon) "DestroyJavaVM"
                    // thread works can be found in Sun bug #4363932.
                    if (ignoreThreads.add(curThread)) {
                        ignoreThreadCount += 1;
                    }
                    continue;
                }

                try {
                    if (DEBUG) {
                        System.out.println(Runtime.getRuntime() +
                            ": SPI, joining to: " + curThread);
                    }
                    curThread.join();
                }
                catch (InterruptedException e) { }
            }
        }
    }

    public final void writeObjectCount(String mSignature, int objCount) {
        try {
            lock.acquireUninterruptibly();
            
            // NOTE: Now exclusively used by compatible mode instrumentation
            
            byte[] sigBytes = mSignature.getBytes();
            int sigLen = sigBytes.length;
            // Message code (1 byte) + signature length (2 bytes) +
            // object count (4 bytes);
            int totalLen = 7 + sigLen;
            
            if (sendBuffer.remaining() < totalLen) {
                int pos = sendBuffer.position();
                sendBuffer.putLong(0, pos - 8);
                sendBuffer.flip();
                
                int written = 0;
                while ((written += sendChannel.write(sendBuffer)) < pos);
                
                sendBuffer.clear();
                sendBuffer.position(8);
            }

            sendBuffer.put((byte) 2);
            sendBuffer.putChar((char) sigLen);
            sendBuffer.put(sigBytes);
            sendBuffer.putInt(objCount);
        }
        catch (NullPointerException e) {
            // Structures not initialized because start() wasn't called
            e.printStackTrace();
            System.err.println("Trying to send object count for: " +
                mSignature);
            System.err.println("Instrumentation socket is not connected");
        }
        catch (SocketException e) {
            // If transmission failed because we are in shutdown and the socket
            // was already closed, suppress the error message. This only arises
            // in a very specific case when a filter is run on another filter,
            // and two exit hooks get created.
            if (!e.getMessage().toLowerCase().endsWith("socket closed")) {
                e.printStackTrace();
                // Port number output disabled because it causes problems with
                // differencing
                System.err.println("Error attempting to write object count!");
                    // (Port " + socket.getLocalPort() + ")");
            }
        }
        catch (IOException e) {
            // Don't want subject to die suddenly, so simply report error
            e.printStackTrace();
            System.err.println("Error attempting to write object count!");
                // (Port " + socket.getLocalPort() + ")");
        }
        catch (Exception e) {
            // Catch all unexpected exceptions and report them
            System.err.println("Error writing object count");
        }
        finally {
            lock.release();
        }
    }

    public final void writeTraceMessage(int bId, String mSignature) {
        try {
            lock.acquireUninterruptibly();
            
            byte[] sigBytes = mSignature.getBytes();
            int sigLen = sigBytes.length;
            
            // Message code (1 byte) + block ID (4 bytes) +
            // signature length (2 bytes)
            int totalLen = 7 + ((doTimestamps) ? 8 : 0) + sigLen;
            
            if (sendBuffer.remaining() < totalLen) {
                int pos = sendBuffer.position();
                sendBuffer.putLong(0, pos - 8);
                sendBuffer.flip();
                
                int written = 0;
                while ((written += sendChannel.write(sendBuffer)) < pos);
                
                sendBuffer.clear();
                sendBuffer.position(8);
            }
            
            sendBuffer.put((byte) 1);  // Message code: trace data

            if (doTimestamps) {
                // Compatible-mode instrumentation for sequence tracing
                sendBuffer.putLong(System.currentTimeMillis());
            }
            // else compatible-mode instrumentation for coverage tracing
            
            sendBuffer.putInt(bId);
            sendBuffer.putChar((char) sigLen);
            sendBuffer.put(sigBytes);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: Failure transmitting trace data " +
                "buffer, trace information has been lost");
        }
        finally {
            lock.release();
        }
    }

    public final byte[] getObjectArray(String mSignature, int objCount) {
        Map<Object, Object> covMap = covArrays.get();
        
        if (DEBUG) {
            System.out.println("SPI: Got provider map " +
                System.identityHashCode(covMap));
            System.out.println("  for: " + mSignature);
        }
        
        byte[] covArray = (byte[]) covMap.get(mSignature);
        if (covArray == null) {
            covArray = new byte[objCount];
            covMap.put(mSignature, covArray);
        }
        
        return covArray;
    }

    public final void markMethodInSequence(String mSignature, int objCount) {
        if (sequenceIndex > sequenceArray.length - 2) {
            // The array is full, so transmit the data. This also resets the
            // index..
            writeSequenceData();
        }
        sequenceArray[sequenceIndex++] = NEW_METHOD_MARKER;
        if (nameToIndexMap.containsKey(mSignature)) {
            // We've already linked this method to an index, so use that value
            sequenceArray[sequenceIndex++] =
                ((Integer) nameToIndexMap.get(mSignature)).intValue();
        }
        else {
            // Need to create a new index to correspond to this method
            sequenceArray[sequenceIndex++] = nextMethodIndex;
            Integer nextIdx = new Integer(nextMethodIndex);
            nameToIndexMap.put(mSignature, nextIdx);
            if (!indexToNameMap.containsKey(nextIdx)) {
                indexToNameMap.put(nextIdx, mSignature);
                newMethodMap.put(nextIdx,
                    new NewMethodData(mSignature, objCount));
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
    
    /*************************************************************************
     * Reallocates the send buffer used for transmitting coverage
     * trace data.
     * 
     * @param increment Amount by which to increase the buffer size.
     */
    private final void reallocateBuffer(int increment) {
        ByteBuffer tmpBuffer = ByteBuffer.allocateDirect(
            sendBuffer.capacity() + increment);
        sendBuffer.flip();
        tmpBuffer.put(sendBuffer);
        sendBuffer = tmpBuffer;
    }
    
    /*************************************************************************
     * Transmits all pending coverage trace data prior to shutting the
     * probe down and exiting.
     * 
     * <p>This method waits for all non-daemon application threads to exit,
     * merges the coverage arrays from all pending per-thread coverage
     * array maps, and transmits the coverage data to the
     * event dispatcher.</p>
     */
    @SuppressWarnings("unchecked")
    final void sendFinalCoverageData() {
        lock.acquireUninterruptibly();

        CoverageArrays<Map<Object, Object>> capturedCovArrays;
        try {
            if (ASSERTS) {
                if (sendBuffer.position() != 0)
                    throw new AssertionError(
                        String.valueOf(sendBuffer.position()));
            }
            
            // Capture a reference to the map and then replace it with a
            // new empty map. From this point on, no new trace data will
            // be recorded. This is intended only to avoid concurrent
            // modification exceptions during iteration caused by daemon
            // threads which continue to run.
            capturedCovArrays = covArrays;
            covArrays = new CoverageArrays<Map<Object, Object>>();
            
            if (sendBuffer.capacity() < 32768) {
                sendBuffer = ByteBuffer.allocateDirect(32768);
            }
        }
        finally {
            lock.release();
        }
        
        // It is CRITICAL that we release the lock here before proceeding,
        // because it is a semaphore and therefore not re-entrant (it
        // wouldn't matter even if it were, since the exit handler is
        // a different thread)
        
        threadExitHandler.halt();
        try {
            threadExitHandler.join();
        }
        catch (InterruptedException e) {
            System.err.println("WARNING: Unexpected interrupt received " +
                "waiting for exit handler thread to halt");
        }
        
        if (DEBUG) {
            System.out.println("SPI: sending final coverage data");
            Map<Thread, StackTraceElement[]> threadStacks =
                Thread.getAllStackTraces();
            Iterator<Thread> ts = threadStacks.keySet().iterator();
            while (ts.hasNext()) {
                System.out.println("  " + ts.next());
            }
        }
        
        // From this point on we assume we have single threaded access
        // to all of the coverage arrays.
        
        // We now iterate over all of the coverage arrays from different
        // threads and merge them into a single map for transmission.
        // This is motivated by the fact that we expect I/O to be the
        // major bottleneck, so reducing the required transmission
        // bandwidth is the biggest win. It should result in less
        // redundant work in the event dispatcher anyway.
        
        OrderedMap mergedCovArrays = new LinkedMap();
        
        Map<Map<Object, Object>, Object> threadCovArrays =
            capturedCovArrays.threadCovArrays;
        Iterator<Map<Object, Object>> covArrayIter =
            threadCovArrays.keySet().iterator();
        int size = threadCovArrays.size();
        for (int i = size; i-- > 0; ) {
            Map<Object, Object> myArrays = covArrayIter.next();
            int arrCount = myArrays.size();
            MapIterator it = ((OrderedMap) myArrays).orderedMapIterator();
            
            if (DEBUG) {
                System.out.println("SPI: Shutdown, sending data for:");
            }
            
            for (int j = arrCount; j-- > 0; ) {
                String sig = (String) it.next();
                byte[] covArray = (byte[]) it.getValue();
                
                if (DEBUG) {
                    System.out.println("  " + sig);
                }
                
                byte[] mergeArray = (byte[]) mergedCovArrays.get(sig);
                if (mergeArray == null) {
                    // First time we've seen coverage for this method,
                    // so simply add the coverage array
                    mergedCovArrays.put(sig, covArray);
                    continue;
                }
                
                // A coverage array for this method has already been
                // provided by another thread(s). Merge the
                // current thread's array with the existing array.
                int idx = covArray.length - 1;
                for (int k = idx; k >= 0; k--) {
                    mergeArray[k] |= covArray[k];
                }
                
                it.remove();
            }
        }
        
        sendCoverageData(mergedCovArrays);
    }
    
    /*************************************************************************
     * Transmits a set of coverage arrays to the event dispatcher.
     * 
     * @param covArrays Map containing the coverage arrays to be sent.
     */
    final void sendCoverageData(OrderedMap covArrays) {
        try {
            
        lock.acquireUninterruptibly();

        if (ASSERTS) {
            if (sendBuffer.position() != 0)
                throw new AssertionError();
            if (sendBuffer.capacity() < 16)
                throw new AssertionError();
        }
        
        int arrCount = covArrays.size();
        
        ByteBuffer[] sendStack = new ByteBuffer[arrCount + 1];
        int bufferPos = 1;

        sendBuffer.position(8);
        sendBuffer.put((byte) 1);  // Message code: trace data
        sendBuffer.putInt(arrCount);  // Number of methods being reported
        
        // We have to cast to OrderedMap to get the appropriate iterator;
        // using the regular iterator causes LRU maps to throw a
        // ConcurrentModificationException just by the very act
        // of iterating
        long writeLen = 0;
        MapIterator it = covArrays.orderedMapIterator();
        for (int i = arrCount; i-- > 0; ) {
            byte[] sigBytes = ((String) it.next()).getBytes();
            byte[] covArray = (byte[]) it.getValue();
            it.remove();
            
            // Lengths of the following are bounded by JLS limits:
            //  - A method signature cannot exceed 65536 bytes
            //  - A method cannot be longer than 65536 bytes (this
            //    would certainly seem to ensure that there must
            //    be fewer than 32767 basic blocks, but we'll
            //    give more room just to be safe)
            // The 'char' type is used to get the equivalent of
            // an unsigned short.
            char sigLen = (char) sigBytes.length;
            char arrLen = (char) covArray.length; 
        
            if (ASSERTS) {
                if (sigLen > 65536)
                    throw new AssertionError();
                if (arrLen > 65536)
                    throw new AssertionError();
            }

            // Coverage trace data makes no use of timestamps
            
            sendStack[bufferPos++] = ByteBuffer.wrap(covArray);
            writeLen += arrLen;
            
            try {
                sendBuffer.putChar((char) arrLen);
            }
            catch (BufferOverflowException e) {
                // The use of an exception handler here is intentional. Once
                // the buffer is of sufficient size (hopefully right from
                // initialization), we should never again encounter this
                // exception. From that point on, no cost is incurred on
                // the normal path, which from a performance perspective is
                // much preferable to incurring the cost of limit/capacity
                // checking on every one of these writes to the buffer
                reallocateBuffer(4096);
                sendBuffer.putChar((char) arrLen);
            }
            
            try {
                sendBuffer.putChar((char) sigLen);
            }
            catch (BufferOverflowException e) {
                reallocateBuffer(4096);
                sendBuffer.putChar((char) sigLen);
            }
            
            try {
                sendBuffer.put(sigBytes);
            }
            catch (BufferOverflowException e) {
                reallocateBuffer(4096);
                sendBuffer.put(sigBytes);
            }
        }
        
        writeLen += sendBuffer.position();
        sendBuffer.putLong(0, writeLen - 8);
        sendBuffer.flip();
        sendStack[0] = sendBuffer;
        
        long written = 0;
        while ((written += sendChannel.write(sendStack)) < writeLen);
        sendBuffer.clear();
        
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: Failure transmitting trace data, " +
                "trace information has been lost");
        }
        finally {
            lock.release();
        }
    }

    public final void writeSequenceData() {
        // Not thread safe
        try {
        
        if (ASSERTS) {
            if (sendBuffer.position() != 8) {
                System.out.println(sendBuffer.position());
                throw new AssertionError();
            }
        }
            
        sendBuffer.put((byte) 1);  // Message code: trace data
        
        // Send any new index->signature mappings
        // Not thread safe
        int mappingCount = newMethodMap.size();
        //System.out.println("sp.pos=" + sendBuffer.position());
        //System.out.println("sp.mappingCount=" + mappingCount);
        sendBuffer.putInt(mappingCount);
        Iterator<Object> indexes = newMethodMap.keySet().iterator();
        for (int i = mappingCount; i-- > 0; ) {
            Integer idx = (Integer) indexes.next();
            NewMethodData data = (NewMethodData) newMethodMap.get(idx);
            byte[] sigBytes = data.signature.getBytes();
            int sigLen = sigBytes.length;
            
            // 8 = 2 bytes for object count, 4 bytes for index,
            // 2 bytes for signature length
            if (sendBuffer.remaining() < (8 + sigLen)) {
                //System.out.println("sp.partial_write");
                int numBytes = sendBuffer.position();
                sendBuffer.putLong(0, numBytes - 8);
                sendBuffer.flip();
                
                int written = 0;
                while ((written += sendChannel.write(sendBuffer)) < numBytes);
                sendBuffer.clear();

                sendBuffer.position(8);
            }
            
            sendBuffer.putChar((char) data.objCount);
            sendBuffer.putInt(idx.intValue());
            sendBuffer.putChar((char) sigLen);
            sendBuffer.put(sigBytes);
        }
        newMethodMap.clear();
        
        // Clear the buffer, so it can be entirely filled by the object
        // ID data; the buffer size should match the sequence array size
        // (in bytes)
        //System.out.println("sp.final_write");
        int numBytes = sendBuffer.position();
        //System.out.println("sp.pos=" + numBytes);
        sendBuffer.putLong(0, numBytes - 8);
        sendBuffer.flip();

        int written = 0;
        while ((written += sendChannel.write(sendBuffer)) < numBytes);
        sendBuffer.clear();
        
        // Write the actual sequence data
        sendBuffer.putInt(sequenceIndex);
        
        // Not thread safe
        for (int i = 0; i < sequenceIndex; i++) {
            sendBuffer.putInt(sequenceArray[i]);
        }
        
        numBytes = sendBuffer.position();
        sendBuffer.flip();
        written = 0;
        while ((written += sendChannel.write(sendBuffer)) < numBytes);
        sendBuffer.clear();
        sendBuffer.position(8);
        
        sequenceIndex = 0;
        
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: Failure transmitting sequence trace " +
                "data, trace information has been lost");
        }
    }

    /*************************************************************************
     * Runs the (daemon) thread.
     *
     * <p>Note that the run method is uninterruptable. Instead, since it is
     * running as a daemon thread, it will die automatically when the 
     * subject exits.</p>
     */
    public final void run() {
        int signal;
        synchronized(this) {
            if (useSignalSocket) {
                while (true) {
                    try {
                        signal = signalIn.readInt();
                        if (signal == SIG_ECHO) {
                            signalOut.writeInt(signal);
                            signalOut.flush();
                        }
                    }
                    catch (InterruptedIOException e) { break; }
                    catch (IOException e) { }
                }
            }
            else {
                while (true) {
                    try {
                        this.wait();
                    }
                    catch (InterruptedException e) {
                        System.err.println("Interrupt received in " +
                            "SocketProbe!");
                        continue;
                    }
                    break;
                }
            }
        }
    }

    /**
     * A very specialized, unorthodox implementation of a map that can
     * check whether a given prefix matches the prefix of any key in
     * the map.
     * 
     * <p>This map has specific knowledge about the format of method
     * signatures encoded in the instrumentation inserted by Sofya.
     * It is intended that only full signatures in this format are
     * added to the map, otherwise the behavior is undefined. It can
     * then query whether a given prefix matches the prefix of any
     * key in the map.</p>
     * 
     * <p>Qeurying "containsKey" operations on this map with only the
     * prefix ("className.methodName") of an encoded signature will
     * test whether any key in the map has a matching prefix. If the
     * "containsKey" request specifies a full encoded signature, it
     * will only return true if the exact signature exists as a key
     * in the map.</p>
     * 
     * <p>Issuing "get" or "remove" requests on this map with only the
     * prefix ("className.methodName") of an encoded signature will
     * retrieve or remove the first key with a matching prefix,
     * if any. Note that multiple keys with the same prefix can be
     * removed with successive calls to "remove" with the same prefix.
     * Issuing a "get" or "remove" request with a full encoded
     * signature will only get or remove a key in the map with an
     * exact matching signature.</p>
     * 
     * <p>This map clearly violates the standard contract of map. It
     * should not be used in any other context whatsoever.</p>
     */
    @SuppressWarnings("serial")
    static final class PrefixHashingMap extends LinkedMap {
        PrefixHashingMap() {
        }

        protected int hash(Object key) {
            String strKey = (String) key;
            String prefix;
            int chopIndex = strKey.indexOf('#');
            if (chopIndex == -1) {
                prefix = strKey;
            }
            else {
                prefix = strKey.substring(0, chopIndex);
            }
            return prefix.hashCode();
        }

        protected boolean isEqualKey(Object key1, Object key2) {
            String strKey1 = (String) key1;
            String strKey2 = (String) key2;
            int chopIndex1 = strKey1.indexOf('#');
            int chopIndex2 = strKey2.indexOf('#');
            String prefix1;
            String prefix2;

            if (chopIndex1 == -1) {
                prefix1 = strKey1;
                prefix2 = strKey2.substring(0, chopIndex2);
            }
            else {
                prefix1 = strKey1;
                prefix2 = strKey2;
            }

            return prefix1.equals(prefix2);
        }
    }
    
    /*************************************************************************
     * Customized implementation of the LRU map to ensure that the hit
     * block data in the byte array for a least-recently-used method is
     * written to the trace before its entry is removed from the map.
     */
    @SuppressWarnings("serial")
    final class LRUTraceMap extends LRUMap {
        /** Temporary cache for pending added entries. */
        private final Object[] pendingAdd = new Object[2];
        private static final int KEY = 0;
        private static final int VALUE = 1;

        /** Accumulator for amount by which we've exceeded the map maximum
            size on addition of entries; triggers allocation of a new map
            if it exceeds a certain threshold. */
        private int overMaxCount = 0;
        /** Temporary map used to pass removed least-recently-used
            coverage arrays to the send method for transmission. */
        private final PrefixHashingMap toRemove = new PrefixHashingMap();
        
        LRUTraceMap(int maxSize) {
            super(maxSize);
        }

        // We override this method to temporarily cache the object to
        // be added. If we reallocate the map as a result of this call,
        // we need to be able to access the pending entry to add it to
        // the newly allocated map, as the LRU removal trigger is called
        // before the object is actually put into this map (and thus it
        // would be missed when we copy the contents of this map into
        // the new map)
        public Object put(Object key, Object value) {
            pendingAdd[KEY] = key;
            pendingAdd[VALUE] = value;
            
            Object ret = super.put(key, value);
            
            pendingAdd[KEY] = null;
            pendingAdd[VALUE] = null;
            
            return ret;
        }
        
        /**
         * This methods is fired when an attempt to add a new coverage
         * array to the map exceeds the per-thread limit represented by
         * the LRU maximum.
         * 
         * <p>This method checks a fixed number of least-recently-used
         * coverage arrays to heuristically determine if they are still
         * in use (on the call stack). Those that are not are removed
         * from the map and transmitted to the event dispatcher.</p>
         * 
         * <p>If we are unable to remove enough coverage arrays to drop
         * back below the limit, it suggests that the thread is in a
         * deep call stack that exceeds the initial per-thread limit.
         * In this case we take the amount by which we are exceeding
         * the limit and add it to an an accumulator. If the accumulation
         * exceeds a given threshold, we will allocate a new larger map.
         * This avoids LRU thrashing in the situation that a thread
         * consistently remains at that (or greater) call stack depth.
         * Note that the design effectively assigns a linear weighting
         * to the depth by which the call stack exceeds the limit on
         * any attempted LRU removal. In other words, the deeper we are
         * exceeding the map limit, the quicker we reach the threshold
         * for reallocating the map.</p>
         * 
         * @param entry Least-recently-used entry returned by the
         * map implementation.
         * 
         * @returns <code>false<code> always; entries, including the
         * one passed to this method, will be removed if possible,
         * however.
         */
        @SuppressWarnings("unchecked")
        protected boolean removeLRU(LinkEntry entry) {
            if (ASSERTS) {
                if (toRemove.size() > 0)
                    throw new AssertionError();
            }
            
            // Mark this entry for removal
            toRemove.put(entry.getKey(), entry.getValue());

            // Mark additional LRUs for removal
            OrderedMapIterator iter = this.orderedMapIterator();
            // Skip first entry -- it is the argument to this method
            iter.next(); 
            for (int i = THREAD_LRU_SCAN_SIZE; i-- > 0; ) {
                iter.next();
                toRemove.put(iter.getKey(), iter.getValue());
            }
            
            // Filter methods that are still on the call stack
            checkStack(toRemove);
            
            if (DEBUG) {
                System.out.println("SPI: lru.pre.size=" + this.size());
                System.out.println("SPI: toRemove.size=" + toRemove.size());
            }
            
            int removeCount = toRemove.size();
            if (removeCount > 0) {
                // Remove methods that were determined to be safe
                iter = toRemove.orderedMapIterator();
                for (int i = removeCount; i-- > 0; ) {
                    iter.next();
                    String removeSig = (String) iter.getKey();
                    this.remove(removeSig);
                    
                    if (DEBUG) {
                        System.out.println("  Removed: " + removeSig);
                    }
                }

                // Transmit trace data for removed methods. If successful,
                // the map will be cleared as a result of this call.
                sendCoverageData(toRemove);
            }
            
            // Check whether we are still over the preferred maximum size
            // for the map. If so, accumulate and test whether we are
            // over the reallocation threshold
            int curSize = this.size();
            int curMax = this.maxSize();
            int newSize = curSize + 1;
            int overMax = newSize - curMax;
            if (overMax > 0) {
                overMaxCount += overMax;
                
                if (overMaxCount > THREAD_LRU_OVERMAX_THRESHOLD) {
                    // Set the new size to the maximum of: the current
                    // maximum size plus the preferred increment size,
                    // or the current real size of the map
                    
                    int tryMax = curMax + THREAD_LRU_EXPAND_INCREMENT;
                    int newMax = (tryMax > newSize) ? tryMax : newSize;
                    LRUTraceMap expanded = new LRUTraceMap(newMax);
                    
                    iter = this.orderedMapIterator();
                    for (int i = curSize; i-- > 0; ) {
                        iter.next();
                        expanded.put(iter.getKey(), iter.getValue());
                    }
                    
                    // Make sure the pending key gets added to the new
                    // map (it is not added by the implementation until
                    // after this method is triggered)
                    if (pendingAdd[KEY] != null) {
                        expanded.put(pendingAdd[KEY], pendingAdd[VALUE]);
                    }
                    
                    if (DEBUG) {
                        System.out.println("SPI: Allocated new map " +
                            System.identityHashCode(expanded) + ", limit " +
                            newMax);
                        System.out.println("  new.contains={");
                        iter = expanded.orderedMapIterator();
                        for (int i = newMax; i-- > 0; ) {
                            iter.next();
                            System.out.println("    " + iter.getKey() +
                                " --> " + iter.getValue());
                        }
                        System.out.println("  }");
                    }
                    
                    // Update the thread-local variable to point to
                    // the new map
                    covArrays.set(expanded, this);
                }
            }
            
            if (DEBUG) {
                System.out.println("SPI: overMaxCount(" +
                    System.identityHashCode(this) + ")=" + overMaxCount);
            }
            
            // Return false so the LRU implementation doesn't try to
            // remove the entry (it will already have been removed
            // above it was possible).
            return false;
        }
        
        /**
         * Heuristically queries whether methods whose trace data have
         * been marked for removal as least-recently-used are still on
         * the call stack.
         * 
         * <p>It is heuristic because stack trace elements don't give us
         * enough information to determine the signature of methods on
         * the stack. Thus we can only match by class and method name.
         * In the worst case, this may simply cause us to hang on to
         * a method or methods that aren't currently on the call stack,
         * but have the same name as a method that is. Probably this
         * occur often in practice.</p>
         * 
         * <p>IMPORTANT: The map that is passed to this method must be
         * a prefix hashing map, so that the partial information derived
         * from stack trace elements can be checked against possibly
         * multiple keys in the map that match the prefix. See the
         * description of this map type for additional details.</p>
         * 
         * @param toRemove Map containing methods marked for transmission
         * and removal of trace data. Keys are the signatures of the
         * methods, which is all this method looks at.
         */
        private void checkStack(PrefixHashingMap toRemove) {
            StackTraceElement[] callStack =
                Thread.currentThread().getStackTrace();
            for (int i = callStack.length - 1; i >= 0; i--) {
                String stackMethod = callStack[i].getClassName() + "@" +
                    callStack[i].getMethodName();
                while (toRemove.containsKey(stackMethod)) {
                    if (DEBUG) {
                        System.out.println("SPI: Instance of " + stackMethod +
                            " on stack, will not discard");
                    }
                    toRemove.remove(stackMethod);
                }
            }
        }
    }
    
    /**
     * Provides thread-local copies of the coverage array maps for
     * each thread in the monitored program.
     * 
     * <p>This class also retains references to all of the maps served
     * to the threads, to be used at shutdown to ensure all pending data
     * gets sent. The shutdown process ensures that application threads
     * have exited, enabling thread-safe access at that point in the
     * probe lifecycle.</p>
     * 
     * <p>Per-thread instances of this class are reclaimed when the
     * associated thread exits, an the coverage data for the thread
     * is eagerly transmitted. (Weak references are used to detect
     * when threads exit).</p>
     */
    final class CoverageArrays<T extends Map<Object, Object>>
            extends ThreadLocal<T> {
        final Map<Map<Object, Object>, Object> threadCovArrays =
            new IdentityHashMap<Map<Object, Object>, Object>();
        final Set<WeakThreadReference<Thread>> cleanupRefs =
            new HashSet<WeakThreadReference<Thread>>();
        final ReferenceQueue<Thread> threadCleanupQ =
            new ReferenceQueue<Thread>();
        
        CoverageArrays() {
        }
        
        @SuppressWarnings("unchecked")
        protected synchronized T initialValue() {
            if (DEBUG) {
                System.out.println("SPI: Allocating thread local map");
            }
            
            Thread curThread = Thread.currentThread();
             
            T covArrays = (T) new LRUTraceMap(THREAD_LRU_MAX);
            
            cleanupRefs.add(new WeakThreadReference<Thread>(curThread,
                threadCleanupQ, covArrays));
            
            threadCovArrays.put(covArrays, null);
            
            return covArrays;
        }
        
        public synchronized void set(T value, T oldValue) {
            threadCovArrays.remove(oldValue);
            
            Thread curThread = Thread.currentThread();
            
            // Clear the old weak reference to the thread, so that
            // the old map becomes reclaimable (its contents should
            // have been copied to the new map), and the thread
            // exit handler doesn't attempt to process the reference.
            Iterator<WeakThreadReference<Thread>> iter =
                cleanupRefs.iterator();
            while (iter.hasNext()) {
                WeakThreadReference<Thread> ref = iter.next();
                if (ref.get() == curThread) {
                    ref.clear();
                    iter.remove();
                    
                    if (DEBUG) {
                        System.out.println("SPI: Removed defunct " +
                            "weak reference");
                    }
                    
                    break;
                }
            }
        
            cleanupRefs.add(new WeakThreadReference<Thread>(curThread,
                threadCleanupQ, value));
            
            threadCovArrays.put(value, null);
            
            super.set(value);
        }
    }
    
    /**
     * Reads from the thread reference queue to detect when threads
     * have exited. Upon thread exit, the associated coverage arrays
     * are transmitted, and the coverage arrays map is released for
     * garbage collection.
     */
    final class ThreadExitHandler extends Thread {
        private CoverageArrays<Map<Object, Object>> probeCovArrays;
        private volatile boolean stop;
        
        private ThreadExitHandler() {
            throw new AssertionError("Illegal constructor");
        }
        
        ThreadExitHandler(CoverageArrays<Map<Object, Object>> covArrays) {
            super("sofya-thread-exit-handler");
            this.probeCovArrays = covArrays;
        }
        
        @SuppressWarnings("unchecked")
        public void run() {
            while (!stop) {
                WeakThreadReference<Thread> pendingRef;
                try {
                    pendingRef = (WeakThreadReference)
                        probeCovArrays.threadCleanupQ.remove();
                }
                catch (InterruptedException e) {
                    continue;
                }
                
                sendCoverageData((OrderedMap) pendingRef.covArrays);
                
                probeCovArrays.cleanupRefs.remove(pendingRef);
                probeCovArrays.threadCovArrays.remove(pendingRef.covArrays);
            }
            
            if (DEBUG) {
                System.out.println("SPI: thread exit handler exiting");
            }
        }
        
        void halt() {
            this.stop = true;
            this.interrupt();
        }
    }
    
    /**
     * Holds a weak reference to a thread, allowing the thread exit
     * handler to detect when a thread has exited by reading from
     * the queue with which these references are registered.
     * 
     * <p>A thread reference carries a strong reference to the
     * coverage arrays map for the thread, so that the coverage
     * data can be transmitted before the map is released.</p>
     */
    static final class WeakThreadReference<T extends Thread>
            extends WeakReference<T> {
        Map<Object, Object> covArrays;
        
        WeakThreadReference(T referent, ReferenceQueue<? super T> q,
                Map<Object, Object> covArrays) {
            super(referent, q);
            this.covArrays = covArrays;
        }
    }
    
    /**
     * Stores the signature and number of trace objects for a new
     * method recorded to the sequence array. This information, for
     * entirely new methods, is transmitted at the head of sequence
     * trace data packets, along with the string table index associated
     * with the signature, for use by the event dispatcher in
     * reconstructing this information from the sequence array.
     * The object count supports coverage tracing using sequence trace
     * instrumentation.
     */
    static final class NewMethodData {
        public final String signature;
        public final int objCount;
        
        NewMethodData() {
            throw new AssertionError("Illegal constructor");
        }
        
        NewMethodData(String signature, int objCount) {
            this.signature = signature;
            this.objCount = objCount;
        }
    }
    
    /**
     * Helper thread to properly trigger the call to finish upon
     * exit of the "main" thread when the subject is an old version
     * of Galileo in which the SocketProbe does not utilize a
     * finish method that can be chained to Sofya's finish method.
     */
    final class MainWaiter extends Thread {
        private final Thread mainThread;
        
        MainWaiter() {
            throw new AssertionError("Illegal constructor");
        }
        
        MainWaiter(Thread mainThread) {
            this.mainThread = mainThread;
        }
        
        public void run() {
            try {
                mainThread.join();
            }
            catch (InterruptedException e) {
                System.err.println("Interrupted waiting for " +
                    "main thread to exit");
            }
            SocketProbeImpl.this.finish();
        }
    }
}
