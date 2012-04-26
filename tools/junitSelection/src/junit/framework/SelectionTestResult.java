package junit.framework;

import java.util.Arrays;
import java.util.Vector;
import java.util.HashMap;
import java.util.Enumeration;

/**
 * A test result which imposes a numbered ordering on the tests which it is asked to
 * run and provides services for selecting and prioritizing (changing the order)
 * running of tests.
 *
 * @author Alex Kinneer
 */
public class SelectionTestResult extends TestResult {
	
    /** Current test number. */
    private int testNumber = 1;
    
    /** Array used to select tests by ID. If <code>null</code>,
        tests are selected by name. */
    private int[] selectedIDs = null;
    
    /** Array used to select tests by name. If <code>null</code>,
        tests are selected by ID. */
    private String[] selectedNames = null;
    
    /** Flag indicating that test names and IDs should only be
        displayed, not run. */
    private boolean displayOnly;
    
    /** Flag indicating that the test result should count the
        number of tests in the suite and not run the tests. */
    private boolean countOnly;
    
    /** Flag indicating that tests are being prioritized. */
    private boolean prioritizing;
    
    /** Array which maintains the prioritized order in which
        tests are to be run, by ID. */
    private int[] prioritizedIDs = null;
    
    /** Array which maintains the prioritized order in which
        tests are to be run, by name. */
    private String[] prioritizedNames = null;
    
    /** Map which keys tests on their ID, for use during
        prioritized runs. */
    private HashMap prioritizedTests = null;
    
    /** Map which keys test names on their IDs, for use during
        prioritized runs when selecting by name (all test names
        must be unique). */
    private HashMap nameNumberMap = null;
    
    /** Flag which specifies whether the test case granularity is method level
        or class level. Method level is used by default. */
    private boolean classLvl = false;
    
    /** Number of the last test method in a test class, used to trigger
        end-of-class events at the appropriate times when the test case
        granularity is set to classes. */
    private int classLastTest = 1;
    
    /** Current test class number, when the test case granularity is set to
        classes. */
    private int clTestNumber = 1;
    
    /**
     * A selecting test result with no criteria for selection isn't very useful.
     */
    protected SelectionTestResult() { }

    /**
     * Creates a selection test result that selects tests by number.
     *
     * @param selectedIDs Array which contains the selected test numbers.
     * @param displayOnly If <code>true</code>, the names of all the tests in the test suite
     * will be printed and no tests are run. Otherwise, tests are selected and run normally.
     * @param prioritizing If <code>true</code>, the selected tests are collected. A call
     * to {@link SelectionTestResult#runPrioritized()} will then run them in the
     * order in which they appear in <code>selectedIDs</code>.
     */
    public SelectionTestResult(int[] selectedIDs, boolean displayOnly, boolean countOnly, boolean prioritizing) {
        super();
        if (displayOnly || countOnly) {
            this.displayOnly = displayOnly;
            this.countOnly = countOnly;
        }
        else {
            this.selectedIDs = selectedIDs;
            if (prioritizing) {
                this.prioritizing = prioritizing;
                // Copy the list in original order since we need to sort for
                // the first pass (to find the tests). We'll use the original
                // order on the second pass.
                prioritizedIDs = new int[selectedIDs.length];
                System.arraycopy(selectedIDs, 0, prioritizedIDs, 0, selectedIDs.length);
                prioritizedTests = new HashMap();
            }
            Arrays.sort(this.selectedIDs);  // So we can use binary search
        }
    }
    
    /**
     * Creates a selection test result that selects tests by name.
     *
     * @param selectedIDs Array which contains the selected test names.
     * @param displayOnly If <code>true</code>, the names of all the tests in the test suite
     * will be printed and no tests are run. Otherwise, tests are selected and run normally.
     * @param prioritizing If <code>true</code>, the selected tests are collected. A call
     * to {@link SelectionTestResult#runPrioritized()} will then run them in the
     * order in which they appear in <code>selectedNames</code>.
     */
    public SelectionTestResult(String[] selectedNames, boolean displayOnly, boolean countOnly, boolean prioritizing) {
        super();
        if (displayOnly || countOnly) {
            this.displayOnly = displayOnly;
            this.countOnly = countOnly;
        }
        else {
            this.selectedNames = selectedNames;
            if (prioritizing) {
                this.prioritizing = prioritizing;
                prioritizedNames = new String[selectedNames.length];
                System.arraycopy(selectedNames, 0, prioritizedNames, 0, selectedNames.length);
                prioritizedTests = new HashMap();
                nameNumberMap = new HashMap();
            }
            Arrays.sort(this.selectedNames);  // So we can use binary search
        }
    }
    
