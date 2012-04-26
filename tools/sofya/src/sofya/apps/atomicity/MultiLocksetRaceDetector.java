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
import java.util.Map;
import java.util.Iterator;

import sofya.ed.semantic.EventSelectionFilter;
import static sofya.apps.AtomicityChecker.ENABLE_ESCAPE_DETECTION;
import static sofya.apps.AtomicityChecker.ENABLE_HAPPENS_BEFORE;

import gnu.trove.THashSet;
import gnu.trove.TLongHashSet;
import gnu.trove.THashMap;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongIterator;

/**
 * Implements the multi-lockset race detection algorithm for fields described
 * by Wang and Stoller.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public final class MultiLocksetRaceDetector extends EventSelectionFilter {

    ////////////////////////////////////////////////////////////////////
    // One or the other of the following pairs of maps is instantiated
    // depending on whether we are operating with object sensitivity.
    // The object-sensitive maps are actually double maps that map
    // the object IDs to maps from field names to their lock set(s).
    // This allows us to quickly purge stale lock set data for garbage
    // collected objects if we see a new object created that has the
    // same ID as a previously known object.
    
    /** An object-sensitive map from variables to their access-protecting
        lock sets. */
    private TLongObjectHashMap os_readLockSets;
    /** An object-sensitive map from variables to their write-protecting
        lock set. */
    private TLongObjectHashMap os_writeLocks;
    
    /** An object-insensitive map from variables to their access-protecting
        lock sets. */
    private Map<Object, Set<Object>> readLockSets;
    /** An object-insensitive map from variables to their write-protecting
        lock set. */
    private Map<Object, Object> writeLocks;
    
    /** Implementation of the dynamic object escape analysis described
        by Wang and Stoller. */
    private DynamicEscapeDetector escapeDetector;
    /** Implementation of the happens-before analysis described by Wang
        and Stoller. */
    private HappenBeforeChecker hbChecker;
    
    /** Specifies whether every field of every object instance is
        considered distinct. */
    private boolean objSensitive;
    
    private MultiLocksetRaceDetector() {
    }
    
    /**
     * Creates a new multi-lockset race detector.
     *
     * @param objectSensitive Specifies whether race detection should
     * treat field of every object instance as distinct.
     */
    @SuppressWarnings("unchecked")
    public MultiLocksetRaceDetector(DynamicEscapeDetector escapeDetector,
            HappenBeforeChecker hbChecker, boolean objectSensitive) {
        this.escapeDetector = escapeDetector;
        this.hbChecker = hbChecker;
        this.objSensitive = objectSensitive;
        
        if (objSensitive) {
            this.os_readLockSets = new TLongObjectHashMap();
            this.os_writeLocks = new TLongObjectHashMap();
        }
        else {
            this.readLockSets = new THashMap();
            this.writeLocks = new THashMap();
        }
    }
    
    /**
     * Tests whether a field is believed to be involved in a data race.
     *
     * @param threadId ID of the thread performing the field related
     * operation.
     * @param objectId ID of the object that owns the field. Any negative
     * value is permitted for static fields, but the value should be
     * consistent.
     * @param fieldName Fully qualified name of the field.
     *
     * @return <code>true</code> if the multi-lockset race detection
     * believes the field is involved in a data race, <code>false</code>
     * otherwise.
     */
    @SuppressWarnings("unchecked")
    public boolean isPossibleRace(int threadId, long objectId,
            String fieldName, boolean isWrite) {
        // An object ID < 0 is a static field, which is escaped
        // by definition
        if (ENABLE_ESCAPE_DETECTION) {
            if ((objectId >= 0) && !escapeDetector.isEscaped(objectId)) {
                //System.out.println("mlrd(f1)");
                return false;
            }
        }
        
        TLongHashSet fieldWriteLocks;
        if (objSensitive) {
            Map<Object, Object> fieldMap =
                (THashMap) os_writeLocks.get(objectId);
            if (fieldMap == null) {
                //System.out.println("mlrd(f2)::" + threadId + "::" + objectId + "::" + fieldName + "::" + isWrite);
                return false;
            }
            else {
                fieldWriteLocks = (TLongHashSet) fieldMap.get(fieldName);
            }
        }
        else {
            fieldWriteLocks = (TLongHashSet) writeLocks.get(fieldName);
        }
        
        if (fieldWriteLocks == null) {
            //System.out.println("mlrd(f3)");
            return false;
        }
        else if (fieldWriteLocks.size() == 0) {
            if (ENABLE_HAPPENS_BEFORE) {
                return hbChecker.isConcurrent(threadId, objectId,
                    fieldName, isWrite);
            }
            else {
                return true;
            }
        }
        else {
            Set fieldReadLocksets;
            if (objSensitive) {
                THashMap fieldMap = (THashMap) os_readLockSets.get(objectId);
                if (fieldMap == null) {
                    // Uninitialized (all locks)
                    //System.out.println("mlrd(f4)");
                    return false;
                }
                else {
                    fieldReadLocksets = (Set) fieldMap.get(fieldName);
                }
            }
            else {
                fieldReadLocksets = (Set) readLockSets.get(fieldName);
            }
            
            if (fieldReadLocksets == null) {
                // Uninitialized (all locks)
                //System.out.println("mlrd(f5)");
                return false;
            }
            int size = fieldReadLocksets.size();
            Iterator iterator = fieldReadLocksets.iterator();
            for (int i = size; i-- > 0; ) {
                TLongHashSet fieldReadLockset = (TLongHashSet) iterator.next();
                
                if (emptyIntersection(fieldReadLockset, fieldWriteLocks)) {
                    if (ENABLE_HAPPENS_BEFORE) {
                        return hbChecker.isConcurrent(threadId, objectId,
                            fieldName, isWrite);
                    }
                    else {
                        return true;
                    }
                }
            }
            
            //System.out.println("mlrd(f6)");
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void updateReadLockSets(ThreadData td, long objectId,
            String fieldName) {
        TLongHashSet heldLocks = td.ownedMonitorIds();
        
        Set<Object> fieldLocksets;
        if (objSensitive) {
            Map<Object, Set<Object>> fieldMap =
                (THashMap) os_readLockSets.get(objectId);
            if (fieldMap == null) {
                fieldMap = new THashMap();
                os_readLockSets.put(objectId, fieldMap);
            }
            
            fieldLocksets = fieldMap.get(fieldName);
            if (fieldLocksets == null) {
                fieldLocksets = new THashSet();
                fieldMap.put(fieldName, fieldLocksets);
                fieldLocksets.add(heldLocks);
                return;
            }
        }
        else {
            fieldLocksets = readLockSets.get(fieldName);
            if (fieldLocksets == null) {
                fieldLocksets = new THashSet();
                readLockSets.put(fieldName, fieldLocksets);
                fieldLocksets.add(heldLocks);
                return;
            }
        }
        
        boolean add = true;
        int size = fieldLocksets.size();
        Iterator iterator = fieldLocksets.iterator();
        for (int i = size; i-- > 0; ) {
            TLongHashSet locks = (TLongHashSet) iterator.next();
            if (isSubset(heldLocks, locks)) {
                iterator.remove();
            }
            else if (add && isSubset(locks, heldLocks)) {
                add = false;
            }
        }
        
        if (add) {
            fieldLocksets.add(heldLocks);
        }
    }
    
    private void updateWriteLockSet(ThreadData td, long objectId,
            String fieldName) {
        TLongHashSet heldLocks = td.ownedMonitorIds();
        
        TLongHashSet fieldLocks;
        if (objSensitive) {
            THashMap fieldMap = (THashMap) os_writeLocks.get(objectId);
            if (fieldMap == null) {
                fieldMap = new THashMap();
                os_writeLocks.put(objectId, fieldMap);
            }
            
            fieldLocks = (TLongHashSet) fieldMap.get(fieldName);
            if (fieldLocks == null) {
                fieldMap.put(fieldName, heldLocks);
                return;
            }
        }
        else {
            fieldLocks = (TLongHashSet) writeLocks.get(fieldName);
            if (fieldLocks == null) {
                writeLocks.put(fieldName, heldLocks);
                return;
            }
        }
        
        intersect(fieldLocks, heldLocks);
    }
    
    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        updateReadLockSets(td, -1l, fd.getFullName());
    }
    
    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objId = od.getId();
        
        if (ENABLE_ESCAPE_DETECTION) {
            if (escapeDetector.isEscaped(objId)) {
                updateReadLockSets(td, objId, fd.getFullName());
            }
        }
        else {
            updateReadLockSets(td, objId, fd.getFullName());
        }
    }
    
    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        updateWriteLockSet(td, -1l, fd.getFullName());
    }
    
    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objId = od.getId();
        
        if (ENABLE_ESCAPE_DETECTION) {
            if (escapeDetector.isEscaped(objId)) {
                updateWriteLockSet(td, objId, fd.getFullName());
            }
        }
        else {
            updateWriteLockSet(td, objId, fd.getFullName());
        }
    }
    
    ////////////////////////////////////////////////////////////////////////
    // The JDI only guarantees that the ID of a mirrored object is unique
    // if it has not been garbage collected. So if we are in
    // object-sensitive mode, whenever a new object is constructed we
    // clear any lock sets associated with its ID, to make sure the new
    // object doesn't erroneously "inherit" the lock set data from the
    // previous (now collected) object.
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (objSensitive) {
            long objId = od.getId();
            os_readLockSets.remove(objId);
            os_writeLocks.remove(objId);
        }
    }
    
    /**
     * Tests whether set <em>a</em> is a subset of set <em>b</em>.
     */
    private static final boolean isSubset(TLongHashSet a, TLongHashSet b) {
        int size = a.size();
        TLongIterator iterator = a.iterator();
        for (int i = size; i-- > 0; ) {
            long val = iterator.next();
            if (!b.contains(val)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Reduces set <em>a</em> to the intersection of <em>a</em> and
     * <em>b</em>.
     */
    private static final void intersect(TLongHashSet a, TLongHashSet b) {
        int size = a.size();
        TLongIterator iterator = a.iterator();
        for (int i = size; i-- > 0; ) {
            long val = iterator.next();
            if (!b.contains(val)) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Tests whether the intersection of set <em>a</em> and set
     * <em>b</em> is empty without modifying either set.
     */
    private static final boolean emptyIntersection(TLongHashSet a,
            TLongHashSet b) {
        int size = a.size();
        TLongIterator iterator = a.iterator();
        for (int i = size; i-- > 0; ) {
            long val = iterator.next();
            if (!b.contains(val)) {
                size--;
            }
        }
        return (size == 0);
    }
    
    /**
     * Prints the contents of a hash set of longs to the console.
     */
    @SuppressWarnings("unused")
    private static final void printLongHashSet(TLongHashSet s) {
        int size = s.size();
        TLongIterator iterator = s.iterator();
        System.out.print("[ ");
        for (int i = size; i-- > 0; ) {
            System.out.print(iterator.next() + " ");
        }
        System.out.println("]");
    }
}
