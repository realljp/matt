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

import java.util.List;
import java.util.Map;
import java.util.Iterator;

import sofya.ed.semantic.EventSelectionFilter;
import sofya.base.MethodSignature;
import sofya.base.exceptions.SofyaError;

import com.sun.jdi.*;

import gnu.trove.TLongHashSet;

/**
 * Implements the dynamic escape detection algorithm as described by
 * Stoller and Wang.
 *
 * @author Alex Kinneer
 * @version 12/29/2006
 */
public final class DynamicEscapeDetector extends EventSelectionFilter {
    /** Set recording the IDs of escaped objects. */
    private TLongHashSet escaped = new TLongHashSet();
    
    private Field badField;
    
    /*#ifdef FOR_MOLDYN
    private final TLongHashSet permNotEscaped;
    /*#endif*/
    
    /**
     * Creates a new dynamic escape detector.
     */
    public DynamicEscapeDetector() {
        /*#ifdef FOR_MOLDYN
        permNotEscaped = new TLongHashSet();
        /*#endif*/
    }
    
    /**
     * Reports whether an given object has escaped from its creating
     * thread.
     *
     * @param objectId ID of the object that should be checked for
     * escape.
     *
     * @return <code>true</code> if the object has escaped (may be
     * accessible by more than one thread).
     */
    public boolean isEscaped(long objectId) {
        boolean result = escaped.contains(objectId);
        //System.out.println("isEscaped: " + objectId + " (" + result + ")");
        return result;
        //return escaped.contains(objectId);
    }
    
    public void threadStartEvent(ThreadData td) {
        ObjectData target = td.getRunnableTarget();
        if (target != null) {
            markEscaped(target.getReference());
        }
        else {
            markEscaped(td.getObject().getReference());
        }
    }
    
    public void instanceFieldWriteEvent(ThreadData td, ObjectData od,
            FieldData fd) {
        Value newVal = fd.getNewValue();
        
        if (escaped.contains(od.getId())
                && (newVal instanceof ObjectReference)) {
            markEscaped((ObjectReference) newVal);
        }
    }
    
    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        Value newVal = fd.getNewValue();
        
        if (newVal instanceof ObjectReference) {
            markEscaped((ObjectReference) newVal);
        }
    }
    
    public void staticCallEvent(ThreadData td, CallData cd) {
        if (cd.canGetArguments() && cd.isNative()) {
            markEscaped(cd.getArguments());
        }
    }
    
    public void virtualCallEvent(ThreadData td, CallData cd) {
        if (cd.canGetArguments()) {
            checkAndMarkNativeArgs(cd);
        }
    }
    
    public void interfaceCallEvent(ThreadData td, CallData cd) {
        if (cd.canGetArguments()) {
            checkAndMarkNativeArgs(cd);
        }
    }
    
    private void checkAndMarkNativeArgs(CallData cd) {
        Arguments args = cd.getArguments();
        ObjectReference thisObj = args.getThis();
        ClassType thisType = (ClassType) thisObj.referenceType();
        
        MethodSignature callSig = cd.getCalledSignature();
        String rawProbeStr = cd.getRawCalledSignature();
        String callJNISig =
            rawProbeStr.substring(rawProbeStr.lastIndexOf('#') + 1);
        
        try {
            Method callMethod = thisType.concreteMethodByName(
                callSig.getMethodName(), callJNISig);
            if (callMethod.isNative()) {
                markEscaped(args);
            }
        }
        catch (ClassNotPreparedException e) {
            // Class should be prepared by the time we attempt to call
            // a method on it
            throw new SofyaError("Could not check escaping arguments for " +
                "call", e);
        }
    }
    
    private void markEscaped(Arguments arguments) {
        /*#ifdef FAST_JDI
        Value[] vals = arguments.getAllArgumentValues();
        if (vals != null) {
            int size = vals.length;
            for (int i = size; --i >= 0; ) {
                Value curVal = vals[i];
                if (curVal instanceof ObjectReference) {
                    markEscaped((ObjectReference) curVal);
                }
            }
        }
        else /*#endif*/ {
            Map args = arguments.getAllArguments();
            int size = args.size();
            Iterator iterator = args.values().iterator();
            for (int i = size; i-- > 0; ) {
                Value val = (Value) iterator.next();
                if (val instanceof ObjectReference) {
                    markEscaped((ObjectReference) val);
                }
            }
        }
    }
    
    private void markEscaped(ObjectReference objRef) {
        if (objRef == null) {
            return;
        }
        
        //System.out.println(objRef.type().name());
        
        long objId = objRef.uniqueID();
        if (escaped.contains(objId)) {
            return;
        }
        /*#ifdef FOR_MOLDYN
        else {
            if (permNotEscaped.contains(objId)) {
                return;
            }
            
            ReferenceType objType = objRef.referenceType();
            
            if (objType.name().startsWith("moldyn.particle")) {
                permNotEscaped.add(objId);
                return;
            }
        }
        /*#endif*/
        
        escaped.add(objId);
        
        if (objRef instanceof ArrayReference) {
            ArrayReference arrRef = (ArrayReference) objRef;
            
            List arrValues = arrRef.getValues();
            int length = arrValues.size();
            Iterator iterator = arrValues.iterator();
            
            boolean refElemType = false;
            for (int i = length; i-- > 0; ) {
                if (refElemType) {
                    markEscaped((ObjectReference) iterator.next());
                }
                else {
                    Value elemValue = (Value) iterator.next();
                    if (elemValue instanceof ObjectReference) {
                        refElemType = true;
                        markEscaped((ObjectReference) elemValue);
                    }
                    else {
                        return;
                    }
                }
            }
        }
        else {
            ReferenceType objType = objRef.referenceType();
            List<Field> fields = null;
            try {
                fields = objType.allFields();
            }
            catch (ClassNotPreparedException e) {
                // Shouldn't a class always be prepared before an instance
                // can be assigned to a field?
                throw new SofyaError("Could not read fields", e);
            }
            
            Map<Field, Value> fieldVals = objRef.getValues(fields);
            int size = fieldVals.size();
            Iterator<Map.Entry<Field, Value>> entries =
                fieldVals.entrySet().iterator();
            for (int i = size; i-- > 0; ) {
                Map.Entry<Field, Value> e = entries.next();
                
                Field f = e.getKey();
                if (f == badField) {
                    continue;
                }
                else if (badField == null) {
                    if ("sun.reflect.UnsafeStaticFieldAccessorImpl.base".equals(
                            f.toString())) {
                        badField = f;
                        continue;
                    }
                }

                Value fVal = e.getValue();
                if (fVal instanceof ObjectReference) {
                    markEscaped((ObjectReference) fVal);
                }
            }
        }
    }
    
    ////////////////////////////////////////////////////////////////////////
    // The JDI only guarantees that the ID of a mirrored object is unique
    // if it has not been garbage collected. So whenever a new object is
    // constructed, we remove its ID from the escaped set to make sure
    // the new object doesn't erroneously "inherit" the status of the old
    // (now collected) object.
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        /*#ifdef FOR_MOLDYN
        long objId = od.getId();
        if (!permNotEscaped.contains(objId)) {
            escaped.remove(od.getId());
        }
        /*#else*/
        escaped.remove(od.getId());
        /*#endif*/
    }
}
