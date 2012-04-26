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

import sofya.base.Handler;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.base.exceptions.SofyaError;
import sofya.ed.structural.CoverageTrace;
import sofya.ed.structural.TraceHandler;
import static sofya.base.SConstants.*;

/**
 * The TraceViewer is used to display the contents of a trace (.tr)
 * file in human-readable form.
 *
 * <p>Usage:<br><code>java sofya.viewers.TraceViewer &lt;SourceFile.java&gt;
 * [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] : Redirect output of viewer to
 * <i>OutputFile</i><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<i>SourceFile</i> must include <i>.tr</i>
 * extension and any necessary path information, relative or absolute.)</code>
 * </p>
 *
 * @author Alex Kinneer
 * @version 07/31/2007
 */
public class TraceViewer extends Viewer {
    /** Handler for the trace file.*/
    private TraceHandler traceHandler = new TraceHandler();
    /** String associated with the entity type. */
    private String entityStr = "Blocks";
    /** Flag that is set if the viewer detects that the trace file
        is in a Galileo or early Sofya format; used to control
        subsequent processing. */
    private boolean isLegacyTrace = false;
    
    /** Compilation flag to control whether the viewer sorts its output
        in the order originally used by Galileo and early versions of
        Sofya. */
    /* This is intended to assist in validating trace files against
       accepted outputs during testing, when the raw format of the trace
       file has been changed. In that case, the outputs of the viewer
       may be compared to determine that the contents of the traces are
       logically equivalent, but we need consistent sort order to
       easily perform the diff. */
    private static final boolean FORCE_LEGACY_SORT = false;

    /** Private constructor for use by <code>main</code> (if necessary). */
    private TraceViewer() { }

