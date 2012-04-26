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

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import sofya.base.SConstants;
import sofya.ed.CoverageListenerManager;
import static sofya.ed.structural.Sofya$$Probe$10_36_3676__.SequenceProbe.sequenceArray;

import org.apache.commons.collections.OrderedMap;
import org.apache.commons.collections.OrderedMapIterator;
import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.collections.map.LinkedMap;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;

/**
 * Base class to receive and process probes to produce coverage traces
 * for a JUnit event dispatcher.
 * 
 * @author Alex Kinneer
 * @version 11/21/2006
 */
public abstract class AbstractJUnitCoverageProcessingStrategy
        extends AbstractJUnitProcessingStrategy {
    // There is some code cloning in this class from the SocketProbeImpl;
    // Not an ideal situation, but these are implementation details that
    // we just don't want exposed in the public API

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
    private ThreadExitHandler threadExitHandler = new ThreadExitHandler();
    
    /** Listener manager that serves the listeners to which the coverage
        events will be dispatched. */
    protected CoverageListenerManager listenerManager;

    /** Conditional compilation flag to control whether debug outputs are
        present in the class file. */
    private static final boolean DEBUG = false;
    /** Conditional compilation flag to control whether asserts are
        present in the class file. */
    private static final boolean ASSERTS = true;

    protected AbstractJUnitCoverageProcessingStrategy() {
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
    
    @Override
    protected void setup(int instMode) {
        checkInstrumentationMode(instMode);

        this.instMode = instMode;

        // Initialize data structures
        switch (instMode) {
        case SConstants.INST_COMPATIBLE:
            threadExitHandler = null;
            break;
        case SConstants.INST_OPT_NORMAL:
            covArrays = new CoverageArrays<Map<Object, Object>>();
            if (threadExitHandler != null) {
                threadExitHandler.setCoverageArrays(covArrays);
                threadExitHandler.start();
            }
            else {
                // This happens under some circumstances, such
                // as in the case of shutdown hooks
                System.err.println("NOTE: Instrumented code is executing " +
                    "after completion of the test suite");
            }
            break;
        case SConstants.INST_OPT_SEQUENCE:
            sequenceArray = new int[SEQUENCE_ARRAY_SIZE];
            indexToNameMap = new TIntObjectHashMap();
            nameToIndexMap = new TObjectIntHashMap();
            threadExitHandler = null;
            break;
        default:
            throw new IllegalArgumentException("Subject contains " +
                "unrecognized type of instrumentation");
        }
    }
    
    public void release() {
        super.release();
        
        if (threadExitHandler != null) {
            threadExitHandler.halt();
            threadExitHandler = null;
        }
    }

    public void newTest(int testNum) {
        listenerManager.newEventStream(testNum);
        
        if (threadExitHandler != null) {
            threadExitHandler.newTest();
        }
    }

    public void endTest(int testNum) {
        // Ensure all pending trace data is captured
        switch (instMode) {
        case SConstants.INST_COMPATIBLE:
            break;
        case SConstants.INST_OPT_NORMAL:
            // Wait for any remaining threads spawned by the test
            // case to exit
            Thread curThread = Thread.currentThread();
            Iterator<WeakThreadReference<Thread>> ts =
                covArrays.cleanupRefs.iterator();
            while (ts.hasNext()) {
                Thread refThread = ts.next().get();
                if ((refThread != null) && (refThread != curThread)
                        && !refThread.isDaemon()) {
                    try {
                        refThread.join();
                    }
                    catch (InterruptedException e) {
                        System.err.println("Interrupted waiting for " +
                            "application thread \"" + refThread.getName() +
                            "\" to exit");
                    }
                }
                
                ts.remove();
            }
            
            if (ASSERTS) {
                assert covArrays.cleanupRefs.size() == 0;
            }
            
            processFinalCoverageData();
            
            // Clear coverage data for the current thread
            if (instMode == SConstants.INST_OPT_NORMAL) {
                covArrays.remove();
            }
            
            break;
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
        
        listenerManager.commitCoverageResults(testNum);
        
        isInstrumented = false;
    }
    
    public boolean checkInstrumented() {
        return isInstrumented;
    }
    
    /**
     * Performs the actual processing of a set of coverage arrays,
     * dispatching the coverage information to the coverage listener(s).
     * 
     * @param covArrays Set of coverage arrays ready for processing
     * and dispatch to listeners.
     */
    protected abstract void processCoverageData(OrderedMap covArrays);
    
    /**
     * Performs final processing of all coverage arrays upon the
     * completion of a test case.
     * 
     * <p>All pending coverage arrays for all threads are processed
     * and dispatched to listeners.</p>
     */
    @SuppressWarnings("unchecked")
    private void processFinalCoverageData() {
        Iterator<Map<Object, Object>> covArrayIter =
            covArrays.threadCovArrays.keySet().iterator();
        int size = covArrays.threadCovArrays.size();
        for (int i = size; i-- > 0; ) {
            processCoverageData((OrderedMap) covArrayIter.next());
            covArrayIter.remove();
        }
        
        if (ASSERTS) {
            assert covArrays.threadCovArrays.size() == 0;
        }
    }

    public byte[] getObjectArray(String mSignature, int objCount) {
        if (instMode != SConstants.INST_OPT_NORMAL) {
            if (instMode == -1) {
                setup(SConstants.INST_OPT_NORMAL);
            }
            else {
                throw new IllegalStateException("Subject contains " +
                    "inconsistent instrumentation");
            }
        }
        
        isInstrumented = true;

        Map<Object, Object> covMap = covArrays.get();
        
        if (DEBUG) {
            System.out.println("Got provider map " +
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
    
    // Remainder mostly copied from SocketProbeImpl...
    
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
                processCoverageData(toRemove);
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
        private volatile int stateFlag;
        private Object stateLock = new Object();
        
        private static final int STATE_NEW_TEST = 1;
        private static final int STATE_END_TEST = 2;
        private static final int STATE_HALT     = 3;
        
        private static final boolean DEBUG = false;
        
        private java.io.PrintStream fLog;
        
        {
            if (DEBUG) {
                try {
                    fLog = new java.io.PrintStream(
                        new java.io.BufferedOutputStream(
                        new java.io.FileOutputStream("exit_handler.log")));
                }
                catch (Exception e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }
        
        ThreadExitHandler() {
        }
        
        ThreadExitHandler(CoverageArrays<Map<Object, Object>> covArrays) {
            super("sofya-thread-exit-handler");
            setCoverageArrays(covArrays);
        }
        
        void setCoverageArrays(CoverageArrays<Map<Object, Object>> covArrays) {
            this.probeCovArrays = covArrays;
        }
        
        @Override
        public void start() {
            if (probeCovArrays == null) {
                throw new IllegalStateException("Coverage arrays have " +
                    "not been specified");
            }
            super.start();
        }
        
        @SuppressWarnings("unchecked")
        public void run() {
            if (!waitOnNewTest()) return;
            
            runLoop:
            while (true) {
                WeakThreadReference<Thread> pendingRef;
                try {
                    pendingRef = (WeakThreadReference)
                        probeCovArrays.threadCleanupQ.remove();
                }
                catch (InterruptedException e) {
                    if (DEBUG) {
                        fLog.println("thread-exit-handler: read queue " +
                            "interrupted");
                    }
                    
                    switch (stateFlag) {
                    case STATE_END_TEST:
                        if (!waitOnNewTest()) {
                            break runLoop;
                        }
                        break;
                    case STATE_HALT:
                        break runLoop;
                    }
                    
                    if (DEBUG) {
                        fLog.println("thread-exit-handler: reading queue");
                    }
                    continue;
                }
                
                if (DEBUG) {
                    fLog.println("thread-exit-handler: processing coverage " +
                        "array");
                }
                
                processCoverageData((OrderedMap) pendingRef.covArrays);
                
                probeCovArrays.cleanupRefs.remove(pendingRef);
                probeCovArrays.threadCovArrays.remove(pendingRef.covArrays);
            }
            
            if (DEBUG) {
                fLog.println("thread-exit-handler: exiting");
            }
        }
        
        private final boolean waitOnNewTest() {
            if (DEBUG) {
                fLog.println("thread-exit-handler: checking flag for " +
                    "new test signal");
            }
            
            synchronized (stateLock) {
                if (stateFlag != STATE_NEW_TEST) {
                    if (DEBUG) {
                        fLog.println("thread-exit-handler: notifying " +
                            "endTest pending threads");
                    }
                    stateLock.notifyAll();
                    
                    if (DEBUG) {
                        fLog.println("thread-exit-handler: waiting for " +
                            "new test");
                    }
                    try {
                        stateLock.wait();
                    }
                    catch (InterruptedException e) {
                        if (stateFlag != STATE_HALT) {
                            System.err.println("Thread exit handler " +
                                "interrupted");
                        }
                        return false;
                    }
                }
            }
            return true;
        }
        
        void newTest() {
            if (DEBUG) {
                fLog.println("thread-exit-handler: new test signaled");
            }
            
            synchronized (stateLock) {
                this.stateFlag = STATE_NEW_TEST;
                this.stateLock.notifyAll();
            }
        }
        
        void endTest() {
            if (DEBUG) {
                fLog.println("thread-exit-handler: end of test signaled");
            }
            
            synchronized (stateLock) {
                this.stateFlag = STATE_END_TEST;
                this.interrupt();
                
                try {
                    if (instMode == SConstants.INST_OPT_NORMAL) {
                        if (DEBUG) {
                            fLog.println("thread-exit-handler: waiting on " +
                                "end of test");
                        }
                        
                        this.stateLock.wait();
                    }
                }
                catch (InterruptedException e) {
                    System.err.println("thread-exit-handler: end of test " +
                        "wait interrupted");
                }
            }
        }
        
        void halt() {
            synchronized (stateLock) {
                this.stateFlag = STATE_HALT;
                this.interrupt();
            }
            
            if (DEBUG) {
                if (fLog.checkError()) {
                    System.err.println("WARNING: Error writing thread " +
                        "exit handler log file");
                }
                fLog.close();
            }
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
}
