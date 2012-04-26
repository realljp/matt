package junit.textui;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.NumberFormat;

import junit.framework.AssertionFailedError;
import junit.framework.SelectionTestListener;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.runner.BaseTestRunner;
import junit.runner.Differencer;

import sofya.base.Handler;
import sofya.base.ProjectDescription;
import sofya.ed.structural.AbstractEventDispatcher;
import sofya.ed.structural.JUnitEventDispatcher;
import sofya.ed.structural.SequenceTraceWriter;
import sofya.ed.structural.TraceHandler;

/**
 * Test listener and printer which tracks test case numbers, and provides
 * facilities for storing outputs, tracing instrumented test cases, and
 * diffing outputs.
 *
 * @author Alex Kinneer
 * @version 06/21/2006
 */
public class SelectionResultPrinter implements SelectionTestListener {
	
    /** Default writer to which outputs are written. */
    private PrintWriter resultWriter;
    
    /** Writer which will be attached to a temporary file when diffing
        without saving outputs to file. (The default writer is already
        reserved for output to the console.) */
    private PrintWriter tempWriter;

    /** Directory where outputs (and traces) will be saved if appropriate. */
    private String outputDir = null;
    
    /** Module used for diffing files, if any. */
    private Differencer differencer = null;
    
    /** JUnitEventDispatcher used for handling instrumented test cases,
        if appropriate. */
    private JUnitEventDispatcher dispatcher = null;
    
    /** Facade object to get information from the listener responsible for
        writing trace files. */
    private TraceFileWriter tfWriter = null;

    /** The current test number. */
    private int testNumber = 1;

    /** Message printed when the test case outcome is different. */
    private static final String DIFF_RESULT_MSG = "different test result";
    
    /** Message printed when the test case output is different. */
    private static final String DIFF_OUTPUT_MSG = "different test output";
    
    /** Message printed when the test case trace file is different. */
    private static final String DIFF_TRACE_MSG = "different test trace";

    /** Permanent reference to console standard out. */
    private static final PrintStream stdout = System.out;
    
    /** Permanent reference to console standard error. */
    private static final PrintStream stderr = System.err;

    /** Flag which specifies whether the test case granularity is method level
        or class level. Method level is used by default. */
    protected boolean classLvl = false;

    /**
     * Default constructor, creates a result printer which directs output
     * to stdout and has no special configuration.
     */
    public SelectionResultPrinter() {
        setWriter(null);  // Defaults to System.out
    }

    /**
     * Creates a result printer with specified configuration.
     *
     * @param writer Writer to which JUnit outputs should be directed. When
     * <code>outputDir</code> is set, this will be limited to certain outputs
     * of the JUnit tool itself instead of test outputs. May be
     * <code>null</code>, in which case outputs will be sent to
     * <code>stdout</code> (<code>System.out</code>).
     * @param outputDir Directory to which output files and traces should
     * be automatically saved. May be <code>null</code>, in which case outputs
     * are not saved to file.
     * @param differencer Diffing module to be used for comparing outputs;
     * can be used even when <code>outputDir</code> is <code>null</code>. May
     * be <code>null</code>, in which case outputs will not be diffed.
     * @param dispatcher Instance of <code>JUnitEventDispatcher</code> to be
     * used to interface with instrumented subject classes. It should be
     * configured externally, however the result printer needs a reference in
     * order to produce trace files at the appropriate times. May be
     * <code>null</code>, in which case tests are not traced (and will produce
     * errors if the subject is instrumented).
     */
    public SelectionResultPrinter(Writer writer, String outputDir,
                                  Differencer differencer,
                                  JUnitEventDispatcher dispatcher) {
        setWriter(writer);
        setOutputDir(outputDir);
        setDifferencer(differencer);
        setDispatcher(dispatcher);
    }

    /**
     * Sets the writer to which outputs should be directed. If an output
     * directory is also specified, the redirection of test outputs to
     * file(s) will override this setting, but certain tool outputs
     * will still be directed to this stream.
     *
     * @param writer Writer to which the outputs should be written.
     * May be <code>null</code>, in which case outputs will be directed
     * to <code>stdout</code>.
     */
    public void setWriter(Writer writer) {
        if (writer == null) {
            resultWriter = new PrintWriter(
                           new BufferedWriter(
                           new OutputStreamWriter(System.out)), true);
        }
        else if (writer instanceof PrintWriter) {
            resultWriter = (PrintWriter) writer;
        }
        else {
            resultWriter = new PrintWriter(writer);
        }
    }

