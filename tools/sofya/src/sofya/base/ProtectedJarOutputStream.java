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

import java.io.OutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Specialized <code>JarOutputStream</code> which disables the
 * <code>close()</code> method.
 *
 * <p>Using this class protects a JarOutputStream from being
 * arbitrarily closed when an external method is called to write
 * data to the stream. The <code>closeStream</code> method
 * may be used instead to close the stream. Specifically, this
 * class is used to protect a JarOutputStream from closure
 * by the <code>dump</code> method of the BCEL
 * <code>JavaClass</code> class.
 *
 * @author Alex Kinneer
 * @version 09/16/2004
 */
public class ProtectedJarOutputStream extends JarOutputStream {
    /**
     * Creates a ProtectedJarOutputStream which writes to the given
     * underlying stream.
     *
     * @param out Any output stream to which Jar file data is to be
     * written.
     */
    public ProtectedJarOutputStream(OutputStream out) throws IOException {
        super(out);
    }
        
    /**
     * Creates a ProtectedJarOutputStream which writes to the given
     * underlying stream and inserts the provided manifest file into
     * the Jar file.
     *
     * @param out Any output stream to which Jar file data is to be
     * written.
     * @param man Manifest to be inserted into the Jar file.
     */
    public ProtectedJarOutputStream(OutputStream out, Manifest man)
            throws IOException {
        super(out, man);
    }
        
    /**
     * Null override to prevent the stream from being closed with this
     * method.
     *
     * @throws IOException Never for this method.
     */
    public void close() throws IOException { }
        
    /**
     * Actually closes the stream.
     */
    public void closeStream() throws IOException {
        super.close();
    }
}
