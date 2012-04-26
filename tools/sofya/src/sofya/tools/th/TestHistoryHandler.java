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

package sofya.tools.th;

import java.util.*;
import java.io.*;

import sofya.base.Handler;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.*;
import static sofya.base.SConstants.*;

import gnu.trove.THashMap;

/**
 * The TestHistoryHandler provides methods to manipulate Galileo test history
 * files and manage test histories for methods.
 *
 * @author Alex Kinneer
 * @version 07/21/2005
 *
 * @see sofya.tools.th.TestHistory
 * @see sofya.viewers.TestHistoryViewer
 */
@SuppressWarnings("unchecked")
public class TestHistoryHandler extends Handler {
    /** Maps method names to their test histories. */
    private Map<Object, Object> histories = new THashMap();
    /** Bit vector storing type of blocks recorded by the test history. */
    private int typeFlags = 0x00000000;
    
    /** Constant to indicate the current version of the file format. This
        value is written to file to enable future versions of the handler
        to more easily implement legacy support. */
    private static final int FILE_FORMAT_VERSION = 301;

    /**************************************************************************
     * Default constructor, creates a test history handler with no test
     * histories yet registered.
     *
     * <p><b>Note:</b> You must call
     * {@link TestHistoryHandler#setTypeFlags} before
     * attempting to save a test history file or an IllegalArgumentException
     * will be thrown.</p>
     */
    public TestHistoryHandler() { }

