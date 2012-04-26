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

package sofya.base;

import java.io.*;
import java.util.*;

import sofya.base.exceptions.BadFileFormatException;

/**
 * The SourceHandler provides routines to display Java source code files.
 *
 * @author Alex Kinneer
 * @version 11/19/2004
 *
 * @see sofya.viewers.SourceViewer
 */
public class SourceHandler extends Handler {
    /** Collection of source code lines in the file. */
    private String[] sourceLines = new String[0];
  
    /*************************************************************************
     * Reads Java source code file.
     *
     * <p>The source code is read from the file and stored to an internal
     * data structure. This data can then be retrieved via other
     * accessor functions in this class, such as
     * {@link #getSource}.</p>
     *
     * @param fileName Name of the source file to be read.
     *
     * @throws BadFileFormatException If the specified file is not a Java
     * source code file.
     * @throws FileNotFoundException If the specified file doesn't exist.
     * @throws IOException If there is an error reading from the source file.
     */
    public void readSourceFile(String fileName) throws BadFileFormatException,
                                                       FileNotFoundException,
                                                       IOException {
        if (!fileName.endsWith(".java")) {
            throw new BadFileFormatException("Not a Java source code file");
        }
        
        ArrayList<Object> lineBuffer = new ArrayList<Object>();
        String inputLine = null;
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(openInputFile(fileName)));
        
        try {
            while (true) { 
                inputLine = br.readLine();
                if (inputLine != null) {
                    lineBuffer.add(inputLine);
                }
                else { break; }
            }
            sourceLines = (String[]) lineBuffer.toArray(sourceLines);
        }
        finally {
            br.close();
        }
    }
    
    /*************************************************************************
     * Gets the number of lines in the file.
     *
     * @return The number of lines read from the file.
     */ 
    public int getNumOfLines() {
        return sourceLines.length;
    }
    
    /*************************************************************************
     * Gets all of the lines in the file.
     *
     * @return A collection of all of the lines read from the file.
     */
    public String[] getSource() {
        return sourceLines;
    } 
    
    /*************************************************************************
     * Gets a line from the file.
     *
     * @param lineNumber Line number of the line to be retrieved.
     *
     * @return The matching line in the file.
     *
     * @throws IndexOutOfBoundsException If <code>lineNumber</code> is
     * negative or exceeds the number of lines in the source file.
     */
    public String getSourceLine(int lineNumber)
                  throws IndexOutOfBoundsException {
        try {
            return sourceLines[lineNumber];
        } catch (IndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException("Line number exceeds " +
                "source length");
        }
    } 
    
    /*************************************************************************
     * Test driver for SourceHandler.
     */ 
    public static void main(String args[]) {
        SourceHandler src = new SourceHandler();
        String[] srclines;
    
        try {
            src.readSourceFile(args[0]);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            System.out.println(e.getMessage()) ;
            System.exit(1);
        }
        
        srclines = src.getSource();
        System.out.println("Contents : ");
        for (int i = 0; i < srclines.length; i++) {
            System.out.println(srclines[i]);
        }
        System.out.println("Length in lines " + src.getNumOfLines());
        
        try {
            System.out.println("Line : " + src.getSourceLine(200));
        }
        catch (IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } 
    }
}



/*****************************************************************************/

/*
  $Log: SourceHandler.java,v $
  Revision 1.6  2007/07/30 16:20:04  akinneer
  Updated year in copyright notice.

  Revision 1.5  2006/09/08 21:29:59  akinneer
  Updated copyright notice.

  Revision 1.4  2006/09/08 20:20:52  akinneer
  "Generified". Cleaned up imports.

  Revision 1.3  2006/03/21 21:49:39  kinneer
  Fixed JavaDoc references to reflect post-refactoring package organization.
  Various minor code cleanups. Updated copyright notice.

  Revision 1.2  2005/06/06 18:47:02  kinneer
  Added new class and copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.12  2004/09/14 15:24:25  kinneer
  Minor JavaDoc fix.

  Revision 1.11  2004/03/12 22:07:16  kinneer
  Modified read methods to ensure release of IO resources if an exception
  is thrown. Deprecated and eliminated references to NoFileNameException,
  which was a spurious annoyance. FileNotFoundException is now used
  instead.

  Revision 1.10  2003/08/27 18:44:06  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.9  2003/08/18 18:42:57  kinneer
  See v2.1.0 release notes for details.

  Revision 1.8  2003/08/13 18:28:37  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.7  2003/08/01 17:10:46  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.6  2002/07/04 06:56:48  sharmahi
  galileo/src/handlers/AbstractFile.java

  Revision 1.5  2002/07/03 06:14:57  sharmahi
  Added exceptions

  Revision 1.4  2002/06/25 09:09:57  sharmahi
  Added Package name "handlers"

  Revision 1.3  2002/06/09 08:45:27  sharmahi
  After first glance and review of fomrat, code style and file layout

  Revision 1.2  2002/01/30 16:14:52  sharmahi
  Added Source Handler and Viewer

*/
