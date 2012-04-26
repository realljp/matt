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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.DecimalFormat;

import sofya.base.exceptions.MethodNotFoundException;
import sofya.base.exceptions.SofyaError;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;

/**
 * The TestHistoryViewer is used to display the contents of a test history
 * file in human-readable form.
 *
 * <p>Usage:<br>
 * <code>java sofya.viewers.TestHistoryViewer &lt;histfile&gt;
 * [COUNT|LIST] [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[COUNT|LIST] : Format of the output
 * to be displayed<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] : Redirect output of
 * viewer to <i>OutputFile</i><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<i>histfile</i> must include any necessary
 * path information, relative or absolute.)</code></p>
 *
 * <p>This class is modeled upon the <code>th_printer</code> tool available
 * in the Aristotle system.</p>
 *
 * @author Alex Kinneer
 * @version 09/23/2003
 */
public class TestHistoryViewer extends Viewer {
    /** Handler for the test history file.*/
    private TestHistoryHandler thHandler = new TestHistoryHandler(); 
    /** Flag indicating whether the output is to be in list or count form. */
    private boolean count = false;
    
    /*************************************************************************
     * Standard constructor, creates a TestHistoryViewer to display the
     * formatted contents of the specified test history file to the system
     * console (<code>System.out</code>).
     *
     * @param inputFile Name of the test history file to be displayed.
     * @param className Name of the class for which the test history was
     * constructed, include <code>.java</code> extension.
     * @param count If <code>true</code>, the viewer will print a count of
     * the number of tests, otherwise the standard list is printed.
     */ 
    public TestHistoryViewer(String inputFile, String className,
                             boolean count) {
        super(inputFile);
        this.count = count;
    }

    /*************************************************************************
     * Standard constructor, creates a TestHistoryViewer to display the
     * formatted contents of the specified test history file to the specified
     * output file.
     *
     * @param inputFile Name of the test history file to be displayed.
     * @param className Name of the class for which the test history was
     * constructed, include <code>.java</code> extension.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     * @param count If <code>true</code>, the viewer will print a count of
     * the number of tests, otherwise the standard list is printed.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public TestHistoryViewer(String inputFile, String className,
                             String outputFile, boolean count)
                             throws SameFileNameException,
                                    IOException {
        super(inputFile, outputFile);
        this.count = count;
    }

    /*************************************************************************
     * Standard constructor, creates a TestHistoryViewer to display the
     * formatted contents of the specified test history file to the specified
     * output stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the test history file to be displayed.
     * @param className Name of the class for which the test history was
     * constructed, include <code>.java</code> extension.
     * @param stream Stream to which the viewer output should be written.
     * @param count If <code>true</code>, the viewer will print a count of
     * the number of tests, otherwise the standard list is printed.
     */ 
    public TestHistoryViewer(String inputFile, String className,
                             OutputStream stream, boolean count) {
        super(inputFile, stream);
        //this.className = className;
        this.count = count;
    }
    
    /*************************************************************************
     * Prints the test history information to the specified stream.
     *
     * @param stream Stream to which the test history information should be
     * written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        thHandler.readTestHistoryFile(inputFile);
        
        stream.println("Test history information for tests in file " +
            inputFile + LINE_SEP + "Trace type: " + thHandler.getTypeString());
        
        String methods[] = thHandler.getMethodList();
        if (count) {
            for (int i = 0; i < methods.length; i++) {
                printCountInfo(methods[i], stream);
            }
        }
        else {
            for (int i = 0; i < methods.length; i++) {
                printListInfo(methods[i], stream);
            }
        }
    }
    
    /*************************************************************************
     * Writes readable representation of the test history information for a
     * method, in list form. Each block and the tests that exercise it will
     * be printed.
     *
     * @param methodName Name of the method whose associated test history
     * information is to be printed.
     * @param stream Stream to which formatted output is to be written.
     */
    private void printListInfo(String methodName, PrintWriter stream) {
        final int COL_MAX = 80;
        int column;
        StringBuffer sb = new StringBuffer();
        
        // Procedure header
        stream.println();
        printMethodName(methodName, stream);
        stream.println();
        stream.println(" block  tests that hit block");
        stream.println("---------------------------------------" +
            "-----------------------------------------");
        
        // Blocks and their tests
        TestHistory th = null;
        try {
            th = thHandler.getTestHistory(methodName);
        }
        catch (MethodNotFoundException e) {
            throw new SofyaError("TestHistoryHandler falsely claimed to " +
                "have a test history for " + methodName);
        }
        int maxTestWidth = String.valueOf(th.getHighestTestID()).length();
        for (int blockID = 1; blockID <= th.getHighestBlockID(); blockID++) {
            sb.append(rightJust(blockID, 5) + "   ");
            column = 8;
            for (int testID = 0; testID <= th.getHighestTestID(); testID++) {
                if (th.query(blockID, testID)) {
                    column += maxTestWidth + 1;
                    if (column > COL_MAX) {
                        column = maxTestWidth + 9;
                        sb.append(LINE_SEP + "        ");
                    }
                    sb.append(testID + 1);
                    for (int i = 1 + (maxTestWidth -
                            String.valueOf(testID + 1).length()); i > 0; i--) {
                        sb.append(" ");
                    }
                }
            }
            stream.println(sb.toString());
            sb.delete(0, sb.length());
        }
    }
    
