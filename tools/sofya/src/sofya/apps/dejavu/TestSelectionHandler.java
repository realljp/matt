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

package sofya.apps.dejavu;

import java.io.*;
import java.util.*;

import sofya.base.Handler;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.*;

import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;

/**
 * The TestSelectionHandler provides methods to manipulate Sofya test
 * selection files and manage test selection information for methods
 * obtained from running the DejaVu tool.
 *
 * @author Alex Kinneer
 * @version 09/21/2004
 */
@SuppressWarnings("unchecked")
public class TestSelectionHandler extends Handler {
    /** Maps method names to their test selections. */
    private Map<Object, Object> selections = new THashMap();
    /** Number of tests from which test selection may occur. */
    private int numTests = -1;

    /**************************************************************************
     * Default constructor, creates a test selection handler with no test
     * selections yet registered and no test count set.
     *
     * <p><b>Note:</b> A handler created with this constructor should
     * only be used to read an existing test selection file. Any attempt
     * to add test selection information for a method or write a test
     * selection file prior to calling {@link #readTestSelectionFile} will
     * result in an <code>IllegalStateException</code> being thrown. To
     * create a new test selection file, {@link #TestSelectionHandler(int)}
     * should be used.</p>
     */
    public TestSelectionHandler() { }
    
    /**************************************************************************
     * Default constructor, creates a test selection handler with no test
     * selections yet registered and the specified number of tests from
     * which selection may occur.
     *
     * @param numTests Number of tests from which selection may occur.
     */
    public TestSelectionHandler(int numTests) {
        this.numTests = numTests;
    }