    /**
     * Sets whether the test case granularity is classes or methods.
     *
     * @param enable <code>true</code> to set the granularity level to
     * classes, <code>false</code> to set the granularity level to
     * methods.
     */
    public void setLevelClass(boolean enable) {
        classLvl = enable;
    }
    
    /**
     * Reports whether the test case granularity is classes or methods.
     *
     * @returns <code>true</code> if the granularity level is classes,
     * <code>false</code> if the granularity level is methods.
     */
    public boolean isLevelClass() {
        return classLvl;
    }
    
    /**
     * Runs a test case if it is selected, and no other filtering
     * conditions apply.
     */
    protected void run(final TestCase test) {
        if (countOnly) {
            incrementTestNumber();
            return;
        }
        else if (displayOnly) {
            // Activated by the '-names' parameter to the FilterTestRunner - the
            // test name is printed and the test is not run
            System.out.println(testNumber + ":\t" + test.getClass().getName() +
                               "." + test.getName());
            incrementTestNumber();
            return;
        }
        
        if (!classLvl) {
            // Array is assumed to be sorted.
            if (selectedIDs == null) {  // Search by name
                if ((Arrays.binarySearch(selectedNames,
                        test.getClass().getName() + "." + test.getName())) >= 0) {
                    if (prioritizing) {
                        nameNumberMap.put(test.getClass().getName() + "." + test.getName(), new Integer(testNumber));
                        prioritizedTests.put(new Integer(testNumber), test);
                    }
                    else {
                        super.run(test);
                    }
                }
            }
            else {  // Search by test number
                // The first condition is a short-circuit: if the current test
                // number is greater than the last element of the selectedIDs
                // tests array, we know it can't be selected.
                if ((testNumber <= selectedIDs[selectedIDs.length - 1]) &&
                        ((Arrays.binarySearch(selectedIDs, testNumber) >= 0))) {
                    if (prioritizing) {
                        prioritizedTests.put(new Integer(testNumber), test);
                    }
                    else {
                        super.run(test);
                    }
                }
            }
        }
        else {
            super.run(test);
        }
        
        incrementTestNumber();
    }
    
    /**
     * Runs the selected tests in the prioritized order, which is taken to be the order
     * in which tests were listed in the array passed to the constructor.
     *
     * <p>When running in prioritization mode (enabled by setting the
     * <code>prioritizing</code> parameter in the constructor to <code>true</code>),
     * calls to the {@link SelectionTestResult#run(TestCase)} method do not run the
     * test, but rather check whether it is selected and store a reference to it if true.
     * The runner can then call this method to run the tests in the prioritized order.</p>
     *
     * <p>The two passes are required because the design of JUnit is such that we must
     * walk through all the tests first to discover the selected tests. Only then are we
     * able to run them in arbitrary order.</p> 
     */
    public void runPrioritized() {
        if (!classLvl) {
            if (prioritizedIDs == null) {  //  Selections by name
                Integer testNumberKey;
                for (int i = 0; i < prioritizedNames.length; i++) {
                    testNumberKey = (Integer) nameNumberMap.get(prioritizedNames[i]);
                    if (testNumberKey == null) {
                        System.err.println("WARNING: test '" + prioritizedNames[i] + "' not found");
                        continue;
                    }
                    setTestNumber(testNumberKey.intValue());
                    super.run((TestCase) prioritizedTests.get(testNumberKey));
                }
            }
            else {  // Selections by number
                for (int i = 0; i < prioritizedIDs.length; i++) {
                    if (!prioritizedTests.containsKey(new Integer(prioritizedIDs[i]))) {
                        System.err.println("WARNING: test " + prioritizedIDs[i] + " not found");
                        continue;
                    }
                    setTestNumber(prioritizedIDs[i]);
                    super.run((TestCase) prioritizedTests.get(new Integer(prioritizedIDs[i])));
                }
            }
        }
        else {
            if (prioritizedIDs == null) {  //  Selections by name
                Integer classNumberKey;
                for (int i = 0; i < prioritizedNames.length; i++) {
                    classNumberKey =
                        (Integer) nameNumberMap.get(prioritizedNames[i]);
                    if (classNumberKey == null) {
                        System.err.println("WARNING: test '" +
                            prioritizedNames[i] + "' not found");
                        continue;
                    }
                    setClassNumber(classNumberKey.intValue());
                    TestSuite ts =
                        (TestSuite) prioritizedTests.get(classNumberKey);
                    startTestClass(ts);
                    ts.run(this);
                }
            }
            else {  // Selections by number
                for (int i = 0; i < prioritizedIDs.length; i++) {
                    if (!prioritizedTests.containsKey(
                            new Integer(prioritizedIDs[i]))) {
                        System.err.println("WARNING: test " +
                            prioritizedIDs[i] + " not found");
                        continue;
                    }
                    setClassNumber(prioritizedIDs[i]);
                    TestSuite ts = (TestSuite) prioritizedTests.get(
                        new Integer(prioritizedIDs[i]));
                    startTestClass(ts);
                    ts.run(this);
                }
            }
        }
    }
    
