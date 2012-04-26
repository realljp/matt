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

package sofya.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProgramUnit;
import sofya.base.Utility.IntegerPtr;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.EmptyFileException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;
import sofya.graphs.GraphCache;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFHandler;
import sofya.graphs.cfg.CFGCacheFactory;
import sofya.ed.structural.CoverageTrace;
import sofya.ed.structural.TraceHandler;
import static sofya.base.SConstants.*;
import static sofya.base.Utility.isParsableInt;

import gnu.trove.THashMap;

/**
 * The TestHistoryBuilder gathers coverage information from trace files
 * produced by the {@link sofya.ed.structural.TraceHandler} attached
 * to a {@link sofya.ed.structural.ProgramEventDispatcher} and uses it
 * to produce test history files for test subjects.
 *
 * <p>Usage:<br><code>java sofya.tools.TestHistoryBuilder -&lt;B|E|X|C&gt;
 * [-tag &lt;tag&gt;] &lt;tracedir&gt; &lt;histfile&gt;
 * &lt;classname|classlist_file&gt;</code><br>
 *
 * <p><b>Note:</b> Arguments must be given in exactly the order shown,
 * including optional arguments when used.</p>
 *
 * <p>A class list file should be a simple text file ending with the extension
 * "<i>.prog</i>" which contains a list of classes, one per line, including all
 * package qualifications. If the fourth argument does not end in
 * "<i>.prog</i>", it will be treated as a single class name.</p>
 *
 * @author Alex Kinneer
 * @version 05/13/2005
 *
 * @see sofya.tools.th.TestHistory
 * @see sofya.tools.th.TestHistoryHandler
 * @see sofya.viewers.TestHistoryViewer
 */
public final class TestHistoryBuilder {
    /** Convenience reader for the <code>System.in</code> stream. */
    private static final BufferedReader stdin = new BufferedReader(
                                                new InputStreamReader(
                                                    System.in));
    
    /** Stream to which normal builder outputs will be printed. */
    @SuppressWarnings("unused")
    private PrintStream stdout = System.out;
    /** Stream to which error builder outputs will be printed. */
    private PrintStream stderr = System.err;
    
    /** Bit vector representing types of blocks used to build the test
        history. */
    private int typeFlags = 0x00000000;
    /** Database tag associated with the subject. */
    private String tag = null;
    
    /** Handler for loading CF files. May be used on demand by the memory
        sensitive cache. */
    private CFHandler cfHandler;
    
    /** Compilation flag to control whether the test history builder
        does stricter validation of the naming convention correlating
        numbered trace files to test numbers. */
    private static final boolean CHECK_NUMBERING = true;
    
    /** Compilation flag to control debug outputs. */
    private static final boolean DEBUG = false;
    
    /*************************************************************************
     * Creates a test history builder which prints its outputs to the
     * standard streams (<code>System.out</code> and <code>System.err</code>).
     *
     * @param typeFlags Bit mask representing the types of blocks to be
     * instrumented.
     */
    public TestHistoryBuilder(int typeFlags) {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
    }

    /*************************************************************************
     * Creates a test history builder which prints its outputs to the
     * standard streams (<code>System.out</code> and <code>System.err</code>).
     *
     * @param typeFlags Bit mask representing the types of blocks to be
     * instrumented.
     */
    public TestHistoryBuilder(int typeFlags, String tag) {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
        if ((tag != null) && (tag.length() == 0)) tag = null;
        this.tag = tag;
    }

    /*************************************************************************
     * Creates a test history builder which prints its outputs to the
     * specified streams.
     *
     * @param typeFlags Bit mask representing the types of blocks to be
     * instrumented.
     */
    public TestHistoryBuilder(int typeFlags, String tag,
                              PrintStream stdout, PrintStream stderr) {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
        if ((tag != null) && (tag.length() == 0)) tag = null;
        this.tag = tag;
        if ((stdout == null) || (stderr == null)) {
            throw new IllegalArgumentException("Required parameter is null");
        }
        this.stdout = stdout;
        this.stderr = stderr;
    }
    
