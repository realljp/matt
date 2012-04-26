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

package sofya.graphs.cfg;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.CacheException;

import gnu.trove.THashSet;

/**
 * Front end to the CFG builder. 
 *
 * <p>A classfile name (or set of names) is provided as an input to the
 * jCFG class. It then uses a {@link sofya.graphs.cfg.CFGBuilder} to construct
 * the control flow graphs for each method in each class and then save .cf
 * and .map files for the class.</p>
 *
 * <p><b>Note:</b> This class cannot be instantiated. The correct method for
 * other objects to build graphs for a class is to use the
 * {@link sofya.graphs.cfg.CFGBuilder} class.
 *
 * <p>Usage:<br><code>java sofya.graphs.cfg.jCFG [-tag &lt;tag&gt;]
 * &lt;<i>classname</i>&gt; [<i>classname</i> ...]
 * (without <i>.class</i> extension)</code></p>
 *
 * @author Alex Kinneer
 * @version 04/29/2005
 *
 * @see sofya.graphs.cfg.CFG
 * @see sofya.graphs.cfg.CFGBuilder
 */
public class jCFG {
    /** Graph builder used to construct and cache CFGs. */
    private static CFGBuilder cfgb;
    /** Database tag to be associated with the CFGs. */
    private static String tag = null;
    
    /** Private default constructor does not permit instantiation. */
    private jCFG() { }

    /*************************************************************************
     * Prints the jCFG usage message and exit.
     */
    private static void printUsage() {
        System.err.println("Usage:\njava sofya.graphs.cfg.jCFG [-tag <tag>] " +
            "<classfile|jarfile|listfile> [classfile|jarfile ...]\n");
        System.exit(1);
    }
    
