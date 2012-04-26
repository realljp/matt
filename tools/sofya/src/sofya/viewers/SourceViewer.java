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

import sofya.base.SourceHandler;

/**
 * The SourceViewer is used to display the contents of a Java source file.
 *
 * <p>Usage:<br><code>java sofya.viewers.SourceViewer
 * &lt;SourceFile.java&gt; [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] : Redirect output
 * of viewer to <i>OutputFile</i><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(<i>SourceFile</i> must include
 * <i>.java</i> extension and any necessary path information,
 * relative or absolute.)</code></p>
 *
 * @author Alex Kinneer
 * @version 03/12/2004
 */
public class SourceViewer extends Viewer {
    /** File handler for the source file being viewed. */
    private SourceHandler inputHandler = new SourceHandler();
    
    /*************************************************************************
     * Standard constructor, creates a SourceViewer to display the
     * contents of the specified Java source file to the system console
     * (<code>System.out</code>).
     *
     * @param inputFile Name of the Java source file whose contents are to be
     * displayed.
     */ 
    public SourceViewer(String inputFile) {
        super(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a SourceViewer to display the
     * contents of the specified Java source file to the specified output file.
     *
     * @param inputFile Name of the Java source file whose contents are to be
     * displayed.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public SourceViewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        super(inputFile, outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a SourceViewer to display the
     * contents of the specified Java source file to the specified
     * output stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the Java source file whose contents are to be
     * displayed.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public SourceViewer(String inputFile, OutputStream stream) {
        super(inputFile, stream);
    }

    /*************************************************************************
     * Prints the source code, with line numbers, to the specified stream.
     *
     * @param stream Stream to which the source code should be written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        inputHandler.readSourceFile(inputFile);
        String[] srcLines = inputHandler.getSource();
        for (int i = 0; i < srcLines.length; i++) {
            stream.println((i + 1) + " " + srcLines[i]);
        }
    }
    
    /*************************************************************************
     * Prints the SourceViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.SourceViewer " +
            "<source_file> [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for SourceViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length < 1 || argv.length > 2) {
            printUsage();
        }

        try {
            SourceViewer sView = new SourceViewer(argv[0]);
            if (argv.length == 2) sView.setOutputFile(argv[1]);
            sView.print();
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
  $Log: SourceViewer.java,v $
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

  Revision 1.6  2004/03/12 22:17:34  kinneer
  Removed reference to deprecated NoFileNameException.

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

  Revision 1.1  2003/03/03 20:38:33  aristot
  Moved SourceViewer to viewers dir

  Revision 1.5  2002/07/08 05:43:14  sharmahi
  Added package name

  Revision 1.4  2002/07/03 06:15:12  sharmahi
  Added Exceptions

  Revision 1.3  2002/06/09 08:45:27  sharmahi
  After first glance and review of fomrat, code style and file layout

  Revision 1.2  2002/01/30 16:14:52  sharmahi
  Added Source Handler and Viewer

*/      
   
      
