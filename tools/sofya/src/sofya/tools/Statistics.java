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

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProjectDescription;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.*;
import sofya.graphs.cfg.*;
import static sofya.base.SConstants.*;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

/**
 * This class collects and reports various statistics about a class
 * or set of classes which may be useful for supporting claims about
 * characteristics of subjects upon which research conclusions are
 * being based. It can operate on a list of one or more class
 * and/or jar files, or on a &quot;<code>prog.lst</code>&quot;
 * file.
 *
 * @author Alex Kinneer
 * @version 06/09/2006
 */
public final class Statistics {
    /** Writer to which the output is sent. */
    private static PrintWriter out = new PrintWriter(new BufferedWriter(
        new OutputStreamWriter(System.out)));

    /** Total number of instructions in all classes analyzed. */
    private static long totalInstr = 0;
    /** Total number of CFG basic blocks in all classes analyzed. */
    private static int totalBlocks = 0;
    /** Total number of CFG blocks corresponding to actual code
        (true basic and call blocks) in all classes analyzed. */
    private static int totalRealBlocks = 0;
    /** Total number of exception handlers in all classes analyzed. */
    private static int totalHandlers = 0;
    /** Total number of finally blocks in all classes analyzed. */
    private static int totalFinallys = 0;
    /** Total number of classes analyzed. */
    private static int totalClassCount = 0;
    /** Total number of methods analyzed in all classes. */
    private static int totalMethodCount = 0;
    /** Total number of methods containing exception handlers in
        all classes analyzed. */
    private static int totalMethodsWithHandlers = 0;
    /** Total number of branches in all classes analyzed. */
    private static int totalBranches = 0;
    /** Records size distributions of methods based on ranges of
        instruction counts over all methods analyzed. */
    private static int[] instrDist = new int[13];
    /** Records size distributions of methods based on ranges of
        'real' block counts over all methods analyzed. */
    private static int[] blockDist = new int[14];

    /** Handler for loading CFGs from CF files in database, if possible. */
    private static CFHandler cfHandler = new CFHandler();
    /** GraphBuilder to construct graphs for which CFGs could not be
        loaded from file. */
    private static CFGBuilder graphBuilder = null;
    /** Database tag specified by user. */
    private static String tag = null;

    /** Flag which forces the statistics tool to always request a freshly
        built CFG (as opposed to reading it from the database. */
    private static boolean forceFreshCFG = false;

    /** Initializes data structures. */
    static {
        for (int i = 0; i < instrDist.length; i++) {
            instrDist[i] = 0;
        }
        for (int i = 0; i < blockDist.length; i++) {
            blockDist[i] = 0;
        }
    }

    /** No reason to instantiate this class. */
    private Statistics() { }

    /**
     * Prints the Statistics tool usage message and exists with
     * an error code.
     */
    private static void printUsage() {
        System.err.println("java sofya.tools.Statistics [-o <outfile>] " +
            "<classfile|jarfile|listfile>\n  [classfile|jarfile ...]");
        System.exit(1);
    }

    /**
     * Entry point for the Statistics tool.
     */
    public static void main(String[] argv) {
        String outFile = null;

        if (argv.length < 1) {
            printUsage();
        }

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
            else if (argv[index].equals("-o")) {
                if (index + 1 < argv.length) {
                    outFile = argv[++index];
                }
                else {
                    System.err.println("Output file not specified");
                    printUsage();
                }
            }
            else if (argv[index].equals("-fresh")) {
                forceFreshCFG = true;
            }
            else if (!argv[index].startsWith("-")) {
                break;
            }
            else {
                System.err.println("Unrecognized parameter: " + argv[index]);
                printUsage();
            }
        }

        // Set up output redirection if necessary
        if (outFile != null) {
            try {
                out = new PrintWriter(new BufferedWriter(
                      new FileWriter(outFile)));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error creating output file");
                System.exit(1);
            }
        }

