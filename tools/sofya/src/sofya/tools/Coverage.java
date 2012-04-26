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

package sofya.tools;

import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.NumberFormat;

import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.viewers.Viewer;

/**
 * Computes coverage information for Java subjects traced by Sofya.
 * Currently only basic block coverage is supported.
 *
 * @author Alex Kinneer
 * @version 12/02/2005
 */
public final class Coverage {
    private static TestHistoryHandler thHandler = new TestHistoryHandler();
    /*private static CFHandler cfHandler = new CFHandler();
    
    static {
        cfHandler.setClearOnLoad(false);
    }
    */
    public static void printCoverage(PrintWriter pw, String thFile,
                                     boolean all)
                                     throws IOException {
        printCoverage(pw, thFile, all, null);
    }
    
    public static void printCoverage(PrintWriter pw, String thFile,
                                     boolean all, String tag)
                                     throws IOException {
        thHandler.readTestHistoryFile(thFile);
        String[] methodList = thHandler.getMethodList();
        long totBlocks = 0;
        long totCovered = 0;
        
        for (int i = 0; i < methodList.length; i++) {
            TestHistory tHist = null;
            try {
                tHist = thHandler.getTestHistory(methodList[i]);
            }
            catch (MethodNotFoundException e) {
                IOException ioe =
                    new IOException("Error read test history file");
                ioe.initCause(e);
                throw ioe;
            }
            
            pw.println(methodList[i]);
            pw.println();
            pw.println(" blockID   covered");
            pw.println("-------------------");
            
            int maxId = tHist.getHighestBlockID();
            for (int blkId = 1; blkId <= maxId; blkId++) {
                boolean cvrg = !tHist.isEmpty(blkId);
                
                if (cvrg) {
                    if (all) {
                        pw.println(Viewer.rightJust(blkId, 7) + "      YES");
                    }
                    totCovered += 1;
                }
                else {
                    pw.println(Viewer.rightJust(blkId, 7) + "       NO");
                }
            }
            totBlocks += maxId;
            
            pw.println();
        }
        
        NumberFormat fmt = NumberFormat.getPercentInstance();
        fmt.setMinimumFractionDigits(2);
        pw.println();
        pw.print("percent coverage: ");
        pw.println(fmt.format(((double) totCovered) / totBlocks));
        pw.println();
        
        pw.flush();
    }
    
    /*private static Block loadBlock(String methodName, int blkId)
            throws IOException {
        int ulPos = methodName.indexOf('_');
        int breakPos = methodName.lastIndexOf('.', ulPos);
        String className = methodName.substring(0, breakPos);
        
        CFG cfg = null;
        if (cfHandler.containsCFG(methodName)) {
            try {
                cfg = cfHandler.getCFG(methodName);
            }
            catch (MethodNotFoundException e) { }
        }
        else {
            cfHandler.readCFFile
        }
        return null;
    }*/

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java sofya.tools.Coverage <test_history> " +
            "[-ALL]"); // [-tag <db_tag>]");
        System.exit(1);
    }
    
    public static void main(String[] argv) {
        if (argv.length < 1) {
            printUsage();
        }
        
        String thFile = null;
        String tag = null;
        boolean all = false;
        
        // Process arguments
        int index = 0; for ( ; index < argv.length; index++) {
            if (argv[index].equals("-tag")) {
                if (index + 1 < argv.length) {
                    tag = argv[++index];
                }
                else {
                    System.err.println("Tag not specified");
                    printUsage();
                }
            }
            else if (argv[index].equals("-ALL")) {
                all = true;
            }
            else if (!argv[index].startsWith("-")) {
                thFile = argv[index];
            }
            else {
                System.err.println("Unrecognized parameter: " + argv[index]);
                printUsage();
            }
        }
        
        if (thFile == null) {
            System.err.println("No test history file specified");
            printUsage();
        }
        
        try {
            printCoverage(new PrintWriter(new OutputStreamWriter(System.out)),
                thFile, all, tag);
        }
        catch (FileNotFoundException e) {
            System.err.println(thFile + " not found");
            System.exit(1);
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
