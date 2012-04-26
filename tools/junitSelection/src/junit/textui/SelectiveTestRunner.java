package junit.textui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import junit.extensions.UniverseFileGenerator;
import junit.framework.SelectionTestResult;
import junit.framework.SelectionTestSuite;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.Differencer;
import junit.runner.UnixShellDifferencer;
import junit.runner.Version;
import junit.textui.SelectionResultPrinter.TraceFileWriter;

import sofya.base.SConstants.TraceObjectType;
import sofya.ed.structural.JUnitEventDispatcher;
import sofya.ed.structural.JUnitProcessingStrategy;
import sofya.ed.structural.SequenceTraceWriter;
import sofya.ed.structural.TraceHandler;
import sofya.ed.structural.AbstractEventDispatcher.SetupException;
import sofya.ed.structural.processors.JUnitBlockCoverageProcessingStrategy;
import sofya.ed.structural.processors.JUnitBlockSequenceProcessingStrategy;
import sofya.ed.structural.processors.JUnitBranchCoverageProcessingStrategy;
import sofya.ed.structural.processors.JUnitBranchSequenceProcessingStrategy;

/**
 * A command line based tool to run selected tests, prioritize
 * test selection, support tracing of instrumented subjects, and
 * perform automatic differencing between test runs.
 * <pre>
 * java junit.textui.SelectiveTestRunner [-wait] [-sID &lt;IDs&gt; | -sName &lt;names&gt;]
 *    [-o &lt;outputdir&gt;] [-d &lt;diffdir&gt;] [-t &lt;options&gt;] [-p] TestCaseClass
 * </pre>
 * SelectiveTestRunner expects the name of a TestCase class as argument.
 * If this class defines a static <code>suite</code> method it 
 * will be invoked and the returned test is run. Otherwise all 
 * the methods starting with "test" having no arguments are run.
 * <p>
 * When the wait command line argument is given to SelectiveTestRunner
 * waits until the users types RETURN.
 * <p>
 *
 * @author Alex Kinneer
 * @version 04/24/2006
 */
public class SelectiveTestRunner extends TestRunner {
	
    /** Array containing selected test numbers. If non-null,
        <code>selectedNames</code> array must be null. */
    protected int[] selectedIDs = null;
    
    /** Array containing the names of selected tests. If non-null,
        <code>selectedIDs</code> array must be null. */
    protected String[] selectedNames = null;
    
    /** Test listener which prints outputs, and performs differencing
        and tracing if appropriate. */
    protected SelectionResultPrinter fPrinter;
    
    /** Event dispatcher used to trace instrumented test cases,
        if appropriate. */
    protected JUnitEventDispatcher dispatcher;
    
    /** Module which generates a universe file which can be used to run
        the tests in the test suite in the same order as the current
        run (in a less efficient manner). */
    protected UniverseFileGenerator ufg = null;

    /** Activated by &apos;<code>-names</code>&apos; option. When <code>true</code>,
        the runner simply prints the names and numbers of the test cases
        encountered without running them. */
    protected boolean displayOnly = false;
    
    /** Activated by &apos;<code>-count</code>&apos; option. When <code>true</code>,
        the runner simply prints the total number of tests cases found in
        the test suite. */
    protected boolean countOnly = false;
    
    /** Activated by '<code>-p</code>' option. When <code>true</code>,
        the runner operates in priorization mode and will run tests in
        the literal order specified. */
    protected boolean prioritizing = false;

    protected boolean classLvl = false;

    /** Convenience reader for System.in. */
    protected static final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    /**
     * Constructs a SelectiveTestRunner.
     */
    public SelectiveTestRunner() {
        fPrinter = new SelectionResultPrinter();
        
        // Creating a default dispatcher
        dispatcher = JUnitEventDispatcher.createEventDispatcher(
                new JUnitBlockCoverageProcessingStrategy());
        setDispatcherOptions(dispatcher, "type=BC", TraceObjectType.BASIC_BLOCK);
    }