        List<Object> inputList = new ArrayList<Object>();
        List<ProgramUnit> unitList = new ArrayList<ProgramUnit>();

        ProgramUnit defaultUnit = new ProgramUnit();
        inputList.add(defaultUnit);

        for ( ; index < argv.length; index++) {
            if (argv[index].endsWith(".prog")) {
                try {
                    Handler.readProgFile(argv[index], tag, unitList);
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
                inputList.addAll(unitList);
            }
            else if (argv[index].endsWith(".jar")) {
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

        List<String> classList = new ArrayList<String>();
        int inputCount = inputList.size();
        Iterator iterator = inputList.iterator();
        for (int i = inputCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) iterator.next();
            classList.addAll(pUnit.classes);
        }

        try {
            graphBuilder = new CFGBuilder(classList);
        }
        catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }

        if (ProjectDescription.ENABLE_BRANCH_EXTENSIONS) {
            graphBuilder.addTransformer(new BranchFlowProcessor());
        }

        iterator = inputList.iterator();
        for (int i = inputCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) iterator.next();

            try {
                if (pUnit.isJar) {
                    printJarStatistics(pUnit);
                }
                else {
                    printStatistics(pUnit);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        printProgramSummary();

        if (outFile != null) out.close();
    }

    /**
     * Prints statistics for a single class. This consists of various
     * information about each method in the class followed by a
     * summary of characteristics of the class as a whole.
     *
     * @param className Name of the class for which to print statistics.
     * @param source Input stream from which the class data should be
     * read. This parameter is permitted to be <code>null</code>, in
     * which case the method will attempt to find the class on the
     * classpath or by treating the name as an absolute path, in
     * that order.
     *
     * @throws IOException If the class cannot be found or there is
     * a problem reading the class file.
     * @throws ClassFormatError If the class file is invalid.
     */
    private static void printClassStatistics(String className,
            InputStream source)
            throws TypeInferenceException, IOException, ClassFormatError {
        // Read the class
        JavaClass javaClass = null;
        if (source != null) {
            javaClass = new ClassParser(source, className).parse();
        }
        else {
            javaClass = Handler.parseClass(className);
        }

        // Retrieve some BCEL data
        ConstantPoolGen cpg = new ConstantPoolGen(javaClass.getConstantPool());
        Method[] methods = javaClass.getMethods();
        String fullName = javaClass.getClassName();

        // Initialize class-level counters
        long cl_totalInstr = 0;
        int cl_totalBlocks = 0;
        int cl_totalRealBlocks = 0;
        int cl_totalHandlers = 0;
        int cl_totalFinallys = 0;
        int cl_totalMethodsWithHandlers = 0;
        int cl_totalBranches = 0;

        // Attempt to load a CF file for the class, and create a
        // graph builder for it if that fails
        try {
            cfHandler.readCFFile(fullName + ".java", tag);
        }
        catch (FileNotFoundException e) {
            // It'll be backed by a graph builder as well
        }
        catch (EmptyFileException e) {
            System.err.println("WARNING: " + e.getMessage());
            return;
        }
        try {
            graphBuilder.loadClass(javaClass);
        }
        catch (CFGBuilder.LoadException e) {
            String msg = e.getMessage();
            Throwable cause = e.getCause();
            if (cause != null) {
                msg += ": " + cause.getMessage();
            }
            System.err.println("WARNING: " + msg);
            return;
        }

        // Print class header
        out.println();
        out.println("Statistics for class " + fullName);
        out.println("----------------------------------------" +
            "----------------------------------------");

        // Iterate over methods and print statistics for each
        for (int i = 0; i < methods.length; i++) {
            // Remove any special BCEL formatting from signature
            // (tabs, newlines) and print method name
            out.print("Method: ");
            StringTokenizer stok = new StringTokenizer(methods[i].toString());
            while (true) {
                out.print(stok.nextToken());
                if (stok.hasMoreTokens()) {
                    out.print(" ");
                }
                else {
                    out.println();
                    break;
                }
            }

            // Get instruction list
            MethodGen mg = new MethodGen(methods[i], fullName, cpg);
            MethodSignature ms = new MethodSignature(mg);
            if (mg.getInstructionList() == null) {
                // Ignore abstract/native methods
                continue;
            }

            // Start printing statistics, pretty much self-explanatory
            int instrCount = mg.getInstructionList().size();
            cl_totalInstr += instrCount;
            out.println("Number of instructions: " + instrCount);
            recordInstructionCount(instrCount);

            CFG cfg = null;
            try {
                if (forceFreshCFG) {
                    cfg = graphBuilder.buildCFG(ms);
                }
                else if (cfHandler.containsCFG(ms)) {
                    try {
                        cfg = cfHandler.getCFG(ms);
                    }
                    catch (MethodNotFoundException e) {
                        System.err.println("WARNING: Handler falsely " +
                            "reported CFG was available for " + e.getMessage());
                        cfg = graphBuilder.getCFG(ms);
                    }
                }
                else {
                    cfg = graphBuilder.getCFG(ms);
                }
            }
            catch (MethodNotFoundException e) {
                System.err.println("Could not load or construct CFG for " +
                    ms + "; method does not exist in class");
                System.exit(1);
            }
            catch (TypeInferenceException e) {
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

            Block[] blocks = cfg.getBasicBlocks();
            cl_totalBlocks += blocks.length;
            out.println("Number of basic blocks: " + blocks.length);

            int realBlockCount = 0;
            for (int n = 0; n < blocks.length; n++) {
                if ((blocks[n].getType() == BlockType.BLOCK)
                        || (blocks[n].getType() == BlockType.CALL)) {
                    realBlockCount += 1;
                }
            }
            cl_totalRealBlocks += realBlockCount;
            out.println("Number of 'real' blocks: " + realBlockCount);
            recordBlockCount(realBlockCount);

            CodeExceptionGen[] exceptions = mg.getExceptionHandlers();
            cl_totalHandlers += exceptions.length;
            out.println("Number of exception handlers: " + exceptions.length);

            int finallyCount = 0;
            for (int n = 0; n < exceptions.length; n++) {
                if (exceptions[n].getCatchType() == null) {
                    finallyCount++;
                }
            }
            cl_totalFinallys += finallyCount;
            out.println("Number of finally blocks: " + finallyCount);

            if (exceptions.length > 0) {
                cl_totalMethodsWithHandlers += 1;
            }

            int branchCount = cfg.getNumberOfBranches();
            cl_totalBranches += branchCount;
            out.println("Number of branches: " + branchCount);
            out.println();


            totalMethodCount += 1;
        }

        // Print the class summary
        out.println();
        out.println("Summary for class " + fullName);
        out.println("----------------------------------------" +
            "----------------------------------------");
        out.println("Avg # of instructions: " + (cl_totalInstr / (double) methods.length));
        out.println("Avg # of basic blocks: " + (cl_totalBlocks / (double) methods.length));
        out.println("Agv # of 'real' blocks: " + (cl_totalRealBlocks / (double) methods.length));
        out.println("Avg # of exception handlers: " + (cl_totalHandlers / (double) methods.length));
        out.println("Avg # of finally blocks: " + (cl_totalFinallys / (double) methods.length));
        out.println("Avg # of branches: " + (cl_totalBranches / (double) methods.length));
        out.println("Proportion of methods with exception handlers: " +
            (cl_totalMethodsWithHandlers / (double) methods.length) * 100 + " %");
        out.println();
        out.println("Number of instructions in class: " + cl_totalInstr);
        out.println();

        // Add class totals to program totals
        totalInstr += cl_totalInstr;
        totalBlocks += cl_totalBlocks;
        totalRealBlocks += cl_totalRealBlocks;
        totalHandlers += cl_totalHandlers;
        totalFinallys += cl_totalFinallys;
        totalBranches += cl_totalBranches;
        totalMethodsWithHandlers += cl_totalMethodsWithHandlers;
        totalClassCount += 1;
    }

    private static void printStatistics(ProgramUnit pUnit)
            throws TypeInferenceException, IOException, ClassFormatError {
        int clCount = pUnit.classes.size();
        Iterator iterator = pUnit.classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String entry = (String) iterator.next();

            if (pUnit.useLocation) {
                String file = pUnit.location +
                    entry.replace('.', File.separatorChar) + ".class";
                BufferedInputStream fin = new BufferedInputStream(
                    new FileInputStream(file));
                try {
                    printClassStatistics(file, fin);
                }
                finally {
                    try {
                        fin.close();
                    }
                    catch (IOException e) { }
                }

            }
            else {
                printClassStatistics(entry, null);
            }
        }
    }

    /**
     * Prints statistics for all the classes in a jar file.
     *
     * @param jarName Name of the jar file for which statistics
     * should be printed.
     *
     * @throws IOException If the jar file cannot be found or there is
     * a problem reading a class from the file.
     * @throws ClassFormatError If a class file in the jar is invalid.
     */
    private static void printJarStatistics(ProgramUnit jarUnit)
            throws TypeInferenceException, IOException, ClassFormatError {
        JarFile sourceJar = new JarFile(jarUnit.location);

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                if (ze.isDirectory() || !ze.getName().endsWith(".class")) continue;

                entryStream = new BufferedInputStream(sourceJar.getInputStream(ze));
                printClassStatistics(ze.getName(), entryStream);
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }

            try {
                sourceJar.close();
            }
            catch (IOException e) { }
        }
    }

