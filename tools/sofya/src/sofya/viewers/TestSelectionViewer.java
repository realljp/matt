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

package sofya.viewers;

import java.io.*;
import java.util.*;

import sofya.base.exceptions.*;
import sofya.apps.dejavu.TestSelectionHandler;

import gnu.trove.TIntHashSet;

/**
 * The TestSelectionViewer is used to display the list of selected
 * tests in a human readable format, or as a list for further processing.
 *
 * <p>Usage:<br>
 * <code>java sofya.viewers.TestSelectionViewer &lt;test_file&gt;
 * [-t <i>t|l|s</i>] [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-t <i>t|l|s</i> : Format of the output.<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;OutputFile : Redirect output of viewer
 * to <i>OutputFile</i>
 * </code></p>
 * 
 * @author Rogan Creswick
 * @author Alex Kinneer
 * @version 07/20/2005
 */
public class TestSelectionViewer extends Viewer {
    /** Constant flag for tabular format. **/
    public static final int TABULAR = 0;
    /** Constant flag for statistical format. **/
    public static final int STATISTICAL = 1;
    /** Constant flag for list format. **/
    public static final int LIST = 2;

    /** Handler for the test selection file. **/
    private TestSelectionHandler tsHandler = new TestSelectionHandler();

    /** Output format specifier **/
    private int format = TABULAR;
    
    /** Flag specifying whether headers should be printed for methods that
        did not contribute any selected tests. */
    private boolean verbose = false;
    
    /** Maximum number of digits in a test ID, used to control spacing. */
    private static int MAX_TESTID_LENGTH = 4;  // Up to 9999 tests
    
    /**********************************************************************
     * Standard constructor, creates a TestSelectionViewer to display
     * the selected tests in the specified format.
     * 
     * @param inputFile Name of the test selection file to be displayed.
     * @param format Output format to be used. Should be one of the
     * following:<br><code>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.TABULAR<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.STATISTICAL<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.LIST</code>
     */
    public TestSelectionViewer(String inputFile, int format) {
        super(inputFile);
        setOutputFormat(format);
    }
    
    /**********************************************************************
     * Standard constructor, creates a TestSelectionViewer to display
     * the selected tests in the specified format to the specified output
     * file.
     * 
     * @param inputFile Name of the test selection file to be displayed.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     * @param format Output format to be used. Should be one of the
     * following:<br><code>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.TABULAR<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.STATISTICAL<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.LIST</code>
     */
    public TestSelectionViewer(String inputFile, String outputFile, int format)
                               throws SameFileNameException, IOException {
        super(inputFile, outputFile);
        setOutputFormat(format);
    }
    
    /**********************************************************************
     * Standard constructor, creates a TestSelectionViewer to display
     * the selected tests in the specified format to the specified output
     * stream.
     * 
     * @param inputFile Name of the test selection file to be displayed.
     * @param stream Stream to which the viewer output should be written.
     * @param format Output format to be used. Should be one of the
     * following:<br><code>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.TABULAR<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.STATISTICAL<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.LIST</code>
     */
    public TestSelectionViewer(String inputFile, OutputStream stream,
                               int format) {
        super(inputFile, stream);
        setOutputFormat(format);
    }

    /**********************************************************************
     * Standard constructor, creates a TestSelectionViewer to display
     * the selected tests in the specified format.
     * 
     * <p>The TestSelectionHandler passed to this constructor must have
     * useful test selection data available prior to calling a print
     * method.</p>
     *
     * @param tsHandler TestSelectionHandler for the file of interest.
     */
    public TestSelectionViewer(TestSelectionHandler tsHandler) {
        super();
        this.inputFile = null;
        this.tsHandler = tsHandler;
    }

    /*************************************************************************
     * Sets the output format to be used.
     *
     * @param format Output format to be used. Should be one of the
     * following:<br><code>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.TABULAR<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.STATISTICAL<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;TestSelectionViewer.LIST</code>
     *
     * @throws IllegalArgumentException If the specified output format is
     * not recognized.
     */ 
    public void setOutputFormat(int format) {
        if ((format < 0) || (format > 2)) {
            throw new IllegalArgumentException("Invalid output format");
        }
        this.format = format;
    }
    
