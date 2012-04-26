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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;

import sofya.base.MethodSignature;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.ed.semantic.EventListener.*;
import sofya.ed.semantic.EventSpecification.*;
import sofya.ed.semantic.ProbeLocationTree.ProbeIterator;
import sofya.ed.semantic.SemanticEventDispatcher.InternalException;
import static sofya.ed.semantic.SemanticConstants.*;
import static sofya.ed.semantic.SemanticEventData.warnKeyNotAdaptive;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.generic.Type;

import gnu.trove.THashSet;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;

/**
 * The instrumentation manager provides the interface to the adaptive
 * instrumentation capabilities of a {@link SemanticEventDispatcher}.
 * This interface is only available if the event dispatcher is
 * operating using an {@link AdaptiveEventSpecification}.
 *
 * <p>The methods provided by this interface are dispatched
 * <strong>asynchronously</strong> to avoid the possibility of
 * deadlocking the event processing thread. As a consequence,
 * calls will return immediately, though the calling thread may
 * subsequently block normally if no new events are in the
 * event queue prior to suspension of the target VM to transfer
 * instrumentation updates.</p>
 * 
 * <p>Certain methods offer a parameter to specify whether the update
 * should be synchronously applied. If <code>true</code> is
 * specified for this parameter, it is guaranteed that between the
 * time the relevant update method is called and the instrumentation
 * update occurs in the target VM, no new events can reach the event
 * queue to be dispatched to listeners. However, there may still
 * be previous events pending in the queue that will be dispatched
 * to listeners after the update request is received. <em>This
 * aspect of synchronous/asynchronous handling should not be confused
 * with the issue of dispatch of the method calls themselves, which
 * are always asynchronous! It is only relevant to the question of
 * when updates are guaranteed to occur with respect to the visibility
 * of other observable events occurring in the target VM.!</em></p>
 *
 * <p><strong>NOTE:</strong> Support for EDL is limited. In particular,
 * do not use wildcards with any method on this API, as support has
 * not yet been implemented (this will be addressed in future).</p>
 *
 * @author Alex Kinneer
 * @version beta
 */
@SuppressWarnings("unchecked")
public final class InstrumentationManager {
    
    private final SemanticEventDispatcher dispatcher;
    private final JDIEventManager jdiEventManager;
    private final VirtualMachine vm;
    
    private final ExecutorPool execPool;

    private final Map<Object, Object> eventSpecs = new THashMap();
    private SemanticInstrumentor instrumentor;
    private ProbeLogHandler logHandler;
    private TIntObjectHashMap probeTable;
    private final Map<Object, Object> classCache = new THashMap();

    private final Map<ReferenceType, byte[]> classRedefMap = new THashMap();
    private final Map<Object, Set<Object>> dirtyClasses = new THashMap();
    final TIntHashSet pendingRemoveIds = new TIntHashSet();

    private final BufferedInputStream targetIn;
    private final DataOutputStream targetOut;

    private final String defaultClassLoader;
    
    @SuppressWarnings("unchecked")
    private final Set<Object> detachedKeys = new THashSet();
    private volatile boolean isDetached = false;
    
    /** Name of the thread created inside the target VM to handle requests
        to retrieve class bytecodes. Mostly randomly generated to minimize
        likelihood of name collisions with &quot;real&quot; threads. */
    public static final String THREAD_NAME =
        "__Probe_Manager#9b96308ad0c64b6e8ade31ee08b2c03a$_";

    @SuppressWarnings("unused")
    private static final boolean REDEFINE_CLUSTERED = true;
    @SuppressWarnings("unused")
	private static final boolean ASSERTS = true;
    @SuppressWarnings("unused")
    private static final boolean DEBUG = false;
    @SuppressWarnings("unused")
    private static final boolean DUMP_UPDATED_CLASS = false;
    private static int mod_num = 0;

    /* Prototype work for handling adaptive instrumentation on call
       (and similar) events. Won't delete just yet...
    static final class ClassReference {
        final String className;
        boolean isDirty = true;
        ClassTrackingData dirtyRef;
        SoftReference cleanRef;

        private ClassReference() {
            throw new AssertionError("Illegal constructor");
        }

        ClassReference(String className) {
            this.className = className;
        }
    }

    static final class ClassTrackingData {
        // event code -> requesting key sets
        final TIntObjectHashMap reqKeySets = new TIntObjectHashMap();

        ClassTrackingData() {
        }
    }*/

    private static final class ExecutorPool {
        final ConcurrentLinkedQueue<Thread> threadPool =
            new ConcurrentLinkedQueue<Thread>();
        
        InstrumentationManager instManager;
        SemanticEventDispatcher dispatcher;
        volatile ErrorHandler errorHandler = new HaltingErrorHandler();
        
