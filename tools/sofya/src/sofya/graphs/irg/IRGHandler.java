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

package sofya.graphs.irg;

import java.io.*;
import java.util.*;

import sofya.base.Handler;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.*;

import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;

/**
 * The IRGHandler provides routines to read and write interclass relation graph
 * (&apos;<code>.prog.irg</code>&apos;) files in the Sofya database.
 *
 * <p>Since there is a one-to-one mapping between IRGs and their corresponding
 * database files, static methods suffice to read and write the database files.
 * Thus this handler cannot be instantiated.</p>
 *
 * @author Alex Kinneer
 * @version 10/04/2004
 *
 * @see sofya.graphs.irg.IRG
 */
public class IRGHandler extends Handler {
    /**
     * No reason to instantiate this handler since it can read or write an
     * IRG in a single static operation.
     */
    private IRGHandler() { }
    
    /**************************************************************************
     * Writes an interclass relation graph to a
     * &apos;<code>.prog.irg</code>&apos; file in the database directory.
     *
     * @param fileName Name of the IRG database file to be written. This
     * should include the &apos;<code>.prog</code>&apos; portion of the
     * extension.
     * @param tag Database tag to be associated with the file.
     * @param irg Interclass relation graph to be written to the database
     * file.
     *
     * @throws IOException If there is an error writing to the file.
     */
    public static void writeIRGFile(String fileName, String tag, IRG irg)
                       throws IOException {
        Date date = new Date();
        
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                         openOutputFile(fileName + ".irg", tag, false))),
                                        true);
        
        // Write the header
        pw.println("0 Interclass Relation Graph");
        pw.println("0 File: " + fileName + ".irg Created: " + date); 
        pw.println("0 version " + ProjectDescription.versionString);
        pw.println("0");
        
        // Write the name table
        String[] nameTable = irg.nameTable();
        
        if (nameTable.length > IRG.MAX_NAME_TABLE_SIZE) {
            throw new SofyaError("Name table too large (> " +
                IRG.MAX_NAME_TABLE_SIZE + " entries)");
        }
        
        pw.println("0 name table");
        pw.println("4 " + nameTable.length);
        for (int i = 0; i < nameTable.length; i++) {
            pw.println("1 " + i + " " + nameTable[i]);
        }
        pw.println("0 end of name table");
        
        // Write the data for each class
        for (int i = 0; i < nameTable.length; i++) {
            pw.println("1 " + i);
            
            IRG.ClassNode clNode = null;
            try {
                clNode = irg.getClassRelationData(nameTable[i]);
            }
            catch (ClassNotFoundException notPossible) { }
            
            writeIndices(pw, clNode.getIndices(IRG.ClassNode.ISUPERCLASS));
            writeIndices(pw, clNode.getIndices(IRG.ClassNode.ISUBCLASSES));
            writeIndices(pw, clNode.getIndices(IRG.ClassNode.IIMPLEMENTORS));
            writeIndices(pw, clNode.getIndices(IRG.ClassNode.IUSERS));
        }
    }
    
    /**************************************************************************
     * Reads an interclass relation graph from a
     * &apos;<code>.prog.irg</code>&apos; file in the database directory.
     *
     * @param fileName Name of the IRG database file to be read. This
     * should include the &apos;<code>.prog</code>&apos; portion of the
     * extension.
     * @param tag Database tag associated with the file.
     *
     * @return The IRG read from the specified database file.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws EmptyFileException If the specified file contains no data.
     * @throws BadFileFormatException If the specified file is not an IRG
     * file or is otherwise malformed or corrupted.
     * @throws IOException If there is an error reading from the IRG file.
     */
    @SuppressWarnings("unchecked")
    public static IRG readIRGFile(String fileName, String tag)
               throws FileNotFoundException, EmptyFileException,
                      BadFileFormatException, IOException {
        String[] nameTable;
        Map<Object, Object> classDataMap = new THashMap();
        
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                            openInputFile(fileName + ".irg", tag)));
        
        try {
            String input = ""; 
        
            for (int i = 0; i < 5; i++) {
                input = br.readLine();
                if (input == null) throw new EmptyFileException();
            }
            
            StringTokenizer stok = new StringTokenizer(input);
            String token;
            
            try {
                token = stok.nextToken();
                token = stok.nextToken() + " " + stok.nextToken();
                if (!token.equals("name table")) {
                    throw new BadFileFormatException("IRG file is invalid: " +
                        "could not find name table");
                }
            }
            catch (NoSuchElementException e) {
                throw new BadFileFormatException("IRG file is invalid: name " +
                    "table header not found where expected");
            }
            
            input = br.readLine();
            if (input == null) {
                throw new BadFileFormatException("IRG file is incomplete");
            }
            stok = new StringTokenizer(input);
            
            int tableSize = 0;
            try {
                if (!stok.nextToken().equals("4")) {
                    throw new BadFileFormatException("IRG file is invalid: " +
                        "missing name table size record");
                }
                tableSize = Integer.parseInt(stok.nextToken());
                if (tableSize < 0 || tableSize >= IRG.MAX_NAME_TABLE_SIZE) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted:name table size is invalid");
                }
            }
            catch (NumberFormatException e) {
                throw new BadFileFormatException("IRG file is corrupted: " +
                    "name table size is invalid");
            }
            catch (NoSuchElementException e) {
                throw new BadFileFormatException("IRG file is corrupted: " +
                    "name table size is missing");
            }
            
            nameTable = new String[tableSize];
            int nameIndex;
            String className;
            for (int i = 0; i < tableSize; i++) {
                input = br.readLine();
                if (input == null) {
                    throw new BadFileFormatException("IRG file is incomplete");
                }
                stok = new StringTokenizer(input);
                if (!stok.hasMoreTokens() || !stok.nextToken().equals("1")) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: missing name table entry");
                }
                try {
                    nameIndex = Integer.parseInt(stok.nextToken());
                    if (nameIndex < 0 || nameIndex >= tableSize) {
                        throw new BadFileFormatException("IRG file is " +
                            "corrupted: name table entry contains out-" +
                            "of-range index");
                    }
                    className = stok.nextToken();
                }
                catch (NumberFormatException e) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: name table entry contains invalid index");
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: name table entry is incomplete");
                }
                nameTable[nameIndex] = className;
            }
            
            input = br.readLine();
            if (input == null) {
                throw new BadFileFormatException("IRG file is incomplete");
            }
            
            for (int i = 0; i < tableSize; i++) {
                input = br.readLine();
                if (input == null) {
                    throw new BadFileFormatException("IRG file is incomplete");
                }
                stok = new StringTokenizer(input);
                if (!stok.hasMoreTokens() || !stok.nextToken().equals("1")) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted:class data record not found where " +
                        "expected");
                }
                try {
                    nameIndex = Integer.parseInt(stok.nextToken());
                    if (nameIndex < 0 || nameIndex >= tableSize) {
                        throw new BadFileFormatException("IRG file is " +
                            "corrupted: class data record contains out-" +
                            "of-range name index");
                    }
                }
                catch (NumberFormatException e) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: class data name is invalid");
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: class data name is missing");
                }
                className = nameTable[nameIndex];
                int superclass = readSuperclass(br, nameTable.length);
                TIntArrayList subclasses = readIndices(br, nameTable.length);
                TIntArrayList implementors = readIndices(br, nameTable.length);
                TIntArrayList users = readIndices(br, nameTable.length);
                classDataMap.put(className, new IRG.ClassNode(nameIndex,
                    superclass, subclasses, implementors, users, nameTable));
            }
        }
        finally {
            br.close();
        }

        return new IRG(nameTable, classDataMap);
    }
    
    /**************************************************************************
     * Helper method which writes a list of class name indices to the file.
     *
     * <p>A &quot;2&quot; record is written which specifies the number of
     * indices which follow. The indices are then converted to 4-wide
     * hexadecimal integers and written as a list in subsequent &quot;3&quot;
     * records (maximum 15 per line). Note that the maximum value that can be
     * encoded by a length 4 hexadecimal integer is 65535, which is primarily
     * where the limit on the name table size originates. Also note that
     * the hexadecimal encoding does <i>not</i> represent bit vectors, as in
     * most other database files.</p>
     *
     * @param pw Writer attached to the database file being written.
     * @param classIndices List of indices to be written to the file.
     */
    private static void writeIndices(PrintWriter pw, int[] classIndices) {
        StringBuffer sb = new StringBuffer();
        int n = 0;

        pw.println("2 " + classIndices.length);
        for (int i = 0; i < classIndices.length; i++) {
            String hexData = Integer.toHexString(classIndices[i]);
            
            if (n == 0) {
                sb.append("3 ");
            }
            
            for (int j = 0; j < 4 - hexData.length(); j++) {
                sb.append("0");
            }
            sb.append(hexData);
            n += 1;
            
            if ((n == 15) || (i == classIndices.length)) {
                pw.println(sb.toString());
                sb.delete(0, sb.length());
                n = 0;
            }
            else {
                sb.append(" ");
            }
        }
        if (sb.length() > 0) {
            pw.println(sb.toString());
        }
    }
    
    /**************************************************************************
     * Helper method which reads the superclass record for a class from the
     * file.
     *
     * @param br Reader attached to the database file being read.
     * @param nameTableSize Size of the name table in the IRG.
     *
     * @return The index to the entry in the name table containing the name
     * of the superclass for the class being read.
     *
     * @throws BadFileFormatException If the superclass cannot be read because
     * the file data is invalid, incomplete, or incorrect.
     * @throws IOException If any other error occurs while attempting to
     * read the file.
     */
    private static int readSuperclass(BufferedReader br, int nameTableSize)
                       throws BadFileFormatException, IOException {
        String input = br.readLine();
        if (input == null) {
            throw new BadFileFormatException("IRG file is incomplete");
        }
        
        StringTokenizer stok = new StringTokenizer(input);
        int numIndices;
        try {
            if (!stok.nextToken().equals("2")) {
                throw new BadFileFormatException("IRG file is corrupted: " +
                    "class relation data not found where expected");
            }
            numIndices = Integer.parseInt(stok.nextToken());
            if (numIndices > 1) {
                throw new BadFileFormatException("IRG file is illegal: " +
                    "class cannot have more than one superclass");
            }
        }
        catch (NumberFormatException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class index count is invalid");
        }
        catch (NoSuchElementException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class index count is missing");
        }
        
        input = br.readLine();
        if (input == null) {
            throw new BadFileFormatException("IRG file is incomplete");
        }
        
        stok = new StringTokenizer(input);
        if (!stok.nextToken().startsWith("3")) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class relation data is missing or incomplete");
        }
        
        try {
            int index = Integer.parseInt(stok.nextToken(), 16);
            if ((index < 0 || index >= nameTableSize) &&
                    (index != IRG.NO_SUPERCLASS)) {
                throw new BadFileFormatException("IRG file is " +
                    "corrupted: class relation data contains out-" +
                    "of-range index: " + index);
            }
            return index;
        }
        catch (NumberFormatException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class superclass index is invalid");
        }
        catch (NoSuchElementException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class superclass index is missing");
        }
    }
    
    /**************************************************************************
     * Helper method which reads the a list of name table indices from the
     * file.
     *
     * @param br Reader attached to the database file being read.
     * @param nameTableSize Size of the name table in the IRG.
     *
     * @return The list of name table indices read from the file.
     *
     * @throws BadFileFormatException If the list cannot be read because
     * the file data is invalid, incomplete, or incorrect.
     * @throws IOException If any other error occurs while attempting to
     * read the file.
     */
    private static TIntArrayList readIndices(BufferedReader br,
                                             int nameTableSize)
            throws BadFileFormatException, IOException {
        String input = br.readLine();
        if (input == null) {
            throw new BadFileFormatException("IRG file is incomplete");
        }
        
        StringTokenizer stok = new StringTokenizer(input);
        int numIndices;
        try {
            if (!stok.nextToken().equals("2")) {
                throw new BadFileFormatException("IRG file is corrupted: " +
                    "class relation data not found where expected");
            }
            numIndices = Integer.parseInt(stok.nextToken());
        }
        catch (NumberFormatException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class index count is invalid");
        }
        catch (NoSuchElementException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                "class index count is missing");
        }
        
        TIntArrayList indices = new TIntArrayList(numIndices);
        
        int numLines = (int) ((numIndices + 14.0) / 15.0);
        int index;
        try {
            for (int i = 0; i < numLines; i++) {
                input = readNextLine(br);
                stok = new StringTokenizer(input);
                if (!stok.nextToken().startsWith("3")) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: class relation data is missing " +
                        "or incomplete");
                }
                try {
                    while (stok.hasMoreTokens()) {
                        index = Integer.parseInt(stok.nextToken(), 16);
                        if (index < 0 || index >= nameTableSize) {
                            throw new BadFileFormatException("IRG file is " +
                                "corrupted: class relation data contains " +
                                "out-of-range index: " + index);
                        }
                        indices.add(index);
                    }
                }
                catch (NumberFormatException e) {
                    throw new BadFileFormatException("IRG file is " +
                        "corrupted: class relation data contains " +
                        "invalid index");
                }
            }
        }
        catch (NoSuchElementException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                      "missing class relation data record");
        }
        catch (EOFException e) {
            throw new BadFileFormatException("IRG file is corrupted: " +
                      "class relation data record is incomplete");
        }
        
        return indices;
    }
    
    /**************************************************************************
     * Test driver for IRGHandler.
     */
    public static void main(String[] argv) throws Exception {
        // (Diff the two files after running)
        IRG irg = IRGHandler.readIRGFile("listtest.prog", null);
        IRGHandler.writeIRGFile("listtest_copy.prog", null, irg);
        
        IRG.ClassNode cn = null;
        
        String[] classList = irg.getNameTable();
        for (int i = 0; i < classList.length; i++) {
            System.out.println(classList[i]);
        }
        System.out.println();
        System.out.println("galileo.GConstants:");
        cn = irg.getClassRelationData("galileo.GConstants");
        System.out.println("  superclass: " + cn.getSuperclass());
        System.out.println("  implementors: ");
        for (Iterator it = cn.implementorIterator(); it.hasNext(); ) {
            System.out.println("    " + (String) it.next());
        }
    }
}