    /*************************************************************************
     * Gets the output format currently set to be used.
     *
     * @return An integer representing the currently specified output
     * format (see {@link TestSelectionViewer#setOutputFormat}).
     */ 
    public int getOutputFormat() {
        return format;
    }

    /*************************************************************************
     * Sets whether headers should be printed for methods that
     * did not contribute any selected tests.
     *
     * @param enable <code>true</code> to enable printing of headers for
     * non-contributing methods, <code>false</code> otherwise.
     */ 
    public void setVerbose(boolean enable) {
        this.verbose = enable;
    }
    
    /*************************************************************************
     * Gets whether headers are to be printed for methods that
     * did not contribute any selected tests.
     *
     * @return <code>true</code> if printing of headers for non-contributing
     * methods is enabled, <code>false</code> otherwise.
     */ 
    public boolean isVerbose() {
        return this.verbose;
    }
    
    /*************************************************************************
     * Prints the test selection information to the specified stream.
     *
     * @param stream Stream to which the test selection information should be
     * written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        if (inputFile != null) {
            // read the file:
            tsHandler.readTestSelectionFile(inputFile);
        }

        String methods[] = tsHandler.getMethodList();

        switch (format) {
            case TABULAR: 
                printTabular(methods, stream);
                break;
            case STATISTICAL: 
                printStatistical(methods, stream);
                break;
            case LIST :
                printList(methods, stream);
                break;
            default: 
                throw new IllegalArgumentException("Invalid output format");
        }
    }

    /*************************************************************************
     * Prints the regression test selection information in a tabular format.
     *
     * @param methods The array of method names for which we selected tests.
     * @param stream The output stream to print to.
     */
    private void printTabular(String methods[], PrintWriter stream) {
        final int COL_MAX = 80;
        TreeSet<Object> tests = new TreeSet<Object>();
        StringBuffer sb = new StringBuffer();
        
        stream.println("Test Selection Data");
        stream.println();

        int maxCount = COL_MAX / (MAX_TESTID_LENGTH + 2);
        int count;
        for (int i = 0; i < methods.length; i++) {
            int[] selectedTests;
             
            try {
                selectedTests = tsHandler.getSelectedTests(methods[i]);
            }
            catch (MethodNotFoundException shouldnotHappen) {
                throw new SofyaError("TestSelectionHandler falsely " +
                    "reported test selection data is available for " +
                    methods[i]);
            }
            
            if ((selectedTests.length == 0) && !verbose) {
                continue;
            }
            
            printMethodName(methods[i], stream);
            stream.println("----------------------------------------" +
                            "----------------------------------------");
            count = 0;
            for (int j = 0; j < selectedTests.length; j++) {
                // Add each test to the test set so we can count the
                // uniques easily later.
                tests.add(new Integer(selectedTests[j]));
                
                sb.append(rightJust(selectedTests[j], MAX_TESTID_LENGTH));
                sb.append("  ");
                count++;
                
                if (count == maxCount) {
                    stream.println(sb.toString());
                    sb.delete(0, sb.length());
                    count = 0;
                }
            }
            stream.println(sb.toString());
            sb.delete(0, sb.length());
            stream.println();
        }
        
        stream.println();
        stream.println("All Selected Tests");
        stream.println("----------------------------------------" +
                       "----------------------------------------");
                       
        Iterator iter = tests.iterator();
        count = 0;
        while(iter.hasNext()) {
            int testNum = ((Integer) iter.next()).intValue();
            
            sb.append(rightJust(testNum, MAX_TESTID_LENGTH));
            sb.append("  ");
            count++;
            if (count == maxCount) {
                stream.println(sb.toString());
                sb.delete(0, sb.length());
                count = 0;
                //throw new Error("reached maxCount: " + maxCount);
            }
        }
        stream.println(sb.toString());
        stream.println();
        stream.println("Total selected: " + tests.size());
        stream.println();
    }

