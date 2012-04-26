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

package sofya.apps.atomicity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import sofya.ed.semantic.EventSelectionFilter;
import sofya.base.MethodSignature;
import sofya.base.exceptions.IncompleteClasspathException;
import static sofya.apps.AtomicityChecker.ENABLE_ESCAPE_DETECTION;

import org.apache.bcel.generic.ObjectType;

import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TObjectByteHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntObjectIterator;

/**
 * Implementation of the happens-before analysis described by Wang and
 * Stoller, with the exception that vector clocks are used. The vector
 * clock implementation is an adaptation of the hybrid limited
 * happens-before detection algorithm described by O&apos;Callahan
 * and Choi (the lockset portion is factored out in a manner consistent
 * with the multi-lockset algorithm described by Wang and Stoller).
 *
 * <p>Additionally note that this implementation uses &quot;sparse&quot;
 * vector clocks: a clock is a set of mappings from each thread ID to the
 * current clock value for that thread. The map implementation returns 0
 * for an absent mapping, thus a clock value is only explicitly stored
 * if it is greater than zero. This improves memory efficiency by
 * avoiding allocating potentially large spans of empty elements, as would
 * be necessary with arrays or vectors.</p>
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
@SuppressWarnings("unchecked")
public final class HappenBeforeChecker extends EventSelectionFilter {
    /** Stores the current thread vector clocks for all active threads. */
    private TIntObjectHashMap threadClocks = new TIntObjectHashMap();

    /** A double map that maps object IDs to field names to sets of memory
        read events on those fields. */
    private TLongObjectHashMap memReadEvents = new TLongObjectHashMap();
    /** A double map that maps object IDs to field names to sets of memory
        write events on those fields. */
    private TLongObjectHashMap memWriteEvents = new TLongObjectHashMap();

    /** Maps object IDs of threads that are being started to the current
        vector clock of the thread that called start on the new thread;
        the vector clock is used as the initial clock for the started
        thread. */
    private TLongObjectHashMap pendingStarts = new TLongObjectHashMap();
    /** Maps object IDs of threads to sets of threads that have joined
        that thread. When the thread exits, it transmits its vector
        clock value for itself to all of the joined threads. Note that
        timed joins will not receive the joined thread's clock value if
        the join times out before the joined thread dies (as expected,
        since subsequent events are still concurrent). */
    private TLongObjectHashMap waitingJoins = new TLongObjectHashMap();

    /** Dynamic escape detector used to determine when to start recording
        clock events for memory accesses. */
    private DynamicEscapeDetector escapeDetector;

    /** Cache to record which classes are thread classes. */
    private TObjectByteHashMap threadClasses = new TObjectByteHashMap();
    /** Cache value indicating that we don't yet know if a class is
        a thread class. */
    private static final byte UNKNOWN_CLASS = 0;
    /** Cache value indicating that a class is a thread class. All
        other values except <code>UNKNOWN_CLASS</code> are taken to
        mean that a class is not a thread class. */
    private static final byte THREAD_CLASS = 1;

    /** Special constant used to indicate that a memory access occurred
        on a static field (simplifies double-map implementation). */
    private static final long STATIC_FIELD = -1;

    /** Constant for the <code>java.lang.Thread</code> type, used for
        determining which classes are thread classes. */
    private static final ObjectType THREAD_TYPE =
        new ObjectType("java.lang.Thread");

    /** If a thread cannot determine its parent (the thread that caused it
        to be started), a warning is issued because the vector clock cannot
        be initialized properly (a heuristic is used). This set contains
        threads for which those warnings should be suppressed (such as
        system threads not started in user code, and the main thread). */
    private static final Set<Object> NO_WARN_THREADS;

    static {
        // Sun JVM specific - could be modified for other VMs as necessary.
        // Ideally, this should eventually be moved to a configuration file.
        Set<Object> noWarnThreads = new THashSet();
        noWarnThreads.add("Signal Dispatcher");
        noWarnThreads.add("main");
        noWarnThreads.add("DestroyJavaVM");
        NO_WARN_THREADS = Collections.unmodifiableSet(noWarnThreads);
    }

    private HappenBeforeChecker() {
    }

    /**
     * Creates a new happens-before checker.
     *
     * @param escapeDetector Dynamic escape detector that is used to determine
     * when to start recording clock events for memory accesses. Clock events
     * are not recorded for memory accesses to objects that have not yet
     * been reported as escaped from their creating thread.
     */
    public HappenBeforeChecker(DynamicEscapeDetector escapeDetector) {
        this.escapeDetector = escapeDetector;
    }

    /**
     * Reports whether a memory access performed by the current thread could
     * potentially be concurrent with other accesses to the same memory
     * location.
     *
     * <p>The current thread's vector clock is compared against the stored
     * clock value for previously recorded events on the same memory location,
     * as described by O&apos;Callahan and Choi in their hybrid technique.
     * It is provable that for determining basic happens-before causal
     * relationships, the full vector clock does not need to be stored for
     * the previously recorded events, and their clock values for other
     * threads are implicitly taken to be zero.</p>
     *
     * <p>If the current memory access is a read event, it is only checked for
     * concurrency with other write events, otherwise the current memory
     * access is checked for concurrency with all previous accesses.</p>
     *
     * @param threadId ID of the thread that performed the memory access.
     * @param objId ID of the object owning the memory location (field);
     * -1 signifies a static field.
     * @param fieldName Name of the memory location.
     * @param isWrite Flag indicating whether the memory access is a write
     * access.
     *
     * @return <code>true</code> if the current memory access is possibly
     * concurrent with previous accesses to the same memory location
     * (e.g. there exists at least one previous access to the same memory
     * location that did not necessarily have to occur before the current
     * access to that memory location).
     */
    public boolean isConcurrent(int threadId, long objId,
            String fieldName, boolean isWrite) {
        // System.out.println("isConcurrent: [" + threadId + "] " + objId +
        //     "::" + fieldName);

        TIntIntHashMap threadClock =
            (TIntIntHashMap) threadClocks.get(threadId);

        // If current event is read or write, always check for concurrency
        // with previous write events
        if (isConcurrent(threadId, objId, fieldName,
                threadClock, memWriteEvents)) {
            return true;
        }

        // Only check for concurrency with previous read events if the
        // current event is a write event
        if (isWrite && isConcurrent(threadId, objId, fieldName,
                threadClock, memReadEvents)) {
            return true;
        }

        return false;
    }

    /**
     * Implementation of the concurrency check.
     */
    private boolean isConcurrent(int threadId, long objId,
            String fieldName, TIntIntHashMap threadClock,
            TLongObjectHashMap memEvents) {
        THashMap fldMemTuples = (THashMap) memEvents.get(objId);
        if (fldMemTuples == null) {
            return false;
        }
        Set memTuples = (Set) fldMemTuples.get(fieldName);
        if (memTuples == null) {
            return false;
        }

        int eventCnt = memTuples.size();
        Iterator eventIterator = memTuples.iterator();
        for (int i = eventCnt; i-- > 0; ) {
            MemoryEvent event = (MemoryEvent) eventIterator.next();
            if (event.threadId == threadId) {
                continue;
            }

            // Check whether the current event is concurrent with the
            // stored event
            boolean eventGTclock = false;
            boolean existGreaterClockElem = false;

            // We iterate over the key set of the map storing all of the
            // thread clocks, since the sparse vector clock for an
            // individual thread does not explicitly store any clock
            // values that are zero (the thread element would not be
            // returned by an iteration over the thread local clock).
            int elemCnt = threadClocks.size();
            TIntObjectIterator clockIterator = threadClocks.iterator();
            for (int j = elemCnt; j-- > 0; ) {
                clockIterator.advance();
                int curThreadId = clockIterator.key();
                int curClockVal = threadClock.get(curThreadId);

                if (curThreadId == event.threadId) {
                    eventGTclock = (event.timestamp > curClockVal);
                }
                else {
                    existGreaterClockElem = (curClockVal > 0);
                }

                if (eventGTclock && existGreaterClockElem) {
                    return true;
                }
            }

            if (!(existGreaterClockElem || eventGTclock)) {
                return true;
            }
        }

        return false;
    }

    public void threadStartEvent(ThreadData td) {
        int threadId = td.getId();
        long threadObjId = td.getObjectId();

        // Inherit the clock from the thread that started this thread.
        // This is necessary to prevent events in the new thread from
        // being marked as concurrent with events on the parent thread
        // that happened before this thread was started, which would be
        // the case if the current thread's vector clock initialized its
        // value for the parent thread to zero.

        TIntIntHashMap threadClock =
            (TIntIntHashMap) pendingStarts.remove(threadObjId);

        if (threadClock == null) {
            // If we can't figure out who started this thread, initialize
            // the vector clock such that each element is the value that
            // the current vector clock for the corresponding thread
            // is reporting. This is of course a heuristic, and we hope
            // that it doesn't happen at all for threads "that we care
            // about" other than main (which will be initialized to all
            // zeros as desired).
            String threadName = td.getName();
            if (!NO_WARN_THREADS.contains(threadName)) {
                System.err.println("WARNING: Thread \"" + threadName + "\" " +
                    "parent is unknown");
            }
            threadClock = new TIntIntHashMap();
            int tCnt = threadClocks.size();
            TIntObjectIterator iterator = threadClocks.iterator();
            for (int i = tCnt; i-- > 0; ) {
                iterator.advance();
                int curThreadId = iterator.key();
                TIntIntHashMap curClock = (TIntIntHashMap) iterator.value();

                threadClock.put(curThreadId, curClock.get(curThreadId));
            }
        }

        threadClock.put(threadId, 1);
        threadClocks.put(threadId, threadClock);
    }

    public void threadDeathEvent(ThreadData td) {
        int threadId = td.getId();
        long threadObjId = td.getObjectId();

        TIntIntHashMap clock = (TIntIntHashMap) threadClocks.remove(threadId);
        //if (clock == null) {
        //    System.err.println("HappenBeforeChecker:ignoring unknown " +
        //        "thread \"" + td.getName() + "\"");
        //    return;
        //}
        
        int curClockVal = clock.get(threadId);

        THashSet joinPool = (THashSet) waitingJoins.remove(threadObjId);
        if (joinPool == null) {
            return;
        }

        int size = joinPool.size();
        Iterator iterator = joinPool.iterator();
        for (int i = size; i-- > 0; ) {
            TIntIntHashMap waitingClock = (TIntIntHashMap) iterator.next();
            waitingClock.put(threadId, curClockVal);
        }
    }

    public void virtualCallEvent(ThreadData td, CallData cd) {
        if (!cd.canGetArguments()) {
            return;
        }

        MethodSignature callSig = cd.getCalledSignature();
        String receiverClass = callSig.getClassName();

        // Check whether the called method is a thread class. The
        // result is cached after the first check to avoid repeatedly
        // creating temporary objects and doing the subclass query.
        byte classFlag = threadClasses.get(receiverClass);
        switch (classFlag) {
        case UNKNOWN_CLASS:
            ObjectType receiverType = new ObjectType(receiverClass);
            try {
                if (receiverType.subclassOf(THREAD_TYPE)) {
                    threadClasses.put(receiverClass, THREAD_CLASS);
                }
                else {
                    threadClasses.put(receiverClass, (byte) -1);
                    return;
                }
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
            break;
        case THREAD_CLASS:
            break;
        default:
            return;
        }

        int threadId = td.getId();
        String calledMethodName = callSig.getMethodName();

        if (calledMethodName.equals("start")) {
            TIntIntHashMap clock = (TIntIntHashMap) threadClocks.get(threadId);
            long threadObjId = cd.getArguments().getThis().uniqueID();

            pendingStarts.put(threadObjId, (TIntIntHashMap) clock.clone());

            if (!clock.increment(threadId)) {
                throw new AssertionError("Failure in trove: could not " +
                    "increment clock value");
            }
        }
        else if (calledMethodName.equals("join")) {
            TIntIntHashMap clock = (TIntIntHashMap) threadClocks.get(threadId);
            long threadObjId = cd.getArguments().getThis().uniqueID();

            Set<Object> joinPool = (Set) waitingJoins.get(threadObjId);
            if (joinPool == null) {
                joinPool = new THashSet();
                waitingJoins.put(threadObjId, joinPool);
            }
            joinPool.add(clock);

            if (!clock.increment(threadId)) {
                throw new AssertionError("Failure in trove: could not " +
                    "increment clock value");
            }
        }
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        int threadId = td.getId();
        String fldName = fd.getFullName();

        TIntIntHashMap threadClock =
            (TIntIntHashMap) threadClocks.get(threadId);

        Set<Object> memTuples;
        Map<Object, Set<Object>> fldMemTuples =
            (THashMap) memReadEvents.get(STATIC_FIELD);
        if (fldMemTuples == null) {
            fldMemTuples = new THashMap();
            memReadEvents.put(STATIC_FIELD, fldMemTuples);
            memTuples = new THashSet();
            fldMemTuples.put(fldName, memTuples);
        }
        else {
            memTuples = fldMemTuples.get(fldName);
            if (memTuples == null) {
                memTuples = new THashSet();
                fldMemTuples.put(fldName, memTuples);
            }
        }

        memTuples.add(new MemoryEvent(threadId, threadClock.get(threadId)));
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objId = od.getId();

        if (ENABLE_ESCAPE_DETECTION) {
            if (!escapeDetector.isEscaped(objId)) {
                return;
            }
        }

        int threadId = td.getId();
        String fldName = fd.getFullName();

        TIntIntHashMap threadClock =
            (TIntIntHashMap) threadClocks.get(threadId);

        Set<Object> memTuples;
        Map<Object, Set<Object>> fldMemTuples =
            (THashMap) memReadEvents.get(objId);
        if (fldMemTuples == null) {
            fldMemTuples = new THashMap();
            memReadEvents.put(objId, fldMemTuples);
            memTuples = new THashSet();
            fldMemTuples.put(fldName, memTuples);
        }
        else {
            memTuples = fldMemTuples.get(fldName);
            if (memTuples == null) {
                memTuples = new THashSet();
                fldMemTuples.put(fldName, memTuples);
            }
        }

        memTuples.add(new MemoryEvent(threadId, threadClock.get(threadId)));
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        int threadId = td.getId();
        String fldName = fd.getFullName();

        TIntIntHashMap threadClock =
            (TIntIntHashMap) threadClocks.get(threadId);

        Set<Object> memTuples;
        Map<Object, Set<Object>> fldMemTuples =
            (THashMap) memWriteEvents.get(STATIC_FIELD);
        if (fldMemTuples == null) {
            fldMemTuples = new THashMap();
            memWriteEvents.put(STATIC_FIELD, fldMemTuples);
            memTuples = new THashSet();
            fldMemTuples.put(fldName, memTuples);
        }
        else {
            memTuples = fldMemTuples.get(fldName);
            if (memTuples == null) {
                memTuples = new THashSet();
                fldMemTuples.put(fldName, memTuples);
            }
        }

        memTuples.add(new MemoryEvent(threadId, threadClock.get(threadId)));
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objId = od.getId();

        if (ENABLE_ESCAPE_DETECTION) {
            if (!escapeDetector.isEscaped(objId)) {
                return;
            }
        }

        int threadId = td.getId();
        String fldName = fd.getFullName();

        TIntIntHashMap threadClock =
            (TIntIntHashMap) threadClocks.get(threadId);

        Set<Object> memTuples;
        Map<Object, Set<Object>> fldMemTuples =
            (THashMap) memWriteEvents.get(objId);
        if (fldMemTuples == null) {
            fldMemTuples = new THashMap();
            memWriteEvents.put(objId, fldMemTuples);
            memTuples = new THashSet();
            fldMemTuples.put(fldName, memTuples);
        }
        else {
            memTuples = fldMemTuples.get(fldName);
            if (memTuples == null) {
                memTuples = new THashSet();
                fldMemTuples.put(fldName, memTuples);
            }
        }

        memTuples.add(new MemoryEvent(threadId, threadClock.get(threadId)));
    }

    ////////////////////////////////////////////////////////////////////////
    // The JDI only guarantees that the ID of a mirrored object is unique
    // if it has not been garbage collected. So whenever a new object is
    // constructed, we clear any stored memory event tuples associated
    // with the object ID to make sure the new object doesn't erroneously
    // "inherit" the memory event history of the old object.
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        long objId = od.getId();
        memReadEvents.remove(objId);
        memWriteEvents.remove(objId);
    }

    void printClocks() {
        int size = threadClocks.size();
        TIntObjectIterator iterator = threadClocks.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            System.out.println(iterator.key() + " : " +
                clockToString((TIntIntHashMap) iterator.value()));
        }
    }

    private static String clockToString(TIntIntHashMap clock) {
        StringBuffer sb = new StringBuffer("[ ");

        int size = clock.size();
        TIntIntIterator iterator = clock.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            sb.append(iterator.key());
            sb.append(":");
            sb.append(iterator.value());
            sb.append(" ");
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Checks two full vector clocks for concurrency.
     *
     * <p>This method is retained in case it should prove useful in
     * the future.</p>
     */
    @SuppressWarnings("unused")
    private static boolean isConcurrent(TIntIntHashMap clkA,
            TIntIntHashMap clkB) {
        boolean existAgtB = false;
        boolean existBgtA = false;

        int size = clkA.size();
        TIntIntIterator iterator = clkA.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            int clkAThread = iterator.key();
            int clkATime = iterator.value();
            int clkBTime = clkB.get(clkAThread);

            existAgtB = existAgtB || (clkATime > clkBTime);
            existBgtA = existBgtA || (clkBTime > clkATime);

            if (existAgtB && existBgtA) {
                return true;
            }
        }

        size = clkB.size();
        iterator = clkB.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            int clkBThread = iterator.key();
            int clkBTime = iterator.value();
            int clkATime = clkA.get(clkBThread);

            existAgtB = existAgtB || (clkATime > clkBTime);
            existBgtA = existBgtA || (clkBTime > clkATime);

            if (existAgtB && existBgtA) {
                return true;
            }
        }

        return !(existAgtB || existBgtA);
    }
}

class MemoryEvent {
    public final int threadId;
    public final int timestamp;
    private final int hashCode;

    private MemoryEvent() {
        throw new AssertionError("Illegal constructor");
    }

    public MemoryEvent(int threadId, int timestamp) {
        this.threadId = threadId;
        this.timestamp = timestamp;

        // Pre-compute hashcode; all constants in the following equation
        // are primes or products of primes.
        this.hashCode = 10933 + (threadId * 1439) + timestamp;
    }

    public boolean equals(Object o) {
        // We do not expect to ever receive a non-type compatible argument,
        // so "handling" an exception we never expect to see is the
        // fastest solution
        try {
            MemoryEvent otherEvent = (MemoryEvent) o;
            return ((otherEvent.threadId == this.threadId) &&
                (otherEvent.timestamp == this.timestamp));
        }
        catch (ClassCastException e) {
            return false;
        }
    }

    public int hashCode() {
        return hashCode;
    }
}