    /**************************************************************************
     * Standard constructor, creates a test history handler which will
     * indicate that the test history stores information for the given
     * block types.
     *
     * @param typeFlags Bit mask representing the types of blocks that are
     * marked in traces managed by the handler. Can be any bitwise combination
     * of the following (See {@link sofya.base.SConstants}):
     * <ul>
     * <li><code>SConstants.BlockType.MASK_BASIC</code></li>
     * <li><code>SConstants.BlockType.MASK_ENTRY</code></li>
     * <li><code>SConstants.BlockType.MASK_EXIT</code></li>
     * <li><code>SConstants.BlockType.MASK_CALL</code></li>
     * </ul>
     *
     * @throws IllegalArgumentException If the bit mask doesn't have a bit set
     * which corresponds to a valid block type.
     */
    public TestHistoryHandler(int typeFlags) {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type specified");
        }
        this.typeFlags = typeFlags;
    }

    /**************************************************************************
     * Writes a test history file from the test history information currently
     * registered with the handler.
     *
     * @param fileName Name of the test history file to be written.
     *
     * @throws IOException If there is an I/O error creating or writing
     * the file.
     */
    public void writeTestHistoryFile(String fileName) throws IOException {
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName, false))), true);
    
        // Test history header information
        pw.println("0 Test History Information");
        pw.println("0 File: " + fileName + "  Created: " +
                    new Date(System.currentTimeMillis()));
        pw.println("0 Version: " + ProjectDescription.versionString);
        pw.println("0 Trace type: " + getTypeString());
        pw.println("4 " + FILE_FORMAT_VERSION);
    
        String[] methodNames = getMethodList();
        for (int i = 0; i < methodNames.length; i++) {
            writeTestHistory(pw, methodNames[i].replace(' ', '_'),
                             (TestHistory) histories.get(methodNames[i]));
        }

        pw.close();
        if (pw.checkError()) {
            throw new IOException("Error writing test history file");
        }
    }

    /**************************************************************************
     * Writes a test history to the specified writer stream.
     *
     * @param methodName Name of the method for which the test history is
     * being written.
     * @param th The test history to be written to file.
     */
    private void writeTestHistory(PrintWriter pw, String methodName,
                                  TestHistory th) throws IOException {
        String testVector = null;
        StringBuffer outputLine = new StringBuffer();
        int j, k, vLength, extraChar;
        
        // Procedure header
        pw.println("1 \"" + methodName + "\" " + th.getHighestBlockID() +
                   " " + th.getHighestTestID() + " " + typeFlags);
        
        if (!th.isHistoryEmpty()) {
            // Each block and associated test vector
            for (int i = 1; i <= th.getHighestBlockID(); i++) {
                if (th.isEmpty(i)) {
                    continue;
                }
                
                pw.println("2 " + i);
                testVector = th.getTestVector(i);
                vLength = testVector.length();
                extraChar = vLength % 2;
                j = k = 0;
                outputLine.replace(0, outputLine.length(), "3 ");
                while (j < vLength - extraChar) {
                    outputLine.append(testVector.substring(j, j += 2) + " ");
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
                        testVector.substring(vLength - extraChar, vLength));
                    // Compatibility fix for consistency with aristotle
                    outputLine.append("0");
                }
                pw.println(outputLine.toString());
            }
        }
        
        pw.println("0 end of procedure " + methodName);
    }
    
    /**************************************************************************
     * Reads a test history file making the information available for request
     * from the handler. The {@link TestHistoryHandler#getTestHistory} method
     * can be used to retrieve information loaded by this method.
     *
     * @param fileName Name of the test history file to be read.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws BadFileFormatException If the specified file is not a test
     * history file, or is otherwise malformed or corrupted.
     * @throws IOException If there is an I/O error reading the file.
     */
    public void readTestHistoryFile(String fileName)
                throws FileNotFoundException, LocatableFileException,
                       BadFileFormatException, IOException {
        histories.clear();
        LineNumberReader br = new LineNumberReader(
                              new InputStreamReader(openInputFile(fileName)));
        
        int fileVersion = FILE_FORMAT_VERSION;
        try {
            String currentLine;
            StringTokenizer stok;
            try {
                currentLine = readNextLine(br);
            
                stok = new StringTokenizer(currentLine);
                if (stok.nextToken().startsWith("4")) {
                    try {
                        fileVersion = Integer.parseInt(stok.nextToken());
                        currentLine = readNextLine(br);
                    }
                    catch (NumberFormatException e) {
                        throw new LocatableFileException("Test history file is " +
                            "invalid: file version is missing or invalid",
                            br.getLineNumber());
                    }
                }
                else {
                    fileVersion = 200;
                }
            }
            catch (EOFException e) {
                throw new BadFileFormatException("Test history " +
                    "file is incomplete");
            }
        
            TestHistory th = null;
            String methodName = null;
            StringBuilder testVector = new StringBuilder();
            int highestBlockID = 0, highestTestID = 0, numLines;
            mainLoop:
            while (true) {
                try {
                    // Read method header information
                    stok = new StringTokenizer(currentLine);
                    if (!stok.nextToken().startsWith("1")) {
                        throw new LocatableFileException("Test history file " +
                            "is corrupted: method information not found " +
                            "where expected", br.getLineNumber());
                    }
                    // Read method name using quotation marks as tokens
                    stok.nextToken("\"");
                    methodName = stok.nextToken();
                    stok.nextToken(" \t\n\r\f");
                    // Get entity counts
                    highestBlockID = Integer.parseInt(stok.nextToken());
                    highestTestID = Integer.parseInt(stok.nextToken());
                    typeFlags = Integer.parseInt(stok.nextToken());
                }
                catch (NoSuchElementException e) {
                    throw new LocatableFileException("Test history file " +
                        "is corrupted: method header is incomplete",
                        br.getLineNumber());
                }
            
                th = new TestHistory(highestBlockID, highestTestID);
                try {
                    int blockID = 1;
                    while (blockID <= highestBlockID) {
                        currentLine = readNextLine(br);
                        stok = new StringTokenizer(currentLine);
                        
                        if (!stok.nextToken().startsWith("2")) {
                            histories.put(methodName, th);
                            continue mainLoop;
                        }
                        
                        blockID = Integer.parseInt(stok.nextToken());
                        
                        testVector.delete(0, testVector.length());
                        numLines = 1 + (((highestTestID - 1) / 8) / 20);
                        for (int j = 0; j < numLines; j++) {
                            currentLine = readNextLine(br);
                            stok = new StringTokenizer(currentLine);
                            if (!stok.nextToken().startsWith("3")) {
                                throw new BadFileFormatException("Test " +
                                    "history file is corrupted: test " +
                                    "history for block is missing");
                            }
                            while (stok.hasMoreTokens()) {
                                String str = stok.nextToken();
                                testVector.append(str);
                            }
                        }
                        
                        // Handle compatibility fix for consistency
                        // with aristotle
                        if (fileVersion >= 301) {
                            int bitOvf = highestTestID % 8;
                            if ((bitOvf > 0) && (bitOvf <= 4)) {
                                testVector.deleteCharAt(
                                    testVector.length() - 1);
                                if ((testVector.length() % 2) != 1) {
                                    throw new AssertionError();
                                }
                            }
                        }

                        String vecStr = testVector.toString();
                        int vecLen = vecStr.length();
                        if ((vecLen * 4) < highestTestID) {
                            throw new BadFileFormatException("Test history " +
                                "file is corrupted: test history for block " +
                                "is incomplete");
                        }
                        
                        th.setTestVector(blockID, vecStr);
                    }
                    histories.put(methodName, th);
                
                    // Look for next method header
                    try {
                        currentLine = readNextLine(br);
                    }
                    catch (EOFException e) { break; }
                }
                catch (NumberFormatException e) {
                    throw new DataTypeException("Test history file " +
                        "is corrupted: numeric block ID is missing.",
                        br.getLineNumber());
                }
                catch (NoSuchElementException e) {
                    throw new LocatableFileException("Test history file " +
                        "is corrupted: method test history is incomplete",
                        br.getLineNumber());
                }
                catch (EOFException e) {
                    histories.put(methodName, th);
                    break;
                }
            }
        }
        finally {
            br.close();
        }
    }
    
    /**************************************************************************
     * Gets the list of methods names for which test histories are currently
     * registered with the handler.
     *
     * @return A list of the method names for which test histories can be
     * retrieved from this handler.
     */
    public String[] getMethodList() {
        String[] methodNames = (String[]) histories.keySet().toArray(
            new String[histories.size()]);
        Arrays.sort(methodNames);
        return methodNames;
    }
    
    /**************************************************************************
     * Reports whether a test history exists for a given method.
     *
     * @param methodName Name of the method which the handler should
     * check for a test history.
     *
     * @return <code>true</code> if a test history is available for the given
     * method, <code>false</code> otherwise.
     */
    public boolean containsTestHistory(String methodName) {
        return histories.containsKey(methodName);
    }

    /**************************************************************************
     * Gets the test history associated with a given method.
     *
     * @param methodName Name of the method for which the test history is to
     * be retrieved.
     *
     * @return The {@link sofya.tools.th.TestHistory} associated with the
     * specified method.
     *
     * @throws MethodNotFoundException If the handler has no test history
     * associated with a method of the specified name.
     */
    public TestHistory getTestHistory(String methodName)
                       throws MethodNotFoundException {
        if (histories.containsKey(methodName)) {
            return (TestHistory) histories.get(methodName);
        }
        else {
            throw new MethodNotFoundException(methodName);
        }
    }
    
    /**************************************************************************
     * Sets the test history associated with a given method.
     *
     * <p>If a test history already exists for the specified method, it is
     * overwritten. Otherwise the method and test history are added to the
     * set of methods registered with the handler.</p>
     *
     * @param methodName Name of the method for which a test history is
     * being specified.
     * @param th The test history to be associated with the method.
     */
    public void setTestHistory(String methodName, TestHistory th) {
        histories.put(methodName, th);
    }
    
    /*************************************************************************
     * Sets the bit mask indicating what types of blocks are marked by the
     * test histories being managed by the handler.
     *
     * @param typeFlags Bit mask representing the types of blocks that are
     * marked in traces managed by the handler. Can be any bitwise combination
     * of the following (See {@link sofya.base.SConstants}):
     * <ul>
     * <li><code>SConstants.BlockType.MASK_BASIC</code></li>
     * <li><code>SConstants.BlockType.MASK_ENTRY</code></li>
     * <li><code>SConstants.BlockType.MASK_EXIT</code></li>
     * <li><code>SConstants.BlockType.MASK_CALL</code></li>
     * </ul>
     *
     * @throws IllegalArgumentException If the bit mask doesn't have a bit set
     * which corresponds to a valid block type.
     */
    public void setTypeFlags(int typeFlags) {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type specified");
        }
        this.typeFlags = typeFlags;
    }
    
    /*************************************************************************
     * Gets the bit mask indicating what types of blocks are marked by the
     * test history being managed by the handler.
     *
     * @return Bit mask controlling what types of blocks are instrumented.
     */
    public int getTypeFlags() {
        return typeFlags;
    }
    
    /*************************************************************************
     * Gets a string listing the types of blocks marked by the test history
     * being managed by the handler.
     *
     * @return A string listing the types of blocks marked by the test
     * histories being managed by this handler. The types are delimited by
     * a single space character (ASCII 32).
     */
    public String getTypeString() {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid trace type specified");
        }
        StringBuffer sb = new StringBuffer();
        if ((typeFlags & BlockType.MASK_BASIC) == BlockType.MASK_BASIC) {
            sb.append("Basic ");
        }
        if ((typeFlags & BlockType.MASK_ENTRY) == BlockType.MASK_ENTRY) {
            sb.append("Entry ");
        }
        if ((typeFlags & BlockType.MASK_EXIT) == BlockType.MASK_EXIT) {
            sb.append("Exit ");
        }
        if ((typeFlags & BlockType.MASK_CALL) == BlockType.MASK_CALL) {
            sb.append("Call ");
        }
        if ((typeFlags & BlockType.MASK_RETURN) == BlockType.MASK_RETURN) {
            sb.append("Return ");
        }
        return sb.toString().trim();
    }
    
    protected static String toHex(BitSet bv, int size) {
        return Handler.toHex(bv, size);
    }

    protected static BitSet toBinary(String hexString) {
        return Handler.toBinary(hexString);
    }
    
}
