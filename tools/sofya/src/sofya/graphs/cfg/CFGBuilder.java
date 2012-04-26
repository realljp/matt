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

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.exceptions.*;
import sofya.graphs.GraphCache;
import sofya.graphs.GraphCache.CachedGraph;
import sofya.graphs.cfg.TypeInferenceAlgorithm.SortedEdgeSet;
import static sofya.base.SConstants.*;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import gnu.trove.THashMap;

/**
 * This class constructs and caches intraprocedural control flow graphs
 * for Java methods. It provides for the ability to apply transformations
 * to the control flow graphs after the basic construction algorithm
 * has completed.
 *
 * @author Alex Kinneer
 * @version 09/26/2006
 */
@SuppressWarnings("unchecked")
public class CFGBuilder {
    /** List of classes for which CFG construction is going to occur.
        Essentially just passed through to the type inference module. */
    private List<String> classList;
    /** Name of currently loaded class. */
    private String className;

    /** Control flow graph cache */
    private GraphCache<CFG> cfgCache =
        new GraphCache<CFG>(new CFGSerializer());
    /** Type inference module. */
    private TypeInferrer typeInferrer;

    /** Handler for writing .cf files */
    private CFHandler cfHandler;
    /** Handler for writing .map files */
    private MapHandler mapHandler;
    /** List of transformations to be applied to the graph once the control
        flow has been computed. */
    private List<Object> transformers = new LinkedList<Object>();

    /** BCEL representation of class, used primarily to extract instruction
        lists to be passed to graph building methods */
    private JavaClass javaClass;
    /** Array of methods implemented by the class */
    private Method[] methods;
    /** Constant pool for the class. */
    private ConstantPoolGen cpg;
    /** Collection of handles to instructions that are leaders of blocks. */
    private TreeSet<Object> leaders;
    /** Collection of handles to instructions that end blocks. */
    private TreeSet<Object> end;
    /** Collection of handles to call instructions for which blocks are
        to be created. */
    private TreeSet<Object> calls;
    /** ID of the normal exit node (as opposed to an exceptional exit node). */
    private int normalExitNodeID = -1;
    /** Maps instructions to lists of JSR instructions which target them. */
    private Map<Object, List<Object>> jsrTargets = new THashMap();

    /** List of packages that are excluded when identifying call blocks
        (invocations of methods on classes in these packages are not marked as
        call blocks). Wildcard matching is automatic (e.g. an exclusion for
        "<code>java</code>" matches <b>all</b> classes whose package name
        begins with "<code>java.</code>").
      */
    public static final String[] excludePackages = new String[]
      {
        "java",
        "javax",
        "com.sun",
        "org.omg"
      };

    /** Conditional compilation flag specifying whether debugging information
        is to be printed. */
    private static final boolean DEBUG = false;
    private static final boolean ASSERTS = true;

    /*************************************************************************
     * Convenience constructor used internally.
     */
    protected CFGBuilder() {
        Comparator<Object> c = new IHandleComparator();
        leaders = new TreeSet<Object>(c);
        end = new TreeSet<Object>(c);
        calls = new TreeSet<Object>(c);
    }

    /*************************************************************************
     * Constructs a CFG builder.
     *
     * @param classList List of classes for which CFG construction is
     * expected to be requested.
     *
     * @throws IOException If certain I/O operations required by the type
     * inference module fail.
     */
    public CFGBuilder(List<String> classList) throws IOException {
        this();
        if (classList == null) {
            throw new NullPointerException();
        }
        if (classList.size() == 0) {
            throw new IllegalArgumentException("No classes specified");
        }
        if (!classList.get(0).getClass().equals(String.class)) {
            throw new ClassCastException("Class names must be " +
                 "specified with strings");
        }
        this.classList = classList;
        cfHandler = new CFHandler(cfgCache);
        mapHandler = new MapHandler(cfgCache);
        typeInferrer = new TypeInferrer(classList, this, cfgCache);
    }

    /*************************************************************************
     * Creates a CFG builder to build graphs for the class represented by
     * the given BCEL JavaClass.
     *
     * <p><strong>Note:</strong> Interprocedural type inference algorithms
     * will be severely crippled by the use of this constructor.</p>
     *
     * @param javaClass BCEL representation of an already parsed Java class.
     *
     * @throws BadFileFormatException If the specified class is an interface.
     * @throws IOException If certain I/O operations required by the type
     * inference module fail.
     */
    public CFGBuilder(JavaClass javaClass)
           throws BadFileFormatException, IOException {
        this();
        if (javaClass.isInterface()) {
            throw new BadFileFormatException(
                "Cannot build graphs for interface");
        }
        this.javaClass = javaClass;
        className = javaClass.getClassName();
        cpg = new ConstantPoolGen(javaClass.getConstantPool());
        methods = javaClass.getMethods();

        typeInferrer = new TypeInferrer(
            Collections.singletonList(className), this, cfgCache);
    }

    /*************************************************************************
     * Adds a transformation to be applied to control flow graphs after
     * construction.
     *
     * @param t Transformation to be added to the list of transformations
     * applied by this CFG builder.
     */
    public void addTransformer(CFGTransformer t) {
        transformers.add(t);
    }

    /*************************************************************************
     * Removes a transformation from the transformation list.
     *
     * @param t Transformation to be removed from the list of transformations
     * applied by this CFG builder.
     */
    public void removeTransformer(CFGTransformer t) {
        transformers.remove(t);
    }

    /*************************************************************************
     * Loads a new class on which to operate.
     *
     * @param className Name of the class for which graphs are to be
     * created.
     *
     * @throws LoadException If the specified class cannot be found, read,
     * parsed, or is an interface.
     */
    public void loadClass(String className) throws LoadException {
        parseClass(className, null);
    }

