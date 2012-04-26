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

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.ProtectedJarOutputStream;
import sofya.base.exceptions.*;
import sofya.graphs.irg.*;
import sofya.mutator.selectors.*;
import sofya.mutator.verifier.*;
import sofya.mutator.Mutation.Variant;

import org.apache.bcel.Repository;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import gnu.trove.THashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.THashMap;

/**
 * This class generates mutants from a mutation table, with various selection
 * criteria.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 10/24/2006
 */
public class Mutator {
    /** Reference to a mutant generator used by the front-end. */
    private static Mutator mutator;
    /** Extension to be attached to the name of the mutated class or jar
        file, if specified. */
    private static String mutSuffix;
    
    /** Mutation selector used to implement the mutation selection
        policy specified by the user. */
    private MutationSelector selector;
    /** Verifier to be used to validate mutations, if enabled. */
    private Verifier verifier;
    /** Class relation graph used by the verifier to provide transitiverequestedVariant
        verification, if applicable. */
    private IRG classGraph;

    /** Class with mutations applied or currently being mutated. */
    private ClassGen classFile;
    /** Constant pool for the currently loaded class. */
    private ConstantPoolGen cpg;
    /** Method currently loaded for mutation. */
    private Method method;
    /** Method with mutations applied or currently being mutated. */
    private MethodGen mg;
    /** Instruction list currently loaded for mutation. */
    private InstructionList il;
    /** Fully qualified name of the class loaded for mutation. */
    private String fullClassName;

    /** Flag specifying whether the mutator should generator verbose output. */
    private boolean verbose = false;

    /** Transitive verification ignores classes with these qualifiers. */
    @SuppressWarnings("unused")
    private static final String[] VERIFY_EXCLUDE = new String[] {
        "java", "javax", "org.ietf.jgss", "org.omg", "org.w3c.dom",
        "org.xml.sax" };

    /**
     * Static value of requested mutation class name (from cmd line value)
     */
    public static String requestedClassName;
    
    /**
     * Visitor that actually applies the mutations to a class, filtered
     * by a given selection policy.
     */
    @SuppressWarnings("unchecked")
    private class MutationAgent implements MutationVisitor {
        /** Selector providing the mutation selection policy. */
        private MutationSelector selector;
        /** Mutation table to record the mutations actually applied. */
        private MutationTable table;

        /** Records the mutation group currently being visited,
            <code>null</code> otherwise. */
        private MutationGroup currentGroup;
        /** Mutation group that contains the mutations actually visited
            in the mutation group that was just visited, <code>null</code>
            otherwise. */
        private MutationGroup visitedGroup;

        /** Map to support passing of data between groupable mutations. */
        private Map<Object, Object> linkData = new THashMap();

        private boolean verify = false;

        {
            verify = (verifier != null);
        }

        private MutationAgent() {
        }

        /**
         * Creates a new mutation agent to apply mutations.
         *
         * @param selector Selector providing the mutation selection policy.
         * @param table Mutation table that will record the mutations actually
         * applied by this agent.
         */
        public MutationAgent(MutationSelector selector, MutationTable table) {
            this.selector = selector;
            this.table = table;
        }

        public void visit(Mutation m) throws MutationException {
            if (!selector.isSelected(m)) return;

            if(!(Mutator.requestedClassName.equals(classFile.getClassName()))) {
            	System.out.println("mutator requested class != current classFile");
            }
            Variant variant = selector.getVariant(m);
            m.apply(classFile, variant);

            if (verify && !verifyMutant(m)) {
                m.undo(classFile);
                return;
            }

            table.addMutation(m);

            if (verbose) {
                System.out.println("Mutation #" + m.getID().asInt() + ", " +
                    m.getType() + ", applied to classfile " + classFile.getClassName());
            }
        }

