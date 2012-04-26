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

package sofya.apps;

import static sofya.base.Utility.isParsableInt;
import gnu.trove.THashMap;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import sofya.apps.dejavu.BlockTestMapper;
import sofya.apps.dejavu.BytecodeNodeComparer;
import sofya.apps.dejavu.CFEdgeSelector;
import sofya.apps.dejavu.CFGLoader;
import sofya.apps.dejavu.ClassPair;
import sofya.apps.dejavu.GraphTraverser;
import sofya.apps.dejavu.InputParser;
import sofya.apps.dejavu.MethodPair;
import sofya.apps.dejavu.OutputAdapter;
import sofya.apps.dejavu.TestMapper;
import sofya.base.Utility.IntegerPtr;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.graphs.Edge;
import sofya.viewers.TestSelectionViewer;

/**
 * <p>DejaVu is a regression test selection tool for Java, implemented as
 * part of the Sofya toolset.</p>
 *
 * <p>Usage:
 * <code>java sofya.apps.DejaVu &lt;prog1&gt; &lt;prog2&gt; &lt;thist&gt;
 * -t [slt] -tag1 &lt;tag1&gt; -tag2 &lt;tag2&gt;<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;prog1&gt;: prog file for version one
 * of the program<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;prog2&gt;: prog file for version two
 * of the program<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;thist&gt;: full path to test
 * history file.<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-t &lt;[s]tatistical | [l]ist | [t]abular&gt;:
 * output format<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-tag1: Database tag for version one of
 * the program<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-tag2: Database tag for version two of
 * the program<br></code>
 * </p>
 *
 * <p>The first three parameters <strong>must</strong> be in the order shown.
 * The order of the remaining parameters is not important.</p>
 *
 * @author Rogan Creswick
 * @author Alex Kinneer
 * @version 08/02/2007
 */
@SuppressWarnings("unchecked")
public class DejaVu {
    /**
     *  Parser object to validate the inputs on a syntactic level, and
     *  match classes and methods.
     */
    private InputParser parser;

    /**
     * Graph traverser which is responsible for walking the two graphs
     * and building the dangerous edge list.
     */
    private GraphTraverser traverser;

    /**
     * Mapper which uses the list of dangerous edges built by the
     * graph traverser and selects the corresponding tests.
     */
    private TestMapper testMapper;

    /**
     * Printer which displays the output and stores a canonical form of the
     * output in &lt;galileodb&gt;/&lt;tag2&gt;/output-tag1-tag2.dejavu
     *
     * @see sofya.viewers.TestSelectionViewer
     */
    private OutputAdapter outputAdapter;


    private boolean saveDangerousEdges = false;

    private final Map<Object, Object> storedEdges;

    private Map<Object, Object> labelCache = new THashMap();

    /**
     * Standard constructor for DejaVu.
     */
    public DejaVu() {
        storedEdges = null;
    }

    /**
     * Constructs an instance of DejaVu, specifying whether it should generate
     * an output file containing the dangerous edges selected by the
     * graph traverser.
     *
     * @param saveDangerousEdges Flag specifying whether the dangerous edges
     * should be saved to an output file.
     */
    @SuppressWarnings("unchecked")
    public DejaVu(boolean saveDangerousEdges) {
        this.saveDangerousEdges = saveDangerousEdges;

        if (saveDangerousEdges) {
            storedEdges = new THashMap();
        }
        else {
            storedEdges = null;
        }
    }