    /**
     * Uses the given size of a method in instructions to increment
     * the appropriate counter in the array which records the distributions
     * of method sizes based on instruction counts.
     *
     * @param count Number of instructions in the method.
     */
    private static void recordInstructionCount(int count) {
        if ((count >= 0) && (count < 10)) {
            instrDist[0] += 1;
        }
        else if ((count >= 10) && (count < 20)) {
            instrDist[1] += 1;
        }
        else if ((count >= 20) && (count < 30)) {
            instrDist[2] += 1;
        }
        else if ((count >= 30) && (count < 40)) {
            instrDist[3] += 1;
        }
        else if ((count >= 40) && (count < 50)) {
            instrDist[4] += 1;
        }
        else if ((count >= 50) && (count < 60)) {
            instrDist[5] += 1;
        }
        else if ((count >= 60) && (count < 80)) {
            instrDist[6] += 1;
        }
        else if ((count >= 80) && (count < 100)) {
            instrDist[7] += 1;
        }
        else if ((count >= 100) && (count < 150)) {
            instrDist[8] += 1;
        }
        else if ((count >= 150) && (count < 200)) {
            instrDist[9] += 1;
        }
        else if ((count >= 200) && (count < 350)) {
            instrDist[10] += 1;
        }
        else if ((count >= 350) && (count < 500)) {
            instrDist[11] += 1;
        }
        else if (count >= 500) {
            instrDist[12] += 1;
        }
        else {
            // What the?  (negative instruction count??)
            System.err.println("WARNING: Method reported negative number of instructions");
        }
    }