        public void visit(MutationGroup m, boolean begin)
                throws MutationException {
           		boolean found_mut = false;
            	Iterator mutiter = m.iterator();
            	Mutation submut = null;
            	// determine if the requested mutation is
            	// within this mutation group.  The selector
            	// object contains the requested mutation ID
            	// in the context of the class being mutated
            	// in this mutation group
            	while(mutiter.hasNext()) {
            		submut = (Mutation) mutiter.next();
            		if(!selector.isSelected(submut))
            			continue;
            		else {
            			found_mut = true;
            			break;
            		}
            	}
            	if (begin) {
            		// if this isn't the selected mutation nor
            		// is the selected mutation within the parent
            		// mutation group, don't visit members of this mutation
	                if (!selector.isSelected(m) && !(found_mut)) {
	                	m.visitMembers(false);
	                }
	                else {
		            	if (verbose) {
		                    System.out.println("Processing group #" +
		                        m.getID().asInt() + " on classfile " + classFile.getClassName());
		                }
		            	// this will warn if the class being visited is
		            	// not the originally requested class of the user
		            	// this would be an error condition but is only warned
		            	// about here.  If you want this to cause an error add
		            	// a throw of a new MutationException() instead of
		            	// the System.out.println() invocation.
		                if(!(Mutator.requestedClassName.equals(classFile.getClassName()))) {
		                	System.out.println("mutator requested class "+requestedClassName+" != "+classFile.getClassName());
		                }
		                m.visitMembers(true);
		                // compute the absolute variant ID requested in the group
		                // the selector contains the sub-variant number if the N:M
		                // format is used for the -ids cmd line flag
		                m.setRequestedVariant(m.getID().asInt() + selector.getRequestedVariant(m));
		                String className = m.getClassName();
		                String methodName = m.getMethodName();
		                String signature = m.getSignature();
		
		                if (!className.equals(fullClassName)) {
		                    throw new MutationException("Mutation group expects " +
		                        "unloaded class: " + className);
		                }
		                if (!loadMethod(methodName, signature)) {
		                    throw new MutationException("Mutation group method '" +
		                        methodName + signature + "' could not be loaded");
		                }
		
		                currentGroup = m;
		                visitedGroup = new MutationGroup(className, methodName,
		                    signature);
	                }
                }
            	else {
	                if (visitedGroup.size() > 0) {
	                    commitMethod(true);
	                }

	                table.addMutation(visitedGroup);
	                currentGroup = null;
	                visitedGroup = null;
	
	                if (verbose) {
	                    System.out.println("Finished processing group #" +
	                        m.getID().asInt() + " on classfile " + classFile.getClassName());
	                }
            	}
        }

        public void visit(GroupableMutation m) throws MutationException {
        	// verify if this groupable mutation is the selected mutation
        	// or the parent mutation group is the selected mutation
        	// to apply.  If neither this nor the parent are the
        	// selected mutation just return
        	if (!selector.isSelected(m)) {
        		if(!selector.isSelected(m.getParent()))
        		return;
        	}
        	// logic check if this is the right class to mutate
        	// change the println() to a throw new MutationException
        	// if you want this to be more than a warning message
            if(!(Mutator.requestedClassName.equals(classFile.getClassName()))) {
            	System.out.println("mutator requested class != current classFile");
            }
            // find the specified variant in the group
            // if this mutation isn't the selected one, get
            // the selected one from the parent of this
            // mutation (the parent will be a mutation group)
            Variant variant = null;
            if(!selector.isSelected(m))
            	variant = selector.getVariant(m.getParent());
            else
            	variant = selector.getVariant(m);

            MutationGroup parent = m.getParent();
            if (parent == currentGroup) {
                visitedGroup.addMutation(m);
                m.apply(classFile, mg, il, linkData, variant);

                if (verify) {
                    commitMethod(false);
                    if (!verifyMutant(m)) {
                        m.undo(classFile, mg, il, linkData);
                        return;
                    }
                }

            }
            else if (parent == null) {
                m.apply(classFile, variant);

                if (verify && !verifyMutant(m)) {
                    m.undo(classFile);
                    return;
                }

                table.addMutation(m);
            }
            else {
                throw new SofyaError("GroupableMutation visited prior " +
                    "to parent");
            }

            if (verbose) {
                System.out.println("Mutation #" + m.getID().asInt() + ", " +
                    m.getType() + ", applied to classfile " + classFile.getClassName());
            }
        }

