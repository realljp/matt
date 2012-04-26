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

package sofya.graphs.cfg;

import java.util.*;
import java.io.*;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProjectDescription;
import sofya.base.SConstants.BlockType;
import sofya.base.SConstants.BranchType;
import sofya.base.SConstants.BlockSubType;
import sofya.base.exceptions.*;
import sofya.graphs.GraphCache;
import sofya.graphs.Edge;
import sofya.graphs.cfg.CFEdge.BranchID;

import org.apache.bcel.generic.Type;

import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * The CFHandler provides routines to manipulate Sofya control flow
 * (.cf) files and to form control flow graph (CFG) objects.
 *
 * @author Alex Kinneer
 * @version 10/13/2006
 *
 * @see sofya.graphs.cfg.CFG
 * @see sofya.graphs.cfg.MapHandler
 * @see sofya.viewers.CFViewer
 */
@SuppressWarnings("unchecked")
public class CFHandler extends Handler {
    /** Map which links method signatures to {@link sofya.graphs.cfg.CFG}s. */
    private GraphCache<CFG> cfgs;
    /** {@link sofya.handlers.MapHandler} for related .map file. */
    private MapHandler mapHandler;
    
    /** Records what class we believe is loaded. This value can only
        be set internally by reading a CF file. It may be used later
        if a request is received to save a new or modified CF file
        based on the loaded CF data. */
    private String className = null;
    
    /** Flag which is only set internally during loading of a CF file
        if it is determined that it is a 'legacy' CF file (one which
        does not store method signatures in a parseable format).
        If set, it modifies how CFGs are stored and keyed, and how the
        CF file is written. */
    private boolean legacyCF = !HANDLER_EXTENSIONS;
    /** Flag which specifies whether the control flow graphs should be
        written to file using the same sorting criterion applied
        up through v3.3.0 of Galileo. */
    private boolean legacySort = false;
    /** Cache which stores and keys CFGs in the 'legacy' fashion, used
        if the legacyCF flag is set. When in use, the handler effectively
        behaves in the same way that it did up to v3.3.0 of Galileo. */
    private Map<Object, CFG> legacyCache = new THashMap();
    
    /** Conditional compilation flag which controls whether extensions defined
        for dealing with exception handling constructs are written to the .cf
        file. Setting this value to false ensures compatibility with the
        Aristotle control flow file format, at the expense of information
        critical for uniquely identifying edges in the presence of certain
        exception handling constructs. Setting this value to false
        CAUSES DATA LOSS!
      */
    private static final boolean CF_EXTENSIONS = true;
    
    /** Conditional compilation flag which controls whether branch IDs
        are written to the .cf file.
     */
    private static final boolean BRANCH_EXTENSIONS =
        ProjectDescription.ENABLE_BRANCH_EXTENSIONS;
    
    /*************************************************************************
     * Default constructor, creates a handler with a new cache for CFG
     * storage.
     */
    public CFHandler() {
        cfgs = new GraphCache<CFG>(new CFGSerializer());
        mapHandler = new MapHandler(cfgs);
    }
    
    /*************************************************************************
     * Creates a handler which is backed by the given CFG cache.
     *
     * @param sharedCache CFG cache from which the handler will retrieve
     * CFGs for writing to file.
     */
    public CFHandler(GraphCache<CFG> sharedCache) {
        cfgs = sharedCache;
        mapHandler = new MapHandler(cfgs);
    }
    
    /*************************************************************************
     * Writes current control flow information to .cf file.
     *
     * <p>The control flow graphs currently registered with the handler
     * are written to a <code>.cf</code> file. If necessary (and permitted),
     * the  file is created and placed in the Sofya database directory
     * automatically.</p>
     *
     * @param fileName Name of the control flow (.cf) file to be written.
     *
     * @throws FileNotFoundException If the specified file doesn't exist
     * and can't be created.
     * @throws IOException If there is an error writing to the .cf file.
     */
    public void writeCFFile(String fileName)
                throws FileNotFoundException, IOException {
        // If this old method is being called, we'll assume the old style
        // sorting is expected
        setLegacySort(true);
        
        // Try to determine the class name
        if (!legacyCF) {
            if (className != null) {
                writeCFFile(className, fileName, null);
            }
            else {
                Iterator methods = cfgs.keySet().iterator();
                if (methods.hasNext()) {
                    MethodSignature ms = (MethodSignature) methods.next();
                    writeCFFile(ms.getClassName(), fileName, null);
                }
                else {
                    throw new IllegalStateException("Cannot create CF file " +
                        "without valid class name");
                }
            }
        }
        else {
            writeCFFile(null, fileName, null);
        }
    }
    
