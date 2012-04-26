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

package sofya.ed.semantic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.StringTokenizer;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
/*#ifdef FAST_JDI
import edu.unl.jdi.event.Event;
/*#endif*/
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;

import sofya.base.MethodSignature;
import sofya.base.exceptions.SofyaError;
import sofya.base.Utility.IntegerPtr;
import static sofya.base.Utility.isParsableInt;

import org.apache.bcel.generic.Type;

import gnu.trove.THashMap;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongObjectHashMap;

/**
 * Defines event notifications corresponding to observable events occurring
 * in a module being traced.
 *
 * <p>This interface serves a dual-purpose of hiding dependence on the
 * <code>com.sun.jdi</code> packages, since Sun guidelines state that such
 * packages are not part of the official JDK. Clients of the event
 * dispatcher framework are therefore shielded from having to deal with
 * any changes to the interfaces in those packages. The one notable exception
 * is the handling of mirrored values, for which constructing facades
 * has been decided to add little value and considerable overhead.</p>
 *
 * @author Alex Kinneer
 * @version 08/02/2007
 */
public interface EventListener {
    // FIXME: Scrutinize and minimize all pre-requests of payload data
    // so that it is only requested when needed.
    
    /**
     * Notification that the target virtual machine has been launched.
     */
    public void systemStarted();

    /**
     * Notification that the system has begun executing user code.
     *
     * <p>This event will be preceded by thread start events.</p>
     */
    public void executionStarted();

    /**
     * Notification that a thread has started. These events are not dependent
     * on the module description.
     *
     * @param td Information about the newly started thread.
     */
    public void threadStartEvent(ThreadData td);

    /**
     * Notification that a thread has terminated. These events are not dependent
     * on the module description.
     *
     * @param td Information about the terminated thread.
     */
    public void threadDeathEvent(ThreadData td);

    /**
     * Notification that a class was prepared (this is, loaded by the
     * classloader). These events are not dependent on the module
     * description.
     *
     * @param td Information about the thread which caused the class to
     * be prepared.
     * @param className Name of the class which was prepared.
     */
    public void classPrepareEvent(ThreadData td, String className);

