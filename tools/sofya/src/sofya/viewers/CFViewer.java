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

import sofya.base.ProjectDescription;
import sofya.graphs.*;
import sofya.graphs.cfg.*;
import sofya.graphs.cfg.CFEdge.BranchID;

import gnu.trove.THashSet;

/**
 * The CFViewer is used to display the contents of a control flow (.cf)
 * file in human-readable form.
 *
 * <p>Usage:<br /><code>java sofya.viewers.CFViewer [-tag <i>tag</i>]
 * [-ext] &lt;SourceFile.java&gt; [OutputFile]<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-tag <i>tag</i> : Specify tag associated
 * with the subject's database files<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-ext : Specifies extended output necessary
 * for branch tracing interpretation<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] : Redirect output of viewer
 * to <i>OutputFile</i><br />
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<i>SourceFile</i> must include
 * <i>.java</i> extension. Do not include any path.)</code></p>
 *
 * @author Alex Kinneer
 * @version 11/16/2004
 */
public class CFViewer extends Viewer {
    /** Handler for the control flow file. */
    private CFHandler cfHandler = new CFHandler();
    /** Tag associated with the file to be viewed. */
    String tag = null;
    /** Flag indicating whether extended information is to be displayed. */
    private boolean extendedView = false;
    
    /**
     * Private no-argument constructor for use by <code>main</code>.
     */
    private CFViewer() { }
    
