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

import java.util.Set;

import static sofya.apps.AtomicityChecker.ENABLE_HAPPENS_BEFORE;

import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TLongObjectHashMap;

/**
 * This class implements a default global event classification policy.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public class DefaultEventClassifier extends EventClassifier {

    ////////////////////////////////////////////////////////////////////
    // There is a data flow between the various data structures below:
    // Initially if a field is still known to be thread-local or
    // read-only, it remains in the field read and/or write maps. Once
    // a field is known to escape its thread and not be read only it
    // is transferred to the non thread local set, and is subsequently
    // only checked for a race condition. Once a race is detected, it
    // is moved to the permanent non movers set for maximum subsequent
    // lookup efficiency.
    ////////////////////////////////////////////////////////////////////
    // Only one of the following sets of data structures is instantiated
    // depending on whether we are operating with object sensitivity.
    // The object-sensitive field read and write maps are actually
    // double maps that map the object IDs to maps from field names to
    // the associated thread information. The other two simply maintain
    // mappings from object IDs to associated fields. This allows us
    // to quickly purge stale data for garbage collected objects if
    // we see a new object created that has the same ID as a previously
    // known object.

    /** Records object-sensitive mappings between fields and the first
        thread to read them. */
    private TLongObjectHashMap os_fieldReads;
    /** Records object-sensitive mappings between fields and the first
        thread to write them. */
    private TLongObjectHashMap os_fieldWrites;
    /** Object-sensitive set of fields that have been found to escape
        their creating thread, but which may be both-movers if no
        race has been detected. */
    private TLongObjectHashMap os_nonThreadLocalFields;
    /** Object-sensitive set of fields that have been determined to be
        non-movers, cached for quick lookup. */
    private TLongObjectHashMap os_nonMoverFields;

    /** Records object-insensitive mappings between fields and the first
        thread to read them. */
    private TObjectIntHashMap fieldReads;
    /** Records object-insensitive mappings between fields and the first
        thread to write them. */
    private TObjectIntHashMap fieldWrites;
    /** Object-insensitive set of fields that have been found to escape
        their creating thread, but which may be both-movers if no
        race has been detected. */
    private Set<Object> nonThreadLocalFields;
    /** Object-insensitive set of fields that have been determined to be
        non-movers, cached for quick lookup. */
    private Set<Object> nonMoverFields;

    /** Implementation of the Wang and Stoller multi-lockset race detection
        algorithm for fields. */
    private MultiLocksetRaceDetector raceDetector;
    /** Implementation of the happens-before analysis described by Wang
        and Stoller. */
    private HappenBeforeChecker hbChecker;

    /** Specifies whether race detection should view every field of every
        object instance as distinct. */
    private boolean objSensitive;

    // private TIntHashSet liveThreads = new TIntHashSet();

    private DefaultEventClassifier() {
    }

    /**
     * Creates a new default event classifier.
     *
     * @param objectSensitive Specifies whether race detection should
     * treat field of every object instance as distinct.
     * @param raceDetector Multi-lockset race detector to be used to refine
     * race detection.
     * @param hbChecker Happens-before checker used to refine race
     * detection.
     */
    @SuppressWarnings("unchecked")
    public DefaultEventClassifier(boolean objectSensitive,
            MultiLocksetRaceDetector raceDetector,
            HappenBeforeChecker hbChecker) {
        this.objSensitive = objectSensitive;
        this.raceDetector = raceDetector;
        this.hbChecker = hbChecker;

        if (objectSensitive) {
            os_fieldReads = new TLongObjectHashMap();
            os_fieldWrites = new TLongObjectHashMap();
            os_nonThreadLocalFields = new TLongObjectHashMap();
            os_nonMoverFields = new TLongObjectHashMap();
        }
        else {
            fieldReads = new TObjectIntHashMap();
            fieldWrites = new TObjectIntHashMap();
            nonThreadLocalFields = new THashSet();
            nonMoverFields = new THashSet();
        }
    }

    @SuppressWarnings("unchecked")
    private void classifyFieldEvent(ThreadData td, ObjectData od, FieldData fd,
            boolean isWrite) {
        int threadId = td.getId();
        String fieldName = fd.getFullName();

        if (objSensitive) {
            long objId = (od == null) ? -1 : od.getId();

            Set<Object> nonMovers = (Set) os_nonMoverFields.get(objId);
            if ((nonMovers != null)
                    && nonMovers.contains(fieldName)) {
                if (ENABLE_HAPPENS_BEFORE
                        && hbChecker.isConcurrent(threadId, objId,
                                fieldName, isWrite)) {
                    eventClass = EventClass.NON_MOVER;
                }
                else {
                    eventClass = EventClass.BOTH_MOVER;
                }
                return;
            }

            Set<Object> nonLocals = (Set) os_nonThreadLocalFields.get(objId);
            if ((nonLocals != null)
                    && nonLocals.contains(fieldName)) {
                eventClass = EventClass.NON_MOVER;
            }
            else {
                TObjectIntHashMap fieldReadMap = null;
                TObjectIntHashMap fieldWriteMap = null;

                threadLocalCheck: {
                    // Until we determine otherwise...
                    eventClass = EventClass.BOTH_MOVER;

                    fieldReadMap = (TObjectIntHashMap) os_fieldReads.get(objId);
                    boolean prevRead = (fieldReadMap != null) &&
                        fieldReadMap.containsKey(fieldName);

                    fieldWriteMap =
                        (TObjectIntHashMap) os_fieldWrites.get(objId);
                    boolean prevWritten = (fieldWriteMap != null) &&
                        fieldWriteMap.containsKey(fieldName);

                    if (isWrite) {
                        if (prevRead) {
                            int prevThreadId = fieldReadMap.get(fieldName);
                            if ((prevThreadId != threadId) && true) {
                                    // liveThreads.contains(prevThreadId)) {
                                eventClass = EventClass.NON_MOVER;
                                break threadLocalCheck;
                            }
                        }
                    }

                    if (prevWritten) {
                        int prevThreadId = fieldWriteMap.get(fieldName);
                        if ((prevThreadId != threadId) && true) {
                                // liveThreads.contains(prevThreadId)) {
                            eventClass = EventClass.NON_MOVER;
                            break threadLocalCheck;
                        }
                    }
                    else if (isWrite) {
                        if (fieldWriteMap == null) {
                            fieldWriteMap = new TObjectIntHashMap();
                            os_fieldWrites.put(objId, fieldWriteMap);
                        }
                        fieldWriteMap.put(fieldName, threadId);
                    }

                    if (!isWrite && !prevRead) {
                        if (fieldReadMap == null) {
                            fieldReadMap = new TObjectIntHashMap();
                            os_fieldReads.put(objId, fieldReadMap);
                        }
                        fieldReadMap.put(fieldName, threadId);
                    }
                }

                if (eventClass == EventClass.NON_MOVER) {
                    if (nonLocals == null) {
                        nonLocals = new THashSet();
                        os_nonThreadLocalFields.put(objId, nonLocals);
                    }
                    nonLocals.add(fieldName);
                    // Free memory
                    if (fieldWriteMap != null) {
                        fieldWriteMap.remove(fieldName);
                    }
                    if (fieldReadMap != null) {
                        fieldReadMap.remove(fieldName);
                    }
                }
            }

            if (eventClass == EventClass.NON_MOVER) {
                boolean dataRace = raceDetector.isPossibleRace(threadId, objId,
                    fieldName, isWrite);
                if (!dataRace) {
                    eventClass = EventClass.BOTH_MOVER;
                }
                else {
                    if (nonMovers == null) {
                        nonMovers = new THashSet();
                        os_nonMoverFields.put(objId, nonMovers);
                    }
                    nonMovers.add(fieldName);
                    // Free memory
                    nonLocals.remove(fieldName);
                }
            }
        }
        else {
            if (nonMoverFields.contains(fieldName)) {
                eventClass = EventClass.NON_MOVER;
                return;
            }

            if (nonThreadLocalFields.contains(fieldName)) {
                eventClass = EventClass.NON_MOVER;
            }
            else {
                threadLocalCheck: {
                    // Until we determine otherwise...
                    eventClass = EventClass.BOTH_MOVER;

                    boolean prevRead = fieldReads.containsKey(fieldName);
                    boolean prevWritten = fieldWrites.containsKey(fieldName);

                    if (isWrite) {
                        if (prevRead) {
                            int prevThreadId = fieldReads.get(fieldName);
                            if ((prevThreadId != threadId) && true) {
                                    // liveThreads.contains(prevThreadId)) {
                                eventClass = EventClass.NON_MOVER;
                                break threadLocalCheck;
                            }
                        }
                    }

                    if (prevWritten) {
                        int prevThreadId = fieldWrites.get(fieldName);
                        if ((prevThreadId != threadId) && true) {
                                // liveThreads.contains(prevThreadId)) {
                            eventClass = EventClass.NON_MOVER;
                            break threadLocalCheck;
                        }
                    }
                    else if (isWrite) {
                        fieldWrites.put(fieldName, threadId);
                    }

                    if (!isWrite && !prevRead) {
                        fieldReads.put(fieldName, threadId);
                    }
                }

                if (eventClass == EventClass.NON_MOVER) {
                    nonThreadLocalFields.add(fieldName);
                    // Free memory
                    fieldWrites.remove(fieldName);
                    fieldReads.remove(fieldName);
                }
            }

            if (eventClass == EventClass.NON_MOVER) {
                long objId = (od == null) ? -1 : od.getId();
                boolean dataRace = raceDetector.isPossibleRace(threadId, objId,
                    fieldName, isWrite);
                if (!dataRace) {
                    eventClass = EventClass.BOTH_MOVER;
                }
                else {
                    nonMoverFields.add(fieldName);
                    // Free memory
                    nonThreadLocalFields.remove(fieldName);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // The JDI only guarantees that the ID of a mirrored object is unique
    // if it has not been garbage collected. So if we are in
    // object-sensitive mode, whenever a new object is constructed we
    // clear any information associated with its ID, to make sure the new
    // object doesn't erroneously "inherit" that information from the
    // previous (now collected) object.
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (objSensitive) {
            long objId = od.getId();
            os_fieldReads.remove(objId);
            os_fieldWrites.remove(objId);
            os_nonThreadLocalFields.remove(objId);
            os_nonMoverFields.remove(objId);
        }
    }

    public void threadStartEvent(ThreadData td) {
        // liveThreads.add(td.getId());
    }

    public void threadDeathEvent(ThreadData td) {
        // liveThreads.remove(td.getId());
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        eventClass = EventClass.BOTH_MOVER;
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        eventClass = EventClass.RIGHT_MOVER;
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        eventClass = EventClass.BOTH_MOVER;
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        eventClass = EventClass.LEFT_MOVER;
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        classifyFieldEvent(td, null, fd, false);
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        // if (od.isArray()) {
        //     System.out.println("[" + od.getId() + "]." + fd.getFullName() +
        //         "=" + fd.getCurrentValue());
        // }
        classifyFieldEvent(td, od, fd, false);
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        classifyFieldEvent(td, null, fd, true);
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        // if (od.isArray()) {
        //     System.out.println("[" + od.getId() + "]." + fd.getFullName() +
        //         "=" + fd.getCurrentValue() + "->" + fd.getNewValue());
        // }
        classifyFieldEvent(td, od, fd, true);
    }
}
