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
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import sofya.base.Handler;
import sofya.base.ByteSourceHandler;
import sofya.base.exceptions.MethodNotFoundException;

import org.apache.bcel.classfile.ClassFormatException;

/**
 * The ByteSourceViewer is used to display a human-readable listing of the
 * compiled Java bytecode for a class. Instructions are listed using the
 * assembly mnemonics defined by the Java Virtual Machine specification.
 *
 * <p>Usage:<br><code>java sofya.viewers.ByteSourceViewer
 * &lt;SourceFile&gt; [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[OutputFile] :
 * Redirect output of viewer to <i>OutputFile</i><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(Do not include <i>.class</i> extension
 * with <i>SourceFile</i>)</code></p>
 *
 * @author Alex Kinneer
 * @version 10/10/2006
 */
public class ByteSourceViewer extends Viewer {
    /** File handler for the class file being viewed. */
    private ByteSourceHandler inputHandler = new ByteSourceHandler();
    /** Specifies the name of a particular method to be viewed in the class. */
    private String methodName = null;
    
    private String sourceJar = null;

    private ByteSourceViewer() {
    }
    
    /*************************************************************************
     * Standard constructor, creates a ByteSourceViewer to display the
     * contents of the specified Java class to the system console
     * (<code>System.out</code>).
     *
     * @param inputFile Name of the Java class whose bytecode is to be
     * displayed.
     */ 
    public ByteSourceViewer(String inputFile) {
        super(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a ByteSourceViewer to display the
     * contents of the specified Java class to the specified output file.
     *
     * @param inputFile Name of the Java class whose bytecode is to be
     * displayed.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public ByteSourceViewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        super(inputFile, outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a ByteSourceViewer to display the
     * contents of the specified Java class to the specified output stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the Java class whose bytecode is to be
     * displayed.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public ByteSourceViewer(String inputFile, OutputStream stream) {
        super(inputFile, stream);
    }
    
    /**
     * Specifies a jar file from which classes should be read.
     * 
     * @param jarFile Name of the jar file that the viewer should attempt
     * to use to locate class files for viewing.
     */
    public void setSourceJar(String jarFile) {
        this.sourceJar = jarFile;
    }
    
    /*************************************************************************
     * Prints the bytecode for the Java class to the specified stream.
     *
     * @param stream Stream to which the bytecode should be written.
     *
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */ 
    public void print(PrintWriter stream) throws IOException {
        if (sourceJar == null) {
            inputHandler.readSourceFile(inputFile);
        }
        else {
            inputHandler.readSourceFile(sourceJar, inputFile);
        }
        String[] methods = inputHandler.getMethodList();
        String methodCode = null;
        
        for (int i = 0; i < methods.length; i++) {
            if ((methodName == null) || (methods[i].indexOf(methodName) != -1)) {
                stream.println(methods[i]);
                try {
                    methodCode = inputHandler.getSource(methods[i]);
                    stream.println(methodCode);
                }
                catch (MethodNotFoundException dontCare) { }
            }
        }
    }
    
    /**
     * Prints the bytecode for all Java classes contained in jar file.
     * 
     * @throws IllegalStateException If no jar file has been specified
     * using {@link #setSourceJar(String)}.
     * @throws IOException If there is an error writing to the stream, or
     * creating the output file, if applicable.
     */
    public void printJar() throws IOException {
        if (sourceJar == null) {
            throw new IllegalStateException("No jar file specified");
        }
        
        PrintWriter stream = getTargetStream();
        
        List<String> classes = new ArrayList<String>(20);
        int clCount = Handler.readJarClassesPaths(sourceJar, classes);
        Iterator<String> clIter = classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String curClass = clIter.next();
            
            setInputFile(curClass);
            stream.println("========================================" +
                "========================================");
            stream.println(" " + curClass);
            stream.println("========================================" +
            "========================================");
            stream.println();
            print(stream);
            stream.println();
        }
    }
    
    /*************************************************************************
     * Prints the ByteSourceViewer usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.ByteSourceViewer " +
            "<source_class> [output_file]");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for ByteSourceViewer.
     */ 
    public static void main(String argv[]) {
        if (argv.length < 1 || argv.length > 4) {
            printUsage();
        }

        try {
            ByteSourceViewer bsView = new ByteSourceViewer();
            int i = 1; for ( ; i < argv.length; i++) {
                if (argv[i].equals("-method")) {
                    if (i + 1 < argv.length) {
                        bsView.methodName = argv[++i];
                    }
                    else {
                        System.err.println("Method name missing");
                        System.exit(1);
                    }
                }
                else {
                    bsView.setOutputFile(argv[i]);
                }
            }
            if (argv[0].endsWith(".jar")) {
                bsView.setSourceJar(argv[0]);
                bsView.printJar();
            }
            else {
                bsView.setInputFile(argv[0]);
                bsView.print();
            }
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (ClassFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}



/*****************************************************************************/

/*
  $Log: ByteSourceViewer.java,v $
  Revision 1.7  2007/07/30 15:58:45  akinneer
  Updated year in copyright notice.

  Revision 1.6  2006/10/11 14:32:40  akinneer
  Added support to ByteSourceViewer for viewing class files in jar files.
  Modifications to Viewer to facilitate.

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

  Revision 1.9  2004/04/29 20:04:59  kinneer
  Changed to produce more useful output on unexpected errors.

  Revision 1.8  2004/04/07 22:13:36  kinneer
  Minor change to argument processing for easier extensibility.

  Revision 1.7  2004/04/03 00:10:33  kinneer
  Added parameter to allow viewing of individual methods' bytecodes.

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

  Revision 1.1  2003/03/03 20:35:43  aristot
  Moved ByteSourceViewers to viewers dir

  Revision 1.2  2002/08/21 06:27:53  sharmahi
  removed line numbers being output.

  Revision 1.1  2002/08/07 07:57:03  sharmahi
  After adding comments

*/      
   
      