    /*************************************************************************
     * Loads a new class from a given stream.
     *
     * @param className Name of the class for which graphs are to be
     * managed.
     * @param source Stream from which the class should be read.
     *
     * @throws LoadException If the specified class cannot be found, read,
     * parsed, or is an interface.
     */
    public void loadClass(String className, InputStream source)
                throws LoadException {
        if (source == null) {
            throw new NullPointerException();
        }
        parseClass(className, source);
    }

    /*************************************************************************
     * Loads a class previously parsed by BCEL.
     *
     * @param clazz BCEL parsed representation of the class to be loaded.
     *
     * @throws LoadException If the given class represents an interface.
     */
    public void loadClass(JavaClass clazz) throws LoadException {
        if (clazz.isInterface()) {
            throw new LoadException("Error loading class " +
                clazz.getClassName(), new BadFileFormatException(
                "Cannot build graphs for interface"));
        }
        this.javaClass = clazz;
        className = javaClass.getClassName();
        cpg = new ConstantPoolGen(javaClass.getConstantPool());
        methods = javaClass.getMethods();
    }

    /*************************************************************************
     * Parses the class.
     *
     * <p>Uses BCEL to parse the class file.</p>
     *
     * @param className Name of the class to be parsed.
     * @param source Stream from which the class should be parsed. May be
     * <code>null</code>, in which case this method will attempt to load
     * the class from the classpath or the filesystem (in that order).
     *
     * @throws LoadException If the specified class cannot be found, read,
     * parsed, or is an interface.
     */
    protected void parseClass(String className, InputStream source)
                   throws LoadException {
        try {
            if (source != null) {
                javaClass = new ClassParser(source, className).parse();
            } // Search the classpath for the file
            else {
                javaClass = Handler.parseClass(className);
            }
        }
        catch (IOException e) {
            throw new LoadException("Error parsing class " + className, e);
        }
        catch (ClassFormatException e) {
            throw new LoadException("Error parsing class " + className, e);
        }

        if (javaClass.isInterface()) {
            throw new LoadException("Error loading class " + className,
                new BadFileFormatException("Cannot build graphs " +
                    "for interface"));
        }

        this.className = javaClass.getClassName();
        cpg = new ConstantPoolGen(javaClass.getConstantPool());
        methods = javaClass.getMethods();
        if (methods.length == 0) {
            throw new LoadException("Error loading class " + className,
               new BadFileFormatException("Class implements no methods"));
        }
    }

    /*************************************************************************
     * Sets the list of classes comprising the program for which graphs
     * are being built (if applicable).
     *
     * @param classes List of classes constituting the program for which CFG
     * construction is being performed. The list may contain a single
     * class, though this will cause some type inference algorithms to
     * perform poorly.
     */
    public void setClassList(List<String> classes) {
        if (classList == null) {
            throw new NullPointerException();
        }
        this.classList = classes;
        typeInferrer.setClassList(classes, this);
    }

    /*************************************************************************
     * Gets the name of the class currently loaded in the CFG builder.
     *
     * @return Name of the class currently loaded in the CFG builder.
     */
    public String getLoadedClass() {
        return className;
    }

    /*************************************************************************
     * Get the number of methods in the class.
     *
     * @return The number of methods found in the class.
     */
    public int getMethodCount() {
        return methods.length;
    }

    /*************************************************************************
     * Gets the signatures of all the methods found in the currently
     * loaded class.
     *
     * @return An array of method signatures for each method found in
     * the current class.
     */
    public MethodSignature[] getMethods() {
        MethodSignature[] sigs = new MethodSignature[methods.length];
        for (int i = 0; i < methods.length; i++) {
            sigs[i] = new MethodSignature(methods[i], className);
        }
        return sigs;
    }

