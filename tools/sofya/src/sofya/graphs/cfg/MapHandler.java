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

import java.io.*;
import java.util.*;

import sofya.base.SConstants.*;
import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.*;
import sofya.graphs.GraphCache;

import org.apache.bcel.generic.Type;

import gnu.trove.THashMap;

/**
 * The MapHandler provides routines to manipulate Sofya map (.map)
 * files and to form control flow graph (CFG) objects.
 *
 * @author Alex Kinneer
 * @version 11/04/2004
 *
 * @see sofya.graphs.cfg.CFG
 * @see sofya.graphs.cfg.CFHandler
 * @see sofya.viewers.MapViewer
 */
@SuppressWarnings("unchecked")
public class MapHandler extends Handler {
    /** Map which links method signatures to {@link sofya.graphs.cfg.CFG}s. */
    private GraphCache<CFG> cfgs;
    
    /** Records what class we believe is loaded. This value can only
        be set internally by reading a map file. It may be used later
        if a request is received to save a new or modified map file
        based on the loaded map data. */
    private String className = null;
    
    /** Flag which is only set internally during loading of a map file
        if it is determined that it is a 'legacy' map file (one which
        does not store method signatures in a parseable format).
        If set, it modifies how CFGs are stored and keyed, and how the
        map file is written. */
    private boolean legacyMap = !HANDLER_EXTENSIONS;
    /** Flag which specifies whether the control flow map data should be
        written to file using the same sorting criterion applied
        up through v3.3.0 of Galileo. */
    private boolean legacySort = false;
    /** Cache which stores and keys CFG map data in the 'legacy' fashion, used
        if the legacyCF flag is set. When in use, the handler effectively
        behaves in the same way that it did up to v3.3.0 of Galileo. */
    private Map<Object, CFG> legacyCache = new THashMap();
    /** Flag indicating whether any previously cached CFGs should be cleared
        when a new map file is read. */
    private boolean clearOnLoad = true;
    
    /*************************************************************************
     * Default constructor, initalizes internal CFG hashmap.
     */
    public MapHandler() {
        cfgs = new GraphCache<CFG>(new CFGSerializer());
    }
    
    /*************************************************************************
     * Creates a handler which is backed by the given CFG cache.
     *
     * @param sharedCache CFG cache from which the handler will retrieve
     * CFGs for writing to file.
     */
    public MapHandler(GraphCache<CFG> sharedCache) {
        cfgs = sharedCache;
    }
    
    /*************************************************************************
     * Writes current map information to .map file.
     *
     * <p>The map information for control flow graphs currently registered
     * with the handler are written to a <code>.map</code> file. If necessary
     * (and permitted), the  file is created and placed in the Sofya database
     * directory automatically.</p>
     *
     * @param fileName Name of the map (.map) file to be written.
     *
     * @throws FileNotFoundException If the specified file doesn't exist
     * and can't be created.
     * @throws IOException If there is an error writing to the .map file.
     */
    public void writeMapFile(String fileName)
                throws FileNotFoundException, IOException {
        // If this old method is being called, we'll assume the old style
        // sorting is expected
        setLegacySort(true);
        
        // Try to determine the class name
        if (!legacyMap) {
            if (className != null) {
                writeMapFile(className, fileName, null);
            }
            else {
                Iterator methods = cfgs.keySet().iterator();
                if (methods.hasNext()) {
                    MethodSignature ms = (MethodSignature) methods.next();
                    writeMapFile(ms.getClassName(), fileName, null);
                }
                else {
                    throw new IllegalStateException("Cannot create map file " +
                        "without valid class name");
                }
            }
        }
        else {
            writeMapFile(null, fileName, null);
        }
    }
    
