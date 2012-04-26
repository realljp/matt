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

import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.MapHandler;

/**
 * The MapViewer is used to display the contents of a map (.map) file in
 * human-readable form.
 *
 * <p>Usage:<br><code>java sofya.viewers.MapViewer [-tag <i>tag</i>]
 * &lt;SourceFile.java&gt; [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-tag <i>tag</i> : Specify tag associated
 * with the subject's database files<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] : Redirect output of viewer
 * to <i>OutputFile</i><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<i>SourceFile</i> must include
 * <i>.java</i> extension. Do not include any path.)</code></p>
 *
 * @author Alex Kinneer
 * @version 09/23/2004
 */
public class MapViewer extends Viewer {
    /** Handler for the map file. */
    private MapHandler mapHandler = new MapHandler();
    /** Tag associated with the file to be viewed. */
    String tag = null;
    
    /*************************************************************************
     * Standard constructor, creates a MapViewer to display the formatted
     * contents of the specified map file to the system console
     * (<code>System.out</code>).
     *
     * @param inputFile Name of the map file to be displayed,
     * with <code>.java</code> extension.
     */ 
    public MapViewer(String inputFile) {
        super(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a MapViewer to display the formatted
     * contents of the specified map file to the specified output file.
     *
     * @param inputFile Name of the map file to be displayed,
     * with <code>.java</code> extension.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public MapViewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        super(inputFile, outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a MapViewer to display the formatted
     * contents of the specified map file to the specified output stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the map file to be displayed,
     * with <code>.java</code> extension.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public MapViewer(String inputFile, OutputStream stream) {
        super(inputFile, stream);
    }
    
    /*************************************************************************
     * Prints the map information to the specified stream.
     *
     * @param stream Stream to which the map information should be written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        mapHandler.readMapFile(inputFile, tag);
        
        String intro = "Map Information for File " + inputFile;
        stream.println(intro);
        
        Iterator graphs = mapHandler.sortedIterator();
        while (graphs.hasNext()) {
            CFG cfg = (CFG) graphs.next();
            writeMethodInfo(cfg, stream);
        }
    }
    
    /*************************************************************************
     * Writes readable representation of the map information from a control
     * flow graph for a method to the output stream.
     *
     * @param cfg Control flow graph from which map information is to be
     * written.
     * @param stream Stream to which formatted output is to be written.
     */
    private void writeMethodInfo(CFG cfg, PrintWriter stream) {
        stream.println();
        printMethodName(cfg.getMethodName(), stream);
        stream.println("node   label   type    subtype    strtline    " +
                       "endline");
        stream.println("--------------------------------------" +
                       "--------------------------------------");
        
        StringBuffer line = new StringBuffer();
        for (int i = 0; i < cfg.getHighestNodeId(); i++) {
            Block block = cfg.getBlock(i + 1);
            
            line.append(rightJust(block.getID(), 3));
            line.append("      ");
            line.append(block.getLabel());
            line.append("     ");
            line.append(sizeString(block.getType().toString(), 8));
            line.append("  ");
            line.append(sizeString(block.getSubType().toString(), 10));
            line.append(rightJust(block.getStartOffset(), 4));
            line.append(rightJust(block.getEndOffset(), 11));
            
            stream.println(line.toString());
            line.delete(0, line.length());
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
     * Prints the MapViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.MapViewer " +
            "[-tag <tag>] <source_file> [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for MapViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length < 1 || argv.length > 4) {
            printUsage();
        }

        try {
            MapViewer mapView = null;
            if (argv[0].equals("-tag")) {
                if (argv.length < 3) {
                    printUsage();
                }
                mapView = new MapViewer(argv[2]);
                mapView.setTag(argv[1]);
                if (argv.length == 4) mapView.setOutputFile(argv[3]);
            }
            else {
                mapView = new MapViewer(argv[0]);
                if (argv.length == 2) mapView.setOutputFile(argv[1]);
            }
            mapView.print();
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
 $Log: MapViewer.java,v $
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

 Revision 1.13  2004/08/30 19:50:15  kinneer
 Modified to work with handler updates.

 Revision 1.12  2004/02/18 19:04:32  kinneer
 Modified to handle new signature strings with underscores instead
 of spaces (formatting tweak).

 Revision 1.11  2004/01/07 20:37:14  kinneer
 Various minor changes to accomodate interface modifications in other
 classes.

 Revision 1.10  2003/12/11 23:20:03  kinneer
 Updated usage.  Added unique string to distinguish blocks with System.exit
 subtype.

 Revision 1.9  2003/11/25 23:18:33  kinneer
 Consistency updates for tag parameter.

 Revision 1.8  2003/11/10 21:09:27  kinneer
 Modifications to support annotated edge labels (so that unique edge
 labels can be ensured for JSR/RET nodes).

 Revision 1.7  2003/11/04 00:24:53  kinneer
 Extensions to support tagging mechanism added to CFG builder.

 Revision 1.6  2003/09/25 16:39:38  kinneer
 Modified to handle new possibility of MethodNotFoundExceptions thrown
 from handlers.

 Revision 1.5  2003/08/27 18:45:12  kinneer
 Release 2.2.0.  Additional details in release notes.

 Revision 1.4  2003/08/18 18:43:33  kinneer
 See v2.1.0 release notes for details.

 Revision 1.3  2003/08/13 18:28:53  kinneer
 Release 2.0, please refer to release notes for details.

 Revision 1.2  2003/08/01 17:13:54  kinneer
 Viewers interface deprecated. Viewer abstract class introduced. See
 release notes for additional details.

 All classes cleaned for readability and JavaDoc'ed.

 Revision 1.1  2003/03/03 20:37:44  aristot
 Moved MapViewer to viewers dir

 Revision 1.5  2002/08/07 08:00:39  sharmahi
 After fixing bug wherein method name gets output on multiple lines
 if it is too long.

 Revision 1.4  2002/07/17 07:12:52  sharmahi
 Changed a small part of info output to file

 Revision 1.3  2002/07/10 06:44:28  sharmahi
 Final version before code review

*/