    /*************************************************************************
     * Sets the stream to which standard outputs will be printed.
     *
     * @param stdout Stream to which standard outputs will be printed.
     */
    public void setStdoutStream(PrintStream stdout) {
        if (stdout == null) {
            throw new IllegalArgumentException("Stream cannot be null");
        }
        this.stdout = stdout;
    }
    
    /*************************************************************************
     * Sets the stream to which standard error outputs will be printed.
     *
     * @param stderr Stream to which error outputs will be printed.
     */
    public void setStderrStream(PrintStream stderr) {
        if (stderr == null) {
            throw new IllegalArgumentException("Stream cannot be null");
        }
        this.stderr = stderr;
    }

    /*************************************************************************
     * Builds test history information for a subject class from a set of
     * traces and writes it to a file.
     *
     * @param traceDir Path to the directory containing the traces from which
     * to gather test history information.
     * @param thFile Name of the test history file to be created.
     * @param classList List of the classes for which test history information
     * is being constructed.
     *
     * @return <code>true</code> if the test history file was successfully
     * written. If <code>false</code>, the reason for failure will be printed
     * to the error stream.
     */
    @SuppressWarnings("unchecked")
    public boolean buildTestHistory(String traceDir, String thFile,
                                    List<String> classList) {
        if ((traceDir == null) || (thFile == null) || (classList == null)) {
            throw new IllegalArgumentException("Required parameter is null");
        }
        
        // Get the list of trace files
        File tDir = new File(traceDir);
        if (!tDir.exists()) {
            stderr.println("Trace directory cannot be found");
            return false;
        }
        if (!tDir.canRead()) {
            stderr.println("Cannot read trace directory");
            return false;
        }
        
        String[] fileList = (tDir.list(new FilenameFilter() {
                                 public boolean accept(File dir, String name) {
                                     if (name.endsWith(".tr")) return true;
                                     return false;
                                 }
                             }));
        
        if (fileList.length == 0) {
            stderr.println("No .tr files found in directory");
            return false;
        }

        Arrays.sort(fileList, new NumericStringComparator());
        if (DEBUG) {
            stdout.println(Arrays.toString(fileList));
        }
        
        GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
        TraceHandler trHandler = new TraceHandler();
        TestHistoryHandler thHandler = new TestHistoryHandler(typeFlags);
        Map<Object, Object> nameSigMap = new THashMap();
        cfHandler = new CFHandler(procCFGs);
        
        // Tell the handler to collect all the CFGs, instead of clearing
        // them out each time a new control flow file is read
        cfHandler.setClearOnLoad(false);
        
        // Iterate over the classes creating empty test histories for each
        // method and caching the CFGs for block-type screening later
        String[] procList = null;
        CFG procCFG = null;
        String className;
        int size = classList.size();
        Iterator iterator = classList.iterator();
        for (int i = size; i-- > 0; ) {
            className = (String) iterator.next();
            
            try {
                cfHandler.readCFFile(className + ".java", tag);
            }
            catch (FileNotFoundException e) {
                stderr.println("WARNING: CF file does not exist for class " +
                    className);
                continue;
            }
            catch (BadFileFormatException e) {
                stderr.println(e.getMessage());
                return false;
            }
            catch (EmptyFileException e) {
                stderr.println("WARNING: CF file for class " + className +
                    " is empty");
                continue;
            }
            catch (IOException e) {
                stderr.println("I/O error: \"" + e.getMessage() +
                               "\" while reading CF file for class " +
                               className);
                return false;
            }

            Iterator procs = procCFGs.iterator(className);
            while (procs.hasNext()) {
                procCFG = (CFG) procs.next();
                
                MethodSignature mSig = procCFG.getSignature();
                if (mSig == null) {
                    stderr.println("Cannot operate on legacy control flow " +
                        "files");
                    return false;
                }
                
                // For now, traces still don't use signatures
                String mName = procCFG.getMethodName();
                nameSigMap.put(mName, mSig);
                
                thHandler.setTestHistory(mName, new TestHistory(
                    procCFG.getHighestNodeId(), fileList.length));
            }
        }

        procList = thHandler.getMethodList();
        TestHistory th;
        CoverageTrace trace;
        
        List<String> irregulars = CHECK_NUMBERING ?
            irregulars = new ArrayList<String>(4) : null;
            
        // Now iterate over all of the trace files, and for each match hit
        // blocks to test histories for all methods of interest.
        int testNum = 0;
        int numFiles = fileList.length;
        for (int fileIdx = 0; fileIdx < numFiles; fileIdx++) {
            if (CHECK_NUMBERING &&
                    !fileList[fileIdx].startsWith(String.valueOf(testNum))) {
                int dotPos = fileList[fileIdx].indexOf('.');
                String namePrefix = fileList[fileIdx].substring(0, dotPos);
                IntegerPtr prefixNum = new IntegerPtr();
                if (!isParsableInt(namePrefix, 10, prefixNum)) {
                    irregulars.add(fileList[fileIdx]);
                    continue;
                }
                int prefixVal = prefixNum.value;
                if (prefixVal > testNum) {
                    if (prefixVal > (testNum + 1)) {
                        stderr.println("WARNING: Trace files not found " +
                            "for tests " + testNum + "-" + (prefixVal - 1) +
                            "! Skipping and setting test number to " +
                            prefixVal + ".");
                    }
                    else {
                        stderr.println("WARNING: Trace file not found " +
                            "for test " + testNum + "! Skipping and " +
                            "incrementing test number.");
                    }
                    testNum = prefixVal;
                }
                else {
                    // Must be less than expected test number
                    irregulars.add(fileList[fileIdx]);
                    continue;
                }
            }
            
            String traceFile =
                traceDir + File.separatorChar + fileList[fileIdx];
            if (DEBUG) {
                stdout.println("INFO: Processing \"" + fileList[fileIdx] +
                    "\" as test number " + testNum);
            }
            
            // Read the trace
            try {
                trHandler.readTraceFile(traceFile);
            }
            catch (EmptyFileException e) {
                testNum += 1;
                continue;
            }
            catch (BadFileFormatException e) {
                 stderr.println("Error opening trace file " +
                     fileList[fileIdx]);
                 stderr.println(e.getMessage());
                 return false;
            }
            catch (IOException e) {
                stderr.println("I/O error: \"" + e.getMessage() +
                    "\" while reading trace file for " + traceFile);
                return false;
            }
            
            // Transform method name keys in trace file into format
            // expected in CFGs. Ideally this should be unified under
            // the new format used in trace files at some point.
            Map<Object, Object> rekeyedTraces = new THashMap();
            Set<String> trProcs = trHandler.getUnsortedMethodList();
            Iterator<String> trIter = trProcs.iterator();
            int trCount = trProcs.size();
            for (int i = trCount; i-- > 0; ) {
                String trProc = trIter.next();
                try {
                    rekeyedTraces.put(Handler.unpackSignature(trProc),
                        trHandler.getTrace(trProc));
                }
                catch (MethodNotFoundException e) {
                    throw new AssertionError(e);
                }
            }
            
            for (int i = 0; i < procList.length; i++) {
                trace = (CoverageTrace) rekeyedTraces.get(procList[i]);
                if (trace == null) {
                    // This method wasn't witnessed in the trace
                    continue;
                }
                
                MethodSignature mSig =
                    (MethodSignature) nameSigMap.get(procList[i]);
                procCFG = (CFG) procCFGs.get(mSig).getGraph();
                
                if (procCFG.getHighestNodeId() != trace.getHighestId()) {
                    stderr.println("Mismatch in block count in trace " +
                        traceFile.substring(0, traceFile.lastIndexOf('.')) +
                        ".");
                    stderr.println("  (" + procCFG.getSignature() + ")");
                    return false;
                }
                try {
                    th = thHandler.getTestHistory(procList[i]);
                }
                catch (MethodNotFoundException e) {
                    e.printStackTrace(stderr);
                    return false;
                }
                for (int blkID = 1; blkID <= trace.getHighestId(); blkID++) {
                    BlockType nodeType = procCFG.getBlock(blkID).getType();
                    int mask = nodeType.toMask();
                    
                    if ((typeFlags & mask) == mask) {
                        if (trace.query(blkID)) {
                            th.set(blkID, testNum);
                        }
                    }
                }
            }
            
            testNum += 1;
        }
        
        if (CHECK_NUMBERING) {
            Iterator<String> iter = irregulars.iterator();
            while (iter.hasNext()) {
                String irregularTrace = iter.next();
                stdout.println("WARNING: Processing: \"" + irregularTrace +
                    "\" as test number " + testNum);
                
                String traceFile = traceDir + File.separatorChar +
                    irregularTrace;
                // Read the trace
                try {
                    trHandler.readTraceFile(traceFile);
                }
                catch (EmptyFileException e) {
                    testNum += 1;
                    continue;
                }
                catch (BadFileFormatException e) {
                     stderr.println("Error opening trace file " +
                         irregularTrace);
                     stderr.println(e.getMessage());
                     return false;
                }
                catch (IOException e) {
                    stderr.println("I/O error: \"" + e.getMessage() +
                        "\" while reading trace file for " + traceFile);
                    return false;
                }
                
                // Transform method name keys in trace file into format
                // expected in CFGs. Ideally this should be unified under
                // the new format used in trace files at some point.
                Map<Object, Object> rekeyedTraces = new THashMap();
                Set<String> trProcs = trHandler.getUnsortedMethodList();
                Iterator<String> trIter = trProcs.iterator();
                int trCount = trProcs.size();
                for (int i = trCount; i-- > 0; ) {
                    String trProc = trIter.next();
                    try {
                        rekeyedTraces.put(Handler.unpackSignature(trProc),
                            trHandler.getTrace(trProc));
                    }
                    catch (MethodNotFoundException e) {
                        throw new AssertionError(e);
                    }
                }
                
                for (int i = 0; i < procList.length; i++) {
                    trace = (CoverageTrace) rekeyedTraces.get(procList[i]);
                    if (trace == null) {
                        // This method wasn't witnessed in the trace
                        continue;
                    }
                    
                    MethodSignature mSig =
                        (MethodSignature) nameSigMap.get(procList[i]);
                    procCFG = (CFG) procCFGs.get(mSig).getGraph();
                    
                    if (procCFG.getHighestNodeId() != trace.getHighestId()) {
                        stderr.println("Mismatch in block count in trace " +
                            traceFile.substring(0, traceFile.lastIndexOf('.')) +
                            ".");
                        stderr.println("  (" + procCFG.getSignature() + ")");
                        return false;
                    }
                    try {
                        th = thHandler.getTestHistory(procList[i]);
                    }
                    catch (MethodNotFoundException e) {
                        e.printStackTrace(stderr);
                        return false;
                    }
                    for (int blkID = 1; blkID <= trace.getHighestId(); blkID++) {
                        BlockType nodeType = procCFG.getBlock(blkID).getType();
                        int mask = nodeType.toMask();
                        
                        if ((typeFlags & mask) == mask) {
                            if (trace.query(blkID)) {
                                th.set(blkID, testNum);
                            }
                        }
                    }
                }
                
                testNum += 1;
            }
        }

        // Write test history file
        try {
            thHandler.writeTestHistoryFile(thFile);
        }
        catch (IOException e) {
            stderr.println("I/O error: \"" + e.getMessage() +
                "\" while writing test history file '" + thFile + "'");
            return false;
        }
        return true;
    }
    
