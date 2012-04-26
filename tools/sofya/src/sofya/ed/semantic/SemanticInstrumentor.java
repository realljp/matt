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

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.text.NumberFormat;

import sofya.base.*;
import sofya.base.exceptions.*;
import sofya.ed.Instrumentor;
import sofya.ed.semantic.EventSpecification.FieldType;
import sofya.ed.semantic.EventSpecification.MonitorType;
import sofya.ed.semantic.EventSpecification.ArrayElementType;
import sofya.ed.semantic.EventSpecification.ArrayElementBounds;
import sofya.ed.semantic.IntervalList.IntervalNode;
import sofya.ed.semantic.FieldInterceptorData.FieldInterceptor;
import sofya.ed.semantic.BytecodeChange.CallInterceptRecord;
import sofya.ed.semantic.BytecodeChange.FieldInterceptRecord;
import sofya.apps.atomicity.AtomicityEvents;
import static sofya.ed.semantic.SemanticConstants.*;
import static sofya.ed.semantic.EventSpecification.FIELD_WITNESS_READ;
import static sofya.ed.semantic.EventSpecification.FIELD_WITNESS_WRITE;
import static sofya.ed.semantic.InstrumentationMetadata.*;
import static org.apache.bcel.generic.SUtility.getRealPosition;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * The instrumentor for semantic event dispatching. This class compiles an event
 * specification for the {@link SemanticEventDispatcher} and uses the
 * specification to insert probes necessary to enable the
 * {@link SemanticEventDispatcher} to produce all of those events.
 *
 * <p>This class can be used to instrument for semantic event dispatch by
 * compiling an event specification given by a module description file.
 * It is also used to instrument for atomicity checking.</p>
 *
 * <p><strong>Note that the instrumentation should only ever be applied once,
 * or the behavior of the tools is undefined.</strong> If the parameters of
 * the instrumentation need to be changed (e.g. as by a change to the
 * module description), the system should be recompiled first and then
 * instrumented again.</p>
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
@SuppressWarnings("unchecked")
public class SemanticInstrumentor extends Instrumentor {
    private static final boolean ASSERTS = true;
    private static final boolean DEBUG = false;
    
    /** Reference to the instrumentor, used by the main method. */
    private static SemanticInstrumentor inst = null;
    
    /** Records classes already processed by the instrumentor, so that
        they are only processed once even when they are referenced
        by multiple EDL specifications in a single EDL suite.
        Sets of classes are keyed by their location, so that multiple
        classes with the same name can be distinguished if they
        reside in different locations. */
    private static final Map<Object, Set<Object>> finishedClasses =
        new THashMap();
    /** A dummy key used to record already processed classes, when
        no location is provided; this primarily supports instrumentation
        of classes specified directly on the command line. */
    private static final Object DEFAULT_LOC = new Object();

    /** Compiled representations of the event specifications. */
    private EventSpecification[] eventSpecs;
    /** Maximum index in the array of event specifications; used for
        efficient loop control. */
    private int specsLastIndex;
    
    /** Data file generated for the {@link SemanticEventDispatcher}. */
    protected SemanticEventData dataFile;
    /** Set of classes for which observables are included by default. */
    protected Map<Object, Set<String>> moduleClasses = new THashMap();
    /** Flag which specifies whether boundary events are to be raised
        (execution entering or leaving the module through method calls). */
    protected boolean autoBoundaryEvents = true;

    protected ObjectType loadedClassType;
    
    /** Name of the method currently being instrumented. */
    protected String methodName;
    
    protected boolean clinitPrepared = false;

    /** Stores the first instruction of an un-instrumented method; used
        to ensure legal insertion of certain instrumentation. */
    protected InstructionHandle origStart;
    /** Stores the last instruction of an un-instrumented method; used
        to properly target various instrumentation instructions. */
    protected InstructionHandle origEnd;
    /** Local variable index used to hold exception objects in
        inserted exception handlers. */
    protected int excHandlerVar;
    /** Exception handlers associated with the method currently being
        instrumented. */
    protected CodeExceptionGen[] exceptionHandlers;
    /** Maps instruction handles to source code line numbers, these are
        pre-loaded from the method line number table before beginning
        instrumentation of the method. */
    protected TObjectIntHashMap lineNumbers = new TObjectIntHashMap();
    
    
    /** Index to the local variable, added to the current method,
        that is used to store the value that will be written to an
        array element.  */
    protected int fieldValueLocal = -1;
    
    /** Index to the local variable, added to the current method,
        that is used to store the element type determined for an
        array element instruction; used for screening array element events
        by type. */
    protected int arrayElemClassLocal = -1;
    /** Index to the local variable, added to the current method,
        that is used to store the index of the array element that will
        be affected by an array element instruction. */
    protected int arrayIndexLocal = -1;

    /** Caches previously created interceptor methods, so that they can be
        used by multiple calls to the same intercepted method. */
    protected Map<Object, Object> interceptorCache = new THashMap();
    /** Next available ID for an interceptor, to avoid name collisions. */
    protected int curInterceptID = 0;

    /** Index of the entry in the constant pool containing the signature
        of the static JDI breakpoint trigger method added by
        the instrumentor. */
    protected int triggerStaticCodeRefIdx = -1;
    /** Index of the entry in the constant pool containing the signature
        of the object-instance JDI breakpoint trigger method added by
        the instrumentor. */
    protected int triggerCodeRefIdx = -1;
    /** Index of the entry in the constant pool containing the signature
        of the JDI breakpoint trigger method added by the instrumentor
        to capture monitor events. */
    protected int triggerMonRefIdx = -1;
    /** Index of the entry in the constant pool containing the signature
        of the JDI breakpoint trigger method added by the instrumentor
        to capture exception catch events. */
    protected int triggerCatchRefIdx = -1;

    /** Maps current bytecode offsets to instruction handles, to be used
        during adaptive instrumentation to update probe logs after
        changes are made to the instrumentation. */
    protected final TIntObjectHashMap handleOffsets = new TIntObjectHashMap();

    /** Name of the probe communications class. */
    public static final String PROBE_CLASS = "sofya.ed.semantic.EDProbe";
    
    /** &quot;Null&quot; branch handle - setting the target of this handle
    is meaningless; this can be used to avoid tests inside of loops that
    would only be necessary on the first iteration. */
    private static final BranchHandle nullBh =
        (new InstructionList()).append(new GOTO(null));

    /** Observer that may be used to log the transformations applied by
        the instrumentor. */
    private InstrumentorObserver observer;
 
    private boolean adaptive = false;
    private ProbeLogHandler logHandler;
    private ClassLog classLog;
    private MethodLog methodLog;
    private TIntHashSet removeProbeIds;
    private ListIterator probes;
    private BytecodeChange curProbe;
    private final Set<Object> removedHandlers = new THashSet();

    
    private FieldInterceptorData fldInterceptors;
    private final TObjectIntHashMap fldReadIcptIndices =
        new TObjectIntHashMap();
    private final TObjectIntHashMap fldWriteIcptIndices =
        new TObjectIntHashMap();
    
    private static final boolean ENABLE_TRIGGER_ECHO = false;
    
    static {
        finishedClasses.put(DEFAULT_LOC, new THashSet());
    }

    // 2 bits for type code
    // 00: event
    // 01: assert
    // 10: <unused>
    // 11: <unused>
    //
    // if (event)
    //   8 bits for event code
    //   2 flag bits
    //   20 bits for index
    // else if (assert)
    //   2 bits reserved for future use
    //   28 bits for assert ID

    /**
     * Constructs the bitmask for an event probe, such that only the
     * string index needs to be added.
     *
     * @param eventCode Code for the type of observable event marked
     * by the probe.
     *
     * @return The encoded bitmask for the observable event of the
     * given type. The string index can simply be added to this result.
     */
    private static int buildEventMask(int eventCode) {
        return SemanticConstants.TYPE_EVENT + (eventCode << 22);
    }

    private SemanticInstrumentor() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new semantic instrumentor.
     *
     * @param edd Event dispatch data object to which this instrumentor
     * will store event dispatch information.
     */
    public SemanticInstrumentor(SemanticEventData edd) {
        this.dataFile = edd;
        this.adaptive = false;
        this.eventSpecs = edd.getEventSpecificationsArray(true);
        this.specsLastIndex = eventSpecs.length - 1;

        for (int i = specsLastIndex; i >= 0; i--) {
            moduleClasses.put(eventSpecs[i].getKey(),
                eventSpecs[i].getModuleClassNames(null));
        }
    }

    public SemanticInstrumentor(SemanticEventData edd,
            ProbeLogHandler logHandler) {
        this(edd);
        this.adaptive = true;
        this.logHandler = logHandler;
    }

    /**
     * Reports whether the instrumentor is set to insert probes to
     * automatically observe module boundary events (calls which cause
     * execution to exit the module).
     *
     * @return <code>true</code> if calls to methods outside of the
     * module are to generate events regardless of the module
     * description, <code>false</code> otherwise.
     */
    public boolean isAutoBoundaryEventsEnabled() {
        return autoBoundaryEvents;
    }

    /**
     * Specifies whether the instrumentor is to insert probes to
     * automatically observe module boundary events (calls which cause
     * execution to exit the module).
     *
     * @param enable <code>true</code> if calls to methods outside of the
     * module are to be instrumented to generate events regardless of the
     * module description, <code>false</code> otherwise.
     */
    public void setAutoBoundaryEventsEnabled(boolean enable) {
        autoBoundaryEvents = enable;
    }

    /**
     * Gets the observer currently attached to this instrumentor, if
     * any.
     *
     * @return The instrumentor observer currently observing events in
     * this instrumentor, or <code>null</code> if no observer has been
     * attached.
     */
    public InstrumentorObserver getInstrumentorObserver() {
        return observer;
    }

    /**
     * Sets the observer currently attached to this instrumentor.
     *
     * @param observer Observer to be attached to this instrumentor, or
     * <code>null</code> to remove any existing observer.
     */
    public void setInstrumentorObserver(InstrumentorObserver observer) {
        this.observer = observer;
    }

    void loadClass(JavaClass javaClass) throws IOException {
        Repository.addClass(javaClass);
        this.javaClass = javaClass;
        this.fullClassName = javaClass.getClassName();
        this.className = fullClassName;
        javaClassFile = new ClassGen(javaClass);
        cpg = javaClassFile.getConstantPool();
        methods = javaClassFile.getMethods();
        iFactory = new InstructionFactory(cpg);
        starterInserted = false;
        lastInstrumented = null;
        init();
    }

    protected void init() throws IOException {
        super.init();
        
        clinitPrepared = false;
        curInterceptID = 0;
        interceptorCache.clear();
        fldReadIcptIndices.clear();
        fldWriteIcptIndices.clear();
        loadedClassType = new ObjectType(fullClassName);

        if (adaptive) {
            triggerStaticCodeRefIdx = cpg.lookupMethodref(fullClassName,
                TRIGGER_STATIC_PACKET_NAME, TRIGGER_STATIC_PACKET_SIG);
            triggerCodeRefIdx = cpg.lookupMethodref(fullClassName,
                TRIGGER_OBJ_PACKET_NAME, TRIGGER_OBJ_PACKET_SIG);
            triggerMonRefIdx = cpg.lookupMethodref(fullClassName,
                TRIGGER_MON_PACKET_NAME, TRIGGER_MON_PACKET_SIG);
            triggerCatchRefIdx = cpg.lookupMethodref(fullClassName,
                TRIGGER_CATCH_PACKET_NAME, TRIGGER_CATCH_PACKET_SIG);

            ClassLog clLog = logHandler.getLog(fullClassName);
            setLog(clLog);
            if (observer != null) {
                observer.classBegin(clLog);
            }
        }
        else {
            triggerStaticCodeRefIdx = cpg.addMethodref(fullClassName,
                TRIGGER_STATIC_PACKET_NAME, TRIGGER_STATIC_PACKET_SIG);
            triggerCodeRefIdx = cpg.addMethodref(fullClassName,
                TRIGGER_OBJ_PACKET_NAME, TRIGGER_OBJ_PACKET_SIG);
            triggerMonRefIdx = cpg.addMethodref(fullClassName,
                TRIGGER_MON_PACKET_NAME, TRIGGER_MON_PACKET_SIG);
            triggerCatchRefIdx = cpg.addMethodref(fullClassName,
                TRIGGER_CATCH_PACKET_NAME, TRIGGER_CATCH_PACKET_SIG);
            createInstTriggers();

            if (observer != null) {
                observer.classBegin(fullClassName);
            }
        }
        
        createFieldInterceptors();

        if (DEBUG) {
            System.out.println("static_code_idx: " + triggerStaticCodeRefIdx);
            System.out.println("obj_code_idx: " + triggerCodeRefIdx);
            System.out.println("mon_idx: " + triggerMonRefIdx);
        }
    }