    /**
     * Constructs a SelectiveTestRunner.
     *
     * @param writer Outputs of test cases are written to this stream.
     * @param outputDir Specifies the directory where files containing the
     * outputs of test cases are to be written.
     * @param differencer The differencing module to be used for diffing outputs.
     * @param dispatcher JUnitEventDispatcher to be used for tracing
     * instrumented subject classes.
     */
    public SelectiveTestRunner(Writer writer, String outputDir,
                               Differencer differencer,
                               JUnitEventDispatcher dispatcher) {
        fPrinter = new SelectionResultPrinter(writer, outputDir,
                                              differencer, dispatcher);
    }

    /**
     * Constructs a SelectiveTestRunner using the given SelectionResultPrinter
     * for all the output.
     *
     * @param printer SelectionResultPrinter which will be used to handle
     * the test cases outputs.
     */
    public SelectiveTestRunner(SelectionResultPrinter printer) {
        fPrinter = printer;
    }

    /**
     * Throws <code>UnsupportedOperationException</code>. Use
     * {@link SelectiveTestRunner#run(Test,int[])} or
     * {@link SelectiveTestRunner#run(Test,String[])} instead.
     */
    static public TestResult run(Test test) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws <code>UnsupportedOperationException</code>. Use
     * {@link SelectiveTestRunner#runAndWait(Test,int[])} or
     * {@link SelectiveTestRunner#runAndWait(Test,String[])} instead.
     */
    static public void runAndWait(Test suite) {
        throw new UnsupportedOperationException();
    }

    /**
     * Runs the specified tests from a test suite and collects the
     * results, selecting tests by number. This method can be used
     * to start a test run from your program.
     * <pre>
     * public static void main (String[] args) {
     *     test.textui.TestRunner.run(suite(), selectedTestIDs);
     * }
     * </pre>
     *
     * @param test Test suite from which to run selected tests.
     * @param selectedIDs List of tests to be run. The order
     * of entries in this list is considered important if
     * prioritization is activated.
     */
    static public TestResult run(Test test, int[] selectedIDs) {
        SelectiveTestRunner runner= new SelectiveTestRunner();
        runner.setSelectedIDs(selectedIDs);
        return runner.doRun(test);
    }

    /**
     * Runs the specified tests from a test suite and collects the
     * results, selecting tests by name. This method can be used to
     * start a test run from your program.
     * <pre>
     * public static void main (String[] args) {
     *     test.textui.TestRunner.run(suite(), selectedTestNames);
     * }
     * </pre>
     *
     * <p><strong>WARNING:</strong> It is possible for a test suite
     * to invoke a test method multiple times at different points
     * (presumably on different inputs). Selecting by name will
     * cause all invocations to be selected, as each is considered
     * a distinct test case. As this may not be expected behavior,
     * the test runner will emit a warning when the number of
     * tests run exceeds the number in the selection list.
     * Selection by ID will never cause more than one test to be
     * selected per list entry.</p>
     *
     * @param test Test suite from which to run selected tests.
     * @param selectedNames List of tests to be run. The order
     * of entries in this list is considered important if
     * prioritization is activated.
     */
    static public TestResult run(Test test, String[] selectedNames) {
        SelectiveTestRunner runner= new SelectiveTestRunner();
        runner.setSelectedNames(selectedNames);
        return runner.doRun(test);
    }

    /**
     * Runs selected tests from a suite and waits until the user
     * types RETURN.
     *
     * @param suite Test suite from which to run selected tests.
     * @param selectedIDs List of tests to be run.
     */
    static public void runAndWait(Test suite, int[] selectedIDs) {
        SelectiveTestRunner aTestRunner= new SelectiveTestRunner();
        aTestRunner.setSelectedIDs(selectedIDs);
        aTestRunner.doRun(suite, true);
    }

    /**
     * Runs selected tests from a suite and waits until the user
     * types RETURN.
     *
     * @param suite Test suite from which to run selected tests.
     * @param selectedIDs List of tests to be run.
     */
    static public void runAndWait(Test suite, String[] selectedNames) {
        SelectiveTestRunner aTestRunner= new SelectiveTestRunner();
        aTestRunner.setSelectedNames(selectedNames);
        aTestRunner.doRun(suite, true);
    }

    /**
     * Creates the TestResult to be used for the test run.
     */
    protected TestResult createTestResult() {
        SelectionTestResult stResult;
        if (selectedIDs == null) { // Selection is by names
            stResult = new SelectionTestResult(selectedNames, displayOnly,
                                               countOnly, prioritizing);
        }
        else { // Selection is by test numbers
            stResult = new SelectionTestResult(selectedIDs, displayOnly,
                                               countOnly, prioritizing);
        }
        stResult.setLevelClass(classLvl);
        return stResult;
    }

    /**
     * Runs the test suite.
     *
     * @param suite Test suite to be run.
     * @param wait Pauses the runner after the suite finishes,
     * if <code>true</code>.
     */
    public TestResult doRun(Test suite, boolean wait) {
    	
        if ((selectedIDs == null) && (selectedNames == null)) {
            if (!(displayOnly || countOnly)) {
                try {
                    if (!stdin.ready()) {
                        throw new IllegalStateException("No tests selected");
                    }
                    else {
                        getSelectedFromStdin();
                    }
                }
                catch (IOException e) {
                    throw (RuntimeException) (new RuntimeException("Nested " +
                        "exception: " + e)).fillInStackTrace();
                }
            }
        }
        
        TestResult result = createTestResult();
        result.addListener(fPrinter);
        if (ufg != null) {
            result.addListener(ufg);
        }
        long startTime = System.currentTimeMillis();

        suite.run(result);
        
        // If prioritizing, in the first run the test result will only have
        // collected references to the tests to be run. We must now issue a
        // callback to have it run the tests in the specified order.
        if (prioritizing) {
            ((SelectionTestResult) result).runPrioritized();
        }
        
        long endTime = System.currentTimeMillis();
        long runTime = endTime-startTime;
        
        if (fPrinter.getOutputDir() != null) {
            fPrinter.printSummary(result, runTime);
        }
        
        if (ufg != null) {
        	ufg.finish();
        }
        
        if (countOnly) {
            System.out.println(((SelectionTestResult) result).getTestNumber());
        }

        pause(wait);
        return result;
    }

    /**
     * Selects the tests to be run with a list of IDs. Deactivates
     * selection by name and destroys the list of selected test names.
     *
     * @param selectedIDs List of IDs corresponding to the tests to be run.
     */
    public void setSelectedIDs(int[] selectedIDs) {
        this.selectedIDs = selectedIDs;
        this.selectedNames = null;
    }

    /**
     * Selects the tests to be run with a list of names. Deactivates
     * selection by ID and destroys the list of selected test IDs.
     *
     * @param selectedIDs List of names corresponding to the tests to be run.
     */
    public void setSelectedNames(String[] selectedNames) {
        this.selectedNames = selectedNames;
        this.selectedIDs = null;
    }

    /**
     * Entry point for SelectiveTestRunner.
     */
    public static void main(String args[]) {
        SelectiveTestRunner aTestRunner= new SelectiveTestRunner();
        try {
            TestResult r= aTestRunner.start(args);
            if (!r.wasSuccessful())
                System.exit(FAILURE_EXIT);
            System.exit(SUCCESS_EXIT);
        }
        catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(FAILURE_EXIT);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(EXCEPTION_EXIT);
        }
    }

    /**
     * Starts a test run. Analyzes the command line arguments
     * and runs the given test suite.
     *
     * throws Exception If there is an error setting up or
     * running the test suite.
     */
    public TestResult start(String args[]) throws Exception {
        String testCase = "";
        boolean wait = false;

        for (int i= 0; i < args.length; i++) {
            if (args[i].equals("-wait"))
                wait= true;
            else if (args[i].equals("-names"))
                displayOnly = true;
            else if (args[i].equals("-count"))
                countOnly = true;
            else if (args[i].equals("-c")) 
                testCase= extractClassName(args[++i]);
            else if (args[i].equals("-v"))
                System.err.println("JUnit " + Version.id() + " by Kent " +
                    "Beck and Erich Gamma");
            else if (args[i].equals("-o")) {
                if (args.length > (i + 1)) {
                    fPrinter.setOutputDir(args[++i]);
                }
                else {
                    throw new IllegalStateException("You must provide a " +
                        "directory for output files");
                }
            }
            else if (args[i].equals("-d")) {
                if (args.length > (i + 1)) {
                    fPrinter.setDifferencer(
                        new UnixShellDifferencer(args[++i]));
                }
                else {
                    throw new IllegalStateException("You must provide a " +
                        "directory for differencing");
                }
            }
            else if (args[i].equals("-t")) {
                if (args.length == (i + 1)) {
                    throw new IllegalStateException("You must specify " +
                        "trace types");
                }
                dispatcher.release();
                if (args[i + 1].equals("branch")) {
                    dispatcher = JUnitEventDispatcher.createEventDispatcher(
                        new JUnitBranchCoverageProcessingStrategy());
                    setDispatcherOptions(dispatcher, args[i + 2],
                        TraceObjectType.BRANCH_EDGE);
                    i += 2;
                }
                else if (args[i + 1].equals("block")) {
                    dispatcher = JUnitEventDispatcher.createEventDispatcher(
                        new JUnitBlockCoverageProcessingStrategy());
                    setDispatcherOptions(dispatcher, args[i + 2],
                        TraceObjectType.BASIC_BLOCK);
                    i += 2;
                }
                else {
                    dispatcher = JUnitEventDispatcher.createEventDispatcher(
                        new JUnitBlockCoverageProcessingStrategy());
                    setDispatcherOptions(dispatcher, args[i + 1],
                        TraceObjectType.BASIC_BLOCK);
                    i += 1;
                }

                fPrinter.setDispatcher(dispatcher);
            }
            else if (args[i].equals("-p")) {
                prioritizing = true;
            }
            else if (args[i].equals("-univ")) {
                if (args.length > (i + 1)) {
                    ufg = new UniverseFileGenerator(args[++i]);
                }
                else {
                    throw new IllegalStateException("You must provide a " +
                        "name for the universe file");
                }
            }
            else if (args[i].equals("-sID")) {
                if (args.length > (i + 1)) {
                    try {
                        getSelected(args[++i]);
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalStateException("Illegal value '" +
                            e.getMessage() + "' in test selection list");
                    }
                }
                else {
                    throw new IllegalStateException("You must provide a " +
                        "list of selected test numbers");
                }
            }
            else if (args[i].equals("-sName")) {
                if (args.length > (i + 1)) {
                    getNames(args[++i]);
                }
                else {
                    throw new IllegalStateException("You must provide a " +
                        "list of selected test names");
                }
            }
            else if (args[i].equals("-classlvl")) {
                classLvl = true;
                fPrinter.setLevelClass(true);
            }
            else
                testCase= args[i];
        }
        
        if (ufg != null) {
            ufg.setSuiteName(testCase);
            if (classLvl) {
                ufg.setLevelClass(true);
            }
        }

        if (testCase.equals("")) 
            throw new IllegalStateException("Usage: TestRunner [-wait] " +
                "testCaseName, where name is the name of the TestCase class");

        if (dispatcher != null) {
            try {
                // Starting dispatcher
                dispatcher.startDispatcher();
            }
            catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        try {
            // The result printer is notified to generate a trace which will
            // capture the probes that may transmit as a result of static
            // initializers being run if the loading of the test suite causes
            // the classloader to bring in some subject classes.

            fPrinter.startInit();
            Test suite = getTest(testCase);
            if (classLvl) {
                if (suite instanceof TestSuite) {
                    suite = new SelectionTestSuite((TestSuite) suite);
                }
                else {
                    // I have a sneaking suspicion this will never happen,
                    // but make a nice error message anyway...
                    throw new Exception("Cannot use selective test runner on " +
                        "static test methods");
                }
            }
            fPrinter.endInit();

            return doRun(suite, wait);
        }
        catch (NumberFormatException e) {
            // Thrown on invalid input to stdin: suppress stack trace
            throw e;
        }
        catch (IllegalStateException e) {
            // Thrown when no tests are selected: suppress stack trace
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not create and run test suite: " + e);
        }
        finally {
            if (dispatcher != null) {
                dispatcher.release();
            }
        }
    }

    /**
     * Prints specified message and exits with failure status.
     *
     * @param message Error message to be displayed.
     */
    protected void runFailed(String message) {
        System.err.println(message);
        System.exit(FAILURE_EXIT);
    }

    /**
     * Sets the output printer to the specified SelectionResultPrinter.
     * The SelectionResultPrinter is also responsible for tracing and
     * differencing, when those options are enabled.
     *
     * @param printer SelectionResultPrinter to be used for handling
     * test outputs.
     */
    public void setPrinter(SelectionResultPrinter printer) {
        fPrinter= printer;
    }

    /**
     * Parses the options specified to the event dispatcher and sets
     * the parameters on the event dispatcher object appropriately.
     *
     * @param dispatcher JUnitEventDispatcher to be configured.
     * @param options Comma-delimited string of '='-delimited name-value
     * option pairs.
     *
     * @throws AbstractEventDispatcher.SetupException If a setup error
     * prevents the JUnitEventDispatcher from being prepared to
     * handle trace messages.
     */
    private void setDispatcherOptions(JUnitEventDispatcher dispatcher,
                                      String options,
                                      TraceObjectType objectType)
            throws SetupException {
    	
        StringTokenizer strtok = new StringTokenizer(options, "=,");
        String token = null;

        JUnitProcessingStrategy strategy = dispatcher.getProcessingStrategy();
        List typeParam = new ArrayList(1);
        String trName = null;
        boolean appendToTrace = false;
        boolean usingRelaySocket = false;
        String preData = null;
        String postData = null;

        SequenceTraceWriter seqTrace = null;

        try {
            while (strtok.hasMoreTokens()) {
                token = strtok.nextToken();
                if (token.equals("type")) {
                    typeParam.add("-" + strtok.nextToken());
                }
                else if (token.equals("trname")) {
                    trName = strtok.nextToken();
                }
                else if (token.equals("at")) {
                    token = strtok.nextToken().toLowerCase();
                    if (token.equals("t") || token.equals("true")) {
                        appendToTrace = true;
                    }
                }
                else if (token.equals("mode")) {
                    token = strtok.nextToken().toLowerCase();

                    if (token.equals("norm")) {
                        if (objectType == TraceObjectType.BRANCH_EDGE) {
                            strategy =
                                new JUnitBranchCoverageProcessingStrategy();
                            dispatcher.setProcessingStrategy(strategy);
                        }
                        else {
                            strategy =
                                new JUnitBlockCoverageProcessingStrategy();
                            dispatcher.setProcessingStrategy(strategy);
                        }
                    }
                    else if (token.equals("seq")) {
                        seqTrace = new SequenceTraceWriter();

                        if (objectType == TraceObjectType.BRANCH_EDGE) {
                            JUnitBranchSequenceProcessingStrategy seqStrategy =
                                new JUnitBranchSequenceProcessingStrategy();
                            dispatcher.setProcessingStrategy(seqStrategy);
                            seqStrategy.addEventListener(seqTrace);
                            strategy = seqStrategy;
                        }
                        else {
                            JUnitBlockSequenceProcessingStrategy seqStrategy =
                                new JUnitBlockSequenceProcessingStrategy();
                            dispatcher.setProcessingStrategy(seqStrategy);
                            seqStrategy.addEventListener(seqTrace);
                            strategy = seqStrategy;
                        }
                    }
                    else {
                        throw new IllegalArgumentException("Unrecognized " +
                            "instrumentation mode");
                    }
                }
                else if (token.equals("relay")) {
                    token = strtok.nextToken().toLowerCase();
                    if (token.equals("t") || token.equals("true")) {
                        usingRelaySocket = true;
                    }
                }
                else if (token.equals("pre")) {
                    preData = strtok.nextToken();
                }
                else if (token.equals("post")) {
                    postData = strtok.nextToken();
                }
                else {
                    throw new IllegalArgumentException("Unrecognized " +
                        "option for event dispatcher");
                }
            }
        }
        catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Value of event dispatcher " +
                "option is missing");
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port value must be an " +
                "integer between 1025 and 65535");
        }