    /*************************************************************************
     * Writes current map information to .map file.
     *
     * <p>The map information for control flow graphs currently registered
     * with the handler are written to a <code>.map</code> file. If necessary
     * (and permitted), the  file is created and placed in the Sofya database
     * directory automatically.</p>
     *
     * @param className Name of the class for which map information is
     * to be written to file.
     * @param fileName Name of the map (.map) file to be written.
     * @param tag Database tag to be associated with the file.
     *
     * @throws FileNotFoundException If the specified file doesn't exist
     * and can't be created.
     * @throws IOException If there is an error writing to the .map file.
     */
    public void writeMapFile(String className, String fileName, String tag)
                throws FileNotFoundException, IOException {
        Date date = new Date();
        
        Iterator methods;
        if (legacyMap) {
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
                         openOutputFile(fileName + ".map", tag, false))),
                             true);
        
        pw.println("0 Mapping Information");
        pw.println("0 File: " + fileName + " Created: " + date); 
        pw.println("0 version " + ProjectDescription.versionString);
        pw.println("0");
        
        CFG cfg = null;
        while (methods.hasNext()) {
            if (legacyMap) {
                cfg = (CFG) legacyCache.get(methods.next());
            }
            else {
                MethodSignature key = (MethodSignature) methods.next();
                cfg = (CFG) cfgs.get(key).getGraph();
            }
            
            pw.println("1 \"" + cfg.getMethodName() + "\" " +
                       cfg.getNumberOfNodes() + " " + cfg.getHighestNodeId());
            
            if (HANDLER_EXTENSIONS) {
                if (!legacyMap) {
                    MethodSignature ms = cfg.getSignature();
                    pw.println("4 " +  ms.getClassName() + "#" +
                        ms.getMethodName() + "#" +
                        Type.getMethodSignature(ms.getReturnType(),
                                                ms.getArgumentTypes()));
                }
            }
                       
            // 0 being written out for start RTL and end RTL information to
            // maintain consistency with the Aristotle map file format.
            List blockList = cfg.blockList();
            for (int j = 0; j < cfg.getNumberOfNodes(); j++) {
                Block block = (Block) blockList.get(j);
                pw.println("2 " + block.getID() +
                           " " + block.getLabel().toChar() +
                           " " + block.getType().toInt() +
                           " " + block.getSubType().toInt() +
                           " " + block.getStartOffset() +
                           " " + block.getEndOffset() + 
                           "  0  0 ");
            }
            
            pw.println("0 end of method " + cfg.getMethodName());
        }

        pw.close();
        if (pw.checkError()) {
            throw new IOException("Error writing control flow file");
        }
    }

    /*************************************************************************
     * Reads map information from a .map file.
     *
     * <p>The map information is read from the file and stored to
     * internal data structures. This data can then be accessed via other
     * accessor functions in this class, such as
     * {@link sofya.graphs.cfg.MapHandler#getCFG}. The .map file
     * is assumed to be located in the Sofya database directory.</p>
     *
     * @param fileName Name of the map file to be read.
     * @param tag Database tag associated with the file.
     *
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws EmptyFileException If the specified file contains no data.
     * @throws BadFileFormatException If the specified file is not a .map
     * file or is otherwise malformed or corrupted.
     * @throws IOException If there is an error reading from the .map file.
     */
    public void readMapFile(String fileName, String tag)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException {
        legacyMap = !HANDLER_EXTENSIONS;
        
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                            openInputFile(fileName + ".map", tag)));
        StreamTokenizer stok = new StreamTokenizer(br);
        prepareTokenizer(stok);

        try {
            if (clearOnLoad) {
                cfgs.clear();
                legacyCache.clear();
            }
            
            if (!readToEOL(stok)) {
                throw new EmptyFileException();
            }
        
            String token = null;
            for (int i = 0; i < 3; i++) {
                if (stok.nextToken() == StreamTokenizer.TT_EOF) {
                    throw new BadFileFormatException("Map file " +
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
            int nodeID;
            BlockType type;
            BlockSubType subType;
            BlockLabel label;
            int nodeStartOffset, nodeEndOffset;
            outer:
            while (true) {
                // Read method header information
                if (readInt(stok) != 1) {
                    throw new BadFileFormatException("Map file is " +
                        "corrupted: method header not found where expected");
                }
                
                // Read method name using quotation marks as tokens
                stok.whitespaceChars('"', '"');
                methodName = readString(stok);
                stok.wordChars('"', '"');
                
                // Get entity counts
                readInt(stok);
                readInt(stok);
                
                // Scan to end of line
                if (!readToEOL(stok)) {
                    throw new EOLException(stok.lineno());
                }
                
                cfg = new CFG(methodName);
            
                if (!readToNextDataLine(stok)) {
                    throw new DatabaseEOFException();
                }
                
                if (HANDLER_EXTENSIONS) {
                    readSig:
                    if (!legacyMap) {
                        if (readInt(stok) != 4) {
                            legacyMap = true;
                            System.err.println("INFO: Legacy map " +
                                "file. Consider rebuilding with the " +
                                "current version of Galileo.");
                            stok.pushBack();
                            break readSig;
                        }
                        
                        stok.whitespaceChars('#', '#');
                        String clName = readString(stok);
                        String mName = readString(stok);
                        String signatureStr = readString(stok);
                        cfg.setSignature(new MethodSignature(clName,
                            mName, Type.getReturnType(signatureStr),
                            Type.getArgumentTypes(signatureStr)));
                        className = clName;
                        stok.wordChars('#', '#');
                        
                        if (!readToNextDataLine(stok)) {
                            throw new DatabaseEOFException();
                        }
                    }
                }

                while (readInt(stok) == 2) {
                    nodeID = readInt(stok);
                    label = BlockLabel.fromChar(readString(stok).charAt(0));
                    type = BlockType.fromInt(readInt(stok));
                    subType = BlockSubType.fromInt(readInt(stok));
                    nodeStartOffset = readInt(stok);
                    nodeEndOffset = readInt(stok);
                    cfg.addBlock(
                        new Block(nodeID, type, subType, label,
                                  nodeStartOffset, nodeEndOffset));
                    
                    if (!readToNextDataLine(stok)) {
                        if (legacyMap) {
                            legacyCache.put(methodName, cfg);
                        }
                        else {
                            cfgs.put(cfg.getSignature(), cfg).setComplete(
                                true);
                        }
                        break outer;
                    }
                }
                stok.pushBack();
                
                if (legacyMap) {
                    legacyCache.put(methodName, cfg);
                }
                else {
                    cfgs.put(cfg.getSignature(), cfg).setComplete(true);
                }
            }
        }
        finally {
            br.close();
        }
    }
    
    /*************************************************************************
     * Adds {@link sofya.graphs.cfg.CFG} to the current .map file, using the
     * method name or signature associated with the given <code>CFG</code>
     * object.
     *
     * @param cfg {@link sofya.graphs.cfg.CFG} to be added to the map
     * information in the currently opened .map file.
     */
    public void addCFG(CFG cfg) {
        if (legacyMap || (cfg.getSignature() == null)) {
            legacyCache.put(cfg.getMethodName(), cfg);
            legacyMap = true;
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
        if (legacyMap) {
            System.err.println("WARNING: Legacy map file does not support " +
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
        if (legacyMap) {
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
        if (legacyMap) {
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
        if (legacyMap) {
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
     * @return {@link sofya.graphs.cfg.CFG} constructed for the
     * specified method, or <code>null</code> if no match is found.
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
     * Gets the list of pretty-printed method names associated with control
     * flow graphs registered with the handler.
     *
     * @return List of names of the methods the handler knows about.
     */ 
    public String[] getMethodList() {
        String[] methodList;
        if (legacyMap) {
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
        if (legacyMap) {
            System.err.println("WARNING: Legacy map file does not support " +
                "signatures");
            return new MethodSignature[0];
        }
        else {
            MethodSignature[] sigList = (MethodSignature[]) cfgs.keySet()
                .toArray(new MethodSignature[cfgs.size()]);
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
        if (legacyMap) {
            System.err.println("WARNING: Legacy map file does not support " +
                "signatures");
            return new MethodSignature[0];
        }
        else {
            Set<MethodSignature> keys = cfgs.keySet(className);
            MethodSignature[] sigList = (MethodSignature[]) keys.toArray(
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
        if (legacyMap) {
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
        if (legacyMap) {
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
     * cleared whenever a new map file is read. This is true by default.
     *
     * <p>Normally it is the contract of the handler to only retain graphs
     * from the most recently loaded map file. This may be disabled under
     * certain special circumstances for performance reasons.</p>
     *
     * @param enabled <code>true</code> to have existing control flow graphs
     * removed from the cache whenever a new map file is read,
     * <code>false</code> to permit the handler to cache control flow graphs
     * from multiple map files.
     */
    public void setClearOnLoad(boolean enabled) {
        clearOnLoad = enabled;
    }
    
    /*************************************************************************
     * Gets whether the handler clears existing control flow graphs from its
     * cache when a new map file is read.
     *
     * @return <code>true</code> if existing control flow graphs are cleared
     * whenever a new map file is read, <code>false</code> if the handler
     * will retain control flow graphs from multiple map files in its cache.
     */
    public boolean isClearedOnLoad() {
        return clearOnLoad;
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
    
    /**************************************************************************
     * Test driver for MapHandler.
     */
    public static void main(String argv[]) {
        MapHandler readmapHandler = new MapHandler();
        MapHandler writemapHandler = new MapHandler();
        
        try {
            readmapHandler.readMapFile(argv[0], null);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            System.out.println(e.getMessage()) ;
            System.exit(1);
        }
        
        String methods[] = readmapHandler.getMethodList();
        
        try {
            for (int i = 0; i < methods.length; i++) {
                CFG cfg = readmapHandler.getCFG(methods[i]);
                writemapHandler.addCFG(cfg);
            }
        }
        catch (MethodNotFoundException shouldntHappen) {
            System.err.println("Critical error: handler is reporting it " +
                "can provide CFG for method for which it does not have " +
                "any actual CFG stored");
            System.exit(1);
        }
        
        try {
            writemapHandler.writeMapFile(argv[1]);
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(1); 
        }
        catch (NullPointerException e) {
            System.out.println(e.getMessage());
            System.exit(1); 
        }
    }
}

/*
  $Log: MapHandler.java,v $
  Revision 1.7  2007/07/30 16:17:49  akinneer
  Updated year in copyright notice.

  Revision 1.6  2006/09/08 21:30:08  akinneer
  Updated copyright notice.

  Revision 1.5  2006/09/08 20:12:24  akinneer
  - Eliminated use of constant interface anti-pattern in favor of static
    imports.
  - Added generated SerialUID values to exception classes.
  - "Generified".

  Revision 1.4  2006/03/21 21:50:33  kinneer
  Updated JavaDocs to reflect post-refactoring package organization.
  Various minor code cleanups. Modified copyright notice.

  Revision 1.3  2005/06/06 18:47:25  kinneer
  Minor revisions and added copyright notices.

  Revision 1.2  2005/03/30 22:28:02  kinneer
  Made an error message more informative.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.26  2004/09/14 19:14:48  kinneer
  Wrote JavaDocs for new methods.

  Revision 1.25  2004/08/30 19:55:04  kinneer
  Modified to use disk-cache backed storage for CFGs and implemented
  functions to support modifications to the GraphBuilder to facilitate
  improved CFG building algorithms.

  Revision 1.24  2004/05/26 23:21:40  kinneer
  Simplified sorting of procedures on output.

  Revision 1.23  2004/03/12 22:07:16  kinneer
  Modified read methods to ensure release of IO resources if an exception
  is thrown. Deprecated and eliminated references to NoFileNameException,
  which was a spurious annoyance. FileNotFoundException is now used
  instead.

  Revision 1.22  2004/02/18 19:03:34  kinneer
  Added method to test for presence of data for a method. Can be used instead
  of calling a getX method and handling an exception.

  Revision 1.21  2004/02/02 19:10:56  kinneer
  All MethodNotFoundExceptions now include the name of the method
  in the exception message.

  Revision 1.20  2004/01/07 20:48:58  kinneer
  Trivial updates to reflect changes in ProjectDescription. Some fields
  made final in Handler class.

  Revision 1.19  2003/09/25 16:38:37  kinneer
  Eliminated all null flags. Requesting objects for methods which do
  not exist now cause MethodNotFoundExceptions to be thrown.

  Revision 1.18  2003/08/27 18:44:05  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.17  2003/08/18 18:42:47  kinneer
  See v2.1.0 release notes for details.

  Revision 1.16  2003/08/13 18:28:36  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.15  2003/08/01 17:10:46  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.14  2002/09/11 16:46:03  sharmahi
  added end RTL and start RTL information to map files

  Revision 1.13  2002/08/07 08:02:36  sharmahi
  *** empty log message ***

  Revision 1.12  2002/07/08 04:32:22  sharmahi
  Corrected case of Log

*/