    /*************************************************************************
     * Builds a control flow graph for a method and adds it to the cache,
     * using an index to locate the method.
     *
     * @param methodIndex Index of the method in the class for which the
     * control flow graph is to be built.
     * @param auto Flag which is set to indicate if the control flow graph
     * construction has been requested internally (as by the
     * TypeInferrer).
     *
     * @return The resulting control flow graph.
     *
     * @throws IllegalArgumentException If the given index is out of range.
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    @SuppressWarnings("unchecked")
    private CFG buildCFG(int methodIndex, boolean auto)
            throws IllegalArgumentException,
                   TypeInferenceException,
                   TransformationException {
        if (methodIndex < 0 || methodIndex >= methods.length) {
            throw new IllegalArgumentException("Method index out of range");
        }
        if (methods[methodIndex].isAbstract()
                || methods[methodIndex].isNative()) {
            return null;
        }
        reset();

        MethodGen mg = new MethodGen(methods[methodIndex], className, cpg);
        MethodSignature signature = new MethodSignature(mg);

        if (DEBUG) {
            System.out.println("++++++++++\n" + mg + "\n++++++++++");
        }
        
        CachedGraph cg = cfgCache.get(signature);
        if ((cg != null) && cg.isComplete() && cg.isFresh()) {
            if (!auto) cg.setFresh(false);
            return (CFG) cg.getGraph();
        }

        String displayString = Handler.formatSignature(className,
            methods[methodIndex].toString(), '.');
        List<Object> pendingInference = new ArrayList<Object>();

        CFG cfg = new CFG(signature, displayString);
        cg = cfgCache.put(signature, cfg);

        formBasicBlocks(cfg, mg.getInstructionList());
        formEdges(cfg, pendingInference);

        Map<Object, Object> inferredTypes = new THashMap();
        // Map<Object, Object> inferredTypes =
        //     new LinkedHashMap<Object, Object>();
        typeInferrer.inferTypes(cfg, mg, pendingInference, inferredTypes);

        if (ASSERTS) {
            assert inferredTypes.keySet().size() == pendingInference.size();
        }
        
        int nextEdgeID = cfg.nextEdgeID;
        //Iterator it = inferredTypes.keySet().iterator();
        int size = pendingInference.size();
        Iterator it = pendingInference.iterator();
        for (int i = size; i-- > 0; ) {
            Block thrower = (Block) it.next();
            SortedEdgeSet types = (SortedEdgeSet) inferredTypes.get(thrower);
            if (DEBUG) {
                System.out.println("Adding: " + thrower + " -> " + types);
            }

            if (DEBUG) System.out.println(thrower);
            for (Iterator iter = types.iterator(); iter.hasNext(); ) {
                CFEdge inferredEdge = (CFEdge) iter.next();
                if (DEBUG) {
                    System.out.println(" {");
                    System.out.println("  " + inferredEdge.getLabelType());
                    System.out.println("  " + inferredEdge.getLabel());
                    System.out.println(" }");
                }
                if (inferredEdge.getSuccNodeID() == -1) {
                    Block predecessor =
                        cfg.getBlock(inferredEdge.getPredNodeID());
                    Block successor = cfg.addExceptionalExit(predecessor);
                    inferredEdge.setSuccNodeID(successor.getID());
                }
                inferredEdge.setID(nextEdgeID++);
                cfg.addEdge(inferredEdge);
            }
        }
        cfg.nextEdgeID = nextEdgeID;

        for (Iterator li = transformers.iterator(); li.hasNext(); ) {
            CFGTransformer cfgt = (CFGTransformer) li.next();
            cfgt.transformCFG(cfg);
        }

        if (auto) {
            cg.setFresh(true);
        }

        cg.setComplete(true);

        return cfg;
    }

    /*************************************************************************
     * Helper method to find the index of a method by its signature.
     *
     * @param signature Signature of the method to be found.
     *
     * @return The index of the method in the class method array.
     *
     * @throws MethodNotFoundException If no method matching the specified name
     * and signature can be found. 
     */
    private int findMethod(MethodSignature signature)
                throws MethodNotFoundException {
        for (int index = 0; index < methods.length; index++) {
            MethodSignature currentSignature =
                new MethodSignature(methods[index], className);
            if (signature.equals(currentSignature)) {
                return index;
            }
        }
        throw new MethodNotFoundException(signature.toString());
    }

    /*************************************************************************
     * Helper method to find the index of a method by its signature,
     * attempting to load the class if the signature is not implemented
     * in the current class.
     *
     * @param signature Signature of the method to be found.
     *
     * @return The index of the method in the class method array, which
     * may be re-initialized as a result of loading a new class.
     *
     * @throws MethodNotFoundException If no method matching the specified name
     * and signature can be found in the class specified by the signature
     * (possible in the presence of subclassing).
     * @throws LoadException If unable to load the necessary class to locate
     * the requested method.
     */
    private int findMethodAutoLoad(MethodSignature signature)
                throws LoadException, MethodNotFoundException {
        String className = signature.getClassName();
        if (!className.equals(this.className)) {
            loadClass(className);
        }
        return findMethod(signature);
    }

    /*************************************************************************
     * Builds a control flow graph for a method and adds it to the cache,
     * locating the method by signature.
     *
     * @param signature Signature of the method for which a control flow
     * graph is to be built.
     *
     * @return The control flow graph for the specified method.
     *
     * @throws IllegalArgumentException If the requested method is
     * <code>native</code> or <code>abstract</code>.
     * @throws MethodNotFoundException If no method matching the specified 
     * signature can be found. 
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    public CFG buildCFG(MethodSignature signature)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException {
        int index = findMethod(signature);
        if (methods[index].isAbstract() || methods[index].isNative()) {
            throw new IllegalArgumentException("Method is abstract or native");
        }
        return buildCFG(index, false);
    }

    /*************************************************************************
     * Builds a control flow graph for a method and adds it to the cache,
     * loading another class if necessary.
     *
     * <p>This method is a hook provided to the type inference module to
     * enable interprocedural analyses. Such algorithms should take care
     * to restore the originally loaded class prior to completion so as to
     * prevent the CFG builder from becoming confused and to preserve
     * consistency in the externally visible state of the builder (this is
     * not done automatically for performance reasons).</p>
     *
     * @param signature Signature of the method for which a control flow
     * graph is to be built.
     *
     * @return The control flow graph for the specified method.
     *
     * @throws IllegalArgumentException If the requested method is
     * <code>native</code> or <code>abstract</code>.
     * @throws MethodNotFoundException If no method matching the specified 
     * signature can be found. 
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     * @throws LoadException If unable to load the necessary class to locate
     * the requested method.
     */
    CFG buildCFGAutoLoad(MethodSignature signature)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException,
                      LoadException {
        int index = findMethodAutoLoad(signature);
        if (methods[index].isAbstract() || methods[index].isNative()) {
            throw new IllegalArgumentException("Method is abstract or native");
        }
        return buildCFG(index, true);
    }