    /*************************************************************************
     * Entry point for the CFG front-end.
     *
     * <p>The subject class is loaded, control flow graphs are built for
     * each of its methods, and control flow graph and map files are
     * written to the database directory based on the data.</p>
     */
    public static void main(String[] argv) {
        if (argv.length < 1) {
            printUsage();
        }
        
        int index = 0;
        
        // Check and set tag name if necessary
        if (argv[0].equals("-tag")) {
            if (argv.length < 3) {
                printUsage();
            }
            tag = argv[1];
            index = 2;
        }
        
        // Read the list file or list of classes
        LinkedList<ProgramUnit> inputList = new LinkedList<ProgramUnit>();
        if (argv[index].endsWith(".prog")) {
            try {
                Handler.readProgFile(argv[index], tag, inputList);
            }
            catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
        else {
            ProgramUnit defaultUnit = new ProgramUnit();
            inputList.add(defaultUnit);
            
            for ( ; index < argv.length; index++) {
                if (argv[index].endsWith(".jar")) {
                    ProgramUnit jarUnit = new ProgramUnit(argv[index]);
                    
                    try {
                        Handler.readJarClasses(argv[index], jarUnit.classes);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    
                    inputList.add(jarUnit);
                }
                else {
                    defaultUnit.addClass(argv[index]);
                }
            }
        }
        
        // Process the input list to obtain a copy where jar files are
        // replaced with their contained class files. This is necessary for
        // the construction of an IRG when an interprocedural type inference
        // algorithm is activated.
        List<String> classList = new ArrayList<String>();
        int inputCount = inputList.size();
        Iterator iterator = inputList.iterator();
        for (int i = inputCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) iterator.next();
            classList.addAll(pUnit.classes);
        }
        
        //long startTime = System.currentTimeMillis();
        
        try {
            cfgb = new CFGBuilder(classList);
            classList = null; // Free the memory
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        catch (CacheException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        
        if (ProjectDescription.ENABLE_BRANCH_EXTENSIONS) {
            cfgb.addTransformer(new BranchFlowProcessor());
        }

        // Loop building and saving cf and map files
        String entry = null;
        while (inputList.size() > 0) {
            ProgramUnit pUnit = (ProgramUnit) inputList.removeFirst();
            
            if (pUnit.isJar) {
                try {
                    entry = pUnit.location;
                    buildGraphsForJar(pUnit);
                }
                // (Messages are printed when exception is first caught
                // inside the called method)
                catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                catch (ClassFormatError e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            else {
                int clCount = pUnit.classes.size();
                iterator = pUnit.classes.iterator();
                for (int i = clCount; i-- > 0; ) {
                    entry = (String) iterator.next();
                    try {
                        cfgb.loadClass(entry);
                    }
                    catch (CFGBuilder.LoadException e) {
                        String msg = e.getMessage();
                        Throwable cause = e.getCause();
                        boolean doExit = true;
                        if (cause != null) {
                            msg += ": " + e.getCause().getMessage();
                            doExit =
                                !(cause instanceof BadFileFormatException);
                        }
                        System.err.println(msg);
                        if (doExit) {
                            System.exit(1);
                        }
                        else {
                            continue;
                        }
                    }
                    
                    try {
                        buildGraphs();
                    }
                    catch (TypeInferenceException e) {
                        if (e.getCause() != null) {
                            e.getCause().printStackTrace();
                        }
                        System.err.println(e.getMessage());
                        System.exit(1);
                    }
                    catch (CacheException e) {
                        if (e.getCause() != null) {
                            e.getCause().printStackTrace();
                        }
                        System.err.println(e.getMessage());
                        System.exit(1);
                    }
                    catch (TransformationException e) {
                        if (e.getCause() != null) {
                            e.getCause().printStackTrace();
                        }
                        System.err.println(e.getMessage());
                        System.exit(1);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Error saving cf or map file for " +
                                           entry);
                        System.exit(1);
                    }
                }
            }
        }
        
        //long endTime = System.currentTimeMillis();
        
        //System.out.println("Time elapsed: " + (endTime - startTime));
    }

    /**
     * Helper method to construct graphs for all methods in the class and write
     * the corresponding database files.
     */
    private static void buildGraphs() throws TypeInferenceException,
                                             TransformationException,
                                             IOException {
        cfgb.buildAllCFG();
        cfgb.writeCFFile(false, tag);
        cfgb.writeMapFile(false, tag);
    }
    
    /**
     * Helper method to construct graphs and write database files for all
     * classes contained in a jar file.
     *
     * @param jarName Name of the jar file for which CFGs are to be
     * constructed.
     */
    private static void buildGraphsForJar(ProgramUnit jarUnit)
                        throws IOException, Exception {
        JarFile sourceJar = new JarFile(jarUnit.location);
        Set includeClasses = new THashSet(jarUnit.classes);
        
        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                String entryName = ze.getName();
                
                if (ze.isDirectory() || !entryName.endsWith(".class")) {
                    continue;
                }
                else {
                    entryName = entryName.substring(0,
                        entryName.lastIndexOf(".class")).replace('/', '.');
                    if (includeClasses.contains(entryName)) {
                        continue;
                    }
                }
                
                ///////////////////////
                // Temporary for JMeter
                ///////////////////////
                // if (ze.getName().endsWith("$Test.class")) {
                //     System.err.println("Skipping: " + ze.getName());
                //     continue;
                // }
                
                entryStream =
                    new BufferedInputStream(sourceJar.getInputStream(ze));
                try {
                    cfgb.loadClass(ze.getName(), entryStream);
                }
                catch (CFGBuilder.LoadException exc) {
                    System.err.println(exc.getMessage());
                    if (exc.getCause() != null) {
                        System.err.println("   " + exc.getCause().getMessage());
                    }
                    continue;
                }
                
                try {
                    buildGraphs();
                }
                catch (IOException exc) {
                    exc.printStackTrace();
                    System.err.println("Error saving cf or map file for " +
                        ze.getName());
                    throw exc;
                }
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }
    }
}

/*****************************************************************************/

