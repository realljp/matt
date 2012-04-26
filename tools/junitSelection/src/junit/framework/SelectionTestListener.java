package junit.framework;

/**
 * Interface extension which indicates that a test listener provides mechanisms
 * to impose a numeric ordering on tests.
 */
public interface SelectionTestListener extends TestListener {
	
    /** Decrements the test number. */
    public void decrementTestNumber();
    
    /** Increments the test number. */
    public void incrementTestNumber();
    
    /** Sets the test number. */
    public void setTestNumber(int number);
    
    /** Gets the test number. */
    public int getTestNumber();
    
    /** Notifies that a new test class is starting. */
    public void startTestClass(Test testClass);
    
    /** Notifies that the current test class has finished. */
    public void endTestClass();
}