    private final void createInstTriggers() {
        InstructionList code = new InstructionList();
        int maxStack;
        if (ENABLE_TRIGGER_ECHO) {
            code.append(iFactory.createGetStatic("java.lang.System",
                "out", new ObjectType("java.io.PrintStream")));
            code.append(new ILOAD(0));
            code.append(iFactory.createInvoke("java.io.PrintStream",
                "println", Type.VOID, new Type[] { Type.INT },
                Constants.INVOKEVIRTUAL));
            maxStack = 2;
        }
        else {
            code.append(new NOP());
            maxStack = 1;
        }
        code.append(new RETURN());

        MethodGen triggerMethod = new MethodGen(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_FINAL,
            Type.VOID, new Type[] { Type.INT }, new String[] { "inst_code" },
            TRIGGER_STATIC_PACKET_NAME, fullClassName, code, cpg);
        triggerMethod.setMaxStack(maxStack);
        javaClassFile.addMethod(triggerMethod.getMethod());

        triggerMethod = new MethodGen(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_FINAL,
            Type.VOID, new Type[] { Type.OBJECT, Type.INT },
            new String[] { "obj_ref", "inst_code" },
            TRIGGER_OBJ_PACKET_NAME, fullClassName, code, cpg);
        triggerMethod.setMaxStack(maxStack);
        javaClassFile.addMethod(triggerMethod.getMethod());

        triggerMethod = new MethodGen(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_FINAL,
            Type.VOID, new Type[] { Type.OBJECT, Type.BYTE },
            new String[] { "mon_obj_ref", "mon_event_code" },
            TRIGGER_MON_PACKET_NAME, fullClassName, code, cpg);
        triggerMethod.setMaxStack(maxStack);
        javaClassFile.addMethod(triggerMethod.getMethod());

        triggerMethod = new MethodGen(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_FINAL,
            Type.VOID, new Type[] { Type.CLASS }, new String[] { "class_ref" },
            TRIGGER_CATCH_PACKET_NAME, fullClassName, code, cpg);
        triggerMethod.setMaxStack(maxStack);
        javaClassFile.addMethod(triggerMethod.getMethod());
    }
    
    private final void createFieldInterceptors() {
        Field[] flds = javaClassFile.getFields();
        
        fldInterceptors = new FieldInterceptorData();
        
        for (int i = flds.length; --i >= 0; ) {
            String shortName = flds[i].getName();
            String fullName = fullClassName + "." + shortName;
            boolean isStatic = flds[i].isStatic();
            
            int witnessMask = 0;
            
            for (int j = specsLastIndex; j >= 0; j--) {
                witnessMask |=
                    eventSpecs[j].witnessField(fullName, isStatic,
                        flds[i].getType());
            }
            
            if ((witnessMask & FIELD_WITNESS_READ) > 0) {
                fldReadIcptIndices.put(fullName,
                    genFieldInterceptor(shortName, flds[i].getType(),
                        loadedClassType, flds[i].isStatic(), true));
            }
            
            if ((witnessMask & FIELD_WITNESS_WRITE) > 0) {
                fldWriteIcptIndices.put(fullName,
                    genFieldInterceptor(shortName, flds[i].getType(), 
                        loadedClassType, flds[i].isStatic(), false));
            }
        }
        
        
        dataFile.recordFieldInterceptors(fullClassName, fldInterceptors);
    }
    
    private final int genFieldInterceptor(String fieldName,
            Type fieldType, Type declType, boolean isStatic, boolean forRead) {
        int typeSize = fieldType.getSize();
        
        byte eventCode;
        String icptName;
        String[] argNames;
        Type[] argTypes;
        Type returnType;
        InstructionHandle bkpt_ih;
        
        InstructionList code = new InstructionList();
        int maxStack;
        int maxLocals;
        
        if (forRead) {
            icptName = FIELD_READ_INTERCEPTOR_NAME + fieldName;
            returnType = fieldType;
            
            if (isStatic) {
                eventCode = EVENT_GETSTATIC;
                argTypes = new Type[0];
                argNames = new String[0];
                maxStack = typeSize;
                maxLocals = 0;
            
                bkpt_ih = code.append(iFactory.createGetStatic(fullClassName,
                    fieldName, fieldType));
            }
            else {
                eventCode = EVENT_GETFIELD;
                argTypes = new Type[] { declType };
                argNames = new String[] { "recvObj" };
                maxStack = typeSize;
                maxLocals = 1;

                code.append(new ALOAD(0));
                bkpt_ih = code.append(iFactory.createGetField(fullClassName,
                    fieldName, fieldType));
            }
            
            code.append(InstructionFactory.createReturn(fieldType));
        }
        else {
            icptName = FIELD_WRITE_INTERCEPTOR_NAME + fieldName;
            returnType = Type.VOID;
            
            if (isStatic) {
                eventCode = EVENT_PUTSTATIC;
                argTypes = new Type[] { fieldType };
                argNames = new String[] { "storeVal" };
                maxStack = typeSize;
                maxLocals = typeSize;
                
                code.append(InstructionFactory.createLoad(fieldType, 0));
                bkpt_ih = code.append(iFactory.createPutStatic(fullClassName,
                    fieldName, fieldType));
            }
            else {
                eventCode = EVENT_PUTFIELD;
                argTypes = new Type[] { declType, fieldType };
                argNames = new String[] { "recvObj", "storeVal" };
                maxStack = 1 + typeSize;
                maxLocals = 1 + typeSize;
                
                code.append(new ALOAD(0));
                code.append(InstructionFactory.createLoad(fieldType, 1));
                bkpt_ih = code.append(iFactory.createPutField(fullClassName,
                    fieldName, fieldType));
            }
            
            code.append(new RETURN());
        }
        
        // Create the interceptor
        MethodGen mg = new MethodGen(
            Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_FINAL,
            returnType, argTypes, argNames, icptName, fullClassName,
            code, cpg);
        
        mg.setMaxStack(maxStack);
        mg.setMaxLocals(maxLocals);

        javaClassFile.addMethod(mg.getMethod());
        
        code.dispose();
        
        fldInterceptors.addInterceptor(new FieldInterceptor(eventCode,
            fullClassName, fieldName, icptName, mg.getSignature(),
            bkpt_ih.getPosition()));
        
        if (observer != null) {
            observer.fieldInterceptorMethodAdded(new MethodSignature(mg));
        }
        
        return cpg.addMethodref(mg);
    }

    /**
     * Instruments a method.
     *
     * <p>Performs the actual instrumentation and update of the method in the
     * class.</p>
     *
     * @param m Method to be instrumented.
     * @param methodIndex Index to the method in the class method array.
     * @param insertStarter <em>Ignored</em>.
     *
     * @return The instrumented method. If the method is <code>native</code>,
     * <code>abstract</code>, or has no body, it is returned unchanged.
     */
    protected Method instrument(Method m, int methodIndex,
            boolean insertStarter) {
        if (m.isNative() || m.isAbstract()) {
            return m;
        }

        String jniName = m.getName();
        String jniSignature = m.getSignature();

        if (adaptive) {
            String nameAndSig = jniName + jniSignature;
            setMethod(nameAndSig);

            if (classLog.addedMethods.containsKey(nameAndSig)) {
                return m;
            }
            curProbe = startProbeIteration();
        }

        MethodGen mg = new MethodGen(m, fullClassName, cpg);
        InstructionList il = mg.getInstructionList();
        mSignature = fullClassName + "#" + jniName + "#" + jniSignature;
        methodName = mg.getName();

        if (observer != null) observer.methodBegin(mg, il);

        exceptionHandlers = mg.getExceptionHandlers();
        cacheHandlerStarts(exceptionHandlers);
        Set<Object> handlerKeys = handlerStarts.keySet();
        removedHandlers.clear();

        // Load the line number table.
        lineNumbers.clear();
        LineNumberGen[] lnTable = mg.getLineNumbers();
        for (int i = 0; i < lnTable.length; i++) {
            lineNumbers.put(lnTable[i].getInstruction(),
                            lnTable[i].getSourceLine());
        }

        boolean isConstructor = methodName.equals("<init>");
        if (isConstructor) {
            origStart = findCallToSuper(il).getNext();
        }
        else {
            origStart = il.getStart();
        }
        origEnd = il.getEnd();

        // Create an extra local variable which will be used for the temporary
        // storage of exception objects when adding exceptional exit nodes
        int origMaxLocals = mg.getMaxLocals();
        excHandlerVar = origMaxLocals;
        fieldValueLocal = origMaxLocals + 1;
        mg.setMaxLocals(origMaxLocals + 3);
        
        mg.addLocalVariable("excObjVal", new ObjectType("java.lang.Throwable"),
            excHandlerVar, il.getStart(), il.getEnd());

        InstructionHandle syntheticHandler = null;
        if (mg.isSynchronized()) {
            syntheticHandler = transformSynchronization(mg, il);
        }

        boolean superConstructorCalled = false;
        InstructionHandle ih;
        for (ih = il.getStart(); ih != null; ) {
            int ih_pos = getRealPosition(ih);
            if (ih_pos < 0) {
                ih = ih.getNext();
                continue;
            }
            handleOffsets.put(ih_pos, ih);

            if (adaptive && (curProbe != null)) {
                //System.out.println("ih.position=" + ih_pos);
                //System.out.println("curProbe.start=" + logAdvisor.curProbe.start);
                if (curProbe.start != ih_pos) {
                    if (ASSERTS) assert(ih_pos < curProbe.start);
                }
                else {
                    //System.out.println(logAdvisor.curProbe);
                    ih = handleProbe(mg, il, ih, ih_pos);
                    continue;
                }
            }

            Instruction i = ih.getInstruction();

            checkCatch: if (handlerKeys.contains(ih)) {
                if (adaptive && methodLog.syntheticHandlers.contains(
                        getRealPosition(ih))) {
                    break checkCatch;
                }
                addCatchProbe(mg, il, ih);
            }

            switch (i.getOpcode()) {
            case Constants.NEW:
                addNewObjectProbe(mg, il, ih);
                break;
            case Constants.MONITORENTER:
                addMonitorEnterProbes(mg, il, ih);
                break;
            case Constants.MONITOREXIT:
                addMonitorExitProbes(mg, il, ih);
                break;
            case Constants.INVOKESTATIC:
                addCallProbe(EVENT_STATIC_CALL, mg, il, ih);
                break;
            case Constants.INVOKEVIRTUAL:
                addCallProbe(EVENT_VIRTUAL_CALL, mg, il, ih);
                break;
            case Constants.INVOKEINTERFACE:
                addCallProbe(EVENT_INTERFACE_CALL, mg, il, ih);
                break;
            case Constants.INVOKESPECIAL:
                byte eventCode =
                    ((InvokeInstruction) i).getMethodName(cpg).equals("<init>")
                    ? SemanticConstants.EVENT_CONSTRUCTOR
                    : SemanticConstants.EVENT_VIRTUAL_CALL;
                if (superConstructorCalled) {
                    addCallProbe(eventCode, mg, il, ih);
                }
                else {
                    if ((eventCode == SemanticConstants.EVENT_CONSTRUCTOR)
                            && (methodName.equals("<init>"))) {
                        superConstructorCalled = true;
                    }
                    else {
                        addCallProbe(eventCode, mg, il, ih);
                    }
                }
                break;
            case Constants.GETSTATIC:
                processFieldInstruction(mg, il, ih, FieldType.GETSTATIC);
                break;
            case Constants.GETFIELD: 
                processFieldInstruction(mg, il, ih, FieldType.GETFIELD);
                break;
            case Constants.PUTSTATIC:
                processFieldInstruction(mg, il, ih, FieldType.PUTSTATIC);
                break;
            case Constants.PUTFIELD:
                processFieldInstruction(mg, il, ih, FieldType.PUTFIELD);
                break;
            case Constants.AALOAD: case Constants.BALOAD:
            case Constants.CALOAD: case Constants.DALOAD:
            case Constants.FALOAD: case Constants.IALOAD:
            case Constants.LALOAD: case Constants.SALOAD:
                insertArrayProbe(mg, il, ih, ArrayElementType.LOAD);
                break;
            case Constants.AASTORE: case Constants.BASTORE:
            case Constants.CASTORE: case Constants.DASTORE:
            case Constants.FASTORE: case Constants.IASTORE:
            case Constants.LASTORE: case Constants.SASTORE:
                insertArrayProbe(mg, il, ih, ArrayElementType.STORE);
                break;
            default:
                break;
            }

            ih = ih.getNext();
        }

        // Remove and re-add all exception handlers previously attached
        // to the method, which causes all of the handlers which have been
        // added to raise call return events on exceptional return to bind
        // first (those handlers then transfer control back to the existing
        // handlers). This would be unnecessary if BCEL could *insert*
        // new exception handlers...
        for (int i = 0; i < exceptionHandlers.length; i++) {
            CodeExceptionGen handler = exceptionHandlers[i];
            if (adaptive && removedHandlers.contains(handler)) {
                continue;
            }

            mg.removeExceptionHandler(handler);

            if (adaptive && (removeProbeIds.size() > 0)) {
                if (checkRemoveHandler(handler)) continue;
            }

            mg.addExceptionHandler(handler.getStartPC(),
                                   handler.getEndPC(),
                                   handler.getHandlerPC(),
                                   handler.getCatchType());
        }

        addEntryProbe(mg, il);
        addExitProbes(mg, il, syntheticHandler);

        // Check to see whether the start marker still needs to be inserted,
        // and if so, do it
        if ((classHasMain || classHasStaticInit) && !starterInserted) {
            if (classHasStaticInit) {
                if (methodName.equals("<clinit>")) {
                    insertStartProbe(il);
                    starterInserted = true;
                }
            }
            else if (methodName.equals("main")) {
                insertStartProbe(il);
                starterInserted = true;
            }
        }
        
        // At runtime, if the class wasn't loaded yet, the act of
        // requesting the class bytecode would cause it to be prepared
        // before we would have the opportunity to insert this event
        // anyway. If this proves to be a problem, we might have to
        // look for an alternative.
        if (!adaptive && classHasStaticInit && methodName.equals("<clinit>")) {
            insertClassPrepareProbe(mg, il);
        }

        // Insert the new instruction list into the method
        mg.setInstructionList(il);

        // As we have added new method calls to the
        // method, the operand stack size needs to be recalculated.
        // This is done by BCEL using control-flow analysis.
        mg.setMaxStack();

        // (Sets offsets in the instruction list)
        Method mInstrumented = mg.getMethod();

        if (observer != null) observer.methodEnd(mg, il, handleOffsets);
        handleOffsets.clear();

        il.dispose();
        origStart = origEnd = null;
        fieldValueLocal = -1;
        arrayElemClassLocal = arrayIndexLocal = -1;

        return mInstrumented;
    }