        public void visit(ClassMutation m) throws MutationException {
        	//System.out.println("DEBUG: visit(classMut) Mutator.fullClassName: " + Mutator.requestedClassName);
            if (!selector.isSelected(m)) return;

            Variant variant = selector.getVariant(m);
            if(!classFile.getClassName().equals(Mutator.requestedClassName))
            	System.out.println("visit: asking to mutate "+
            		classFile.getFileName() + " but original request was for " + Mutator.requestedClassName);
            m.apply(classFile, variant);

            if (verify && !verifyMutant(m)) {
                m.undo(classFile);
                return;
            }

            table.addMutation(m);

            if (verbose) {
                System.out.println("Mutation #" + m.getID().asInt() + ", " +
                    m.getType() + ", applied to classfile " + classFile.getClassName());
            }
        }

        public void visit(MethodMutation m) throws MutationException {
        	//System.out.println("DEBUG: visit(methodMut) Mutator.fullClassName: " + Mutator.requestedClassName);
            if (!selector.isSelected(m)) return;

            Variant variant = selector.getVariant(m);
            m.apply(classFile, variant);

            if (verify && !verifyMutant(m)) {
                m.undo(classFile);
                return;
            }

            table.addMutation(m);

            if (verbose) {
                System.out.println("Mutation #" + m.getID().asInt() + ", " +
                    m.getType() + ", applied to classfile " + classFile.getClassName());
            }
        }
    }

    /**
     * Creates a new mutator.
     */
    public Mutator() {
        this.selector = new DefaultMutationSelector();
    }

    /**
     * Creates a new mutator.
     *
     * @param selector Selector providing the mutation selection policy.
     */
    public Mutator(MutationSelector selector) {
        this.selector = selector;
    }

    /**
     * Creates a new mutator.
     *
     * @param selector Selector providing the mutation selection policy.
     * @param verifier Verifier to be used for verifying mutants.
     */
    public Mutator(MutationSelector selector, Verifier verifier,
            IRG classGraph) {
        this(selector);
        this.verifier = verifier;
        this.classGraph = classGraph;
    }

    /**
     * Gets the fully qualified name of the currently loaded class.
     *
     * @return The fully qualified name of the loaded class.
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
        initClass(parseClass(className, null));
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
        initClass(parseClass(className, source));
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
     * @throws IOException If the class cannot be found or for any other
     * I/O error that prevents successful parsing of the class.
     * @throws ClassFormatError If the class is not a valid class file.
     */
    protected JavaClass parseClass(String className, InputStream source)
                   throws IOException, ClassFormatError {
        JavaClass clazz;
        if (source != null) {
            clazz = new ClassParser(source, className).parse();
        }
        else {
            clazz = Handler.parseClass(className);
        }

        return clazz;
    }

     /**
     * @throws BadFileFormatException If the class to be loaded is an
     * interface.
     */
    protected void initClass(JavaClass clazz) throws BadFileFormatException {
        if (clazz.isInterface()) {
            throw new BadFileFormatException("Cannot mutate an interface");
        }
        JavaClass javaClass = clazz;
        Repository.addClass(clazz);
         this.fullClassName = javaClass.getClassName();
        classFile = new ClassGen(clazz);
        this.cpg = classFile.getConstantPool();
    }

    /**
     * Loads a method for mutation.
     *
     * <p>Used for groupable mutations.</p>
     *
     * @param name Name of the method to be loaded.
     * @param signature Signature of the method to be loaded.
     *
     * @return <code>true</code> if the method was successfully loaded.
     */
    private boolean loadMethod(String name, String signature) {
        this.method = classFile.containsMethod(name, signature);
        if (method == null) return false;

        this.mg = new MethodGen(method, fullClassName, cpg);
        this.il = mg.getInstructionList();
        return true;
    }

