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

package sofya.ed.structural;

import java.io.*;
import java.util.*;

import sofya.base.Handler;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.*;
import sofya.ed.BlockCoverageListener;
import sofya.ed.BranchCoverageListener;
import sofya.ed.CoverageListenerManager;
import sofya.ed.ParameterValueAbsentException;
import sofya.ed.structural.ActiveComponent;
import sofya.ed.structural.AbstractEventDispatcher.TraceFileException;
import static sofya.base.SConstants.*;

/**
 * The trace handler provides routines to manipulate Sofya coverage
 * trace (.tr) files and to create coverage trace objects.
 *
 * @author Alex Kinneer
 * @version 11/27/2006
 *
 * @see sofya.ed.structural.CoverageTrace
 * @see sofya.viewers.TraceViewer
 */
@SuppressWarnings("unchecked")
public class TraceHandler extends Handler
        implements CoverageListenerManager, ActiveComponent {

    /** Map which links method names to
        {@link sofya.ed.structural.CoverageTrace} objects. */
    private Map<String, Object> methodTraces = new HashMap();
    /** Trace type bit vector. */
    private int typeFlags = 0x00000000;
    /** Program entity for which trace information is recorded in the trace file
        managed by this handler. */
    private TraceObjectType objectType = TraceObjectType.BASIC_BLOCK;

    /** Default trace file name. */
    private String trName = "instout";
    /** Flag specifying whether the handler should append current trace
        information to any existing trace file of the same name. */
    private boolean appendToTrace = false;
    /** Stream to which trace handler error outputs will be printed. */
    private PrintStream stderr = System.err;

    /** Flag that specifies whether trace files should be created in the
        database directory. This is only ever false when the <code>main</code>
        method is performing a merge between two trace files. */
    private boolean toDatabase = true;

    private boolean synch = false;
    
    /** Flag indicating whether the trace handler is ready to receive an
        event stream. */
    private boolean ready = true;

    /**************************************************************************
     * Creates a trace handler with no traces yet registered and the
     * structural entity type set to basic blocks.
     *
     * <p><b>Note:</b> You must call
     * {@link TraceHandler#setTypeFlags} before attempting
     * to save a trace file or an IllegalArgumentException will be thrown.</p>
     */
    public TraceHandler() { }

    /**************************************************************************
     * Creates a trace handler with the specified trace types active and the
     * structural enetity type set to basic blocks.
     *
     * @param typeFlags Bit mask representing the types of blocks that are
     * marked in the traces managed by the handler. Can be any bitwise
     * combination of the following (See {@link sofya.base.SConstants}):
     * <ul>
     * <li><code>SConstants.BlockType.MASK_BASIC</code></li>
     * <li><code>SConstants.BlockType.MASK_ENTRY</code></li>
     * <li><code>SConstants.BlockType.MASK_EXIT</code></li>
     * <li><code>SConstants.BlockType.MASK_CALL</code></li>
     * </ul>
     *
     * @throws IllegalArgumentException If the bit vector doesn't have a bit
     * set which corresponds to a valid block type.
     */
    public TraceHandler(int typeFlags) {
        if ((typeFlags & TraceObjectType.BASIC_BLOCK.validMask()) == 0) {
            throw new IllegalArgumentException("No valid trace type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
        this.ready = true;
    }

    /**************************************************************************
     * Creates a trace handler with the specified trace types and structural
     * entity type.
     *
     * @param typeFlags Bit mask representing the types of objects that are
     * marked in the traces managed by the handler. The meaning of the bits
     * in the vector is determined by <code>objectType</code>.
     * @param objectType Code specifying the type of trace entities recorded
     * by the traces managed by the handler.
     */
    public TraceHandler(int typeFlags, TraceObjectType objectType) {
        if ((typeFlags & objectType.validMask()) == 0) {
            throw new IllegalArgumentException("No valid trace type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
        this.objectType = objectType;
        this.ready = true;
    }

    /*************************************************************************
     * Reports whether the trace handler will merge any new coverage
     * information into any existing trace file of the same name.
     *
     * @return <code>true</code> if the trace handler will merge current
     * coverage information with the information contained in any existing
     * trace file with the same name.
     */
    public boolean isAppending() {
        return appendToTrace;
    }

    /*************************************************************************
     * Sets whether the trace handler will merge any new coverage
     * information into any existing trace file of the same name.
     *
     * @param enable <code>true</code> to instruct the trace handler to
     * merge current coverage information with the information contained
     * in any existing trace file with the same name.
     */
    public void setAppending(boolean enable) {
        appendToTrace = enable;
        ready = true;
    }

    /*************************************************************************
     * Gets the name of the trace file that will be written.
     *
     * @return The name of the trace file.
     */
    public String getTraceFileName() {
        return trName;
    }

    /*************************************************************************
     * Sets the name of the trace file to be written.
     *
     * @param value The name of the trace file to be written.
     */
    public void setTraceFileName(String value) {
        if ((value == null) || (value.length() == 0)) {
            throw new IllegalArgumentException("Trace file name must be " +
                "specified");
        }
        trName = value;
        ready = true;
    }

    public void register(EventDispatcherConfiguration edConfig) {
        this.stderr = edConfig.getStandardError();
    }

    public List<String> configure(List<String> params) {
        Iterator<String> li = params.iterator();
        while (li.hasNext()) {
            String param = li.next();
             if (param.startsWith("-")) {
                if (param.equals("-at")) {
                    li.remove();
                    appendToTrace = true;
                }
                else if (param.equals("-trname")) {
                    li.remove();
                    if (li.hasNext()) {
                        trName = (String) li.next();
                        li.remove();
                    }
                    else {
                        throw new ParameterValueAbsentException("Trace file " +
                            "name not specified");
                    }
                }
            }
        }

        this.ready = true;

        return params;
    }
    
    public void reset() {
        this.typeFlags = 0x00000000;
        this.trName = "instout";
        this.appendToTrace = false;
        this.ready = false;
    }

    public boolean isReady() {
        return ready;
    }

    public void release() {
        reset();
    }

    public void initialize() {
        if (appendToTrace) {
            try {
                readTraceFile(ProjectDescription.dbDir +
                    File.separatorChar + trName + ".tr", true);
            }
            catch (EmptyFileException e) {
                // It will get created.
            }
            catch (FileNotFoundException e) {
                // It will get created.
            }
            catch (BadFileFormatException e) {
                throw new TraceFileException("Cannot append to trace - " +
                    "the existing trace is invalid: " + e.getMessage());
            }
            catch (IOException e) {
                throw new TraceFileException("I/O error: \"" +
                    e.getMessage() + "\" attempting to read existing " +
                    "trace");
            }
        }
    }
    
    public void setSynchronized(boolean synch) {
        this.synch = synch;
    }
    
    public boolean isSynchronized() {
        return synch;
    }

    public void newEventStream(int streamId) {
        if (!appendToTrace) {
            clear();
        }
    }

    public void commitCoverageResults(int streamId) {
        try {
            writeTraceFile(trName, false);
        }
        catch (IOException e) {
            throw new TraceFileException("Cannot write trace file", e);
        }
    }

    public void initializeBlockListener(String classAndSignature,
                                        int blockCount) {
        BlockCoverageTrace trace;
        if (synch) {
            synchronized(this) {
                trace = (BlockCoverageTrace) findTrace(classAndSignature);
                if (trace == null) {
                    trace = new BlockCoverageTrace(blockCount);
                    setTrace(classAndSignature, trace);
                    return;
                }
            }
        }
        else {
            trace = (BlockCoverageTrace) findTrace(classAndSignature);
            if (trace == null) {
                trace = new BlockCoverageTrace(blockCount);
                setTrace(classAndSignature, trace);
                return;
            }
        }
        
        if (trace.getHighestId() != blockCount) {
            throw new IllegalStateException("Method " + classAndSignature +
                " reports inconsistent number of basic blocks or " +
                "branches");
        }
    }

    public void initializeBranchListener(String classAndSignature,
            int branchCount) {
        BranchCoverageTrace trace;
        if (synch) {
            synchronized(this) {
                trace = (BranchCoverageTrace) findTrace(classAndSignature);
                if (trace == null) {
                    trace = new BranchCoverageTrace(branchCount);
                    setTrace(classAndSignature, trace);
                    return;
                }
            }
        }
        else {
            trace = (BranchCoverageTrace) findTrace(classAndSignature);
            if (trace == null) {
                trace = new BranchCoverageTrace(branchCount);
                setTrace(classAndSignature, trace);
                return;
            }
        }

        if (trace.getHighestId() != branchCount) {
            throw new IllegalStateException("Method " + classAndSignature +
                    " reports inconsistent number of basic blocks or " +
                    "branches");
        }
    }

    public BlockCoverageListener getBlockCoverageListener(
            String classAndSignature) {
        return (BlockCoverageListener) findTrace(classAndSignature);
    }

    public BranchCoverageListener getBranchCoverageListener(
            String classAndSignature) {
        return (BranchCoverageListener) findTrace(classAndSignature);
    }
    
    /**************************************************************************
     * Writes a trace file to the Sofya database using the trace information
     * currently registered with the handler.
     *
     * @param fileName Name of the trace file to be written, without extension.
     * @param append If <code>true</code>, data will be appended to the end
     * of the specified trace file, otherwise the existing contents of the
     * trace file will be overwritten.
     *
     * @throws IOException If an error occurs while writing to the trace file.
     */
    public void writeTraceFile(String fileName, boolean append)
                throws IOException {
        FileOutputStream fStream = null;
        if (toDatabase) {
            fStream = openOutputFile(fileName + ".tr", null, append);
        }
        else {
            fStream = openOutputFile(fileName + ".tr", append);
        }

        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(fStream)), true);
        String[] methodNames = getMethodList();
        String traceVector;
        CoverageTrace trace;
        int j, k, vLength, extraChar;
        
        // '3' indicates the beginning of the trace file
        pw.println("3 " + methodNames.length + " " + objectType.toInt() + " " +
                   objectType.toString(typeFlags));

        StringBuilder outputLine = new StringBuilder();
        for (int i = 0; i < methodNames.length; i++) {
            trace = (CoverageTrace) methodTraces.get(methodNames[i]);
            traceVector = trace.getTraceVector();
            vLength = traceVector.length();
            extraChar = vLength % 2;
            pw.println("1 \"" + methodNames[i] + "\" " + trace.getHighestId());

            j = k = 0;
            outputLine.replace(0, outputLine.length(), "2 ");
            while (j < vLength - extraChar) {
                outputLine.append(traceVector.substring(j, j += 2) + " ");
                if (++k == 20) {
                    if (!(j >= vLength)) {
                        pw.println(outputLine.toString());
                        outputLine.replace(0, outputLine.length(), "2 ");
                    }
                    k = 0;
                }
            }
            if (extraChar > 0) {
                outputLine.append(
                    traceVector.substring(vLength - extraChar, vLength));
            }
            pw.println(outputLine.toString());
        }

        pw.close();
        if (pw.checkError()) {
            throw new IOException("Error writing trace file");
        }
    }

    /*************************************************************************
     * Reads a trace file, making the information available for request
     * from the handler. The {@link TraceHandler#getTrace} method
     * can be used to retrieve information loaded by this method.
     *
     * <p>This method assumes that the trace file must contain information
     * about the same type of trace objects (basic blocks or branch edges)
     * that the handler is currently set to operate on (as with
     * {@link TraceHandler#setObjectType}).</p>
     *
     * @param fileName Name of the trace file to be read.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws EmptyFileException If the specified file contains no data.
     * @throws BadFileFormatException If the specified file is not a .tr
     * file or is otherwise malformed or corrupted.
     * @throws IOException If there is an error reading from the .tr file.
     */
    public void readTraceFile(String fileName)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException {
        readTraceFile(fileName, true);
    }

    /*************************************************************************
     * Reads a trace file, making the information available for request
     * from the handler. The {@link TraceHandler#getTrace} method
     * can be used to retrieve information loaded by this method.
     *
     * @param fileName Name of the trace file to be read.
     * @param safe Specifies whether the contents of the trace file
     * should correspond to the same type of trace entities that the handler
     * is currently set to operate on. If <code>true</code>, a
     * <code>BadFileFormatException</code> is thrown if there is a mismatch.
     * If <code>false</code>, the trace entity type for the handler will be
     * changed to object type found in the trace file and the type flags
     * will be set accordingly.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws EmptyFileException If the specified file contains no data.
     * @throws BadFileFormatException If the specified file is not a .tr
     * file or is otherwise malformed or corrupted.
     * @throws IOException If there is an error reading from the .tr file.
     */
    public void readTraceFile(String fileName, boolean safe)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException {
        methodTraces.clear();
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(openInputFile(fileName)));

        try {
            String input = new String();
            try {
                input = readNextLine(br);
            }
            catch (EOFException e) {
                throw new EmptyFileException();
            }

            StringTokenizer stok = new StringTokenizer(input);
            String token;
            int numberOfMethods;
            try {
                stok.nextToken();  // Consume '3'
                numberOfMethods = Integer.parseInt(stok.nextToken());
            }
            catch (NumberFormatException e) {
                throw new BadFileFormatException("Trace file is corrupted: " +
                                 "header information is invalid");
            }
            catch (NoSuchElementException e) {
                throw new BadFileFormatException("Trace file is corrupted: " +
                                 "header information is incomplete");
            }

            try {
                int fileEntityType = Integer.parseInt(stok.nextToken());

                if (safe && (fileEntityType != objectType.toInt())) {
                    throw new BadFileFormatException("Trace file contains " +
                        "information about type of program entity (basic " +
                        "block\nor branch edge) that was not requested");
                }
                else {
                    objectType = TraceObjectType.fromInt(fileEntityType);
                }
            }
            catch (NumberFormatException e) {
                // For compatability - we still want to read old trace files.
                // This exception will be thrown if we tried to read the object
                // type code (block/edge) and got a string instead
                if (safe && (objectType != TraceObjectType.BASIC_BLOCK)) {
                    throw new BadFileFormatException("Trace file contains " +
                        "information about type of program entity (basic " +
                        "block\nor branch edge) that was not requested");
                }
                // Reset the tokenizer back to the token we just tried to read
                stok = new StringTokenizer(input);
                stok.nextToken();
                stok.nextToken();
            }
            catch (NoSuchElementException e) {
                throw new BadFileFormatException("Trace file is corrupted: " +
                                 "header information is incomplete");
            }

            switch (objectType.toInt()) {
            case TraceObjectType.IBASIC_BLOCK:
                while (stok.hasMoreTokens()) {
                    token = stok.nextToken();
                    if (token.equals("Basic")) {
                        typeFlags |= BlockType.MASK_BASIC;
                    }
                    else if (token.equals("Entry")) {
                        typeFlags |= BlockType.MASK_ENTRY;
                    }
                    else if (token.equals("Exit")) {
                        typeFlags |= BlockType.MASK_EXIT;
                    }
                    else if (token.equals("Call")) {
                        typeFlags |= BlockType.MASK_CALL;
                    }
                    else if (token.equals("Return")) {
                        typeFlags |= BlockType.MASK_RETURN;
                    }
                    else {
                        stderr.println("WARNING: Unknown block type " +
                            "contained in trace");
                    }
                }
                break;
            case TraceObjectType.IBRANCH_EDGE:
                while (stok.hasMoreTokens()) {
                    token = stok.nextToken();
                    if (token.equals("If")) {
                        typeFlags |= BranchType.MASK_IF;
                    }
                    else if (token.equals("Switch")) {
                        typeFlags |= BranchType.MASK_SWITCH;
                    }
                    else if (token.equals("Throw")) {
                        typeFlags |= BranchType.MASK_THROW;
                    }
                    else if (token.equals("Call")) {
                        typeFlags |= BranchType.MASK_CALL;
                    }
                    else if (token.equals("Entry")) {
                        typeFlags |= BranchType.MASK_ENTRY;
                    }
                    else if (token.equals("Other")) {
                        typeFlags |= BranchType.MASK_OTHER;
                    }
                    else {
                        stderr.println("WARNING: Unknown branch type " +
                            "contained in trace");
                    }
                }
                break;
            default:
                throw new BadFileFormatException("Unknown type of trace " +
                    "object");
            }

            if ((typeFlags & objectType.validMask()) == 0) {
                throw new BadFileFormatException("No valid trace type found " +
                    "in trace file");
            }

            try {
                input = readNextLine(br);
            }
            catch (EOFException e) {
                throw new EmptyFileException();
            }

            String methodName = null;
            CoverageTrace trace = null;
            StringBuilder traceVector = new StringBuilder();
            int highestBlockID = 0, numLines;
            for (int i = 0; i < numberOfMethods; i++) {
                try {
                    // Read method header information
                    stok = new StringTokenizer(input);
                    if (!(stok.nextToken().startsWith("1"))) {
                        throw new BadFileFormatException("Trace file is " +
                            "corrupted: method information not found where " +
                            "expected");
                    }
                    // Read method name using quotation marks as tokens
                    stok.nextToken("\"");
                    methodName = stok.nextToken();
                    stok.nextToken(" \t\n\r\f");
                    // Get entity counts
                    highestBlockID = Integer.parseInt(stok.nextToken());
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("Trace file is " +
                        "corrupted: method header is incomplete");
                }

                switch (objectType.toInt()) {
                case TraceObjectType.IBASIC_BLOCK:
                    trace = new BlockCoverageTrace(highestBlockID);
                    break;
                case TraceObjectType.IBRANCH_EDGE:
                    trace = new BranchCoverageTrace(highestBlockID);
                    break;
                }

                numLines = 1 + (((highestBlockID - 1) / 8) / 20);
                traceVector.delete(0, traceVector.length());
                try {
                    for (int j = 0; j < numLines; j++) {
                        input = readNextLine(br);
                        stok = new StringTokenizer(input);
                        if (!stok.nextToken().startsWith("2")) {
                            throw new BadFileFormatException("Trace file is " +
                                "corrupted: trace information for method is " +
                                "missing");
                        }
                        while (stok.hasMoreTokens()) {
                            traceVector.append(stok.nextToken());
                        }
                    }
                    trace.setTraceVector(traceVector.toString());
                }
                catch (NoSuchElementException e) {
                    throw new BadFileFormatException("Trace file is " +
                        "corrupted: method trace information is incomplete");
                }
                catch (EOFException e) {
                    throw new BadFileFormatException("Trace file is " +
                        "corrupted: method trace information is incomplete");
                }
                methodTraces.put(methodName, trace);

                try {
                    input = readNextLine(br);
                }
                catch (EOFException e) { break; }
            }
        }
        finally {
            br.close();
        }
    }

    /**************************************************************************
     * Gets the list of methods names for which traces are currently
     * registered with the handler.
     *
     * @return A list of the method names for which traces can be
     * retrieved from this handler.
     */
    public String[] getMethodList() {
        String[] methodNames = (String[]) methodTraces.keySet().toArray(
            new String[methodTraces.size()]);
        Arrays.sort(methodNames);
        return methodNames;
    }
    
    public Set<String> getUnsortedMethodList() {
        return Collections.unmodifiableSet(methodTraces.keySet());
    }

    /**************************************************************************
     * Gets the trace associated with a given method.
     *
     * @param methodName Name of the method for which the trace is to
     * be retrieved.
     *
     * @return The {@link sofya.ed.structural.CoverageTrace} associated with
     * the specified method.
     *
     * @throws MethodNotFoundException If the handler has no trace associated
     * with a method of the specified name.
     */
    public CoverageTrace getTrace(String methodName) throws MethodNotFoundException {
        if (methodTraces.containsKey(methodName)) {
            return (CoverageTrace) methodTraces.get(methodName);
        }
        else {
            throw new MethodNotFoundException(methodName);
        }
    }

    /**************************************************************************
     * Internal helper method to retrieve a trace without any possibility of
     * an exception thrown.
     *
     * @param classAndSignature Name and signature of the method for which
     * the trace is to be retrieved.
     *
     * @return The {@link sofya.ed.structural.CoverageTrace} associated with
     * the specified method, or <code>null</code> if no trace can be found.
     */
    private CoverageTrace findTrace(String classAndSignature) {
        return (CoverageTrace) methodTraces.get(classAndSignature);
    }

    /**************************************************************************
     * Sets the trace associated with a given method.
     *
     * <p>If a test history already exists for the specified method, it is
     * overwritten. Otherwise the method and test history are added to the
     * set of methods registered with the handler.</p>
     *
     * @param methodName Name of the method for which a trace is
     * being specified.
     * @param trace The test history to be associated with the method.
     */
    public void setTrace(String methodName, CoverageTrace trace) {
        methodTraces.put(methodName, trace);
    }

    /**************************************************************************
     * Reports whether the handler contains a trace for the given method.
     *
     * @param methodName Name of the method which the handler should
     * check for a trace.
     *
     * @return <code>true</code> if a trace is registered for the given
     * method, <code>false</code> otherwise.
     */
    public boolean containsTrace(String methodName) {
        return methodTraces.containsKey(methodName);
    }

    /**************************************************************************
     * Gets the type code for the trace entities recorded in the traces being
     * managed by the handler.
     *
     * @return Code indicating the type of trace entities recorded in the
     * traces.
     */
    public TraceObjectType getObjectType() {
        return objectType;
    }

    /**************************************************************************
     * Sets the type code for the trace entities recorded in the traces being
     * managed by the handler.
     *
     * @param type Code specifying the type of trace entities recorded in the
     * traces.
     */
    public void setObjectType(TraceObjectType type) {
        if (type == null) {
            throw new NullPointerException();
        }
        this.objectType = type;
    }

    /*************************************************************************
     * Sets the bit mask indicating what types of objects are marked by the
     * traces being managed by the handler. The meanings of the bits in the
     * bit mask are determined by the trace entity type.
     *
     * @param typeFlags Bit mask representing the types of objects marked
     * by this trace.
     *
     * @throws IllegalArgumentException If the bit mask doesn't have a bit
     * set which corresponds to a valid object type for the current trace
     * entity type.
     */
    public void setTypeFlags(int typeFlags) {
        if ((typeFlags & objectType.validMask()) == 0) {
            throw new IllegalArgumentException("No valid trace type " +
                                               "specified");
        }
        this.typeFlags = typeFlags;
        this.ready = true;
    }

    /*************************************************************************
     * Gets the bit mask indicating what types of objects are marked by the
     * traces being managed by the handler.
     *
     * @return Bit mask controlling what types of objects are marked in the
     * traces.
     */
    public int getTypeFlags() {
        return typeFlags;
    }

    /*************************************************************************
     * Gets a string listing the types of objects marked by the traces being
     * managed by the handler.
     *
     * @return A string listing the types of objects marked by the traces being
     * managed by this handler. The types are delimited by a single space
     * character (ASCII 32).
     */
    public String getTypesString() {
        return objectType.toString(typeFlags);
    }

    /**************************************************************************
     * Removes all of the method traces.
     */
    public void clear() {
        methodTraces.clear();
    }

    /**************************************************************************
     * Merges another trace file with the currently loaded trace file.
     *
     * @param fileName Name of the trace file to be merged with the currently
     * loaded file.
     *
     * @throws FileNotFoundException If the trace file to be merged with cannot
     * be found.
     * @throws EmptyFileException If the trace file to be merged with is empty.
     * @throws BadFileFormatException If the trace file to be merged with is
     * not a .tr file or is otherwise malformed or corrupted.
     * @throws IOException If any other error I/O occurs while trying to read
     * the trace file to be merged with.
     */
    public void mergeTraceFile(String fileName)
                 throws FileNotFoundException, EmptyFileException,
                        BadFileFormatException, IOException {
        TraceHandler mergeTarget = new TraceHandler();
        mergeTarget.readTraceFile(fileName);

        if (this.typeFlags != mergeTarget.typeFlags) {
            throw new IllegalArgumentException("Trace files do not cover " +
                "the same block types");
        }

        String[] mergeMethods = mergeTarget.getMethodList();
        for (int i = 0; i < mergeMethods.length; i++) {
            CoverageTrace mergeTrace = null;
            try {
                mergeTrace = mergeTarget.getTrace(mergeMethods[i]);
            }
            catch (MethodNotFoundException e) {
                throw new SofyaError("Handler falsely reported trace was " +
                    "available for " + e.getMessage() + " in " + fileName);
            }

            if (this.containsTrace(mergeMethods[i])) {
                CoverageTrace myTrace = null;
                try {
                    myTrace = this.getTrace(mergeMethods[i]);
                }
                catch (MethodNotFoundException e) {
                    throw new SofyaError("Handler falsely reported trace " +
                        "was available for " + e.getMessage() + " in " +
                        "currently loaded trace file");
                }
                this.setTrace(mergeMethods[i], myTrace.union(mergeTrace));
            }
            else {
                this.setTrace(mergeMethods[i], mergeTrace);
            }
        }
    }

    protected static String toHex(BitSet bv, int size) {
        return Handler.toHex(bv, size);
    }

    protected static BitSet toBinary(String hexString) {
        return Handler.toBinary(hexString);
    }

    /**************************************************************************
     * Provides a function to merge two trace files, if given the
     * argument &apos;<code>-merge</code>&apos; with appropriate values.
     *
     * <p>Simply runs a couple of basic tests if no arguments are supplied.</p>
     */
    public static void main(String argv[]) {
        TraceHandler trHandler = new TraceHandler();

        if (argv.length > 0) {
            if (argv[0].equals("-merge")) {
                if (argv.length < 4) {
                    System.err.println("Usage:\njava sofya.inst." +
                        "TraceHandler <trace1> <trace2> <merged>");
                    System.exit(1);
                }

                try {
                    trHandler.readTraceFile(argv[1]);
                    trHandler.mergeTraceFile(argv[2]);
                    trHandler.toDatabase = false;
                    trHandler.writeTraceFile(argv[3], false);
                }
                catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                catch (FileNotFoundException e) {
                    System.err.println("Could not find trace file " +
                        e.getMessage());
                    System.exit(1);
                }
                catch (EmptyFileException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            else {
                System.err.println("Unrecognized parameter: " + argv[0]);
                System.exit(1);
            }
        }
        else {
            // Simple test driver code
            trHandler.toDatabase = false;
            try {
                trHandler.readTraceFile("test.tr");
                trHandler.writeTraceFile("test.copy.tr", false);

                trHandler.mergeTraceFile("test2.tr");
                trHandler.writeTraceFile("merged.tr", false);
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}



/*****************************************************************************/

/*
  $Log: TraceHandler.java,v $
  Revision 1.11  2007/08/01 19:05:56  akinneer
  Added method to get traces in unsorted order, to improve performance
  of the test history builder.

  Revision 1.10  2007/01/18 21:49:05  akinneer
  Updated copyright date.

  Revision 1.9  2006/11/29 15:11:23  akinneer
  Modified so that new packed signature format is used in trace files.
  This is the only way to support the "append trace" option in the
  program event dispatcher. A new C utility program is available to map
  the new packed signature format back to the legacy format.

  Revision 1.8  2006/11/16 22:16:53  akinneer
  Updated to handle new packed signature format.

  Revision 1.7  2006/11/08 20:35:21  akinneer
  Partial commit: nio performance improvements and bug fixing -- some things
  may be broken!
  Updated to respect new CoverageListenerManager interface definitions.
  Assumes some responsibility for internal synchronization.

  Revision 1.6  2006/10/06 20:59:22  akinneer
  Fixed bugs related to command line processing.

  Revision 1.5  2006/09/08 21:30:05  akinneer
  Updated copyright notice.

  Revision 1.4  2006/09/08 20:27:39  akinneer
  Generified. Removed constant interface anti-pattern.

  Revision 1.3  2006/07/26 17:30:22  akinneer
  Changed illegal constructor errors to assertion errors; replaced
  StringBuffers with StringBuilders where possible.

  Revision 1.2  2006/04/24 20:42:26  akinneer
  Renamed some methods for consistency.

  Revision 1.1  2006/03/21 22:09:55  kinneer
  Moved from 'inst', or new.

  Revision 1.2  2005/06/06 18:47:47  kinneer
  Minor revisions and added copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.22  2004/05/26 23:23:21  kinneer
  Implemented extensions to read and write branch trace files.

  Revision 1.21  2004/04/16 18:00:32  kinneer
  Added trace file merge method and linked it to command line parameter.

  Revision 1.20  2004/03/12 22:07:16  kinneer
  Modified read methods to ensure release of IO resources if an exception
  is thrown. Deprecated and eliminated references to NoFileNameException,
  which was a spurious annoyance. FileNotFoundException is now used
  instead.

  Revision 1.19  2004/02/18 19:03:34  kinneer
  Added method to test for presence of data for a method. Can be used instead
  of calling a getX method and handling an exception.

  Revision 1.18  2004/02/09 22:40:40  kinneer
  Modified to substitute '_' for spaces in method names.

  Revision 1.17  2004/02/02 19:10:56  kinneer
  All MethodNotFoundExceptions now include the name of the method
  in the exception message.

  Revision 1.16  2003/10/09 23:52:21  kinneer
  Replaced null flags with exceptions.

  Revision 1.15  2003/08/27 18:44:06  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.14  2003/08/18 18:43:01  kinneer
  See v2.1.0 release notes for details.

  Revision 1.13  2003/08/13 18:28:37  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.12  2003/08/01 17:10:47  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.11  2002/09/11 23:29:18  sharmahi
  the condition in the while loop of the writeMethodTraceIno needed a <=

  Revision 1.10  2002/09/11 16:50:19  sharmahi
  made the .tr file format consistent with that of Aristotle to print out 20 ints
  on one line

  Revision 1.9  2002/08/27 22:25:12  sharmahi
  changed output tr file format;removed extra white character

  Revision 1.8  2002/08/19 21:24:53  sharmahi
  changed the output trace file name to instout.tr

  Revision 1.7  2002/08/13 07:39:07  sharmahi
  trace file created name changed to "instrout.tr"

  Revision 1.6  2002/08/07 08:03:16  sharmahi
  changed the TraceVector being queried

  Revision 1.5  2002/07/17 05:52:04  sharmahi
  Added a new method

  Revision 1.4  2002/06/25 09:09:57  sharmahi
  Added Package name "handlers"

  Revision 1.3  2002/06/25 08:15:51  sharmahi
  Added Exceptions

  Revision 1.2  2002/06/09 08:45:27  sharmahi
  After first glance and review of fomrat, code style and file layout

  Revision 1.1  2002/04/16 08:41:45  sharmahi
  Adding after finalizing and code reviews.


*/