    /*************************************************************************
     * Builds a control flow graph for a method and adds it to the cache,
     * using name and signature elements to locate the method.
     *
     * @param methodName Name of the method for which to build a graph.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method.
     *
     * @return The resulting control flow graph.
     *
     * @throws IllegalArgumentException If the requested method is
     * <code>native</code> or <code>abstract</code>.
     * @throws MethodNotFoundException If no method matching the specified name
     * and signature can be found. 
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    public CFG buildCFG(String methodName, Type returnType,
                        Type[] argumentTypes)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException {
        MethodSignature signature = new MethodSignature(
            className, methodName, returnType, argumentTypes);
        return buildCFG(signature);
    }

    /*************************************************************************
     * Attempts to retrieve a control flow graph for a method from the cache,
     * using an index to locate the method. If the graph can't be found, it
     * will be built and added to the cache.
     *
     * @param methodIndex Index of the method in the class for which the
     * control flow graph is to be retrieved.
     *
     * @return The requested control flow graph.
     *
     * @throws IllegalArgumentException If the given index is out of range.
     * @throws TypeInferenceException If the CFG must be built and type
     * inference fails during that process for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    private CFG getCFG(int methodIndex) throws IllegalArgumentException,
                                               TypeInferenceException,
                                               TransformationException {
        if (methodIndex < 0 || methodIndex >= methods.length) {
            throw new IllegalArgumentException("Method index out of range");
        }
        if (methods[methodIndex].isAbstract()
                || methods[methodIndex].isNative()) {
            return null;
        }

        MethodSignature ms =
            new MethodSignature(methods[methodIndex], className);
        CFG cfg = null;

        CachedGraph c = cfgCache.get(ms);
        if (c == null) {
            cfg = buildCFG(methodIndex, false);
        }
        else {
            cfg = (CFG) c.getGraph();
        }

        return cfg;
    }

    /*************************************************************************
     * Gets a control flow graph for a method from the cache by signature.
     * If the graph does not exist it is built and added to the cache.
     *
     * @param signature Signature of the method for which the control flow
     * graph is to be retrieved.
     *
     * @return The requested control flow graph.
     *
     * @throws IllegalArgumentException If the requested method is
     * <code>native</code> or <code>abstract</code>.
     * @throws MethodNotFoundException If no method matching the specified 
     * signature can be found. 
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    public CFG getCFG(MethodSignature signature)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException  {
        CachedGraph cg = cfgCache.get(signature);
        if (cg == null) {
            CFG cfg = buildCFG(findMethod(signature), false);
            if (cfg == null) {
                throw new IllegalArgumentException("Method is abstract " +
                    "or native");
            }
            return cfg;
        }
        else {
            return (CFG) cg.getGraph();
        }
    }

    /*************************************************************************
     * Gets a control flow graph for a method from the cache by signature.
     * If the graph does not exist it is built and added to the cache,
     * loading another class if necessary to build the graph.
     *
     * <p>This method is a hook provided to the type inference module to
     * enable interprocedural analyses. Such algorithms should take care
     * to restore the originally loaded class prior to completion so as to
     * prevent the CFG builder from becoming confused and to preserve
     * consistency in the externally visible state of the builder (this is
     * not done automatically for performance reasons).</p>
     *
     * @param signature Signature of the method for which the control flow
     * graph is to be retrieved.
     *
     * @return The requested control flow graph.
     *
     * @throws MethodNotFoundException If no method matching the specified 
     * signature can be found. 
     * @throws TypeInferenceException If type inference fails during CFG
     * construction for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     * @throws LoadException If unable to load the necessary class to locate
     * the requested method.
     */
    CFG getCFGAutoLoad(MethodSignature signature)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException,
                      LoadException {
        CachedGraph cg = cfgCache.get(signature);
        if (cg == null) {
            return buildCFG(findMethodAutoLoad(signature), true);
        }
        else {
            return (CFG) cg.getGraph();
        }
    }

    /*************************************************************************
     * Attempts to retrieve a control flow graph for a method from the cache,
     * using name and signature elements to locate the method. If the graph
     * can't be found, it will be built and added to the cache.
     *
     * @param methodName Name of the method for which to build a graph.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method.
     *
     * @return The resulting control flow graph.
     *
     * @throws IllegalArgumentException If the requested method is
     * <code>native</code> or <code>abstract</code>.
     * @throws MethodNotFoundException If no method matching the specified name
     * and signature can be found. 
     * @throws TypeInferenceException If the CFG must be built and type
     * inference fails during that process for any reason.
     * @throws TransformationException If any transformation applied to
     * the CFG during construction fails.
     */
    public CFG getCFG(String methodName, Type returnType, Type[] argumentTypes)
               throws IllegalArgumentException, MethodNotFoundException,
                      TypeInferenceException, TransformationException {
        MethodSignature signature = new MethodSignature(
            className, methodName, returnType, argumentTypes);
        return getCFG(signature);
    }

    /*************************************************************************
     * Ensures that control flow graphs are built for every method in the
     * class.
     *
     * @param rebuild If <code>true</code>, every control flow graph is
     * rebuilt, otherwise graphs are only built for those methods which
     * don't yet have a control flow graph in the cache.
     *
     * @throws TypeInferenceException If any CFG must be built and type
     * inference fails during that process for any reason.
     * @throws TransformationException If any CFG must be built and any
     * transformation applied to the CFG during that process fails.
     */
    private void ensureAllCFGBuilt(boolean rebuild)
                 throws TypeInferenceException, TransformationException {
        if (rebuild) {
            for (int i = 0; i < methods.length; i++) {
                buildCFG(i, false);
            }
        }
        else {
            for (int i = 0; i < methods.length; i++) {
                getCFG(i);
            }
        }
    }

    /*************************************************************************
     * Builds the control flow graphs for every method in the class and returns
     * an iterator over the collection. This refreshes the cache for every
     * method in the class.
     *
     * <p>An iterator is returned because some graphs may be cached to disk
     * and thus can't be returned directly in-memory.</p>
     *
     * @return An iterator over the collection of control flow graphs for
     * every method in the class.
     *
     * @throws TypeInferenceException If type inference fails for any reason
     * during construction of a CFG.
     * @throws TransformationException If any transformation applied to a
     * constructed CFG fails.
     */
    public Iterator<CFG> buildAllCFG()
            throws TypeInferenceException, TransformationException {
        ensureAllCFGBuilt(true);
        return cfgCache.iterator(className);
    }