    /**
     * Commits a mutated method to the class.
     *
     * @param dispose Specifies whether the instruction list should be
     * disposed after the method is committed. This is normally only
     * <code>false</code> when the method is being committed for
     * verification.
     *
     * @throws MutationException If the method cannot be found in the
     * class for replacement.
     */
    private void commitMethod(boolean dispose) throws MutationException {
        il.setPositions();
        mg.setInstructionList(il);

        // Find the position of the method
        Method[] methods = classFile.getMethods();
        int i = 0;
        for ( ; i < methods.length; i++) {
            if (methods[i] == method) {
                break;
            }
        }

        if (i == methods.length) {
            throw new MutationException("Could not find position " +
                "of method in class");
        }

        // Commit the changed method
        method = mg.getMethod();
        classFile.setMethodAt(method, i);

        if (dispose) {
            il.dispose();
        }
    }

    /**
     * Writes the mutated class file to a stream. Typically the stream
     * should be pointed to a file.
     *
     * @param dest Stream to which the mutated class file should
     * be written.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public void writeClass(OutputStream dest) throws IOException {
        classFile.getJavaClass().dump(dest);
    }

    /**
     * Mutates the currently loaded class.
     *
     * @throws MutationException If the mutation table for the class cannot
     * be read, the mutation table to record applied mutatations cannot
     * be created, or if application of a mutation fails.
     */
    public void mutate() throws MutationException {
        MutationIterator mutants = null;
        try {
            mutants = MutationHandler.readMutationFile(fullClassName + ".mut");
        }
        catch (IOException e) {
            throw new MutationException("Could not read mutation table:\n" +
               "\t" + fullClassName, e);
        }

        FileWriterMutationTable fwmt = null;
        try {
            fwmt = MutationHandler.writeMutationFile(fullClassName + ".mut.apl");
        }
        catch (IOException e) {
            throw new MutationException("Could not create applied mutation " +
               "table:\n\t" + fullClassName, e);
        }

        MutationAgent agent = new MutationAgent(selector, fwmt);

        try {
            int count = mutants.count();
            selector.setMutationCount(count);
            for (int i = count; i-- > 0; ) {
                Mutation m = null;

                try {
                    m = mutants.next();
                }
                catch (NoSuchElementException e) {
                    throw new MutationException("Error reading mutation " +
                        "table", e);
                }
                //if(m instanceof MutationGroup) 
                m.accept(agent);
            }
        }
        finally {
            try {
                fwmt.close();
            }
            catch (IOException e) {
                // Failure to close the file may mean the file is unreadable,
                // see FileWriterMutationTable for details
                throw new MutationException("Failed to close applied " +
                    "mutation table:\n\t" + fullClassName, e);
            }
        }
    }

    /**
     * Checks that the class file resulting from application of a mutant still
     * passes verification.
     *
     * <p>Interaction with previously applied mutations may cause verification
     * to fail even if the currently applied mutation in isolation does not
     * create an invalid class file. A simple example is if a prior mutation
     * changed the access level of a field in another class such that it is
     * no longer visible to an attempted access from the current method
     * being mutated. For this reason, verification is disabled by default,
     * though it may still be useful in many circumstances.</p>
     *
     * @param m Mutation that was applied and needs to be verified.
     *
     * @return <code>true</code> if the class file passes verification.
     *
     * @throws MutationException If the method is unable to execute the
     * verifier for any reason.
     */
    private boolean verifyMutant(Mutation m) throws MutationException {
        try {
            VerificationResult result;

            verifier.loadClass(classFile.getJavaClass());

            if (m instanceof MethodMutation) {
                MethodMutation mm = (MethodMutation) m;

                result = verifier.verify(fullClassName, mm.getMethodName(),
                    mm.getSignature(), Verifier.Pass.THREE_B);
            }
            else {
                result = verifier.verify(fullClassName, Verifier.Pass.THREE_B);

                if (result.getResult() == 0) {
                    // Do transitive verification
                    IRG.ClassNode clData = null;
                    try {
                        clData = classGraph.getClassRelationData(fullClassName);
                    }
                    catch (ClassNotFoundException e) {
                        throw new MutationException("Could not verify " +
                            "mutant, class dependency data could not be " +
                            "found", e);
                    }

                    Iterator dependents = clData.userIterator();
                    while (dependents.hasNext()) {
                        String dependent = (String) dependents.next();
                        result = verifier.verify(dependent,
                            Verifier.Pass.THREE_B);
                        if (result.getResult() != 0) {
                            break;
                        }
                    }
                }
            }

            if (result.getResult() != 0) {
                System.err.println("Mutation #" + m.getID().asInt() +
                    ", " + m.getType() + ", failed verification");
                //System.err.println("    " + result.getMessage());
                return false;
            }
            return true;
        }
        catch (VerifierException e) {
            throw new MutationException("Could not verify mutation", e);
        }
    }

