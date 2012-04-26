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

package sofya.ed.structural;

/**
 * A Sofya probe is responsible for transmitting trace data from the 
 * instrumented program to the event dispatcher. It is calls to methods
 * on this interface which are actually inserted into instrumented
 * programs. The instrumentor is responsible for inserting a call to
 * {@link #start(int, int, boolean, boolean, int)} as the first executable
 * instruction of an instrumented class.
 *
 * <p>To enable this mechanism to work with the greatest possible
 * genericity, and to avoid naming and dynamic linking conflicts when an
 * instrumented dispatcher is being run as a subject, this interface is
 * handled in a special fashion. When a dispatcher runs, it constructs a
 * jar file which contains this interface (and additional classes), and
 * prepends the jar file to the bootstrap classpath for the subject. Thus
 * when a subject seeks to resolve a call to the methods defined by this
 * interface, the first definition of the interface to be located by the
 * class loader is the one provided on the bootstrap classpath, including
 * when the subject is an instrumented dispatcher. An isolation class
 * loader is also provided on the bootstrap classpath, which is used to
 * load the probe implementation, and any other Sofya classes or library
 * classes that it requires. This isolates both the Sofya classes, and
 * any library dependencies required by the probe implementation, from the
 * application class loader. Thus, all other subject classes (including
 * Sofya classes, if the subject is a Sofya event dispatcher) will be found
 * by the regular application class loader in their expected locations on
 * the subject's own classpath. Note that this also means that the probe
 * runs inside the same JVM that is running the subject, so the fact that
 * an instrumented dispatcher locates the shared probe before it locates
 * its own is not problematic (it will however copy its own probe
 * when constructing its own jar file for its subject).</p>
 *
 * <p>When the probe itself and any of its dependencies are instrumented,
 * the instrumentor modifies the probe calls to invoke on an interface with
 * a modified name. That alternately-named interface is created dynamically
 * by the invoking dispatcher at runtime, and the probe implementation and
 * its dependent classes are likewise patched to use alternate names. This
 * ensures that the instrumentation in the probe implementation and its
 * dependent classes does not call into the probe itself, causing infinite
 * recursion. Instead, instrumentation is bound to alternately-named and
 * uninstrumented copies of the probe implementation obtained from the
 * top-level invoking event dispatcher.</p>
 *
 * <p>With this mechanism it is possible to create a chain of dispatchers
 * executing other dispatchers as subjects, although intervention to set the
 * ports correctly is required beyond the first subject dispatcher. This is
 * likely only useful up to the second level of self analysis, as data
 * obtained from further chaining is redundant. (The first level of self
 * analysis will provide trace information for the
 * {@link SocketProcessingStrategy#processProbesSynchronized} method
 * and the second level of self analysis will provide trace information for
 * {@link SocketProcessingStrategy#processProbes}).</p>
 *
 * @see sofya.ed.structural.ProgramEventDispatcher
 * @see sofya.ed.cfInstrumentor
 *
 * @author Alex Kinneer
 * @version 11/08/2006
 */
public interface Sofya$$Probe$10_36_3676__ {
    /** Globally visible reference to the actual probe implementation. */
    static final Sofya$$Probe$10_36_3676__ probe = ImplLoader.createImpl();
    
    /**
     * Loader used to initialize the <code>probe</code> field reference,
     * as it is a final field, and custom static initializers are not
     * permitted for interfaces.
     */
    static final class ImplLoader {
        private static final String implName;
        
        // Accessed by JUnitEventDispatcher
        static Sofya$$Probe$10_36_3676__ junitProbe;
        
        static {
            String implProp = System.getProperty("PROBE_IMPL_CLASS");
            if (implProp != null) {
                implName = implProp;
                System.clearProperty("PROBE_IMPL_CLASS");
            }
            else {
                implName = "sofya.ed.structural.SocketProbeImpl";
            }
        }
        
        /**
         * Creates an instance of the probe implementation.
         */
        static Sofya$$Probe$10_36_3676__ createImpl() {
            Sofya$$Probe$10_36_3676__ theInstance = null;
            
            if (junitProbe != null) {
                return junitProbe;
            }
            
            try {
                Sofya$$Probe$ClassLoader loader =
                    new Sofya$$Probe$ClassLoader();
                
                Class cl = loader.loadClass(implName);
                
                //System.out.println("SocketProbeImpl loader:" +
                //    cl.getClassLoader());
                theInstance = (Sofya$$Probe$10_36_3676__) cl.newInstance();
            }
            catch (Exception e) {
                throw new Error("Sofya: Could not instantiate probe " +
                    "implementation", e);
            }
            return theInstance;
        }
        
        private ImplLoader() {
            throw new AssertionError("Illegal constructor");
        }
    }
    
    /**
     * Holder class for the sequence array, so that it does not get
     * allocated when we are not using optimized sequence tracing
     * instrumentation.
     */
    public static final class SequenceProbe {
        /** Array which stores the traced object IDs sequentially, using
            special marker/index pairs to indicate entry into new methods.
            It is public so that the instrumentation is not required
            to make a method call to retrieve a reference to it. */
        public static int[] sequenceArray;
        /** Index pointing to the next open entry in the sequence array.
            Instrumentation is responsible for updating this pointer when
            recording an object ID, and calling
            <code>writeSequenceData</code> when the array is filled. */
        public static int sequenceIndex = 0;
        
        private SequenceProbe() {
            throw new AssertionError("Illegal constructor");
        }
    }
    