        strategy.configure(typeParam);

        if (seqTrace == null) {
            TraceHandler trHandler;

            if (objectType == TraceObjectType.BRANCH_EDGE) {
                JUnitBranchCoverageProcessingStrategy brCovStrategy =
                    (JUnitBranchCoverageProcessingStrategy) strategy;
                trHandler = (TraceHandler)
                    brCovStrategy.getCoverageListenerManager();
                trHandler.setTypeFlags(brCovStrategy.getTypeFlags());
            }
            else {
                JUnitBlockCoverageProcessingStrategy blkCovStrategy =
                    (JUnitBlockCoverageProcessingStrategy) strategy;
                trHandler = (TraceHandler)
                    blkCovStrategy.getCoverageListenerManager();
                trHandler.setTypeFlags(blkCovStrategy.getTypeFlags());
            }

            trHandler.setObjectType(objectType);

            if (trName != null) {
                trHandler.setTraceFileName(trName);
            }
            trHandler.setAppending(appendToTrace);
            fPrinter.setTraceFileWriter(new TraceFileWriter(trHandler));
        }
        else {
            if (trName != null) {
                seqTrace.setTraceFileName(trName);
            }
            seqTrace.setAppending(appendToTrace);
            seqTrace.useRelaySocket(usingRelaySocket);
            seqTrace.setPreData(preData);
            seqTrace.setPostData(postData);
            fPrinter.setTraceFileWriter(new TraceFileWriter(seqTrace));
        }
    }

    /**
     * Parses the list of selected test numbers and ranges and initializes
     * the array used for selection.
     *
     * @param selectionString Comma-delimited string of test selections.
     *
     * @throws Exception If the selection string cannot be parsed, typically
     * because non-numeric data was encountered.
     */
    private void getSelected(String selectionString) throws Exception {
        StringTokenizer strtok = new StringTokenizer(selectionString, ",");
        LinkedList tests = new LinkedList();
        int numSelected = 0;
        int dashPos = -1, rangeStart, rangeEnd;
        String token = null;

        while (strtok.hasMoreTokens()) {
            token = strtok.nextToken();
            if ((dashPos = token.indexOf("-")) == -1) {
                numSelected += 1;
                // Range self-to-self (simplifies things later)
                tests.add(new Integer(token));
                tests.add(new Integer(token));
            }
            else {
                if (token.startsWith("-") || token.endsWith("-")) {
                    throw new Exception("Incomplete range: '" + token + "'");
                }
                else {
                    rangeStart = Integer.parseInt(token.substring(0, dashPos));
                    rangeEnd = Integer.parseInt(token.substring(dashPos + 1, token.length()));
                    numSelected += Math.abs(rangeEnd - rangeStart) + 1;
                    // Range start-to-end
                    tests.add(new Integer(rangeStart));
                    tests.add(new Integer(rangeEnd));
                }
            }
        }

        this.selectedIDs = new int[numSelected];
        int n = 0;
        for (ListIterator li = tests.listIterator(); li.hasNext(); ) {
            // List should always be even-length, with every two elements
            // specifiying a range
            rangeStart = ((Integer) li.next()).intValue();
            rangeEnd = ((Integer) li.next()).intValue();
            if (rangeStart <= rangeEnd) {
                for (int i = rangeStart; i <= rangeEnd; i++) { // Inclusive
                    selectedIDs[n++] = i;
                }
            }
            else {
                for (int i = rangeStart; i >= rangeEnd; i--) { // Inclusive
                    selectedIDs[n++] = i;
                }
            }
        }
    }

    /**
     * Parses the list of selected test names and initializes the
     * array used for selection.
     *
     * @param selectionString Comma-delimited string of test names. Names
     * must include full package and class qualifiers to prevent
     * ambiguity.
     */
    private void getNames(String selectionString) {
        StringTokenizer strtok = new StringTokenizer(selectionString, ",");
        selectedNames = new String[strtok.countTokens()];
        int n = 0;
        while (strtok.hasMoreTokens()) {
            selectedNames[n++] = strtok.nextToken();
        }
    }

    /**
     * Reads the selected tests from standard input. Tests may only be
     * selected by ID, and one ID per line is expected. Ranges are not
     * supported (all IDs in the range must be specified, one per line).
     */
    private void getSelectedFromStdin() {
        LinkedList selected = new LinkedList();
        String line;
        try {
            while ((line = stdin.readLine()) != null) {
                selected.add(line);
            }
            selectedIDs = new int[selected.size()];
            int i = 0;
            for (ListIterator li = selected.listIterator(); li.hasNext(); i++) {
                selectedIDs[i] = Integer.parseInt((String) li.next());
            }
        }
        catch (NumberFormatException e) {
            NumberFormatException re = new NumberFormatException("Non-numeric or " +
                "malformed test selection data read from stdin: " + e.getMessage());
            throw (NumberFormatException) re.fillInStackTrace();
        }
        catch (IOException e) {
            throw (RuntimeException) (new RuntimeException("Nested exception: " + e))
                                      .fillInStackTrace();
        }
    }
}