        /** Setting this flag will cause the asynchronous update thread
            to capture the call stack of the thread that initiated the
            request. This allows an error handler to provide a much more
            useful stack trace that correlates the exception to the actual
            point in the code that initiated the update request. Capturing
            this information is very costly, however, so this flag is
            primarily intended for development debugging.
          */
        private static final boolean CAPTURE_STACK = false;
        /** Maps each active update thread to the stack trace of the
            external thread that initiated the request being satisfied
            by the update thread.
          */
        final Map<Object, Object> stackTraces;
        
        ExecutorPool() {
            throw new AssertionError("Illegal constructor");
        }
        
        ExecutorPool(InstrumentationManager instManager,
                SemanticEventDispatcher dispatcher) {
            this.instManager = instManager;
            this.dispatcher = dispatcher;
            stackTraces = (CAPTURE_STACK) ? new THashMap() : null;
        }
        
        final UpdateExecutor requestExecutor() {
            UpdateExecutor sender =
                (UpdateExecutor) threadPool.poll();
            if (sender == null) {
                sender = new UpdateExecutor(this);
                sender.setDaemon(true);
                sender.start();
            }
            return sender;
        }
    }
    
    /**
     * An update executor is a specialized thread class used to execute
     * the asynchronous instrumentation update requests.
     */
    static final class UpdateExecutor extends Thread {
        private final ExecutorPool pool;
        private volatile boolean synchronous = false;
        
        UpdateExecutor(ExecutorPool pool) {
            this.pool = pool;
        }

        void release() {
            synchronous = false;
            if (ExecutorPool.CAPTURE_STACK) {
                pool.stackTraces.remove(this);
            }
            pool.threadPool.add(this);
        }

        void execute() {
            if (ExecutorPool.CAPTURE_STACK) {
                pool.stackTraces.put(this,
                    Thread.currentThread().getStackTrace());
            }
            
            this.interrupt();
        }

        public void run() {
            while (true) {
                if (!interrupted()) {
                    try {
                        synchronized(this) {
                            wait();
                        }
                    }
                    catch (InterruptedException e) {
                    }
                }
                
                boolean notifyResume = true;
                try {
                    if (DEBUG) {
                        System.out.println("Notifying event dispatcher of " +
                            "impending class redefinition");
                        System.out.flush();
                    }
                    pool.dispatcher.startClassRedefinition(synchronous);
                    if (DEBUG) {
                        System.out.println("Notified");
                        System.out.flush();
                    }

                    pool.instManager.redefineClasses();
                }
                catch (Throwable err) {
                    pool.errorHandler.handleError(err, this, pool.instManager);
                    notifyResume = pool.errorHandler.adviseResume();
                }
                finally {
                    pool.instManager.pendingRemoveIds.clear();
                    
                    if (notifyResume) {
                        if (DEBUG) {
                            System.out.println("Notifying event dispatcher " +
                                "that redefinition is complete");
                            System.out.flush();
                        }
                        pool.dispatcher.endClassRedefinition(synchronous);
                        if (DEBUG) {
                            System.out.println("Notified");
                            System.out.flush();
                        }
                    }

                    release();
                }
            }
        }
    }

    private InstrumentationManager() {
        throw new AssertionError("Illegal constructor");
    }

    InstrumentationManager(SemanticEventDispatcher dispatcher,
            BufferedInputStream targetIn, BufferedOutputStream targetOut,
            JDIEventManager jdiEventManager, VirtualMachine vm,
            String defaultClassLoader) {
        this.dispatcher = dispatcher;
        this.targetIn = targetIn;
        this.targetOut = new DataOutputStream(targetOut);
        this.jdiEventManager = jdiEventManager;
        this.vm = vm;
        this.execPool = new ExecutorPool(this, dispatcher);
        this.defaultClassLoader = defaultClassLoader;

        //UpdateExecutor.initialize(this, dispatcher);
    }
    
    void initialize() throws InternalException {
        SemanticEventData eventData = dispatcher.getEDData();
        
        probeTable = eventData.getProbeTable();
        ProbeTracker logger = new ProbeTracker(true, true, eventData);
        logHandler = logger;
        
        instrumentor = new SemanticInstrumentor(eventData, logHandler);
        instrumentor.setInstrumentorObserver(logger);
        instrumentor.autoBoundaryEvents = false;
        
        EventSpecification[] allSpecs =
            eventData.getEventSpecificationsArray(true);
        int len = allSpecs.length;
        for (int i = 0; i < len; i++) {
            if (allSpecs[i] instanceof AdaptiveEventSpecification) {
                this.eventSpecs.put(allSpecs[i].getKey(),
                        allSpecs[i]);
            }
        }
        
        try {
            logHandler.initializeLogHandler();
        }
        catch (IOException e) {
            throw new InternalException("Failed to initialize " +
                "log handler", e);
        }
    }