    /**
     * Uses the given size of a method in 'real' basic blocks to increment
     * the appropriate counter in the array which records the distributions
     * of method sizes based on block counts.
     *
     * @param count Number of 'real' basic blocks in the method.
     */
    private static void recordBlockCount(int count) {
        if ((count >= 0) && (count < 2)) {
            blockDist[0] += 1;
        }
        else if (count == 2) {
            blockDist[1] += 1;
        }
        else if (count == 3) {
            blockDist[2] += 1;
        }
        else if ((count >= 4) && (count < 10)) {
            blockDist[3] += 1;
        }
        else if ((count >= 10) && (count < 20)) {
            blockDist[4] += 1;
        }
        else if ((count >= 20) && (count < 30)) {
            blockDist[5] += 1;
        }
        else if ((count >= 30) && (count < 40)) {
            blockDist[6] += 1;
        }
        else if ((count >= 40) && (count < 50)) {
            blockDist[7] += 1;
        }
        else if ((count >= 50) && (count < 60)) {
            blockDist[8] += 1;
        }
        else if ((count >= 60) && (count < 70)) {
            blockDist[9] += 1;
        }
        else if ((count >= 70) && (count < 80)) {
            blockDist[10] += 1;
        }
        else if ((count >= 80) && (count < 90)) {
            blockDist[11] += 1;
        }
        else if ((count >= 90) && (count < 100)) {
            blockDist[12] += 1;
        }
        else if (count >= 100) {
            blockDist[13] += 1;
        }
        else {
            // What the?  (negative block count??)
            System.err.println("WARNING: Method reported negative number of blocks");
        }
    }

