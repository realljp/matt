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
import java.util.StringTokenizer;

/**
 * Abstract superclass of all viewers.  Defines the contract for viewers
 * and encapsulates data members and methods shared by all classes of
 * viewer.
 *
 * <p>Since viewer outputs are expected to be human-readable character
 * data, all viewer classes use java.io.Writer objects internally to write
 * data to the streams. Reading of the streams should be handled
 * accordingly.</p>
 *
 * @author Alex Kinneer
 * @version 10/10/2006
 */
public abstract class Viewer {
    /** Buffered character reader attached to <code>System.in</code>,
        provided for convenience. */
    protected static final BufferedReader stdin =
        new BufferedReader(new InputStreamReader(System.in));
    /** The system dependent newline character sequence. */
    protected static final String LINE_SEP =
        System.getProperty("line.separator");

    /** Stream which will currently be used if no output file is specified. */
    private PrintWriter destStream = new PrintWriter(System.out, true);
    /** Name of the file being viewed. */
    protected String inputFile;
    /** Name of the output file, if any. */
    protected File outputFile;
    
    /*************************************************************************
     * Prints the viewer output to the specified stream - subclasses
     * must provide the implementation for this method.
     *
     * <p>The stream passed to this method takes highest precedence, it
     * need not necessarily be any stream maintained or created internally
     * by the viewer. Use {@link Viewer#print()} to have the viewer select
     * or create a stream based on specified settings and an order of
     * precedence.</p>
     *
     * @param stream Stream to which the viewer outputs are to be written.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public abstract void print(PrintWriter stream) throws IOException;

    /*************************************************************************
     * Protected default constructor to prevent unsafe instantiation of
     * viewers.
     */
    protected Viewer() { }
    
    /*************************************************************************
     * Standard constructor, creates a viewer which displays its output
     * to the system console (<code>System.out</code>).
     *
     * @param inputFile Name of the input file whose contents are being
     * displayed by the viewer.
     */ 
    public Viewer(String inputFile) {
        setInputFile(inputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a viewer which prints its output
     * to the specified file.
     *
     * @param inputFile Name of the input file whose contents are being
     * displayed by the viewer.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public Viewer(String inputFile, String outputFile)
           throws SameFileNameException, IOException {
        if (outputFile == null) {
            throw new IOException("Output file must be specified");
        }
        setInputFile(inputFile);
        setOutputFile(outputFile);
    }
    
    /*************************************************************************
     * Standard constructor, creates a viewer which prints its output
     * to the specified stream.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param inputFile Name of the input file whose contents are being
     * displayed by the viewer.
     * @param stream Stream to which the viewer output should be written.
     */ 
    public Viewer(String inputFile, OutputStream stream) {
        setInputFile(inputFile);
        setOutputStream(stream);
    }

    /*************************************************************************
     * Sets the file to be viewed by this viewer.
     *
     * @param fileName Name of the input file whose contents are being
     * displayed by the viewer.
     */ 
    public final void setInputFile(String fileName) {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new IllegalArgumentException("No file specified for " +
                                               "viewing");
        }
        inputFile = fileName;
    }
    
    /*************************************************************************
     * Sets the name of the file to which viewer outputs should be written.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, output to file
     * takes precedence over any stream specified in a constructor or with
     * {@link Viewer#setOutputStream}.</p>
     *
     * @param fileName Name of the file to which the viewer output should
     * be written. If <code>null</code>, the viewer will use the last
     * specified stream, or <code>System.out</code> by default.
     *
     * @throws SameFileNameException If the specified output file and input
     * file are the same file.
     * @throws IOException If there is an error creating the output file.
     */ 
    public final void setOutputFile(String fileName)
            throws SameFileNameException, IOException {
        if (fileName == null) {
            outputFile = null;
            return;
        }
        outputFile = new File(fileName);
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }
        else {
            if (fileName.equals(inputFile)) {
                throw new SameFileNameException("Cannot specify input " +
                    "file as the output file");
            }
        }
    }
    