    /**
     * Transitively checks that all the classes dependent on the mutated
     * class (identified by constant pool class references) pass verification.
     *
     * <p>The BCEL verifier in pass 3a rejects even code generated by the
     * Sun compiler, so this method only applies verification up through
     * pass 2. This won't find much, but it may be better than nothing.</p>
     *
     * @param clWorkList Work list of classes to be verified.
     *
     * @return <code>true</code> if all of the classes transitively
     * referenced by this class pass level 2 verification.
     *
     * @throws MutationException If the method is unable to execute the
     * verifier for any reason.
     */
    /*private boolean verifyTransitive(LinkedList clWorkList)
            throws MutationException {
        Set verified = new THashSet();
        JavaClass currentClass = null;

        while (clWorkList.size() > 0) {
            String className = (String) clWorkList.removeFirst();
            if (verified.contains(className)) {
                continue;
            }
            else {
                verified.add(className);
            }
            //System.out.println("Verifying " + className);

            try {
               currentClass = parseClass(className, null);
            }
            catch (IOException e) {
                System.err.println("WARNING: Could not verify class '" +
                    className + "'\n    " + e.getMessage());
                continue;
            }
            catch (ClassFormatError e) {
                System.err.println("WARNING: Could not verify class '" +
                    className + "'\n    " + e.getMessage());
                continue;
            }

            Verifier v = VerifierFactory.getVerifier(className);
            v.flush();

            VerificationResult result = v.doPass2();
            if (result.getStatus() != VerificationResult.VERIFIED_OK) {
                return false;
            }

//             int methodCount = currentClass.getMethods().length;
//             for (int i = 0; i < methodCount; i++) {
//                 VerificationResult result = v.doPass3b(i);
//                 if (result.getStatus() != VerificationResult.VERIFIED_OK) {
//                     System.err.println(result.getMessage());
//                     return false;
//                 }
//             }

            ConstantPool cp = currentClass.getConstantPool();
            Constant[] constants = cp.getConstantPool();
            for (int i = 0; i < constants.length; i++) {
                if ((constants[i] != null) &&
                        (constants[i].getTag() == CONSTANT_Class)) {
                    ConstantClass ccl = (ConstantClass) constants[i];
                    String refClass = ccl.getBytes(cp).replace('/', '.');

                    boolean exclude = false;
                    for (int j = 0; j < VERIFY_EXCLUDE.length; j++) {
                        if (refClass.startsWith(VERIFY_EXCLUDE[j])) {
                            exclude = true;
                        }
                    }

                    if (!exclude) {
                        clWorkList.add(refClass);
                    }
                }
            }
        }

        return true;
    }*/