    /**
     * Prints summary statistics for all the classes that were
     * analyzed.
     */
    private static void printProgramSummary() {
        // Kind of ugly, but whatcha gonna do...
        out.println();
        out.println("Program statistics");
        out.println("----------------------------------------" +
            "----------------------------------------");
        out.println("Avg # of instructions/method: " + (totalInstr / (double) totalMethodCount));
        out.println("Avg # of basic blocks/method: " + (totalBlocks / (double) totalMethodCount));
        out.println("Avg # of 'real' blocks/method: " + (totalRealBlocks / (double) totalMethodCount));
        out.println("Avg # of exception handlers/method: " + (totalHandlers / (double) totalMethodCount));
        out.println("Avg # of finally blocks/method: " + (totalFinallys / (double) totalMethodCount));
        out.println("Avg # of branches/method: " + (totalBranches / (double) totalMethodCount));
        out.println();
        out.println("Avg # of instructions/class: " + (totalInstr / (double) totalClassCount));
        out.println("Avg # of basic blocks/class: " + (totalBlocks / (double) totalClassCount));
        out.println("Avg # of 'real' blocks/class: " + (totalRealBlocks / (double) totalClassCount));
        out.println("Avg # of exception handlers/class: " + (totalHandlers / (double) totalClassCount));
        out.println("Avg # of finally blocks/class: " + (totalFinallys / (double) totalClassCount));
        out.println("Avg # of branches/class: " + (totalBranches / (double) totalClassCount));
        out.println();
        out.println("Total # of instructions: " + totalInstr);
        out.println("Total # of basic blocks: " + totalBlocks);
        out.println("Total # of 'real' blocks: " + totalRealBlocks);
        out.println("Total # of exception handlers: " + totalHandlers);
        out.println("Total # of finally blocks: " + totalFinallys);
        out.println("Total # of branches: " + totalBranches);
        out.println("Proportion of methods with exception handlers: " +
            (totalMethodsWithHandlers / (double) totalMethodCount) * 100 + " %");
        out.println("Proportion of finallys to all handlers: " + (totalFinallys / (double) totalHandlers) * 100 + " %");
        out.println();
        out.println("Number of methods: " + totalMethodCount);
        out.println("Number of classes: " + totalClassCount);
        out.println();
        out.println("Distribution of method sizes: " );
        out.println();
        out.println("  # of instructions\t# of methods\tProportion out of all methods");
        out.println("  0-9\t\t\t" + instrDist[0] + "\t\t" + (instrDist[0] / (double) totalMethodCount) * 100 + " %");
        out.println("  10-19\t\t\t" + instrDist[1] + "\t\t" + (instrDist[1] / (double) totalMethodCount) * 100 + " %");
        out.println("  20-29\t\t\t" + instrDist[2] + "\t\t" + (instrDist[2] / (double) totalMethodCount) * 100 + " %");
        out.println("  30-39\t\t\t" + instrDist[3] + "\t\t" + (instrDist[3] / (double) totalMethodCount) * 100 + " %");
        out.println("  40-49\t\t\t" + instrDist[4] + "\t\t" + (instrDist[4] / (double) totalMethodCount) * 100 + " %");
        out.println("  50-59\t\t\t" + instrDist[5] + "\t\t" + (instrDist[5] / (double) totalMethodCount) * 100 + " %");
        out.println("  60-79\t\t\t" + instrDist[6] + "\t\t" + (instrDist[6] / (double) totalMethodCount) * 100 + " %");
        out.println("  80-99\t\t\t" + instrDist[7] + "\t\t" + (instrDist[7] / (double) totalMethodCount) * 100 + " %");
        out.println("  100-149\t\t" + instrDist[8] + "\t\t" + (instrDist[8] / (double) totalMethodCount) * 100 + " %");
        out.println("  150-199\t\t" + instrDist[9] + "\t\t" + (instrDist[9] / (double) totalMethodCount) * 100 + " %");
        out.println("  200-349\t\t" + instrDist[10] + "\t\t" + (instrDist[10] / (double) totalMethodCount) * 100 + " %");
        out.println("  350-499\t\t" + instrDist[11] + "\t\t" + (instrDist[11] / (double) totalMethodCount) * 100 + " %");
        out.println("  500+\t\t\t" + instrDist[12] + "\t\t" + (instrDist[12] / (double) totalMethodCount) * 100 + " %");
        out.println();
        out.println("  # of 'real' blocks\t# of methods\tProportion out of all methods");
        out.println("  0-1\t\t\t" + blockDist[0] + "\t\t" + (blockDist[0] / (double) totalMethodCount) * 100 + " %");
        out.println("  2\t\t\t" + blockDist[1] + "\t\t" + (blockDist[1] / (double) totalMethodCount) * 100 + " %");
        out.println("  3\t\t\t" + blockDist[2] + "\t\t" + (blockDist[2] / (double) totalMethodCount) * 100 + " %");
        out.println("  4-9\t\t\t" + blockDist[3] + "\t\t" + (blockDist[3] / (double) totalMethodCount) * 100 + " %");
        out.println("  10-19\t\t\t" + blockDist[4] + "\t\t" + (blockDist[4] / (double) totalMethodCount) * 100 + " %");
        out.println("  20-29\t\t\t" + blockDist[5] + "\t\t" + (blockDist[5] / (double) totalMethodCount) * 100 + " %");
        out.println("  30-39\t\t\t" + blockDist[6] + "\t\t" + (blockDist[6] / (double) totalMethodCount) * 100 + " %");
        out.println("  40-49\t\t\t" + blockDist[7] + "\t\t" + (blockDist[7] / (double) totalMethodCount) * 100 + " %");
        out.println("  50-59\t\t\t" + blockDist[8] + "\t\t" + (blockDist[8] / (double) totalMethodCount) * 100 + " %");
        out.println("  60-69\t\t\t" + blockDist[9] + "\t\t" + (blockDist[9] / (double) totalMethodCount) * 100 + " %");
        out.println("  70-79\t\t\t" + blockDist[10] + "\t\t" + (blockDist[10] / (double) totalMethodCount) * 100 + " %");
        out.println("  80-89\t\t\t" + blockDist[11] + "\t\t" + (blockDist[11] / (double) totalMethodCount) * 100 + " %");
        out.println("  90-99\t\t\t" + blockDist[12] + "\t\t" + (blockDist[12] / (double) totalMethodCount) * 100 + " %");
        out.println("  100+\t\t\t" + blockDist[13] + "\t\t" + (blockDist[13] / (double) totalMethodCount) * 100 + " %");
        out.println();
        out.flush();
    }
}