    /*************************************************************************
     * Sets the stream to which viewer outputs should be written.
     *
     * <p><b>Note:</b> When using {@link Viewer#print()}, an output file
     * specified using {@link Viewer#setOutputFile} takes precedence over
     * the specified stream.</p>
     *
     * @param stream Stream to which the viewer output should be written.
     */ 
    public final void setOutputStream(OutputStream stream) {
        destStream = new PrintWriter(stream, true);
    }
    
    /*************************************************************************
     * Prints the viewer outputs to the stream with highest precedence,
     * ordered as follows:
     * <ul><li>The specified output file, if any</li>
     *     <li>The specified stream, if any</li>
     *     <li>The <code>System.out</code> stream</li>
     * </ul>
     *
     * @throws IOException If there is an error writing to the stream.
     */ 
    public final void print() throws IOException {
        PrintWriter outStream = getTargetStream();
        try {
            print(outStream);
        }
        finally {
            if (outputFile != null) {
                outStream.close();
            }
        }
    }
    
    /*************************************************************************
     * Prints a method name in a normalized human-readable format. Used
     * to guarantee a consistent display format.
     *
     * @param methodName Method name to be formatted, as returned by a
     * BCEL method.
     * @param stream Stream to which the normalized form is to be printed.
     */
    public static void printMethodName(String methodName, PrintWriter stream) {
        if (methodName.length() > 70) {
            StringTokenizer st = new StringTokenizer(methodName, "_");
            StringBuffer sb = new StringBuffer("Method: \"");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if ((sb.length() + token.length()) > 70) {
                    stream.println(sb.toString());
                    sb.delete(0, sb.length());
                    sb.append("\t");
                } 
                sb.append(token);
                if (st.hasMoreTokens()) {
                    sb.append("_");
                }
            }
            stream.println(sb.toString() + "\"");
        }
        else {
            stream.println("Method: \"" + methodName + "\"");
        }
    }
    
    /*************************************************************************
     * Gets the stream to which viewer outputs should be sent, according
     * to the following precedence order:
     * <ul><li>The specified output file, if any</li>
     *     <li>The specified stream, if any</li>
     *     <li>The <code>System.out</code> stream</li>
     * </ul>
     * 
     * @return The stream to which viewer outputs should be written.
     *
     * @throws IOException If there is an error opening an output stream
     * for the output file.
     */ 
    protected final PrintWriter getTargetStream() throws IOException {
        if (outputFile != null) {
            return new PrintWriter(
                   new BufferedWriter(
                   new FileWriter(outputFile)), true);
        }
        else {
            return destStream;
        }
    }

    // Utility functions for simple formatting of String objects.
    // Law 6/18/02002
    // Copyright James B. Law, 2002

    /*************************************************************************
     * Sets a string to a fixed size.
     *
     * <p>If the string is shorter than the requested size, it is padded
     * with spaces (ASCII 32). If it is longer than the requested size,
     * it is truncated.</p>
     *
     * @param s String to be resized.
     * @param size New length to which the string is to be sized.
     *
     * @return The provided string, padded or truncated to the specified size.
     */
    public static String sizeString(String s, int size) {
        int l = s.length();
        
        // If it's short, pad it,
        if (l < size) {
            StringBuffer t = new StringBuffer(s);
            while (l < size) {
                t.append(" ");
                l += 1;
            }
            return t.toString();
        }
        // else clip it off.
        return s.substring(0, size - 1);
    }

    /*************************************************************************
     * Converts an integer to a string padded to a specified width.
     *
     * <p>If the length of the integer string after conversion is less
     * than the requested length, it is padded with spaces (ASCII 32).
     * Otherwise it is left unchanged (it will not be truncated).</p>
     *
     * @param value Integer value to be converted to a string.
     * @param width Required length of the string after conversion.
     *
     * @return The string representation of the provided integer value,
     * padded to the specified size if necessary.
     */
    public static String rightJust(int value, int width) {
        StringBuffer t = new StringBuffer();
        t.append(value);
        int l = t.length();
        
        if (l < width) {
            while (l < width) {
                t.insert(0, ' ');
                l += 1;
            }
        }
        // don't want to clip it off
        return t.toString();
    }
}