    public void reinstrument(String methodName, Type returnType,
                             Type[] argumentTypes, boolean insertStarter,
                             TIntHashSet removeProbeIds)
                             throws MethodNotFoundException {
        if (!adaptive) {
            throw new IllegalStateException("Not in adaptive " +
                "instrumentation mode");
        }
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        this.removeProbeIds = removeProbeIds;

        String jniSignature =
            Type.getMethodSignature(returnType, argumentTypes);
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodName) &&
                    jniSignature.equals(methods[i].getSignature())) {
                javaClassFile.setMethodAt(instrument(methods[i], i,
                    insertStarter), i);
                return;
            }
        }
        throw new MethodNotFoundException(methodName);
    }
    
    private final void setLog(ClassLog log) {
        this.classLog = log;
    }
    
    private final void setMethod(String nameAndSig) {
        methodLog = (MethodLog) classLog.methodLogs.get(nameAndSig);
        if (methodLog == null) {
            methodLog = new MethodLog(nameAndSig);
            classLog.methodLogs.put(nameAndSig, methodLog);
        }
    }
    
    private final BytecodeChange startProbeIteration() {
        probes = methodLog.bytecodeLog.listIterator(0);
        return nextProbe();
    }

    private final BytecodeChange nextProbe() {
        if (probes.hasNext()) {
            return (BytecodeChange) probes.next();
        }
        else {
            return null;
        }
    }

    private final void removeProbe() {
        probes.remove();
    }

    private final InstructionHandle handleProbe(MethodGen mg,
            InstructionList il, InstructionHandle ih, int ih_pos) {
        int probeLen = curProbe.length;
        if (removeProbeIds.contains(curProbe.id)) {
            switch (curProbe.action) {
            case BytecodeChange.ACTION_INSERT:
                InstructionHandle end_ih = ih;

                int remLen = ih.getInstruction().getLength();
                while (remLen < probeLen) {
                    end_ih = end_ih.getNext();
                    remLen += end_ih.getInstruction().getLength();
                }
                InstructionHandle sub_ih = end_ih.getNext();

                InstructionTargeter[] targeters = ih.getTargeters();
                if (targeters != null) {
                    for (int j = targeters.length - 1; j >= 0; j--) {
                        if (DEBUG) {
                            System.out.println("++ " + ih + ", " + sub_ih);
                        }
                        
                        if (targeters[j] instanceof CodeExceptionGen) {
                            CodeExceptionGen handler =
                                (CodeExceptionGen) targeters[j];
                            boolean doRemove =
                                checkRemoveHandler(handler);
                            if (doRemove) {
                                mg.removeExceptionHandler(handler);
                                removedHandlers.add(handler);
                                continue;
                            }
                        }
                    }
                }

                // Used by various transformations
                if (origStart == ih) {
                    origStart = sub_ih;
                }

                try {
                    if (DEBUG) {
                        System.out.println("delete: " + ih + " -> " + end_ih);
                    }
                    il.delete(ih, end_ih);
                }
                catch (TargetLostException e) {
                    InstructionHandle[] targets = e.getTargets();
                    int targetsLen = targets.length;
                    for (int i = 0; i < targetsLen; i++) {
                        targeters = targets[i].getTargeters();
                        int targetersLen = targeters.length;
                        for (int j = 0; j < targetersLen; j++) {
                            targeters[j].updateTarget(targets[i],
                                sub_ih);
                        }
                    }
                }
                ih = sub_ih;

                if (curProbe.id == methodLog.exitProbeId) {
                    methodLog.exitProbeId = -1;
                }

                break;
            case BytecodeChange.ACTION_CALL_INTERCEPT: {
                CallInterceptRecord icpt =
                    (CallInterceptRecord) curProbe.interceptor;
                ih.setInstruction(iFactory.createInvoke(
                    icpt.callTarget.getClassName(),
                    icpt.callTarget.getMethodName(),
                    icpt.callTarget.getReturnType(),
                    icpt.callTarget.getArgumentTypes(),
                    icpt.opcode));

                handleOffsets.put(ih_pos, ih);
                ih = ih.getNext();
                break;
            }
            case BytecodeChange.ACTION_FIELD_INTERCEPT: {
                FieldInterceptRecord icpt =
                    (FieldInterceptRecord) curProbe.interceptor;
                
                FieldInstruction restore_fi;
                switch (icpt.opcode) {
                case Constants.GETSTATIC:
                    restore_fi = iFactory.createGetStatic(icpt.className,
                        icpt.fieldName, icpt.fieldType);
                    break;
                case Constants.PUTSTATIC:
                    restore_fi = iFactory.createPutStatic(icpt.className,
                            icpt.fieldName, icpt.fieldType);
                    break;
                case Constants.GETFIELD:
                    restore_fi = iFactory.createGetField(icpt.className,
                            icpt.fieldName, icpt.fieldType);
                    break;
                case Constants.PUTFIELD:
                    restore_fi = iFactory.createPutField(icpt.className,
                            icpt.fieldName, icpt.fieldType);
                    break;
                default:
                    throw new AssertionError("Illegal field interceptor " +
                        "opcode: " + icpt.opcode);
                }
                ih.setInstruction(restore_fi);
                
                handleOffsets.put(ih_pos, ih);
                ih = ih.getNext();
                break;
            }}

            removeProbe();
            if (observer != null) {
                observer.probeRemoved(curProbe.id, curProbe.eventCode);
            }
        }
        else { // Skip
            while (probeLen > 0) {
                probeLen -= ih.getInstruction().getLength();
                if (DEBUG) {
                    System.out.println("--> skipping: " + ih.getPosition());
                }
                handleOffsets.put(getRealPosition(ih), ih);
                ih = ih.getNext();
            }
        }

        if (curProbe.precedes) {
            if (DEBUG) {
                System.out.println("--> skipping[post]: " + ih.getPosition());
            }
            handleOffsets.put(getRealPosition(ih), ih);
            ih = ih.getNext();
        }

        curProbe = nextProbe();
        return ih;
    }
    
    private final boolean checkRemoveHandler(CodeExceptionGen handler) {
        //System.out.println("checkRemoveHandler-->");
        List handlerLog = methodLog.handlerLog;
        int size = handlerLog.size();
        Iterator iterator = handlerLog.listIterator(0);
        for (int j = size; j-- > 0; ) {
            AddedExceptionHandler ha = (AddedExceptionHandler) iterator.next();

            if (!removeProbeIds.contains(ha.probeId)) {
                continue;
            }

            int cur_start_pc = getRealPosition(handler.getStartPC());
            int cur_end_pc = getRealPosition(handler.getEndPC());
            int cur_handler_pc = getRealPosition(handler.getHandlerPC());

            if ((ha.start_pc == cur_start_pc)
                    && (ha.end_pc == cur_end_pc)
                    && (ha.handler_pc == cur_handler_pc)) {
                ObjectType catchType = handler.getCatchType();
                if (catchType == null) {
                    if (ha.catch_type.equals("\0")) {
                        iterator.remove();
                        //System.out.println("  removed");
                        return true;
                    }
                }
                else if (catchType.toString().equals(ha.catch_type)) {
                    iterator.remove();
                    //System.out.println("  removed");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Searches a constructor for the call to its superclass constructor.
     *
     * <p>The behavior of this method is undefined if called on a method
     * that is not a constructor.</p>
     *
     * @param il Instruction list of the constructor to be searched.
     *
     * @return The handle to the invoke instruction representing the
     * call to the superclass constructor.
     */
    private InstructionHandle findCallToSuper(InstructionList il) {
        InstructionHandle ih;
        for (ih = il.getStart(); ih != null; ih = ih.getNext()) {
            Instruction i = ih.getInstruction();
            if (i instanceof INVOKESPECIAL) {
                INVOKESPECIAL is = (INVOKESPECIAL) i;
                try {
                    ObjectType t1 = new ObjectType(fullClassName);
                    ObjectType t2 =
                        new ObjectType(is.getReferenceType(cpg).toString());
                    if (t1.subclassOf(t2)) {
                        break;
                    }
                }
                catch (ClassNotFoundException e) {
                    throw new IncompleteClasspathException(e);
                }
            }
        }

        if (ih == null) {
            throw new ClassFormatError("Constructor does not call " +
                "superclass constructor");
        }

        return ih;
    }

    /**
     * Transforms a synchronized method to make it the equivalent of
     * enclosing the method body in a synchronized block and removing
     * the synchronized flag.
     *
     * <p>This enables all monitor related events to be properly raised
     * for synchronized methods.</p>
     *
     * @param mg BCEL handle to the method to be transformed.
     * @param il Instruction list for the method to be transformed.
     *
     * @return A handle to the last instruction of the synthetic exception
     * handler that is inserted to ensure release of the monitor on
     * exceptional exit. This may be used later to insert other instructions
     * that should be executed before an exceptional exit (such as method
     * exit instrumentation).
     */
    private InstructionHandle transformSynchronization(MethodGen mg,
            InstructionList il) {
        // Construct the entry and exit patches
        InstructionList enterPatch = new InstructionList();
        InstructionList exitPatch = new InstructionList();

        if (mg.isStatic()) {
            // For efficiency, we'll cache the reference to the class object
            // that is locked so that at the exit points we can just load it
            // from a local variable instead of calling getClass() again.
            // We create a new local variable for this purpose.
            int clLocalVar = mg.getMaxLocals();
            mg.setMaxLocals(mg.getMaxLocals() + 1);

            enterPatch.append(new PUSH(cpg, fullClassName));
            enterPatch.append(iFactory.createInvoke("java.lang.Class",
                "forName", Type.getType("Ljava/lang/Class;"),
                new Type[]{Type.STRING}, Constants.INVOKESTATIC));
            enterPatch.append(new DUP());
            enterPatch.append(new ASTORE(clLocalVar));
            exitPatch.append(new ALOAD(clLocalVar));
        }
        else {
            enterPatch.append(new ALOAD(0));
            exitPatch.append(new ALOAD(0));
        }
        enterPatch.append(new MONITORENTER());
        exitPatch.append(new MONITOREXIT());

        //InstructionHandle newStart = il.insert(il.getStart(), enterPatch);
        // Whoops -- this actually shouldn't be done; the synchronization
        // for the method should act as if it occurs before any bytecode
        // is executed, and thus is unreachable from the bytecode...
        //updateTargeters(origStart, newStart);
        
        il.insert(il.getStart(), enterPatch);
        enterPatch.dispose();

        insertBeforeReturns(il, exitPatch, false, 0, (byte) -1);

        // Insert GOTO that will jump handler by default
        InstructionHandle newEnd = il.insert(origEnd, new GOTO(origEnd));
        if (origStart == origEnd) { origStart = newEnd; }

        // Insert handler instructions
        InstructionList excExitPatch = exitPatch.copy();
        InstructionHandle relHandlerEnd = excExitPatch.getEnd();
        InstructionHandle handlerEnd = excExitPatch.append(new ATHROW());
        InstructionHandle handlerStart = il.insert(origEnd, excExitPatch);

        il.setPositions();
        exitPatch.dispose();
        excExitPatch.dispose();

        // Add handler - null catches any type
        CodeExceptionGen newHandler = mg.addExceptionHandler(origStart, newEnd,
            handlerStart, (ObjectType) null);
        if (observer != null) {
            observer.exceptionHandlerAdded(-1, newHandler, false);
        }

        // Add failsafe handler to ensure lock is released - emulated
        // from bytecode generated by the Sun compiler
        newHandler = mg.addExceptionHandler(handlerStart, relHandlerEnd,
            handlerStart, (ObjectType) null);
        if (observer != null) {
            observer.exceptionHandlerAdded(-1, newHandler, false);
        }

        // Remove synchronization flag from method
        mg.setAccessFlags(mg.getAccessFlags() & ~Constants.ACC_SYNCHRONIZED);

        //return handlerStart;
        return handlerEnd;
    }

    /**
     * Inserts a sequence of instructions before every <code>return</code>
     * in a method.
     *
     * <p>This is used to implement such things as method exit instrumentation
     * and synchronized method transformation.</p>
     *
     * @param il Instruction list of the method.
     * @param patch Sequence of instructions to be inserted before every
     * <code>return</code>.
     */
    private void insertBeforeReturns(InstructionList il,
            InstructionList patch, boolean observe,
            int probeId, byte eventCode) {
        InstructionHandle ih = il.getStart();
        for ( ; ih != null; ih = ih.getNext()) {
            if (ih.getInstruction() instanceof ReturnInstruction) {
                InstructionList locPatch = patch.copy();

                if (observe && (observer != null)) {
                    observer.probeInserted(probeId, eventCode, locPatch,
                        ih, false);
                }

                InstructionHandle new_ih = il.insert(ih, locPatch);
                // Update targets of any instructions pointing to the return
                // instruction
                updateTargeters(ih, new_ih);
            }
        }
    }

    /**
     * Inserts a one-time probe indicating the system is starting.
     *
     * @param il Instruction list for the method.
     */
    private void insertStartProbe(InstructionList il) {
        InstructionHandle insert_ih = il.getStart();
        if (adaptive) {
            curProbe = startProbeIteration();
            if (curProbe != null) {
                if (curProbe.start == getRealPosition(insert_ih)) {
                    return;
                }
            }
        }

        InstructionHandle realStart = il.getStart();
        InstructionList patch = new InstructionList();

        int strIndex = dataFile.addString("<start>");
        patch.append(iFactory.createGetStatic(PROBE_CLASS,
            START_FLAG_FIELD_NAME, Type.BOOLEAN));
        patch.append(new IFNE(realStart));
        patch.append(new ICONST(1));
        patch.append(iFactory.createPutStatic(PROBE_CLASS,
            START_FLAG_FIELD_NAME, Type.BOOLEAN));
        patch.append(new PUSH(cpg,
            buildEventMask(SemanticConstants.EVENT_START) + strIndex));
        patch.append(new INVOKESTATIC(triggerStaticCodeRefIdx));

        if (observer != null) {
            int probeId = observer.newProbe(EVENT_START, new THashSet());
            observer.probeInserted(probeId, EVENT_START,
                patch, realStart, false);
        }
        il.insert(patch);
        patch.dispose();
    }

    /**
     * Adds method entry probes to a method; handles all types of methods
     * including static initializers.
     *
     * @param mg BCEL handle to the method for which to insert method entry
     * probes.
     * @param il Instruction list for the method to be instrumented.
     */
    private InstructionHandle addEntryProbe(MethodGen mg, InstructionList il) {
        InstructionList instCode;
        InstructionHandle insert_ih;
        Instruction store_instr;
        byte eventCode = 0;
        Set<String> liveKeys = new THashSet();

        if (mg.isStatic()) {
            if (mg.getName().equals("<clinit>")) {
                
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessStaticInitializerEntry(
                            fullClassName)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return null;
                }

                eventCode = EVENT_STATIC_INIT_ENTER;
            }
            else {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessMethodEntry(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return null;
                }

                eventCode = EVENT_SMETHOD_ENTER;
            }
            insert_ih = il.getStart();

            if (adaptive) {
                curProbe = startProbeIteration();
                if (curProbe != null) {
                    if (curProbe.start
                            == getRealPosition(insert_ih)) {
                        return null;
                    }
                }
            }

            instCode = new InstructionList();
            store_instr = new INVOKESTATIC(triggerStaticCodeRefIdx);
        }
        else {
            if (mg.getName().equals("<init>")) {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessConstructorEntry(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return null;
                }

                insert_ih = origStart;
                eventCode = EVENT_CONSTRUCTOR_ENTER;
            }
            else {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessMethodEntry(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return null;
                }

                insert_ih = il.getStart();
                eventCode = EVENT_VMETHOD_ENTER;
            }

            if (adaptive) {
                curProbe = startProbeIteration();
                if (curProbe != null) {
                    if (curProbe.start
                            == getRealPosition(insert_ih)) {
                        return null;
                    }
                }
            }

            instCode = new InstructionList();
            instCode.append(new ALOAD(0));
            store_instr = new INVOKESTATIC(triggerCodeRefIdx);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(mg.getClassName());
        sb.append("#");
        sb.append(mg.getName());
        sb.append("#");
        sb.append(mg.getSignature());
        
        sb.append("#");
        sb.append(Integer.toHexString(mg.getAccessFlags()));
        
        int probePacket = buildEventMask(eventCode);
        probePacket += dataFile.addString(sb.toString());

        instCode.append(new PUSH(cpg, probePacket));
        InstructionHandle store_ih = instCode.append(store_instr);

        if (observer != null) {
            int probeId = observer.newProbe(eventCode, liveKeys);
            observer.probeInserted(probeId, eventCode,
                instCode, insert_ih, false);
        }
        InstructionHandle start_ih = il.insert(insert_ih, instCode);
        instCode.dispose();

        LocalVariableGen[] localVars = mg.getLocalVariables();
        int nLocals = localVars.length;
        for (int i = nLocals - 1; i >= 0; i--) {
            if ((localVars[i].getStart() == origStart)
                    || (localVars[i].getStart() == insert_ih)) {
                localVars[i].setStart(start_ih);
            }
        }
        
        return store_ih;
    }

    /**
     * Adds method exit probes to a method; handles all types of methods
     * including static initializers.
     *
     * @param mg BCEL handle to the method for which to insert method exit
     * probes.
     * @param il Instruction list for the method to be instrumented.
     * @param syntheticHandler Handle to the last instruction of any
     * synthetic catch-all handler previously inserted; used to guarantee
     * an exit event even on exceptional exit. May be <code>null</code>,
     * in which case the handler will be created.
     */
    private void addExitProbes(MethodGen mg, InstructionList il,
            InstructionHandle syntheticHandler) {
        if (adaptive && (methodLog.exitProbeId != -1)) {
            return;
        }

        InstructionList instCode;
        Instruction store_instr;
        byte eventCode;
        Set<String> liveKeys = new THashSet();

        if (mg.isStatic()) {
            if (mg.getName().equals("<clinit>")) {
                return;
            }
            else {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessMethodExit(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return;
                }

                eventCode = EVENT_SMETHOD_EXIT;
            }

            instCode = new InstructionList();
            store_instr = new INVOKESTATIC(triggerStaticCodeRefIdx);
        }
        else {
            if (mg.getName().equals("<init>")) {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessConstructorExit(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return;
                }

                eventCode = EVENT_CONSTRUCTOR_EXIT;
            }
            else {
                for (int i = specsLastIndex; i >= 0; i--) {
                    if (eventSpecs[i].witnessMethodExit(mg)) {
                        liveKeys.add(eventSpecs[i].getKey());
                    }
                }
                if (liveKeys.size() == 0) {
                    return;
                }

                eventCode = EVENT_VMETHOD_EXIT;
            }

            instCode = new InstructionList();
            instCode.append(new ALOAD(0));
            store_instr = new INVOKESTATIC(triggerCodeRefIdx);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(mg.getClassName());
        sb.append("#");
        sb.append(mg.getName());
        sb.append("#");
        sb.append(mg.getSignature());
        int probePacket = buildEventMask(eventCode);
        probePacket += dataFile.addString(sb.toString());

        instCode.append(new PUSH(cpg, probePacket));
        instCode.append(store_instr);

        int probeId = 0;
        if (observer != null) {
            probeId = observer.newProbe(eventCode, liveKeys);
        }

        // Insert instrumentation for regular return
        insertBeforeReturns(il, instCode, true, probeId, eventCode);
        
        // Insert handler instructions
        InstructionList handlerCode = instCode.copy();
        handlerCode.getEnd().getPrev().setInstruction(
            (new PUSH(cpg, probePacket + (1 << 20))).getInstruction());

        if (syntheticHandler == null) {
            InstructionHandle newEnd = il.insert(origEnd, new GOTO(origEnd));
            if (observer != null) {
                observer.probeInserted(probeId, eventCode, newEnd,
                    origEnd, false);
            }

            if (origStart == origEnd) { origStart = newEnd; }

            InstructionList locHandlerCode = handlerCode.copy();
            locHandlerCode.append(new ATHROW());

            if (observer != null) {
                observer.probeInserted(probeId, eventCode, locHandlerCode,
                    origEnd, false);
            }
            InstructionHandle handlerStart =
                il.insert(origEnd, locHandlerCode);

            // Add handler - null catches any type
            CodeExceptionGen new_handler = mg.addExceptionHandler(origStart,
                newEnd, handlerStart, (ObjectType) null);
            if (observer != null) {
                observer.exceptionHandlerAdded(probeId, new_handler, true);
            }
        }
        else {
            InstructionList locHandlerCode = handlerCode.copy();
            if (observer != null) {
                observer.probeInserted(probeId, eventCode, locHandlerCode,
                    syntheticHandler, false);
            }
            il.insert(syntheticHandler, locHandlerCode);
        }
        
        if (origEnd.getInstruction() instanceof ATHROW) {
            InstructionList locHandlerCode = handlerCode.copy();
            if (observer != null) {
                observer.probeInserted(probeId, eventCode, locHandlerCode,
                    origEnd, false);
            }
            InstructionHandle new_ih = il.insert(origEnd, locHandlerCode);
            updateTargeters(origEnd, new_ih);
        }

        //il.setPositions();
        instCode.dispose();

        if (observer != null) {
            observer.exitProbeAdded(probeId);
        }
    }

    /**
     * Inserts probes to raise monitor contention and acquisition events.
     *
     * @param il Instruction list for the method.
     * @param ih Handle to the <code>MONITORENTER</code> instruction.
     */
    private void addMonitorEnterProbes(MethodGen mg, InstructionList il,
            InstructionHandle ih) {
        InstructionList patch = new InstructionList();
        
        Set<String> contendLiveKeys = new THashSet();
        Set<String> acquireLiveKeys = new THashSet();
        
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessAnyMonitor(MonitorType.CONTEND, mg)) {
                contendLiveKeys.add(eventSpecs[i].getKey());
            }
            if (eventSpecs[i].witnessAnyMonitor(MonitorType.ACQUIRE, mg)) {
                acquireLiveKeys.add(eventSpecs[i].getKey());
            }
        }

        boolean witnessContend = contendLiveKeys.size() > 0;
        boolean witnessAcquire = acquireLiveKeys.size() > 0;

        int contendProbeId = 0;
        int acquireProbeId = 0;

        if (witnessAcquire) {
            patch.append(new DUP());
            if (observer != null) {
                acquireProbeId = observer.newProbe(EVENT_MONITOR_ACQUIRE,
                    acquireLiveKeys);
                observer.probeInserted(acquireProbeId, EVENT_MONITOR_ACQUIRE,
                    patch, ih, !witnessContend);
            }
            InstructionHandle new_ih = il.insert(ih, patch);
            updateTargeters(ih, new_ih);
            patch.dispose();
        }

        if (witnessContend) {
            patch.append(new DUP());
            patch.append(new PUSH(cpg, EVENT_MONITOR_CONTEND));
            patch.append(new INVOKESTATIC(triggerMonRefIdx));

            if (observer != null) {
                contendProbeId = observer.newProbe(EVENT_MONITOR_CONTEND,
                    contendLiveKeys);
                observer.probeInserted(contendProbeId, EVENT_MONITOR_CONTEND,
                    patch, ih, true);
            }
            InstructionHandle new_ih = il.insert(ih, patch);
            if (!witnessAcquire) updateTargeters(ih, new_ih);
            patch.dispose();
        }

        if (witnessAcquire) {
            patch.append(new PUSH(cpg, EVENT_MONITOR_ACQUIRE));
            patch.append(new INVOKESTATIC(triggerMonRefIdx));

            if (observer != null) {
                observer.probeInserted(acquireProbeId, EVENT_MONITOR_ACQUIRE,
                    patch, ih, false);
            }
            il.append(ih, patch);
            patch.dispose();
        }
    }

    /**
     * Inserts probes to raise monitor to-be-released and released events.
     *
     * @param il Instruction list for the method.
     * @param ih Handle to the <code>MONITOREXIT</code> instruction.
     */
    private void addMonitorExitProbes(MethodGen mg, InstructionList il,
            InstructionHandle ih) {
        InstructionList patch = new InstructionList();
        
        Set<String> preReleaseLiveKeys = new THashSet();
        Set<String> releaseLiveKeys = new THashSet();
        
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessAnyMonitor(MonitorType.PRE_RELEASE, mg)) {
                preReleaseLiveKeys.add(eventSpecs[i].getKey());
            }
            if (eventSpecs[i].witnessAnyMonitor(MonitorType.RELEASE, mg)) {
                releaseLiveKeys.add(eventSpecs[i].getKey());
            }
        }

        boolean witnessPreRelease = preReleaseLiveKeys.size() > 0;
        boolean witnessRelease = releaseLiveKeys.size() > 0;

        int preReleaseProbeId = 0;
        int releaseProbeId = 0;

        if (witnessRelease) {
            patch.append(new DUP());
            if (observer != null) {
                releaseProbeId = observer.newProbe(EVENT_MONITOR_RELEASE,
                    releaseLiveKeys);
                observer.probeInserted(releaseProbeId, EVENT_MONITOR_RELEASE,
                    patch, ih, !witnessPreRelease);
            }
            InstructionHandle new_ih = il.insert(ih, patch);
            updateTargeters(ih, new_ih);
            patch.dispose();
        }

        if (witnessPreRelease) {
            patch.append(new DUP());
            patch.append(new PUSH(cpg, EVENT_MONITOR_PRE_RELEASE));
            patch.append(new INVOKESTATIC(triggerMonRefIdx));

            if (observer != null) {
                preReleaseProbeId = observer.newProbe(EVENT_MONITOR_PRE_RELEASE,
                    preReleaseLiveKeys);
                observer.probeInserted(preReleaseProbeId,
                    EVENT_MONITOR_PRE_RELEASE, patch, ih, true);
            }
            InstructionHandle new_ih = il.insert(ih, patch);
            if (!witnessRelease) updateTargeters(ih, new_ih);
            patch.dispose();
        }

        if (witnessRelease) {
            patch.append(new PUSH(cpg, EVENT_MONITOR_RELEASE));
            patch.append(new INVOKESTATIC(triggerMonRefIdx));

            if (observer != null) {
                observer.probeInserted(releaseProbeId, EVENT_MONITOR_RELEASE,
                    patch, ih, false);
            }
            il.append(ih, patch);
            patch.dispose();
        }
    }

    /**
     * Inserts a probe to observe the execution of a <code>NEW</code>
     * instruction.
     *
     * @param il Instruction list for the method.
     * @param ih Handle to the <code>NEW</code> instruction in the
     * instruction list.
     */
    private void addNewObjectProbe(MethodGen mg, InstructionList il,
            InstructionHandle ih) {
        CPInstruction cpi = (CPInstruction) ih.getInstruction();
        Constant c = (Constant) cpg.getConstant(cpi.getIndex());

        int nameIndex = ((ConstantClass) c).getNameIndex();
        String className = ((ConstantUtf8)
            cpg.getConstant(nameIndex)).getBytes().replace('/', '.');

        Set<String> liveKeys = new THashSet();
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessNewObject(className, mg)) {
                liveKeys.add(eventSpecs[i].getKey());
            }
        }
        if (liveKeys.size() == 0) {
            return;
        }

        int strIndex = dataFile.addString(className);
        int probePacket = buildEventMask(EVENT_NEW_OBJ) + strIndex;

        InstructionList patch = new InstructionList();
        patch.append(new PUSH(cpg, probePacket));
        patch.append(new INVOKESTATIC(triggerStaticCodeRefIdx));

        if (observer != null) {
            int probeId = observer.newProbe(EVENT_NEW_OBJ,
                liveKeys);
            observer.probeInserted(probeId, EVENT_NEW_OBJ, patch, ih, true);
        }
        InstructionHandle new_ih = il.insert(ih, patch);
        patch.dispose();

        updateTargeters(ih, new_ih);
    }

    /**
     * Inserts a probe to observe a call.
     *
     * @param eventCode Event code associated with the particular type of
     * invoke instruction for which a probe is being inserted.
     * @param il Instruction list for the method.
     * @param ih Handle to the call instruction in the instruction list.
     */
    private void addCallProbe(byte eventCode, MethodGen mg,
            InstructionList il, InstructionHandle ih) {
        // TODO: (Possibly) Isolate specs requesting interceptors; optimize to
        // switch to non-interceptor form when no active specs require an
        // interceptor
        
        InvokeInstruction ii = (InvokeInstruction) ih.getInstruction();
        InstructionHandle next_ih = ih.getNext();
        Set<String> liveKeys = new THashSet();
        boolean interceptorRequired = false;
        
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessCall(ii, cpg, mg)) {
                liveKeys.add(eventSpecs[i].getKey());
                
                if (eventSpecs[i].useCallInterceptor(ii, cpg)) {
                    interceptorRequired |= true;
                }
            }
            else {
                String callClass = mg.getClassName();
                String specKey = eventSpecs[i].getKey();
                Set<String> specModuleClasses = moduleClasses.get(specKey);
                
                if (specModuleClasses.contains(fullClassName)) {
                    // If we are in a module class, ...
                    if (!specModuleClasses.contains(callClass)
                            && autoBoundaryEvents) {
                        // ...the called method is not in a module class,
                        // and automatic boundary events are enabled,
                        // observe the call
                        liveKeys.add(specKey);
                    }
                }
                else { // If we are not in a module class, ...
                    if (specModuleClasses.contains(callClass)) {
                        // ... the called method is in a module class,
                        // and automatic boundary events are enabled,
                        // observe the call
                        liveKeys.add(specKey);
                    }
                }
            }
        }
        if (liveKeys.size() == 0) {
            return;
        }

        StringBuilder sb =
            new StringBuilder(ii.getReferenceType(cpg).toString());
        sb.append("#");
        sb.append(ii.getName(cpg));
        sb.append("#");
        sb.append(ii.getSignature(cpg));
        //sb.append("#");
        //sb.append(<acc_flags for called method>);
        
        int strIndex = dataFile.addString(sb.toString());
        int callProbePacket = buildEventMask(eventCode) + strIndex;
        int returnProbePacket = buildEventMask(EVENT_CALL_RETURN) + strIndex;

        InstructionList regReturnPatch = new InstructionList();
        regReturnPatch.append(new PUSH(cpg, returnProbePacket));
        regReturnPatch.append(new INVOKESTATIC(triggerStaticCodeRefIdx));
        regReturnPatch.append(new GOTO(next_ih));

        InstructionList excReturnPatch = new InstructionList();
        InstructionHandle handler =
            excReturnPatch.append(new ASTORE(excHandlerVar));
        excReturnPatch.append(new PUSH(cpg, returnProbePacket + (1 << 20)));
        excReturnPatch.append(new INVOKESTATIC(triggerStaticCodeRefIdx));
        excReturnPatch.append(new ALOAD(excHandlerVar));
        excReturnPatch.append(new ATHROW());

        // Note that the call return event will be rolled into the
        // same probe ID; this is fine because we always want them
        // to be added and removed in conjunction with the
        // associated call event
        if (interceptorRequired) {
            insertCallInterceptor(eventCode, il, ih, callProbePacket,
                regReturnPatch, excReturnPatch, liveKeys);
        }
        else {
            InstructionList callPatch = new InstructionList();
            callPatch.append(new PUSH(cpg, callProbePacket));
            callPatch.append(new INVOKESTATIC(triggerStaticCodeRefIdx));

            int probeId = 0;
            if (observer != null) {
                probeId = observer.newProbe(eventCode, liveKeys);
                observer.probeInserted(probeId, eventCode, callPatch,
                    ih, true);
            }
            InstructionHandle new_ih = il.insert(ih, callPatch);
            callPatch.dispose();

            updateTargeters(ih, new_ih);

            if (observer != null) {
                observer.probeInserted(probeId, eventCode,
                    regReturnPatch, next_ih, false);
            }
            il.insert(next_ih, regReturnPatch);
            if (observer != null) {
                observer.probeInserted(probeId, eventCode,
                    excReturnPatch, next_ih, false);
            }
            il.insert(next_ih, excReturnPatch);
            CodeExceptionGen new_handler = mg.addExceptionHandler(ih, ih,
                handler, (ObjectType) null);
            if (observer != null) {
                observer.exceptionHandlerAdded(probeId, new_handler, true);
            }
        }

        regReturnPatch.dispose();
        excReturnPatch.dispose();
    }

    private void insertCallInterceptor(byte eventCode, InstructionList il,
            InstructionHandle call, int callProbePacket,
            InstructionList regReturnPatch, InstructionList excReturnPatch,
            Set<String> liveKeys) {
        InvokeInstruction invoke = (InvokeInstruction) call.getInstruction();
        short invokeOpcode = invoke.getOpcode();

        // Ignore calls to constructors
        if ((invokeOpcode == Constants.INVOKESPECIAL)
                && invoke.getMethodName(cpg).equals("<init>")) {
            return;
        }

        int probeId = 0;

        MethodSignature invokeSig = new MethodSignature(invoke, cpg);
        if (interceptorCache.containsKey(invokeSig)) {
            InvokeInstruction relayCall =
                (InvokeInstruction) interceptorCache.get(invokeSig);
            call.setInstruction(relayCall.copy());
            if (observer != null) {
                probeId = observer.newProbe(eventCode, liveKeys);
                observer.callInterceptorAdded(probeId, eventCode, call,
                    invokeOpcode, new MethodSignature(relayCall, cpg),
                    invokeSig);
            }
            return;
        }

        NumberFormat fmt = NumberFormat.getIntegerInstance();
        fmt.setGroupingUsed(false);
        fmt.setMinimumIntegerDigits(5);
        String interceptorName = "intercept$" + fmt.format(curInterceptID++);

        boolean isStatic = (invokeOpcode == Constants.INVOKESTATIC);
        Type returnType = invoke.getReturnType(cpg);
        Type[] argTypes = invoke.getArgumentTypes(cpg);

        boolean isNative = false;
        if (!isStatic) {
            Type[] tmpArgTypes = new Type[argTypes.length + 1];
            tmpArgTypes[0] = invoke.getLoadClassType(cpg);
            System.arraycopy(argTypes, 0, tmpArgTypes, 1, argTypes.length);
            argTypes = tmpArgTypes;
        }
        else {
            try {
                isNative = isNative((INVOKESTATIC) invoke, cpg);
            }
            catch (ClassNotFoundException e) {
                throw new SofyaError("Could not determine if call is " +
                    "to native method");
            }
        }

        String[] argNames = new String[argTypes.length];
        for (int i = 0; i < argNames.length; i++) {
            argNames[i] = "arg" + i;
        }

        InstructionList code = new InstructionList();

        MethodGen interceptor = new MethodGen(
            Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_FINAL,
            returnType, argTypes, argNames,
            interceptorName, fullClassName,
            code,
            cpg);

        // Build interceptor code

        // Insert probe
        callProbePacket = callProbePacket + (1 << 20);  // The interceptor flag
        if (isNative) {
            callProbePacket = callProbePacket + (1 << 21);
        }

        code.append(new PUSH(cpg, callProbePacket));
        InstructionHandle probe = code.append(
            new INVOKESTATIC(triggerStaticCodeRefIdx));

        // Copy the source code line number. We extract these from the line
        // number table prior to modifying the method, so it is just a
        // quick lookup on the handle to the call instruction
        int lineNum = lineNumbers.get(call);
        if (lineNum == 0) {
            // The compile sometimes associates the line number with the
            // argument loading instructions prior to the call. If we don't
            // get a valid line number at the call instruction itself, do
            // a quick heuristic search of preceding instructions
            InstructionHandle prev = call.getPrev();
            while ((prev != null)
                    && ((prev.getInstruction().produceStack(cpg) > 0)
                       || (prev.getInstruction() instanceof InvokeInstruction)
                       || (getRealPosition(prev) == -1))) {
                lineNum = lineNumbers.get(prev);
                if (lineNum != 0) {
                    break;
                }
                prev = prev.getPrev();
            }
        }
        if (lineNum == 0) {
            System.err.println("WARNING: Could not determine source code " +
                "line number for call in\n    " + fullClassName + "." +
                methodName + " (" + call.getPosition() + ": " +
                invoke.getMethodName(cpg) + ")");
        }
        else {
            interceptor.addLineNumber(probe, lineNum);
        }

        int argCount= argTypes.length;
        int lvOffset = 0;
        for (int i = 0; i < argCount; i++) {
            code.append(InstructionFactory.createLoad(argTypes[i], lvOffset));
            lvOffset += argTypes[i].getSize();
        }
        InstructionHandle nc = code.append(invoke.copy());

        BranchHandle jump = (BranchHandle) regReturnPatch.getEnd();
        code.append(regReturnPatch);
        InstructionHandle handler = excReturnPatch.getStart();
        code.append(excReturnPatch);

        jump.setTarget(code.append(
            InstructionFactory.createReturn(returnType)));

        //il.setPositions();
        interceptor.setMaxLocals();
        interceptor.setMaxStack();

        interceptor.addExceptionHandler(nc, nc, handler, (ObjectType) null);

        javaClassFile.addMethod(interceptor.getMethod());
        dataFile.recordCallInterceptor(fullClassName, interceptor.getName());
        code.dispose();

        // Replace original call
        InvokeInstruction relayCall = iFactory.createInvoke(
            fullClassName, interceptorName,
            returnType, argTypes,
            Constants.INVOKESTATIC);
        call.setInstruction(relayCall.copy());

        interceptorCache.put(invokeSig, relayCall);

        if (observer != null) {
            probeId = observer.newProbe(eventCode, liveKeys);
            observer.callInterceptorAdded(probeId, eventCode, call, invokeOpcode,
                new MethodSignature(relayCall, cpg), invokeSig);
        }
    }

    /**
     * Inserts a probe to observe the catching of an exception by a handler.
     *
     * @param il Instruction list for the method.
     * @param ih Handle to the first instruction of the exception handler.
     */
    private void addCatchProbe(MethodGen mg, InstructionList il,
            InstructionHandle ih) {
        CodeExceptionGen handler = (CodeExceptionGen) handlerStarts.get(ih);
        ObjectType catchType = handler.getCatchType();
        if (catchType == null) {
            catchType = Type.THROWABLE;
        }

        Set<String> liveKeys = new THashSet();
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessCatch(catchType.toString(), mg)) {
                liveKeys.add(eventSpecs[i].getKey());
            }
        }
        if (liveKeys.size() == 0) {
            return;
        }

        InstructionList patch = new InstructionList();

        patch.append(new DUP());
        patch.append(iFactory.createInvoke("java.lang.Object", "getClass",
                Type.getType("Ljava/lang/Class;"), new Type[]{},
                Constants.INVOKEVIRTUAL));
        patch.append(new INVOKESTATIC(triggerCatchRefIdx));

        if (observer != null) {
            int probeId = observer.newProbe(EVENT_CATCH,
                liveKeys);
            observer.probeInserted(probeId, EVENT_CATCH, patch, ih, false);
        }
        InstructionHandle new_ih = il.insert(ih, patch);
        patch.dispose();

        updateTargeters(ih, new_ih);
    }
    
    private void processFieldInstruction(MethodGen mg, InstructionList il,
            InstructionHandle ih, FieldType fType) {
        boolean shouldWitness = false;
        FieldInstruction fi = (FieldInstruction) ih.getInstruction();
        
        Set<String> liveKeys = new THashSet();
        for (int i = specsLastIndex; i >= 0; i--) {
            if (eventSpecs[i].witnessField(fi, cpg, fType, mg)) {
                liveKeys.add(eventSpecs[i].getKey());
                shouldWitness = true;
            }
        }
        if (!shouldWitness) {
            return;
        }
        
        byte eventCode;
        
        Type declType = fi.getReferenceType(cpg);
        Type fldType = fi.getType(cpg);
        
        String className = declType.toString();
        String shortName = fi.getFieldName(cpg);
        String fullName =  className + "." + shortName;
        
        String declTypeSig = declType.getSignature();
        String fldTypeSig = fldType.getSignature();
        
        if (DEBUG) {
            System.out.println("SemInst:fullName=" + fullName);
            System.out.println("SemInst:declTypeSig=" + declTypeSig);
            System.out.println("SemInst:fldTypeSig=" + fldTypeSig);
        }

        int icptIndex;
        
        switch (fType.toInt()) {
        case FieldType.IGETSTATIC:
            eventCode = EVENT_GETSTATIC;
            
            if (fldReadIcptIndices.containsKey(fullName)) {
                icptIndex = fldReadIcptIndices.get(fullName);
            }
            else if (className.equals(fullClassName)
                    || isInterface(className)) {
                icptIndex = genFieldInterceptor(shortName, fldType,
                    declType, true, true);
                fldReadIcptIndices.put(fullName, icptIndex);
            }
            else {
                icptIndex = cpg.addMethodref(className,
                    FIELD_READ_INTERCEPTOR_NAME + shortName,
                    "()" + fldTypeSig);
                fldReadIcptIndices.put(fullName, icptIndex);
            }
            
            break;
        case FieldType.IPUTSTATIC:
            eventCode = EVENT_PUTSTATIC;
            
            if (fldWriteIcptIndices.containsKey(fullName)) {
                icptIndex = fldWriteIcptIndices.get(fullName);
            }
            else if (className.equals(fullClassName)
                    || isInterface(className)) {
                icptIndex = genFieldInterceptor(shortName, fldType,
                    declType, true, false);
                fldWriteIcptIndices.put(fullName, icptIndex);
            }
            else {
                icptIndex = cpg.addMethodref(className,
                    FIELD_WRITE_INTERCEPTOR_NAME + shortName,
                    "(" + fldTypeSig + ")V");
                fldWriteIcptIndices.put(fullName, icptIndex);
            }
            
            break;
        case FieldType.IGETFIELD:
            eventCode = EVENT_GETFIELD;
            
            if (fldReadIcptIndices.containsKey(fullName)) {
                icptIndex = fldReadIcptIndices.get(fullName);
            }
            else if (className.equals(fullClassName)
                    || isInterface(className)) {
                icptIndex = genFieldInterceptor(shortName, fldType,
                    declType, false, true);
                fldReadIcptIndices.put(fullName, icptIndex);
            }
            else {
                icptIndex = cpg.addMethodref(className,
                    FIELD_READ_INTERCEPTOR_NAME + shortName,
                    "(" + declTypeSig + ")" + fldTypeSig);
                fldReadIcptIndices.put(fullName, icptIndex);
            }
            
            break;
        case FieldType.IPUTFIELD:
            eventCode = EVENT_PUTFIELD;
            
            if (fldWriteIcptIndices.containsKey(fullName)) {
                icptIndex = fldWriteIcptIndices.get(fullName);
            }
            else if (className.equals(fullClassName)
                    || isInterface(className)) {
                icptIndex = genFieldInterceptor(shortName, fldType,
                    declType, false, false);
                fldWriteIcptIndices.put(fullName, icptIndex);
            }
            else {
                icptIndex = cpg.addMethodref(className,
                    FIELD_WRITE_INTERCEPTOR_NAME + shortName,
                    "(" + declTypeSig + fldTypeSig + ")V");
                fldWriteIcptIndices.put(fullName, icptIndex);
            }
            
            break;
        default:
             throw new AssertionError("Unknown field type: " + fType.toInt() +
                 "=" + fType.toString());
        }
        
        if (observer != null) {
            int probeId = observer.newProbe(eventCode, liveKeys);
            observer.fieldInterceptorAdded(probeId, eventCode, ih, cpg);
        }
        
        // Must follow observer notification, so that the observer can
        // pick up the original field instruction properly
        InvokeInstruction icpt_call = new INVOKESTATIC(icptIndex);
        ih.setInstruction(icpt_call);
    }

    private final boolean isInterface(String className)
            throws IncompleteClasspathException {
        ObjectType classType = new ObjectType(className);
        try {
            return classType.referencesInterfaceExact();
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }
    }
    
    /**
     * Inserts a probe to witness an array element event.
     *
     * @param il Instruction list for the method being instrumented.
     * @param ih Handle to the array element instruction for which to insert
     * the probe.
     * @param isWrite Flag indicating whether the array element is being
     * written.
     */
    private void insertArrayProbe(MethodGen mg, InstructionList il,
            InstructionHandle ih, ArrayElementType elemAction) {
        //System.out.println();
        //System.out.println("SemInst:2093:" + elemAction);
        
        ArrayInstruction instr = (ArrayInstruction) ih.getInstruction();
        
        Set<String> liveKeys = new java.util.HashSet();
        Map<Type, IntervalList> preds =
            new java.util.HashMap<Type, IntervalList>();
        
        List<ArrayElementBounds> boundsList =
            new ArrayList<ArrayElementBounds>();
        for (int j = specsLastIndex; j >= 0; j--) {
            if (!eventSpecs[j].witnessArrayElement(instr, cpg, mg,
                    elemAction, boundsList)) {
                continue;
            }
            
            int size = boundsList.size();
            Iterator<ArrayElementBounds> iterator =
                boundsList.iterator();
            for (int k = size; k-- > 0; ) {
                ArrayElementBounds bounds = iterator.next();
                
                IntervalList boundPreds = preds.get(
                    bounds.javaType);
                if (boundPreds == null) {
                    boundPreds = new IntervalList();
                    preds.put(bounds.javaType, boundPreds);
                }
                boundPreds.addInterval(bounds);
            }
            
            liveKeys.add(eventSpecs[j].getKey());
        }
        
        //if (preds.size() > 0) {
            //System.out.println("SemInst:2009:preds=");
            //System.out.println(preds);
            //System.out.println();
        //}

        if (liveKeys.size() == 0) {
            return;
        }

        if (arrayElemClassLocal < 0) {
            arrayElemClassLocal = mg.getMaxLocals();
            mg.setMaxLocals(mg.getMaxLocals() + 1);
        }
        if (arrayIndexLocal < 0) {
            arrayIndexLocal = mg.getMaxLocals();
            mg.setMaxLocals(mg.getMaxLocals() + 1);
        }
        
        Type elemType = instr.getType(cpg);
        boolean needClassLiteral = false;
        
        BranchHandle pendingTypeCheck = nullBh;
        BranchHandle[] pendingRangeChecks =
            new BranchHandle[]{ nullBh, nullBh };
        
        InstructionList patch = new InstructionList();

        InstructionList cleanup = new InstructionList();
        InstructionHandle finish_ih =
            cleanup.append(new ILOAD(arrayIndexLocal));
        
        InstructionHandle new_ih;
        if (elemAction == ArrayElementType.STORE) {
            new_ih = patch.append(
                InstructionFactory.createStore(elemType, fieldValueLocal));
            patch.append(new ISTORE(arrayIndexLocal));
            
            cleanup.append(
                InstructionFactory.createLoad(elemType, fieldValueLocal));
        }
        else { // Element read
            new_ih = patch.append(new ISTORE(arrayIndexLocal));
        }
        
        InstructionHandle splice_ih = patch.getEnd();
        
        // Update targeters of array instruction before generating
        // instrumentation, so that we can set jumps to the array
        // instruction in the instrumentation
        updateTargeters(ih, new_ih);
        
        // Store for the end (if there is any wildcard)
        IntervalList wildcardInterval = preds.remove(TYPE_ANY);
        
        // Iterate and add per-type interval checks (no particular
        // order required since there is precise type matching)
        int size = preds.size();
        Iterator<Type> predTypes = preds.keySet().iterator();
        for (int i = size; i-- > 0; ) {
            Type curType = predTypes.next();
            
            // Single dimension arrays of primitives are not covariant
            // with single dimension arrays of objects, so we can
            // avoid the current check if there is a mismatch of that type
            if (elemType instanceof BasicType) {
                if (!(curType instanceof BasicType)) {
                    continue;
                }
            }
            else if (curType instanceof BasicType) {
                continue;
            }
            
            needClassLiteral = true;
            
            IntervalList intervals = preds.get(curType);
            
            // The type check
            InstructionHandle check_ih =
                patch.append(new ALOAD(arrayElemClassLocal));
            pendingTypeCheck.setTarget(check_ih);
            
            patch.append(genLoadClassRef(curType));
            pendingTypeCheck = patch.append(new IF_ACMPNE(null));
            
            genArrayElementChecks(patch, elemType, intervals, elemAction,
                pendingRangeChecks, finish_ih);
        }
        
        // Handle wildcard check
        InstructionHandle check_ih = null;
        if (wildcardInterval != null) {
            check_ih = genArrayElementChecks(patch, elemType, 
                wildcardInterval, elemAction,
                pendingRangeChecks, finish_ih);
        }
        
        if (needClassLiteral) {
            new_ih = patch.append(splice_ih, new DUP());
            new_ih = patch.append(new_ih,
                new INVOKEVIRTUAL(cpg.addMethodref("java.lang.Object",
                "getClass", "()Ljava/lang/Class;")));
            new_ih = patch.append(new_ih,
                new INVOKEVIRTUAL(cpg.addMethodref("java.lang.Class",
                "getComponentType", "()Ljava/lang/Class;")));
            patch.append(new_ih, new ASTORE(arrayElemClassLocal));
        }
        
        patch.append(cleanup);
        if (check_ih != null) {
            pendingTypeCheck.setTarget(check_ih);
        }
        else {
            pendingTypeCheck.setTarget(finish_ih);
        }
        
        pendingRangeChecks[0].setTarget(finish_ih);
        pendingRangeChecks[1].setTarget(finish_ih);

        if (observer != null) {
            byte eventCode;
            if (elemAction == ArrayElementType.STORE) {
                eventCode = EVENT_PUTFIELD;
            }
            else {
                eventCode = EVENT_GETFIELD;
            }
            int probeId = observer.newProbe(eventCode, liveKeys);
            observer.probeInserted(probeId, eventCode, patch, ih, true);
        }
        
        il.insert(ih, patch);
        patch.dispose();
    }
    
    private final InstructionHandle genArrayElementChecks(
            InstructionList patch, Type elemType, IntervalList intervals,
            ArrayElementType elemAction, BranchHandle[] pendingRangeChecks,
            InstructionHandle finish_ih) {
        InstructionHandle start_ih = null;
        InstructionHandle check_ih;
        boolean headIsTail = false;
        
        // Index range checks
        IntervalNode node = intervals.head;
        if (node != intervals.TAIL) {
            // Handle the head node
            if (node.min < 0) { // (Unbounded)
                if (node.max >= 0) {
                    start_ih = check_ih =
                        patch.append(new ILOAD(arrayIndexLocal));
                    
                    pendingRangeChecks[0].setTarget(check_ih);
                    pendingRangeChecks[1].setTarget(check_ih);
                        
                    patch.append(new PUSH(cpg, node.max));
                    pendingRangeChecks[0] =
                        patch.append(new IF_ICMPGT(null));
                    pendingRangeChecks[1] = nullBh;
                    
                    genArrayElemTrigger(patch, elemType, elemAction);
                    patch.append(new GOTO(finish_ih));
                }
                else if (ASSERTS) {
                    throw new AssertionError("Unbounded head node");
                }
            }
            else {
                if (ASSERTS) {
                    assert (node.max >= 0);
                }
                    
                if (node.max == node.min) {
                    start_ih = check_ih =
                        patch.append(new ILOAD(arrayIndexLocal));
                    
                    pendingRangeChecks[0].setTarget(check_ih);
                    pendingRangeChecks[1].setTarget(check_ih);
                    
                    patch.append(new PUSH(cpg, node.max));
                    pendingRangeChecks[0] =
                        patch.append(new IF_ICMPNE(null));
                    pendingRangeChecks[1] = nullBh;
                    
                    genArrayElemTrigger(patch, elemType, elemAction);
                    patch.append(new GOTO(finish_ih));
                }
                else { // A closed, inclusive interval
                    start_ih = check_ih =
                        patch.append(new ILOAD(arrayIndexLocal));
                    
                    pendingRangeChecks[0].setTarget(check_ih);
                    pendingRangeChecks[1].setTarget(check_ih);

                    patch.append(new PUSH(cpg, node.min));
                    pendingRangeChecks[0] =
                        patch.append(new IF_ICMPLT(null));
                    
                    patch.append(new ILOAD(arrayIndexLocal));
                    patch.append(new PUSH(cpg, node.max));
                    pendingRangeChecks[1] =
                        patch.append(new IF_ICMPGT(null));
                    
                    genArrayElemTrigger(patch, elemType, elemAction);
                    patch.append(new GOTO(finish_ih));
                }
            }
            
            node = node.next;

            while (node != intervals.TAIL) {
                if (ASSERTS) {
                    // Only head or tail may have unbounded interval
                    assert (node.min >= 0) && (node.max >= 0);
                }
                
                if (node.max == node.min) {
                    check_ih =
                        patch.append(new ILOAD(arrayIndexLocal));
                    
                    pendingRangeChecks[0].setTarget(check_ih);
                    pendingRangeChecks[1].setTarget(check_ih);
                    
                    patch.append(new PUSH(cpg, node.max));
                    pendingRangeChecks[0] =
                        patch.append(new IF_ICMPNE(null));
                    pendingRangeChecks[1] = nullBh;
                    
                    genArrayElemTrigger(patch, elemType, elemAction);
                    patch.append(new GOTO(finish_ih));
                }
                else { // A closed, inclusive interval
                    check_ih =
                        patch.append(new ILOAD(arrayIndexLocal));
                    
                    pendingRangeChecks[0].setTarget(check_ih);
                    pendingRangeChecks[1].setTarget(check_ih);

                    patch.append(new PUSH(cpg, node.min));
                    pendingRangeChecks[0] =
                        patch.append(new IF_ICMPLT(null));
                    
                    patch.append(new ILOAD(arrayIndexLocal));
                    patch.append(new PUSH(cpg, node.max));
                    pendingRangeChecks[1] =
                        patch.append(new IF_ICMPGT(null));
                    
                    genArrayElemTrigger(patch, elemType, elemAction);
                    patch.append(new GOTO(finish_ih));
                }
                
                node = node.next;
            }
        }
        else {
            headIsTail = true;
        }
        
        // Should be at the tail
        if (ASSERTS) {
            assert node == intervals.TAIL;
            // There should never be any max set for TAIL
            assert node.max < 0;
        }
        
        if (node.min >= 0) {
            check_ih = patch.append(new ILOAD(arrayIndexLocal));
            if (start_ih == null) start_ih = check_ih;
            pendingRangeChecks[0].setTarget(check_ih);
            pendingRangeChecks[1].setTarget(check_ih);

            patch.append(new PUSH(cpg, node.min));
            patch.append(new IF_ICMPLT(finish_ih));
            genArrayElemTrigger(patch, elemType, elemAction);
            patch.append(new GOTO(finish_ih));
        }
        else if (headIsTail) { // (Unbounded)
            genArrayElemTrigger(patch, elemType, elemAction);
            patch.append(new GOTO(finish_ih));
        }
        else { // tail is just a sentinel
            pendingRangeChecks[0].setTarget(finish_ih);
            pendingRangeChecks[1].setTarget(finish_ih);
        }
        
        pendingRangeChecks[0] = pendingRangeChecks[1] = nullBh;
        
        return start_ih;
    }
    
    private final Instruction genLoadClassRef(Type type) {
        // The cases are organized to generate a tableswitch...
        // Cheap optimizations add up for something on the critical path
        switch (type.getType()) {
        case Constants.T_BOOLEAN:
            return iFactory.createGetStatic("java.lang.Boolean",
                "TYPE", Type.CLASS);
        case Constants.T_CHAR:
            return iFactory.createGetStatic("java.lang.Character",
                "TYPE", Type.CLASS);
        case Constants.T_FLOAT:
            return iFactory.createGetStatic("java.lang.Float",
                "TYPE", Type.CLASS);
        case Constants.T_DOUBLE:
            return iFactory.createGetStatic("java.lang.Double",
                "TYPE", Type.CLASS);
        case Constants.T_BYTE:
            return iFactory.createGetStatic("java.lang.Byte",
                "TYPE", Type.CLASS);
        case Constants.T_SHORT:
            return iFactory.createGetStatic("java.lang.Short",
                "TYPE", Type.CLASS);
        case Constants.T_INT:
            return iFactory.createGetStatic("java.lang.Integer",
                "TYPE", Type.CLASS);
        case Constants.T_LONG:
            return iFactory.createGetStatic("java.lang.Long",
                "TYPE", Type.CLASS);
        case Constants.T_VOID:
            return iFactory.createGetStatic("java.lang.Void",
                "TYPE", Type.CLASS);
        case Constants.T_ARRAY:
            return new LDC_W(cpg.addArrayClass((ArrayType) type));
        case Constants.T_OBJECT: // Same as T_REFERENCE
            return new LDC_W(cpg.addClass((ObjectType) type));
        default:
            throw new ClassGenException("Cannot generate instructions " +
                "to load class constant for \"" + type + "\"");
        }
    }
    
    private final void genArrayElemTrigger(InstructionList patch,
            Type elemType, ArrayElementType elemAction) {
        String triggerName, triggerSig;
        
        patch.append(new DUP());
        patch.append(new DUP());
        patch.append(new ILOAD(arrayIndexLocal));
        
        // NEW
        patch.append(InstructionFactory.createArrayLoad(elemType));
        patch.append(new ILOAD(arrayIndexLocal));
        
        if (elemAction == ArrayElementType.LOAD) {
            triggerName = TRIGGER_ARRAY_ELEM_LOAD_NAME;
            
            if (elemType instanceof BasicType) {
                String elemTypeSig = elemType.getSignature();
                triggerSig = "([" + elemTypeSig + elemTypeSig + "I)V";
            }
            else {
                triggerSig = "([Ljava/lang/Object;Ljava/lang/Object;I)V";
            }
        }
        else {
            triggerName = TRIGGER_ARRAY_ELEM_STORE_NAME;
            
            if (elemType instanceof BasicType) {
                String typeSig = elemType.getSignature();
                triggerSig = "([" + typeSig + typeSig + "I" + typeSig + ")V";
            }
            else {
                triggerSig = "([Ljava/lang/Object;Ljava/lang/Object;I" +
                    "Ljava/lang/Object;)V";
            }
            
            patch.append(InstructionFactory.createLoad(
                elemType, fieldValueLocal));
        }

        patch.append(new INVOKESTATIC(cpg.addMethodref(PROBE_CLASS,
            triggerName, triggerSig)));
    }

    /**
     * Updates the targets of instructions to point to a probe which has
     * been inserted in front of the instruction they target.
     */
    private void updateTargeters(InstructionHandle ih,
            InstructionHandle new_ih) {
        // Update targeters, which includes all affected branches
        // and exception handlers
        InstructionTargeter[] targeters = ih.getTargeters();
        if (targeters != null) {
            for (int t = 0; t < targeters.length; t++) {
                /* We only want to update the start offsets to _handler_ blocks
                   of exception handlers. The offsets to the start and end
                   instructions of the region watched for exceptions should not
                   be changed, otherwise there are circumstances where we may
                   actually shift the watched region such that the code that
                   is supposed to be protected in fact no longer is. */
                if (targeters[t] instanceof CodeExceptionGen) {
                    CodeExceptionGen exceptionHandler =
                        (CodeExceptionGen) targeters[t];
                    if ((exceptionHandler.getStartPC() == ih) ||
                        (exceptionHandler.getEndPC() == ih)) {
                        continue;
                    }
                }
                targeters[t].updateTarget(ih, new_ih);
            }
        }
    }

    private void insertClassPrepareProbe(MethodGen mg, InstructionList il) {
        InstructionList patch = new InstructionList();
        
//        patch.append(iFactory.createGetStatic(
//            "java.lang.System", "out", Type.getType("Ljava/io/PrintStream;")));
//        patch.append(new PUSH(cpg, fullClassName + ": <clinit> enter"));
//        patch.append(iFactory.createInvoke("java.io.PrintStream", "println",
//             Type.VOID, new Type[]{Type.OBJECT}, Constants.INVOKEVIRTUAL));
        
        patch.append(iFactory.createGetStatic(PROBE_CLASS,
            CPE_FLAG_FIELD_NAME, Type.BYTE));
        BranchHandle detach_cond = patch.append(new IFGT(null));
        
        // Wait until the flag is set, checking it on 10ms intervals
        InstructionHandle loop_start = patch.append(
            iFactory.createGetStatic(fullClassName, CPE_FLAG_FIELD_NAME,
                Type.BYTE));
        BranchHandle loop_cond = patch.append(new IFGT(null));
        
//        patch.append(iFactory.createGetStatic(
//            "java.lang.System", "out", Type.getType("Ljava/io/PrintStream;")));
//        patch.append(new PUSH(cpg, "<clinit> waiting on flag"));
//        patch.append(iFactory.createInvoke("java.io.PrintStream", "println",
//             Type.VOID, new Type[]{Type.OBJECT}, Constants.INVOKEVIRTUAL));
        
        patch.append(new PUSH(cpg, 10l));
        patch.append(iFactory.createInvoke("java.lang.Thread", "sleep",
            Type.VOID, new Type[]{Type.LONG}, Constants.INVOKESTATIC));
        patch.append(new GOTO(loop_start));
        
//        InstructionHandle exit_ih = patch.append(iFactory.createGetStatic(
//            "java.lang.System", "out", Type.getType("Ljava/io/PrintStream;")));
//        patch.append(new PUSH(cpg, "<clinit> exit"));
//        patch.append(iFactory.createInvoke("java.io.PrintStream", "println",
//            Type.VOID, new Type[]{Type.OBJECT}, Constants.INVOKEVIRTUAL));
        
        if (observer != null) {
            observer.methodBegin(mg, il);
            int probeId = observer.newProbe((byte) -1, new THashSet());
            observer.probeInserted(probeId, (byte) -1, patch,
                il.getStart(), false);
        }
        InstructionHandle end_ih = il.getStart();
        detach_cond.setTarget(end_ih);
        loop_cond.setTarget(end_ih);
        il.insert(patch);
        
        clinitPrepared = true;
    }
    
    /**
     * This method creates or patches a static initializer to transmit the
     * probe that raises an event indicating the preparation of a new class.
     *
     * <p>Due to (as usual) a bug in the JDI, the preparing thread may not
     * be properly suspended at the point of writing the class-prepared
     * probe. Thus in the cases where a static initializer must be created
     * (as opposed to modifying an existing one), it was found to be small
     * enough that the thread might sometimes execute the synthetic
     * initializer and additional code in the class before the class prepare
     * event could be processed, which of course caused events of interest
     * to be lost. Therefore, such synthetic initializers force the thread
     * into a sleep loop until the event dispatcher sets a flag
     * variable permitting the thread to continue. The flag is checked
     * each time the thread wakes. Since this only happens once, during
     * static initialization of the class, and is transparent to the existing
     * user code, the interference associated with this technique is deemed
     * negligible. Note that this is only done for the synthetic static
     * initializers, as existing initializers seem to be large enough to
     * always cause proper processing of the class prepare events.</p>
     */
    private void addClassPrepareEvent() {
        // Locate existing static initializer, if any
        int pos = 0;
        Method staticInit = null;
        Method[] methods = javaClassFile.getMethods();
        for ( ; pos < methods.length; pos++) {
            if (methods[pos].getName().equals("<clinit>")) {
                staticInit = methods[pos];
                break;
            }
        }

        if (staticInit == null) {
            InstructionList code = new InstructionList();
            code.append(new RETURN());
            
            // Create the method
            MethodGen mg = new MethodGen(0,
                Type.VOID, new Type[]{}, new String[]{},
                "<clinit>", fullClassName, code, cpg);
            
            insertClassPrepareProbe(mg, code);

            mg.setMaxStack();
            mg.setMaxLocals(1);

            javaClassFile.addMethod(mg.getMethod());
            if (observer != null) {
                observer.staticInitializerAdded();
            }
        }
        else {
            MethodGen mg = new MethodGen(staticInit, fullClassName, cpg);
            InstructionList il = (InstructionList) mg.getInstructionList();
            
            insertClassPrepareProbe(mg, il);

            mg.setInstructionList(il);
            mg.setMaxStack();
            
            javaClassFile.setMethodAt(mg.getMethod(), pos);
            
            if (observer != null) {
                observer.methodEnd(mg, il, null);
            }
        }
    }

    private final void finishClass() {
        if (!adaptive
                || (javaClassFile.containsField(CPE_FLAG_FIELD_NAME)
                        == null)) {
            FieldGen fg = new FieldGen(Constants.ACC_PRIVATE |
                Constants.ACC_STATIC | Constants.ACC_TRANSIENT,
                Type.BYTE, CPE_FLAG_FIELD_NAME, cpg);
            javaClassFile.addField(fg.getField());
        }

        // At runtime, if the class wasn't loaded yet, the act of
        // requesting the class bytecode would cause it to be prepared
        // before we would have the opportunity to insert this event
        // anyway. If this proves to be a problem, we might have to
        // look for an alternative.
        if (!adaptive && !clinitPrepared) {
            addClassPrepareEvent();
        }

        if (observer != null) observer.classEnd(fullClassName);
    }

    public JavaClass generateClass() {
        finishClass();
        return super.generateClass();
    }

    public void writeClass(OutputStream dest) throws IOException {
        finishClass();
        super.writeClass(dest);
    }

    private static void instrumentClass(OutputStream fout) throws Exception {
        inst.instrumentAll();
        inst.writeClass(fout);
    }

    private static void instrumentClassFiles(ProgramUnit pUnit)
            throws Exception {
        int clCount = pUnit.classes.size();
        Iterator iterator = pUnit.classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String className = (String) iterator.next();

            try {
                if (pUnit.useLocation) {
                    Set<Object> finished = finishedClasses.get(pUnit.location);
                    if ((finished != null) && finished.contains(className)) {
                        continue;
                    }
                    
                    inst.loadClass(pUnit.location +
                        className.replace('.', File.separatorChar) + ".class");
                }
                else {
                    if (finishedClasses.get(DEFAULT_LOC).contains(className)) {
                        continue;
                    }
                    inst.loadClass(className);
                }
            }
            catch (InterfaceClassfileException e) {
                System.out.println("NOTE: " + e.getMessage());
                continue;
            }
            catch (BadFileFormatException e) {
                System.err.println("WARNING: " + e.getMessage());
                continue;
            }
            catch (EmptyFileException e) {
                System.err.println("WARNING: " + e.getMessage());
                continue;
            }
            catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                return;
            }

            BufferedOutputStream fout = null;
            try {
                fout = new BufferedOutputStream(new FileOutputStream(
                        inst.getClassName() + ".class"));
            }
            catch (Exception e) {
                System.err.println("Unable to create output file");
                System.exit(1);
            }

            try {
                instrumentClass(fout);
                
                Set<Object> finished;
                if (pUnit.useLocation) {
                    finished = finishedClasses.get(pUnit.location);
                    if (finished == null) {
                        finished = new THashSet();
                        finishedClasses.put(pUnit.location, finished);
                    }
                }
                else {
                    finished = finishedClasses.get(DEFAULT_LOC);
                }
                finished.add(className);
            }
            finally {
                try {
                    fout.close();
                }
                catch (IOException e) {
                    System.err.println("WARNING: Failed to close file \"" +
                        inst.getClassName() + "\"");
                }
            }
        }
    }

    /**
     * Helper method which instruments all of the class files found
     * in a jar file.
     *
     * @param jarName Name of the jar file to be instrumented.
     */
    private static void instrumentJar(ProgramUnit jarUnit) throws Exception {
        JarFile sourceJar = new JarFile(jarUnit.location);
        ProtectedJarOutputStream instJar = null;
        Set includeClasses = new THashSet(jarUnit.classes);

        File f = new File(jarUnit.location + ".inst");
        try {
            instJar = new ProtectedJarOutputStream(new BufferedOutputStream(
                          new FileOutputStream(f)));
        }
        catch (IOException e) {
            IOException ioe = new IOException("Could not create output jar " +
                "file");
            ioe.fillInStackTrace();
            throw ioe;
        }

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                boolean copyOnly = false;

                ZipEntry ze = (ZipEntry) e.nextElement();
                String entryName = ze.getName();
                if (ze.isDirectory() || !entryName.endsWith(".class")) {
                    copyOnly = true;
                }
                else {
                    entryName = entryName.substring(0,
                        entryName.lastIndexOf(".class")).replace('/', '.');
                    if (!includeClasses.contains(entryName)) {
                        copyOnly = true;
                    }
                    else {
                        Set<Object> finished =
                            finishedClasses.get(jarUnit.location);
                        if ((finished != null)
                                && finished.contains(entryName)) {
                            copyOnly = true;
                        }
                    }
                }

                if (!copyOnly) {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    try {
                        inst.loadClass(ze.getName(), entryStream);
                    }
                    catch (BadFileFormatException exc) {
                        System.err.println(exc.getMessage());
                        copyOnly = true;
                    }
                }

                instJar.putNextEntry(new JarEntry(ze.getName()));
                if (!copyOnly) {
                    instrumentClass(instJar);
                    
                    Set<Object> finished =
                        finishedClasses.get(jarUnit.location);
                    if (finished == null) {
                        finished = new THashSet();
                        finishedClasses.put(jarUnit.location, finished);
                    }
                    finished.add(entryName);
                }
                else {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    Handler.copyStream(entryStream, instJar, false, false);
                }
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
                instJar.closeStream();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }

        if (f.exists()) {
            if (!f.renameTo(new File(jarUnit.location))) {
                System.out.println("Instrumented jar file is named " +
                    f.getName());
            }
        }
    }

    /**
     * Prints the usage message and exits.
     *
     * @param msg Targeted explanation of the usage error,
     * may be <code>null</code>.
     */
    private static void printUsage(String msg) {
        if (msg != null) System.err.println(msg);
        System.err.println("Usage:");
        System.err.println("java sofya.ed.semantic.SemanticInstrumentor " +
            "[-dabe] <module_description_file>");
        System.err.println("    -dabe : Disable automatic boundary events, " +
            "prevents insertion of");
        System.err.println("            instrumentation to report " +
            "when execution leaves the module");
        System.exit(1);
    }

    /**
     * Entry point for the instrumentor.
     */
    public static void main(String[] argv) {
        if (argv.length < 1) {
            printUsage(null);
        }

        boolean autoBoundaryEvents = true;
        boolean forAtomicity = false;
        boolean adaptive = false;

        int index;
        for (index = 0; index < argv.length; index++) {
            if (!argv[index].startsWith("-")) {
                break;
            }
            else if (argv[index].equals("-dabe")) {
                autoBoundaryEvents = false;
            }
            else if (argv[index].equals("-atom")) {
                forAtomicity = true;
            }
            else if (argv[index].equals("-mod")) {
                adaptive = true;
            }
            else {
                printUsage("Unrecognized parameter: " + argv[index]);
            }
        }

        EDLHandler edlHandler = new EDLHandler();
        SemanticEventData dataFile = null;
        String dataFileName;

        if (!forAtomicity) {
            if (index >= argv.length) {
                System.err.println("Must provide EDL file");
                System.exit(1);
            }
            
            dataFileName = argv[index] + ".dat";
            if (adaptive) {
                try {
                    dataFile = edlHandler.readDataFile(dataFileName);
                    edlHandler.mergeEDLFile(argv[index], dataFile);
                }
                catch (IOException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
            }
            else {
                try {
                    dataFile = edlHandler.readEDLFile(argv[index]);
                }
                catch (FileNotFoundException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                catch (IOException e) {
                    //e.printStackTrace();
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
            }
        }
        else {
            if (index >= argv.length) {
                System.err.println("Must provide progam file");
                System.exit(1);
            }
            if (!argv[index].endsWith(".prog")) {
                System.err.println("Instrumentation for atomicity checking " +
                    "requires a program file");
                System.exit(1);
            }

            List<ProgramUnit> classes = new ArrayList<ProgramUnit>();
            try {
                Handler.readProgFile(argv[index], null, classes);
            }
            catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }

            autoBoundaryEvents = false;

            dataFileName = argv[index].substring(0,
                argv[index].lastIndexOf(".")) + ".dat";
            dataFile = new SemanticEventData("atomicity");
            dataFile.addEventSpecification(
                new AtomicityEvents(classes, classes, true), false);
        }
        
        ProbeTracker logger = new ProbeTracker(adaptive, false, dataFile);
        try {
            logger.initializeLogHandler();
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        try {
            if (adaptive) {
                inst = new SemanticInstrumentor(dataFile, logger);
            }
            else {
                inst = new SemanticInstrumentor(dataFile);
            }
            inst.autoBoundaryEvents = autoBoundaryEvents;

            InstrumentorObserver instObserver = logger;
            inst.setInstrumentorObserver(instObserver);

            Collection<EventSpecification> specs =
                dataFile.getEventSpecifications();
            int specCount = specs.size();
            Iterator<EventSpecification> specIter = specs.iterator();
            
            instObserver.begin();
            
            for (int i = specCount; i-- > 0; ) {
                EventSpecification curSpec = specIter.next();
                
                Set<ProgramUnit> classes = curSpec.getSystemClassUnits(null);
                Iterator<ProgramUnit> iterator = classes.iterator();
                for (int j = classes.size(); j-- > 0; ) {
                    ProgramUnit pUnit = iterator.next();
                    if (pUnit.isJar) {
                        instrumentJar(pUnit);
                    }
                    else {
                        instrumentClassFiles(pUnit);
                    }
                }
            }

            instObserver.end();

            edlHandler.writeDataFile(dataFileName, dataFile);
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            if (!e.getMessage().startsWith("Port")) {
                System.err.println("Error specifying port");
                System.exit(1);
            }
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error reading from class or jar file");
            System.exit(1);
        }
        catch (ClassFormatError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