    /*************************************************************************
     * Prints the regression test selection information in a statistical
     * format.
     *
     * @param methods The array of method names for which we selected tests.
     * @param stream The output stream to print to.
     */
    private void printStatistical(String methods[], PrintWriter stream) {
        TIntHashSet tests = new TIntHashSet();
        int total = tsHandler.getNumberOfTests();
        final String centerPad = "                    ";

        stream.println("Test Selection Statistics");
        stream.println();
        stream.println("Number of tests          Percentage of total ");
        stream.println("========================================" +
                       "========================================");
        stream.println();

        for (int i = 0; i < methods.length; i++) {
            int[] selectedTests;
             
            try {
                selectedTests = tsHandler.getSelectedTests(methods[i]);
            }
            catch (MethodNotFoundException shouldnotHappen) {
                throw new SofyaError("TestSelectionHandler falsely " +
                    "reported test selection data is available for " +
                    methods[i]);
            }
            
            if ((selectedTests.length == 0) && !verbose) {
                continue;
            }
            
            printMethodName(methods[i], stream);
            stream.println("----------------------------------------" +
                           "----------------------------------------");
            for (int j = 0; j < selectedTests.length; j++) {
                // Use the tree set to eliminate duplicates
                tests.add(selectedTests[j]);        
            }
            
            stream.println(" " + sizeString(Integer.toString(
                    selectedTests.length), MAX_TESTID_LENGTH) + centerPad +
                    100 * (1.0 * selectedTests.length / total));
            stream.println();
        }
        
        stream.println();
        stream.println("Totals");
        stream.println("========================================" +
                       "========================================");
        stream.println(" " + sizeString(Integer.toString(tests.size()),
            MAX_TESTID_LENGTH) + centerPad +
            100 * (1.0 * tests.size() / total));
        stream.println();
    }

    /*************************************************************************
     * Prints the selected tests as a list with no "fancy" formating.
     *
     * @param methods The array of method names for which we selected tests.
     * @param stream The output stream to print to.
     */
    private void printList(String methods[], PrintWriter stream) {
        // Use a tree set here, because we want the list sorted
        TreeSet<Object> tests = new TreeSet<Object>(); 

        for (int i = 0; i < methods.length; i++) {
            try {
                int[] selectedTests = tsHandler.getSelectedTests(methods[i]);
                for (int j = 0; j < selectedTests.length; j++){
                    // Use the set to eliminate duplicates
                    tests.add(new Integer(selectedTests[j]));        
                }
            }
            catch (MethodNotFoundException shouldnotHappen) {
                throw new SofyaError("TestSelectionHandler falsely " +
                    "reported test selection data is available for " +
                    methods[i]);
            }
        }
        
        Iterator iter = tests.iterator();
        while (iter.hasNext()) {
            stream.println(((Integer) iter.next()).intValue());
        }
    }

    /*************************************************************************
     * Prints the TestSelectionViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.TestSelectionViewer " +
                           "<test_selection_file> [-t <t|s|l>] [output_file]");
        System.exit(1);
    }

    /*************************************************************************
     *  Entry point for TestSelectionViewer.
     */
    public static void main(String argv[]) {
        String outputFile = null;
        int format = TABULAR;
        boolean verbose = false;

        if (argv.length < 1) {
            printUsage();
        }
        
        
        int argIndex = 1;
        
        for ( ; argIndex < argv.length; argIndex++) {
            if (argv[argIndex].equals("-t")) {
                argIndex += 1;
                if (argIndex == argv.length) {
                    printUsage();
                }
                if (argv[argIndex].equals("t")) {
                    format = TABULAR;
                }
                else if (argv[argIndex].equals("l")) {
                    format = LIST;
                }
                else if (argv[argIndex].equals("s")) {
                    format = STATISTICAL;
                }
                else {
                    System.out.println("Invalid output format");
                    printUsage();
                }
            }
            else if (argv[argIndex].equals("-v")) {
                verbose = true;
            }
            else if (!argv[argIndex].startsWith("-")) {
                break;
            }
        }

        if (argIndex < argv.length) {
            outputFile = argv[argIndex];
        }
        
        try {
            TestSelectionViewer tsViewer =
                new TestSelectionViewer(argv[0], format);
            if (outputFile != null) {
                tsViewer.setOutputFile(outputFile);
            }
            tsViewer.verbose = verbose;
            tsViewer.print();
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}