import junit.framework.*;

/**
 * To compile this class, you must first set the classpath to point to
 * build/classes, build/testcases, and lib/junit3.7.jar
 */
public class Suite extends TestSuite {
    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        
        // JUnit 3.8.1 preferred method of building the comprehensive test suite
        suite.addTestSuite(BinStringTest.class);
        return suite;
    }
}
