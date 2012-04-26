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

import java.util.*;
import java.io.*;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProjectDescription;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.FieldInstruction;

import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;

/**
 * The probe tracker processes notifications received from the
 * {@link sofya.ed.semantic.SemanticInstrumentor} and uses them to
 * construct save, and load logs of the transformations applied to
 * instrumented classes.
 * 
 * <p>Probe logs are used to make possible subsequent online adaptive
 * instrumentation, via {@link sofya.ed.semantic.InstrumentationManager},
 * and/or re-instrumentation with the <code>SemanticInstrumentor</code>.
 * The probe tracker is able to actively maintain the state of probe
 * logs as further re-instrumentation is performed.</p>
 * 
 * <p>Note additionally that changes resulting from online adaptive
 * instrumentation are not recorded to the persistent storage of probe
 * logs, as these changes are not reflected in the class files
 * residing on disk.</p>
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
public final class ProbeTracker implements InstrumentorObserver,
                                           ProbeLogHandler {
    /** Maps class names to the in-memory representations of their
        probe logs. */
    private final Map<Object, Object> classLogs;
    /** Change log for the class currently being tracked; a stateful
        model is used that assumes that only one class is under
        transformation at a time. */
    private ClassLog curClassLog;
    /** Method change log for the method currently being tracked. */
    private MethodLog methodLog;
    /** Signature of the method currently being tracked. */
    private MethodSignature methodSig;

    /** Temporary list to record added but not yet committed probes for
        the currently tracked method. The bytecode offsets used to
        track inserted probes cannot be safely resolved until all
        modifications to the method instruction list have been completed
        and committed. */
    private final List<Comparable<Object>> probeList =
        new ArrayList<Comparable<Object>>(20);
    /** Temporary list to record added but not yet committed exception
        handlers for the currently tracked method. The bytecode offsets
        for the trap region and handler cannot be safely resolved until
        all modifications to the method instruction list have been
        completed and committed. */
    private final List<Object> handlerList = new ArrayList<Object>();

    /** Reference to the event data object used to communicate various
        information to the event dispatcher. */
    private final SemanticEventData seData;
    /** Flag indicating whether we are operating in adaptive instrumentation
        (reinstrumentation) mode. */
    private final boolean adaptive;
    /** Flag indicating whether we are operating online (changes are
        being directed by an instrumentation manager in a running
        event dispatcher). */
    private final boolean online;
    /** Path to the directory used to save probe log files on disk. */
    private String logDir;
    
    /** Next available probe ID to be assigned by <code>newProbe</code>. */
    private int nextId = 1;
    /** List of probe IDs freed by the removal of probes. If non-empty,
        these are reused first. */
    private final TIntArrayList freeIds;

    /** Maps event codes to the tree structures recording the locations of
        probes inserted to raise events of that type. */
    private final TIntObjectHashMap probeTable;
    /** Maps probe IDs to the records for those probes; Used to track the
        number of low-level transformations associated with the logical
        probe. */
    private final TIntObjectHashMap methodProbeTable = new TIntObjectHashMap();

    /** Conditional compilation flag to control whether assertions are
        present in the class file. Should be false in a production
        environment, as the functions of this class are in the main
        performance critical code path. */
    private static final boolean ASSERTS = true;
    /** Conditional compilation flag to control whether debug statements are
        present in the class file. */
    private static final boolean DEBUG = false;

    /**
     * Intermediate object to record a pending probe insertion.
     * 
     * <p>The bytecode offsets needed to record the location of the probe
     * in the log cannot be resolved until all transformations to the
     * method have been applied and committed by setting instruction
     * positions in the instruction list. As probes are added, the
     * relevant instruction handles are stored using instances of this
     * class to represent the probe. When notification is received that
     * instrumentation of the method has completed, the positions of
     * the handles are resolved to offsets and recorded to the log.
     * The instances of this class are then released.</p>
     */
    private static abstract class InstructionProbe
            implements Comparable<Object> {
        public final int type;
        public final int id;
        public final byte eventCode;
        public int startPos = -1;
        public final InstructionHandle start;

        InstructionProbe() {
            throw new AssertionError("Illegal constructor");
        }

        InstructionProbe(int type, int id, byte eventCode,
                InstructionHandle start) {
            this.type = type;
            this.id = id;
            this.eventCode = eventCode;
            this.start = start;
        }

        /**
         * Compare to another intermediate probe record by bytecode
         * offset, lazily resolving the real offsets. Should only
         * be used when modifications to the method instruction list
         * are complete.
         */
        public int compareTo(Object o) {
            InstructionProbe other = (InstructionProbe) o;
            if (startPos < 0) {
                startPos = this.start.getPosition();
            }
            int thisStart = startPos;
            if (other.startPos < 0) {
                other.startPos = other.start.getPosition();
            }
            int otherStart = other.startPos;
            return (thisStart < otherStart ? -1 :
                (thisStart == otherStart ? 0 : 1));
        }

        // So that this object's comparable implementation is
        // consistent with equals
        public boolean equals(Object o) {
            try {
                if (startPos < 0) {
                    startPos = this.start.getPosition();
                }
                InstructionProbe other = (InstructionProbe) o;
                if (other.startPos < 0) {
                    other.startPos = other.start.getPosition();
                }
                return this.startPos == other.startPos;
            }
            catch (ClassCastException e) {
                return false;
            }
        }

        public int hashCode() {
            if (startPos < 0) {
                startPos = start.getPosition();
            }
            return startPos;
        }

        public String toString() { return String.valueOf(startPos); }
    }

    /**
     * Intermediate probe record for a probe consisting
     * of the actual insertion of one or more bytecode instructions.
     */
    private static final class DirectProbe extends InstructionProbe {
        public final InstructionHandle end;
        public final boolean precedes;
        public static final int TYPE = 1;

        private DirectProbe() {
            throw new AssertionError("Illegal constructor");
        }

        DirectProbe(int id, byte eventCode, InstructionHandle start,
                InstructionHandle end, boolean precedes) {
            super(TYPE, id, eventCode, start);
            this.end = end;
            this.precedes = precedes;
        }
    }

    /**
     * Intermediate probe record for an interceptor probe consisting of
     * the replacement of an invoke instruction.
     */
    private static final class CallInterceptorProbe extends InstructionProbe {
        public final short opcode;
        public final MethodSignature callTarget;
        public static final int TYPE = 2;

        private CallInterceptorProbe() {
            throw new AssertionError("Illegal constructor");
        }

        CallInterceptorProbe(int id, byte eventCode, InstructionHandle call,
                short opcode, MethodSignature callTarget) {
            super(TYPE, id, eventCode, call);
            this.opcode = opcode;
            this.callTarget = callTarget;
        }
    }
    
    private static final class FieldInterceptorProbe extends InstructionProbe {
        public final FieldInstruction instruction;
        public final ConstantPoolGen cpg;
        public static final int TYPE = 3;
        
        private FieldInterceptorProbe() {
            throw new AssertionError("Illegal constructor");
        }
        
        FieldInterceptorProbe(int id, byte eventCode,
            InstructionHandle field_ih, ConstantPoolGen cpg) {
            super(TYPE, id, eventCode, field_ih);
            this.instruction = (FieldInstruction) field_ih.getInstruction();
            this.cpg = cpg;
        }
    }

    /**
     * Intermediate probe record to track the addition of an exception
     * handler to a method.
     */
    private static final class ExceptionHandler {
        public final int probeId;
        public final CodeExceptionGen handler;
        public final boolean removable;

        private ExceptionHandler() {
            throw new AssertionError("Illegal constructor");
        }

        ExceptionHandler(int id, CodeExceptionGen handler, boolean removable) {
            this.probeId = id;
            this.handler = handler;
            this.removable = removable;
        }
    }

    private ProbeTracker() {
        throw new AssertionError("Illegal constructor");
    }

    @SuppressWarnings("unchecked")
    ProbeTracker(boolean adaptive, boolean online,
            SemanticEventData seData) {
        this.curClassLog = new ClassLog("<init>");
        this.adaptive = adaptive;
        this.online = online;
        this.classLogs = new THashMap();
        this.seData = seData;

        if (adaptive) {
            if (seData.areProbesLogged()) {
                this.nextId = seData.getNextFreeProbeId();
                this.freeIds = seData.getProbeIdFreeList();
                this.probeTable = seData.getProbeTable();
            }
            else {
                throw new IllegalStateException("Cannot reinstrument " +
                    "without probe logs");
            }
        }
        else {
            this.freeIds = new TIntArrayList();
            this.probeTable = new TIntObjectHashMap();
        }
    }
    
    public void begin() {
    }
    
    public void classBegin(String className) {
        if (DEBUG) System.out.println("classBegin::" + className);

        // This assertion should not occur online, so it will not
        // be removed through conditional compilation
        assert !adaptive;

        if (!curClassLog.className.equals(className)) {
            curClassLog = new ClassLog(className);
        }
    }

    public void classBegin(ClassLog classLog) {
        curClassLog = classLog;
    }

    public void classEnd(String className) {
        if (DEBUG) System.out.println("classEnd::" + className);
        if (!online) {
            try {
                saveLog(curClassLog);
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("WARNING: Error in ProbeTracker " +
                    "recording class probe data");
            }
        }
    }

    public void methodBegin(MethodGen method, InstructionList il) {
        if (DEBUG) System.out.println("methodBegin::" + method);

        methodSig = new MethodSignature(method);
        String nameAndSig = method.getName() + method.getSignature();
        if (adaptive) {
            methodLog = (MethodLog) curClassLog.methodLogs.get(
                nameAndSig);
            if (methodLog == null) {
                methodLog = new MethodLog(nameAndSig);
            }
        }
        else {
            methodLog = new MethodLog(nameAndSig);
            //System.out.println("methodBegin::" + method.getClassName() + "." + methodLog.methodSig);
        }
    }

    public int newProbe(byte eventCode, Set<String> liveKeys) {
        if (DEBUG) {
            System.out.println("newProbe::eventCode=" + eventCode);
        }
        int newId;
        int freeListSize = freeIds.size();
        if (freeListSize > 0) {
            // Treat it like a stack - removing from the end will avoid
            // recopying the entire array every time we take a value
            newId = freeIds.remove(freeListSize - 1);
        }
        else {
            newId = nextId++;
        }

        ProbeRecord newProbe = new ProbeRecord(newId, methodSig, liveKeys);
        ProbeLocationTree pTree =
            (ProbeLocationTree) probeTable.get(eventCode);
        if (pTree == null) {
            pTree = new ProbeLocationTree();
            probeTable.put(eventCode, pTree);
        }
        pTree.add(newProbe);

        methodProbeTable.put(newId, newProbe);

        return newId;
    }

    public void probeInserted(int id, byte eventCode, InstructionList il,
            InstructionHandle ih, boolean precedes) {
        if (DEBUG) System.out.println("probeInserted[1]::" + eventCode);
        probeList.add(new DirectProbe(id, eventCode, il.getStart(),
            il.getEnd(), precedes));

        ProbeRecord probe = (ProbeRecord) methodProbeTable.get(id);
        if (ASSERTS) assert probe != null;
        probe.changeCount++;
    }

    public void probeInserted(int id, byte eventCode,
            InstructionHandle inserted, InstructionHandle ih,
            boolean precedes) {
        if (DEBUG) System.out.println("probeInserted[2]::" + eventCode);
        probeList.add(new DirectProbe(id, eventCode, inserted, inserted,
            precedes));

        ProbeRecord probe = (ProbeRecord) methodProbeTable.get(id);
        if (ASSERTS) assert probe != null;
        probe.changeCount++;
    }

    public void probeRemoved(int id, byte eventCode) {
        if (DEBUG) System.out.println("probeRemoved::" + eventCode);
        ProbeLocationTree pTree =
            (ProbeLocationTree) probeTable.get(eventCode);
        //System.out.println("before: " + pTree);

        int remChanges = pTree.decrementAndRemove(methodSig, id);
        if (ASSERTS) assert remChanges >= 0;
        if (remChanges == 0) {
            freeIds.add(id);
        }

        //System.out.println("after: " + pTree);
    }

    public void exceptionHandlerAdded(int id, CodeExceptionGen handler,
            boolean removable) {
        if (DEBUG) System.out.println("exceptionHandlerAdded");
        handlerList.add(new ExceptionHandler(id, handler, removable));
    }

    @SuppressWarnings("unchecked")
    public void methodEnd(MethodGen method, InstructionList il,
            TIntObjectHashMap offsets) {
        MethodSignature locSig = new MethodSignature(method);
        List<Object> bytecodeLog = methodLog.bytecodeLog;
        if (DEBUG) System.out.println("methodEnd::" + locSig);

        /*if (method.getName().equals("run")) {
            TIntObjectIterator _tmpIter = offsets.iterator();
            while (_tmpIter.hasNext()) {
                _tmpIter.advance();
                System.out.print(_tmpIter.key());
                System.out.println(" --> " + _tmpIter.value());
            }
        }*/

        Collections.sort(probeList);
        Iterator newProbes = probeList.iterator();
        int addedCount = probeList.size();

        if (DEBUG) System.out.println("addedCount=" + addedCount);

        if (!adaptive || (bytecodeLog.size() == 0)) {
            for (int i = addedCount; i-- > 0; ) {
                InstructionProbe newProbe =
                    (InstructionProbe) newProbes.next();

                if (newProbe.startPos < 0) {
                    newProbe.startPos = newProbe.start.getPosition();
                }
                if (ASSERTS) assert(newProbe.startPos >= 0);

                switch (newProbe.type) {
                case DirectProbe.TYPE:
                    DirectProbe dProbe = (DirectProbe) newProbe;
                    bytecodeLog.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos,
                        dProbe.end.getPosition() +
                        dProbe.end.getInstruction().getLength(),
                        dProbe.precedes));
                    break;
                case CallInterceptorProbe.TYPE:
                    CallInterceptorProbe ciProbe =
                        (CallInterceptorProbe) newProbe;
                    bytecodeLog.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos, ciProbe.opcode,
                        ciProbe.callTarget));
                    break;
                case FieldInterceptorProbe.TYPE:
                    FieldInterceptorProbe fiProbe =
                        (FieldInterceptorProbe) newProbe;
                    FieldInstruction ins = fiProbe.instruction;
                    bytecodeLog.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos,
                        ins.getOpcode(),
                        ins.getReferenceType(fiProbe.cpg).toString(),
                        ins.getFieldName(fiProbe.cpg),
                        ins.getFieldType(fiProbe.cpg)));
                    break;
                }
            }
        }
        else {
            // Insert bytecode change records for new probes into bytecode log
            // and update offsets of existing records
            InstructionProbe newProbe;
            if (addedCount > 0) {
                newProbe = (InstructionProbe) newProbes.next();
            }
            else {
                newProbe = null;
            }

            ListIterator<Object> bcChanges = bytecodeLog.listIterator(0);
            while (bcChanges.hasNext()) {
                BytecodeChange curChange = (BytecodeChange) bcChanges.next();
                InstructionHandle start_ih =
                    (InstructionHandle) offsets.get(curChange.start);
                /*if (start_ih == null) {
                    TIntObjectIterator _tmpIter = offsets.iterator();
                    while (_tmpIter.hasNext()) {
                        _tmpIter.advance();
                        System.out.print(_tmpIter.key());
                        System.out.println(" --> " + _tmpIter.value());
                    }
                    System.out.println(curChange.start);
                    System.exit(1);
                }*/
                int curChangeNewStart = start_ih.getPosition();
                //curChange.start = start_ih.getPosition();

                updateAndContinue: {
                    if (addedCount > 0) {
                        if (newProbe.startPos < 0) {
                            newProbe.startPos = newProbe.start.getPosition();
                        }
                        if (ASSERTS) {
                            assert(newProbe.startPos != -1);
                            assert(curChangeNewStart != newProbe.startPos);
                        }

                        if (curChangeNewStart > newProbe.startPos) {
                            break updateAndContinue;
                        }
                    }

                    curChange.start = curChangeNewStart;
                    continue;
                }

                // Should be guaranteed by previous conditions/asserts
                if (ASSERTS) assert(curChangeNewStart > newProbe.startPos);

                // Roll back to insertion point
                bcChanges.previous();

                if (newProbe.startPos < 0) {
                    newProbe.startPos = newProbe.start.getPosition();
                }
                if (ASSERTS) assert(newProbe.startPos >= 0);

                switch (newProbe.type) {
                case DirectProbe.TYPE:
                    DirectProbe dProbe = (DirectProbe) newProbe;
                    bcChanges.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos,
                        dProbe.end.getPosition() +
                        dProbe.end.getInstruction().getLength(),
                        dProbe.precedes));
                    break;
                case CallInterceptorProbe.TYPE:
                    CallInterceptorProbe icProbe =
                        (CallInterceptorProbe) newProbe;
                    bcChanges.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos, icProbe.opcode,
                        icProbe.callTarget));
                    break;
                case FieldInterceptorProbe.TYPE:
                    FieldInterceptorProbe fiProbe =
                        (FieldInterceptorProbe) newProbe;
                    FieldInstruction ins = fiProbe.instruction;
                    bcChanges.add(new BytecodeChange(newProbe.id,
                        newProbe.eventCode, newProbe.startPos,
                        ins.getOpcode(),
                        ins.getReferenceType(fiProbe.cpg).toString(),
                        ins.getFieldName(fiProbe.cpg),
                        ins.getFieldType(fiProbe.cpg)));
                    break;
                }

                if (ASSERTS) {
                    // Check insertion point
                    BytecodeChange chkNewProbe =
                        (BytecodeChange) bcChanges.previous();
                    assert(chkNewProbe.start == newProbe.startPos);
                    bcChanges.next();
                }

                addedCount -= 1;
                if (addedCount > 0) {
                    newProbe = (InstructionProbe) newProbes.next();
                }
            }

            // New probes that need to be appended to the end of the list
            //System.out.println("ProbeTracker.java:450:addedCount=" + addedCount);
            if (addedCount > 0) {
                while (true) {
                    //System.out.println("ProbeTracker.java:453:addedCount=" + addedCount);

                    if (newProbe.startPos < 0) {
                        newProbe.startPos = newProbe.start.getPosition();
                    }
                    if (ASSERTS) assert(newProbe.startPos >= 0);

                    switch (newProbe.type) {
                    case DirectProbe.TYPE:
                        DirectProbe dProbe = (DirectProbe) newProbe;
                        bytecodeLog.add(new BytecodeChange(newProbe.id,
                            newProbe.eventCode, newProbe.startPos,
                            dProbe.end.getPosition() +
                            dProbe.end.getInstruction().getLength(),
                            dProbe.precedes));
                        break;
                    case CallInterceptorProbe.TYPE:
                        CallInterceptorProbe ciProbe =
                            (CallInterceptorProbe) newProbe;
                        bytecodeLog.add(new BytecodeChange(newProbe.id,
                            newProbe.eventCode, newProbe.startPos,
                            ciProbe.opcode,  ciProbe.callTarget));
                        break;
                    case FieldInterceptorProbe.TYPE:
                        FieldInterceptorProbe fiProbe =
                            (FieldInterceptorProbe) newProbe;
                        FieldInstruction ins = fiProbe.instruction;
                        bytecodeLog.add(new BytecodeChange(newProbe.id,
                            newProbe.eventCode, newProbe.startPos,
                            ins.getOpcode(),
                            ins.getReferenceType(fiProbe.cpg).toString(),
                            ins.getFieldName(fiProbe.cpg),
                            ins.getFieldType(fiProbe.cpg)));
                        break;
                    }

                    addedCount -= 1;
                    if (addedCount > 0) {
                        newProbe = (InstructionProbe) newProbes.next();
                    }
                    else {
                        break;
                    }
                }
            }
        }

        // Update added exception handler offsets
        Iterator iterator = methodLog.handlerLog.iterator();
        int size = methodLog.handlerLog.size();
        AddedExceptionHandler handler = null;
        // try {
        for (int i = size; i-- > 0; ) {
            handler = (AddedExceptionHandler) iterator.next();
            InstructionHandle ih =
                (InstructionHandle) offsets.get(handler.start_pc);
            handler.start_pc = ih.getPosition();
            ih = (InstructionHandle) offsets.get(handler.end_pc);
            handler.end_pc = ih.getPosition();
            ih = (InstructionHandle) offsets.get(handler.handler_pc);
            // if (ih == null) {
            //     System.out.println(handler.start_pc);
            //     System.out.println(handler.end_pc);
            //     System.out.println(handler.handler_pc);
            // }
            handler.handler_pc = ih.getPosition();
        }
        // }
        // catch (NullPointerException e) {
        //     System.out.println(locSig);
        //     TIntObjectIterator ioi = offsets.iterator();
        //     while (ioi.hasNext()) {
        //         ioi.advance();
        //         System.out.println(ioi.key() + " -> " + ioi.value());
        //     }
        //     System.out.println(handler.start_pc);
        //     System.out.println(handler.end_pc);
        //     System.out.println(handler.handler_pc);
        //     System.out.println(handler.catch_type);
        //     throw e;
        // }


        // Add new exception handlers
        size = handlerList.size();
        iterator = handlerList.iterator();
        for (int i = size; i-- > 0; ) {
            ExceptionHandler newHandler = (ExceptionHandler) iterator.next();
            if (newHandler.removable) {
                methodLog.handlerLog.add(new AddedExceptionHandler(
                    newHandler.probeId, newHandler.handler));
            }
            else {
                methodLog.syntheticHandlers.add(
                    newHandler.handler.getHandlerPC().getPosition());
            }
        }

        curClassLog.methodLogs.put(method.getName() + method.getSignature(),
            methodLog);

        probeList.clear();
        handlerList.clear();
        methodProbeTable.clear();
    }

    public void callInterceptorAdded(int id, byte eventCode,
            InstructionHandle call, short opcode,
            MethodSignature interceptor, MethodSignature target) {
        if (DEBUG) System.out.println("callInterceptorAdded::" + target);
        probeList.add(new CallInterceptorProbe(id, eventCode, call, opcode,
            target));
        
        String nameAndSig = interceptor.getMethodName() +
            interceptor.getTypeSignature();
        TIntHashSet probeIds =
            (TIntHashSet) curClassLog.addedMethods.get(nameAndSig);
        if (probeIds == null) {
            probeIds = new TIntHashSet();
            curClassLog.addedMethods.put(nameAndSig, probeIds);
        }
        probeIds.add(id);

        ProbeRecord probe = (ProbeRecord) methodProbeTable.get(id);
        if (ASSERTS) assert probe != null;
        probe.changeCount++;
    }
    
    public void fieldInterceptorAdded(int id, byte eventCode,
            InstructionHandle field_ih, ConstantPoolGen cpg) {
        if (DEBUG) System.out.println("fieldInterceptorAdded");
        probeList.add(new FieldInterceptorProbe(id, eventCode, field_ih, cpg));
        
        ProbeRecord probe = (ProbeRecord) methodProbeTable.get(id);
        if (ASSERTS) assert probe != null;
        probe.changeCount++;
    }
    
    public void fieldInterceptorMethodAdded(MethodSignature interceptor) {
        String nameAndSig = interceptor.getMethodName() +
            interceptor.getTypeSignature();
        TIntHashSet probeIds =
            (TIntHashSet) curClassLog.addedMethods.get(nameAndSig);
        if (probeIds == null) {
            probeIds = new TIntHashSet();
            curClassLog.addedMethods.put(nameAndSig, probeIds);
            
            // Don't really want to bother tracking probe ids for this...
        }
    }

    public void staticInitializerAdded() {
        if (DEBUG) System.out.println("staticInitializerAdded");
        TIntHashSet probeIds =
            (TIntHashSet) curClassLog.addedMethods.get("<clinit>()V");
        if (probeIds == null) {
            probeIds = new TIntHashSet();
            curClassLog.addedMethods.put("<clinit>()V", probeIds);
            
            // Don't really want to bother tracking probe ids for this...
        }
    }

    public void exitProbeAdded(int id) {
        if (DEBUG) System.out.println("exitProbeAdded");
        methodLog.exitProbeId = id;
    }

    public void end() {
        seData.setProbesLogged(true);
        seData.setNextFreeProbeId(nextId);
        seData.setProbeIdFreeList(freeIds);
        seData.setProbeTable(probeTable);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // ProbeLogHandler interface
    ///////////////////////////////////////////////////////////////////////////

    public void initializeLogHandler() throws IOException {
        String suiteName = seData.getSuiteName();
        logDir = ProjectDescription.getdbDir() +
            File.separator + suiteName + File.separator;
        
        if (!online) {
            if (Handler.ensureTagExists(suiteName)) {
                // Tag already existed
                System.out.println("INFO: Data for EDL suite \"" + suiteName +
                    "\" already exists and may be overwritten");
            }
        }
    }
    
    public ClassLog getLog(String className) throws IOException {
        ClassLog log = (ClassLog) classLogs.get(className);
        if (log != null) {
            return log;
        }
        
        File logFile = new File(logDir + className + ".probes.dat");
        if (!logFile.exists()) {
            throw new FileNotFoundException("No log file available " +
                "for \"" + className + "\"");
        }
        
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(logFile)));

        log = new ClassLog(className);
        try {
            int size = in.readInt();
            for (int i = size; i-- > 0; ) {
                String nameAndSig = in.readUTF();
                int idCount = in.readInt();
                
                TIntHashSet ids = new TIntHashSet();
                for (int j = idCount; j-- > 0; ) {
                    ids.add(in.readInt());
                }
                
                log.addedMethods.put(nameAndSig, ids);
            }

            size = in.readInt();
            for (int i = size; i-- > 0; ) {
                String mSig = in.readUTF();

                MethodLog methodLog = new MethodLog(mSig);
                int count = in.readInt();
                for (int j = count; j-- > 0; ) {
                    byte action = in.readByte();
                    BytecodeChange bcProbe = null;
                    switch (action) {
                    case BytecodeChange.ACTION_INSERT:
                        bcProbe = new BytecodeChange(
                            in.readInt(), in.readByte(), in.readInt(),
                            in.readShort(), in.readBoolean());
                        break;
                    case BytecodeChange.ACTION_CALL_INTERCEPT:
                        bcProbe = new BytecodeChange(in.readInt(),
                            in.readByte(), in.readInt(), in.readShort(),
                            new MethodSignature(
                            in.readUTF(), in.readUTF(), in.readUTF()));
                        break;
                    }
                    methodLog.bytecodeLog.add(bcProbe);
                }

                count = in.readInt();
                for (int j = count; j-- > 0; ) {
                    methodLog.handlerLog.add(new AddedExceptionHandler(
                        in.readInt(), in.readInt(), in.readInt(),
                        in.readInt(), in.readUTF()));
                }

                count = in.readInt();
                for (int j = count; j-- > 0; ) {
                    methodLog.syntheticHandlers.add(in.readInt());
                }

                methodLog.exitProbeId = in.readInt();

                log.methodLogs.put(mSig, methodLog);
            }
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) { }
        }
        
        classLogs.put(className, log);

        return log;
    }
    
    public void saveLog(ClassLog log) throws IOException {
        String suiteName = seData.getSuiteName();
        Handler.ensureTagExists(suiteName);
        
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(logDir + log.className + ".probes.dat")));

        try {
            int size = log.addedMethods.size();
            Iterator<Object> mIter = log.addedMethods.keySet().iterator();
            out.writeInt(size);
            for (int i = size; i-- > 0; ) {
                String nameAndSig = (String) mIter.next();
                
                out.writeUTF(nameAndSig);
                
                TIntHashSet ids =
                    (TIntHashSet) log.addedMethods.get(nameAndSig);
                TIntIterator iIter = ids.iterator();
                int idCount = ids.size();
                out.writeInt(idCount);
                for (int j = idCount; j-- > 0; ) {
                    out.writeInt(iIter.next());
                }
            }

            size = log.methodLogs.size();
            Iterator logKeys = log.methodLogs.keySet().iterator();
            out.writeInt(size);
            for (int i = size; i-- > 0; ) {
                String mSig = (String) logKeys.next();
                out.writeUTF(mSig);

                MethodLog methodLog = (MethodLog) log.methodLogs.get(mSig);
                int count = methodLog.bytecodeLog.size();
                Iterator iterator = methodLog.bytecodeLog.iterator();
                out.writeInt(count);
                for (int j = count; j-- > 0; ) {
                    BytecodeChange probe = (BytecodeChange) iterator.next();
                    switch (probe.action) {
                    case BytecodeChange.ACTION_INSERT:
                        out.writeByte(BytecodeChange.ACTION_INSERT);
                        out.writeInt(probe.id);
                        out.writeByte(probe.eventCode);
                        out.writeInt(probe.start);
                        out.writeShort(probe.length);
                        out.writeBoolean(probe.precedes);
                        break;
                    case BytecodeChange.ACTION_CALL_INTERCEPT:
                        out.writeByte(BytecodeChange.ACTION_CALL_INTERCEPT);
                        out.writeInt(probe.id);
                        out.writeByte(probe.eventCode);
                        out.writeInt(probe.start);
                        probe.interceptor.serialize(out);
                        break;
                    case BytecodeChange.ACTION_FIELD_INTERCEPT:
                        out.writeByte(BytecodeChange.ACTION_FIELD_INTERCEPT);
                        out.writeInt(probe.id);
                        out.writeByte(probe.eventCode);
                        out.writeInt(probe.start);
                        probe.interceptor.serialize(out);
                        break;
                    }
                }

                count = methodLog.handlerLog.size();
                iterator = methodLog.handlerLog.iterator();
                out.writeInt(count);
                for (int j = count; j-- > 0; ) {
                    AddedExceptionHandler handler =
                        (AddedExceptionHandler) iterator.next();
                    out.writeInt(handler.probeId);
                    out.writeInt(handler.start_pc);
                    out.writeInt(handler.end_pc);
                    out.writeInt(handler.handler_pc);
                    out.writeUTF(handler.catch_type);
                }

                count = methodLog.syntheticHandlers.size();
                TIntIterator handlerPCs = methodLog.syntheticHandlers.iterator();
                out.writeInt(count);
                for (int j = count; j-- > 0; ) {
                    out.writeInt(handlerPCs.next());
                }

                out.writeInt(methodLog.exitProbeId);
            }
        }
        finally {
            out.close();
        }
    }

    // For debugging...
    @SuppressWarnings("unused")
    private static void writeTextLog(ClassLog log) throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(
            new FileWriter(log.className + ".probes.log")));

        try {
            out.println("=======================");
            out.println("Added methods: ");
            int size = log.addedMethods.size();
            Iterator<Object> iterator = log.addedMethods.keySet().iterator();
            for (int i = size; i-- > 0;) {
                out.println("    " + iterator.next());
            }

            out.println();
            out.println("=======================");
            out.println("Bytecode modifications: ");
            out.println();
            size = log.methodLogs.size();
            Iterator logKeys = log.methodLogs.keySet().iterator();
            for (int i = size; i-- > 0; ) {
                String mSig = (String) logKeys.next();
                out.println(mSig);

                MethodLog methodLog = (MethodLog) log.methodLogs.get(mSig);
                int count = methodLog.bytecodeLog.size();
                Iterator probes = methodLog.bytecodeLog.iterator();
                for (int j = count; j-- > 0; ) {
                    BytecodeChange probe = (BytecodeChange) probes.next();
                    switch (probe.action) {
                    case BytecodeChange.ACTION_INSERT:
                        out.println("    " + probe.id + ": start=" +
                            probe.start + ", length=" + probe.length);
                        break;
                    case BytecodeChange.ACTION_CALL_INTERCEPT:
                    case BytecodeChange.ACTION_FIELD_INTERCEPT:
                        out.println("    " + probe.id + ": start=" +
                            probe.start + ", target=" + probe.interceptor);
                        break;
                    }
                }

                out.println();
                out.println("    Exception handlers");
                count = methodLog.handlerLog.size();
                Iterator handlers = methodLog.handlerLog.iterator();
                for (int j = count; j-- > 0; ) {
                    AddedExceptionHandler handler =
                        (AddedExceptionHandler) handlers.next();
                    out.println("    " + handler.probeId + ": start=" +
                        handler.start_pc + ", end=" + handler.end_pc +
                        ", handler=" + handler.handler_pc + ", catch=" +
                        handler.catch_type);
                }
            }
        }
        finally {
            out.close();
        }
    }
    
    public void shutdownLogHandler() throws IOException {
    }
}
