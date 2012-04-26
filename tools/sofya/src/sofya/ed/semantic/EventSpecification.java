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

import java.util.List;
import java.util.Set;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.jdi.Type;

import sofya.base.ProgramUnit;
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.InvokeInstruction;

/**
 * Defines the interface to be implemented by classes which supply a
 * specification of events to be produced by a
 * {@link SemanticEventDispatcher}.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public interface EventSpecification {
    /**
     * Type-safe enumeration for the field related instructions.
     */
    public static final class FieldType {
        private int typeCode = -1;

        static final int IGETSTATIC   = 0;
        static final int IPUTSTATIC   = 1;
        static final int IGETFIELD    = 2;
        static final int IPUTFIELD    = 3;

        public static final FieldType GETSTATIC =
            new FieldType(IGETSTATIC);
        public static final FieldType PUTSTATIC =
            new FieldType(IPUTSTATIC);
        public static final FieldType GETFIELD =
            new FieldType(IGETFIELD);
        public static final FieldType PUTFIELD =
            new FieldType(IPUTFIELD);

        private FieldType() { }
        private FieldType(int typeCode) {
            this.typeCode = typeCode;
        }

        int toInt() { return typeCode; }

        static FieldType fromInt(int typeCode) {
            switch (typeCode) {
            case IGETSTATIC:
                return FieldType.GETSTATIC;
            case IPUTSTATIC:
                return FieldType.PUTSTATIC;
            case IGETFIELD:
                return FieldType.GETFIELD;
            case IPUTFIELD:
                return FieldType.PUTFIELD;
           default:
                throw new IllegalArgumentException();
            }
        }

        static int mapFromOpcode(int opcode) {
            switch (opcode) {
            case Constants.GETSTATIC:
                return IGETSTATIC;
            case Constants.PUTSTATIC:
                return IPUTSTATIC;
            case Constants.GETFIELD:
                return IGETFIELD;
            case Constants.PUTFIELD:
                return IPUTFIELD;
            default:
                throw new IllegalArgumentException();
            }
        }
        
        boolean isRead() {
            switch (typeCode) {
            case Constants.GETSTATIC:
                return true;
            case Constants.PUTSTATIC:
                return false;
            case Constants.GETFIELD:
                return true;
            case Constants.PUTFIELD:
                return false;
            default:
                throw new IllegalArgumentException();
            }
        }

        public String toString() {
            switch (typeCode) {
            case IGETSTATIC:
                return "GETSTATIC";
            case IPUTSTATIC:
                return "PUTSTATIC";
            case IGETFIELD:
                return "GETFIELD";
            case IPUTFIELD:
                return "PUTFIELD";
            default:
                throw new SofyaError();
            }
        }
    }

    /**
     * Type-safe enumeration for the invoke instructions.
     */
    public static final class CallType {
        private int typeCode = -1;

        static final int ICONSTRUCTOR = 0;
        static final int ISTATIC      = 1;
        static final int IVIRTUAL     = 2;
        static final int IINTERFACE   = 3;

        public static final CallType CONSTRUCTOR =
            new CallType(ICONSTRUCTOR);
        public static final CallType STATIC =
            new CallType(ISTATIC);
        public static final CallType VIRTUAL =
            new CallType(IVIRTUAL);
        public static final CallType INTERFACE =
            new CallType(IINTERFACE);

        private CallType() { }
        private CallType(int typeCode) {
            this.typeCode = typeCode;
        }

        int toInt() { return typeCode; }

        static CallType fromInt(int typeCode) {
            switch (typeCode) {
            case ICONSTRUCTOR:
                return CONSTRUCTOR;
            case ISTATIC:
                return STATIC;
            case IVIRTUAL:
                return VIRTUAL;
            case IINTERFACE:
                return INTERFACE;
            default:
                throw new IllegalArgumentException();
            }
        }

        static int mapFromInstruction(InvokeInstruction i,
                ConstantPoolGen cpg) {
            switch (i.getOpcode()) {
            case Constants.INVOKESPECIAL:
                if (i.getMethodName(cpg).equals("<init>")) {
                    return ICONSTRUCTOR;
                }
                else {
                    return IVIRTUAL;
                }
            case Constants.INVOKESTATIC:
                return ISTATIC;
            case Constants.INVOKEVIRTUAL:
                return IVIRTUAL;
            case Constants.INVOKEINTERFACE:
                return IINTERFACE;
            default:
                throw new IllegalArgumentException();
            }
        }

        public String toString() {
            switch (typeCode) {
            case ICONSTRUCTOR:
                return "INVOKESPECIAL";
            case ISTATIC:
                return "INVOKESTATIC";
            case IVIRTUAL:
                return "INVOKEVIRTUAL";
            case IINTERFACE:
                return "INVOKEINTERFACE";
            default:
                throw new SofyaError();
            }
        }
    }

    /**
     * Type-safe enumeration for the method entry and exit events.
     */
    public static final class MethodAction {
        private int typeCode = -1;

        static final int IVIRTUAL_ENTER = 0;
        static final int IVIRTUAL_EXIT  = 1;
        static final int ISTATIC_ENTER  = 2;
        static final int ISTATIC_EXIT   = 3;

        public static final MethodAction VIRTUAL_ENTER =
            new MethodAction(IVIRTUAL_ENTER);
        public static final MethodAction VIRTUAL_EXIT =
            new MethodAction(IVIRTUAL_EXIT);
        public static final MethodAction STATIC_ENTER =
            new MethodAction(ISTATIC_ENTER);
        public static final MethodAction STATIC_EXIT =
            new MethodAction(ISTATIC_EXIT);

        private MethodAction() { }
        private MethodAction(int typeCode) {
            this.typeCode = typeCode;
        }

        int toInt() { return typeCode; }

        static MethodAction fromInt(int typeCode) {
            switch (typeCode) {
            case IVIRTUAL_ENTER:
                return VIRTUAL_ENTER;
            case IVIRTUAL_EXIT:
                return VIRTUAL_EXIT;
            case ISTATIC_ENTER:
                return STATIC_ENTER;
            case ISTATIC_EXIT:
                return STATIC_EXIT;
            default:
                throw new IllegalArgumentException();
            }
        }

        public String toString() {
            switch (typeCode) {
            case IVIRTUAL_ENTER:
                return "virtual_method_enter";
            case IVIRTUAL_EXIT:
                return "virtual_method_exit";
            case ISTATIC_ENTER:
                return "static_method_enter";
            case ISTATIC_EXIT:
                return "static_method_exit";
            default:
                throw new SofyaError();
            }
        }
    }

    /**
     * Type-safe enumeration for the monitor events.
     */
    public static final class MonitorType {
        private int typeCode = -1;

        static final int ICONTEND     = 0;
        static final int IACQUIRE     = 1;
        static final int IPRE_RELEASE = 2;
        static final int IRELEASE     = 3;

        public static final MonitorType CONTEND =
            new MonitorType(ICONTEND);
        public static final MonitorType ACQUIRE =
            new MonitorType(IACQUIRE);
        public static final MonitorType PRE_RELEASE =
            new MonitorType(IPRE_RELEASE);
        public static final MonitorType RELEASE =
            new MonitorType(IRELEASE);

        private MonitorType() { }
        private MonitorType(int typeCode) {
            this.typeCode = typeCode;
        }

        int toInt() { return typeCode; }

        static MonitorType fromInt(int typeCode) {
            switch (typeCode) {
            case ICONTEND:
                return CONTEND;
            case IACQUIRE:
                return ACQUIRE;
            case IPRE_RELEASE:
                return PRE_RELEASE;
            case IRELEASE:
                return RELEASE;
            default:
                throw new IllegalArgumentException();
            }
        }

        public String toString() {
            switch (typeCode) {
            case ICONTEND:
                return "monitor_contend";
            case IACQUIRE:
                return "monitor_acquire";
            case IPRE_RELEASE:
                return "monitor_pre_release";
            case IRELEASE:
                return "monitor_release";
            default:
                throw new SofyaError();
            }
        }
    }
    
    /**
     * Type-safe enumeration for the array element events.
     */
    public static final class ArrayElementType {
        private int typeCode = -1;

        static final int ILOAD  = 0;
        static final int ISTORE = 1;

        public static final ArrayElementType LOAD =
            new ArrayElementType(ILOAD);
        public static final ArrayElementType STORE =
            new ArrayElementType(ISTORE);

        private ArrayElementType() { }
        private ArrayElementType(int typeCode) {
            this.typeCode = typeCode;
        }

        int toInt() { return typeCode; }

        static ArrayElementType fromInt(int typeCode) {
            switch (typeCode) {
            case ILOAD:
                return LOAD;
            case ISTORE:
                return STORE;
            default:
                throw new IllegalArgumentException();
            }
        }

        public String toString() {
            switch (typeCode) {
            case ILOAD:
                return "array_element_load";
            case ISTORE:
                return "array_element_store";
            default:
                throw new SofyaError();
            }
        }
    }
    
    /**
     * Specifies the bounds on the indexes of elements to be witnessed
     * in an array of a given type.
     * 
     * <p>Note that if the minimum index is greater than the maximum index,
     * the &quot;tails&quot; of the array are specified; that is,
     * elements from the start of the array up to the maximum index, and
     * elements from the end of the array down to the minimum index are
     * to be witnessed.</p>
     */
    public static final class ArrayElementBounds {
        /** Constant indicating that there is no bound. */
        public static final int NO_BOUND = -1;

        /** Element type of arrays to which the bounds apply. */
        org.apache.bcel.generic.Type javaType;
        /** The minimum index to be observed. */
        int min = NO_BOUND;
        /** The maximum index to be observed. */
        int max = NO_BOUND;
        
        /**
         * Creates an array element bounds that includes all elements
         * by default.
         * 
         * <p>The type must be initialized after instantiation. This
         * constructor is intended for deserialization.</p>
         */
        ArrayElementBounds() { }
        
        /**
         * Creates an array element bounds for a given element type, that
         * includes all elements by default.
         * 
         * @param javaType Element type of arrays to which the bounds apply.
         */
        public ArrayElementBounds(org.apache.bcel.generic.Type javaType) {
            this.javaType = javaType;
        }
        
        /**
         * Creates an array element bounds for a given element type, with
         * specified minimum and maximum indexes of elements to be witnessed.
         * 
         * @param javaType Element type of arrays to which the bounds apply.
         * @param min Minimum index of array elements to be witnessed.
         * @param max Maximum index of array elements to be witnessed.
         */
        public ArrayElementBounds(org.apache.bcel.generic.Type javaType,
                int min, int max) {
            this(javaType);
            this.min = min;
            this.max = max;
        }
        
        public org.apache.bcel.generic.Type getType() {
            return javaType;
        }
        
        public void setType(org.apache.bcel.generic.Type javaType) {
            this.javaType = javaType;
        }
        
        public int getMinimum() {
            return min;
        }
        
        public void setMinimum(int min) {
            this.min = min;
        }
       
        public int getMaximum() {
            return max;
        }
        
        public void setMaximum(int max) {
            this.max = max;
        }
        
        public String toString() {
            return javaType + "{ " + min + ", " + max + " }";
        }
        
        /**
         * Creates a deep copy of this array element bounds.
         */
        ArrayElementBounds copy() {
            ArrayElementBounds theCopy = new ArrayElementBounds();
            theCopy.javaType = this.javaType;
            theCopy.min = this.min;
            theCopy.max = this.max;
            return theCopy;
        }
        
        /**
         * Copies the bounds associated with this object to another
         * array element bounds object.
         */
        void copyTo(ArrayElementBounds sink) {
            sink.min = this.min;
            sink.max = this.max;
        }
        
        /**
         * Non-destructively merges the bounds from another array element
         * bounds with the current bounds; that is, bounds are only
         * copied if they are currently unbounded.
         */
        void merge(ArrayElementBounds bounds) {
            if (this.min == NO_BOUND) {
                this.min = bounds.min;
            }
            if (this.max == NO_BOUND) {
                this.max = bounds.max;
            }
        }
        
        /**
         * Non-destructively merges the bounds from a global array
         * element bounds with the current bounds. This merge has
         * special conditions to ensure that the global bounds can
         * only narrow the set of witnessed array elements, if
         * any overriding bounds are specified with the specific
         * event request.
         */
        void mergeGlobal(ArrayElementBounds globalBounds) {
            if (this.min == NO_BOUND) {
                if (this.max == NO_BOUND) {
                    this.min = globalBounds.min;
                }
                else if (globalBounds.min <= this.max) {
                    this.min = globalBounds.min;
                }
            }
            if (this.max == NO_BOUND) {
                if (this.min == NO_BOUND) {
                    this.max = globalBounds.max;
                }
                else if (globalBounds.max >= this.min) {
                    this.max = globalBounds.max;
                }
            }
        }
        
        void serialize(DataOutputStream out) throws IOException {
            out.writeInt(min);
            out.writeInt(max);
        }
        
        void deserialize(DataInputStream in) throws IOException {
            this.min = in.readInt();
            this.max = in.readInt();
        }
    }
    
    /**
     * Gets the key name associated with this specification.
     * 
     * @return The name that uniquely identifies this specification.
     */
    String getKey();

    /**
     * Gets the set of program units defining the classes that comprise
     * the entire system.
     *
     * @param intoSet If non-<code>null</code>, the result of the call will
     * be put into this set, and it will be used as the return value;
     * otherwise a new set is created and returned.
     *
     * @return A set containing all of the program units comprising the
     * entire system; if created by this method, the set will be
     * unmodifiable.
     */
    Set<ProgramUnit> getSystemClassUnits(Set<ProgramUnit> intoSet);
    
    /**
     * Gets the set of classes comprising the entire system.
     *
     * @param intoSet If non-<code>null</code>, the result of the call will
     * be put into this set, and it will be used as the return value;
     * otherwise a new set is created and returned.
     *
     * @return A set containing all of the classes comprising the system;
     * if created by this method, the set will be unmodifiable.
     */
    Set<String> getSystemClassNames(Set<String> intoSet);


    /**
     * Gets the set of program units defining the classes that comprise
     * the default observable module.
     *
     * @param intoSet If non-<code>null</code>, the result of the call will
     * be put into this set, and it will be used as the return value;
     * otherwise a new set is created and returned.
     *
     * @return A set containing all of the class units comprising the
     * default observable module; if created by this method, the set will be
     * unmodifiable.
     */
    Set<ProgramUnit> getModuleClassUnits(Set<ProgramUnit> intoSet);
    
    /**
     * Gets the set of classes comprising the default observable module.
     *
     * @param intoSet If non-<code>null</code>, the result of the call will
     * be put into this set, and it will be used as the return value;
     * otherwise a new set is created and returned.
     *
     * @return A set containing all of the classes for which observables
     * are included, unless otherwise constrained; if created by this method,
     * the set will be unmodifiable.
     */
    Set<String> getModuleClassNames(Set<String> intoSet);

    /**
     * Checks whether an allocation of a class by a NEW instruction
     * in a given location should be witnessed (is part of the specification).
     *
     * @param newClass Name of the class to check for inclusion.
     * @param inMethod Method in which the NEW instruction is executed.
     *
     * @return <code>true</code> if the NEW instruction is included in the
     * specification and should be observed.
     */
    boolean witnessNewObject(String newClass, MethodGen inMethod);

    /**
     * Checks whether the field referenced by a field instruction in
     * a given location is part of the specification and should be witnessed.
     *
     * @param fi Field instruction to be checked for inclusion in the
     * specification.
     * @param cpg Constant pool for the class containing the instruction.
     * @param fType Type of the field to be checked for inclusion.
     * @param inMethod Method in which the field instruction is executed.
     *
     * @return Returns <code>true</code> if a field event at the current
     * location is included in the specification.
     */
    boolean witnessField(FieldInstruction fi, ConstantPoolGen cpg,
            FieldType fType, MethodGen inMethod);

    /** Bitmask which indicates that no events should be witnessed for a
        field. */
    static final int FIELD_WITNESS_NONE  = 0;
    /** Bitmask to check the bit specifying that read events should be
        witnessed for a field. */
    static final int FIELD_WITNESS_READ  = 1;
    /** Bitmask to check the bit specifying that write events should be
        witnessed for a field. */
    static final int FIELD_WITNESS_WRITE = 2;

    /**
     * Checks whether a field event occurring in a given location
     * is included in the specification.
     *
     * @param fieldName Fully qualified name of the field to be checked for
     * inclusion in the specification.
     * @param fType Field type of the field to be checked for inclusion.
     * @param javaType Java type of the field to be checked for inclusion.
     * @param className Name of the class in which the field instruction
     * is executed.
     * @param methodName Name of the the method in which the field
     * instruction is executed.
     * @param signature JDI-style signature of the method in which the
     * instruction is executed.
     *
     * @return <code>true</code> if the specified field event occurring in
     * the given method should be observed.
     */
    boolean witnessField(String fieldName, FieldType fType, Type javaType,
            String className, String methodName, String signature);

    /**
     * Checks whether a named field is included in the specification.
     *
     * <p>This method is only called at runtime, by the event dispatcher,
     * to query whether a field is to be monitored by watchpoints.</p>
     *
     * <p>This method is permitted to be <em>heuristic</em>, in the sense
     * that it may in some circumstances indicate that a field is
     * observable in some way when in fact it is not. A correct response
     * should be attempted on a best-effort basis.</p>
     *
     * @param fieldName Fully qualified name of the field to be checked for
     * inclusion in the specification.
     * @param isStatic Specifies whether it is a static field.
     * @param javaType Type of the field to be checked for inclusion
     * in the specification.
     *
     * @return A bitmask indicating which events on the field should be
     * witnessed. If no bits are set, the field should not be witnessed
     * at all. If the lowest bit is set, reads from the field should be
     * witnessed. If the second lowest bit is set, writes to the field
     * should be witnessed.
     */
    int witnessField(String fieldName, boolean isStatic, String javaType);
    
    /**
     * Checks whether a named field is included in the specification.
     *
     * <p>This method is only called during instrumentation,
     * to query whether a field is to be monitored by breakpoints.</p>
     *
     * <p>This method is permitted to be <em>heuristic</em>, in the sense
     * that it may in some circumstances indicate that a field is
     * observable in some way when in fact it is not. A correct response
     * should be attempted on a best-effort basis.</p>
     *
     * @param fieldName Fully qualified name of the field to be checked for
     * inclusion in the specification.
     * @param isStatic Specifies whether it is a static field.
     * @param javaType Type of the field to be checked for inclusion
     * in the specification.
     *
     * @return A bitmask indicating which events on the field should be
     * witnessed. If no bits are set, the field should not be witnessed
     * at all. If the lowest bit is set, reads from the field should be
     * witnessed. If the second lowest bit is set, writes to the field
     * should be witnessed.
     */
    int witnessField(String fieldName, boolean isStatic,
            org.apache.bcel.generic.Type javaType);
    
    /**
     * Checks whether a method call in a given location is included in
     * the specification.
     *
     * @param call Invoke instruction to be checked for exclusion in the
     * specification.
     * @param cpg Constant pool for the class containing the invoke instruction.
     * @param inMethod Method in which the call is executed.
     *
     * @return <code>true</code> if the call is to a method included in the
     * specification.
     */
    boolean witnessCall(InvokeInstruction call, ConstantPoolGen cpg,
            MethodGen inMethod);

    /**
     * Checks whether a method call should be observed using an
     * &quot;interceptor&quot; that can provide additional data about the
     * call.
     *
     * <p>The principle benefit of witnessing a call using an interceptor
     * is that the values of the arguments to the method can be obtained.
     * Important information can be obtained about calls for which subsequent
     * {@link EventListener#virtualMethodEnterEvent}s cannot be raised, either
     * because it is not possible (such as with <code>native</code> methods),
     * or desirable (such as with <code>Thread.start()</code> or
     * <code>Thread.join()</code> methods). However, there is considerable
     * extra cost and potential interference associated with an interceptor,
     * so they generally should not be used for other method calls.</p>
     *
     * @param call Invoke instruction to be checked for observation using
     * an interceptor.
     * @param cpg Constant pool for the class containing the invoke instruction.
     *
     * @return <code>true</code> if the call is to a method that should be
     * witnessed using an interceptor.
     */
    boolean useCallInterceptor(InvokeInstruction call, ConstantPoolGen cpg);

    /**
     * Checks whether entry into a constructor is included in the specification.
     *
     * @param mg Constructor to be checked for inclusion in the
     * specification.
     *
     * @return <code>true</code> if entry into the constructor is included
     * in the specification.
     */
    boolean witnessConstructorEntry(MethodGen mg);

    /**
     * Checks whether exit from a constructor is included in the specification.
     *
     * @param mg Constructor to be checked for inclusion in the
     * specification.
     *
     * @return <code>true</code> if exit from the constructor is included
     * in the specification.
     */
    boolean witnessConstructorExit(MethodGen mg);

    /**
     * Checks whether entry into a method is included in the specification.
     *
     * <p>This query treats interface methods as virtual methods
     * since they are also dynamically bound, a behavior that distinguishes
     * this query from the call invocation events where interface method
     * calls and other virtual calls are treated as distinct events.</p>
     *
     * @param mg Virtual method to be checked for inclusion in the
     * specification.
     *
     * @return <code>true</code> if entry into the virtual method is included
     * in the specification.
     */
    boolean witnessMethodEntry(MethodGen mg);

    /**
     * Checks whether exit from a method is included in the specification.
     *
     * <p>This query treats interface methods as virtual methods
     * since they are also dynamically bound, a behavior that distinguishes
     * this query from the call invocation events where interface method
     * calls and other virtual calls are treated as distinct events.</p>
     *
     * @param mg Virtual method to be checked for inclusion in the
     * specification.
     *
     * @return <code>true</code> if exit from the virtual method is included
     * in the specification.
     */
    boolean witnessMethodExit(MethodGen mg);

    /**
     * Checks whether any monitor events of a given type occurring in a
     * given location are included in the specification.
     *
     * <p>This is a helper method which is used by the instrumentor to optimize
     * away the associated instrumentation if no monitor events of a certain
     * type are to be witnessed at all.</p>
     *
     * @param type Monitor event type for which the check should be performed.
     * @param inMethod Method in which the monitor events are executed.
     *
     * @return <code>true</code> if monitor events of the specified type
     * may be witnessed, <code>false</code> otherwise.
     */
    boolean witnessAnyMonitor(MonitorType type, MethodGen inMethod);

    /**
     * Checks whether events associated with monitors on instances of a
     * given class should be witnessed.
     *
     * @param className Fully qualified name of the class to be checked for
     * monitor event inclusion in the specification.
     * @param type Type of monitor event.
     *
     * @return <code>true</code> if the given monitor events should be
     * witnessed for monitors on instances of the given class,
     * <code>false</code> otherwise.
     */
    boolean witnessMonitor(String className, MonitorType type);

    /**
     * Checks whether the throwing of an exception of a given class from a
     * given location is included in the specification.
     *
     * @param exceptionClass Fully qualified name of the exception class
     * to be checked for inclusion in the specification.
     * @param className Name of the class in which the throw occurs.
     * @param methodName Name of the the method in which the throw occurs.
     * @param signature JDI-style signature of the method in which the
     * throw occurs.
     *
     * @return <code>true</code> if the throwing of the given exception
     * from the specified location should be observed, <code>false</code>
     * otherwise.
     */
    boolean witnessThrow(String exceptionClass, String className,
            String methodName, String signature);

    /**
     * Checks whether the throwing of an exception of a given class is
     * included in the specification; that is, there is some location from
     * which throwing an exception of the given class is considered an
     * observable event.
     *
     * @param exceptionClass Fully qualified name of the exception class
     * to be checked for inclusion in the specification.
     *
     * @return <code>true</code> if the throwing of the given exception
     * from some location in the program should be observed,
     * <code>false</code> otherwise.
     */
    boolean witnessThrow(String exceptionClass);

    /**
     * Checks whether the catching of an exception of a given class at a
     * given location is included in the specification.
     *
     * @param exceptionClass Fully qualified name of the exception class
     * to be checked for inclusion in the specification.
     * @param inMethod Method in which the catch occurs.
     *
     * @return <code>true</code> if the catching of the given exception
     * at the specified location should be observed, <code>false</code>
     * otherwise.
     */
    boolean witnessCatch(String exceptionClass, MethodGen inMethod);

    /**
     * Checks whether the catching of an exception of a given class is
     * included in the specification; that is, there is some location at
     * which catching an exception of the given class is considered an
     * observable event.
     *
     * @param exceptionClass Fully qualified name of the exception class
     * to be checked for inclusion in the specification.
     *
     * @return <code>true</code> if the catching of the given exception
     * at some location in the program should be observed,
     * <code>false</code> otherwise.
     */
    boolean witnessCatch(String exceptionClass);

    /**
     * Checks whether entry into a static intializer is included in the
     * specification.
     *
     * @param className Fully qualified name of the class containing
     * the static initializer to be checked for inclusion in the
     * specification.
     *
     * @return <code>true</code> if entry into the static initializer is
     * included in the specification.
     */
    boolean witnessStaticInitializerEntry(String className);
    
    /**
     * Checks whether an array element event occurring in a given location
     * is included in the specification.
     *
     * @param ai Array element instruction to be checked for inclusion.
     * @param cpg Constant pool for the class containing the instruction.
     * @param inMethod Method in which the instruction is executed.
     * @param elemActionType The type of array element action (access
     * or store).
     * @param witnessed <strong>[Out]</strong> List of bounds on the
     * indexes of array elements to be witnessed, for any applicable
     * element types; the specification is responsible for populating
     * this list.
     *
     * @return <code>true</code> if the specified array element event
     * occurring in the given method should be observed; if this method
     * returns <code>true</code>, there must be at least one item in
     * the list passed to the <code>witnessed</code> argument.
     */
    boolean witnessArrayElement(ArrayInstruction ai,
            ConstantPoolGen cpg, MethodGen inMethod,
            ArrayElementType elemActionType,
            List<ArrayElementBounds> witnessed);

    /**
     * Serializes the specification as binary data.
     *
     * @param stream Stream to which the specification is serialized.
     *
     * @throws IOException On any I/O error that prevents serialization.
     */
    public void serialize(DataOutputStream stream)
            throws IOException;

    /**
     * Deserializes the specification from binary data.
     *
     * <p>An instance of the class will need to be instantiated first, likely
     * by reflection. If called on an existing instance, that specification
     * will be destroyed (overwritten).</p>
     *
     * @param stream Stream from which the specification is deserialized.
     *
     * @return A reference to the specification.
     *
     * @throws IOException On any I/O error that prevents deserialization.
     */
    public EventSpecification deserialize(DataInputStream stream)
            throws IOException;
}