    final void redefineClasses() throws MethodNotFoundException, IOException,
                                        InternalException {
        if (DEBUG) {
            System.out.println("InstrumentationManager:redefineClasses");
            System.out.println("InstrumentationManager:currentThread=" +
                Thread.currentThread());
        }
        
        // TODO: Classloader support
        String classLoader = defaultClassLoader;

        int modClassCount = dirtyClasses.size();
        Iterator clIter = dirtyClasses.keySet().iterator();
        for (int i = modClassCount; i-- > 0; ) {
            String className = (String) clIter.next();
            
            if (DEBUG) {
                System.out.println("InstrumentationManager:className=" +
                    className);
            }

            ReferenceType clType =
                    resolveReferenceType(classLoader, className);
            if (clType == null) {
                System.err.println("No matching class loaded by the " +
                    "specified class loader (" + className + ")");
                continue;
            }

            String cacheKey;
            if (classLoader == null) {
                cacheKey = defaultClassLoader + "#" + className;
            }
            else {
                cacheKey = classLoader + "#" + className;
            }

            JavaClass jClass = (JavaClass) classCache.get(cacheKey);
            if (jClass == null) {
                if (classLoader != null) {
                    dispatcher.markClassLoader(clType.classLoader());
                    if (DEBUG) {
                        System.out.println("Classloader specified");
                        System.out.flush();
                    }
                }

                targetOut.writeByte((byte) 2);
                targetOut.writeUTF(className);
                targetOut.writeBoolean(classLoader != null);
                targetOut.flush();

                ClassParser cp = new ClassParser(targetIn, className);
                jClass = cp.parse();
                classCache.put(cacheKey, jClass);
                if (DEBUG) {
                    System.out.println("Class loaded");
                    System.out.flush();
                }
            }
            else if (DEBUG) {
                System.out.println("Class loaded from cache");
                System.out.flush();
            }

            Set dirtyMethods = (Set) dirtyClasses.get(className);

            // Locked to protect state of probe location tree in the face
            // of concurrent modification caused by the asynchronous dispatch
            // of the instrumentation update requests. See comment in
            // enableMethodEvent() below for additional details.
            JavaClass modClass = null;
            synchronized(this) {
                modClass = reinstrumentClass(jClass, dirtyMethods);
            }

            if (modClass != null) {
                if (DUMP_UPDATED_CLASS) {
                    try {
                        modClass.dump(className + ".class.mod." + mod_num);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Failed to save modified class");
                    }
                    mod_num++;
                }

                classRedefMap.put(clType, modClass.getBytes());
                if (!REDEFINE_CLUSTERED) {
                    vmRedefineClasses();
                    dirtyClasses.remove(className);
                    if (DEBUG) {
                        System.out.println("Class redefined");
                        System.out.flush();
                    }
                }
                
                classCache.put(cacheKey, modClass);
            }
        }
        
        if (REDEFINE_CLUSTERED) {
            vmRedefineClasses();
            dirtyClasses.clear();
        }

        // Enable to test error handler mechanism...
        // throw new RuntimeException("Test exception");
    }

    final JavaClass reinstrumentClass(JavaClass jClass, Set dirtyMethods)
            throws MethodNotFoundException, IOException {
        instrumentor.loadClass(jClass);

        int methodCount = dirtyMethods.size();
        Iterator iterator = dirtyMethods.iterator();
        for (int i = methodCount; i-- > 0; ) {
            MethodSignature method = (MethodSignature) iterator.next();
            //System.out.println("InstrumentationManager.java:338:dirtyMethod=" + method);
            //gnu.trove.TIntIterator iter = pendingRemoveIds.iterator();
            //while (iter.hasNext()) System.out.println("  -> " + iter.next());

            String methodName = method.getMethodName();
            Type returnType = method.getReturnType();
            Type[] argTypes = method.getArgumentTypes();

            try {
                instrumentor.reinstrument(methodName, returnType, argTypes,
                    false, pendingRemoveIds);
            }
            catch (MethodNotFoundException e) {
                MethodNotFoundException mnfe = new MethodNotFoundException(
                    "Class does not declare method:\n\t" +
                    methodName + Type.getMethodSignature(returnType,
                    argTypes));
                mnfe.setStackTrace(e.getStackTrace());
                throw mnfe;
            }
        }

        return instrumentor.generateClass();
    }
    
    /**
     * Utility method to resolve the name of a class loaded by a given
     * class loader to a JDI reference type.
     * 
     * @param clLoader Name of the class loader responsible for loading
     * the class to be resolved.
     * @param clName Name of the class to be resolved.
     * 
     * @return The JDI reference type for the specified class, or
     * <code>null</code> if the specified class loader has not
     * loaded the requested class.
     */
    final ReferenceType resolveReferenceType(String clLoader,
            String clName) {
        List classes = vm.classesByName(clName);
        int clCount = classes.size();
        Iterator clIter = classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            ReferenceType rType = (ReferenceType) clIter.next();
            ClassLoaderReference curLoaderRef = rType.classLoader();
            String typeLoader = curLoaderRef.referenceType().name();
            if (typeLoader.equals(clLoader)) {
                return rType;
            }
        }