    /**************************************************************************
     * Writes a test selection file from the test selection information
     * currently registered with the handler.
     *
     * @param fileName Name of the test selection file to be written.
     *
     * @throws IllegalStateException If the number of tests has not yet been
     * set, either in the constructor or by {@link #readTestSelectionFile}.
     * @throws IOException If there is an I/O error creating or writing
     * the file.
     */
    public void writeTestSelectionFile(String fileName, String tag)
                throws IllegalStateException, IOException {
        if (numTests == -1) {
            throw new IllegalStateException("Number of tests has not " +
                                            "been set");
        }
        
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName, tag, false))), true);
    
        // Test selection header information
        pw.println("0 Test Selection Information");
        pw.println("0 File: " + fileName + "  Created: " +
                    new Date(System.currentTimeMillis()));
        pw.println("0 Version: " + ProjectDescription.versionString);
        pw.println("4 " + numTests);
    
        String[] methodNames = getMethodList();
        for (int i = 0; i < methodNames.length; i++) {
            writeSelectionData(pw, methodNames[i],
                               (BitSet) selections.get(methodNames[i]));
        }

        pw.close();
        if (pw.checkError()) {
            throw new IOException("Error writing test selection file");
        }
    }
    
    /**************************************************************************
     * Reads a test selection file making the information available for request
     * from the handler. The {@link #getSelectedTests} method can be used to
     * retrieve information loaded by this method.
     *
     * <p>If the handler was created using the no-argument constructor, this
     * method will set the number of tests, enabling the ability to add new
     * test selection information and write a modified test selection
     * file.</p>
     *
     * @param fileName Name of the test selection file to be read.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws BadFileFormatException If the specified file is not a test
     * selection file, or is otherwise malformed or corrupted.
     * @throws IOException If there is an I/O error reading the file.
     */
    public void readTestSelectionFile(String fileName)
                throws FileNotFoundException, BadFileFormatException,
                       IOException {
        selections.clear();
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                openInputFile(fileName)));
        
        try {
            String currentLine = "0";
            StringTokenizer stok = null;
            // Read through the header
            try {
                currentLine = readNextLine(br);
            }
            catch (EOFException e) {
                throw new BadFileFormatException("Test selection " +
                    "file is incomplete");
            }
            // Get the number of tests
            try {
                stok = new StringTokenizer(currentLine);
                if (!stok.nextToken().equals("4")) {
                    throw new BadFileFormatException("Test selection file " +
                        "is corrupted: number of tests value not found " +
                        "where expected");
                }
                try {
                    numTests = Integer.parseInt(stok.nextToken());
                }
                catch (NumberFormatException e) {
                    throw new BadFileFormatException("Test selection file " +
                        "is corrupted: number of tests is non-numeric");
                }
            }
            catch (NoSuchElementException e) {
                throw new BadFileFormatException("Test selection file is " +
                    "incomplete: number of tests is missing");
            }
        
            String methodName = null;
            StringBuffer tsVector = new StringBuffer();
            int numLines = 1 + (((numTests - 1) / 8) / 20);
            while (true) {
                // Look for method header
                try {
                    currentLine = readNextLine(br);
                }
                catch (EOFException e) { break; }

                try {
                    // Read method header information
                    stok = new StringTokenizer(currentLine);
                    if (!(stok.nextToken().startsWith("1"))) {
                        throw new BadFileFormatException("Test selection " +
                            "file is corrupted: method information not " +
                            "found where expected");
                    }
                    // Read method name using quotation marks as tokens
                    stok.nextToken("\"");
                    methodName = stok.nextToken();
                    stok.nextToken(" \t\n\r\f");
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("Test selection file " +
                        "is corrupted: method header is incomplete");
                }
            
                try {
                    tsVector.delete(0, tsVector.length());
                    for (int i = 0; i < numLines; i++) {
                        currentLine = readNextLine(br);
                        stok = new StringTokenizer(currentLine);
                        if (!stok.nextToken().startsWith("3")) {
                            throw new BadFileFormatException("Test " +
                                "selection file is corrupted: test " +
                                "selection data for method is missing");
                        }
                        while (stok.hasMoreTokens()) {
                            String str = stok.nextToken();
                            tsVector.append(str);
                        }
                    }
                    if (tsVector.toString().length() * 4 < numTests) {
                        throw new BadFileFormatException("Test selection " +
                            "file is corrupted: test selection data for " +
                            "method is incomplete");
                    }
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("Test selection file " +
                        "is corrupted: method test selection data " +
                        "is incomplete");
                }
                catch (EOFException e) {
                    throw new BadFileFormatException("Test selection file " +
                        "is corrupted: method test selection data " +
                        "is incomplete");
                }
                selections.put(methodName, toBinary(tsVector.toString()));
            }
        }
        finally {
            br.close();
        }
    }
    
    /**************************************************************************
     * Gets the list of methods names for which test selections are currently
     * registered with the handler.
     *
     * @return A list of the method names for which test selections can be
     * retrieved from this handler.
     */
    public String[] getMethodList() {
        String[] methodNames = (String[]) selections.keySet().toArray(
            new String[selections.size()]);
        Arrays.sort(methodNames);
        return methodNames;
    }
    
    /**************************************************************************
     * Reports whether test selection data exists for a given method.
     *
     * @param methodName Name of the method which the handler should
     * check for test selection data.
     *
     * @return <code>true</code> if test selection data is available for the
     * given method, <code>false</code> otherwise.
     */
    public boolean containsMethod(String methodName) {
        return selections.containsKey(methodName);
    }

    /**************************************************************************
     * Gets the tests selected for a given method.
     *
     * @param methodName Name of the method for which the selected tests are
     * to be retrieved.
     *
     * @return The list of tests selected for the given method.
     *
     * @throws MethodNotFoundException If the handler has no test selection
     * information associated with a method of the specified name.
     */
    public int[] getSelectedTests(String methodName)
                 throws MethodNotFoundException {
        if (selections.containsKey(methodName)) {
            BitSet selectionVector = (BitSet) selections.get(methodName);
            TIntArrayList selected = new TIntArrayList(selectionVector.size());
            for (int i = 0; i < selectionVector.size(); i++) {
                if (selectionVector.get(i)) {
                    selected.add(i + 1);
                }
            }
            return selected.toNativeArray();
        }
        else {
            throw new MethodNotFoundException(methodName);
        }
    }
    
    /**************************************************************************
     * Records the tests selected for a given method.
     *
     * <p>If test selection information already exists for the specified method,
     * it is overwritten. Otherwise the method and test selection information
     * are added to the set of methods registered with the handler.</p>
     *
     * @param methodName Name of the method for which test selection information
     * is being recorded.
     * @param selectedTests The selected tests to be associated with the method.
     *
     * @throws IllegalStateException If the number of tests has not yet been
     * set, either in the constructor or by {@link #readTestSelectionFile}.
     */
    public void setSelectedTests(String methodName, int[] selectedTests)
                throws IllegalStateException {
        if (numTests == -1) {
            throw new IllegalStateException("Number of tests has not " +
                                            "been set");
        }
        
        BitSet selectionVector = new BitSet(numTests);
        for (int i = 0; i < selectedTests.length; i++) {
            selectionVector.set(selectedTests[i] - 1);
        }
        selections.put(methodName, selectionVector);
    }
    
    /**************************************************************************
     * Gets the number of tests.
     *
     * @return The number of tests from which selection can occur. A value of
     * -1 indicates that the number of tests has not yet been set.
     */
    public int getNumberOfTests() {
        return numTests;
    }

    /**************************************************************************
     * Writes the test selection data for a method to the specified writer
     * stream.
     *
     * @param pw Writer stream to which test selection data will be written.
     * @param methodName Name of the method for which the test selection data
     * is being written.
     * @param tsData Bit vector containing the test selection data.
     */
    private void writeSelectionData(PrintWriter pw, String methodName,
                                    BitSet tsData)
                 throws IOException {
        String tsVector = null;
        StringBuffer outputLine = new StringBuffer();
        int j, k, vLength, extraChar;
        
        // Procedure header
        pw.println("1 \"" + methodName + "\"");
        // Write the test selection vector
        tsVector = toHex(tsData, numTests);
        vLength = tsVector.length();
        extraChar = vLength % 2;
        j = k = 0;
        outputLine.replace(0, outputLine.length(), "3 ");
        while (j < vLength - extraChar) {
            outputLine.append(tsVector.substring(j, j += 2) + " ");
            if (++k == 20) {
                if (!(j >= vLength)) {
                    pw.println(outputLine.toString());
                    outputLine.replace(0, outputLine.length(), "3 ");
                }
                k = 0;
            }
        }
        if (extraChar > 0) {
            outputLine.append(
                tsVector.substring(vLength - extraChar, vLength));
        }
        pw.println(outputLine.toString());
        pw.println("0 end of procedure " + methodName);
    }

    /**************************************************************************
     * Test driver for TestSelectionHandler.
     */
    public static void main(String[] argv) throws Exception {
        TestSelectionHandler tsh = new TestSelectionHandler(10);
        tsh.setSelectedTests("package.TestClass.public void method1(int arg0)",
            new int[]{1, 6, 7, 10});
        tsh.setSelectedTests("package.TestClass.public void " +
            "method2(String arg0)", new int[]{2, 3, 5});
        tsh.writeTestSelectionFile("testFile.sel", null);
        for (int n = 0; n < 2; n++) {
            tsh = new TestSelectionHandler();
            tsh.readTestSelectionFile("testFile.sel");
            String[] methodNames = tsh.getMethodList();
            for (int i = 0; i < methodNames.length; i++) {
                int[] selected = tsh.getSelectedTests(methodNames[i]);
                System.out.println(methodNames[i]);
                System.out.print("[ ");
                for (int j = 0; j < selected.length; j++) {
                    System.out.print(selected[j] + " ");
                }
                System.out.println("]");
            }
            tsh.writeTestSelectionFile("testFile.sel", null);
        }
    }
}
