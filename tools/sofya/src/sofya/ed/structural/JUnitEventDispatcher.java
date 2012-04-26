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
 * A JUnitEventDispatcher is designed to be used by a specially modified JUnit
 * test runner to dispatch an event stream from JUnit test cases run on
 * instrumented code.
 *
 * <p>The JUnitEventDispatcher does not provide any mechanism for invoking a
 * subject class in a separate virtual machine, has a restricted set of
 * configuration options, and exposes special methods for controlling the
 * dispatch of distinct event streams.  It is expected that an instance of
 * a JUnitEvent Dispatcher class will be created inside a JUnit test runner.
 * Instrumented JUnit test suites and test cases can then be run,
 * using a TestListener (part of the JUnit framework) to handle processing
 * of per-test event streams at the appropriate times (as by writing a trace
 * file, for example). It is strongly recommended that the listener regularly
 * issue calls to the {@link #checkError} method to ensure timely termination
 * in the event of a failure. This class requires that the subject classes
 * be instrumented using the '<code>junit</code>' mode of instrumentation
 * (see {@link sofya.ed.cfInstrumentor}).</p>
 *
 * <p>This class cannot be run directly from the command line.</p>
 *
 * @author Alex Kinneer
 * @version 11/22/2006
 */
public final class JUnitEventDispatcher extends AbstractEventDispatcher
        implements Sofya$$Probe$10_36_3676__ {
    /** The singleton instance of the dispatcher. Only one instance may exist
        because it would not make sense to try to dispatch instrumentation
        to multiple event dispatchers. */
    private static JUnitEventDispatcher theDispatcher = null;
    /** The probe processing strategy that this dispatcher is configured
        to use. */
    private JUnitProcessingStrategy processingStrategy;

    /** Flag indicating whether the processing strategy was directly 
        installed as the target of the probe interface's constant
        probe field. If true, the processing strategy is the referent
        of the probe field, otherwise this event dispatcher is the
        referent of that field. */
    private final boolean installedDirect;
    
    /** Flag which indicates if an instrumentation error (wrong mode of
        instrumentation) has been detected. It is used to suppress repeat
        messages. */
    protected boolean instError = false;

    /*************************************************************************
     * No argument constructor not allowed.
     */
    private JUnitEventDispatcher() {
        throw new AssertionError("Illegal constructor");
    }

    /*************************************************************************
     * Constructs a new event dispatcher using a given processing strategy;
     * private to implement the singleton pattern.
     *
     * @param procStrategy Processing strategy to be used to process probes
     * received from the instrumentation.
     */
    private JUnitEventDispatcher(JUnitProcessingStrategy procStrategy,
            boolean installedDirect) {
        processingStrategy = procStrategy;
        dispatcherReady = true;
        this.installedDirect = installedDirect;
    }

    /*************************************************************************
     * Factory method that returns a singleton instance of the event
     * dispatcher.
     *
     * <p>A singleton strategy is enforced because it is only possible to
     * bind instrumentation to one instance for processing. If a
     * completely new instance is desired, call {@link #release} and
     * then call this method.</p>
     * 
     * <p>The instance produced by this factory method will support
     * changing the processing strategy in use at a later time.</p>
     *
     * @param strategy Processing strategy to be used to process probes
     * received from the instrumentation.
     *
     * @return A new event dispatcher, or a reference to the existing
     * singleton instance if this method has already been called and
     * {@link #release} has not yet been called.
     * 
     * @throws IllegalStateException If an instance already exists,
     * such that the processing strategy was directly installed
     * as the probe target.
     */
    public static JUnitEventDispatcher createEventDispatcher(
            JUnitProcessingStrategy strategy) {
        return createEventDispatcher(strategy, false);
    }
    
    /*************************************************************************
     * Factory method that returns a singleton instance of the event
     * dispatcher.
     *
     * <p>A singleton strategy is enforced because it is only possible to
     * bind instrumentation to one instance for processing. If a
     * completely new instance is desired, call {@link #release} and
     * then call this method.</p>
     *
     * @param strategy Processing strategy to be used to process probes
     * received from the instrumentation.
     * @param installDirect Specifies whether the processing strategy
     * should be installed as the direct target of instrumentation probes.
     * If <code>true</code>, this provides extra performance, but it
     * will not be possible to change the processing strategy after
     * instantiation (because the reference is installed to a final field
     * when the probe interface is initialized, an entirely new
     * VM must be launched).
     *
     * @return A new event dispatcher, or a reference to the existing
     * singleton instance if this method has already been called and
     * {@link #release} has not yet been called.
     * 
     * @throws IllegalStateException  If an instance already exists,
     * and was created with a contradicting <code>installDirect</code>
     * request, or the existing instance was created such that the
     * processing strategy was directly installed as the probe target,
     * and the processing strategy requested with this call is different
     * than the one previously installed.
     */
    public static JUnitEventDispatcher createEventDispatcher(
            JUnitProcessingStrategy strategy, boolean installDirect) {
        if (theDispatcher == null) {
            theDispatcher = new JUnitEventDispatcher(strategy, installDirect);
            Sofya$$Probe$10_36_3676__.ImplLoader.junitProbe =
                (installDirect) ? strategy : theDispatcher;
        }
        else {
            if (theDispatcher.installedDirect != installDirect) {
                throw new IllegalStateException("An instance of the event " +
                    "dispatcher already exists with a different policy " +
                    "for installing the processing strategy, and this " +
                    "configuration cannot be changed after instantiation");
            }
            
            theDispatcher.setProcessingStrategy(strategy);
        }
        return theDispatcher;
    }

    /*************************************************************************
     * Gets the processing strategy currently in use by this event dispatcher
     * to receive probes from the subject.
     *
     * @return The processing strategy that this event dispatcher is
     * configured to use to receive probes from the subject.
     */
    public JUnitProcessingStrategy getProcessingStrategy() {
        return processingStrategy;
    }

    /*************************************************************************
     * Specifies the processing strategy to be used by this event dispatcher
     * to receive probes from the subject.
     *
     * @param procStrategy The processing strategy to be used by this event
     * dispatcher to receive probes from the subject.
     * 
     * @throws IllegalStateException If the processing strategy was directly
     * installed when this dispatcher was created, and the requested
     * new processing strategy is different from the installed processing
     * strategy.
     */
    public void setProcessingStrategy(JUnitProcessingStrategy procStrategy) {
        if (installedDirect) {
            Class<?> strategyClass = this.processingStrategy.getClass();
            if (!strategyClass.isInstance(procStrategy)) {
                throw new IllegalStateException("This event dispatcher was " +
                    "constructed to dispatch probes directly, and this " +
                    "configuration cannot be changed after instantiation");
            }
        }
        processingStrategy = procStrategy;
    }

    /*************************************************************************
     * Reports the instrumentation mode detected in the subject.
     *
     * @return The numeric constant for the instrumentation mode detected in
     * the subject (e.g. coverage, sequence, compatibility).
     */
    public int getInstrumentationMode() {
        return processingStrategy.getInstrumentationMode();
    }

    /*************************************************************************
     * Registers the processing strategy and any attached components.
     *
     * @param edc The global event dispatcher configuration for this event
     * dispatcher.
     */
    public void register(EventDispatcherConfiguration edc) {
        processingStrategy.register(edc);
    }

    /*************************************************************************
     * Releases this event dispatcher, its processing strategy, and any
     * attached components.
     */
    public void release() {
        processingStrategy.release();
        theDispatcher = null;
        dispatcherReady = false;
    }

    /*************************************************************************
     * Initializes the event dispatcher such that it is ready to process
     * instrumentation and dispatch event streams.
     *
     * @throws SetupException If there is an error attempting to set up this
     * event dispatcher to receive instrumentation.
     */
    public void startDispatcher() {
        processingStrategy.initialize();
        
        if (!dispatcherReady || !processingStrategy.isReady()) {
            throw new SetupException("Dispatcher is not ready, it must be " +
                "configured or a new instance should be created");
        }
    }

    /*************************************************************************
     * Signals that a new test case is executing.
     *
     * <p>Some event stream observers may need to perform setup before
     * handling a new event stream, such as opening a new trace file.</p>
     *
     * <p>This method resets the flag indicating whether a test case
     * was instrumented.</p>
     *
     * @param testNumber Number associated with the current test.
     */
    public void newTest(int testNumber) {
        isInstrumented = false;
        processingStrategy.newTest(testNumber);
    }

    /*************************************************************************
     * Signals that the current test case has finished executing.
     *
     * <p>Some event stream observers may need to take some kind of action
     * here, such as saving or storing a trace file.</p>
     *
     * <p>Data structures for handling instrumentation will be reset for
     * the next test case.</p>
     */
    public void endTest(int testNumber) throws TraceFileException {
        processingStrategy.endTest(testNumber);
    }

    /**
     * Checks whether the last test case was instrumented.
     *
     * <p>If the test case executed instrumented code, this method will
     * return <code>true</code> until the next call to {@link #newTest},
     * otherwise it returns <code>false</code>.</p>
     *
     * @return <code>true</code> if the last test case executed instrumented
     * code and <code>newTraceFile</code> has not yet been called,
     * <code>false</code> otherwise.
     */
    public boolean checkInstrumented() {
        return isInstrumented || processingStrategy.checkInstrumented();
    }

    /*************************************************************************
     * Checks whether any exceptions have been raised during processing, and
     * rethrows the exception for handling if so. This method returns without
     * action if there are no errors.
     *
     * @throws Exception For any exception that was raised and stored.
     */
    public void checkError() throws Exception {
        processingStrategy.checkError();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    // Implementation of the probe interface (Sofya$$Probe$10_36_3676__)
    ///////////////////////////////////////////////////////////////////////////

    public void start(int port, int instMode, boolean doTimestamps,
                      boolean useSignalSocket, int entityType) {
        if ((instMode < 1) || (instMode > 4)) {
            if (!instError) {
                System.err.println("Cannot process type of instrumentation " +
                    "present in subject!");
                instError = true;
            }
            return;
        }
    }

    public void finish() {
        // We want to ignore calls to this method. They are spurious
        // in this context because the lifecycle of the trace data
        // is determined by the execution of test cases within the
        // JUnit framework. The event dispatcher framework will
        // guarantee the proper capture of all trace data.
    }

    public void writeObjectCount(String mSignature, int objCount) {
        isInstrumented = true;
        processingStrategy.writeObjectCount(mSignature, objCount);
    }

    public void writeTraceMessage(int objData, String mSignature) {
        processingStrategy.writeTraceMessage(objData, mSignature);
    }

    public byte[] getObjectArray(String mSignature, int objCount) {
        isInstrumented = true;
        return processingStrategy.getObjectArray(mSignature, objCount);
    }

    public void markMethodInSequence(String mSignature, int objCount) {
        isInstrumented = true;
        processingStrategy.markMethodInSequence(mSignature, objCount);
    }

    public void writeSequenceData() {
        processingStrategy.writeSequenceData();
    }
}
