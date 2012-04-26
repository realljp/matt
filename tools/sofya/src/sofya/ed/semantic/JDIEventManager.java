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

/*#ifdef FAST_JDI
import java.util.Collections;
/*#endif*/
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.Location;
import com.sun.jdi.ClassType;
import com.sun.jdi.ReferenceType;
/*#ifdef FAST_JDI
import com.sun.jdi.AbsentInformationException;
import edu.unl.jdi.request.EventRequestManager;
import edu.unl.jdi.request.BreakpointRequest;
import edu.unl.jdi.request.AccessWatchpointRequest;
import edu.unl.jdi.request.ModificationWatchpointRequest;
import edu.unl.jdi.request.ArrayPayloadPolicy;
import edu.unl.jdi.request.FieldPayloadPolicy;
import edu.unl.jdi.request.LocalVariablePayloadPolicy;
import edu.unl.jdi.request.ObjectPayloadPolicy;
import edu.unl.jdi.request.ReferenceTypePayloadPolicy;
import edu.unl.jdi.request.ThreadPayloadPolicy;
import edu.unl.jdi.request.StackFramePayloadPolicy;
/*#else*/
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.AccessWatchpointRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
/*#endif*/
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.ThreadDeathRequest;
import com.sun.jdi.request.ThreadStartRequest;
import com.sun.jdi.request.VMDeathRequest;

import sofya.ed.semantic.EventSpecification.*;
import sofya.ed.semantic.FieldInterceptorData.FieldInterceptor;
import sofya.ed.semantic.SemanticEventDispatcher.InternalException;
import static sofya.ed.semantic.EventSpecification.*;
import static sofya.ed.semantic.InstrumentationMetadata.*;
import static sofya.ed.semantic.SemanticEventData.warnKeyNotAdaptive;

import gnu.trove.THashSet;
import gnu.trove.THashMap;