    /*************************************************************************
     * Writes current control flow information to .cf file.
     *
     * <p>The CFGs found in the cache for a specified class are written
     * to a <code>.cf</code> file. If necessary (and permitted), the  file
     * is created and placed in the Sofya database directory
     * automatically.</p>
     *
     * @param className Name of the class for which control flow graphs
     * are to be written to file.
     * @param fileName Name of the control flow (.cf) file to be written.
     * @param tag Database tag to be associated with the file.
     *
     * @throws FileNotFoundException If the specified file doesn't exist
     * and can't be created.
     * @throws IOException If there is an error writing to the .cf file.
     */
    public void writeCFFile(String className, String fileName, String tag)
                throws FileNotFoundException, IOException {
        Date date = new Date();
        
        Iterator methods;
        if (legacyCF) {
            methods = new TreeSet<Object>(legacyCache.keySet()).iterator();
        }
        else {
            if (className == null) {
                throw new NullPointerException();
            }
            
            if (legacySort) {
                TreeMap<Object, Object> legacyKeys = new TreeMap<Object, Object>();
                Iterator keys = cfgs.iterator(className);
                while (keys.hasNext()) {
                    CFG cfg = (CFG) keys.next();
                    legacyKeys.put(cfg.getMethodName(), cfg.getSignature());
                }
                methods = legacyKeys.values().iterator();
            }
            else {
                methods = cfgs.sortedIterator(className);
            }
        }
         
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName + ".cf", tag, false))),
                             true);
        
        pw.println("0 Control Flow Information");
        pw.println("0 File: " + fileName + " Created: " + date); 
        pw.println("0 version " + ProjectDescription.versionString);
        pw.println("0");
           
        CFG cfg = null;
        String label = null, auxLabel = null;
        StringBuffer data = new StringBuffer();
        while (methods.hasNext()) {
            if (legacyCF) {
                cfg = (CFG) legacyCache.get(methods.next());
            }
            else {
                MethodSignature key = (MethodSignature) methods.next();
                cfg = (CFG) cfgs.get(key).getGraph();
            }
            
            pw.print("1 \"" + cfg.getMethodName()  + "\" " +
                     cfg.getNumberOfNodes() + " " + cfg.getHighestNodeId());
            if (BRANCH_EXTENSIONS) {
                pw.print(" " + cfg.getNumberOfBranches() + " " +
                         cfg.getSummaryBranchID());
            }
            pw.println();
            
            if (HANDLER_EXTENSIONS) {
                if (!legacyCF) {
                    MethodSignature ms = cfg.getSignature();
                    pw.println("4 " +  ms.getClassName() + "#" +
                        ms.getMethodName() + "#" +
                        Type.getMethodSignature(ms.getReturnType(),
                                                ms.getArgumentTypes()));
                }
                else {
                    pw.println("4 0");
                }
            }
            else {
                pw.println("4 0");
            }
            
            CFEdge[] edges = (CFEdge[]) cfg.getEdges(CFEdge.ZL_ARRAY);
            for (int j = 0; j < edges.length; j++) {
                CFEdge edge = (CFEdge) edges[j];
                label = edge.getLabel();
                auxLabel = edge.getAuxLabel();
                data.append("3 ");
                
                if (CF_EXTENSIONS) {
                    data.append(edge.getID() + " ");
                }
                
                data.append(edge.getSuccNodeID() + " " + edge.getPredNodeID());
                
                if (BRANCH_EXTENSIONS) {
                    BranchID[] branchIDs = edge.getBranchIDArray();
                    data.append(" ");
                    data.append(branchIDs[0].getID());
                    data.append(":");
                    data.append(branchIDs[0].getType().toInt());
                    for (int k = 1; k < branchIDs.length; k++) {
                        data.append(",");
                        data.append(branchIDs[k].getID());
                        data.append(":");
                        data.append(branchIDs[k].getType().toInt());
                    }
                }
                
                if ((label != null) && (label.length() != 0)) {
                    data.append(" " + label);
                    if (CF_EXTENSIONS) {
                        if (auxLabel != null) {
                            data.append(":" + auxLabel);
                        }
                        if (edge.getSpecialNodeID() != -1) {
                            if (auxLabel == null) { data.append(":null"); }
                            data.append(":" +
                                String.valueOf(edge.getSpecialNodeID()));
                        }
                    }
                }
                
                pw.println(data.toString());
                data.delete(0, data.length());
            }
            
            pw.println("0 end of method " + cfg.getMethodName());
        }

        pw.close();
        if (pw.checkError()) {
            throw new IOException("Error writing control flow file");
        }
    }
    
    /*************************************************************************
     * Reads control flow information from a .cf file.
     *
     * <p>The control flow information is read from the file and stored to
     * internal data structures. This data can then be retrieved via other
     * accessor functions in this class, such as
     * {@link sofya.graphs.cfg.CFHandler#getCFG}. The .cf file is
     * assumed to be located in the Sofya database directory.</p>
     *
     * @param fileName Name of the control flow file to be read.
     * @param tag Database tag associated with the file.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws EmptyFileException If the specified file contains no data.
     * @throws BadFileFormatException If the specified file is not a .cf
     * file or is otherwise malformed or corrupted.
     * @throws IOException If there is an error reading from the .cf file.
     */
    public void readCFFile(String fileName, String tag)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException {
        legacyCF = !HANDLER_EXTENSIONS;
        
        mapHandler.readMapFile(fileName, tag);
        
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                openInputFile(fileName + ".cf", tag)));
        StreamTokenizer stok = new StreamTokenizer(br);
        prepareTokenizer(stok);
        
        try {
            if (!readToEOL(stok)) {
                throw new EmptyFileException();
            }
        
            String token = null;
            for (int i = 0; i < 3; i++) {
                if (stok.nextToken() == StreamTokenizer.TT_EOF) {
                    throw new BadFileFormatException("Control flow file " +
                        "is incomplete");
                }
                token = stok.sval;
            }      
                
            if (!token.equals(fileName)) {
                throw new BadFileFormatException("File name does not " +
                    "match class name in header: " + token);
            }
            else {
                className = token.substring(0, token.lastIndexOf('.'));
            }
            
            for (int i = 0; i < 3; i++) {
                if (!readToEOL(stok)) {
                    throw new EmptyFileException();
                }
            }
            
            CFG cfg = null;
            String methodName = null;
            int branchCount = 0, summaryBrID = -1;
            outer:
            while (true) {
                // Read method header information
                if (readInt(stok) != 1) {
                    throw new BadFileFormatException("Control flow file " +
                        "is corrupted: method header not found where " +
                        "expected");
                }
                
                // Read method name using quotation marks as tokens
                stok.whitespaceChars('"', '"');
                methodName = readString(stok);
                stok.wordChars('"', '"');
                    
                // Get entity counts
                readInt(stok);
                readInt(stok);
                if (BRANCH_EXTENSIONS) {
                    branchCount = readInt(stok);
                    summaryBrID = readInt(stok);
                }
                
                if (!readToNextDataLine(stok)) {
                    throw new DatabaseEOFException();
                }
                if (readInt(stok) != 4) {
                    throw new BadFileFormatException("Control flow file " +
                        "structure is invalid: illegal line type at line: " +
                        stok.lineno());
                }
                
                if (HANDLER_EXTENSIONS) {
                    readSig:
                    if (!legacyCF) {
                        MethodSignature mSig = null;
                        try {
                            stok.whitespaceChars('#', '#');
                            String clName = readString(stok);
                            String mName = readString(stok);
                            String signatureStr = readString(stok);
                            mSig = new MethodSignature(clName, mName,
                                Type.getReturnType(signatureStr),
                                Type.getArgumentTypes(signatureStr));
                            className = clName;
                            stok.wordChars('#', '#');
                        }
                        catch (DataTypeException e) {
                            legacyCF = true;
                            System.err.println("INFO: Legacy control " +
                                "flow file. Consider rebuilding with " +
                                "the current version of Galileo.");
                            stok.wordChars('#', '#');
                            break readSig;
                        }
                        
                        try {
                            cfg = mapHandler.getCFG(mSig);
                        }
                        catch (MethodNotFoundException e) {
                            // This shouldn't happen
                            throw new BadFileFormatException("Unable to " +
                                "read map information: " + e.getMessage());
                        }
                    }
                }
                if (!readToNextDataLine(stok)) {
                    throw new DatabaseEOFException();
                }
                
                if (legacyCF) {
                    try {
                        cfg = mapHandler.getCFG(methodName);
                    }
                    catch (MethodNotFoundException e) {
                        // This shouldn't happen
                        throw new BadFileFormatException("Unable to read " +
                            "map information: " + e.getMessage());
                    }
                }
                
                if (BRANCH_EXTENSIONS) {
                    cfg.setNumberOfBranches(branchCount);
                    cfg.setSummaryBranchID(summaryBrID);
                }
            
                while (readInt(stok) == 3) {
                    CFEdge edge = new CFEdge();
                    
                    if (CF_EXTENSIONS) {
                        edge.setID(readInt(stok));
                    }
                    
                    edge.setSuccNodeID(readInt(stok));
                    edge.setPredNodeID(readInt(stok));
                    
                    if (BRANCH_EXTENSIONS) {
                        stok.ordinaryChar(' ');
                        stok.ordinaryChar(',');
                        stok.ordinaryChar(':');
                        int bid = -1;
                        boolean isType = false;
                        
                        boolean foundIDs = false;
                        brLoop:
                        while (true) {
                            switch (stok.nextToken()) {
                            case ' ':
                                if (foundIDs) {
                                    break brLoop;
                                }
                                else {
                                    continue;
                                }
                            case ':':
                                isType = true;
                                continue;
                            case ',':
                                if (!isType) {
                                    edge.addBranchID(bid, BranchType.DONTCARE);
                                }
                                else {
                                    isType = false;
                                }
                                continue;
                            case StreamTokenizer.TT_NUMBER:
                                if (isType) {
                                    edge.addBranchID(bid,
                                        BranchType.fromInt((int) stok.nval));
                                }
                                else {
                                    bid = (int) stok.nval;
                                    foundIDs = true;
                                }
                                break;
                            case StreamTokenizer.TT_EOL:
                                if (foundIDs) {
                                    break brLoop;
                                }
                                else {
                                    throw new BadFileFormatException(
                                        "Branch ID information is " +
                                        "incomplete at line " + stok.lineno());
                                }
                            case StreamTokenizer.TT_WORD:
                                throw new DataTypeException("Non-numeric " +
                                    "branch ID", stok.lineno());
                            default:
                                throw new DatabaseEOFException();
                            }
                        }
                        if (!isType) {
                            edge.addBranchID(bid,
                                BranchType.DONTCARE);
                        }
                        
                        stok.whitespaceChars(' ', ' ');
                        stok.wordChars(',', ',');
                        stok.wordChars(':', ':');
                    }
                    
                    if (stok.ttype != StreamTokenizer.TT_EOL) {
                        stok.whitespaceChars(':', ':');
                        try {
                            disableParseNumbers(stok);
                            edge.setLabel(readString(stok));
                            edge.setAuxLabel(readString(stok));
                            stok.parseNumbers();
                            edge.setSpecialNodeID(readInt(stok));
                        }
                        catch (EOLException e) {
                            // Ignore, it's optional anyway
                            stok.parseNumbers();
                        }
                        stok.wordChars(':', ':');
                        
                        if (stok.ttype != StreamTokenizer.TT_EOL) {
                            readToEOL(stok);
                        }
                    }
                    
                    cfg.addEdge(edge);

                    if (!readToNextDataLine(stok)) {
                        if (legacyCF) {
                            legacyCache.put(methodName, cfg);
                        }
                        else {
                            cfgs.get(cfg.getSignature()).setComplete(true);
                        }
                        break outer;
                    }
                }
                stok.pushBack();
                
                if (legacyCF) {
                    legacyCache.put(methodName, cfg);
                }
                else {
                    cfgs.get(cfg.getSignature()).setComplete(true);
                }
            }
        }
        finally {
            br.close();
        }
    }
    
    /*************************************************************************
     * Writes a graph to a file in the GraphViz &quot;dot&quot; file
     * format.
     * 
     * @param fileName Name of the dot file to write.
     * @param cfg Control flow graph to be written.
     * 
     * @throws IOException On any error that prevents successful creation
     * or writing of the file.
     */
    public void writeDotFile(String fileName, CFG cfg) throws IOException {
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName + ".dot", false))), true);
        pw.println("digraph \"" + cfg.getSignature().toString() + "\" {");
        pw.println("    // Basic blocks");
        
        TIntObjectHashMap blkNames = new TIntObjectHashMap();
        Block[] blocks = cfg.getBasicBlocks();
        int len = blocks.length;
        for (int i = 0; i < len; i++) {
            Block blk = blocks[i];
            blkNames.put(blk.getID(), printBlockDot(pw, blk));
        }
        
        pw.println("    // Edges");
        StringBuilder label = new StringBuilder();
        List<Edge> edges = cfg.edgeList();
        int edgeCnt = edges.size();
        Iterator<Edge> edgeIter = edges.iterator();
        for (int i = edgeCnt; i-- > 0; ) {
            CFEdge edge = (CFEdge) edgeIter.next();
            String predName = (String) blkNames.get(edge.getPredNodeID());
            String succName = (String) blkNames.get(edge.getSuccNodeID());
            
            label.append("[");
            SortedSet<BranchID> brIds = edge.getBranchIDSet();
            int brCnt = brIds.size();
            Iterator<BranchID> brIter = brIds.iterator();
            for (int j = brCnt; j-- > 0; ) {
                BranchID brId = brIter.next();
                label.append(brId.getID());
                if (j != 0) {
                    label.append(",");
                }
            }
            label.append("]");
            String edgeLabel = edge.getLabel();
            if (edgeLabel != null) {
                label.append(" ");
                label.append(edge.getLabel());
            }
            
            pw.println("    " + predName + " -> " + succName + " [label=\"" +
                label.toString() + "\"]");
            label.setLength(0);
        }
        
        pw.println("}");
    }
    
    /*************************************************************************
     * Writes a basic block to a GraphViz dot file, providing appropriate
     * formatting based on the block type.
     * 
     * @param pw Writer attached to the output file.
     * @param blk Basic block to be written to the dot file.
     * 
     * @return The name used in the dot file to identify the basic block,
     * needed to construct edges.
     */
    private static final String printBlockDot(PrintWriter pw, Block blk) {
        String nodeName;
        String nodeLabel;
        String nodeShape;
        String margin = null;
        String blkId = String.valueOf(blk.getID());
        
        switch (blk.getType().toInt()) {
        case BlockType.IENTRY:
            nodeName = "E";
            nodeLabel = blkId + "(" + nodeName + ")";
            nodeShape = "circle";
            margin = "0";
            break;
        case BlockType.IBLOCK:
            nodeName = "b" + blkId;
            nodeLabel = blkId;
            
            switch (blk.getSubType().toInt()) {
            case BlockSubType.IIF:
            case BlockSubType.ISWITCH:
                nodeShape = "diamond";
                margin = "0.02";
                break;
            default:
                nodeShape = "box";
                break;
            }
            break;
        case BlockType.ICALL:
            nodeName = "b" + blkId;
            nodeLabel = blkId + "(C)";
            nodeShape = "ellipse";
            break;
        case BlockType.IEXIT:
            nodeName = "b" + blkId;
            nodeLabel = blkId + "(X)";
            
            switch (blk.getSubType().toInt()) {
            case BlockSubType.ITHROW:
            case BlockSubType.ISUMMARYTHROW:
                nodeShape = "doublecircle";
                margin = "0";
                break;
            default:
                nodeShape = "circle";
                margin = "0";
                break;
            }
            break;
        case BlockType.IRETURN:
            nodeName = "b" + blkId;
            nodeLabel = blkId;
            nodeShape = "box";
            break;
        default:
            nodeName = "b" + blkId;
            nodeLabel = "\"<unknown>(" + blkId + ")\"";
            nodeShape = "doubleoctagon";
            break;
        }
        
        pw.print("    " + nodeName + " [label=\"" + nodeLabel + "\\n[" +
            blk.getStartOffset() + ":" + blk.getEndOffset() +
            "]\",shape=" + nodeShape + ",height=0.01,width=0.01");
        if (margin != null) {
            pw.println(",margin=" + margin);
        }
        pw.println("];");

        
        return nodeName;
    }
    
    /*************************************************************************
     * Adds {@link sofya.graphs.cfg.CFG} to the current .cf file, using the
     * method name or signature associated with the given <code>CFG</code>
     * object.
     *
     * @param cfg {@link sofya.graphs.cfg.CFG} to be added to the control flow
     * information in the currently opened .cf file.
     */
    public void addCFG(CFG cfg) {
        if (legacyCF || (cfg.getSignature() == null)) {
            legacyCache.put(cfg.getMethodName(), cfg);
            legacyCF = true;
        }
        else {
            cfgs.put(cfg.getSignature(), cfg);
        }
    }
    
    /**************************************************************************
     * Queries whether a CFG exists for a given method.
     *
     * @param signature Signature of the method which the handler should
     * query for a CFG.
     *
     * @return <code>true</code> if a CFG is available for the given method,
     * <code>false</code> otherwise.
     */
    public boolean containsCFG(MethodSignature signature) {
        if (legacyCF) {
            System.err.println("WARNING: Legacy CF file does not support " +
                "signatures");
        }
        return cfgs.containsKey(signature);
    }
    
    /**************************************************************************
     * Queries whether a CFG exists for a given method.
     *
     * @param methodName Name of the method which the handler should
     * query for a CFG.
     *
     * @return <code>true</code> if a CFG is available for the given method,
     * <code>false</code> otherwise.
     */
    public boolean containsCFG(String methodName) {
        if (legacyCF) {
            return legacyCache.containsKey(methodName);
        }
        else {
            return (getWithLegacyName(methodName) != null);
        }
    }

    /*************************************************************************
     * Gets {@link sofya.graphs.cfg.CFG} for a method.
     *
     * @param signature Signature of the method whose
     * {@link sofya.graphs.cfg.CFG} is to be retrieved.
     *
     * @return {@link sofya.graphs.cfg.CFG} constructed for the
     * specified method.
     *
     * @throws MethodNotFoundException If the handler has no CFG associated
     * with a method with the specified signature.
     */
    public CFG getCFG(MethodSignature signature)
               throws MethodNotFoundException {
        if (legacyCF) {
            throw new MethodNotFoundException("Legacy CF file does not " +
                "support signatures");
        }
        return (CFG) cfgs.get(signature).getGraph();
    }
    
    /*************************************************************************
     * Gets {@link sofya.graphs.cfg.CFG} for a method.
     *
     * @param methodName Name of the method whose
     * {@link sofya.graphs.cfg.CFG} is to be retrieved.
     *
     * @return {@link sofya.graphs.cfg.CFG} constructed for the
     * specified method.
     *
     * @throws MethodNotFoundException If the handler has no CFG associated
     * with a method of the specified name.
     */
    public CFG getCFG(String methodName) throws MethodNotFoundException {
        if (legacyCF) {
            if (legacyCache.containsKey(methodName)) {
                return (CFG) legacyCache.get(methodName);
            }
            else {
                throw new MethodNotFoundException(methodName);
            }
        }
        else {
            CFG cfg = getWithLegacyName(methodName);
            if (cfg == null) {
                throw new MethodNotFoundException(methodName);
            }
            else {
                return cfg;
            }
        }
    }
    
    /*************************************************************************
     * Internal helper method to retrieve a method using its 'legacy'
     * key (method name).
     *
     * <p>This is used to support legacy query and retrieval methods. It
     * is potentially much slower than a lookup by signature.</p>
     *
     * @param methodName Name of the method whose
     * {@link sofya.graphs.cfg.CFG} is to be retrieved.
     *
     * @return {@link sofya.graphs.cfg.CFG} constructed for the specified
     * method, or <code>null</code> if no match is found.
     */
    private CFG getWithLegacyName(String methodName) {
        // This is slow - may have to search all entries in cache
        Iterator methods = cfgs.iterator();
        while (methods.hasNext()) {
            CFG cfg = (CFG) methods.next();
            if (cfg.getMethodName().equals(methodName)) {
                return cfg;
            }
        }
        return null;
    }
    
    /*************************************************************************
     * Gets the list of pretty-printed methods associated with control
     * flow graphs registered with the handler.
     *
     * @return List of names of the methods the handler knows about.
     */ 
    public String[] getMethodList() {
        String[] methodList;
        if (legacyCF) {
            methodList = (String[]) legacyCache.keySet()
                .toArray(new String[legacyCache.size()]);
        }
        else {
            methodList = new String[cfgs.size()];
            Iterator methods = cfgs.iterator();
            int i = 0;
            while (methods.hasNext()) {
                CFG cfg = (CFG) methods.next();
                methodList[i++] = cfg.getMethodName();
            }
        }
        Arrays.sort(methodList);
        return methodList;
    }
    
    /*************************************************************************
     * Gets the list of method signatures for control flow graphs registered
     * with the handler.
     *
     * @return List of names of the methods the handler knows about.
     */ 
    public MethodSignature[] getSignatureList() {
        if (legacyCF) {
            System.err.println("WARNING: Legacy CF file does not support " +
                "signatures");
            return new MethodSignature[0];
        }
        else {
            Set<MethodSignature> keys = cfgs.keySet();
            MethodSignature[] sigList = keys.toArray(
                new MethodSignature[cfgs.size()]);
            Arrays.sort(sigList, new MethodSignature.NameComparator());
            return sigList;
        }
    }
    
    /*************************************************************************
     * Gets the list of method signatures for control flow graphs from a
     * specific class which are registered with the handler.
     *
     * @param className Name of the class for which associated method
     * signatures are to be retrieved.
     *
     * @return List of names of the methods the handler knows about.
     */ 
    public MethodSignature[] getSignatureList(String className) {
        if (legacyCF) {
            System.err.println("WARNING: Legacy CF file does not support " +
                "signatures");
            return new MethodSignature[0];
        }
        else {
            Set<MethodSignature> keys = cfgs.keySet(className);
            MethodSignature[] sigList = keys.toArray(
                new MethodSignature[keys.size()]);
            Arrays.sort(sigList, new MethodSignature.NameComparator());
            return sigList;
        }
    }
    
    /*************************************************************************
     * Gets an iterator over all of the control flow graphs currently
     * registered with the handler.
     *
     * @return An iterator over the control flow graphs currently registered
     * with the handler.
     */ 
    public Iterator<CFG> iterator() {
        if (legacyCF) {
            return legacyCache.values().iterator();
        }
        else {
            return cfgs.iterator();
        }
    }
    
    /*************************************************************************
     * Gets a sorted iterator over all of the control flow graphs currently
     * registered with the handler.
     *
     * <p>The order of iteration is consistent with the lexical ordering
     * of the names of the methods with which the graphs are associated.</p>
     *
     * @return An iterator over the graphs in the cache, which iterates
     * over the graphs in the order determined by a lexical sorting of
     * the names of the methods with which the graphs are associated.
     *
     * @see sofya.base.MethodSignature.NameComparator
     */ 
    public Iterator<CFG> sortedIterator() {
        if (legacyCF) {
            TreeMap<Object, CFG> sorted =
                new TreeMap<Object, CFG>(legacyCache);
            return sorted.values().iterator();
        }
        else {
            return cfgs.sortedIterator();
        }
    }
    
    /*************************************************************************
     * Specifies whether the control flow graphs should be written to file
     * using the same sorting criterion applied up through v3.3.0 of Galileo.
     *
     * @param enabled <code>true</code> to enable legacy sorting,
     * </code>false</code> otherwise.
     */
    public void setLegacySort(boolean enabled) {
        legacySort = enabled;
    }
    
    /*************************************************************************
     * Gets whether the control flow graphs are to be written to file
     * using the same sorting criterion applied up through v3.3.0 of Galileo.
     *
     * @return <code>true</code> if legacy sorting is enabled,
     * <code>false</code> otherwise.
     */
    public boolean usingLegacySort() {
        return legacySort;
    }
    
    /*************************************************************************
     * Specifies whether previously cached control flow graphs should be
     * cleared whenever a new control flow file is read. This is true by
     * default.
     *
     * <p>Normally it is the contract of the handler to only retain graphs
     * from the most recently loaded control flow file. This may be disabled
     * under certain special circumstances for performance reasons.</p>
     *
     * @param enabled <code>true</code> to have existing control flow graphs
     * removed from the cache whenever a new control flow file is read,
     * <code>false</code> to permit the handler to cache control flow graphs
     * from multiple control flow files.
     */
    public void setClearOnLoad(boolean enabled) {
        // Since this handler shares its cache with a map handler, and the
        // map file is always read first, we can defer this setting to
        // the map handler
        mapHandler.setClearOnLoad(enabled);
    }
    
    /*************************************************************************
     * Gets whether the handler clears existing control flow graphs from its
     * cache when a new control flow file is read.
     *
     * @return <code>true</code> if existing control flow graphs are cleared
     * whenever a new control flow file is read, <code>false</code> if the
     * handler will retain control flow graphs from multiple control flow
     * files in its cache.
     */
    public boolean isClearedOnLoad() {
        // Since this handler shares its cache with a map handler, and the
        // map file is always read first, we can defer this setting to
        // the map handler
        return mapHandler.isClearedOnLoad();
    }
    
    /*************************************************************************
     * Gets the name of the class which the handler believes is currently
     * loaded, which is set only when a CF file has been loaded by the
     * handler.
     *
     * @return The name of the class for which control flow information was
     * loaded from file.
     */ 
    public String getClassName() {
        return className;
    }
    
    /*************************************************************************
     * Test driver for CFHandler.
     */
    public static void main(String argv[]) {
        CFHandler readCfHandler = new CFHandler();
        CFHandler writeCfHandler = new CFHandler();
        
        //BRANCH_EXTENSIONS = false;
        try {
            readCfHandler.readCFFile(argv[0], null);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage()) ;
            System.exit(1);
        }
        
        String methods[] = readCfHandler.getMethodList();
        
        try {
            for (int i = 0; i < methods.length; i++) {
                CFG cfg = readCfHandler.getCFG(methods[i]);
                writeCfHandler.addCFG(cfg);
                
                writeCfHandler.writeDotFile(methods[i], cfg);
            }
        }
        catch (MethodNotFoundException shouldntHappen) {
            System.err.println("Critical error: handler is reporting " +
                "it can provide CFG for method for which it does not " +
                "have any actual CFG stored");
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        //BRANCH_EXTENSIONS = true;
        try {
            writeCfHandler.writeCFFile(argv[1]);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            System.out.println(e.getMessage()) ;
            System.exit(1);
        }
    }
}