    /** Size of the array which records object sequence information
        when handling optimized sequence instrumentation. */
    public static final int SEQUENCE_ARRAY_SIZE = 16384;
    /** Constant which marks that the next element in the object
        sequence array contains the index to a new method signature. */
    public static final int NEW_METHOD_MARKER = 0xFC000000;
    /** Constant which marks in the sequence array that the current
        method is exiting. */
    public static final int BRANCH_EXIT_MARKER = 0xF8000000;
    // The markers above are safely distinct because they encode invalid
    // block/branch types and a block/branch ID of 0, which is also not
    // legal (numbering starts at 1)
    
    /*************************************************************************
     * Initializes the probe implementation.
     *
     * <p>Probe implementations should take precautions to ensure
     * that all calls to this method after the first are ignored. The
     * instrumentor cannot guarantee that this method will only be
     * called once.</p>
     *
     * <p>The instrumentor should guarantee that a call to this method is
     * inserted into the subject class as the first possible executable
     * statement in the class, either in the static initializer
     * (<i>&lt;clinit&gt;</i>) or in <i>main</i>. This will ensure that all
     * instrumentation is transmitted correctly. If the probe itself is
     * instrumented, a call to this method on the synthetic
     * alternately-named copy of this interface will be
     * inserted in its static initializer, which allows the probe to be
     * traced without introducing infinite recursion problems.</p>
     *
     * <p>If the subject being run is an event dispacher, the subject
     * dispatcher's probe will be running in its own subject's JVM. As a
     * result, to trace the probe (logically considered a component of the
     * subject event dispatcher), the main invoking dispatcher will need to
     * accept two connections, one from the subject event dispatcher and
     * one from the subject dispatcher's probe. For some types of
     * instrumentation, this requires it to synchronize the trace
     * packets it receives. When this is necessary, the
     * <code>doTimestamping</code> flag should be set by the instrumentor,
     * and the probe should respect this request by timestamping the
     * trace events. Under no other circumstance should this flag be set.
     * (See {@link ProgramEventDispatcher}).</p>
     *
     * <p>All parameters should be set by the instrumentor.</p>
     *
     * @param port Port number to be used, if the backing implementation
     * uses a socket transport (alternately, this might be used for some
     * other implementation-specific numeric address).
     * @param instMode Integer flag specifying the type of instrumentation
     * present in the subject.
     * @param doTimestamps Flag indicating whether trace evens are to be
     * timestamped. This is only used with the subject is an event dispatcher.
     * @param useSignalSocket Flag indicating whether a signal socket
     * connection should be made to the event dispatcher. This is only used
     * when the subject is an event dispatcher.
     * @param objType Integer flag specifying the type of program entity
     * traced by the instrumentation present in the subject.
     */
    void start(int port, int instMode, boolean doTimestamps,
            boolean useSignalSocket, int objType);
    
    /*************************************************************************
     * Signals that the program has exited and the probe should shut down.
     */
    void finish();
    
    /*************************************************************************
     * Indicates the number of structural objects in the current method, to
     * be transmitted so that the event dispatcher can notify listeners
     * as necessary.
     *
     * @param mSignature Signature of the method for which the object count
     * is being sent.
     * @param objCount Number of objects in the method.
     */
    void writeObjectCount(String mSignature, int objCount);
    
    /*************************************************************************
     * Records a trace event with the probe.
     *
     * <p>A single threaded subject can only call this method sequentially,
     * resulting in typical in-order processing of trace events.
     * Multi-threaded subjects, however, may call this method from
     * different threads, in which case the order of processing of
     * trace events is not guaranteed to be deterministic.
     * The actual transmission of the event should be synchronized,
     * however, to ensure that the data will not be corrupted.</p>
     *
     * <p>This method may block briefly if an underlying buffer is filled,
     * but should normally return immediately.</p>
     *
     * @param objData ID of the trace object marked by this probe.
     * @param mSignature Full signature of the method which contains
     * the trace object being marked.
     */
    void writeTraceMessage(int objData, String mSignature);
    
    /*************************************************************************
     * Gets the byte array recording the coverage of trace objects for
     * a given method.
     *
     * <p>If this is the first time the method has been traced, an array of
     * the necessary size should be allocated and initialized. Calls to
     * this method are inserted at the beginning of methods in the subject
     * by the instrumentor.</p>
     *
     * @param mSignature Signature of the method for which the byte array
     * is to be retrieved. The signature guarantees uniqueness.
     * @param objCount Number of trace objects in the method, used only
     * when the array must be allocated. This value should be determined
     * by the CFG builder and set by the instrumentor.
     *
     * @return The byte array recording trace objects covered in the
     * method.
     */
    byte[] getObjectArray(String mSignature, int objCount);
    
    /*************************************************************************
     * Inserts a new method marker and index into the sequence array.
     *
     * <p>A map is checked to see if an index has already been created for
     * the given signature string. If it has, the existing index is
     * retrieved, otherwise a new index is created and added. A new
     * method marker is then inserted, followed by the index to the
     * signature string. The index pointer is advanced by two so that
     * it is left pointing to the next open element in the array.
     * If advancing the pointer two elements will exceed the size of
     * the array, the array is first transmitted and the
     * pointer is reset to zero before proceeding.</p>
     *
     * @param mSignature Signature of the method which has been entered
     * and needs to be marked in the array.
     * @param objCount Number of trace objects in the method.
     */
    void markMethodInSequence(String mSignature, int objCount);
    
    /*************************************************************************
     * Transmits the current contents of the trace object sequence array
     * to the event dispatcher.
     * 
     * When this method returns, all of the object IDs currently in the
     * array will have been sent and the array index pointer reset to zero.
     */
    void writeSequenceData();
}
