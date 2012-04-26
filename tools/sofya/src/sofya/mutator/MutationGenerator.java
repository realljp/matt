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

package sofya.mutator;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.lang.reflect.Constructor;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.*;

import org.apache.bcel.Repository;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

/**
 * Class that generates a mutation table (&lt;class&gt;.mut) that lists  possible
 * mutants for each input class file. 
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 06/09/2006
 */
public class MutationGenerator {
    /** Current configuration. */
    private MutatorConfiguration mc;
    /** Enabled operators with configurations. */
    private MutationOperator[] operators;

    /** Currently loaded class. */
    private JavaClass javaClass = null;
    /** BCEL access to currently loaded class structure and bytecodes. */
    private ClassGen classFile = null;

    /** Fully qualified name of the currently loaded class. */
    private String fullClassName;

    /** The mutation table generator instance used by the front-end. */
    private static MutationGenerator mGen;

    /** Database tag specified by user. */
    private static String tag = null;

    private MutationGenerator() {
    }

    /**
     * Creates a new mutation table generator.
     *
     * @param mc Configuration for the new mutation table generator.
     */
    public MutationGenerator(MutatorConfiguration mc) {
        this.mc = mc;
    }

    /**
     * Initializes the mutation table generator.
     *
     * <p>This causes all of the mutation operators to be loaded and
     * configured.</p>
     *
     * @throws MutationException If a mutation operator implementation does
     * not provide the necessary support for proper loading.
     */
    public void initialize() throws MutationException {
        OperatorConfiguration[] opConfigs = mc.getOperators();
        List<Object> loadedOps = new ArrayList<Object>();

        // For now, we just instantiate the operators; settings
        // and properties are ignored.
        for (int i = 0; i < opConfigs.length; i++) {
            String opImplName = opConfigs[i].getName();

            Class opClass = null;
            try {
                opClass = Class.forName(opImplName);
            }
            catch (ClassNotFoundException e) {
                System.err.println("WARNING: Could not load operator: " +
                    opImplName + ",\n         implementing class could " +
                    "not be found");
                continue;
                //throw new MutationException("Could not load operator: " +
                //    opImplName, e);
            }

            Constructor c = null;
            try {
                c = opClass.getDeclaredConstructor(new Class[]{});
                c.setAccessible(true);
            }
            catch (NoSuchMethodException e) {
                throw new MutationException("Operator must implement " +
                    "no-argument constructor (" + opImplName + ")");
            }
            catch (SecurityException e) {
                throw new MutationException("Could not access operator " +
                    " constructor (" + opImplName + ")", e);
            }

            MutationOperator operator = null;
            try {
                operator = (MutationOperator) c.newInstance(new Object[]{});
            }
            catch (Exception e) {
                throw new MutationException("Operator instantiation " +
                    "failed (" + opImplName + ")", e);
            }
            loadedOps.add(operator);
        }

        operators = (MutationOperator[]) loadedOps.toArray(
            new MutationOperator[loadedOps.size()]);
    }

    /**
     * Gets the fully qualified name of the currently loaded class.
     *
     * @return The fully qualified name of the currently loaded class.
     */
    public String getClassName() {
        return fullClassName;
    }

    /**
     * Loads a new class.
     *
     * @param className Name of the class to be loaded.
     */
    public void loadClass(String className)
                throws BadFileFormatException, FileNotFoundException,
                       IOException, ClassFormatError, Exception {
        parseClass(className, null);
    }

    /**
     * Loads a new class from a given input stream.
     *
     * @param className Name of the class to be loaded.
     * @param source Stream from which the class is to be loaded.
     */
    public void loadClass(String className, InputStream source)
                throws BadFileFormatException, IOException,
                       ClassFormatError, Exception {
        if (source == null) {
            throw new NullPointerException();
        }
        parseClass(className, source);
    }

    /**
     * Parses a class file to initialize the data structures used by the
     * mutation generator.
     *
     * @param className Name of the class to be parsed.
     * @param source Stream from which the class should be parsed. Permitted
     * to be <code>null</code> in which case this method will attempt to
     * load the class from the classpath or directly as an absolute path.
     *
     * @throws BadFileFormatException If the class to be loaded is an
     * interface.
     * @throws IOException If the class cannot be found or for any other
     * I/O error that prevents successful parsing of the class.
     * @throws ClassFormatError If the class is not a valid class file.
     */
    protected void parseClass(String className, InputStream source)
                   throws BadFileFormatException, IOException,
                          ClassFormatError {
        if (source != null) {
            javaClass = new ClassParser(source, className).parse();
        }
        else {
            javaClass = Handler.parseClass(className);
        }

        if (javaClass.isInterface()) {
            throw new BadFileFormatException("Cannot instrument an interface");
        }

        Repository.addClass(javaClass);
        this.fullClassName = javaClass.getClassName();
        classFile = new ClassGen(javaClass);
     }