    /**
     * Notification that a thread is contending for a monitor.
     *
     * @param td Information about the thread which is contending for a monitor.
     * @param od Information about the object which owns the monitor.
     * @param md Information about the location of the monitor contention.
     */
    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md);

    /**
     * Notification that a thread has acquired a monitor.
     *
     * @param td Information about the thread which acquired a monitor.
     * @param od Information about the object which owns the monitor.
     * @param md Information about the location of the monitor acquisition.
     */
    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md);

    /**
     * Notification that a thread is about to release a monitor.
     *
     * @param td Information about the thread which is about to release a
     * monitor.
     * @param od Information about the object which owns the monitor.
     * @param md Information about the location of the monitor about to
     * be released event..
     */
    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md);

    /**
     * Notification that a thread has released a monitor.
     *
     * @param td Information about the thread which released a monitor.
     * @param od Information about the object which owns the monitor.
     * @param md Information about the location of the monitor release.
     */
    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md);

    /**
     * Notification that an object was allocated by a <code>NEW</code>
     * instruction.
     *
     * <p>This event cannot yet associate a unique identifier with the
     * object, because this event only represents the allocation of
     * a reference to which an object of a given class may be assigned.
     * In other words, no actual object has yet been created. A unique
     * ID can be obtained once a constructor is executed, which is
     * signaled by the {@link #constructorEnterEvent}.</p>
     *
     * @param td Information about the thread which allocated the object.
     * @param nad Information about the new allocation.
     */
    public void newAllocationEvent(ThreadData td, NewAllocationData nad);

    /**
     * Notification that a constructor was invoked.
     *
     * @param td Information about the thread which invoked the constructor.
     * @param cd Information about the particular constructor which
     * was invoked.
     */
    public void constructorCallEvent(ThreadData td, CallData cd);

    /**
     * Notification that a constructor was entered.
     *
     * <p>This is the first point during the object creation process at
     * which the object can be uniquely identified.</p>
     *
     * @param td Information about the thread executing the constructor.
     * @param od Information about the object under construction.
     * <strong>At the time of this event, only the object ID will be
     * valid.</strong>
     * @param md Information about the entered constructor.
     */
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md);

    /**
     * Notification that a constructor was exited.
     *
     * <p>NOTE: This event is raised only if the constructor completes
     * normally. If a constructor throws an escaping exception, the
     * object is not successfully created and thus further events
     * related to the object are not possible.</p>
     *
     * @param td Information about the thread executing the constructor.
     * @param od Information about the object under construction.
     * <strong>At the time of this event, only the object ID will be
     * valid.</strong>
     * @param md Information about the exited constructor.
     * @param exceptional Flag indicating whether the method exited
     * by throwing an exception.
     */
    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional);

    /**
     * Notification that a static field was read.
     *
     * @param td Information about the thread in which the field access
     * occurred.
     * @param fd Information about the accessed field.
     */
    public void staticFieldAccessEvent(ThreadData td, FieldData fd);

    /**
     * Notification that an instance field was read.
     *
     * @param td Information about the thread in which the field access
     * occurred.
     * @param od Information about the object which owns the accessed field.
     * @param fd Information about the accessed field.
     */
    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd);

    /**
     * Notification that a static field was written.
     *
     * @param td Information about the thread in which the field write
     * occurred.
     * @param fd Information about the written field.
     */
    public void staticFieldWriteEvent(ThreadData td, FieldData fd);

    /**
     * Notification that an instance field was written.
     *
     * @param td Information about the thread in which the field write
     * occurred.
     * @param od Information about the object which owns the written field.
     * @param fd Information about the written field.
     */
    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd);

    /**
     * Notification that a static method was called.
     *
     * @param td Information about the thread which called the method.
     * @param cd Information about the called method.
     */
    public void staticCallEvent(ThreadData td, CallData cd);

    /**
     * Notification that a virtual method was called.
     *
     * @param td Information about the thread which called the method.
     * @param cd Information about the called method.
     */
    public void virtualCallEvent(ThreadData td, CallData cd);

    /**
     * Notification that an interface method was called.
     *
     * @param td Information about the thread which called the method.
     * @param cd Information about the called method.
     */
    public void interfaceCallEvent(ThreadData td, CallData cd);

    /**
     * Notification that a thread has returned from a method call.
     *
     * @param td Information about the thread in which the method call
     * returned.
     * @param cd Information about the called method from which
     * control returned.
     * @param exceptional Flag indicating whether the method returned
     * exceptionally.
     */
    public void callReturnEvent(ThreadData td, CallData cd,
            boolean exceptional);

    /**
     * Notification that a virtual method was entered.
     *
     * @param td Information about the thread executing the method.
     * @param od Information about the object on which the virtual
     * method was called.
     * @param md Information about the entered method.
     */
    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md);

    /**
     * Notification that a virtual method was exited.
     *
     * @param td Information about the thread executing the method.
     * @param od Information about the object on which the virtual
     * method was called.
     * @param md Information about the exited method.
     * @param exceptional Flag indicating whether the method exited
     * by throwing an exception.
     */
    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional);

    /**
     * Notification that a static method was entered.
     *
     * @param td Information about the thread executing the method.
     * @param md Information about the entered method.
     */
    public void staticMethodEnterEvent(ThreadData td, MethodData md);

    /**
     * Notification that a static method was exited.
     *
     * @param td Information about the thread executing the method.
     * @param md Information about the exited method.
     * @param exceptional Flag indicating whether the method exited
     * by throwing an exception.
     */
    public void staticMethodExitEvent(ThreadData td, MethodData md,
            boolean exceptional);

    /**
     * Notification that an exception was thrown.
     *
     * @param td Information about the thread in which the exception was
     * thrown.
     * @param ed Information about the thrown exception.
     */
    public void exceptionThrowEvent(ThreadData td, ExceptionData ed);

    /**
     * Notification that an exception was caught.
     *
     * @param td Information about the thread in which the exception was
     * caught.
     * @param ed Information about the caught exception.
     */
    public void exceptionCatchEvent(ThreadData td, ExceptionData ed);

    /**
     * Notification that a static initializer was entered.
     *
     * @param td Information about the thread executing the static initializer.
     * @param md Information about the entered static initializer.
     */
    public void staticInitializerEnterEvent(ThreadData td, MethodData md);

    /**
     * Notification that the system has terminated.
     */
    public void systemExited();

    /**
     * Type-safe enumeration for indicating the status of a thread on which
     * an observable event has occurred.
     */
    public static class ThreadStatus {
        private int status;

        /** Thread is waiting to acquire a monitor. */
        public static final ThreadStatus MONITOR =
            new ThreadStatus(ThreadReference.THREAD_STATUS_MONITOR);
        /** Thread has been created but not yet started. */
        public static final ThreadStatus NOT_STARTED =
            new ThreadStatus(ThreadReference.THREAD_STATUS_NOT_STARTED);
        /** Thread is running. */
        public static final ThreadStatus RUNNING =
            new ThreadStatus(ThreadReference.THREAD_STATUS_RUNNING);
        /** Thread is sleeping. */
        public static final ThreadStatus SLEEPING =
            new ThreadStatus(ThreadReference.THREAD_STATUS_SLEEPING);
        /** JVM is unabled to determine or report the thread status. */
        public static final ThreadStatus UNKNOWN =
            new ThreadStatus(ThreadReference.THREAD_STATUS_UNKNOWN);
        /** Thread is waiting, as by a call to <code>Object.wait()</code>. */
        public static final ThreadStatus WAIT =
            new ThreadStatus(ThreadReference.THREAD_STATUS_WAIT);
        /** Thread is waiting to be disposed. */
        public static final ThreadStatus ZOMBIE =
            new ThreadStatus(ThreadReference.THREAD_STATUS_ZOMBIE);

        private ThreadStatus() { }
        private ThreadStatus(int status) {
            this.status = status;
        }

        public String toString() {
            switch (status) {
            case ThreadReference.THREAD_STATUS_MONITOR:
                return "acquiring monitor";
            case ThreadReference.THREAD_STATUS_NOT_STARTED:
                return "not started";
            case ThreadReference.THREAD_STATUS_RUNNING:
                return "running";
            case ThreadReference.THREAD_STATUS_SLEEPING:
                return "sleeping";
            case ThreadReference.THREAD_STATUS_UNKNOWN:
                return "unknown";
            case ThreadReference.THREAD_STATUS_WAIT:
                return "waiting";
            case ThreadReference.THREAD_STATUS_ZOMBIE:
                return "zombie";
            default:
                throw new SofyaError("Illegal thread status code");
            }
        }
    }

    /**
     * Provides information about the thread on which an observable event
     * has occurred.
     */
    public static class ThreadData {
        private int id;
        private long objId;
        /*#ifdef FAST_JDI
        private Event transactionEvent;
        private edu.unl.jdi.ThreadReference threadRef;
        /*#else*/
        private ThreadReference threadRef;
        /*#endif*/
        private final ObjectData targetRef;

        private static final Map<VirtualMachine, Field> targetMap =
                new THashMap();

        static void setTargetField(VirtualMachine vm, Field f) {
            targetMap.put(vm, f);
        }

        private ThreadData() {
            throw new AssertionError("Illegal constructor");
        }

        /*#ifdef FAST_JDI
        ThreadData(VirtualMachine vm, int id,
                edu.unl.jdi.ThreadReference threadRef) {
        /*#else*/
        ThreadData(VirtualMachine vm, int id, ThreadReference threadRef) {
        /*#endif*/
            this.id = id;
            this.threadRef = threadRef;
            this.objId = threadRef.uniqueID();

            Field targetField = targetMap.get(vm);
            ObjectReference target =
                (ObjectReference) threadRef.getValue(targetField);
            targetRef = (target != null) ? new ObjectData(target) : null;
        }
        
        /*#ifdef FAST_JDI
        void setTransactionEvent(Event e) {
            transactionEvent = e;
        }
        
        void clearTransactionEvent() {
            transactionEvent = null;
        }
        /*#endif*/

        /**
         * Gets the unique integer identifier assigned to the thread by the
         * event dispatching framework.
         *
         * @return The thread's ID.
         */
        public int getId() {
            return id;
        }

        /**
         * Gets the unique identifier associated with the thread object by
         * the JVM. This may be relevant if, for example, the thread is
         * assigned to a field somewhere.
         *
         * <p>This method will succeed even if the thread has stopped
         * running and/or has been garbage collected.</p>
         *
         * @return A unique identifier associated with the mirrored thread.
         */
        public long getObjectId() {
            objectRefs.put(objId, threadRef);
            return objId;
        }

        /**
         * Gets the name of the thread, as reported by the JVM.
         *
         * @return The thread's name.
         */
        public String getName() {
            return threadRef.name();
        }

        /**
         * Gets the thread's status, as reported by the JVM.
         *
         * @return The thread's status.
         */
        public ThreadStatus getStatus() {
            switch (threadRef.status()) {
            case ThreadReference.THREAD_STATUS_MONITOR:
                return ThreadStatus.MONITOR;
            case ThreadReference.THREAD_STATUS_NOT_STARTED:
                return ThreadStatus.NOT_STARTED;
            case ThreadReference.THREAD_STATUS_RUNNING:
                return ThreadStatus.RUNNING;
            case ThreadReference.THREAD_STATUS_SLEEPING:
                return ThreadStatus.SLEEPING;
            case ThreadReference.THREAD_STATUS_UNKNOWN:
                return ThreadStatus.UNKNOWN;
            case ThreadReference.THREAD_STATUS_WAIT:
                return ThreadStatus.WAIT;
            case ThreadReference.THREAD_STATUS_ZOMBIE:
                return ThreadStatus.ZOMBIE;
            default:
                throw new SofyaError("Unknown thread status code");
            }
        }

        /**
         * Gets the runnable object that will be executed by this thread, if
         * any. That is, the object implementating the <code>Runnable</code>
         * interface that is the target to be executed by this thread.
         *
         * @return The runnable object to be executed by this thread, or
         * <code>null</code> if no runnable target was specified for this
         * thread (e.g. if it is subclassed with an override of
         * <code>run()</code>).
         */
        public ObjectData getRunnableTarget() {
            return targetRef;
        }

        /**
         * Gets a handle to the thread object itself.
         *
         * @return Object information for this thread, generally useful
         * when the thread is an instance of a user-defined subclass of
         * <code>java.lang.Thread</code>.
         */
        public ObjectData getObject() {
            return new ObjectData((ObjectReference) threadRef);
        }

        /**
         * Gets the monitors held by this thread.
         *
         * @return The list of monitors acquired and not yet released by
         * this thread.
         */
        public ObjectData[] ownedMonitors() {
            /*#ifdef FAST_JDI
            List<? extends ObjectReference> ownedMonitors = null;
            if (transactionEvent != null) {
                ownedMonitors = threadRef.ownedMonitors(transactionEvent);
            }
            if (ownedMonitors == null) {
                try {
                    ownedMonitors = threadRef.<ObjectReference>ownedMonitors();
                }
                catch (IncompatibleThreadStateException e) {
                    throw new SofyaError("JDI error: \"" +
                        threadRef.name() + "\"", e);
                }
            }
            /*#else*/
            List<ObjectReference> ownedMonitors = null;
            try {
                ownedMonitors = threadRef.ownedMonitors();
            }
            catch (IncompatibleThreadStateException e) {
                throw new SofyaError("JDI error: \"" +
                    threadRef.name() + "\"", e);
            }
            /*#endif*/
            
            int size = ownedMonitors.size();
            ObjectData[] locks = new ObjectData[size];

            Iterator iterator = ownedMonitors.iterator();
            for (int i = 0; i < size; i++) {
                locks[i] = new ObjectData((ObjectReference) iterator.next());
            }

            return locks;
        }

        /**
         * Gets the IDs of all the objects whose monitors are held by
         * this thread.
         *
         * @return The set of object IDs of monitors acquired and not
         * yet released by this thread.
         */
        public TLongHashSet ownedMonitorIds() {
            /*#ifdef FAST_JDI
            List<? extends ObjectReference> ownedMonitors = null;
            if (transactionEvent != null) {
                ownedMonitors = threadRef.ownedMonitors(transactionEvent);
            }
            if (ownedMonitors == null) {
                try {
                    ownedMonitors = threadRef.<ObjectReference>ownedMonitors();
                }
                catch (IncompatibleThreadStateException e) {
                    throw new SofyaError("JDI error: \"" +
                        threadRef.name() + "\"", e);
                }
            }
            /*#else*/
            List<ObjectReference> ownedMonitors = null;
            try {
                ownedMonitors = threadRef.ownedMonitors();
            }
            catch (IncompatibleThreadStateException e) {
                throw new SofyaError("JDI error: \"" +
                    threadRef.name() + "\"", e);
            }
            /*#endif*/
            
            int size = ownedMonitors.size();
            TLongHashSet locks = new TLongHashSet();

            Iterator iterator = ownedMonitors.iterator();
            for (int i = 0; i < size; i++) {
                ObjectReference lockObj = (ObjectReference) iterator.next();
                long uniqueID = lockObj.uniqueID();
                objectRefs.put(uniqueID, lockObj);
                locks.add(uniqueID);
            }

            return locks;
        }
    }

    /**
     * Provides information about an object on which an event occurred.
     */
    public static class ObjectData {
        private ObjectReference obj;
        private String type;
        private long id;

        private ObjectData() { }
        ObjectData(ObjectReference or) {
            obj = or;
            id = obj.uniqueID();
            //if (SemanticEventDispatcher.second_flag) {
               // System.out.println("id=" + id);
            //}
            type = obj.referenceType().name();
            //if (SemanticEventDispatcher.second_flag) {
               // System.out.println("type=" + type);
            //}
        }

        /**
         * Gets the unique identifier associated with the mirrored object.
         *
         * <p>This method will succeed even if the object has been garbage
         * collected.</p>
         *
         * @return A unique identifier associated with the mirrored object.
         */
        public long getId() {
            objectRefs.put(id, obj);
            return id;
        }

        /**
         * Gets the fully qualified name of the type of the mirrored object.
         *
         * <p>This method will succeed even if the object has been garbage
         * collected.</p>
         *
         * @return The type of the mirrored object.
         */
        public String getType() {
            return type;
        }

        /**
         * Reports whether the object is an array.
         *
         * @return <code>true</code> if the object affected by the event
         * occurred is an array reference.
         */
        public boolean isArray() {
            return (obj instanceof ArrayReference);
        }

        /**
         * Enables garbage collection of the mirrored object.
         *
         * <p>If the target virtual machine has been resumed, requests for
         * information about the mirrored object may fail with an
         * <code>ObjectCollectedException</code> after a call to this
         * method.</p>
         */
        public void enableCollection() {
            obj.enableCollection();
        }

        /**
         * Disables garbage collection of the mirrored object.
         *
         * <p>Calling this method will prevent the target virtual machine
         * from garbage collecting the mirrored object, which guarantees
         * that an <code>ObjectCollectedException</code> cannot be thrown
         * if a request for information about the object is made at a
         * later time. However, call this method only if you are
         * <em>certain</em> that you will need to request information about
         * the mirrored object at an indeterminate time in the future, and
         * it is possible you will not be able to obtain a reference to the
         * object again from a future event. Indiscrimate use of this method
         * will interfere with the behavior of the target virtual machine,
         * and in the extreme may cause an otherwise unnecessary
         * <code>OutOfMemoryError</code>.
         */
        public void disableCollection() {
            obj.disableCollection();
        }

        /**
         * Reports whether the mirrored object has been garbage collected.
         *
         * <p>If the mirrored object has been collected, attempts to request
         * most types of information about the object will result in an
         * <code>ObjectCollectedException</code> and should be avoided.</p>
         *
         * @return <code>true</code> if the mirrored object has been collected.
         */
        public boolean isCollected() {
            boolean isCollected = obj.isCollected();
            if (isCollected) {
                objectRefs.remove(id);
            }
            return isCollected;
        }

        /**
         * Gets the underlying object reference (<em>use with care</em>,
         * you will be exposed to changes in <code>com.sun.jdi</code>).
         *
         * @return The object reference for the object.
         */
        public ObjectReference getReference() {
            objectRefs.put(id, obj);
            return obj;
        }
    }

    /**
     * Provides information about an object which has been allocated
     * by the <code>NEW</code> instruction, but not yet initialized
     * by a constructor.
     */
    public static class NewAllocationData {
        private String className;
        private Location location;

        private NewAllocationData() { }
        NewAllocationData(String className, Location l) {
            this.className = className;
            this.location = l;
        }

        /**
         * Gets the class of the object which was allocated.
         *
         * @return The new object's class.
         */
        public String getNewAllocationClass() {
            return className;
        }

        /**
         * Gets the name of the class in which the object allocation occurred.
         *
         * @return The name of the class in which the object was allocated.
         */
        public String getClassName() {
            return location.declaringType().name();
        }

        /**
         * Gets the name of the method in which the object allocation occurred.
         *
         * @return The name of the method in which the object was allocated.
         */
        public String getMethod() {
            return location.method().name();
        }

        /**
         * Gets the signature of the method in which the object
         * allocation occurred.
         *
         * @return The JNI type signature of the method in which the
         * object was allocated.
         */
        public String getMethodSignature() {
            return location.method().signature();
        }

        // The following methods will return (invalid) information about
        // the probe instruction, rather than the actual instruction, so
        // they are disabled for now.

        /*public long getCodeIndex() {
            return location.codeIndex();
        }

        public String getSourceFile() {
            try {
                return location.sourceName();
            }
            catch (AbsentInformationException e) {
                return "<unknown>";
            }
        }

        public int getLineNumber() {
            return location.lineNumber();
        }*/
    }

    /**
     * Provides information about a field event.
     */
    public static class FieldData {
        private Field field;
        private Location location;
        private Value valueCurrent;
        private Value valueToBe;

        FieldData() { }

        FieldData(Field f, Location l, Value valueCurrent) {
            field = f;
            location = l;
            this.valueCurrent = valueCurrent;
        }

        FieldData(Field f, Location l, Value valueCurrent, Value valueToBe) {
            field = f;
            location = l;
            this.valueCurrent = valueCurrent;
            this.valueToBe = valueToBe;
        }

        /**
         * Gets the underlying field reference.
         *
         * @return The JDI reference to the field.
         */
        Field field() {
            return field;
        }

        /**
         * Gets the short name of the field that was accessed or modified.
         *
         * @return The unqualified name of the field.
         */
        public String getName() {
            return field.name();
        }

        /**
         * Gets the complete name of the field that was accessed or modified.
         *
         * @return The fully qualified name of the field.
         */
        public String getFullName() {
            return field.declaringType().name() + "." + field.name();
        }

        /**
         * Gets the type of the field that was accessed or modified.
         *
         * @return The type of the field.
         */
        public String getType() {
            return field.typeName();
        }

        /**
         * Gets the signature of the field that was accessed or modified.
         *
         * @return The JNI type signature of the field.
         */
        public String getSignature() {
            return field.signature();
        }

        /**
         * Gets the index of the array element to which this field data
         * correlates.
         *
         * <p>Array element accesses and writes are dispatched as field access
         * and write events, as usual. However, the object information
         * provided with those events corresponds to the array reference, and
         * the relevant element of the array is treated as a field of the
         * array object.</p>
         *
         * @return The index of the element in the array to which this
         * field correlates, or -1 if this field is not an array element
         * (it is a true field).
         */
        public int getElementIndex() {
            return -1;
        }

        /**
         * Gets the current value stored in the field that was accessed
         * or modified.
         *
         * @return The value currently stored to the field.
         */
        public Value getCurrentValue() {
            if (valueCurrent instanceof ObjectReference) {
                ObjectReference objRef = (ObjectReference) valueCurrent;
                objectRefs.put(objRef.uniqueID(), objRef);
            }
            return valueCurrent;
        }

        /**
         * Gets the new value to be stored in the field that was
         * modified. Returns <code>null</code> if this field data object
         * is associated with a field access event.
         *
         * @return The new value that will be stored to the field.
         */
        public Value getNewValue() {
            if (valueToBe instanceof ObjectReference) {
                ObjectReference objRef = (ObjectReference) valueToBe;
                objectRefs.put(objRef.uniqueID(), objRef);
            }
            return valueToBe;
        }

        /**
         * Gets the name of the class in which the field event occurred.
         *
         * @return The name of the class in which the field was accessed
         * or modified.
         */
        public String getClassName() {
            return location.declaringType().name();
        }

        /**
         * Gets the name of the method in which the field event occurred.
         *
         * @return The name of the method in which the field was accessed
         * or modified.
         */
        public String getMethod() {
            return location.method().name();
        }

        /**
         * Gets the signature of the method in which the field event occurred.
         *
         * @return The JNI type signature of the method in which the field
         * was accessed or modified.
         */
        public String getMethodSignature() {
            return location.method().signature();
        }

        /**
         * Gets the bytecode offset at which the field event occurred.
         *
         * @return The bytecode offset within the method of the field
         * access or modification instruction.
         */
        public long getCodeIndex() {
            return location.codeIndex();
        }

        /**
         * Gets the name of the source code file corresponding to the
         * field event.
         *
         * @return The name of the source code file which contains the code
         * for the method containing the field access or modification.
         * Returns &apos;&lt;unknown&gt;&apos; if this information is
         * not available.
         */
        public String getSourceFile() {
            try {
                return location.sourceName();
            }
            catch (AbsentInformationException e) {
                return "<unknown>";
            }
        }

        /**
         * Gets the line number in the source code file of the field event.
         *
         * @return The line number of the source code statement for the
         * field access or modification, or -1 if this information is
         * unavailable.
         */
        public int getLineNumber() {
            return location.lineNumber();
        }
    }

    /**
     * Provides access to the arguments to a method.
     *
     * <p>Information about arguments is available for method calls that
     * are witnessed using interceptors.</p>
     */
    public static class Arguments {
        private ObjectReference thisObj;
        private List<LocalVariable> args;
        private Map<LocalVariable, Value> values;
        private boolean isStatic;
        
        /*#ifdef FAST_JDI
        private Value[] payloadValues;
        /*#endif*/

        private Arguments() { }
        
        /*#ifdef FAST_JDI
        Arguments(ObjectReference thisObj, Value[] values) {
            this.thisObj = thisObj;
            this.args = null;
            this.payloadValues = values;
        }
        /*#endif*/
        
        Arguments(ObjectReference thisObj, List<LocalVariable> args,
                Map<LocalVariable, Value> values) {
            this.thisObj = thisObj;
            this.args = args;
            this.values = values;
        }
        
        Arguments(boolean isStatic, List<LocalVariable> args,
                Map<LocalVariable, Value> values) {
            setStatic(isStatic);
            if (!isStatic) {
                this.thisObj = (ObjectReference) values.remove(args.remove(0));
            }
            this.args = args;
            this.values = values;
        }

        void setStatic(boolean isStatic) {
            this.isStatic = isStatic;
        }

        /**
         * Gets the reference to &quot;<code>this</code>&quot; in the
         * method, or <code>null</code> if the call is static.
         *
         * @return The &quot;<code>this</code>&quot; reference for the
         * method (the receiver object of the call, if virtual), or
         * <code>null</code> if the call is static.
         */
        public ObjectReference getThis() {
            if (isStatic) {
                return null;
            }

            //ObjectReference thisObj =
            //    (ObjectReference) values.get(args.get(0));
            objectRefs.put(thisObj.uniqueID(), thisObj);

            return thisObj;
        }

        /**
         * Gets the value of an argument to the method.
         *
         * @param index Index of the argument value to be retrieved.
         *
         * @return The value of the argument at the specified index.
         */
        public Value getArgument(int index) {
            /*#ifdef FAST_JDI
            if (payloadValues != null) {
                Value arg = payloadValues[index];
                if (payloadValues[index] instanceof ObjectReference) {
                    ObjectReference objArg =
                        (ObjectReference) payloadValues[index];
                    objectRefs.put(objArg.uniqueID(), objArg);
                }
                return arg;
            }
            else /*#endif*/ {
                Object arg = values.get(args.get(index));
                if (arg instanceof ObjectReference) {
                    ObjectReference objArg = (ObjectReference) arg;
                    objectRefs.put(objArg.uniqueID(), objArg);
                    return objArg;
                }
                else {
                    return (Value) arg;
                }
            }
        }

        /**
         * Gets the values of the arguments to the method.
         *
         * @return A map from each <code>LocalVariable</code> argument to its
         * corresponding value.
         */
        public Map<LocalVariable, Value> getAllArguments() {
            // TODO: Audit this for better performance solutions in
            // conjunction with improved JDI implementation
            Collection<Value> actualVals = values.values();
            Iterator<Value> iterator = actualVals.iterator();
            int size = actualVals.size();
            for (int i = size; i-- > 0; ) {
                Value val = iterator.next();
                if (val instanceof ObjectReference) {
                    ObjectReference objVal = (ObjectReference) val;
                    objectRefs.put(objVal.uniqueID(), objVal);
                }
            }

            return values;
        }
        
        /*#ifdef FAST_JDI
        public Value[] getAllArgumentValues() {
            if (payloadValues != null) {
                int size = payloadValues.length;
                for (int i = size; --i >= 0; ) {
                    Value val = payloadValues[i];
                    if (val instanceof ObjectReference) {
                        ObjectReference objVal = (ObjectReference) val;
                        objectRefs.put(objVal.uniqueID(), objVal);
                    }
                }
            }

            return payloadValues;
        }
        /*#endif*/

        /**
         * Gets the values of the arguments to the method.
         *
         * <p>This method is <strong>unsafe</strong> in the sense that it
         * does not protect the object ID&apos;s associated with any object
         * arguments. The caller is responsible for preventing the garbage
         * collection of any <code>ObjectReference</code> values returned
         * by this call if it is important that the unique ID be consistent
         * across all instances of <code>ObjectReference</code> for the
         * same mirrored remote object(s). This shortcoming is a weakness of
         * the JDI.</p>
         *
         * <p>Use the {@link #getAllArguments()} method if you
         * want the protection of object ID&apos;s to be managed
         * internally.</p>
         *
         * @return A map from each <code>LocalVariable</code> argument to its
         * corresponding value.
         */
        public Map<LocalVariable, Value> getAllArgumentsFast() {
            return values;
        }
    }

    /**
     * Provides information about entry into a method.
     */
    public static class MethodData {
        private MethodSignature signature;
        private String rawSignature;
        private Arguments args;
        
        private int accFlags;
        
        // Illegal combination of abstract/final, so we can use it
        // as a marker
        private static final int ACC_UNSUPPORTED = 0x0410;
        // Illegal combination of abstract/private
        private static final int ACC_BAD         = 0x0402;

        private MethodData() { }

        MethodData(String signature) {
            this.rawSignature = signature;
            this.signature = parseSignature(signature);
        }

        MethodData(String signature, Arguments args) {
            this(signature);
            this.args = args;
        }

        MethodSignature parseSignature(String sigString) {
            StringTokenizer stok = new StringTokenizer(sigString, "#");

            String className = stok.nextToken();
            String methodName = stok.nextToken();
            String jniSig = stok.nextToken();
            Type returnType = Type.getReturnType(jniSig);
            Type[] argTypes = Type.getArgumentTypes(jniSig);
            
            if (stok.hasMoreTokens()) {
                IntegerPtr flagsOut = new IntegerPtr();
                if (isParsableInt(stok.nextToken(), 16, flagsOut)) {
                    accFlags = flagsOut.value;
                }
                else {
                    accFlags = ACC_BAD;
                }
            }
            else {
                accFlags = ACC_UNSUPPORTED;
            }

            return new MethodSignature(className, methodName,
                                       returnType, argTypes);
        }

        /**
         * Gets the signature of the entered method.
         *
         * @return The signature of the entered method.
         */
        public MethodSignature getSignature() {
            return signature;
        }

        /**
         * Gets the signature string recorded by the probe for the
         * entered method.
         *
         * @return The raw signature string of the entered method.
         */
        public String getRawSignature() {
            return rawSignature;
        }

        /**
         * Reports whether the values of the arguments passed to the method
         * can be retrieved; this is true only on method entry events.
         *
         * @return <code>true</code> if the values of the arguments to the
         * method can be obtained.
         */
        public boolean canGetArguments() {
            return (args != null);
        }

        /**
         * Gets the arguments to the method.
         *
         * @return An object that provides access to information about the
         * actual parameters passed to the method.
         */
        public Arguments getArguments() {
            return args;
        }
        
        /**
         * Gets the Java access flags for the method, as defined in the
         * Java Virtual Machine Specification.
         *
         * @return The integer bitmask corresponding to the access flags
         * that are set for the method.
         * 
         * @throws UnsupportedOperationException If the instrumentation
         * version does not support the delivery of this information,
         * or the instrumentation is corrupted in some way.
         */
        public int getAccessFlags() throws UnsupportedOperationException {
            if (!validateAccFlags()) {
                accError();
            }
            return accFlags;
        }
          
        /**
         * Reports whether the method has <code>public</code> access.
         *
         * @return <code>true</code> if the method is <code>public</code>,
         * <code>false</code> otherwise.
         * 
         * @throws UnsupportedOperationException If the instrumentation
         * version does not support the delivery of this information,
         * or the instrumentation is corrupted in some way.
         */
        public boolean isPublic() throws UnsupportedOperationException {
            if (!validateAccFlags()) {
                accError();
            }
            return (accFlags & 0x0001) > 0;
        }
        
        /**
         * Reports whether the method has <code>protected</code> access.
         *
         * @return <code>true</code> if the method is <code>protected</code>,
         * <code>false</code> otherwise.
         * 
         * @throws UnsupportedOperationException If the instrumentation
         * version does not support the delivery of this information,
         * or the instrumentation is corrupted in some way.
         */
        public boolean isProtected() throws UnsupportedOperationException {
            if (!validateAccFlags()) {
                accError();
            }
            return (accFlags & 0x0004) > 0;
        }
        
        /**
         * Reports whether the method has <code>private</code> access.
         *
         * @return <code>true</code> if the method is <code>private</code>,
         * <code>false</code> otherwise.
         * 
         * @throws UnsupportedOperationException If the instrumentation
         * version does not support the delivery of this information,
         * or the instrumentation is corrupted in some way.
         */
        public boolean isPrivate() throws UnsupportedOperationException {
            if (!validateAccFlags()) {
                accError();
            }
            return (accFlags & 0x0002) > 0;
        }
          
        private final boolean validateAccFlags() {
            return !((accFlags == ACC_UNSUPPORTED) || (accFlags == ACC_BAD));
        }
          
        private final void accError() throws UnsupportedOperationException {
            switch (accFlags) {
            case ACC_UNSUPPORTED:
                throw new UnsupportedOperationException(
                    "Information not supported by this version " +
                    "of Sofya instrumentation");
            case ACC_BAD:
                throw new UnsupportedOperationException(
                    "Instrumentation is invalid");
            default:
                throw new UnsupportedOperationException("Internal error");
            }
        }
    }

    /**
     * Provides information about a method call.
     */
    public static class CallData {
        private Location location;
        private MethodSignature signature;
        private String rawSignature;
        private boolean fromIntercept;
        private boolean isNative;
        private Arguments args;
        
        //private int accFlags;
        
        // Illegal combination of abstract/final
        //private static final int ACC_UNSUPPORTED = 0x0410;
        // Illegal combination of abstract/private
        //private static final int ACC_BAD         = 0x0402;

        private CallData() { }
        CallData(String signature, Location l) {
            this.rawSignature = signature;
            this.signature = parseSignature(signature);
            this.location = l;
        }

        CallData(String signature, Location l, Arguments args,
                boolean fromIntercept) {
            this.rawSignature = signature;
            this.signature = parseSignature(signature);
            this.location = l;
            this.fromIntercept = fromIntercept;
            if (fromIntercept) {
                this.args = args;
            }
        }

        CallData(String signature, Location l, Arguments args,
                boolean fromIntercept, boolean isNative) {
            this.rawSignature = signature;
            this.signature = parseSignature(signature);
            this.location = l;
            this.fromIntercept = fromIntercept;
            this.isNative = isNative;
            if (fromIntercept) {
                this.args = args;
            }
        }

        private MethodSignature parseSignature(String sigString) {
            StringTokenizer stok = new StringTokenizer(sigString, "#");

            String className = stok.nextToken();
            String methodName = stok.nextToken();
            String jniSig = stok.nextToken();
            Type returnType = Type.getReturnType(jniSig);
            Type[] argTypes = Type.getArgumentTypes(jniSig);
            
//            if (stok.hasMoreTokens()) {
//                IntegerPtr flagsOut = new IntegerPtr();
//                if (isParsableInt(stok.nextToken(), 16, flagsOut)) {
//                    accFlags = flagsOut.value;
//                }
//                else {
//                    accFlags = ACC_BAD;
//                }
//            }
//            else {
//                accFlags = ACC_UNSUPPORTED;
//            }

            return new MethodSignature(className, methodName,
                                       returnType, argTypes);
        }

        /**
         * Gets the signature of the called method.
         *
         * @return The signature of the called method.
         */
        public MethodSignature getCalledSignature() {
            return signature;
        }

        /**
         * Gets the signature string recorded by the probe for the
         * called method.
         *
         * @return The raw signature string for the called method.
         */
        public String getRawCalledSignature() {
            return rawSignature;
        }

        /**
         * Gets the name of the class in which the method call occurred.
         *
         * @return The name of the class in which the call was made.
         */
        public String getClassName() {
            return location.declaringType().name();
        }

        /**
         * Gets the name of the method in which the method call occurred.
         *
         * @return The name of the method in which the call was made.
         */
        public String getMethod() {
            return location.method().name();
        }

        /**
         * Gets the signature of the method in which the method call occurred.
         *
         * @return The JNI type signature of the method in which the
         * call was made.
         */
        public String getMethodSignature() {
            return location.method().signature();
        }

        /**
         * Reports whether the called method is a native method.
         *
         * @return <code>true</code> if the called method is a native method.
         */
        public boolean isNative() {
            return isNative;
        }

        /**
         * Reports whether the values of the arguments to the call can
         * be retrieved; this is true only for calls witnessed using
         * an interceptor.
         *
         * @return <code>true</code> if the values of the arguments to the
         * method call can be obtained.
         */
        public boolean canGetArguments() {
            return fromIntercept;
        }

        /**
         * Gets the arguments to the called method.
         *
         * <p>This method is only available if the method call was witnessed
         * using an interceptor.</p>
         *
         * @return An object that provides access to information about the
         * actual parameters to the called method.
         *
         * @throws IllegalStateException If the method call is not witnessed
         * using an interceptor.
         */
        public Arguments getArguments() {
            if (!fromIntercept) {
                throw new IllegalStateException("Call not witnessed with " +
                    "interceptor");
            }

            return args;
        }
        
//        public int getAccessFlags() throws UnsupportedOperationException {
//            if (!validateAccFlags()) {
//                accError();
//            }
//            return accFlags;
//        }
//        
//        public boolean isPublic() {
//            if (!validateAccFlags()) {
//                accError();
//            }
//            return (accFlags & 0x0001) > 0;
//        }
//        
//        public boolean isProtected() {
//            if (!validateAccFlags()) {
//                accError();
//            }
//            return (accFlags & 0x0004) > 0;
//        }
//        
//        public boolean isPrivate() {
//            if (!validateAccFlags()) {
//                accError();
//            }
//            return (accFlags & 0x0002) > 0;
//        }
//        
//        private final boolean validateAccFlags() {
//            return !((accFlags == ACC_UNSUPPORTED) || (accFlags == ACC_BAD));
//        }
//        
//        private final void accError() throws UnsupportedOperationException {
//            switch (accFlags) {
//            case ACC_UNSUPPORTED:
//                throw new UnsupportedOperationException(
//                    "Information not supported by this version " +
//                    "of Sofya instrumentation");
//            case ACC_BAD:
//                throw new UnsupportedOperationException(
//                    "Instrumentation is invalid");
//            default:
//                throw new UnsupportedOperationException("Internal error");
//            }
//        }
        

        // The following methods will return (invalid) information about
        // the probe instruction, rather than the actual instruction, so
        // they are disabled for now.

        /*public long getCodeIndex() {
            return location.codeIndex();
        }*/

        public String getSourceFile() {
            try {
                return location.sourceName();
            }
            catch (AbsentInformationException e) {
                return "<unknown>";
            }
        }

        public int getLineNumber() {
            return location.lineNumber();
        }
    }

    /**
     * Provides information about an exception event.
     */
    public static class ExceptionData {
        private com.sun.jdi.Type exception;
        private Location location;

        private ExceptionData() { }
        ExceptionData(com.sun.jdi.Type e, Location l) {
            this.exception = e;
            this.location = l;
        }

        /**
         * Gets the type of the exception that was thrown or caught.
         *
         * @return The exception's type.
         */
        public String getType() {
            return exception.name();
        }

        /**
         * Gets the type signature of the exception that was thrown or caught.
         *
         * @return The JNI type signature for the exception.
         */
        public String getTypeSignature() {
            return exception.signature();
        }

        /**
         * Gets the name of the class in which the exception was thrown
         * or caught.
         *
         * @return The name of the class where the exception was thrown
         * or caught.
         */
        public String getClassName() {
            return location.declaringType().name();
        }

        /**
         * Gets the name of the method in which the exception was thrown
         * or caught.
         *
         * @return The name of the method where the exception was thrown
         * or caught.
         */
        public String getMethod() {
            return location.method().name();
        }

        /**
         * Gets the signature of the method in which the exception was thrown
         * or caught.
         *
         * @return The JNI type signature of the method where the exception
         * was thrown or caught.
         */
        public String getMethodSignature() {
            return location.method().signature();
        }

        /**
         * Gets the bytecode offset at which the exception event occurred.
         *
         * @return The bytecode offset within the method where the exception
         * was thrown or caught.
         */
        public long getCodeIndex() {
            return location.codeIndex();
        }

        /**
         * Gets the name of the source code file corresponding to the
         * exception event.
         *
         * @return The name of the source code file which contains the code
         * for the method containing the exception event.
         * Returns &apos;&lt;unknown&gt;&apos; if this information is
         * not available.
         */
        public String getSourceFile() {
            try {
                return location.sourceName();
            }
            catch (AbsentInformationException e) {
                return "<unknown>";
            }
        }

        /**
         * Gets the line number in the source code file of the exception event.
         *
         * @return The line number of the source code statement for the
         * exception event, or -1 if this information is unavailable.
         */
        public int getLineNumber() {
            return location.lineNumber();
        }
    }

    /**
     * Provides information about a monitor acquisition or release.
     */
    public static class MonitorData {
        private Location location;

        private MonitorData() { }
        MonitorData(Location l) {
            this.location = l;
        }

        /**
         * Gets the name of the class in which the monitor event occurred.
         *
         * @return The name of the class in which the monitor was acquired or
         * released.
         */
        public String getClassName() {
            return location.declaringType().name();
        }

        /**
         * Gets the name of the method in which the monitor event occurred.
         *
         * @return The name of the method in which the monitor was acquired
         * or released.
         */
        public String getMethod() {
            return location.method().name();
        }

        /**
         * Gets the signature of the method in which the monitor event occurred.
         *
         * @return The JNI type signature of the method in which the monitor
         * was acquired or released.
         */
        public String getMethodSignature() {
            return location.method().signature();
        }

        // The following methods will return (invalid) information about
        // the probe instruction, rather than the actual instruction, so
        // they are disabled for now.

        /*public long getCodeIndex() {
            return location.codeIndex();
        }

        public String getSourceFile() {
            try {
                return location.sourceName();
            }
            catch (AbsentInformationException e) {
                return "<unknown>";
            }
        }

        public int getLineNumber() {
            return location.lineNumber();
        }*/
    }

    /**
     * Stores strong references to any <code>ObjectReference</code>
     * objects that have been requested by clients of the framework
     * (except where otherwise noted in method contracts).
     *
     * <p>The JDI does not guarantee that two <code>ObjectReference</code>
     * objects will return the same unique ID for the same remote
     * object if the first <code>ObjectReference</code> has been
     * garbage collected. This is a shortcoming of the JDI, and is a
     * problem for clients that need to be able to track object identity
     * through multiple events (which yield distinct
     * <code>ObjectReference</code> objects). This
     * cache protects the references from garbage collection,
     * ensuring consistent object ID&apos;s. The references are keyed on
     * the unique ID returned for the reference at the time of storage.
     * This allows clients to release objects from protection at their
     * discretion.</p>
     *
     * <p>If clients wish to remove objects from the cache to free
     * memory, they should be aware that the cache is visible to all
     * implementors of the interface. <strong>Thus it is the responsiblity
     * of all listener classes to define their own protocol for managing
     * the release of references! It is possible for careless clients to
     * alter the cache in ways that are destructive to other
     * clients.</strong></p>
     */
    public static TLongObjectHashMap objectRefs = new TLongObjectHashMap();
}