    /**
     * Selects the tests which traverse changes between the two versions
     * of the subject program. This method encapsulates the core
     * functionality of DejaVu.
     *
     * <p>The following preconditions must be true:
     * <ol>
     * <li>Control flow and map files must exist for all classes
     * specified in the prog files.</li>
     * <li>The path provided on the first line of each prog file
     * must specify a valid location to the base of the subject
     * program's source tree (it must be equivalent to the
     * classpath that would be used to run the program). Package
     * names are used to complete paths to actual class files
     * named in the prog file.</li>
     * <li>A test history file must exist for the subject,
     * constructed from traced execution of the first version of
     * the program.</li>
     * </ol>
     * </p>
     *
     * @param file1 Name of the prog file for the first version of the program
     * @param file2 Name of the prog file for the second version of the program
     * @param thist Path to the test history file, which may be either absolute
     * or relative to current directory.
     * @param oldTag Database tag for the first version of the program.
     * @param newTag Database tag for the second version of the program.
     *
     * @throws FileNotFoundException If an input file cannot be found.
     * @throws BadFileFormatException If an input file is corrupted.
     * @throws MethodNotFoundException If some input files reference different
     * classes than others, or are otherwise inconsistent in some manner that
     * prevents method data from being properly correlated.
     * @throws IOException For any other type of IO error that prevents
     * successful reading of any input file.
     */
    public void selectTests(String file1, String file2,  String thist,
                            String oldTag, String newTag)
                throws FileNotFoundException, BadFileFormatException,
                       MethodNotFoundException, IOException {
        try {
            // The fifth argument specifies the class which is to be used for
            // loading graphs, which must implement the GraphLoader interface.
            // Eventually this argument could be linked to a command line
            // parameter to control what types of graphs DejaVu operates on.
            // For now it is hardcoded to work with CFGs.
            parser = new InputParser(file1, file2, oldTag, newTag,
                                     CFGLoader.class);
        }
        catch (BadFileFormatException e) {
            e.printStackTrace();
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }


        // Instantiate the graph traverser providing the node comparer to be
        // used, which must be an instance of a class which implements the
        // NodeComparer interface. The comparer implementation **must** be
        // consistent with the class of node returned by the graphs obtained
        //through the graph loader class provided to the InputParser (above),
        // or the traverser will almost certainly suffer a horrible death.
        // e.g. If the graph loader yields graphs which produce nodes
        // encapsulating information about bytecodes (such as galileo.cfg.Block
        // objects), the comparer must provide an implementation that
        // understands how to compare such nodes. Again, the comparer
        // here may be linked to a command line argument in the future to
        // provide user-controlled traversal of different types of graphs.
        traverser = new GraphTraverser(
            new BytecodeNodeComparer(),
            new CFEdgeSelector()
          );

        // In the future other test mappers may be plugged in here
        // to enable different edge-to-test mapping criteria
        testMapper = new BlockTestMapper(thist);

        outputAdapter = new OutputAdapter(
            constructOutputFileName(oldTag, newTag),
            newTag,
            testMapper.getTotalNumberOfTests()
          );

        ClassPair[] classList = parser.getClassPairs();

        for (int i = 0; i < classList.length; i++) {
            ClassPair clazz = classList[i];
            MethodPair[] methodPairs = null;

            try {
                methodPairs = parser.getMethods(clazz);
            }
            catch (FileNotFoundException e) {
                System.err.println("WARNING: Database files not found for " +
                    clazz.name);
                continue;
            }

            for (int j = 0; j < methodPairs.length; j++) {
                MethodPair mp = methodPairs[j];
                System.out.println(mp.name);
                Edge[] dangerousEdges = traverser.getDangerousEdges(mp);
                for(int length=0;length<dangerousEdges.length;length++){
                	//System.out.println(mp.name);
                	//System.out.println("Dangerous Edges:");
                	//System.out.println("ID:"+dangerousEdges[length].getID()+";Label:"+dangerousEdges[length].getLabel()+";PredNodeID:"+dangerousEdges[length].getPredNodeID()+";SuccNode:"+dangerousEdges[length].getSuccNodeID());
                }

                if (saveDangerousEdges && (dangerousEdges.length > 0)) {
                    storedEdges.put(mp.name, dangerousEdges);
                    //System.out.println("Dangerous edges:"+dangerousEdges.);
                    
                }

                outputAdapter.addSelected(mp.name,
                    testMapper.selectTests(mp.name, dangerousEdges));
            }
        }
    }

    /**
     * Stores the selected test information in the database directory as
     * '<i>/&lt;tag2&gt;/output-&lt;tag1&gt;-&lt;tag2&gt;.dejavu</i>', and
     * displays the results on screen in the specified format.
     *
     * <p><strong>Note:</strong> You must first call
     * {@link DejaVu#selectTests}.</p>
     *
     * @param format Specifies the format of the output to be displayed
     * to the console. The following values are acceptable:
     * <ul>
     * <li>{@link sofya.viewers.TestSelectionViewer#STATISTICAL}</li>
     * <li>{@link sofya.viewers.TestSelectionViewer#TABULAR}</li>
     * <li>{@link sofya.viewers.TestSelectionViewer#LIST}</li>
     * </ul>
     *
     * database file is an empty snew HashMap()tring.
     * @throws IOException For any IO error that prevents successful
     * writing of the database file or display of the test selection
     * results.
     */
    public void print(int format) throws IOException {
        try {
            outputAdapter.writeTestSelectionFile();
        }
        finally {
            outputAdapter.print(format);
        }
    }

    /**
     * Generates a filename for the print method.
     *
     * @param tagInfoOrg The tag for the original program.
     * @param tagInfoMod The tag for the modified program.
     */
    private String constructOutputFileName(String tagInfoOrig,
                                           String tagInfoMod) {
        return "output-" + tagInfoOrig + "-" + tagInfoMod + ".dejavu";
    }

