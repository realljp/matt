package junit.framework;

import java.util.Vector;
import java.util.Enumeration;

/**
 * Extension of TestSuite to enable test selection and prioritization at the
 * test class level.
 *
 * @author Alex Kinneer
 * @version 06/04/2004
 *
 * @see Test
 * @see TestSuite
 */
public class SelectionTestSuite extends TestSuite {
    protected Vector fTests = null;
    protected String fName;
    
    /**
     * Constructs a SelectionTestSuite from the given TestSuite.
     *
     * @param suite TestSuite on which test selection is going to be
     * performed.
     */
    public SelectionTestSuite(TestSuite suite) {
        fTests = new Vector(suite.testCount());
        for (Enumeration e = suite.tests(); e.hasMoreElements(); ) {
            fTests.add(e.nextElement());
        }
        fName = suite.getName();
    }

    /**
     * Runs the tests and collects their result in a TestResult. Performs
     * selection and prioritization on members of the test suite which
     * are test classes.
     */
    public void run(TestResult result) {
        for (Enumeration e = fTests.elements(); e.hasMoreElements(); ) {
            if (result.shouldStop()) {
                break;
            }
            Test test = (Test) e.nextElement();
            
            SelectionTestResult stResult = null;
            boolean runClass = true;
            if (test instanceof TestSuite) {
                stResult = (SelectionTestResult) result;
                runClass = stResult.getRunClassNow(test);
                if (runClass) {
                    stResult.startTestClass(test);
                }
            }
            
            if (runClass) {
                runTest(test, result);
            }
            
            if (stResult != null) {
                stResult.incrementClassNumber();
            }
        }
    }
}