    /*************************************************************************
     * Standard constructor, creates a TraceViewer to display the formatted
     * contents of the specified trace file to the system console
     * (<code>System.out</code>).
     *
     * @param inputFile Name of the trace file to be displayed,
     * with <code>.tr</code> extension.
     */ 
    public TraceViewer(String inputFile) {
        super(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a TraceViewer to display the formatted
     * contents of the specified trace file to the specified output file.
     *
     * @param inputFile Name of the trace file to be displayed,
     * with <code>.tr</code> extension.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public TraceViewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        super(inputFile, outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a TraceViewer to display the formatted
     * contents of the specified trace file to the specified output stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the trace file to be displayed,
     * with <code>.tr</code> extension.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public TraceViewer(String inputFile, OutputStream stream) {
        super(inputFile, stream);
    }

    /*************************************************************************
     * Prints the trace information to the specified stream.
     *
     * @param stream Stream to which the trace information should be written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        traceHandler.readTraceFile(inputFile, false);
        
        entityStr = (traceHandler.getObjectType() ==
                        TraceObjectType.BASIC_BLOCK)
                    ? "Blocks"
                    : "Edges";
        
        String traceMethods[] = traceHandler.getMethodList();
        stream.println("Trace Information for trace file " + inputFile +
            LINE_SEP + "Trace Type: " + traceHandler.getTypesString());
        
        java.util.Map<String, String> sortMap;
        if (FORCE_LEGACY_SORT) {
            sortMap = new java.util.HashMap<String, String>();
            for (int i = 0; i < traceMethods.length; i++) {
                String unpacked = Handler.unpackSignature(traceMethods[i]);
                sortMap.put(unpacked, traceMethods[i]);
                traceMethods[i] = unpacked;
            }
            java.util.Arrays.sort(traceMethods);
        }
        else {
            sortMap = null;
        }
        
        CoverageTrace trace = null;
        for (int i = 0; i < traceMethods.length; i++) {
            try {
                if (FORCE_LEGACY_SORT) {
                    String handlerKey = sortMap.get(traceMethods[i]);
                    traceMethods[i] = handlerKey;
                    trace = traceHandler.getTrace(handlerKey);
                }
                else {
                    trace = traceHandler.getTrace(traceMethods[i]);
                }
            }
            catch (MethodNotFoundException e) {
                throw new SofyaError("TraceHandler falsely claimed " +
                    "to have a trace for " + traceMethods[i]);
            }
            writeMethodInfo(traceMethods[i], trace, stream); 
        }
    }
    
    /*************************************************************************
     * Writes readable representation of the trace information for a method.
     *
     * @param methodName Name of the method whose associated trace
     * information is to be printed.
     * @param trace Trace of the method collected by a filter.
     * @param stream Stream to which formatted output is to be written.
     */
    private void writeMethodInfo(String methodName, CoverageTrace trace,
                                 PrintWriter stream) {
        int i = 0, hitBlocks = 0;
        
        String printName;
        if (isLegacyTrace) {
            printName = methodName;
        }
        else {
            try {
                printName = Handler.unpackSignature(methodName);
            }
            catch (ArrayIndexOutOfBoundsException e) {
                isLegacyTrace = true;
                printName = methodName;
            }
        }
        
        stream.println();
        printMethodName(printName, stream);
            
        StringBuffer sb = new StringBuffer(entityStr + " Hit: ");
        for (i = 1; i <= trace.getHighestId(); i++) {
            if (trace.query(i)) {
                hitBlocks++;
                if (hitBlocks % 10 == 0) {
                    sb.append(rightJust(i, 4));
                    stream.println(sb.toString());
                    hitBlocks = 0;
                    sb.delete(0, sb.length());
                    sb.append("\t    ");
                }
                else {
                    sb.append(rightJust(i, 4));
                }
            }     
        }
        if (hitBlocks % 10 != 0) {
            stream.println(sb.toString());
        }
    }   

    /*************************************************************************
     * Prints the TraceViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.TraceViewer " +
            "<trace_file> [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for TraceViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length < 1 || argv.length > 3) {
            printUsage();
        }

        try {
            TraceViewer tView = new TraceViewer();
            
            // (Extensible if extra arguments need to be added)
            for (int i = 0; i < argv.length; i++) {
                //if ("-lsort".equals(argv[i])) {
                //    TraceViewer.FORCE_LEGACY_SORT = true;
                //}
                //else {
                    tView.setInputFile(argv[i++]);
                    if (i < argv.length) {
                        tView.setOutputFile(argv[i]);
                    }
                    break;
                //}
            }

            tView.print();
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
 $Log: TraceViewer.java,v $
 Revision 1.7  2007/08/01 18:55:37  akinneer
 Added internal support for sorting output in the "legacy" manner, for
 use in validating current trace file outputs against previous outputs
 during testing.
 Expanded imports.

 Revision 1.6  2007/01/18 21:46:28  akinneer
 Modified to restore generation of "unpacked" signature format.
 Updated copyright date.

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

 Revision 1.9  2004/05/26 23:25:08  kinneer
 Implemented extensions to view branch traces.

 Revision 1.8  2004/02/18 19:04:32  kinneer
 Modified to handle new signature strings with underscores instead
 of spaces (formatting tweak).

 Revision 1.7  2003/10/09 23:51:38  kinneer
 Update to handle exceptions thrown by handlers (replacement of
 null flags).

 Revision 1.6  2003/08/27 18:45:13  kinneer
 Release 2.2.0.  Additional details in release notes.

 Revision 1.4  2003/08/18 18:43:33  kinneer
 See v2.1.0 release notes for details.

 Revision 1.3  2003/08/13 18:28:53  kinneer
 Release 2.0, please refer to release notes for details.

 Revision 1.2  2003/08/01 17:13:54  kinneer
 Viewers interface deprecated. Viewer abstract class introduced. See
 release notes for additional details.

 All classes cleaned for readability and JavaDoc'ed.

 Revision 1.1  2003/03/03 20:39:19  aristot
 Moved TraceViewer to viewers dir

 Revision 1.2  2002/09/11 18:43:41  sharmahi
 decoupled the viewer from need of a cf file

 Revision 1.1  2002/08/07 07:57:23  sharmahi
 Added comments

*/