    /*************************************************************************
     * Retrieves the control flow graphs for every method in the class and
     * returns an iterator over the collection. This will cause control flow
     * graphs to be built for every method that hasn't yet had a control flow
     * graph built, but will retrieve existing control flow graphs from the
     * cache.
     *
     * <p>An iterator is returned because some graphs may be cached to disk
     * and thus can't be returned directly in-memory.</p>
     *
     * @return An iterator over the collection of control flow graphs for
     * every method in the class.
     *
     * @throws TypeInferenceException If a CFG must be built and type
     * inference fails during that process for any reason.
     * @throws TransformationException If a CFG must be built and any
     * transformation applied to it fails.
     */
    public Iterator<CFG> getAllCFG()
            throws TypeInferenceException, TransformationException {
        ensureAllCFGBuilt(false);
        return cfgCache.iterator(className);
    }

    /*************************************************************************
     * Writes the control flow file for the current class.
     *
     * @param rebuild If <code>true</code>, every control flow graph is rebuilt,
     * otherwise graphs are only built for those methods which don't yet have
     * a control flow graph in the cache.
     *
     * @throws IOException If there is an error creating or writing
     * the .cf file.
     * @throws TypeInferenceException If type inference fails for any reason
     * during construction of a CFG.
     */
    public void writeCFFile(boolean rebuild, String tag)
                throws TypeInferenceException, TransformationException,
                       IOException {
        ensureAllCFGBuilt(rebuild);
        cfHandler.setLegacySort(true);  // Diff consistency for now
        cfHandler.writeCFFile(className, className + ".java", tag);
    }

    /*************************************************************************
     * Writes the map file for the current class.
     *
     * @param rebuild If <code>true</code>, every control flow graph is
     * rebuilt, otherwise graphs are only built for those methods which
     * don't yet have a control flow graph in the cache.
     *
     * @throws IOException If there is an error creating or writing
     * the .map file.
     * @throws TypeInferenceException If type inference fails for any reason
     * during construction of a CFG.
     */
    public void writeMapFile(boolean rebuild, String tag)
                throws TypeInferenceException, TransformationException,
                       IOException {
        ensureAllCFGBuilt(rebuild);
        mapHandler.setLegacySort(true);  // Diff consistency for now
        mapHandler.writeMapFile(className, className + ".java", tag);
    }

    /*************************************************************************
     * Resets data structures used by the control flow graph construction
     * algorithm.
     */
    private void reset() {
        leaders.clear();
        end.clear();
        calls.clear();
        jsrTargets.clear();
    }