    /**
     * Generates mutations for the loaded class using the mutation operators
     * enabled in the mutator configuration.
     *
     * @param table Mutation table to record the generated mutations.
     */
    public void generateMutations(MutationTable table) {
        for (int i = 0; i < operators.length; i++) {
            operators[i].generateMutants(table, classFile);
        }
    }

    /**
     * Generates mutations for the loaded class using the mutation operators
     * enabled in the mutator configuration.
     *
     * @return A new mutation table containing the generated mutations.
     */
    public MutationTable generateMutations() {
        MutationTable mt = new StandardMutationTable();
        generateMutations(mt);
        return mt;
    }

    /**
     * Prints the usage message and exits.
     */
    private static void printUsage() {
        System.err.println("Usage:\n java sofya.mutator.MutationGenerator " +
            "[-tag db_tag] <-c config_file>\n    <class|jarfile|listfile> " +
            "[class|jarfile|listfile ...]"); 
        System.exit(1);
    }

    /**
     * Entry point for the mutation generator.
     */
    public static void main(String[] argv) {
        if (argv.length < 3) {
            printUsage();
        }

        String configFile = null;

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
            else if (argv[index].equals("-c")) {
                if (index + 1 < argv.length) {
                    configFile = argv[++index];
                }
                else {
                    System.err.println("Configuration file not specified");
                    printUsage();
                }
            }
            else if (!argv[index].startsWith("-")) {
                break;
            }
            else {
                System.err.println("Unrecognized parameter: " + argv[index]);
                printUsage();
            }
        }

        if (configFile == null) {
            System.err.println("Configuration file not specified");
            printUsage();
        }

        MutatorConfiguration config = null;
        try {
            config = MutationHandler.readConfiguration(configFile);
        }
        catch (BadFileFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
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

        mGen = new MutationGenerator(config);
        try {
            mGen.initialize();
        }
        catch (MutationException e) {
            System.err.println(e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.exit(1);
        }

        int inputCount = inputList.size();
        Iterator iterator = inputList.iterator();
        for (int i = inputCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) iterator.next();

            try {
                if (pUnit.isJar) {
                    generateForJar(pUnit);
                }
                else {
                    generateForClasses(pUnit);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private static void generateForClass()
            throws Exception {
        //MutationTable mt = tGen.generateMutants();
        //mh.writeMutationTable(tGen.getClassName() + ".mut", mt);

        FileWriterMutationTable fwmt =
            MutationHandler.writeMutationFile(mGen.getClassName() + ".mut");
        try {
            mGen.generateMutations(fwmt);
        }
        finally {
            fwmt.close();
        }
    }

    private static void generateForClasses(ProgramUnit pUnit) throws Exception {
        int clCount = pUnit.classes.size();
        Iterator iterator = pUnit.classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String entry = (String) iterator.next();
            try {
                if (pUnit.useLocation) {
                    mGen.loadClass(pUnit.location +
                        entry.replace('.', File.separatorChar) + ".class");
                }
                else {
                    mGen.loadClass(entry);
                }
            }
            catch (BadFileFormatException e) {
                System.err.println(e.getMessage());
                continue;
            }
            catch (EmptyFileException e) {
                System.err.println("WARNING: " + e.getMessage());
                continue;
            }
            catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                continue;
            }

            generateForClass();
        }
    }

    private static void generateForJar(ProgramUnit jarUnit) throws Exception {
        JarFile sourceJar = new JarFile(jarUnit.location);

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                if (ze.isDirectory() || !ze.getName().endsWith(".class")) {
                    continue;
                }

                entryStream = new BufferedInputStream(
                    sourceJar.getInputStream(ze));
                try {
                    mGen.loadClass(ze.getName(), entryStream);
                }
                catch (BadFileFormatException exc) {
                    System.err.println(exc.getMessage());
                }
                catch (FileNotFoundException exc) {
                    System.err.println("WARNING: " + exc.getMessage());
                }

                generateForClass();
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