    /**
     * Reads a file generated by the diffing tool listing methods in a format
     * derived from the source code.
     *
     * @param methodFileName Name of the method list file to be read.
     *
     * @return A list of the methods read from the file, as strings.
     *
     * @throws IOException If the specified file cannot be found or for any
     * I/O error that prevents the file from being successfully read.
     */
    private static List readMethodFile(File methodFileName)
            throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(methodFileName));
        List<Object> methodList = new ArrayList<Object>();

        try {
            String line;
            while ((line = br.readLine()) != null) {
                methodList.add(line);
            }
        }
        finally {
            try {
                br.close();
            }
            catch (IOException e) { }
        }

        return methodList;
    }

    private static void mutateClasses(ProgramUnit pUnit) throws Exception {
        int clCount = pUnit.classes.size();
        Iterator iterator = pUnit.classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String entry = (String) iterator.next();
            String entryPath = null;
            try {
                if (pUnit.useLocation) {
                    entryPath = pUnit.location + entry.replace(
                        '.', File.separatorChar) + ".class";
                    mutator.loadClass(entryPath);
                }
                else {
                    mutator.loadClass(entry);
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
            
            BufferedOutputStream fout = null;
            try {
                StringBuilder outFile = new StringBuilder();
                if (pUnit.useLocation) {
                    outFile.append(entryPath);
                }
                else {
                    outFile.append(mutator.getClassName() + ".class");
                }
                if (mutSuffix != null) {
                    outFile.append(mutSuffix);
                }
                fout = new BufferedOutputStream(new FileOutputStream(
                    outFile.toString()));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("Unable to create output file");
                System.exit(1);
            }

            try {
                mutateClass(fout);
            }
            finally {
                try {
                    fout.close();
                }
                catch (IOException e) {
                    System.err.println("WARNING: Failed to close file \"" +
                        mutator.getClassName() + "\"");
                }
            }
        }
    }

    private static void mutateJar(ProgramUnit jarUnit) throws Exception {
        JarFile sourceJar = new JarFile(jarUnit.location);

        ProtectedJarOutputStream mutantJar = null;
        File f;
        if (mutSuffix == null) {
            f = new File(jarUnit.location);
        }
        else {
            f = new File(jarUnit.location + mutSuffix);
        }
        
        try {
            mutantJar = new ProtectedJarOutputStream(
                        new BufferedOutputStream(
                        new FileOutputStream(f)));
        }
        catch (IOException e) {
            IOException ioe = new IOException("Could not create output jar " +
                "file");
            ioe.fillInStackTrace();
            throw ioe;
        }

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                boolean copyOnly = false;

                ZipEntry ze = (ZipEntry) e.nextElement();
                if (ze.isDirectory() || !ze.getName().endsWith(".class")) {
                    copyOnly = true;
                }
                /* 
                 * restrict mutation to specified classes in jarUnit
                 * which contains only the specified class name to mutate
                 * in the classes[] list.
                 */
                for (int i=jarUnit.classes.size()-1; i >= 0; i--) {
                	String cmpStr = ze.getName().replace("/", ".").replace(".class", "");
                	if(!cmpStr.equals(jarUnit.classes.get(i))) {
                		copyOnly = true;
                	}
                }
                if (!copyOnly) {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    try {
                        mutator.loadClass(ze.getName(), entryStream);
                    }
                    catch (BadFileFormatException exc) {
                        System.err.println(exc.getMessage());
                        copyOnly = true;
                    }
                    catch (FileNotFoundException exc) {
                        System.err.println("WARNING: " + exc.getMessage());
                        copyOnly = true;
                    }
                }

                mutantJar.putNextEntry(new JarEntry(ze.getName()));
                if (!copyOnly) {
                    mutateClass(mutantJar);
                }
                else {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    Handler.copyStream(entryStream, mutantJar, false, false);
                    entryStream.close();
                }
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
                mutantJar.closeStream();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }

        if (f.exists()) {
            if (!f.renameTo(new File(jarUnit.location))) {
                System.out.println("Instrumented jar file is named " +
                    f.getName());
            }
        }
    }

    private static void mutateClass(OutputStream fout) throws Exception {
        mutator.mutate();
        mutator.writeClass(fout);
    }

    /*private static void copyFileStream(InputStream from, OutputStream to)
            throws IOException {
        byte[] buffer = new byte[4096];
        int bytes_read;
        while((bytes_read = from.read(buffer)) != -1) {
            to.write(buffer, 0, bytes_read);
        }
    }*/

    /**
     * Prints the usage message and exits.
     */
    private static void printUsage() {
        System.err.println("Usage:\n"+ 
        "  java sofya.mutator.Mutator [-tag <db_tag>] " +
        "[-verify <prog_file>]\n" +
        "    [-suffix [ext]]\n" +
        "    [  -all\n" +
        "     | -ids <id1,id2,..,idk>\n" +
        "     | -methods <method-list-file>\n" +
        "     | -ops <op1,op2,...,opk>\n" +
        "     | -random <number>\n"+
        "     | -randOp <number> <op1,op2,...,opk>\n" + 
        "     | -randMethod <number> <method-list-file>\n" +
        "    ]\n" +
        "    <class> [in <directory|jarfile>]");
        System.exit(1);
    }

    /**
     * Entry point for the mutator.
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] argv) {
        if (argv.length < 1) {
            printUsage();
        }

        String tag = null;
        MutationSelector selector = null;
        boolean verbose = false;
        boolean verify = false;
        String verifyList = null;

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
            else if (argv[index].equals("-verbose")) {
                verbose = true;
            }
            else if (argv[index].equals("-verify")) {
                verify = true;
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    verifyList = argv[++index];
                }
                else {
                    System.err.println("Verification requires a class list " +
                        "for dependency analysis");
                    printUsage();
                }
            }
            else if (argv[index].equals("-suffix")) {
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    mutSuffix = "." + argv[++index];
                }
                else {
                    mutSuffix = ".mut";
                }
            }
            else if (argv[index].equals("-all")) {
                selector = new DefaultMutationSelector();
            }
            else if (argv[index].equals("-ids")) {
                TIntIntHashMap ids = new TIntIntHashMap();
                String idList = null;
                if (index + 1 < argv.length) {
                    idList = argv[++index];
                }
                else {
                    System.err.println("IDs not specified");
                    printUsage();
                }
                StringTokenizer st = new StringTokenizer(idList, ",");
                while (st.hasMoreTokens()) {
                    String tok = st.nextToken();
                    int sep = tok.indexOf(":");
                    try {
                        if (sep == -1) {
                            ids.put(Integer.parseInt(tok), 0);
                        }
                        else {
                            int mId = Integer.parseInt(tok.substring(0, sep));
                            int vr = Integer.parseInt(tok.substring(sep + 1));
                            ids.put(mId, vr);
                        }
                    }
                    catch (NumberFormatException e) {
                        printUsage();
                    }
                }
                selector = new IDMutationSelector(ids);
            }
            else if (argv[index].equals("-methods")) {
                File methodFile = null;
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    methodFile = new File(argv[++index]);
                }
                else {
                    System.err.println("Method list file not specified");
                    printUsage();
                }

                List methods = null;
                try {
                    methods = readMethodFile(methodFile);
                }
                catch (IOException e) {
                    System.err.println("Failure reading method list file: " +
                        e.getMessage());
                    System.exit(1);
                }
                selector = new MethodMutationSelector(methods);
            }
            else if (argv[index].equals("-ops")) {
                Set<String> ops = new THashSet();
                StringTokenizer opList = null;
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    opList = new StringTokenizer(argv[++index], ",");
                }
                else {
                    System.err.println("Operators not specified");
                    printUsage();
                }
                while (opList.hasMoreTokens()) {
                    ops.add(opList.nextToken());
                }
                selector = new OperatorMutationSelector(ops);
            }
            else if (argv[index].equals("-random")) {
                int randCount = 0;
                if (index + 1 < argv.length) {
                    try {
                        randCount = Integer.parseInt(argv[++index]);
                    }
                    catch (NumberFormatException e) {
                        printUsage();
                    }
                }
                else {
                    System.err.println("Number of mutants not specified");
                    printUsage();
                }
                selector = new RandomIDMutationSelector(randCount);
            }
            else if (argv[index].equals("-randOp")) {
                int randCount = 0;
                StringTokenizer opList = null;
                ArrayList<String> ops = new ArrayList<String>();
                if (index + 1 < argv.length) {
                    randCount = Integer.parseInt(argv[++index]);
                }
                else {
                    System.err.println("Number of random operators not " +
                        "specified");
                    printUsage();
                }
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    opList = new StringTokenizer(argv[++index], ",");
                }
                else {
                    System.err.println("Set of operator choices not " +
                        "given");
                    printUsage();
                }
                while (opList.hasMoreTokens()) {
                    ops.add(opList.nextToken());
                }
                selector = new RandomOperatorMutationSelector(ops, randCount);
            }
            else if (argv[index].equals("-randMethod")) {
                int randCount = 0;
                File methodFile = null;
                if (index + 1 < argv.length) {
                    randCount = Integer.parseInt(argv[++index]);
                }
                else {
                    System.err.println("Number of random methods not " +
                        "specified");
                    printUsage();
                }
                if ((index + 1 < argv.length)
                        && !argv[index + 1].startsWith("-")) {
                    methodFile = new File(argv[++index]);
                }
                else {
                    System.err.println("Method list file not specified");
                    printUsage();
                }

                List methods = null;
                try {
                    methods = readMethodFile(methodFile);
                }
                catch (IOException e) {
                    System.err.println("Failure reading method list file: " +
                        e.getMessage());
                    System.exit(1);
                }
                selector = new RandomMethodMutationSelector(methods, randCount);
            }
            else if (!argv[index].startsWith("-")) {
                break;
            }
            else {
                System.err.println("Unrecognized parameter: " + argv[index]);
                printUsage();
            }
        }

        String className = argv[index++];
        Mutator.requestedClassName = className;

        ProgramUnit classUnit = null;
        if ((index < argv.length) && argv[index].equals("in")) {
            if (++index < argv.length) {
                if (className.endsWith(".class")) {
                    System.err.println("Do not specify absolute path to " +
                        "class when giving a location");
                    System.exit(1);
                }
                else {
                    classUnit = new ProgramUnit(argv[index]);
                }
            }
            else {
                System.err.println("Class is in what location?");
                printUsage();
            }
        }
        else {
            classUnit = new ProgramUnit();
        }
        classUnit.addClass(className);

        if (selector == null) {
            selector = new DefaultMutationSelector();
        }

        if (verify) {
            List<String> verifyClassList = new ArrayList<String>();

            if (verifyList.endsWith(".prog")) {
                List unitList = new ArrayList();

                try {
                    Handler.readProgFile(verifyList, tag, unitList);
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

                int unitCount = unitList.size();
                Iterator iterator = unitList.iterator();
                for (int i = unitCount; i-- > 0; ) {
                    ProgramUnit pUnit = (ProgramUnit) iterator.next();
                    verifyClassList.addAll(pUnit.classes);
                }
            }
            else {
                StringTokenizer stok = new StringTokenizer(verifyList, ",");
                while (stok.hasMoreTokens()) {
                    verifyClassList.add(stok.nextToken());
                }
            }

            IRG classGraph = null;
            try {
                classGraph = new IRG(verifyClassList, true);
            }
            catch (Throwable e) {
                System.err.println("Could not construct class relation " +
                    "graph for verification");
                e.printStackTrace();
                System.exit(1);
            }

            Verifier verifier = VerifierFactory.getDefaultVerifier();
            mutator = new Mutator(selector, verifier, classGraph);
        }
        else {
            mutator = new Mutator(selector);
        }

        mutator.verbose = verbose;

        try {
            if (classUnit.isJar) {
                mutateJar(classUnit);
            }
            else {
                mutateClasses(classUnit);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