/*****************************************************************************/
/*
 $Log: CFHandler.java,v $
 Revision 1.9  2007/07/30 16:17:49  akinneer
 Updated year in copyright notice.

 Revision 1.8  2006/10/16 14:31:42  akinneer
 Added method to generate GraphViz dot file for CFG.

 Revision 1.7  2006/09/08 21:30:08  akinneer
 Updated copyright notice.

 Revision 1.6  2006/09/08 20:12:24  akinneer
 - Eliminated use of constant interface anti-pattern in favor of static
   imports.
 - Added generated SerialUID values to exception classes.
 - "Generified".

 Revision 1.5  2006/03/21 21:50:33  kinneer
 Updated JavaDocs to reflect post-refactoring package organization.
 Various minor code cleanups. Modified copyright notice.

 Revision 1.4  2005/06/06 18:47:25  kinneer
 Minor revisions and added copyright notices.

 Revision 1.3  2005/03/30 22:28:02  kinneer
 Made an error message more informative.

 Revision 1.2  2005/02/24 19:18:58  kinneer
 Modified to utilize fact that cache is shared with MapHandler when
 reading from database.

 Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
 Sofya Java Bytecode Instrumentation and Analysis System

 Revision 1.27  2004/09/14 19:14:48  kinneer
 Wrote JavaDocs for new methods.

 Revision 1.26  2004/08/30 19:55:03  kinneer
 Modified to use disk-cache backed storage for CFGs and implemented
 functions to support modifications to the GraphBuilder to facilitate
 improved CFG building algorithms.

 Revision 1.25  2004/08/20 16:48:18  akinneer
 Linked branch info conditional compilation flag to new Galileo flag
 in ProjectDescription.

 Revision 1.24  2004/06/28 16:54:32  kinneer
 *** empty log message ***

 Revision 1.23  2004/05/26 23:21:14  kinneer
 Implemented extensions to write branch ID data to CF files.

 Revision 1.22  2004/03/12 22:07:16  kinneer
 Modified read methods to ensure release of IO resources if an exception
 is thrown. Deprecated and eliminated references to NoFileNameException,
 which was a spurious annoyance. FileNotFoundException is now used
 instead.

 Revision 1.21  2004/02/18 19:03:34  kinneer
 Added method to test for presence of data for a method. Can be used instead
 of calling a getX method and handling an exception.

 Revision 1.20  2004/02/02 19:10:55  kinneer
 All MethodNotFoundExceptions now include the name of the method
 in the exception message.

 Revision 1.19  2004/01/07 20:48:57  kinneer
 Trivial updates to reflect changes in ProjectDescription. Some fields
 made final in Handler class.

 Revision 1.18  2003/11/13 23:17:54  kinneer
 Added conditional compilation flag to control whether new edge data
 extensions (used with throw,jsr,ret edges) are written to file.

 Revision 1.17  2003/11/12 23:17:43  kinneer
 New handler to manage test selection files which record data collected
 by DejaVu.

 Revision 1.15  2003/09/25 16:38:37  kinneer
 Eliminated all null flags. Requesting objects for methods which do
 not exist now cause MethodNotFoundExceptions to be thrown.

 Revision 1.14  2003/08/27 18:44:05  kinneer
 New handlers architecture. Addition of test history related classes.
 Part of release 2.2.0.

 Revision 1.13  2003/08/18 18:42:41  kinneer
 See v2.1.0 release notes for details.

 Revision 1.12  2003/08/13 18:28:36  kinneer
 Release 2.0, please refer to release notes for details.

 Revision 1.11  2003/08/01 17:10:46  kinneer
 All file handler implementations changed from HashMaps to TreeMaps.
 See release notes for additional details.  Version string for
 Galileo has been set.

 All classes cleaned for readability and JavaDoc'ed.

 Revision 1.10  2002/09/03 21:35:34  sharmahi
 predecessor information changed to get printed after successor information.

 Revision 1.9  2002/07/08 04:44:51  sharmahi
 Made output cf file format changes

 Revision 1.8  2002/07/08 00:59:26  sharmahi
 galileo/src/handlers/AbstractFile.java

 Revision 1.7  2002/07/04 06:56:48  sharmahi
 galileo/src/handlers/AbstractFile.java

 Revision 1.6  2002/07/03 06:16:15  sharmahi
 galileo/src/handlers/AbstractFile.java

 Revision 1.5  2002/06/25 09:09:56  sharmahi
 Added Package name "handlers"

 Revision 1.3  2002/06/03 02:26:20  sharmahi
 Successful read and write behavior implemented.

 Revision 1.2  2002/05/29 05:50:45  sharmahi
 galileo/src/handlers/CFHandler.java

 Revision 1.1  2002/04/16 08:40:52  sharmahi
 Adding CFHandler after decision.

*/

