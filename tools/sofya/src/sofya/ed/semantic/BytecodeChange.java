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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import sofya.base.MethodSignature;

import org.apache.bcel.generic.Type;

import gnu.trove.TLinkable;

/**
 * Structure that records an actual change made by the
 * {@link SemanticInstrumentor} to the bytecode of a method.
 * 
 * @author Alex Kinneer
 * @version 01/18/2007
 */
@SuppressWarnings("serial") // Inherited under duress from TLinkable
final class BytecodeChange
        implements TLinkable, Comparable<Object> {
    /** ID of the logical probe with which this change is associated. */
    public final int id;
    /** Constant for the event type that required this change. */
    public final byte eventCode;
    /** Bytecode offset of the first instruction of this change. */
    public int start;
    /** Total length, in bytes, of the instructions changed or
        inserted. */
    public final short length;
    /** Flag indicating whether the change precedes the program code
        for the event to be observed. */
    public final boolean precedes;
    /** Object recording data about the interceptor call replacement applied
        at this location, if applicable. */
    public final InterceptRecord interceptor;

    /** Flag indicating the type of bytecode change that was applied. */
    public final byte action;
    /** Constant flag value to indicate that this change represents
        inserted instructions. */
    public static final byte ACTION_INSERT = 1;
    /** Constant flag value to indicate that this change represents
        replacement of an invoke instruction to implement a
        call interceptor. */
    public static final byte ACTION_CALL_INTERCEPT = 2;
    /** Constant flag value to indicate that this change represents
        replacement of a field instruction to implement a
        field interceptor. */
    public static final byte ACTION_FIELD_INTERCEPT = 3;

    // Bytecode changes are stored in a directly linked list for
    // maximum efficiency
    private TLinkable prev;
    private TLinkable next;

    private BytecodeChange() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new bytecode change record for inserted instructions.
     * 
     * @param id ID of the logical probe with which this change
     * is associated.
     * @param eventCode Constant for the event type that required
     * this change.
     * @param start Bytecode offset of the first instruction
     * of this change.
     * @param end Bytecode offset of the first instruction immediately
     * following this change.
     * @param precedes Flag indicating whether the change precedes the
     * program code for the event to be observed.
     */
    BytecodeChange(int id, byte eventCode, int start, int end,
            boolean precedes) {
        this.id = id;
        this.eventCode = eventCode;
        this.start = start;
        this.length = (short) (end - start);
        this.precedes = precedes;
        this.action = ACTION_INSERT;
        this.interceptor = null;
    }

    /**
     * Creates a new bytecode change record for inserted instructions.
     * 
     * @param id ID of the logical probe with which this change
     * is associated.
     * @param eventCode Constant for the event type that required
     * this change.
     * @param start Bytecode offset of the first instruction
     * of this change.
     * @param length Total length, in bytes, of the instructions changed
     * or inserted.
     * @param precedes Flag indicating whether the change precedes the
     * program code for the event to be observed.
     */
    BytecodeChange(int id, byte eventCode, int start, short length,
            boolean precedes) {
        this.id = id;
        this.eventCode = eventCode;
        this.start = start;
        this.length = length;
        this.precedes = precedes;
        this.action = ACTION_INSERT;
        this.interceptor = null;
    }

    /**
     * Creates a new bytecode change record for a call interceptor
     * invoke instruction replacement.
     * 
     * @param id ID of the logical probe with which this change
     * is associated.
     * @param eventCode Constant for the event type that required
     * this change.
     * @param offset Bytecode offset of the affected instruction.
     * @param opcode Original opcode of the invoke instruction that
     * was replaced.
     * @param callTarget Method targeted by the invoke instruction
     * that was replaced.
     */
    BytecodeChange(int id, byte eventCode, int offset, short opcode,
            MethodSignature callTarget) {
        this.id = id;
        this.eventCode = eventCode;
        this.start = offset;
        this.length = 3;
        this.precedes = false;
        this.action = ACTION_CALL_INTERCEPT;
        this.interceptor = new CallInterceptRecord(opcode, callTarget);
    }
    
    /**
     * Creates a new bytecode change record for a field interceptor
     * instruction replacement.
     * 
     * @param id ID of the logical probe with which this change
     * is associated.
     * @param eventCode Constant for the event type that required
     * this change.
     * @param offset Bytecode offset of the affected instruction.
     * @param opcode Original opcode of the field instruction that
     * was replaced.
     * @param className Name of the class declaring the field that was
     * the target of the field instruction.
     * @param fieldName Name of the field that was the target of the
     * field instruction.
     * @param fieldType The java type of the field that was the target
     * of the field instruction.
     */
    BytecodeChange(int id, byte eventCode, int offset, short opcode,
            String className, String fieldName, Type fieldType) {
        this.id = id;
        this.eventCode = eventCode;
        this.start = offset;
        this.length = 3;
        this.precedes = false;
        this.action = ACTION_FIELD_INTERCEPT;
        this.interceptor = new FieldInterceptRecord(opcode, className,
            fieldName, fieldType);
    }

    public int hashCode() {
        return this.start;
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        try {
            BytecodeChange other = (BytecodeChange) o;
            if ((this.id != other.id)
                    || (this.start != other.start)
                    || (this.eventCode != other.eventCode)
                    || (this.length != other.length)
                    || (this.action != other.action)) {
                return false;
            }
            switch (action) {
            case ACTION_CALL_INTERCEPT:
            case ACTION_FIELD_INTERCEPT:
                return this.interceptor.equals(other.interceptor);
            default:
                throw new AssertionError("Unknown action type");
            }
        }
        catch (ClassCastException e) {
            return false;
        }
    }

    public int compareTo(Object o) {
        BytecodeChange other = (BytecodeChange) o;
        return (this.start - other.start);
    }

    public TLinkable getNext() {
        return next;
    }

    public TLinkable getPrevious() {
        return prev;
    }

    public void setNext(TLinkable next) {
        this.next = next;
    }

    public void setPrevious(TLinkable prev) {
        this.prev = prev;
    }

    public String toString() {
        return "[ " + id + ": " + start + ", " + length + " ]";
    }
    
    static abstract class InterceptRecord {
        final byte icptType;
        
        /** Original opcode of the instruction that was replaced. */
        final short opcode;
        
        InterceptRecord() {
            throw new AssertionError("Illegal constructor");
        }
        
        InterceptRecord(byte type, short opcode) {
            this.icptType = type;
            this.opcode = opcode;
        }
        
        abstract void serialize0(DataOutputStream out) throws IOException;
        
        void serialize(DataOutputStream out) throws IOException {
            out.writeByte(icptType);
            
            switch (icptType) {
            
            }
            serialize0(out);
        }
        
        static InterceptRecord deserialize(DataInputStream in) throws IOException {
            byte icptType = in.readByte();
            
            switch (icptType) {
            case CallInterceptRecord.TYPE:
                return CallInterceptRecord.deserialize(in);
            case FieldInterceptRecord.TYPE:
                return FieldInterceptRecord.deserialize(in);
            default:
                throw new AssertionError("Unknown interceptor type");
            }
        }
    }
    
    static final class CallInterceptRecord extends InterceptRecord {
        /** Method targeted by the invoke instruction replaced to implement
            the call interceptor. */
        final MethodSignature callTarget;
        
        static final byte TYPE = 1;
        
        CallInterceptRecord(short opcode, MethodSignature callTarget) {
            super(TYPE, opcode);
            this.callTarget = callTarget;
        }
        
        void serialize0(DataOutputStream out) throws IOException {
            out.writeShort(opcode);
            out.writeUTF(callTarget.getClassName());
            out.writeUTF(callTarget.getMethodName());
            out.writeUTF(callTarget.getTypeSignature());
        }
        
        static CallInterceptRecord deserialize(DataInputStream in)
                throws IOException {
            return new CallInterceptRecord(in.readShort(),
                new MethodSignature(in.readUTF(), in.readUTF(), in.readUTF()));
        }
        
        public boolean equals(Object obj) {
            try {
                return this.callTarget.equals(
                    ((CallInterceptRecord) obj).callTarget);
            }
            catch (ClassCastException e) {
                return false;
            }
        }
        
        public int hashCode() {
            return callTarget.hashCode();
        }
        
        public String toString() {
            return callTarget.toString();
        }
    }
    
    static final class FieldInterceptRecord extends InterceptRecord {
        final String className;
        final String fieldName;
        final Type fieldType;
        
        static final byte TYPE = 2;
        
        FieldInterceptRecord(short opcode, String className, String fieldName,
                Type fieldType) {
            super(TYPE, opcode);
            this.className = className;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }
        
        void serialize0(DataOutputStream out) throws IOException {
            out.writeShort(opcode);
            out.writeUTF(className);
            out.writeUTF(fieldName);
            out.writeUTF(fieldType.getSignature());
        }
        
        static FieldInterceptRecord deserialize(DataInputStream in)
                throws IOException {
            return new FieldInterceptRecord(in.readShort(), in.readUTF(),
                in.readUTF(), Type.getType(in.readUTF()));
        }
        
        public boolean equals(Object obj) {
            try {
                FieldInterceptRecord that = (FieldInterceptRecord) obj;
                return (this.className.equals(that.className) &&
                    this.fieldName.equals(that.fieldName) &&
                    this.fieldType.equals(that.fieldType));
            }
            catch (ClassCastException e) {
                return false;
            }
        }
        
        public int hashCode() {
            return className.hashCode() + fieldName.hashCode()
                << (fieldType.hashCode() + 1);
        }
        
        public String toString() {
            return className + "." + fieldName +
                "<" + fieldType.toString() + ">";
        }
    }
}
