package junit.extensions;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import junit.framework.AssertionFailedError;
import junit.framework.SelectionTestListener;
import junit.framework.Test;

/**
 * Generates a universe file that can be used to run the same set of
 * tests one at a time (a less efficient method). Order of execution
 * will be preserved when the test runner is run in prioritizing mode.
 *
 * @author Alex Kinneer
 * @version 06/04/2004
 */
public class UniverseFileGenerator implements SelectionTestListener {
	
    /** Writer used to generate the file. */
    private PrintWriter pw;
    
    /** Current test number. */
    private int testNumber = 1;
    
    /** Name of the test suite from which test selections are occurring. */
    private String suiteName = null;
    
    /** Flag which specifies whether the test case granularity is method level
        or class level. Method level is used by default. */
    private boolean classLvl = false;
    
    /**
     * Creates a universe file generator which will write to the given
     * file.
     *
     * <strong>Note:</strong> You <i>must</i> call
     * {@link UniverseFileGenerator#setSuiteName} before adding this
     * object as a listener to a test result or initiating execution
     * of the test suite, otherwise an
     * <code>IllegalStateException</code> will be thrown.
     *
     * @param universeFile Name of the universe file to be created.
     *
     * @throws IOException If there is an error creating the universe
     * file (it is aggressively created).
     */
    public UniverseFileGenerator(String universeFile) throws IOException {
        pw = new PrintWriter(new BufferedWriter(new FileWriter(universeFile)));
        writeHeader();
    }
    
    /**
     * Writes the universe file header. Currently this is just the
     * environment variable set command to set the classpath necessary
     * to execute the test suite.
     */
    private void writeHeader() {
        // Whatever classpath is working for us now will work in the universe file
        pw.println("CLASSPATH=" + System.getProperty("java.class.path"));
        if (pw.checkError()) {
            System.err.println("WARNING: Error writing universe file header");
        }
    }
    
    /**
     * Each time a test is started, write a line to the universe file
     * that will cause the same test to be executed.
     *
     * @param test Test that is about to be run.
     */
    public void startTest(Test test) {
        if (classLvl) return;
        
        if (suiteName == null) {
            throw new IllegalStateException("Test suite name is not set");
        }
        pw.println("-P [" + suiteName + " -sID " + testNumber + "]");
        if (pw.checkError()) {
            System.err.println("WARNING: Error writing universe file entry");
        }
    }
    
    /**
     * Each time a test class is started, write a line to the universe file
     * that will cause the same test class to be executed.
     *
     * @param test Test class that is about to be run.
     */
    public void startTestClass(Test testClass) {
        if (suiteName == null) {
            throw new IllegalStateException("Test suite name is not set");
        }
        pw.println("-P [" + suiteName + " -sID " + testNumber + " -classlvl]");
        if (pw.checkError()) {
            System.err.println("WARNING: Error writing universe file entry");
        }
    }

    /**
     * Notifies the file generator that the test suite is
     * finished. The file will be closed and resources are
     * released. The test number is also reset to 1.
     */
    public void finish() {
        pw.close();
        pw = null;
        testNumber = 1;
    }

    /**
     * Sets the name of the test suite that is being run. This is
     * required to create the proper invocation information in
     * the universe file.
     *
     * @param name Name of the test suite, including package
     * qualifiers. This should be the same as the argument passed
     * into the test runner.
     */
    public void setSuiteName(String name) {
        suiteName = name;
    }
    
    /**
     * Gets the name of the test suite that is being run.
     *
     * @return Name of the test suite, including package
     * qualifiers.
     */
    public String getSuiteName() {
        return suiteName;
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

    // Null implementation of remainder of TestListener interface
    public void addError(Test test, Throwable t) { }
    public void addFailure(Test test, AssertionFailedError t) { }  
    public void endTest(Test test) { } 
    
    // Implementation of the SelectionTestListener interface
    public void decrementTestNumber() { testNumber--; }
    public void incrementTestNumber() { testNumber++; }
    public void setTestNumber(int number) { testNumber = number; }
    public int getTestNumber() { return testNumber; }
    public void endTestClass() { }
}