        return null;
    }
    
    /**
     * Requests redefinition of a class.
     *
     * @param clLoader Class loader used to load the class to be redefined.
     * @param clName Name of the class to be redefined.
     * @param clBytes New class bytes to be used to redefine the class.
     *
     * @return <code>true</code> if the class was successfully redefined,
     * <code>false</code> otherwise.
     */
    final void vmRedefineClasses() {
        vm.redefineClasses(classRedefMap);
        
        int size = classRedefMap.size();
        Iterator<ReferenceType> iterator = classRedefMap.keySet().iterator();
        for (int i = size; i-- > 0; ) {
            ReferenceType clType = iterator.next();
            jdiEventManager.requestBreakpoints(clType);
        }
        
        classRedefMap.clear();
    }
    
    /**
     * Sets the error handler to be invoked if an exception occurs in
     * a thread executing the update request.
     * 
     * <p>An asynchronous dispatch model is used to execute update
     * requests (even those that are synchronous with respect to the
     * progress of the target program), so this mechanism is necessary
     * to provide for proper exception handling associated with
     * the update request.</p>
     * 
     * <p>The default error handler is the {@link ResumingErrorHandler}.</p>
     * 
     * @param errorHandler Error handler to be invoked if an exception
     * occurs in a thread executing an update request.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        //UpdateExecutor.errorHandler = errorHandler;
        execPool.errorHandler = errorHandler;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Public instrumentation management methods
    //
    ///////////////////////////////////////////////////////////////////////////
  
    /**
     * Issues a request to immediately execute any pending instrumentation
     * updates.
     * 
     * <p>This method is dispatched asynchronously and returns immediately.
     * As a consequence, it does not return any value or throw any exception
     * to the caller. However, if there is an error processing the request,
     * at some point in the future the error handler for this instrumentation
     * manager will be invoked as a result of this call.</p>
     */
    public void updateInstrumentation() {
        dispatcher.suspendTarget();
        UpdateExecutor updater = execPool.requestExecutor();
        updater.synchronous = true;
        updater.execute();
    }
    
    /**
     * Issues a request to execute any pending instrumentation updates.
     * 
     * <p>This method is dispatched asynchronously and returns immediately.
     * As a consequence, it does not return any value or throw any exception
     * to the caller. However, if there is an error processing the request,
     * at some point in the future the error handler for this instrumentation
     * manager will be invoked as a result of this call.</p>
     * 
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API. A call
     * to this method with synchronous equal to <code>true</code> is
     * equivalent to calling {@link #updateInstrumentation()}.
     */
    public void updateInstrumentation(boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }
        UpdateExecutor updater = execPool.requestExecutor();
        updater.synchronous = synchronous;
        updater.execute();
    }

    ///////////////////////////////////////////////////////////////////////////
    // JDI event control methods

    /**
     * Completely detaches the event dispatcher from the monitored program.
     *
     * <p>Detaching the event dispatcher allows the monitored program to
     * proceed normally, which greatly improves performance.</p>
     *
     * <p><strong>Use this method only if you are certain that you are no
     * longer interested in monitoring the program in any way!</strong> It
     * will not be possible to observe any further events after this method
     * is called, with the exception of the following:</p>
     * <ul>
     * <li><code>threadDeath</code> events for any threads that are known
     * at the time this method is called. The information available from
     * the <code>ThreadData</code> objects delivered by these events
     * is highly constrained. Note also that these events will be
     * delivered at the termination of the program, and may not reflect
     * the actual ordering of thread termination.</li>
     * <li>The <code>systemExited</code> event for the program.</li>
     * </ul>
     *
     * <p>At a future point in time, support may be added to re-attach
     * to the monitored program.</p>
     */
    public void detach(String key) {
        if (isDetached) {
            return;
        }
        
        detachedKeys.add(key);
        if (detachedKeys.size() == eventSpecs.size()) {
            dispatcher.detach();
            isDetached = true;
            
            // Free memory
            eventSpecs.clear();
            classCache.clear();
            pendingRemoveIds.clear();
            instrumentor = null;
            logHandler = null;
            probeTable = null;
            detachedKeys.clear();
        }
    }

    /**
     * Enables observation of throw ev
     * ents for a given exception type.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param type Type of exception for which to observe <code>throw</code>
     * events.
     */
    public void enableExceptionEvent(String key, ReferenceType type) {
        jdiEventManager.enableExceptionEvent(key, type);
    }

    /**
     * Disables observation of throw events for a given exception type.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param type Type of exception for which to disable observation of
     * <code>throw</code> events.
     */
    public boolean disableExceptionEvent(String key, ReferenceType type) {
        return jdiEventManager.disableExceptionEvent(key, type);
    }

    /**
     * Enables observation of read events on a field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param field Reference to the field as provided by an
     * {@link EventListener} field related event.
     */
    public void enableFieldAccessEvent(String key, FieldData field) {
        jdiEventManager.enableFieldAccessEvent(key, field.field());
    }

    /**
     * Enables observation of read events on a static field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field on which
     * to observe read events.
     */
    public boolean enableStaticFieldAccessEvent(String key, String fieldName) {
        return jdiEventManager.enableFieldAccessEvent(key, fieldName, true);
    }

    /**
     * Enables observation of read events on an instance field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field on which
     * to observe read events.
     */
    public boolean enableInstanceFieldAccessEvent(String key,
            String fieldName) {
        return jdiEventManager.enableFieldAccessEvent(key, fieldName, false);
    }

    /**
     * Disables observation of read events on a field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param field Reference to the field as provided by an
     * {@link EventListener} field related event.
     * 
     * @return <code>true</code> if an active request for read events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for read events on the specified field were found.
     */
    public boolean disableFieldAccessEvent(String key, FieldData field) {
        return jdiEventManager.disableFieldAccessEvent(key, field.field());
    }

    /**
     * Disables observation of read events on a static field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field for which
     * to disable read events.
     * 
     * @return <code>true</code> if an active request for read events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for read events on the specified field were found.
     */
    public boolean disableStaticFieldAccessEvent(String key,
            String fieldName) {
        return jdiEventManager.disableFieldAccessEvent(key, fieldName, true);
    }

    /**
     * Disables observation of read events on an instance field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field for which
     * to disable read events.
     * 
     * @return <code>true</code> if an active request for read events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for read events on the specified field were found.
     */
    public boolean disableInstanceFieldAccessEvent(String key,
            String fieldName) {
        return jdiEventManager.disableFieldAccessEvent(key, fieldName, false);
    }

    /**
     * Enables observation of write (store) events on a field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param field Reference to the field as provided by an
     * {@link EventListener} field related event.
     */
    public void enableFieldWriteEvent(String key, FieldData field) {
        jdiEventManager.enableFieldWriteEvent(key, field.field());
    }

    /**
     * Enables observation of write (store) events on a static field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field on which
     * to observe write events.
     */
    public boolean enableStaticFieldWriteEvent(String key, String fieldName) {
        return jdiEventManager.enableFieldWriteEvent(key, fieldName, true);
    }

    /**
     * Enables observation of write (store) events on an instance field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field on which
     * to observe write events.
     */
    public boolean enableInstanceFieldWriteEvent(String key,
            String fieldName) {
        return jdiEventManager.enableFieldWriteEvent(key, fieldName, false);
    }

    /**
     * Disables observation of write (store) events on a field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param field Reference to the field as provided by an
     * {@link EventListener} field related event.
     * 
     * @return <code>true</code> if an active request for write events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for write events on the specified field were found.
     */
    public boolean disableFieldWriteEvent(String key, FieldData field) {
        return jdiEventManager.disableFieldWriteEvent(key, field.field());
    }

    /**
     * Disables observation of write (store) events on a static field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field for which
     * to disable write events.
     * 
     * @return <code>true</code> if an active request for write events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for write events on the specified field were found.
     */
    public boolean disableStaticFieldWriteEvent(String key, String fieldName) {
        return jdiEventManager.disableFieldWriteEvent(key, fieldName, true);
    }

    /**
     * Disables observation of write (store) events on an instance field.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param fieldName Fully qualified name of the field for which
     * to disable write events.
     * 
     * @return <code>true</code> if an active request for write events
     * on the specified field was disabled, <code>false</code> if no
     * active requests for write events on the specified field were found.
     */
    public boolean disableInstanceFieldWriteEvent(String key,
            String fieldName) {
        return jdiEventManager.disableFieldWriteEvent(key, fieldName, false);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Bytecode manipulation methods

    @SuppressWarnings("unchecked")
    private final boolean enableMethodEvent(byte eventCode, String key,
            MethodSignature method) {
        // Note that if the probes list is null (never initialized), there
        // are no probes for this event currently associated with the
        // given key. Simply enabling the event in the event specification
        // associated with the key will cause the probe record to be generated
        // when the instrumentation is inserted by the instrumentor. If the
        // event is requested to be disabled before the instrumention is ever
        // inserted, it will be accomplished by simply disabling the event in
        // the event specification.
        ProbeLocationTree locTree =
            (ProbeLocationTree) probeTable.get(eventCode);
        if (locTree == null) {
            locTree = new ProbeLocationTree();
            probeTable.put(eventCode, locTree);
        }
        else {
            // Access to the probe location tree is guarded by a mutual
            // exclusion lock. The same lock is also acquired by update
            // threads before invoking the instrumentor on a class. This
            // is because the logging of modifications done by the
            // instrumentor may cause changes to the tree. In particular,
            // the mutual exclusion avoids the case where the instrumentor
            // removes a probe, but has not yet informed the logger,
            // and thus the removal of the probe has not yet occurred in
            // the location tree. If this method then queries the tree and
            // finds a probe record, it will simply modify the live keys
            // for the probe, and will not mark the class as dirty (needing
            // reinstrumentation to add the probe back), which will
            // cause the request to be lost.
            synchronized(this) {
                ProbeIterator pIter = locTree.iterator();
                while (pIter.hasNext()) {
                    ProbeRecord probe = (ProbeRecord) pIter.next();

                    if (!probe.location.equals(method)) {
                        continue;
                    }
                    else if (probe.liveKeys.size() == 0) {
                        pendingRemoveIds.remove(probe.id);
                    }
                    probe.liveKeys.add(key);

                    // There should be only one match
                    return false;
                }
            }
        }

        // If no existing probes were found, the class needs to be
        // marked for re-instrumentation to insert it
        String className = method.getClassName();
        Set<Object> dirtyMethods = dirtyClasses.get(className);
        if (dirtyMethods == null) {
            dirtyMethods = new THashSet();
            dirtyClasses.put(className, dirtyMethods);
        }
        dirtyMethods.add(method);

        return true;
    }

    @SuppressWarnings("unchecked")
    private final boolean disableMethodEvent(byte eventCode, String key,
            MethodSignature method) {
        ProbeLocationTree locTree =
            (ProbeLocationTree) probeTable.get(eventCode);
        if (locTree == null) {
            locTree = new ProbeLocationTree();
            probeTable.put(eventCode, locTree);
            return false;
        }

        // Locked to protect state of probe location tree in the face
        // of concurrent modification caused by the asynchronous dispatch
        // of the instrumentation update requests. See comment in
        // enableMethodEvent() for additional details.
        synchronized(this) {
            ProbeIterator pIter = locTree.iterator();
            while (pIter.hasNext()) {
                ProbeRecord probe = (ProbeRecord) pIter.next();
                //System.out.println(probe);
                if (!probe.location.equals(method)) {
                    continue;
                }
                else {
                    probe.liveKeys.remove(key);
                    if (probe.liveKeys.size() == 0) {
                        String className = method.getClassName();
                        Set<Object> dirtyMethods = dirtyClasses.get(className);
                        if (dirtyMethods == null) {
                            dirtyMethods = new THashSet();
                            dirtyClasses.put(className, dirtyMethods);
                        }
                        //System.out.println(probe.id);
                        dirtyMethods.add(method);
                        pendingRemoveIds.add(probe.id);

                        return true;
                    }
                }

                // There should be only one match
                return false;
            }
        }

        // If there are no matching probes, this is just a no-op
        return false;
    }

    /**
     * Enables observation of entry events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param argTypes Formal parameter types to the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableConstructorEntryEvent(String key, String className,
            Type[] argTypes, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            Type.VOID, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addConstructorEntry(className, argTypes);
        
        boolean requiresInst =
            enableMethodEvent(EVENT_CONSTRUCTOR_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of entry events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param jniSignature The JNI-style signature of the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableConstructorEntryEvent(String key, String className,
            String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addConstructorEntry(className, method.getArgumentTypes());
        
        boolean requiresInst =
            enableMethodEvent(EVENT_CONSTRUCTOR_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of entry events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param argTypes Formal parameter types to the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableConstructorEntryEvent(String key, String className,
            Type[] argTypes, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            Type.VOID, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeConstructorEntry(className, argTypes);
        
        boolean requiresInst =
            disableMethodEvent(EVENT_CONSTRUCTOR_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of entry events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param jniSignature The JNI-style signature of the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableConstructorEntryEvent(String key, String className,
            String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeConstructorEntry(className, method.getArgumentTypes());
        
        boolean requiresInst =
            disableMethodEvent(EVENT_CONSTRUCTOR_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of exit events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param argTypes Formal parameter types to the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableConstructorExitEvent(String key, String className,
            Type[] argTypes, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            Type.VOID, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addConstructorExit(className, argTypes);
        
        boolean requiresInst =
            enableMethodEvent(EVENT_CONSTRUCTOR_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of exit events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param jniSignature The JNI-style signature of the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableConstructorExitEvent(String key, String className,
            String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addConstructorExit(className, method.getArgumentTypes());
        
        boolean requiresInst =
            enableMethodEvent(EVENT_CONSTRUCTOR_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of exit events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param argTypes Formal parameter types to the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableConstructorExitEvent(String key, String className,
            Type[] argTypes, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            Type.VOID, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeConstructorExit(className, argTypes);
        
        boolean requiresInst =
            disableMethodEvent(EVENT_CONSTRUCTOR_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of entry events for a constructor.
     * 
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class definining the constructor.
     * @param jniSignature The JNI-style signature of the constructor.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableConstructorExitEvent(String key, String className,
            String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, "<init>",
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeConstructorExit(className, method.getArgumentTypes());
        
        boolean requiresInst =
            disableMethodEvent(EVENT_CONSTRUCTOR_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of the virtual method entry event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param returnType Return type of the method.
     * @param argTypes Formal parameter types to the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableVirtualMethodEntryEvent(String key, String className,
            String methodName, Type returnType, Type[] argTypes,
            boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className,
            methodName, returnType, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addMethodEvent(className, methodName, argTypes,
            MethodAction.VIRTUAL_ENTER);
        
        boolean requiresInst =
            enableMethodEvent(EVENT_VMETHOD_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of the virtual method entry event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param jniSignature The JNI-style signature of the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableVirtualMethodEntryEvent(String key, String className,
            String methodName, String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className,
            methodName, jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addMethodEvent(className, methodName,
            method.getArgumentTypes(), MethodAction.VIRTUAL_ENTER);
        
        boolean requiresInst =
            enableMethodEvent(EVENT_VMETHOD_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of the virtual method entry event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param returnType Return type of the method.
     * @param argTypes Formal parameter types to the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableVirtualMethodEntryEvent(String key, String className,
            String methodName, Type returnType, Type[] argTypes,
            boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            returnType, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeMethodEvent(className, methodName, argTypes,
            MethodAction.VIRTUAL_ENTER);
        
        boolean requiresInst =
            disableMethodEvent(EVENT_VMETHOD_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of the virtual method entry event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param jniSignature The JNI-style signature of the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableVirtualMethodEntryEvent(String key, String className,
            String methodName, String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeMethodEvent(className, methodName,
            method.getArgumentTypes(), MethodAction.VIRTUAL_ENTER);
        
        boolean requiresInst =
            disableMethodEvent(EVENT_VMETHOD_ENTER, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of the virtual method exit event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param returnType Return type of the method.
     * @param argTypes Formal parameter types to the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableVirtualMethodExitEvent(String key, String className,
            String methodName, Type returnType, Type[] argTypes,
            boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            returnType, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addMethodEvent(className, methodName, argTypes,
            MethodAction.VIRTUAL_EXIT);
        
        boolean requiresInst =
            enableMethodEvent(EVENT_VMETHOD_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Enables observation of the virtual method exit event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param jniSignature The JNI-style signature of the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void enableVirtualMethodExitEvent(String key, String className,
            String methodName, String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.addMethodEvent(className, methodName,
            method.getArgumentTypes(), MethodAction.VIRTUAL_EXIT);

        boolean requiresInst =
            enableMethodEvent(EVENT_VMETHOD_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of the virtual method entry exit for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param returnType Return type of the method.
     * @param argTypes Formal parameter types to the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableVirtualMethodExitEvent(String key, String className,
            String methodName, Type returnType, Type[] argTypes,
            boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            returnType, argTypes);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeMethodEvent(className, methodName, argTypes,
            MethodAction.VIRTUAL_EXIT);

        boolean requiresInst =
            disableMethodEvent(EVENT_VMETHOD_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }

    /**
     * Disables observation of the virtual method exit event for a method.
     *
     * @param key Identifying key associated with the listener making
     * the request.
     * @param className Name of the class declaring the method.
     * @param methodName Name of the method.
     * @param jniSignature The JNI-style signature of the method.
     * @param synchronous Specifies whether the update should occur
     * immediately, as described in the overview for this API.
     */
    public void disableVirtualMethodExitEvent(String key, String className,
            String methodName, String jniSignature, boolean synchronous) {
        if (synchronous) {
            dispatcher.suspendTarget();
        }

        MethodSignature method = new MethodSignature(className, methodName,
            jniSignature);

        AdaptiveEventSpecification eventSpec =
            (AdaptiveEventSpecification) eventSpecs.get(key);
        if (eventSpec == null) {
            warnKeyNotAdaptive(System.err, key);
            return;
        }
        eventSpec.removeMethodEvent(className, methodName,
            method.getArgumentTypes(), MethodAction.VIRTUAL_EXIT);

        boolean requiresInst =
            disableMethodEvent(EVENT_VMETHOD_EXIT, key, method);

        if (synchronous && requiresInst) {
            UpdateExecutor updater = execPool.requestExecutor();
            updater.synchronous = true;
            updater.execute();
        }
    }
    
    /**
     * An error handler is invoked if an exception is raised while
     * trying to execute an instrumentation update request.
     * 
     * <p>An asynchronous dispatch model is used to execute update
     * requests (even those that are synchronous with respect to the
     * progress of the target program), so a callback to an error
     * handler is necessary to support proper exception handling.</p>
     * 
     * <p>By default, exception information is logged to
     * <code>System.err</code>.</p>
     */
    public static abstract class ErrorHandler {
        /** Target stream to which exception information will be logged. */
        protected PrintStream logTarget;
        
        ErrorHandler() {
            this.logTarget = System.err;
        }
        
        ErrorHandler(PrintStream logTarget) {
            setLogStream(logTarget);
        }
        
        /**
         * Sets the stream to which exception information will be logged.
         * 
         * @param logTarget Stream to which exception information should
         * be logged.
         */
        public void setLogStream(PrintStream logTarget) {
            this.logTarget = logTarget;
        }
        
        /**
         * Gets the stream to which exception information is being logged.
         * 
         * @return The stream to which exception information is currently
         * being logged.
         */
        public PrintStream getLogStream() {
            return logTarget;
        }
        
        /**
         * Utility method to record the affected thread's information
         * and stack trace to the log stream.
         * 
         * @param err Exception that caused the error handler to be invoked.
         * @param t Call dispatch thread in which the exception occurred.
         * @param instManager Instrumentation manager controlling the
         * thread in which the exception occurred.
         */
        protected void logError(Throwable err, Thread t,
                InstrumentationManager instManager) {
            logTarget.println("*** InstrumentationManager: " + t + ":");
            err.printStackTrace(logTarget);
            
            if (instManager.execPool.stackTraces != null) {
                System.out.println("\t=== From invocation ===");
                StackTraceElement[] stackTrace = (StackTraceElement[])
                    instManager.execPool.stackTraces.get(t);
                int size = stackTrace.length;
                for (int i = 0; i < size; i++) {
                    if (!stackTrace[i].getClassName()
                            .equals("java.lang.Thread")) {
                        System.out.println("\tat " + stackTrace[i]);
                    }
                }
            }
        }
        
        /**
         * Implements the behavior for handling the error.
         * 
         * @param err Exception that caused the error handler to be
         * invoked.
         * @param t Call dispatch thread in which the exception occurred.
         * @param instManager Instrumentation manager controlling the
         * thread in which the exception occurred.
         */
        protected abstract void handleError(Throwable err, Thread t,
                InstrumentationManager instManager);
        
        /**
         * Advises the dispatch thread whether it should attempt to
         * resume the event dispatcher.
         * 
         * @return <code>true</code> if the call dispatch thread should
         * issue a signal to the event dispatcher to attempt to resume,
         * <code>false</code> otherwise.
         */
        protected abstract boolean adviseResume();
    }
    
    /**
     * A resuming error handler simply logs the exception and attempts to
     * continue observing the execution of the target program.
     */
    public static final class ResumingErrorHandler extends ErrorHandler {
        public ResumingErrorHandler() {
            super();
        }
        
        public ResumingErrorHandler(PrintStream logTarget) {
            super(logTarget);
        }
        
        protected void handleError(Throwable err, Thread t,
                InstrumentationManager instManager) {
            logError(err, t, instManager);
        }
        
        protected boolean adviseResume() { return true; }
    }
    
    /**
     * A detaching error handler logs the exception and then detaches the
     * event dispatcher from the target program.
     * 
     * <p>This allows the target program to continue executing normally,
     * if possible, and the event dispatcher will not report exit until
     * the target program exits. However, no further events will be
     * dispatched, except as described for
     * {@link InstrumentationManager#detach}.</p>
     */
    public static final class DetachingErrorHandler extends ErrorHandler {
        public DetachingErrorHandler() {
            super();
        }
        
        public DetachingErrorHandler(PrintStream logTarget) {
            super(logTarget);
        }
        
        protected void handleError(Throwable err, Thread t,
                InstrumentationManager instManager) {
            logError(err, t, instManager);
            instManager.dispatcher.detach();
            logTarget.println("*** InstrumentationManager: detaching...");
        }
        
        protected boolean adviseResume() { return false; }
    }
    
    /**
     * A halting error handler logs the exception and attempts to immediately
     * terminate the target program.
     * 
     * <p>There is no guarantee that any further events will received from
     * the event dispatcher. Some events related to the shutdown of the
     * target VM may be observed, however.</p>
     */
    public static final class HaltingErrorHandler extends ErrorHandler {
        public HaltingErrorHandler() {
            super();
        }
        
        public HaltingErrorHandler(PrintStream logTarget) {
            super(logTarget);
        }
        
        protected void handleError(Throwable err, Thread t,
                InstrumentationManager instManager) {
            logError(err, t, instManager);
            instManager.dispatcher.haltTarget();
            logTarget.println("*** InstrumentationManager: " +
                    "halting program...");
        }
        
        protected boolean adviseResume() { return false; }
    }
}