    /*************************************************************************
     * Computes the basic blocks in the method and adds them to the graph.
     *
     * @param cfg Control flow graph to which to add the computed basic
     * blocks.
     * @param il BCEL instruction list representing the method for which
     * to compute the basic blocks.
     */
    private void formBasicBlocks(CFG cfg, InstructionList il) {
        InstructionHandle ih, target, prev_ih, next_ih;
        Instruction instr;
        String methodClass;

        leaders.add(il.getStart());
        for (ih = il.getStart(); ih != null; ih = ih.getNext()) {
            if (calls.contains(ih) &&
                    (leaders.contains(ih) || end.contains(ih))) {
                //System.out.println("Continuing on offset " +
                //                   ih.getPosition());
                continue;
            }
            //System.out.println("Checking for leaders on offset " +
            //                   offset.toString());

            instr = ih.getInstruction();
            if (DEBUG) System.out.println(instr.toString(true));

            if (instr instanceof BranchInstruction) {
                if ((instr instanceof GotoInstruction) ||
                    (instr instanceof IfInstruction) ||
                    (instr instanceof JsrInstruction))
                {
                    // Add the branch instruction to the end list
                    end.add(ih);

                    // Add target to leader list
                    target = ((BranchInstruction) instr).getTarget();
                    leaders.add(target);

                    // Add instruction prior to target to end list
                    prev_ih = target.getPrev();
                    if (prev_ih != null) {
                        end.add(prev_ih);
                    }

                    // Add instruction after branch to leader list
                    next_ih = ih.getNext();
                    if (next_ih != null) {
                        // A GOTO can be the last instruction in a method
                        leaders.add(ih.getNext());
                    }

                    if (instr instanceof JsrInstruction) {
                        if (jsrTargets.containsKey(target)) {
                            List<Object> targets = jsrTargets.get(target);
                            targets.add(ih);
                        }
                        else {
                            jsrTargets.put(target, new LinkedList<Object>(
                                Collections.singleton(ih)));
                        }
                    }
                }
                else if (instr instanceof Select) {
                    Select selectInstr = (Select) instr;

                    // Add switch to end list
                    end.add(ih);

                    // Add default target to leader list. It's not very
                    // apparent, but BCEL stores the default target of a Select
                    // instruction (switch) in the target field inherited
                    // from BranchInstruction.
                    target = selectInstr.getTarget();
                    leaders.add(target);

                    // Add instruction before default target to end list
                    prev_ih = target.getPrev();
                    if (prev_ih != null) {
                        end.add(prev_ih);
                    }

                    // Process case targets
                    InstructionHandle[] targets = selectInstr.getTargets();
                    for (int k = 0; k < targets.length; k++) {
                        // Add case target to leaders list
                        leaders.add(targets[k]);
                        // Add instruction before case target to end list
                        prev_ih = targets[k].getPrev();
                        if (prev_ih != null) {
                            end.add(prev_ih);
                        }
                    }

                    // Add the instruction immediately following the switch
                    // instruction to leaders list
                    leaders.add(ih.getNext());
                }
            }
            else if (instr instanceof InvokeInstruction) {
                InvokeInstruction invokeInstr = (InvokeInstruction) instr;
                methodClass = invokeInstr.getReferenceType(cpg).toString();
                if (DEBUG) {
                    System.out.println("Invoke instruction: " + methodClass +
                        " " + invokeInstr.getMethodName(cpg));
                }
                if ((methodClass.indexOf("java.lang.System") != -1)
                        && invokeInstr.getMethodName(cpg).equals("exit")) {
                    // Add the System.exit call to the end list
                    end.add(ih);
                    // Add the instruction immediately following the System.exit
                    // call to leader list
                    leaders.add(ih.getNext());
                }
                else {
                    int i;
                    for (i = 0; i < excludePackages.length; i++) {
                        if (methodClass.startsWith(excludePackages[i])) {
                            break;
                        }
                    }
                    // An early break means we matched an excluded package
                    if (i == excludePackages.length) {
                        // Add the call instruction to the end list
                        end.add(ih);
                        // Add to the call list (used to generate dummy return
                        // blocks after calls)
                        calls.add(ih);
                        // Add the instruction immediately following the method
                        // call to the leader list
                        leaders.add(ih.getNext());
                    }
                }
            }
            else if (instr instanceof ATHROW) {
                // Add the throw instruction to the end list
                end.add(ih);
                // Add the instruction immediately following the throw
                // instruction to the leader list
                if (ih.getNext() != null) {
                    // (A throw can be the very last instruction in a method)
                    leaders.add(ih.getNext());
                }
            }
            else if (instr instanceof ReturnInstruction) {
                end.add(ih);
                if (ih.getNext() != null) {
                    leaders.add(ih.getNext());
                }
            }
            else if (instr instanceof RET) {
                // Nested try-finallys can create code immediately following
                // a RET that is only reachable as the target of an exception
                // handler (this note is for version tracking - it was
                // previously asserted that RET instructions could be
                // safely ignored).
                end.add(ih);
                if (ih.getNext() != null) {
                    leaders.add(ih.getNext());
                }
            }
        }
        end.add(il.getEnd());

        if (DEBUG) {
            System.out.println(cfg.getMethodName());
            System.out.print("getLeaders # ");
            System.out.print("leaders length: " + leaders.size() + " [");
            for (Iterator it = leaders.iterator(); it.hasNext(); ) {
                System.out.print(" " +
                    ((InstructionHandle) it.next()).getPosition());
            }
            System.out.print(" ]\nend length: " + end.size() + " [");
            for (Iterator it = end.iterator(); it.hasNext(); ) {
                System.out.print(" " +
                    ((InstructionHandle) it.next()).getPosition());
            }
            System.out.print(" ]\ncalls length: " + calls.size() + " [");
            for (Iterator it = calls.iterator(); it.hasNext(); ) {
                System.out.print(" " +
                    ((InstructionHandle) it.next()).getPosition());
            }
            System.out.print(" ]\n");
        }

        // Now create and add the actual basic blocks to the CFG.
        // Virtual blocks (Entry, Exit, Return) are given types immediately,
        // so that they won't be added to the offset->block map by
        // the overridden addNode method. Only 'real' blocks should be
        // retrievable by offset.
        int pos = 1;

        // Create and add entry block
        cfg.addBlock(
            new Block(pos++,
                      BlockType.ENTRY,
                      BlockSubType.DONTCARE,
                      BlockLabel.ENTRY,
                      0, 0,
                      il.getStart(), il.getStart())
            );

        // Add calculated blocks
        Iterator leadersIt = leaders.iterator();
        Iterator endIt = end.iterator();
        while (leadersIt.hasNext() && endIt.hasNext()) {
            InstructionHandle startHandle =
                (InstructionHandle) leadersIt.next();
            InstructionHandle endHandle =
                (InstructionHandle) endIt.next();

            Block block = new Block(pos++);
            block.setStartOffset(startHandle.getPosition());
            block.setEndOffset(endHandle.getPosition());
            block.setStartRef(startHandle);
            block.setEndRef(endHandle);
            cfg.addBlock(block);

            // Add dummy return block after call blocks
            if (calls.contains(endHandle)) {
                int offset = endHandle.getPosition();
                cfg.addBlock(
                    new Block(pos++,
                              BlockType.RETURN,
                              BlockSubType.DONTCARE,
                              BlockLabel.RETURN,
                              offset, offset,
                              endHandle, endHandle)
                    );
            }
        }

        // Create and add exit block
        InstructionHandle endHandle = (InstructionHandle) end.last();
        int offset = endHandle.getPosition();
        normalExitNodeID = pos;
        cfg.addBlock(
            new Block(pos++,
                      BlockType.EXIT,
                      BlockSubType.DONTCARE,
                      BlockLabel.EXIT,
                      offset, offset,
                      endHandle, endHandle)
            );

        // Create and add the summary block for all exceptional
        // exits which are not precisely represented in the
        // control flow (operators, array-bounds, etc...)
        cfg.addBlock(
            new Block(pos++,
                      BlockType.EXIT,
                      BlockSubType.SUMMARYTHROW,
                      BlockLabel.EXIT,
                      0, il.getEnd().getPosition(),
                      il.getStart(), il.getEnd())
            );
    }