/**
 * Manages the setup and manipulation of JDI event requests for the
 * {@link SemanticEventDispatcher}.
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
@SuppressWarnings("unchecked")
final class JDIEventManager {
    /** Manager for creating requests to monitor events. */
    private final EventRequestManager erm;
    
    /** Semantic event data received through the event dispatcher. */
    private final SemanticEventData edData;
    
    /** Array cache of the active event specifications, for fast query
        iterations. This is initialized by and shared with the
        event dispatcher to save memory (thus why we don't request it
        directly from the event data object ourselves). */
    private EventSpecification[] eventSpecs;

    /** Mapping from keys to event specifications that are mutable,
        used to support adaptive instrumentation. */
    private final Map<Object, Object> mutEventSpecs = new THashMap();

    /** Mapping from reference types to the field objects representing
        the field used by the event dispatcher to signal the target VM
        to continue after processing of a class prepare event. */
    private final Map<Object, Object> clPreparedFlagFields = new THashMap();

    /** Mapping from reference types to the exception requests for
        those types. */
    private final Map<Object, Object> exceptionRequests = new THashMap();
    /** Mapping from field objects to the access watchpoint requests
        for those fields. */
    private final Map<Object, Object> fieldReadRequests = new THashMap();
    /** Mapping from field objects to the modification watchpoint requests
        for those fields. */
    private final Map<Object, Object> fieldWriteRequests = new THashMap();
    /** Mapping from field name strings to their corresponding field
        objects. */
    private final Map<Object, Object> fieldsMap = new THashMap();

    //private final Map<Object, Set<String>> pendingFieldReadRequests =
    //    new THashMap();
    //private final Map<Object, Set<String>> pendingFieldWriteRequests =
    //    new THashMap();

    /** Constant lookup key used to correlate event requests to their
        event type key; this is used by the event dispatcher to correlate
        different breakpoints to different event types for processing. */
    public static final Object REQUEST_ID = new Object();
    /** Constant key used to correlate field data to event requests for
        use by the event dispatcher during processing of breakpoint-based
        field events. */
    public static final Object FIELD_DATA = new Object();

    /** Flag to control whether in-target-VM adaptive reinstrumentation is
        to be supported; for possible future use. */
    private static final boolean USE_DYN_INST = true;
    
    /** Flag to control whether assertions are stripped from compiled
        bytecodes completely. */
    @SuppressWarnings("unused")
	private static final boolean ASSERTS = true;

    private JDIEventManager() {
        throw new AssertionError("Illegal constructor");
    }

    JDIEventManager(EventRequestManager erm, SemanticEventData edData,
            EventSpecification[] eventSpecs) {
        this.erm = erm;
        this.edData = edData;
        setEventSpecifications(eventSpecs);
        requestCoreEvents();
    }
    
    private final void setEventSpecifications(EventSpecification[] eventSpecs) {
        this.eventSpecs = eventSpecs;
        
        int len = eventSpecs.length;
        for (int i = 0; i < len; i++) {
            if (eventSpecs[i] instanceof AdaptiveEventSpecification) {
                this.mutEventSpecs.put(eventSpecs[i].getKey(),
                    eventSpecs[i]);
            }
        }
    }

    /**
     * Issues universal event requests to the JDI which the module tracer needs
     * to operate.
     */
    private final void requestCoreEvents() {
        ThreadStartRequest tsRequest = erm.createThreadStartRequest();
        tsRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        tsRequest.enable();

        ThreadDeathRequest tdRequest = erm.createThreadDeathRequest();
        tdRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        tdRequest.enable();

        // Do NOT use SUSPEND_ALL, or the JDWP thread in the target
        // VM may deadlock!! This is a JDI/JDWP bug, for which a
        // report has been filed...
        ClassPrepareRequest cpRequest = erm.createClassPrepareRequest();
        cpRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        cpRequest.enable();

        VMDeathRequest vmdRequest = erm.createVMDeathRequest();
        vmdRequest.enable();
    }

    final void requestBreakpoints(ReferenceType rType) {
        /*#ifdef FAST_JDI
        edu.unl.jdi.ClassType clType = (edu.unl.jdi.ClassType) rType;
        /*#else*/
        ClassType clType = (ClassType) rType;
        /*#endif*/

        Method trigger = clType.concreteMethodByName(
            TRIGGER_STATIC_PACKET_NAME, TRIGGER_STATIC_PACKET_SIG);
        BreakpointRequest bpr = erm.createBreakpointRequest(
            trigger.location());
        bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        bpr.putProperty(REQUEST_ID, TriggerKey.STATIC_PACKET);
        /*#ifdef FAST_JDI
        setBasePayload(bpr);
        /*#endif*/
        bpr.enable();

        trigger = clType.concreteMethodByName(
            TRIGGER_OBJ_PACKET_NAME, TRIGGER_OBJ_PACKET_SIG);
        bpr = erm.createBreakpointRequest(
            trigger.location());
        bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        bpr.putProperty(REQUEST_ID, TriggerKey.OBJECT_PACKET);
        /*#ifdef FAST_JDI
        setObjectTriggerPayload(bpr);
        /*#endif*/
        bpr.enable();

        trigger = clType.concreteMethodByName(
            TRIGGER_MON_PACKET_NAME, TRIGGER_MON_PACKET_SIG);
        bpr = erm.createBreakpointRequest(
            trigger.location());
        bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        bpr.putProperty(REQUEST_ID, TriggerKey.MONITOR_PACKET);
        /*#ifdef FAST_JDI
        setBasePayload(bpr);
        /*#endif*/
        bpr.enable();

        trigger = clType.concreteMethodByName(
            TRIGGER_CATCH_PACKET_NAME, TRIGGER_CATCH_PACKET_SIG);
        bpr = erm.createBreakpointRequest(
            trigger.location());
        bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        bpr.putProperty(REQUEST_ID, TriggerKey.CATCH_PACKET);
        /*#ifdef FAST_JDI
        setBasePayload(bpr);
        /*#endif*/
        bpr.enable();
        
        FieldInterceptorData clIcptData =
            edData.getFieldInterceptors(clType.name());
        if (clIcptData == null) return;
        
        Set<Object> fldInterceptors = clIcptData.getInterceptors();
        int icptCount = fldInterceptors.size();
        Iterator<Object> icpts = fldInterceptors.iterator();
        for (int i = icptCount; i-- > 0; ) {
            FieldInterceptor interceptor = (FieldInterceptor) icpts.next();
            
            Method method = clType.concreteMethodByName(interceptor.methodName,
                    interceptor.jniSignature);
            Location bkptLoc =
                method.locationOfCodeIndex(interceptor.breakpointOffset);
            
            bpr = erm.createBreakpointRequest(bkptLoc);
            bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            bpr.putProperty(REQUEST_ID, TriggerKey.FIELD_BREAKPOINT);
            bpr.putProperty(FIELD_DATA, interceptor);

            /*#ifdef FAST_JDI
            edu.unl.jdi.Field fld = clType.fieldByName(interceptor.fieldName);
            char typeTag = fld.signature().charAt(0);
            if ((typeTag == 'L') || (typeTag == ']')) {
                bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            }
            else {
                bpr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            }
            setFieldPayload(bpr, fld);
            /*#else*/
            Field fld = clType.fieldByName(interceptor.fieldName);
            /*#endif*/
            bpr.enable();
                
            interceptor.field = fld;
            interceptor.request = bpr;
        }
    }
    
    final void requestProbeBreakpoints(ClassType probeClass) {
        Method trigger;
        BreakpointRequest bpr;
        String[] types = new String[] {
            "Z", "B", "C", "D", "F", "I", "J", "S", "Ljava/lang/Object;" };
        
        for (int i = types.length; --i >= 0; ) {
            String curType = types[i];
            
            // Request array element trigger breakpoints for the
            // current type
            trigger = probeClass.concreteMethodByName(
                TRIGGER_ARRAY_ELEM_LOAD_NAME,
                "([" + curType + curType + "I)V");
            bpr = erm.createBreakpointRequest(trigger.location());
            bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            bpr.putProperty(REQUEST_ID, TriggerKey.ARRAY_LOAD_BREAKPOINT);
            /*#ifdef FAST_JDI
            if (curType.charAt(0) == 'L') {
                // Only suspend for object element types
                bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            }
            else {
                bpr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            }
            setArrayPayload(bpr, false);
            /*#endif*/
            bpr.enable();
            
            trigger = probeClass.concreteMethodByName(
                TRIGGER_ARRAY_ELEM_STORE_NAME,
                "([" + curType + curType + "I" + curType + ")V");
            bpr = erm.createBreakpointRequest(trigger.location());
            bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            bpr.putProperty(REQUEST_ID, TriggerKey.ARRAY_STORE_BREAKPOINT);
            /*#ifdef FAST_JDI
            if (curType.charAt(0) == 'L') {
                bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            }
            else {
                bpr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            }
            setArrayPayload(bpr, true);
            /*#endif*/
            bpr.enable();
            
        }
    }

    final ModificationWatchpointRequest requestSignalFieldWatchpoint(
            /*#ifdef FAST_JDI edu.unl.jdi.ClassType
            /*#else*/ ClassType /*#endif*/ probeClass) {
        Field signalField = probeClass.fieldByName(SIGNAL_FIELD_NAME);
        ModificationWatchpointRequest mwr =
            erm.createModificationWatchpointRequest(signalField);
        mwr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        mwr.enable();

        return mwr;
    }
    
    final void releaseSignalFieldWatchpoint(
            ModificationWatchpointRequest mwr) {
        mwr.disable();
        erm.deleteEventRequest(mwr);
    }

    /**
     * Issues event requests to the JDI to monitor field observables in a
     * prepared class.
     *
     * @param rType JDI reference type of the class that was prepared. The list
     * of fields declared by the prepared class is scanned to request events
     * for those fields marked as observables in the module description.
     */
    @SuppressWarnings("unchecked")
    final void requestFieldEvents(ReferenceType rType)
            throws InternalException {
        List fields = rType.fields();

        boolean preparedFlagFieldFound = false;

        Iterator fi = fields.iterator();
        int fsize = fields.size();
        for (int i = fsize; i-- > 0; ) {
            Field curField = (Field) fi.next();
            String shortName = curField.name();

            if (!preparedFlagFieldFound) {
                if (shortName.equals(CPE_FLAG_FIELD_NAME)) {
                    clPreparedFlagFields.put(rType, curField);
                    preparedFlagFieldFound = true;
                    continue;
                }
            }

            ReferenceType declType = curField.declaringType();
            boolean isStatic = curField.isStatic();
            String fullName = declType.name() + "." + shortName;
            
            Set<String> liveReadKeys = new THashSet();
            Set<String> liveWriteKeys = new THashSet();
            
            int startIdx = eventSpecs.length - 1;
            for (int j = startIdx; j >= 0; j--) {
                int statusMask =
                    eventSpecs[j].witnessField(fullName, isStatic,
                        curField.typeName());
                
                if ((statusMask & FIELD_WITNESS_READ) > 0) {
                    liveReadKeys.add(eventSpecs[j].getKey());
                }
                
                if ((statusMask & FIELD_WITNESS_WRITE) > 0) {
                    liveWriteKeys.add(eventSpecs[j].getKey());
                }
            }
            
            if (liveReadKeys.size() > 0) {
                AccessWatchpointRequest awr =
                    erm.createAccessWatchpointRequest(curField);
                awr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                awr.enable();

                if (USE_DYN_INST) {
                    awr.putProperty("liveKeys", liveReadKeys);
                    fieldReadRequests.put(curField, awr);
                    //pendingFieldReadRequests.remove(fullName);
                }
            }
            if (liveWriteKeys.size() > 0) {
                ModificationWatchpointRequest mwr =
                    erm.createModificationWatchpointRequest(curField);
                mwr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                mwr.enable();

                if (USE_DYN_INST) {
                    mwr.putProperty("liveKeys", liveWriteKeys);
                    fieldWriteRequests.put(curField, mwr);
                    //pendingFieldWriteRequests.remove(fullName);
                }
            }

            if (USE_DYN_INST) {
                fieldsMap.put(fullName, curField);
            }
        }
    }

    final Field getClassPreparedFlagField(ReferenceType rType) {
        return (Field) clPreparedFlagFields.remove(rType);
    }
    
    final void dumpBreakpoints() {
        System.out.println();
        System.out.println("== JDIEventManager Breakpoint Data ==");
        System.out.println("accessWatchpointCount=" +
        /*#ifdef FAST_JDI
            erm.<AccessWatchpointRequest>accessWatchpointRequests().size());
        /*#else*/
            erm.accessWatchpointRequests().size());
        /*#endif*/
        System.out.println("modificationWatchpointCount=" +
        /*#ifdef FAST_JDI
            erm.<ModificationWatchpointRequest>modificationWatchpointRequests()
                .size());
        /*#else*/
            erm.modificationWatchpointRequests().size());
        /*#endif*/
        /*#ifdef FAST_JDI
        List<? extends BreakpointRequest> bkptList =
            erm.<BreakpointRequest>breakpointRequests();
        Iterator<? extends BreakpointRequest> bkpts = bkptList.iterator();
        /*#else*/
        List<BreakpointRequest> bkptList = erm.breakpointRequests();
        Iterator<BreakpointRequest> bkpts = bkptList.iterator();
        /*#endif*/
        System.out.println("breakpointCount=" + bkptList.size());
        while (bkpts.hasNext()) {
            BreakpointRequest req = bkpts.next();
            System.out.println("  " + req.location());
        }
        System.out.println();
    }
    
    /*#ifdef FAST_JDI
    private final void setBasePayload(BreakpointRequest bpr) {
        ThreadPayloadPolicy threadPolicy = bpr.enableThreadPayload();
        threadPolicy.sendName(false);
        threadPolicy.sendStatus(false);
        threadPolicy.sendOwnedMonitors();
        
        StackFramePayloadPolicy framePolicy =
            threadPolicy.sendStackFrames(0, 2).get(0);
        try {
            framePolicy.sendVariableValues((byte) 1);
        }
        catch (AbsentInformationException e) {
            throw new AssertionError(e);
        }
    }
    
    private final void setObjectTriggerPayload(BreakpointRequest bpr) {
        ThreadPayloadPolicy threadPolicy = bpr.enableThreadPayload();
        threadPolicy.sendName(false);
        threadPolicy.sendStatus(false);
        threadPolicy.sendOwnedMonitors();
        List<StackFramePayloadPolicy> framePolicies =
            threadPolicy.sendStackFrames(0, 2);
        
        StackFramePayloadPolicy triggerFramePolicy = framePolicies.get(0);
        try {
            triggerFramePolicy.sendVariableValues((byte) 1);
        }
        catch (AbsentInformationException e) {
            throw new AssertionError(e);
        }
        
        StackFramePayloadPolicy appFramePolicy = framePolicies.get(1);
        try {
            appFramePolicy.sendVariableValues((byte) 2);
        }
        catch (AbsentInformationException e) {
            throw new AssertionError(e);
        }
    }
    
    private final void setFieldPayload(BreakpointRequest bpr,
            edu.unl.jdi.Field fld) {
        ThreadPayloadPolicy threadPolicy = bpr.enableThreadPayload();
        threadPolicy.sendName(false);
        threadPolicy.sendStatus(false);
        threadPolicy.sendOwnedMonitors();
        List<StackFramePayloadPolicy> framePolicies =
            threadPolicy.sendStackFrames(0, 2);
        
        List<LocalVariablePayloadPolicy> varPolicies = null;
        StackFramePayloadPolicy triggerFramePolicy = framePolicies.get(0);
        try {
            varPolicies = triggerFramePolicy.sendVariableValues((byte) 1);
        }
        catch (AbsentInformationException e) {
            throw new AssertionError(e);
        }
        
        if (fld.isStatic()) {
            FieldPayloadPolicy fldPolicy = bpr.enableFieldPayload(fld);
            fldPolicy.sendPayloadForObject();
            fldPolicy.sendPayloadForArray();
        }
        else {
            LocalVariablePayloadPolicy rcvrObjPolicy = varPolicies.get(0);
            ObjectPayloadPolicy rcvrObjValPolicy =
                rcvrObjPolicy.sendPayloadForObject();
            ReferenceTypePayloadPolicy rcvrObjValTypePolicy =
                rcvrObjValPolicy.sendReferenceType();
            rcvrObjValTypePolicy.sendSignature(true);
            rcvrObjValPolicy.sendFieldValues(Collections.singletonList(fld));
        }
    }
    
    private final void setArrayPayload(BreakpointRequest bpr,
            boolean isWrite) {
        ThreadPayloadPolicy threadPolicy = bpr.enableThreadPayload();
        threadPolicy.sendName(false);
        threadPolicy.sendStatus(false);
        threadPolicy.sendOwnedMonitors();
        StackFramePayloadPolicy framePolicy =
            threadPolicy.sendStackFrames(0, 2).get(0);
        
        List<LocalVariablePayloadPolicy> varPolicies = null;
        try {
            varPolicies = framePolicy.sendVariableValues((byte) 1);
        }
        catch (AbsentInformationException e) {
            throw new AssertionError(e);
        }
        
        // Request payload information about the array referenced
        // in the event
        LocalVariablePayloadPolicy theArrayVarPolicy = varPolicies.get(0);
        ArrayPayloadPolicy theArrayValPolicy =
            theArrayVarPolicy.sendPayloadForArray();
        ReferenceTypePayloadPolicy theArrayValTypePolicy =
            theArrayValPolicy.sendReferenceType();
        theArrayValTypePolicy.sendSignature(true);
        
        // Request payload information about the current element value
        LocalVariablePayloadPolicy valPolicy = varPolicies.get(1);
        ObjectPayloadPolicy valObjPolicy = valPolicy.sendPayloadForObject();
        ReferenceTypePayloadPolicy valObjTypePolicy =
            valObjPolicy.sendReferenceType();
        valObjTypePolicy.sendSignature(true);
        ArrayPayloadPolicy valArrayPolicy = valPolicy.sendPayloadForArray();
        ReferenceTypePayloadPolicy valArrayTypePolicy =
            valArrayPolicy.sendReferenceType();
        valArrayTypePolicy.sendSignature(true);
        
        if (isWrite) {
            // Request payload information about the new element value
            valPolicy = varPolicies.get(3);
            valObjPolicy = valPolicy.sendPayloadForObject();
            valObjTypePolicy = valObjPolicy.sendReferenceType();
            valObjTypePolicy.sendSignature(true);
            valArrayPolicy = valPolicy.sendPayloadForArray();
            valArrayTypePolicy = valArrayPolicy.sendReferenceType();
            valArrayTypePolicy.sendSignature(true);
        }
    }
    /*#endif*/
   
    ///////////////////////////////////////////////////////////////////////////
    // Adaptive instrumentation support
    ///////////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    final void enableFieldAccessEvent(String key, Field fld) {
        AccessWatchpointRequest awr = (AccessWatchpointRequest)
            fieldReadRequests.get(fld);
        if (awr == null) {
            awr = erm.createAccessWatchpointRequest(fld);
            awr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            fieldReadRequests.put(fld, awr);
        }

        Set<Object> liveKeys = (Set) awr.getProperty("liveKeys");
        if (liveKeys == null) {
            liveKeys = new THashSet();
            awr.putProperty("liveKeys", liveKeys);
        }
        if (liveKeys.size() == 0) {
            awr.enable();
        }
        liveKeys.add(key);
    }

    @SuppressWarnings("unchecked")
    final boolean enableFieldAccessEvent(String key, String fldName,
            boolean isStatic) {
        Field fld = (Field) fieldsMap.get(fldName);
        if (fld == null) {
            if (mutEventSpecs.size() > 0) {
//                Only useful as a possible optimization
//                Set<String> liveKeys = pendingFieldReadRequests.get(fldName);
//                if (liveKeys == null) {
//                    liveKeys = new THashSet();
//                    pendingFieldReadRequests.put(fldName, liveKeys);
//                }
//                liveKeys.add(key);
                
                FieldType fieldType =
                    (isStatic)
                    ? FieldType.GETSTATIC
                    : FieldType.GETFIELD;
                AdaptiveEventSpecification eventSpec =
                    (AdaptiveEventSpecification) mutEventSpecs.get(key);
                if (eventSpec != null) {
                    eventSpec.addFieldEvent(fldName, fieldType);
                    return true;
                }
            }
            
            warnKeyNotAdaptive(System.err, key);
            return false;
        }
        else {
            enableFieldAccessEvent(key, fld);
            return true;
        }
    }

    final boolean disableFieldAccessEvent(String key, Field fld) {
        AccessWatchpointRequest awr = (AccessWatchpointRequest)
            fieldReadRequests.get(fld);
        if (awr == null) {
            return false;
        }
        else {
            Set liveKeys = (Set) awr.getProperty("liveKeys");
            if (liveKeys == null) {
                return false;
            }
            liveKeys.remove(key);
            if (liveKeys.size() == 0) {
                awr.disable();
                return true;
            }
            return false;
        }
    }

    final boolean disableFieldAccessEvent(String key, String fldName,
            boolean isStatic) {
        Field fld = (Field) fieldsMap.get(fldName);
        if (fld == null) {
            if (mutEventSpecs.size() > 0) {
//                Only useful as a possible optimization
//                Set<String> liveKeys = pendingFieldReadRequests.get(fldName);
//                if (liveKeys == null) {
//                    return false;
//                }
//                liveKeys.remove(key);
                
                FieldType fieldType =
                    (isStatic)
                    ? FieldType.GETSTATIC
                    : FieldType.GETFIELD;
                AdaptiveEventSpecification eventSpec =
                    (AdaptiveEventSpecification) mutEventSpecs.get(key);
                if (eventSpec != null) {
                    eventSpec.removeFieldEvent(fldName, fieldType);
                    return true;
                }
            }
            
            warnKeyNotAdaptive(System.err, key);
            return false;
        }
        else {
            return disableFieldAccessEvent(key, fld);
        }
    }

    @SuppressWarnings("unchecked")
    final void enableFieldWriteEvent(String key, Field fld) {
        ModificationWatchpointRequest mwr = (ModificationWatchpointRequest)
            fieldWriteRequests.get(fld);
        if (mwr == null) {
            mwr = erm.createModificationWatchpointRequest(fld);
            mwr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            fieldReadRequests.put(fld, mwr);
        }
        mwr.enable();

        Set<Object> liveKeys = (Set) mwr.getProperty("liveKeys");
        if (liveKeys == null) {
            liveKeys = new THashSet();
            mwr.putProperty("liveKeys", liveKeys);
        }
        if (liveKeys.size() == 0) {
            mwr.enable();
        }
        liveKeys.add(key);
    }

    @SuppressWarnings("unchecked")
    final boolean enableFieldWriteEvent(String key, String fldName,
            boolean isStatic) {
        Field fld = (Field) fieldsMap.get(fldName);
        if (fld == null) {
            if (mutEventSpecs.size() > 0) {
//                Only useful as a possible optimization
//                Set<String> liveKeys = pendingFieldWriteRequests.get(fldName);
//                if (liveKeys == null) {
//                    liveKeys = new THashSet();
//                    pendingFieldWriteRequests.put(fldName, liveKeys);
//                }
//                liveKeys.add(key);
                
                FieldType fieldType =
                    (isStatic)
                    ? FieldType.PUTSTATIC
                    : FieldType.PUTFIELD;
                AdaptiveEventSpecification eventSpec =
                    (AdaptiveEventSpecification) mutEventSpecs.get(key);
                if (eventSpec != null) {
                    eventSpec.addFieldEvent(fldName, fieldType);
                    return true;
                }
            }

            warnKeyNotAdaptive(System.err, key);
            return false;
        }
        else {
            enableFieldWriteEvent(key, fld);
            return true;
        }
    }

    final boolean disableFieldWriteEvent(String key, Field fld) {
        ModificationWatchpointRequest mwr = (ModificationWatchpointRequest)
            fieldWriteRequests.get(fld);
        if (mwr == null) {
            return false;
        }
        else {
            Set liveKeys = (Set) mwr.getProperty("liveKeys");
            if (liveKeys == null) {
                return false;
            }
            liveKeys.remove(key);
            if (liveKeys.size() == 0) {
                mwr.disable();
                return true;
            }
            return false;
        }
    }

    final boolean disableFieldWriteEvent(String key, String fldName,
            boolean isStatic) {
        Field fld = (Field) fieldsMap.get(fldName);
        if (fld == null) {
            if (mutEventSpecs.size() > 0) {
//                Only useful as a possible optimization
//                Set liveKeys = (Set) pendingFieldWriteRequests.get(fldName);
//                if (liveKeys == null) {
//                    return false;
//                }
//                liveKeys.remove(key);

                FieldType fieldType =
                    (isStatic)
                    ? FieldType.PUTSTATIC
                    : FieldType.PUTFIELD;
                AdaptiveEventSpecification eventSpec =
                    (AdaptiveEventSpecification) mutEventSpecs.get(key);
                if (eventSpec != null) {
                    eventSpec.removeFieldEvent(fldName, fieldType);
                    return true;
                }
            }
            
            warnKeyNotAdaptive(System.err, key);
            return false;
        }
        else {
            return disableFieldWriteEvent(key, fld);
        }
    }

    @SuppressWarnings("unchecked")
    final void enableExceptionEvent(Set<String> keys, ReferenceType rType) {
        ExceptionRequest er = (ExceptionRequest) exceptionRequests.get(rType);
        if (er == null) {
            er = erm.createExceptionRequest(rType, true, true);
            er.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            exceptionRequests.put(rType, er);
        }

        Set<Object> liveKeys = (Set) er.getProperty("liveKeys");
        if (liveKeys == null) {
            liveKeys = new THashSet();
            er.putProperty("liveKeys", liveKeys);
        }
        if (liveKeys.size() == 0) {
            er.enable();
        }
        liveKeys.addAll(keys);
    }

    @SuppressWarnings("unchecked")
    final void enableExceptionEvent(String key, ReferenceType rType) {
        ExceptionRequest er = (ExceptionRequest) exceptionRequests.get(rType);
        if (er == null) {
            er = erm.createExceptionRequest(rType, true, true);
            er.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            exceptionRequests.put(rType, er);
        }

        Set<Object> liveKeys = (Set) er.getProperty("liveKeys");
        if (liveKeys == null) {
            liveKeys = new THashSet();
            er.putProperty("liveKeys", liveKeys);
        }
        if (liveKeys.size() == 0) {
            er.enable();
        }
        liveKeys.add(key);
    }
    
    final boolean disableExceptionEvent(String key, ReferenceType rType) {
        ExceptionRequest er = (ExceptionRequest) exceptionRequests.get(rType);
        if (er == null) {
            return false;
        }
        else {
            Set liveKeys = (Set) er.getProperty("liveKeys");
            if (liveKeys == null) {
                return false;
            }
            liveKeys.remove(key);
            if (liveKeys.size() == 0) {
                er.disable();
                return true;
            }
            return false;
        }
    }
}