    /*************************************************************************
     * Writes readable representation of the test history information for a
     * method, in count form. Each block will be printed with the number
     * of tests that exercise it and the correpsonding percentage of all
     * tests that exercise it.
     *
     * @param methodName Name of the method whose associated test history
     * information is to be printed.
     * @param stream Stream to which formatted output is to be written.
     */
    private void printCountInfo(String methodName, PrintWriter stream) {
        double percent = 0.0;
        DecimalFormat dFormat = new DecimalFormat("###0.00");
        StringBuffer sb = new StringBuffer();

        // Get the test history
        TestHistory th = null;
        try {
            th = thHandler.getTestHistory(methodName);
        }
        catch (MethodNotFoundException e) {
            throw new SofyaError("TestHistoryHandler falsely claimed to " +
                "have a test history for " + methodName);
        }
        int maxTestID = th.getHighestTestID();

        // Procedure header
        stream.println();
        printMethodName(methodName, stream);
        stream.println("Number of tests: " + maxTestID);
        stream.println();
        stream.println(" object    number of tests that hit    percentage " +
            "that hit");
        stream.println("----------------------------------------" +
            "----------------------------------------");
        
        for (int blockID = 1; blockID <= th.getHighestBlockID(); blockID++) {
            int testCount = 0;
            for (int testID = 0; testID <= maxTestID; testID++) {
                if (th.query(blockID, testID)) {
                    testCount++;
                }
                if (maxTestID == 0) {
                    percent = 100.0;
                }
                else {
                    percent =
                        ((double) testCount / (double) maxTestID) * 100.0;
                }
            }
            sb.append(rightJust(blockID, 5) + "        " +
                      rightJust(testCount, 10) + "                       ");
            for (int i = 6 - dFormat.format(percent).length(); i > 0; i--) {
                sb.append(" ");
            }
            sb.append(dFormat.format(percent));
            stream.println(sb.toString());
            sb.delete(0, sb.length());
        }
    }

    /*************************************************************************
     * Prints the TestHistoryViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.TestHistoryViewer " +
                           "<history_file> [COUNT|LIST] [output_file]");
                   //"<history_file> <prog_name> [COUNT|LIST] [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for TestHistoryViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length > 3) {
            printUsage();
        }
        String histFile = null, progName = "", outputFile = null;
        boolean isCount = false;

        try {
            if (argv.length < 1) {
                System.out.print("Enter test history file name: ");
                histFile = stdin.readLine();
            }
            else {
                histFile = argv[0];
            }
            /*if (argv.length < 2) {
                System.out.print("Enter name of tested class: ");
                progName = stdin.readLine();
            }
            else {
                progName = argv[1];
            }*/
            if (argv.length >= 2) {
                if (argv[1].equals("COUNT")) {
                    isCount = true;
                }
                else if (!argv[1].equals("LIST")) {
                    outputFile = argv[1];
                }
            }
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        try {
            TestHistoryViewer thView =
                new TestHistoryViewer(histFile, progName, isCount);
            if (outputFile != null) {
                thView.setOutputFile(outputFile);
            }
            else if (argv.length == 3) {
                thView.setOutputFile(argv[2]);
            }
            thView.print();
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            System.err.println(e.getMessage()) ;
            System.exit(1);
        }
    }
}