    /*************************************************************************
     * A string comparator which pads the beginning of the shorter string
     * with zeros before performing the lexographical comparison. Strings
     * of equal length are ordered using the usual lexographic definition.
     *
     * <p>For strings whose leading characters are numeric, this effectivly
     * imposes correct numeric ordering on the strings. This is primarily
     * intended for use in ordering a list (array) of numbered trace files,
     * where normal lexographic sorting will yield a list of the form
     * [ 0.tr 1.tr 10.tr ... 19.tr 2.tr ... ] rather than the desired
     * [ 0.tr 1.tr 2.tr ... 10.tr ... 19.tr 20.tr ... n.tr ].</p>
     */
    private class NumericStringComparator implements Comparator<Object> {
        public int compare(Object o1, Object o2) {
            String str1 = (String) o1;
            String str2 = (String) o2;
            int compareLength = (str1.length() > str2.length())
                                ? str1.length()
                                : str2.length();
            for (int i = compareLength - str1.length(); i > 0; i--) {
                str1 = "0" + str1;
            }
            for (int i = compareLength - str2.length(); i > 0; i--) {
                str2 = "0" + str2;
            }
            return str1.compareTo(str2);
        }
    }

    /*************************************************************************
     * Entry point for the test history builder.
     */
    public static void main(String[] argv) {
        String traceDir = null, thFile = null, tag = null;
        int typeFlags = 0x00000000;
        int argIndex = 1;
        List<String> classList = new ArrayList<String>();
        
        try {
            if (argv.length < 1) {
                System.err.println("USAGE: " +
                    "java sofya.tools.TestHistoryBuilder -<B|E|X|C> " +
                    "[-tag <tag>] <tracedir> <test_history_file> " +
                    "<classname|classlist_file>");
                System.exit(1);
            }

            // Keep things simple - first argument _must_ be block type
            // parameter
            for (int i = 1; i < argv[0].length(); i++) {
                switch(argv[0].charAt(i)) {
                    case 'B':
                        typeFlags |= BlockType.MASK_BASIC;
                        break;
                    case 'E':
                        typeFlags |= BlockType.MASK_ENTRY;
                        break;
                    case 'X':
                        typeFlags |= BlockType.MASK_EXIT;
                        break;
                    case 'C':
                        typeFlags |= BlockType.MASK_CALL;
                        break;
                    case 'R':
                        typeFlags |= BlockType.MASK_RETURN;
                        break;
                    default:
                        System.err.println("Invalid block type");
                        System.exit(1);
                }
            }
            if ((typeFlags & BlockType.MASK_VALID) == 0) {
                System.err.println("No valid block type specified");
                System.exit(1);
            }

            // If tag is used, it _must_ be second argument
            if ((argv.length > 1) && argv[1].equals("-tag")) {
                if (argv.length > 2) {
                    tag = argv[2];
                    argIndex = 3;
                }
                else {
                    System.err.println("Tag value not specified");
                    System.exit(1);
                }
            }

            // if no arguments given, prompt for test dir name
            if (argv.length < (argIndex + 1)) {
                System.out.print("Enter test directory name: ");
                traceDir = stdin.readLine();
            }
            else { // test directory is first argument
                traceDir = argv[argIndex];
            }
            argIndex += 1;

            // if necessary, prompt for test history file name
            if (argv.length < (argIndex + 1)) {
                System.out.print("Enter test history file name: ");   
                thFile = stdin.readLine();   
            }
            else { // test history file name is second argument
                thFile = argv[argIndex]; 
            }
            argIndex += 1;
            
            if (argv.length < (argIndex + 1)) {
                System.out.print("Enter tested class name: ");
                classList.add(stdin.readLine());
            }
            else {
                // List of classes/jars/'prog'-files follows
                List<ProgramUnit> unitList = new ArrayList<ProgramUnit>();
                while (argIndex < argv.length) {
                    if (argv[argIndex].endsWith(".prog")) {
                        try {
                            Handler.readProgFile(argv[argIndex], tag,
                                                 unitList);
                            
                            int size = unitList.size();
                            Iterator units = unitList.iterator();
                            for (int i = size; i-- > 0; ) {
                                ProgramUnit pUnit = (ProgramUnit) units.next();
                                classList.addAll(pUnit.classes);
                            }
                        }
                        catch (IOException e) {
                            System.err.println(e.getMessage());
                            System.exit(1);
                        }
                    }
                    else if (argv[argIndex].endsWith(".jar")) {
                        try {
                            Handler.readJarClasses(argv[argIndex], classList);
                        }
                        catch (IOException e) {
                            System.err.println(e.getMessage());
                            System.exit(1);
                        }
                    }
                    else {
                        classList.add(argv[argIndex]);
                    }
                    argIndex += 1;
                }
            }
            
            //System.out.println("traceDir: " + traceDir + "\nthFile: " +
            //                   thFile + "\nprogName: " + progName);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        TestHistoryBuilder thBuilder = new TestHistoryBuilder(typeFlags, tag);
        
        @SuppressWarnings("unused")
        long startTime = System.currentTimeMillis();
        
        if (!thBuilder.buildTestHistory(traceDir, thFile, classList)) {
            System.err.println("Test history construction failed");
            System.exit(1);
        }
        
        @SuppressWarnings("unused")
        long endTime = System.currentTimeMillis();
        //System.out.println("Time elapsed (ms): " + (endTime - startTime));
    }
}