    /**
     * Computes and adds the edges between basic blocks to the control
     * flow graph.
     *
     * @param cfg Control flow graph to which to add the edges.
     * @param pendingInference <strong>out</strong> Exception throwing
     * blocks for which type inference should be performed are placed
     * in this list.
     */
    @SuppressWarnings("unchecked")
    private void formEdges(CFG cfg, List<Object> pendingInference) {
        int targetOffset = 0;
        int nextID = 1;

        // Holds JSR edges which are waiting to have their special node ID set
        // (to the block containing the corresponding RET).
        Map<Object, Object> pendingJSRs = new THashMap();

        // Create edge between entry block and first real block
        cfg.addEdge(new CFEdge(nextID++, 2, 1, ""));

        int pos = 2;
        for (Iterator it = end.iterator(); it.hasNext(); ) {
            InstructionHandle ih = (InstructionHandle) it.next();
            Instruction instr = ih.getInstruction();

            if (instr instanceof ATHROW) {
                Block block = cfg.getBlock(pos);
                block.setLabel(BlockLabel.BLOCK);
                block.setType(BlockType.BLOCK);
                block.setSubType(BlockSubType.THROW);
                pendingInference.add(block);
            }
            else if ((instr instanceof ReturnInstruction) ||
                ((instr instanceof INVOKESTATIC) &&
                   (((INVOKESTATIC) instr).getReferenceType(cpg).toString()
                   .indexOf("java.lang.System") != -1) &&
                 (((INVOKESTATIC) instr).getMethodName(cpg).equals("exit"))))
            {
                Block block = cfg.getBlock(pos);
                block.setLabel(BlockLabel.BLOCK);
                block.setType(BlockType.BLOCK);

                if (instr instanceof ReturnInstruction) {
                    block.setSubType(BlockSubType.RETURN);
                }
                else {
                    block.setSubType(BlockSubType.SYSTEMEXIT);
                }

                cfg.addEdge(new CFEdge(nextID++, normalExitNodeID, pos, ""));
            }
            else if (instr instanceof Select) { // Switch constructs
                Select selectInstr = (Select) instr;
                InstructionHandle[] targets = selectInstr.getTargets();
                int[] matches = selectInstr.getMatchs();

                if (matches.length != targets.length) {
                    throw new ClassFormatError("Invalid switch instruction: " +
                        ih.toString().trim());
                }

                Block block = cfg.getBlock(pos);
                block.setLabel(BlockLabel.BLOCK);
                block.setType(BlockType.BLOCK);
                block.setSubType(BlockSubType.SWITCH);

                for (int j = 0; j < targets.length; j++) {
                    block = (Block) cfg.blockOffsetMap.get(
                        targets[j].getPosition());
                    cfg.addEdge(
                        new CFEdge(nextID++,
                                   block.getID(),
                                   pos,
                                   Integer.toString(matches[j]))
                        );
                }
                // Add default. Easier to do it this way than to do a bunch
                // of manipulations just to add it to the end of the
                // targets array.
                block = (Block) cfg.blockOffsetMap.get(
                    selectInstr.getTarget().getPosition()); 
                cfg.addEdge(
                    new CFEdge(nextID++, block.getID(), pos, "Default"));
            }
            else if ((instr instanceof GotoInstruction) ||
                     (instr instanceof JsrInstruction))
                {
                BranchInstruction branchInstr = (BranchInstruction) instr;
                InstructionHandle target = branchInstr.getTarget();
                targetOffset = target.getPosition();
                Block predecessor = cfg.getBlock(pos);
                Block successor = (Block) cfg.blockOffsetMap.get(targetOffset);

                predecessor.setLabel(BlockLabel.BLOCK);
                predecessor.setType(BlockType.BLOCK);
                if (instr instanceof JsrInstruction) {
                    predecessor.setSubType(BlockSubType.JSR);

                    CFEdge e = (CFEdge) pendingJSRs.get(ih);
                    if (e == null) {
                        // We initially set the special node ID to be the
                        // same as the successor. (See footer comment #1).
                        e = new CFEdge(nextID++,
                                       successor.getID(),
                                       pos,
                                       "jsr",
                                       String.valueOf(ih.getPosition()),
                                       successor.getID());
                        pendingJSRs.put(ih, e);
                    }
                    else {
                        e.setPredNodeID(pos);
                        e.setSuccNodeID(successor.getID());
                        e.setID(nextID++);
                        pendingJSRs.remove(ih);
                    }
                    cfg.addEdge(e);
                }
                else {
                    // Just a boring old GOTO
                    predecessor.setSubType(BlockSubType.GOTO);
                    cfg.addEdge(
                        new CFEdge(nextID++, successor.getID(), pos, ""));
                }
            }
            else if (instr instanceof RET) { // Return from finally block
                Block retBlock = cfg.getBlock(pos);
                retBlock.setType(BlockType.BLOCK);
                retBlock.setSubType(BlockSubType.FINALLY);
                retBlock.setLabel(BlockLabel.BLOCK);

                // Iterate over the JSRs associated with this finally block, adding the
                // corresponding return edges
                InstructionHandle finallyStart = findFinallyStart(retBlock);
                List targets = (List) jsrTargets.get(finallyStart);
                for (ListIterator li = targets.listIterator(); li.hasNext(); ) {
                    InstructionHandle jsr_ih = (InstructionHandle) li.next();
                    targetOffset = jsr_ih.getNext().getPosition();
                    Block jsrBlock =
                        (Block) cfg.blockOffsetMap.get(targetOffset);

                    // Mark this node in the associated JSR edge(s)
                    CFEdge e = (CFEdge) pendingJSRs.get(jsr_ih);
                    if (e == null) {
                        e = new CFEdge(0, 0, 0, "jsr",
                                       String.valueOf(jsr_ih.getPosition()),
                                       pos);
                        pendingJSRs.put(jsr_ih, e);
                    }
                    else {
                        e.setSpecialNodeID(pos);
                        pendingJSRs.remove(jsr_ih);
                    }

                    cfg.addEdge(
                        new CFEdge(nextID++,
                                   jsrBlock.getID(),
                                   pos,
                                   "jsr",
                                   String.valueOf(jsr_ih.getPosition()),
                                   -1));
                }
            }
            else if (instr instanceof IfInstruction) {
                Block block = cfg.getBlock(pos);
                block.setType(BlockType.BLOCK);
                block.setSubType(BlockSubType.IF);
                block.setLabel(BlockLabel.BLOCK);
                // Target if true
                targetOffset =
                    ((IfInstruction) instr).getTarget().getPosition();
                block = (Block) cfg.blockOffsetMap.get(targetOffset);
                cfg.addEdge(new CFEdge(nextID++, block.getID(), pos, "T"));
                // Target if false (flow to next block)
                cfg.addEdge(new CFEdge(nextID++, pos + 1, pos, "F"));
            }
            else if ((instr instanceof InvokeInstruction)
                    && calls.contains(ih))
            {
                Block block = cfg.getBlock(pos);
                block.setType(BlockType.CALL);
                block.setSubType(BlockSubType.DONTCARE);
                block.setLabel(BlockLabel.CALL);
                pendingInference.add(block);

                // Connect call block to return block
                cfg.addEdge(new CFEdge(nextID++, pos + 1, pos, "<r>"));

                // Connect return block to next block
                pos += 1;
                cfg.addEdge(new CFEdge(nextID++, pos + 1, pos, ""));
            }
            else { // Normal flow from the current block to next block
                Block block = cfg.getBlock(pos);
                block.setType(BlockType.BLOCK);
                block.setSubType(BlockSubType.DONTCARE);
                block.setLabel(BlockLabel.BLOCK);
                cfg.addEdge(new CFEdge(nextID++, pos + 1, pos, ""));
            }
            pos++;
        }

        cfg.nextEdgeID = nextID;
    }