    /**
     * Gets the writer to which outputs are currently being written.
     *
     * <p><b>Warning:</b> The writer may be attached to
     * <code>System.out</code>, so exercise caution if closing the stream!</p>
     *
     * @return The writer to which outputs are currently being written.
     */
    public Writer getWriter() {
        return resultWriter;
    }

    /**
     * Sets the output directory to which test outputs should be saved
     * (in files). Trace files are also copied to this directory.
     *
     * @param outputDir Path of the directory to which outputs are
     * to be saved. May be <code>null</code>, in which case
     * saving of outputs to file is disabled.
     */
    public void setOutputDir(String outputDir) {
        if (outputDir == null) {
            this.outputDir = null;
            return;
        }
        if (outputDir.charAt(outputDir.length() - 1) == File.separatorChar) {
            this.outputDir = outputDir;
        }
        else {
            this.outputDir = outputDir + File.separatorChar;
        }
    }

    /**
     * Gets the directory to which outputs are set to be saved.
     *
     * @return The directory where test outputs are being saved
     * to files.
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Sets the differencing module to be used for comparing test
     * outputs. May be used even if <code>outputDir</code> is
     * not set.
     *
     * @param differencer Differencing module for use in
     * comparison of test outputs. May be <code>null</code>,
     * in which case outputs will not be diffed.
     */
    public void setDifferencer(Differencer differencer) {
        this.differencer = differencer;
    }

    /**
     * Gets the differencing module currently being used to
     * compare test outputs.
     *
     * @return The differencing module which is currently
     * in use for diffing test outputs.
     */
    public Differencer getDifferencer() {
        return differencer;
    }

