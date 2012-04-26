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

package sofya.graphs.irg;

import java.io.*;

/**
 * Front end for building Interclass Relation Graphs.
 *
 * <p>This class uses a &apos;<code>.prog</code>&apos; file to construct
 * an interclass relation graph for the specified classes and writes it to
 * a file in the database.</p>
 *
 * <p>Usage:<br><code>java sofya.graphs.irg.jIRG [-tag &lt;tag&gt;]
 * &lt;<i>listfile</i>&gt;</code></p>
 *
 * @author Alex Kinneer
 * @version 04/14/2004
 *
 * @see sofya.graphs.irg.IRG
 */
public final class jIRG {
    /** No reason to instantiate class. */
    private jIRG() { }
    
    /**
     * Prints the usage message and exits with an error code.
     */
    private static void printUsage() {
        System.err.println("Usage:\njava sofya.graphs.irg.jIRG " +
            "[-tag <tag>] <listfile>\n");
        System.exit(1);
    }
    
    /**
     * Entry point for the IRG front end.
     */
    public static void main(String[] argv) {
        if (argv.length < 1 || argv.length > 3) {
            printUsage();
        }
        
        int index = 0;
        
        // Check and set tag name if necessary
        String tag = null;
        if (argv[0].equals("-tag")) {
            if (argv.length < 3) {
                printUsage();
            }
            tag = argv[1];
            index = 2;
        }
        
        IRG irg = null;
        try {
            irg = new IRG(argv[index], tag);
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        catch (ClassFormatError e) {
            System.err.println("Class format error: " + e.getMessage());
            System.exit(1);
        }
        
        try {
            IRGHandler.writeIRGFile(argv[index], tag, irg);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