    /*************************************************************************
     * Standard constructor, creates a CFViewer to display the formatted
     * contents of the specified control flow file to the system console
     * (<code>System.out</code>).
     *
     * @param inputFile Name of the control flow file to be displayed,
     * with <code>.java</code> extension.
     */ 
    public CFViewer(String inputFile) {
        super(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a CFViewer to display the formatted
     * contents of the specified control flow file to the specified output
     * file.
     *
     * @param inputFile Name of the control flow file to be displayed,
     * with <code>.java</code> extension.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public CFViewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        super(inputFile, outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a CFViewer to display the formatted
     * contents of the specified control flow file to the specified output
     * stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the control flow file to be displayed,
     * with <code>.java</code> extension.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public CFViewer(String inputFile, OutputStream stream) {
        super(inputFile, stream);
    }
    
    /*************************************************************************
     * Prints the control flow information to the specified stream.
     *
     * @param stream Stream to which the control flow information should be
     * written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        cfHandler.readCFFile(inputFile, tag);
        
        String intro = "Control Flow Information for File " + inputFile;
        stream.println(intro);
        
        Iterator graphs = cfHandler.sortedIterator();
        if (!extendedView) {
            while (graphs.hasNext()) {
                CFG cfg = (CFG) graphs.next();
                writeMethodInfo(cfg, stream);
            }
        }
        else {
            while (graphs.hasNext()) {
                CFG cfg = (CFG) graphs.next();
                writeMethodInfoExt(cfg, stream);
            }
        }
    }
    
    /*************************************************************************
     * Writes readable representation of the control flow graph for a method
     * to the output stream.
     *
     * @param cfg Control flow graph to be written.
     * @param stream Stream to which formatted output is to be written.
     */
    private void writeMethodInfo(CFG cfg, PrintWriter stream) {
        stream.println();
        printMethodName(cfg.getMethodName(), stream);
        stream.println("CFG root node: " + cfg.getRootNodeID());
        stream.println("node   label   endline   " + 
                       "cf-successors (node-id(edge-id,\"edge-label\"))");
        stream.println("----------------------------------------------------" +
                       "------------------------");

        StringBuffer line = new StringBuffer(80);
        String nextNode;
        for (int i = 0; i < cfg.getHighestNodeId(); i++) {
            Block block = cfg.getBlock(i + 1);
            int node = block.getID();
            char label = block.getLabel().toChar();
            int end = block.getEndOffset();
            line.delete(0, line.length());
            line.append(rightJust(node, 4) + "     " + label 
                        + rightJust(end, 11) + "     ");

            if (block.getSuccessorCount() > 0) {
                CFEdge[] edges = (CFEdge[]) cfg.getEdges(block,
                    Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
                for (int j = 0; j < edges.length; j++) {
                    int nodeId = edges[j].getSuccNodeID();
                    String edgeLabel = edges[j].getLabel();
                    String edgeAuxLabel = edges[j].getAuxLabel();
                    int edgeId = edges[j].getID();
                    if (edgeAuxLabel != null) {
                        nextNode = "   (" + edgeId + ",\"" + edgeLabel + "," +
                                   edgeAuxLabel + "\")";
                    }
                    else {
                        if (edgeLabel != null) {
                            nextNode = "   (" + edgeId + ",\"" +
                                       edgeLabel + "\")";
                        }
                        else {
                            nextNode = "   (" + edgeId + ",-)";
                        }
                    }
                    if (line.length() + nextNode.length() > 80) {
                        stream.println(line.toString());
                        line.delete(0, line.length());
                        line.append(rightJust(nodeId, 29) + nextNode.trim());
                    }
                    else {
                        line.append(rightJust(nodeId, 3) + nextNode.trim());
                    }
                }
            }
            stream.println(line);
        }
    }
    
    /*************************************************************************
     * Writes extended readable representation of the control flow graph for
     * a method to the output stream.
     *
     * <p>The output will include node start offsets and the branch IDs
     * associated with edges.</p>
     *
     * @param cfg Control flow graph to be written.
     * @param stream Stream to which formatted output is to be written.
     */
    @SuppressWarnings("unchecked")
    private void writeMethodInfoExt(CFG cfg, PrintWriter stream) {
        stream.println(" ");
        String methodName = cfg.getMethodName();
        if (methodName.length() > 70) {
            StringTokenizer st = new StringTokenizer(methodName, "_");
            String buf = "Method: \"";
            String token, temp;
            while (st.hasMoreTokens()) {
                token = st.nextToken();
                temp = buf + token;
                if (temp.length() > 70) {
                    stream.println(buf);
                    buf = "\t";
                } 
                buf += token;
                if (st.hasMoreTokens()) buf += "_";
            }
            stream.println(buf + "\"");
        }
        else {
            stream.println("Method: \"" + methodName + "\"");
        }
        
        stream.println("CFG root node: " + cfg.getRootNodeID());
        stream.println("              offsets    successors");
        stream.println("node label    S      E   " + 
                       "  node-id(edge-id,\"edge-label\",[branch-ids]) ");
        stream.println("----------------------------------------------------" +
                       "----------------------------");
        
        Set<Object> visitedSucc = new THashSet();
        StringBuffer line = new StringBuffer(80);
        for (int i = 0; i < cfg.getHighestNodeId(); i++) {
            Block block = cfg.getBlock(i + 1);
            int node = block.getID();
            char label = block.getLabel().toChar();
            int start = block.getStartOffset();
            int end = block.getEndOffset();
            
            line.delete(0, line.length());
            line.append(rightJust(node, 4) + "   " + label + "  "
                + rightJust(start, 5) + "  " + rightJust(end, 5) + "  ");
            
            Node[] succ = block.getSuccessors();
            visitedSucc.clear();
            for (int j = 0; j < succ.length; j++) {
                int nodeId = succ[j].getID();
                if (visitedSucc.contains(new Integer(nodeId))) continue;
                
                CFEdge[] edges =
                    (CFEdge[]) cfg.getEdges(block, succ[j], CFEdge.ZL_ARRAY);
                visitedSucc.add(new Integer(nodeId));
                
                StringBuffer nextNode = new StringBuffer();
                for (int k = 0; k < edges.length; k++) {
                    String edgeLabel = edges[k].getLabel();
                    String edgeAuxLabel = edges[k].getAuxLabel();
                    int edgeId = edges[k].getID();
                    
                    nextNode.delete(0, nextNode.length());
                    nextNode.append( "   (");
                    nextNode.append(edgeId);
                    if (edgeLabel != null) {
                        nextNode.append(",\"");
                        nextNode.append(edgeLabel);
                    }
                    else {
                        nextNode.append(",-");
                    }
                    if (edgeAuxLabel != null) {
                        nextNode.append(",");
                        nextNode.append(edgeAuxLabel);
                    }
                    if (edgeLabel != null) {
                        nextNode.append("\",");
                    }
                    else {
                        nextNode.append(",");
                    }
                    
                    nextNode.append("[");
                    if (ProjectDescription.ENABLE_BRANCH_EXTENSIONS) {
                        BranchID[] branchIDs = edges[k].getBranchIDArray();
                        nextNode.append(branchIDs[0].getID());
                        for (int n = 1; n < branchIDs.length; n++) {
                            nextNode.append(",");
                            nextNode.append(branchIDs[n].getID());
                        }
                    }
                    nextNode.append("])");
                    
                    if (line.length() + nextNode.length() > 80) {
                        stream.println(line.toString());
                        line.delete(0, line.length());
                        line.append(rightJust(nodeId, 28) +
                            nextNode.toString().trim());
                    }
                    else {
                        line.append(rightJust(nodeId, 4) +
                            nextNode.toString().trim());
                    }
                }
            }
            stream.println(line);
        }
    }

    /*************************************************************************
     * Specifies a tag to be associated with the file to be viewed.
     *
     * <p>The given tag name actually gets mapped to a subdirectory in the
     * database internally.</p>
     *
     * @param tag Tag which is associated with the file being viewed.
     */
    public void setTag(String tag) {
        if ((tag != null) && (tag.length() == 0)) tag = null;
        this.tag = tag;
    }

    /*************************************************************************
     * Prints the CFViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.CFViewer " +
            "[-tag <tag>] [-ext] <source_file> [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for CFViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length < 1 || argv.length > 6) {
            printUsage();
        }

        try {
            CFViewer cfView = new CFViewer();
            
            for (int i = 0; i < argv.length; i++) {
                if (argv[i].equals("-tag")) {
                    if (i + 1 < argv.length) {
                        cfView.tag = argv[++i];
                    }
                    else {
                        System.err.println("Tag not specified");
                        System.exit(1);
                    }
                }
                else if (argv[i].equals("-ext")) {
                    cfView.extendedView = true;
                }
                else {
                    cfView.setInputFile(argv[i++]);
                    if (i < argv.length) {
                        cfView.setOutputFile(argv[i]);
                    }
                    break;
                }
            }

            cfView.print();
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

        
/*****************************************************************************/
/*

$Log: CFViewer.java,v $
Revision 1.7  2011/10/28 08:35:20  wmotycka
Updated javadocs block to enumerate the -ext
command-line flag required for branch traacing
analysis.

$Log: CFViewer.java,v $
Revision 1.6  2007/07/30 15:58:45  akinneer
Updated year in copyright notice.

Revision 1.5  2006/09/08 21:30:16  akinneer
Updated copyright notice.

Revision 1.4  2006/09/08 20:54:04  akinneer
"Generified". Cleaned up imports. Removed dead variables. Added
serialUID fields to exception classes.

Revision 1.3  2006/03/21 21:51:01  kinneer
Updated JavaDocs to reflect post-refactoring package organization.
Various minor code cleanups. Modified copyright notice.

Revision 1.2  2005/06/06 18:48:08  kinneer
Added copyright notices.

Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
Sofya Java Bytecode Instrumentation and Analysis System

Revision 1.18  2004/09/13 16:53:24  kinneer
Modified output to properly reflect sorted order of edges in file.

Revision 1.17  2004/09/08 19:06:46  kinneer
Linked extended view mode to system-wide flag to control whether to
display branch IDs.

Revision 1.16  2004/08/30 19:50:15  kinneer
Modified to work with handler updates.

Revision 1.15  2004/05/12 23:02:58  kinneer
Added '-ext' option for generating extended view which includes node
start offsets and edge branch IDs.

Revision 1.14  2004/02/18 19:04:32  kinneer
Modified to handle new signature strings with underscores instead
of spaces (formatting tweak).

Revision 1.13  2004/01/07 20:37:14  kinneer
Various minor changes to accomodate interface modifications in other
classes.

Revision 1.12  2003/12/11 23:18:53  kinneer
Updated usage.

Revision 1.11  2003/11/25 23:18:33  kinneer
Consistency updates for tag parameter.

Revision 1.10  2003/11/10 21:09:27  kinneer
Modifications to support annotated edge labels (so that unique edge
labels can be ensured for JSR/RET nodes).

Revision 1.9  2003/11/04 00:24:52  kinneer
Extensions to support tagging mechanism added to CFG builder.

Revision 1.8  2003/10/23 00:48:48  kinneer
Bug fix: displays correct labels for edges from nodes that have multiple
edges pointing the same successor node (this happens with fall-through
case statements and sometimes return edges from finally blocks).

Revision 1.7  2003/10/14 00:04:14  kinneer
Modified output to handle edge labels on finally return edges
gracefully.

Revision 1.6  2003/09/25 16:39:38  kinneer
Modified to handle new possibility of MethodNotFoundExceptions thrown
from handlers.

Revision 1.5  2003/08/27 18:45:12  kinneer
Release 2.2.0.  Additional details in release notes.

Revision 1.4  2003/08/18 18:43:32  kinneer
See v2.1.0 release notes for details.

Revision 1.3  2003/08/13 18:28:52  kinneer
Release 2.0, please refer to release notes for details.

Revision 1.2  2003/08/01 17:13:54  kinneer
Viewers interface deprecated. Viewer abstract class introduced. See
release notes for additional details.

All classes cleaned for readability and JavaDoc'ed.

Revision 1.1  2003/03/03 20:36:46  aristot
Moved CFViewer to viewers dir

Revision 1.7  2002/08/07 08:00:58  sharmahi
After fixing bug wherein method name gets output on multiple lines
if it is too long.

Revision 1.6  2002/07/17 07:13:04  sharmahi
Changed a small part of info output to file

Revision 1.5  2002/07/10 06:44:50  sharmahi
Final version before Code review

Revision 1.4  2002/07/09 05:15:40  sharmahi
Working without Format

*/        
              
          
        
        