    /**
     * Searches for the beginning of an abstract (source-level)
     * finally block containing a given basic block.
     *
     * <p>This method runs a linear backward search through the
     * graph for the nearest predecessor block which is the
     * target of a JSR instruction matching the given RET
     * block. When multiple predecessor blocks are encountered,
     * only the path through one of those predecessors is
     * followed (hence linear), since all reverse paths must
     * ultimately reach the same starting point. Note of
     * course that this assumes that the current basic block
     * really is contained within a source-level finally block.</p>
     *
     * <p>This method is actually the driver method for the recursive
     * search routine, which enables handling of nested finally
     * blocks. The use of the driver method avoids the problem of
     * erroneously classifying the block from which the search starts
     * as a nested finally block.</p>
     *
     * @param b Basic block for which the enclosing source-level
     * finally block should be determined.
     *
     * @return Instruction handle referencing the instruction
     * that represents the beginning of the enclosing
     * source-level finally block.
     */
    private InstructionHandle findFinallyStart(Block b) {
        if (jsrTargets.containsKey(b.getStartRef())) {
            return (InstructionHandle) b.getStartRef();
        }

        List predecessors = b.getPredecessorsList();
        if (predecessors.size() == 0) {
            throw new ClassFormatError("Finally block is unreachable");
        }
        return findFinallyStart((Block) predecessors.get(0), 0);
    }

    /**
     * Implementation of the recursive search routine to find the start of
     * a finally block.
     *
     * <p>If a RET block is encountered before an eligible
     * target of a JSR instruction, a counter is incremented. If this
     * counter is greater than zero, it is decremented when a block is
     * found to be targeted by a JSR instruction, and the search continues.
     * When the counter is zero, the first instruction of the targeted
     * block is returned. This allows the search to correctly handle
     * nested finally blocks.</p>
     *
     * @param b Current block in the search.
     * @param nestDepth Current search depth within nested finally blocks.
     *
     * @return Instruction handle referencing the instruction
     * that represents the beginning of the enclosing
     * source-level finally block.
     */
    private InstructionHandle findFinallyStart(Block b, int nestDepth) {
        if (b.getSubType() == BlockSubType.FINALLY) {
            nestDepth += 1;
        }

        if (jsrTargets.containsKey(b.getStartRef())) {
            if (nestDepth == 0) {
                return (InstructionHandle) b.getStartRef();
            }
            else {
                nestDepth -= 1;
            }
        }

        List predecessors = b.getPredecessorsList();
        if (predecessors.size() == 0) {
            throw new ClassFormatError("Finally block is unreachable");
        }
        return findFinallyStart((Block) predecessors.get(0), nestDepth);
    }

    /*************************************************************************
     * Comparator for InstructionHandle objects.
     *
     * InstructionHandles are considered equal if they refer to the same
     * object. Otherwise they are ordered according to their byte code
     * offset.
     */
    private class IHandleComparator implements Comparator<Object> {
        /**
         * Implementation of the <code>compare</code> method defined
         * by interface <code>Comparator</code>.
         */
        public int compare(Object o1, Object o2) {
            if (o1 == o2) return 0;
            if (((InstructionHandle) o1).getPosition()
                    < ((InstructionHandle) o2).getPosition()) {
                return -1;
            }
            else {
                return 1;
            }
        }
    }

    /**
     * Exception which indicates that an error has occurred during an
     * attempt to load and parse a Java class file for graph
     * construction.
     */
    public static class LoadException extends Exception {
        private static final long serialVersionUID = 7264927514836062045L;
        
        /** Originating cause of the load failure. */
        private Throwable cause = null;

        /**
        * Creates a class file load exception.
        */
        public LoadException() {
            super();
        }

        /**
        * Creates a class file load exception with a message.
        * 
        * @param msg Message associated with this exception.
        */
        public LoadException(String msg) {
            super(msg);
        }

        /**
        * Creates a class file load exception with a message and
        * causing exception.
        */
        public LoadException(String msg, Throwable cause) {
            super(msg);
            this.cause = cause;
        }

        /**
        * Gets the wrapped exception indicating the original cause
        * for failure.
        *
        * @return The original exception which caused this class
        * load exception to be raised, may be <code>null</code>.
        */
        public Throwable getCause() {
            return cause;
        }
    }
}

// #1: Some compilers do not always generate a RET instruction if control flow
// always returns from the method in the finally block. It will simply be
// overwritten if a matching RET is found. This may entail a slight performance
// penalty for algorithms which would normally use the special node ID to avoid
// multiple traversals of the same flow paths, but should not otherwise cause
// any problems.