    /**
     * Gets the number of the last test that was run.
     *
     * @return The test number of the last test that was run.
     */
    public int getTestNumber() {
        return testNumber - 1;
    }

    /**
     * Increments the test number and issues a callback to any listeners
     * instructing them to do likewise.
     */
    private void incrementTestNumber() {
        testNumber++;
        
        if (classLvl) {
            // The classLastTest is the number of the first test method in the
            // class offset by the number of tests in the class. Thus when we
            // reach this value, the class is finished and we can fire an event
            // to signal this. Having this event greatly simplifies things for
            // listeners.
            if (testNumber == classLastTest) {
                endTestClass();
            }
        }
        else {
            Enumeration e = cloneListeners().elements();
            while (e.hasMoreElements()) {
                ((SelectionTestListener) e.nextElement()).incrementTestNumber();
            }
        }
    }

    /**
     * Sets the test number and issues a callback to any listeners
     * instructing them to do likewise.
     *
     * <p>This method is used when prioritizing, since tests may be run in
     * arbitary order.</p>
     *
     * @param num Number which is to be set as the current test.
     */
    private void setTestNumber(int num) {
        testNumber = num;
        for (Enumeration e= cloneListeners().elements(); e.hasMoreElements(); ) {
            ((SelectionTestListener) e.nextElement()).setTestNumber(num);
        }
    }

    /**
     * Checks whether a test class should be run. This will be false if the
     * test class is not selected or we are on the first pass of a prioritized
     * run.
     *
     * @param testClass Test class which is being checked. This should always
     * be a TestSuite.
     *
     * @return <code>true</code> If the class is to be run immediately.
     */
    boolean getRunClassNow(Test testClass) {
        // Array is assumed to be sorted.
        if (selectedIDs == null) {  // Search by name
            String className = ((TestSuite) testClass).getName();
            if ((Arrays.binarySearch(selectedNames, className)) >= 0) {
                if (prioritizing) {
                    nameNumberMap.put(className, new Integer(clTestNumber));
                    prioritizedTests.put(new Integer(clTestNumber), testClass);
                    return false;
                }
                else {
                    return true;
                }
            }
        }
        else {  // Search by test number
            // The first condition is a short-circuit: if the current test
            // number is greater than the last element of the selectedIDs
            // tests array, we know it can't be selected.
            if ((clTestNumber <= selectedIDs[selectedIDs.length - 1]) &&
                    ((Arrays.binarySearch(selectedIDs, clTestNumber) >= 0))) {
                if (prioritizing) {
                    prioritizedTests.put(new Integer(clTestNumber), testClass);
                    return false;
                }
                else {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Event signaling the beginning of execution of a test class. This event
     * is only fired when the test case granularity is set to classes.
     *
     * @param testClass Test class which is about to run.
     */
    void startTestClass(Test testClass) {
        if (!classLvl) return;
        
        classLastTest = testNumber + testClass.countTestCases() - 1;
        
        Enumeration e = cloneListeners().elements();
        while (e.hasMoreElements()) {
            ((SelectionTestListener) e.nextElement())
                .startTestClass(testClass);
        }
    }
    
    /**
     * Event signaling the completion of a test class execution. This event
     * is only fired when the test case granularity is set to classes.
     */
    void endTestClass() {
        if (!classLvl) return;
        
        Enumeration e = cloneListeners().elements();
        while (e.hasMoreElements()) {
            ((SelectionTestListener) e.nextElement()).endTestClass();
        }
    }
    
    /**
     * Requests that the test class number be incremented. This is mapped into
     * an equivalent stepping of the test case number for listeners.
     */
    void incrementClassNumber() {
        clTestNumber++;
         
        Enumeration e = cloneListeners().elements();
        while (e.hasMoreElements()) {
            ((SelectionTestListener) e.nextElement()).incrementTestNumber();
        }
    }

    /**
     * Requests that the test class number be set to the given value. This is
     * mapped into an equivalent test case number change for listeners.
     */
    void setClassNumber(int num) {
        clTestNumber = num;
         
        Enumeration e = cloneListeners().elements();
        while (e.hasMoreElements()) {
            ((SelectionTestListener) e.nextElement()).setTestNumber(num);
        }
    }

    /**
     * Returns a copy of the listeners.
     */
    private synchronized Vector cloneListeners() {
    	Vector listener = new Vector(fListeners);
    	return (Vector)listener.clone();
    }
}
