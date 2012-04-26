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
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import com.sun.jdi.Field;
import com.sun.jdi.request.BreakpointRequest;

import sofya.ed.semantic.EventSpecification.FieldType;
import static sofya.ed.semantic.SemanticConstants.*;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

/**
 * Records data about the field interceptor methods added to a class,
 * and the locations of associated breakpoints to be requested to
 * capture field events.
 * 
 * @author Alex Kinneer
 * @version 01/15/2007
 */
@SuppressWarnings("unchecked")
class FieldInterceptorData {
    /** The full set of field interceptor information structures for the
        class (used to efficiently set breakpoints during class loading). */
    private final Set<Object> icptMethods = new THashSet();

    /** Sets of field interceptors keyed on the field type, to support
        adaptive instrumentation. */
    private Map<Object, Object>[] fieldIcpts;
    
    /**
     * Information structure recording information about a field
     * interceptor method. 
     */
    static final class FieldInterceptor {
        /** Sofya event type code for the field being intercepted. */
        final byte eventCode;
        
        /** Name of the class declaring the intercepted field. */
        public final String fieldClass;
        /** Name of the intercepted field. */
        public final String fieldName;

        /** Name of the interceptor method. */
        final String methodName;
        /** JNI signature of the interceptor method. */
        final String jniSignature;
        /** Code offset within the interceptor method at which the JDI
            should set the breakpoint to capture the field event. */
        final int breakpointOffset;
        
        /** Breakpoint request associated with the interceptor (filled in
            at runtime). */
        public BreakpointRequest request;
        /** JDI field object for the intercepted field (filled in at
            runtime). */
        public Field field;
        
        private FieldInterceptor() {
            throw new AssertionError("Illegal constructor");
        }
        
        FieldInterceptor(byte eventCode, String fieldClass, String fieldName,
                String methodName, String jniSignature, int breakpointOffset) {
            this.eventCode = eventCode;
            this.fieldClass = fieldClass;
            this.fieldName = fieldName;
            this.methodName = methodName;
            this.jniSignature = jniSignature;
            this.breakpointOffset = breakpointOffset;
        }
        
        public String toString() {
            return "{ " + fieldClass + "." + fieldName + "=" + methodName +
                "." + jniSignature + ":" + breakpointOffset + " }";
        }
    }
    
    FieldInterceptorData() {
    }
    
    Set<Object> getInterceptors() {
        return icptMethods;
    }
    
    void addInterceptor(FieldInterceptor interceptor) {
        icptMethods.add(interceptor);
    }
    
    /**
     * Gets an interceptor information structure for a field.
     * 
     * @param fieldName Fully-qualified name of the field for which to
     * retrieve the interceptor information structure.
     * @param fldType Field instruction type for which to retreive the
     * interceptor information structure.
     * 
     * @return The field interceptor information structure for the
     * field interceptor used to capture events of the specified type
     * on the specified field.
     */
    FieldInterceptor getInterceptor(String fieldName, FieldType fldType) {
        if (fieldIcpts == null) {
            // We generate the per-event-type maps lazily...
            
            fieldIcpts = new THashMap[4];
            for (int i = 0; i < 4; i++) {
                fieldIcpts[i] = new THashMap();
            }
            
            FieldInterceptor match = null;
            boolean forRead = fldType.isRead();
            
            int size = icptMethods.size();
            Iterator<Object> iter = icptMethods.iterator();
            for (int i = size; i-- > 0; ) {
                FieldInterceptor fldIcpt = (FieldInterceptor) iter.next();
                byte fldCode = fldIcpt.eventCode;
                
                if (match == null) {
                    switch (fldCode) {
                    case EVENT_GETSTATIC:
                        fieldIcpts[FieldType.IGETSTATIC].put(
                            fieldName, fldIcpt);
                        if (forRead) match = fldIcpt;
                        break;
                    case EVENT_PUTSTATIC:
                        fieldIcpts[FieldType.IPUTSTATIC].put(
                            fieldName, fldIcpt);
                        if (!forRead) match = fldIcpt;
                        break;
                    case EVENT_GETFIELD:
                        fieldIcpts[FieldType.IGETFIELD].put(
                            fieldName, fldIcpt);
                        if (forRead) match = fldIcpt;
                        break;
                    case EVENT_PUTFIELD:
                        fieldIcpts[FieldType.IPUTFIELD].put(
                            fieldName, fldIcpt);
                        if (!forRead) match = fldIcpt;
                        break;
                    default:
                        throw new AssertionError("Unknown field event type: " +
                            fldType);
                    }
                }
            }
            
            return match;
        }
        
        return (FieldInterceptor) fieldIcpts[fldType.toInt()].get(fieldName);
    }
    
    void serialize(DataOutputStream out) throws IOException {
        int size = icptMethods.size();
        Iterator<Object> iter = icptMethods.iterator();
        
        out.writeInt(size);
        for (int i = size; i-- > 0; ) {
            FieldInterceptor icptData = (FieldInterceptor) iter.next();
            out.writeByte(icptData.eventCode);
            out.writeUTF(icptData.fieldClass);
            out.writeUTF(icptData.fieldName);
            out.writeUTF(icptData.methodName);
            out.writeUTF(icptData.jniSignature);
            out.writeInt(icptData.breakpointOffset);
        }
    }
    
    static FieldInterceptorData deserialize(DataInputStream in)
            throws IOException {
        FieldInterceptorData clData = new FieldInterceptorData();

        int size = in.readInt();
        for (int i = size; i-- > 0; ) {
            clData.icptMethods.add(new FieldInterceptor(in.readByte(),
                in.readUTF(), in.readUTF(), in.readUTF(),
                in.readUTF(), in.readInt()));
        }
        
        return clData;
    }
}