    /**
     * Sets the <code>JUnitEventDispatcher</code> that will be used to
     * trace an instrumented subject and generate trace files.
     *
     * @param dispatcher Instance of JUnitEventDispatcher to be used for
     * handling an instrumented subject; should be configured externally.
     */
    public void setDispatcher(JUnitEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Gets the <code>JUnitEventDispatcher</code> that is being used
     * to trace an instrumented subject.
     *
     * @return The event dispatcher that is handling the instrumented
     * subject and producing trace files.
     */
    public JUnitEventDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Sets whether the test case granularity is classes or methods.
     *
     * @param enable <code>true</code> to set the granularity level to
     * classes, <code>false</code> to set the granularity level to methods.
     */
    public void setLevelClass(boolean enable) {
        classLvl = enable;
    }

    /**
     * Reports whether the test case granularity is classes or methods.
     *
     * @return <code>true</code> if the granularity level is classes,
     * <code>false</code> if the granularity level is methods.
     */
    public boolean isLevelClass() {
        return classLvl;
    }

    final void setTraceFileWriter(TraceFileWriter tfWriter) {
        this.tfWriter = tfWriter;
    }

    /**
     * Respond to event indicating a test produced an error.
     */
    public void addError(Test test, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String output = "Result was error: " +
            BaseTestRunner.getFilteredTrace(sw.toString());
        resultWriter.print(output);
        resultWriter.flush();

        // Also write to temporary file if diffing when
        // not saving outputs to file
        if (tempWriter != null) {
            tempWriter.print(output);
            tempWriter.flush();
        }
    }

    /**
     * Respond to event indicating a test produced a failure.
     */
    public void addFailure(Test test, AssertionFailedError t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String output = "Result was failure: " +
            BaseTestRunner.getFilteredTrace(sw.toString());
        resultWriter.print(output);
        resultWriter.flush();

        // Also write to temporary file if diffing when
        // not saving outputs to file
        if (tempWriter != null) {
            tempWriter.print(output);
            tempWriter.flush();
        }
    }

    /**
     * Respond to event indicating that a test has completed.
     */
    public void endTest(Test test) {
        if (!classLvl) {
            endTest();
        }
    }

    private void endTest() {
        // If writing output to file, check for errors and close file
        if (outputDir != null) {
            // Close output file and restore standard streams
            System.out.close();
            System.err.close();
            System.setOut(stdout);
            System.setErr(stderr);
            // Close test result file
            if (resultWriter.checkError()) {
                System.err.println("ERROR: problem writing " +
                                   "test result to file");
            }
            resultWriter.close();
            resultWriter = null;
        }

        // Diff the files
        if (differencer != null) {
            // If using temp files (not saving outputs)
            if (tempWriter != null) {
                // Close output file and restore standard streams
                System.out.close();
                System.err.close();
                System.setOut(stdout);
                System.setErr(stderr);
                // Diff output files
                if (differencer.diff("out" + testNumber)) {
                    System.out.println(DIFF_OUTPUT_MSG);
                }
                // Close result file
                tempWriter.close();
                tempWriter = null;
                // Diff result files
                if (differencer.diff("t" + testNumber)) {
                    System.out.println(DIFF_RESULT_MSG);
                }
            }
            else {
                if (differencer.diff(outputDir + "t" + testNumber)) {
                    System.out.println(DIFF_RESULT_MSG);
                }
                if (differencer.diff(outputDir + "out" + testNumber)) {
                    System.out.println(DIFF_OUTPUT_MSG);
                }
            }
        }

        // Generate the trace file if appropriate
        if (dispatcher != null) {
            try {
                dispatcher.checkError();
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR: failure in event " +
                                           "dispatcher");
            }
            
            if (!dispatcher.checkInstrumented()) {
                System.err.println("WARNING: test " + testNumber +
                    " was not instrumented");
            }

            try {
                dispatcher.endTest(testNumber);
            }
            catch (AbstractEventDispatcher.TraceFileException e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR: unable to write " +
                    "trace file");
            }

            if (!tfWriter.usingRelaySocket()) {
                saveTrace(String.valueOf(testNumber - 1));
            }
        }
    }

    /**
     * Respond to event indicating that a test has begun.
     */
    public void startTest(Test test) {
        if (!classLvl) {
            startTest();
        }
    }

    private void startTest() {
        if (outputDir != null) {
            System.out.println(">>>>>>>>running test " + testNumber);
            try {
                resultWriter = new PrintWriter(
                    new BufferedWriter(new FileWriter(
                    outputDir + "t" + testNumber)));
                PrintStream outputStream = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(
                    outputDir + "out" + testNumber)));
                System.setOut(outputStream);
                System.setErr(outputStream);
            }
            catch (IOException e) {
                e.printStackTrace();
                // Use runtime exception since declaring a thrown
                // exception will violate the interface contract for
                // this method
                throw new RuntimeException("ERROR: unable to " +
                    "create file for output");
            }
        }
        else if (differencer != null) {
            try {
                File tempFile1 = new File("t" + testNumber);
                tempFile1.deleteOnExit();
                File tempFile2 = new File("out" + testNumber);
                tempFile2.deleteOnExit();
                tempWriter = new PrintWriter(new BufferedWriter(
                    new FileWriter(tempFile1)));
                PrintStream tempStream = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(tempFile2)));
                System.setOut(tempStream);
                System.setErr(tempStream);
            }
            catch (IOException e) {
                e.printStackTrace();
                // Use runtime exception since declaring a thrown
                // exception will violate the interface contract for
                // this method
                throw new RuntimeException("ERROR: unable to create " +
                    "file for differencing");
            }
        }
        if (dispatcher != null) {
            try {
                dispatcher.newTest(testNumber);
            }
            catch (AbstractEventDispatcher.TraceFileException e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR: unable to create " +
                    "trace file");
            }
        }
    }

    /**
     * Respond to event indicating that initialization of the test suite has
     * begun.
     *
     * <p>Static initializers in instrumented subject classes may run as a
     * result of the test suite initialization (loading), so the event
     * dispatcher is notified to begin capturing those events to a special
     * trace file (if appropriate).</p>
     */
    protected void startInit() {
        if (outputDir != null) {
            System.out.println(">>>>>>>>static initialization");
        }
        if (dispatcher != null) {
            try {
                dispatcher.newTest(-1);
            }
            catch (AbstractEventDispatcher.TraceFileException e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR: unable to create " +
                    "trace file (initialization)");
            }
        }
    }

    /**
     * Respond to event indicating that intialization of the test suite has
     * completed.
     *
     * <p>Static initializers in instrumented subject classes may run as a
     * result of the test suite initialization (loading), so the filter is
     * notified to write the trace file recording those events, (if
     * appropriate).</p>
     */
    protected void endInit() {
        if (dispatcher != null) {
            try {
                dispatcher.endTest(-1);
            }
            catch (AbstractEventDispatcher.TraceFileException e) {
                e.printStackTrace();
                throw new RuntimeException("ERROR: unable to write " +
                    "trace file (initialization)");
            }
            saveTrace("init");
        }
    }

    /**
     * Processes the trace generated by the last test.
     *
     * <p>Depending on the options which have been enabled, processing will
     * consist of diffing and/or copying the trace to the outputs directory,
     * with appropriate modifications when traces are being appended.</p>
     *
     * @param trName Name that should be given to the trace when it is
     * copied to the outputs directory, if applicable. If outputs are not
     * being saved, this parameter is ignored.
     */
    private void saveTrace(String trName) {
        String dbDir = ProjectDescription.dbDir + File.separatorChar;
        File dbTrace = new File(dbDir + tfWriter.getTraceFileName());
        File outputTrace = null;

        if (!tfWriter.isAppending()) {
            outputTrace =
                new File(dbDir + trName + tfWriter.getTraceFileExt());
            dbTrace.renameTo(outputTrace);
        }
        else {
            outputTrace = dbTrace;
        }

        // Diff the trace file if appropriate
        if ((differencer != null)
                && differencer.diff(outputTrace.getAbsolutePath())) {
            System.out.println(DIFF_TRACE_MSG);
        }

        if (outputDir != null) {
            if (!Handler.copyFile(outputTrace,
                    new File(outputDir + outputTrace.getName()))) {
                throw new RuntimeException("ERROR: unable to save trace " +
                    "to output directory");
            }
        }
        if (!tfWriter.isAppending()) {
            outputTrace.renameTo(dbTrace);
        }
    }

    /**
     * Print summary of test suite execution.
     *
     * @param result TestResult containing the results of execution of the
     * test suite.
     * @param runTime Running time of this execution of the test suite.
     */
    protected void printSummary(TestResult result, long runTime) {
        System.out.println();
        //System.out.println("Time: "+elapsedTimeAsString(runTime));
        System.out.println("Time (ms): "+runTime);
        if (result.wasSuccessful()) {
            System.out.println();
            System.out.print("OK");
            System.out.println (" (" + result.runCount() + " test" +
                (result.runCount() == 1 ? "": "s") + ")");
        }
        else {
            System.out.println();
            System.out.println("FAILURES!!!");
            System.out.println("Tests run: "+result.runCount()+ 
                               ",  Failures: "+result.failureCount()+
                               ",  Errors: "+result.errorCount());
        }
        System.out.println();
    }

    /**
     * Returns the formatted string of the elapsed time.
     * Duplicated from BaseTestRunner. Fix it.
     *
     * <p>Copied from junit.textui.ResultPrinter, --AK</p>
     */
    protected String elapsedTimeAsString(long runTime) {
        return NumberFormat.getInstance().format((double)runTime/1000);
    }

    //////////////////////////////////////////////////////////////
    // Implementation of the SelectionTestListener interface
    //
    public void decrementTestNumber() { testNumber--; }
    public void incrementTestNumber() { testNumber++; }
    public void setTestNumber(int number) { testNumber = number; }
    public int getTestNumber() { return testNumber; }
    public void startTestClass(Test testClass) {
        // System.out.println("Received event: start test class: " + testClass);
        // System.out.println("Test number: " + testNumber);
        startTest();
    }
    public void endTestClass() {
        // System.out.println("Received event: end test class");
        endTest();
    }

    static final class TraceFileWriter {
        private final TraceHandler covTrace;
        private final SequenceTraceWriter seqTrace;
        private final int traceType;

        private static final int COVERAGE = 1;
        private static final int SEQUENCE = 2;

        private static final String COVERAGE_EXT = ".tr";
        private static final String SEQUENCE_EXT = ".seq";

        private TraceFileWriter() {
            throw new UnsupportedOperationException();
        }

        TraceFileWriter(TraceHandler covTrace) {
            this.covTrace = covTrace;
            this.seqTrace = null;
            this.traceType = COVERAGE;
        }

        TraceFileWriter(SequenceTraceWriter seqTrace) {
            this.covTrace = null;
            this.seqTrace = seqTrace;
            this.traceType = SEQUENCE;
        }

        String getTraceFileName() {
            switch (traceType) {
            case COVERAGE:
                return covTrace.getTraceFileName() + COVERAGE_EXT;
            case SEQUENCE:
                return seqTrace.getTraceFileName() + SEQUENCE_EXT;
            default:
                throw new IllegalStateException();
            }
        }

        String getTraceFileExt() {
            switch (traceType) {
            case COVERAGE:
                return COVERAGE_EXT;
            case SEQUENCE:
                return SEQUENCE_EXT;
            default:
                throw new IllegalStateException();
            }
        }

        boolean isAppending() {
            switch (traceType) {
            case COVERAGE:
                return covTrace.isAppending();
            case SEQUENCE:
                return seqTrace.isAppending();
            default:
                throw new IllegalStateException();
            }
        }

        boolean usingRelaySocket() {
            switch (traceType) {
            case COVERAGE:
                return false;
            case SEQUENCE:
                return seqTrace.usingRelaySocket();
            default:
                throw new IllegalStateException();
            }
        }
    }
}