    @SuppressWarnings("unused")
    private void saveEdgeInfo(String oldTag, String newTag) throws IOException {
        PrintStream deFile = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(
                constructOutputFileName(oldTag, newTag) + ".de")));
        int totalCount = 0;
        int totalExcCount = 0;

        try {
            int size = storedEdges.size();
            Iterator iterator = storedEdges.keySet().iterator();
            for (int i = size; i-- > 0; ) {
                String methodName = (String) iterator.next();
                Edge[] des = (Edge[]) storedEdges.get(methodName);

                totalCount += des.length;
                totalExcCount += writeDangerousEdges(deFile, methodName, des);
            }

            deFile.println();
            deFile.println("Total dangerous edges: " + totalCount);
            deFile.println("Total dangerous exceptional edges: " + totalExcCount);
        }
        finally {
            deFile.close();
        }
    }

    private int writeDangerousEdges(PrintStream out, String name, Edge[] es)
            throws IOException {
        int excCount = 0;

        out.println();
        out.println(name);
        out.println("\tEdge count: " + es.length);
        for (int i = 0; i < es.length; i++) {
            out.println("\t  " + es[i].getID() + ": " + es[i]);

            String label = es[i].getLabel();
            Class eClass = (Class) labelCache.get(label);

            if (eClass == null) {
                // Ignore known standard labels
                if ((label == null) || label.equals("Default")
                        ||label.equals("T") || label.equals("F")
                        || label.equals("<r>") || label.equals("jsr")) {
                    continue;
                }

                if (label.equals("<any>")) {
                    excCount += 1;
                    continue;
                }

                // Screen out numeric switch labels
                if (isParsableInt(label, 10, new IntegerPtr())) {
                    continue;
                }

                try {
                    eClass = Class.forName(label);
                    labelCache.put(label, eClass);
                }
                catch (ClassNotFoundException e) {
                    System.err.println("WARNING: Could not load class for " +
                        "label " + label);
                    continue;
                }
            }

            if (Throwable.class.isAssignableFrom(eClass)) {
                excCount += 1;
            }
        }

        return excCount;
    }

    /**
     *  Prints the usage message for DejaVu and exits.
     */
    public static void printUsage() {
        System.out.println("Usage:");
        System.out.println("------------------------------------");
        System.out.println("  java sofya.tools.DejaVu <prog1> <prog2> " +
            "<thist> -t [slt] -tag1 <tag1> -tag2 <tag2>");
        System.out.println();
        System.out.println("<prog1> and <prog2> are .prog files.");
        System.out.println("(These paths are relative to their tag " +
            "directories.)");
        System.out.println();
        System.out.println("<thist> is the full path to the test " +
            "history file.");
        System.out.println();
        System.out.println("Where -t controls the output format: " +
            "[s]tatistical, [l]ist, or [t]abular");
        System.out.println("and -tag1 and -tag2 specify the database " +
            "tags for prog1 and prog2, respectively.");
        System.out.println();
        System.exit(1);
    }

    /**
     * Entry point for DejaVu.
     *
     * @param argv Command-line arguments.
     */
    public static void main(String[] argv) {
        String prog1="", prog2="", thist="", tag1="", tag2="";
        int format = TestSelectionViewer.LIST;

        // Allocate some booleans to confirm the params were provided.
        boolean t1  = false;
        boolean t2  = false;
        boolean fmt = false;

        // Do a quick check for argument count.
        if (argv.length != 9) {
            System.err.println("Error: Invalid number of parameters.");
            printUsage();
        }

        // Get the filenames
        prog1 = argv[0];
        prog2 = argv[1];
        thist = argv[2];

        // Get the flagged params:
        for (int i = 3; i < 8; i++) {
            if (argv[i].equals("-t")) {
                if (argv[i+1].equals("s")) {
                    format = TestSelectionViewer.STATISTICAL;
                }
                else if (argv[i+1].equals("t")) {
                    format = TestSelectionViewer.TABULAR;
                }
                else if (argv[i+1].equals("l")) {
                    format = TestSelectionViewer.LIST;
                }
                else {
                    System.err.println("Error: Invalid display type specified");
                    printUsage();
                    System.exit(1);
                }
                fmt = true;
            }
            else if (argv[i].equals("-tag1")) {
                tag1 = argv[i + 1];
                t1 = true;
            }
            else if (argv[i].equals("-tag2")) {
                tag2 = argv[i + 1];
                t2 = true;
            }
        }

        // Confirm we got all the params.
        if (!t1 && t2 && fmt) {
            System.err.println("Error: Required parameters missing: t1=" +
                t1 + " t2=" +t2 + " fmt=" + fmt);
            printUsage();
        }

        DejaVu dvu = new DejaVu();

        try {
            long startTime = System.currentTimeMillis();
            dvu.selectTests(prog1, prog2, thist, tag1, tag2);
            long endTime = System.currentTimeMillis();
            dvu.print(format);
            

            //System.out.println("Generating dangerous edge information");
            //dvu.saveEdgeInfo(tag1, tag2);

            System.out.println("Time elapsed (ms): " + (endTime - startTime));
        }
        catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        catch (BadFileFormatException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /*private static void writeClassName(PrintStream out, String name)
            throws IOException {
        out.println();
        out.println();
        out.println("========================================" +
            "========================================");
        out.println(" " + name);
        out.println("========================================" +
            "========================================");
    }*/
}
