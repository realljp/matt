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

import java.util.*;

import sofya.base.MethodSignature;
import sofya.base.JVMStackReverser;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.base.exceptions.SofyaError;
import sofya.graphs.Graph;
import sofya.graphs.irg.IRG;
import static sofya.base.SConstants.*;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.LocalVariable;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.collections.iterators.SingletonIterator;

import gnu.trove.THashSet;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * Implementation of the flow-insensitive interprocedural type inference
 * algorithm as described by Sinha and Harrold (with necessary
 * interpretations).
 *
 * @author Alex Kinneer
 * @version 09/04/2006
 *
 * @see sofya.graphs.cfg.TypeInferrer
 * @see sofya.graphs.cfg.CFG
 * @see sofya.graphs.irg.IRG
 */
@SuppressWarnings("unchecked")
class FIInterprocedural extends TypeInferenceAlgorithm {
    /** Interclass relation graph for the program being analyzed. */
    private IRG irg;
    /** Set of classes which are considered part of the program. Classes
        not in this set are not analyzed. */
    private Set progClasses;

    /** Reference to the CFG builder, required to request construction of
        CFGs for called methods. */
    private CFGBuilder builder = null;

    /** Maps each method currently under analysis to an
        {@link InferenceSnapshot} recording the progress of the analysis
        on that method. If recursion is encountered, such a snapshot
        may be used to resume the inference. */
    private Map<Object, Object> inProgress = new THashMap();

    /** Cache which maps method signatures to sets of exception types
        previously determined to be possibly instantiated as a result of a
        call to that method. */
    private Map<Object, Object> typesCache = new THashMap();
    /** Cache which maps method signatures to sets of exception types
        previously determined to be possibly thrown from the method. */
    private Map<Object, Object> methodThrowsCache = new THashMap();
    /** Cache which maps method signatures to sets of exception types
        previously determined to be possibly thrown from a specific call
        to the method. */
    private Map<Object, Object> callThrowsCache = new THashMap();

    /** Map which stores sets of computed dynamic bindings for call blocks
        in progress, used when recursion causes inference to resume on
        pending methods. */
    private Map<Object, Object> bindingsWorklists = new THashMap();

    /** Soft memory cache which maps method signatures to the set of
        method implementations that can actually be dynamically bound
        at runtime (will not be precise). */
    private Map<Object, Set<Object>> bindingsCache = new ReferenceMap();

    /** Cache used to optimize querying for the existence of an exception
        handler starting at a given instruction handle. */
    private Map<Object, Object> handlerStarts = new THashMap();

    /** Cache which stores results of conservative thrown exception estimates
        for calls which have implementations in classes outside of the
        boundaries of the IRG. */
    private Map<Object, Set<Object>> safeEstimateCache = new ReferenceMap();

    /** Records last method initialized for analysis. */
    private MethodGen initializedMethod = null;

    /** Conditional compilation flag specifying whether debugging information
        is to be printed. */
    private static final boolean DEBUG = false;

    /**
     * Creates an instance of the flow-insensitive interprocedural type
     * inference module.
     *
     * @param irg Interclass relation graph for the program to which the
     * algorithm will be applied.
     * @param progClasses Set of classes which are considered to constitute
     * the program, calls to methods from any class not in this list will
     * not be analyzed.
     * @param builder Reference to the control flow graph builder to be
     * used for requesting the construction of graphs for called methods
     * when necessary.
     * @param graphs Reference to a cache to be used for storing CFGs if
     * necessary.
     *
     * @throws IllegalStateException If <code>irg</code> is <code>null</code>.
     */
    public FIInterprocedural(IRG irg, Set progClasses,
                             CFGBuilder builder)
           throws IllegalStateException {
        if (irg == null) {
            throw new IllegalStateException("Interprocedural analysis " +
                "requires use of '.prog' file");
        }
        if (builder == null) {
            throw new NullPointerException();
        }

        this.irg = irg;
        this.progClasses = progClasses;
        this.builder = builder;
    }

    protected void init(MethodGen mg) {
        super.init(mg);

        handlerStarts.clear();
        for (int i = 0; i < exceptions.length; i++) {
            handlerStarts.put(
                exceptions[i].getHandlerPC(),
                exceptions[i]);
        }

        initializedMethod = mg;
    }

    // JavaDoc will be inherited (see TypeInferenceAlgorithm)
    public void inferTypes(MethodGen mg, List<Object> blocks,
                           TIntObjectHashMap offsetMap,
                           Map<Object, Object> inferredTypeSets,
                           Map<Object, Object> precisionData)
                throws TypeInferenceException {        
        if (blocks.size() == 0) return;

        init(mg);
        String initialClass = builder.getLoadedClass();
        MethodSignature thisMethod = new MethodSignature(mg);
        precisionData.clear();

        if (DEBUG) System.out.println("inferTypes(5): " + thisMethod);

        InferenceState is = null;
        try {
            // Determine the types of exceptions instantiated as a result
            // of a call to this method. The inferred set for a throw block
            // will be the subset of this set which corresponds to any
            // subclasses of the conservative estimate for that throw
            MethodExceptionData methodData =
                (MethodExceptionData) typesCache.get(thisMethod);
            if (methodData == null) {
                methodData = new MethodExceptionData(thisMethod);
                typesCache.put(thisMethod, methodData);
                findExceptionTypes(methodData);
            }

            MethodThrowsData throwsData =
                (MethodThrowsData) methodThrowsCache.get(thisMethod);
            if (throwsData == null) {
                throwsData = new MethodThrowsData(thisMethod);
                methodThrowsCache.put(thisMethod, throwsData);
            }

            if (DEBUG) System.out.println("inferTypes(5): " + blocks.size());
            Iterator blockIterator = blocks.iterator();
            is = new InferenceState(mg, thisMethod,
                (Block) blockIterator.next(), blockIterator,
                offsetMap, inferredTypeSets,
                precisionData);

            inProgress.put(thisMethod, is);
            try {
                inferTypes(is);
            }
            finally {
                inProgress.remove(thisMethod);
            }
        }
        finally {    
            // Since we may have loaded other classes during the analysis,
            // restore the originally loaded class in the CFG builder so
            // that we don't leave it in a confused state
            if (!builder.getLoadedClass().equals(initialClass)) {
                try {
                    builder.loadClass(initialClass);
                }
                catch (Exception e) {
                    throw new SofyaError("Error in CFGBuilder", e);
                }
            }
        }

        if (DEBUG) {
            System.out.println("inferTypes(5) " + thisMethod +
                " return");
        }
    }

    /**
     * Drives the type inference algorithm starting from the state recorded
     * by a snapshot of the progress of the algorithm.
     *
     * <p>This is the driving method of the interprocedural type inference
     * algorithm. The type inference state identifies the method and blocks
     * on which type inference is performed and collects the results.
     * All blocks corresponding to explicit throw instructions are analyzed
     * first and added to the results. Then the call blocks recorded on the
     * first pass are sent to an auxiliary function for analysis. This
     * split approach allows handling of recursive call chains.</p>
     *
     * @param is {@link InferenceState} containing a snapshot of the progress
     * of the algorithm, which is the point from which the algorithm will
     * proceed.
     *
     * @throws TypeInferenceException If any event prevents the algorithm
     * from completing successfully; most such exceptions will wrap
     * another exception.
     */
    private void inferTypes(InferenceState is)
                 throws TypeInferenceException {
        MethodGen mg = is.methodGen;
        MethodSignature thisMethod = is.methodSignature;
        TIntObjectHashMap offsetMap = is.offsetMap;
        Map<Object, Object> inferredTypeSets = is.inferredTypeSets;
        MethodExceptionData exceptionData =
            (MethodExceptionData) typesCache.get(thisMethod);
        MethodThrowsData thisThrowsData =
            (MethodThrowsData) methodThrowsCache.get(thisMethod);
        // Used if the exception is created at the throw site
        TypeResult instanceType = new TypeResult(false, Type.THROWABLE);

        if (DEBUG) System.err.println("inferTypes(is): " + thisMethod);

        init(mg);

        // First copy inference results from prior analysis,
        // to ensure we collect the precise inference results obtained
        // from the intraprocedural analysis in the case of the
        // combined algorithm
        Set keySet = inferredTypeSets.keySet();
        int keyCount = keySet.size();
        Iterator blocks = keySet.iterator();
        for (int i = keyCount; i-- > 0; ) {
            Block thrower = (Block) blocks.next();

            SortedEdgeSet thrownTypes =
                (SortedEdgeSet) inferredTypeSets.get(thrower);

            if (DEBUG) System.out.println("   " + thrower);
            int typeCount = thrownTypes.size();
            Iterator types = thrownTypes.iterator();
            for (int j = typeCount; j-- > 0; ) {
                CFEdge inferredEdge = (CFEdge) types.next();
                if (DEBUG) {
                    System.out.println("     " + inferredEdge.getLabel());
                }
                // A successor node ID of -1 means the edge has been tagged
                // to be connected to an exceptional exit
                if (inferredEdge.getSuccNodeID() == -1) {
                    // Ignore the "<any>" exceptional exit
                    ObjectType type = (ObjectType) inferredEdge.labelType;
                    if (type != null) {
                        thisThrowsData.add(type);
                    }
                }
            }
        }

        List<Object> callBlocks = new ArrayList<Object>();
        while (is.currentBlock != null) {
            Block thrower = is.currentBlock;
            InstructionHandle ih = (InstructionHandle) thrower.getEndRef();
            Instruction instr = ih.getInstruction();

            switch (instr.getOpcode()) {
            case Constants.ATHROW: {
                //Set edgeSet = new TreeSet(typedEdgeComparator);
                SortedEdgeSet edgeSet = new SortedEdgeSet();
                Iterator typesIterator;
                ObjectType conservativeType;

                // Check whether the exception is created at the throw site
                boolean isNewInstance = inferImmediate(ih, instanceType);

                if (isNewInstance) {
                    conservativeType = instanceType.conservativeType;
                    typesIterator =
                        new SingletonIterator(instanceType.conservativeType);
                    //System.out.println("New instance");
                }
                else {
                    // If not, obtain a conservative estimate and use it
                    // to select the appropriate subset from the set of
                    // exceptions previously computed for this method
                    conservativeType =
                        getConservativeType(thrower, ih, Type.THROWABLE);
                    typesIterator = exceptionData.typeIterator();
                    if (!typesIterator.hasNext()) {
                        typesIterator =
                            new SingletonIterator(conservativeType);
                    }
                    if (DEBUG) {
                        System.out.println("conservative type: " +
                            conservativeType);
                    }
                }

                if (DEBUG) System.out.println("    new info: " + thrower);
                while (typesIterator.hasNext()) {
                    ObjectType objType = (ObjectType) typesIterator.next();

                    if (DEBUG) System.out.println("        " + objType);

                    // Screen out checked exceptions which are not subclasses
                    // of the conservative type estimate
                    if (conservativeType == null) {
                        // A null conservative type means exactly that: no
                        // (checked) exceptions can be thrown (type Throwable
                        // is used to indicate any exception may be thrown),
                        // so only allow unchecked exceptions to be added
                        if (!isUncheckedException(objType)) { continue; }
                    }
                    else {
                        try {
                            if (!objType.subclassOf(conservativeType)) {
                                continue;
                            }
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                    }

                    // Create the edges, matching successor nodes to eligible
                    // handlers when appropriate. If no matching handler
                    // exists, a successor ID of -1 is used to indicate to the
                    // CFG builder that an exceptional exit node is to be
                    // created
                    CFEdge edge = new CFEdge(0, -1, thrower.getID(), objType);
                    CodeExceptionGen handler = matchHandler(ih, objType);
                    if (handler != null) {
                        Block handlerBlock = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        edge.setSuccNodeID(handlerBlock.getID());
                    }
                    else {
                        if (isNewInstance) {
                            assert exceptionData.getTypes().contains(
                                instanceType.conservativeType);
                        }
                        thisThrowsData.add(objType);
                    }

                    edgeSet.add(edge);
                }

                if (edgeSet.size() == 0) {
                    // Possible at an ATHROW if none of the inferred types
                    // where subclasses of the conservative estimate
                    // (rare, usually only with unchecked exceptions)
                    CFEdge edge = new CFEdge(0, -1, thrower.getID(),
                                             conservativeType);
                    CodeExceptionGen handler =
                        matchHandler(ih, conservativeType);
                    if (handler != null) {
                        Block handlerBlock = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        edge.setSuccNodeID(handlerBlock.getID());
                    }
                    else {
                        thisThrowsData.add(conservativeType);
                    }
                    edgeSet.add(edge);
                }

                inferredTypeSets.put(thrower, edgeSet);

                break;
              }
            case Constants.INVOKEINTERFACE:
            case Constants.INVOKESPECIAL:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEVIRTUAL:
                callBlocks.add(thrower);
                break;
            default:
                throw new TypeInferenceException("Block does not contain " +
                    "exception throwing instruction");
            }

            if (is.blockIterator.hasNext()) {
                is.currentBlock = (Block) is.blockIterator.next();
            }
            else {
                is.currentBlock = null;
                break;
            }
        }

        is.blockIterator = callBlocks.iterator();
        if (callBlocks.size() > 0) {
            is.currentBlock = (Block) is.blockIterator.next();
            processCallBlocks(is, thisThrowsData, false);
        }
        else {
            is.currentBlock = null; 
        }

        keySet = new THashSet(inProgress.keySet());
        keyCount = keySet.size();
        Iterator waiting = keySet.iterator();
        for (int i = keyCount; i-- > 0; ) {
            InferenceState waitingIS =
                (InferenceState) inProgress.get(waiting.next());
            if (!waitingIS.methodSignature.equals(thisMethod)) {
                if (DEBUG) {
                    System.out.println(waitingIS.methodSignature);
                    System.err.println("Resume indirect:");
                    System.err.println("\t" + waitingIS.methodGen);
                    System.err.println("\tfrom: " + thisMethod);
                }
                MethodThrowsData caller = (MethodThrowsData)
                    methodThrowsCache.get(waitingIS.methodSignature);
                processCallBlocks(waitingIS, caller, true);
            }
        }

        if (initializedMethod != mg) {
            init(mg);
        }

        int size = callBlocks.size();
        Iterator throwers = callBlocks.iterator();
        for (int i = size; i-- > 0; ) {
            Block thrower = (Block) throwers.next();
            InstructionHandle ih = (InstructionHandle) thrower.getEndRef();
            MethodThrowsData callThrowsData =
                (MethodThrowsData) inferredTypeSets.get(thrower);
            ObjectType conservativeType = callThrowsData.getConservativeType();
            Iterator typesIterator = callThrowsData.typeIterator();
            SortedEdgeSet edgeSet = new SortedEdgeSet();

            if (DEBUG) System.out.println("    new info: " + thrower);
            while (typesIterator.hasNext()) {
                ObjectType objType = (ObjectType) typesIterator.next();

                if (DEBUG) System.out.println("        " + objType);

                // Screen out checked exceptions which are not subclasses
                // of the conservative type estimate
                if (conservativeType == null) {
                    // A null conservative type means exactly that: no
                    // (checked) exceptions can be thrown (type Throwable
                    // is used to indicate any exception may be thrown),
                    // so only allow unchecked exceptions to be added
                    if (!isUncheckedException(objType)) { continue; }
                }
                else {
                    try {
                        if (!objType.subclassOf(conservativeType)) {
                            continue;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }

                // Create the edges, matching successor nodes to eligible
                // handlers when appropriate. If no matching handler exists,
                // a successor ID of -1 is used to indicate to the CFG
                // builder that an exceptional exit node is to be created
                CFEdge edge =
                    new CFEdge(0, -1, thrower.getID(), objType);
                CodeExceptionGen handler = matchHandler(ih, objType);
                if (handler != null) {
                    Block handlerBlock = (Block) offsetMap.get(
                        handler.getHandlerPC().getPosition());
                    edge.setSuccNodeID(handlerBlock.getID());
                    if (objType != null) {
                        callThrowsData.add(objType);
                    }
                }

                edgeSet.add(edge);
            }

            // Add the "<any>" edge
            CFEdge edge = new CFEdge(0, -1, thrower.getID(),
                                    (ObjectType) null);
            CodeExceptionGen handler = matchHandler(ih, null);
            if (handler != null) {
                Block handlerBlock = (Block) offsetMap.get(
                    handler.getHandlerPC().getPosition());
                edge.setSuccNodeID(handlerBlock.getID());
            }
            edgeSet.add(edge);

            inferredTypeSets.put(thrower, edgeSet);
        }

        thisThrowsData.setComplete(true);

        if (DEBUG) {
            System.out.println("inferTypes(is) " + thisMethod +
                " return");
        }
    }

    /**
     * Analyzes the call blocks identified in a method to compute the
     * possible types of exceptions thrown from the corresponding calls.
     *
     * <p>This method will initiate analysis of called methods, and analysis
     * of a given method may be resumed from a called method in the presence
     * of recursion. Computation of possible dynamic bindings for calls is
     * also handled in this method.</p>
     *
     * @param is Snapshot of the type inference progress on the method
     * under analysis, used to control iteration over the call blocks of
     * the method being analyed, record results, and as a means for
     * resuming analysis in the presence of recursive call chains.
     * @param caller Auto-propagating receiving type collector for the
     * exceptions inferred from the provided call blocks. Represents
     * the union of exceptions thrown from the method from which the
     * list of call blocks was obtained, which are then available to callers
     * of that method.
     * @param resumed Flag indicated whether the analysis of the call blocks
     * for this method has been resumed from another method, which occurs
     * in the presence of recursion.
     *
     * @throws TypeInferenceException If any event prevents the algorithm
     * from completing successfully; most such exceptions will wrap
     * another exception.
     */
    @SuppressWarnings("unchecked")
    private void processCallBlocks(InferenceState is,
            MethodThrowsData caller, boolean resumed)
            throws TypeInferenceException {
        if (DEBUG) System.err.println("processCallBlocks");

        MethodGen mg = is.methodGen;
        Map<Object, Object> inferredTypeSets = is.inferredTypeSets;

        if (initializedMethod != mg) {
            init(mg);
        }

        while (is.currentBlock != null) {
            Block thrower = is.currentBlock;
            InstructionHandle ih = (InstructionHandle) thrower.getEndRef();
            Instruction instr = ih.getInstruction();
            MethodThrowsData callThrowsData;
            ObjectType conservativeType = null;

            switch (instr.getOpcode()) {
            case Constants.INVOKEINTERFACE:
            case Constants.INVOKESPECIAL:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEVIRTUAL:
                InvokeInstruction invInstr = (InvokeInstruction) instr;
                MethodSignature invoked = new MethodSignature(invInstr, cpg);

                // Get the conservative estimate of thrown types based on
                // method signature information and enclosing handlers,
                // and determine the nearest superclass
                Set<Object> conservativeThrown = new THashSet();
                conservativeType =
                    getConservativeThrown(ih, invoked, conservativeThrown);

                callThrowsData = null;
                if (resumed) {
                    callThrowsData =
                        (MethodThrowsData) callThrowsCache.get(invoked);
                }
                if (callThrowsData == null) {
                    callThrowsData =
                        new MethodThrowsData(invoked, getAllHandlerTypes(ih));
                    callThrowsCache.put(invoked, callThrowsData);
                }
                callThrowsData.addReceiver(caller);

                if (!progClasses.contains(invoked.getClassName())) {
                    callThrowsData.addAll(conservativeThrown);
                    break;
                }

                Iterator bindings = (Iterator) bindingsWorklists.get(thrower);
                if (bindings == null) {
                    boolean inclConservative = false;
                    // Currently an interclass relation graph is used to
                    // determine possible method bindings
                    Set<Object> currentBindings = bindingsCache.get(invoked);
                    if (currentBindings == null) {
                        if (DEBUG)
                            System.out.println("Determining bindings " +
                                "for: " + invoked);
                        currentBindings = new THashSet();
                        inclConservative = findBindingsWithIRG(invoked,
                            instr.getOpcode(), currentBindings);
                        bindingsCache.put(invoked, currentBindings);
                    }
                    else if (DEBUG) {
                        System.out.println("Bindings were cached (call)");
                    }

                    bindings = (new THashSet(currentBindings)).iterator();
                    bindingsWorklists.put(thrower, bindings);

                    if (inclConservative) {
                        Set<Object> safeThrown = safeEstimateCache.get(invoked);
                        if (safeThrown == null) {
                            System.err.println("WARNING: A concrete " +
                                "implementation exists outside of specified " +
                                "program boundary for:");
                            System.err.println(invoked);
                            System.err.println("A conservative estimate " +
                                "will be included.");
                            System.err.flush();

                            safeThrown = new THashSet();
                            getConservativeThrown(ih, invoked, safeThrown);
                            safeEstimateCache.put(invoked, safeThrown);
                        }
                        callThrowsData.addAll(safeThrown);
                    }
                }

                while (bindings.hasNext()) {
                    MethodSignature boundMethod =
                        (MethodSignature) bindings.next();
                    bindings.remove();

                    MethodThrowsData boundThrowsData =
                        (MethodThrowsData) methodThrowsCache.get(boundMethod);

                    if (boundThrowsData == null) {
                        if (DEBUG) System.out.println("boundThrowsData null");
                        boundThrowsData = new MethodThrowsData(boundMethod);
                        methodThrowsCache.put(boundMethod, boundThrowsData);
                    }

                    boundThrowsData.addReceiver(callThrowsData);
                    callThrowsData.addAll(boundThrowsData.getTypes());

                    findThrownTypes(boundThrowsData);

                    if (initializedMethod != mg) {
                        init(mg);
                    }
                }
                bindingsWorklists.remove(thrower);  // Cleanup

                break;
            default:
                throw new TypeInferenceException("Block " + thrower +
                    " is not call block");
            }

            callThrowsData.setConservativeType(conservativeType);
            inferredTypeSets.put(thrower, callThrowsData);

            if (is.blockIterator.hasNext()) {
                is.currentBlock = (Block) is.blockIterator.next();
            }
            else {
                is.currentBlock = null;
                break;
            }
        }

        if (initializedMethod != mg) {
            init(mg);
        }
    }

    /**
     * Attempts to determine the types that may be thrown by a specific
     * implementation of a method reachable from a call.
     *
     * <p>This method first checks whether the called method is already
     * in the process of being analyzed. If so, a recursive call chain
     * exists and it resumes the analysis of the called method from its
     * previous point. The results are then added to the set of inferred
     * types for the this call.</p>
     *
     * <p>If the called method is not already being analyzed, this method
     * requests the control flow graph for the called method. If no such
     * graph exists, a request is issued to the CFG builder to construct
     * the graph. Once the control flow graph is available, all of its
     * exceptional exit nodes are retrieved and the exceptions on the
     * edges associated with those nodes are added to the set of inferred
     * types for the this call.</p>
     *
     * @param methodThrowsData Receiving type collector for results of
     * type inference on the call.
     *
     * @throws TypeInferenceException If any event prevents the algorithm
     * from completing successfully; most such exceptions will wrap
     * another exception.
     */
    private void findThrownTypes(MethodThrowsData methodThrowsData)
                 throws TypeInferenceException {
        MethodSignature sig = methodThrowsData.getSignature();

        if (DEBUG) System.out.println("findThrownTypes(method): " + sig);

        compute:
        if (inProgress.containsKey(sig)) {
            if (DEBUG) System.out.println("resume(" + sig + ")");
            InferenceState is = (InferenceState) inProgress.get(sig);
            processCallBlocks(is, methodThrowsData, true);
        }
        else {
            CFG cfg;
            try {
                cfg = builder.getCFGAutoLoad(sig);
            }
            catch (CFGBuilder.LoadException e) {
                throw new TypeInferenceException("Error loading class", e);
            }
            catch (MethodNotFoundException e) {
                throw new TypeInferenceException("Class does not contain " +
                    "method " + sig, e);
            }
            catch (TransformationException e) {
                throw new TypeInferenceException("Error constructing CFG " +
                    " for " + sig, e);
            }

            // If null, method is native or abstract
            if (cfg == null) {
                break compute;
            }

            Iterator blocks = cfg.blockList().iterator();
            while (blocks.hasNext()) {
                Block curBlock = (Block) blocks.next();
                if ((curBlock.getType() == BlockType.EXIT) &&
                        (curBlock.getSubType() == BlockSubType.THROW)) {
                    CFEdge[] es = (CFEdge[]) cfg.getEdges(
                        curBlock, Graph.MATCH_INCOMING, CFEdge.ZL_ARRAY);
                    for (int i = 0; i < es.length; i++) {
                        if (DEBUG) {
                            System.out.println("  addType: " +
                                es[i].labelType);
                        }
                        ObjectType type = (ObjectType) es[i].labelType;
                        if (type != null) {
                            methodThrowsData.add(type);
                        }
                    }
                }
            }
        }

        if (DEBUG) {
            System.out.println("findThrownTypes(" + sig +
                ") return");
        }
    }

    /**
     * Infers types for a called method.
     *
     * <p>The safe set of method implementations that can be dynamically
     * bound at the call is obtained from the cache if possible, and determined
     * from the IRG otherwise. Then for each candidate method, a previously
     * determined set of exception types is obtained from the cache if
     * possible, otherwise the method is loaded and searched for exception
     * object creations.</p>
     *
     * @param caller Type collector for the method which called the method for
     * which types are to be inferred.
     * @param invoked Signature of the called method.
     * @param opcode JVM opcode of the call instruction.
     *
     * @throws TypeInferenceException If any event prevents the algorithm
     * from completing successfully; most such exceptions will wrap
     * another exception. A failure to load a method implementation for
     * analysis is a likely cause for this exception.
     */
    @SuppressWarnings("unchecked")
    private void inferTypes(MethodExceptionData caller,
                            MethodSignature invoked,
                            int opcode)
                 throws TypeInferenceException {
        boolean inclConservative = false;

        if (DEBUG) System.out.println("inferTypes(2): " + invoked);

        Set<Object> bindings = bindingsCache.get(invoked);
        if (bindings == null) {
            if (DEBUG) System.out.println("Determining bindings");
            bindings = new THashSet();
            inclConservative = findBindingsWithIRG(invoked, opcode, bindings);
            bindingsCache.put(invoked, bindings);
        }
        else if (DEBUG) System.out.println("Bindings were cached");

        if (inclConservative) {
            // We need to search for object instantiations, which
            // we cannot do without access to the method, so
            // all we can do is issue a warning here.
            System.err.println("WARNING: A concrete implementation " +
                "exists outside of specified program boundary for:");
            System.err.println(invoked);
            System.err.println("A conservative estimate is not possible " +
                               "here.");
            System.err.flush();
        }

        for (Iterator it = bindings.iterator(); it.hasNext(); ) {
            MethodSignature boundMethod = (MethodSignature) it.next();
            MethodExceptionData methodData =
                (MethodExceptionData) typesCache.get(boundMethod);

            if (methodData == null) {
                methodData = new MethodExceptionData(boundMethod);
                methodData.addReceiver(caller);
                typesCache.put(boundMethod, methodData);
                findExceptionTypes(methodData);
            }
            else {
                if (DEBUG) {
                    System.out.println("inferTypes(2): types were cached");
                }
                methodData.addReceiver(caller);
                caller.addAll(methodData.getTypes());
            }
        }

        if (DEBUG) System.out.println("inferTypes(2): return");
    }    

    /**
     * Performs the actual search to locate all instantions of exception
     * objects that may be caused by execution of a method, either directly
     * or through called methods.
     *
     * @param method Type collector for the method for which all possible
     * exception object instantiations are to be determined.
     *
     * @throws TypeInferenceException If any event prevents the algorithm
     * from completing successfully; most such exceptions will wrap
     * another exception. A failure to load a method implementation for
     * analysis is a likely cause for this exception.
     */
    private void findExceptionTypes(MethodExceptionData method)
                 throws TypeInferenceException {
        if (DEBUG) System.out.println("findExceptionTypes(1)");

        String className = method.getSignature().getClassName();
        loadClass(className);
        ConstantPoolGen cpg = classHandler.getConstantPool();

        InstructionList methodSource = null;
        try {
            methodSource =
                classHandler.getInstructionList(method.getSignature());
        }
        catch (MethodNotFoundException e) {
            throw new TypeInferenceException("Class consistency error", e);
        }

        InstructionHandle ih = methodSource.getStart();
        while (ih != null) {
            Instruction instr = ih.getInstruction();
            int opcode = instr.getOpcode();
            switch (opcode) {
            case Constants.INVOKESPECIAL: {
                INVOKESPECIAL invoke = (INVOKESPECIAL) instr;
                ObjectType classType =
                    (ObjectType) invoke.getReferenceType(cpg);
                try {
                    if (invoke.getMethodName(cpg).equals("<init>")
                            && classType.subclassOf(Type.THROWABLE)) {
                        method.add(classType);
                        break;
                    }
                }
                catch (NullPointerException e) {
                    // BCEL throws a useless and uninformative exception if
                    // it cannot load the class referenced by the constructor
                    // !! This may be obsolete as of BCEL 5.2
                    throw new TypeInferenceException("Introspection on " +
                        "creation of new object failed (" + classType +
                        "): classpath is probably incomplete");
                }
                catch (ClassNotFoundException e) {
                    throw new IncompleteClasspathException(e);
                }

                if (progClasses.contains(
                        invoke.getReferenceType(cpg).toString())) {
                    MethodSignature invoked = new MethodSignature(invoke, cpg);
                    inferTypes(method, invoked, opcode);
                }
                else {
                    Type returned = invoke.getReturnType(cpg);
                    if (returned.getType() == Constants.T_OBJECT) {
                        ObjectType returnType = (ObjectType) returned;
                        try {
                            if (returnType.subclassOf(Type.THROWABLE)) {
                                method.add(returnType);
                            }
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                    }
                }
                break;
            }
            case Constants.INVOKEINTERFACE:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEVIRTUAL: {
                InvokeInstruction invoke = (InvokeInstruction) instr;
                if (progClasses.contains(
                        invoke.getReferenceType(cpg).toString())) {
                    MethodSignature invoked = new MethodSignature(invoke, cpg);
                    inferTypes(method, invoked, opcode);
                }
                else {
                    Type returned = invoke.getReturnType(cpg);
                    if (returned.getType() == Constants.T_OBJECT) {
                        ObjectType returnType = (ObjectType) returned;
                        try {
                            if (returnType.subclassOf(Type.THROWABLE)) {
                                method.add(returnType);
                            }
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                    }
                }
                break;
            }}
            ih = ih.getNext();
        }
    }

    /**
     * Determines the safe set of method implementations that can be
     * dynamically bound at a call site using an interclass relation graph
     * ({@link sofya.graphs.irg.IRG}).
     *
     * @param method Signature of the method for which the set of possible
     * dynamic bindings is to be determined.
     * @param opcode JVM opcode of the call instruction for which bindings
     * are being determined (certains types of instructions require
     * special handling).
     * @param bindings <b>Out parameter</b>: Signatures for the safe
     * estimate of all methods which can be dynamically bound at the given
     * call site will be placed in the given set.
     *
     * @return <code>false</code> if all possible bindings were found in the
     * IRG, <code>true</code> if concrete implementations of the method may
     * exist in classes not defined within the IRG.
     *
     * @throws TypeInferenceException If an internal error prevents
     * determination of the possible dynamic bindings, namely if a class cannot
     * be loaded.
     */
    private boolean findBindingsWithIRG(MethodSignature method, int opcode,
            Set<Object> bindings) throws TypeInferenceException {
        String className = method.getClassName();

        IRG.ClassNode classData = null;
        try {
            classData = irg.getClassRelationData(className);
        }
        catch (ClassNotFoundException e) {
            throw new TypeInferenceException("Invalid class hierarchy data",
                                             e);
        }

        loadClass(className);

        boolean includeConservative = false;
        boolean isAbstract = classHandler.classIsAbstract();

        if (classHandler.classIsInterface()) {
            for (Iterator it = classData.implementorIterator();
                    it.hasNext(); ) {
                includeConservative |=
                    findBindingsWithIRG(new MethodSignature((String) it.next(),
                                            method), opcode, bindings);
            }
        }
        else {
            if (classHandler.containsMethod(method)) {
                bindings.add(method);
            }
            else {
                MethodSignature superBinding = findSuperclassBinding(
                    new MethodSignature(classData.getSuperclass(), method));
                if (superBinding != null) {
                    bindings.add(superBinding);
                }
                else {
                    // We may not be able to find a concrete superclass
                    // implementation if the class is abstract and the method
                    // is defined by an implemented interface
                    if (!isAbstract) {
                        includeConservative = true;
                    }
                }
            }

            // An invokespecial is only used for constructors and explicit
            // invocations of a superclass method, as by super.x(...),
            // so we do not want to search subclasses
            if (opcode == Constants.INVOKESPECIAL) {
                return includeConservative;
            }

            for (Iterator it = classData.subclassIterator(); it.hasNext(); ) {
                findSubclassBindings(new MethodSignature((String) it.next(),
                    method), bindings);
            }
        }

        return includeConservative;
    }

    /**
     * Auxiliary method to determine the nearest superclass implementation
     * of the method that will be bound in the event that a class does
     * not directly implement an override of the method in question.
     *
     * <p>We cannot directly recurse in 
     * {@link FIInterprocedural#findBindingsWithIRG}, because it would
     * attempt to locate subclass implementations of the method from the
     * superclass, resulting in an infinite recursion. Thus this auxiliary
     * method simply recurses up the superclass hierarchy to find an
     * implementation.</p>
     *
     * @param method Signature of the method for which the nearest superclass
     * implementation is to be found.
     *
     * @return The full signature of the nearest superclass implementation
     * of the method, may be <code>null</code> in which case the superclass
     * implementation is in a class not defined within the IRG.
     *
     * @throws TypeInferenceException If an internal error prevents
     * determination of the nearest superclass implementation, namely if a
     * class cannot be loaded.
     */
    private MethodSignature findSuperclassBinding(MethodSignature method)
                       throws TypeInferenceException {
        String className = method.getClassName();

        if (className.equals(IRG.UNDEF_ID) ||
                className.equals(IRG.BASE_ID)) {
            return null;
        }

        IRG.ClassNode classData = null;
        try {
            classData = irg.getClassRelationData(className);
        }
        catch (ClassNotFoundException e) {
            throw new TypeInferenceException("Invalid class hierarchy data",
                                             e);
        }

        loadClass(className);
        if (classHandler.containsMethod(method)) {
            return method;
        }
        else {
            return findSuperclassBinding(new MethodSignature(
                classData.getSuperclass(), method));
        }
    }

    /**
     * Auxiliary method to recursively determine all possible subclass
     * implementations to which the method might dynamically bind.
     *
     * <p>We cannot directly recurse in 
     * {@link FIInterprocedural#findBindingsWithIRG}, because it might
     * attempt to locate a superclass implementation of the method from the
     * subclass(es), resulting in an infinite recursion. Thus this auxiliary
     * method simply recurses into the subclass hierarchy to locate
     * implementations, ignoring classes which do not provide an overriding
     * implementation since the nearest superclass implementation is already
     * known.</p>
     *
     * @param method Signature of the method for which subclass implementations
     * are to be found.
     * @param bindings Set to which determined bindings will be added.
     *
     * @return <code>false</code> if all possible bindings were found in the
     * IRG, <code>true</code> if concrete implementations of the method may
     * exist in classes not defined within the IRG.
     *
     * @throws TypeInferenceException If an internal error prevents
     * determination of possible subclass implementations, namely if a class
     * cannot be loaded.
     */
    private boolean findSubclassBindings(MethodSignature method, Set<Object> bindings)
                    throws TypeInferenceException {
        String className = method.getClassName();

        if (className.equals(IRG.UNDEF_ID)) { return true; }

        boolean includeConservative = false;

        IRG.ClassNode classData = null;
        try {
            classData = irg.getClassRelationData(className);
        }
        catch (ClassNotFoundException e) {
            throw new TypeInferenceException("Invalid class hierarchy data",
                                             e);
        }

        loadClass(className);
        if (classHandler.containsMethod(method)) {
            bindings.add(method);
        }

        for (Iterator it = classData.subclassIterator(); it.hasNext(); ) {
            includeConservative |=
                findSubclassBindings(new MethodSignature((String) it.next(),
                    method), bindings);
        }

        return includeConservative;
    }

    /***************************************************************************
     * Gets the exception handler that starts on the given instruction, if any.
     *
     * @param ih Instruction for which the associated exception handler is to
     * be returned.
     *
     * @return The exception handler starting at the given instruction, or
     * <code>null</code> if the instruction is not the beginning of any
     * exception handler.
     */
    private CodeExceptionGen getHandler(InstructionHandle ih) {
        return (CodeExceptionGen) handlerStarts.get(ih);
    }

    /**
     * Determines the narrowest conservative estimate of the possible type of
     * exception throwable from the given instruction.
     *
     * <p>The narrowest type estimate is the nearest common superclass of the
     * types determinable from all instructions producing the thrown object.
     * Type determinations are done as follows:
     * <ul>
     * <li>Class and instance variables: the declared type of the field</li>
     * <li>Method calls: the return type</li>
     * <li><code>null</code> constant:
     *   <code>java.lang.NullPointerException</code></li>
     * <li>Array load: <i>not determinable</i>
     *   (<code>java.lang.Throwable</code>)</li>
     * </ul>
     * Local variables are resolved recursively until:
     * <ol>
     * <li>Assignment from another producing instruction: determine according
     * to rules above</li>
     * <li>Beginning of an exception handler: the class of exception caught
     * by the handler</li>
     * <li>Beginning of the method: <i>not determinable</i>
     *    (<code>java.lang.Throwable</code>)</li>
     * </ol>
     * If a cast operation is encountered, the type of the cast is used and
     * searching along the path terminates.</p>
     *
     * <p>This method is a very constrained form of the flow-sensitive search,
     * used only to obtain an initial conservative estimate as described in
     * the Sinha and Harrold paper.</p>
     *
     * @param b Block which is to be searched, should contain the throwing
     * instruction on the entry call to this method.
     * @param ih Instruction within the block from which the search is to
     * proceed, required for use during recursion.
     * @param currentConservative Current narrowest estimate of the type.
     * Typically seeded with <code>Type.THROWABLE</code> on the initial
     * entry call to this method.
     *
     * @return The narrowest type of exception that can be thrown by the
     * given instruction.
     */
    @SuppressWarnings("unchecked")
    private ObjectType getConservativeType(Block b, InstructionHandle ih,
                                           ObjectType currentConservative) {
        if (DEBUG) System.out.println("getConservativeType(3): " + b);
        Instruction instr = ih.getInstruction();

        Map<Object, Object> producers = new THashMap();
        Set<Object> visited = new THashSet();
        TypeResult infProducers = findProducers(b, ih, currentConservative,
                                                producers, visited);
        ReferenceType widestType = Type.NULL;

        if (infProducers.isPrecise) {
            for (Iterator pIt = producers.keySet().iterator();
                    pIt.hasNext(); ) {
                InstructionHandle curKey = (InstructionHandle) pIt.next();
                Block curBlock = (Block) producers.get(curKey);
                instr = curKey.getInstruction();

                currentConservative = infProducers.conservativeType;
                switch (instr.getOpcode()) {
                case Constants.ACONST_NULL:
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            (ReferenceType) Type.getType(
                                "Ljava.lang.NullPointerException;"));
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    break;
                case Constants.AALOAD:
                    // We would have to track down the array instantiation(s)
                    // to get a conservative estimate. For now, no.
                    widestType = Type.THROWABLE;
                    break;
                case Constants.ALOAD:
                case Constants.ALOAD_0:
                case Constants.ALOAD_1:
                case Constants.ALOAD_2:
                case Constants.ALOAD_3:
                    ALOAD lvLoad = (ALOAD) instr;
                    // We will look for ASTORE instructions to this index
                    int lvIndex = lvLoad.getIndex();
                    LocalVariable lv = localVars.getLocalVariable(lvIndex,
                        curKey.getPosition());
                    if (lv != null) {
                        Type lvType = Type.getType(lv.getSignature());
                        if (lvType instanceof ObjectType) {
                            ObjectType lvoType = (ObjectType) lvType;
                            try {
                                if (lvoType.subclassOf(currentConservative)) {
                                    currentConservative = lvoType;
                                }
                            }
                            catch (ClassNotFoundException e) {
                                throw new IncompleteClasspathException(e);
                            }
                        }
                    }

                    Map<Object, Object> assigns = new THashMap();
                    visited.clear();
                    if (!findStoreLocals(curBlock, curKey, lvIndex,
                                         assigns, visited)) {
                        try {
                            widestType = widestType.getFirstCommonSuperclass(
                                                    currentConservative);
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                        break;
                    }

                    for (Iterator aIt = assigns.keySet().iterator();
                            aIt.hasNext(); ) {
                        curKey = (InstructionHandle) aIt.next();
                        curBlock = (Block) assigns.get(curKey);
                        ObjectType newConservative =
                            getConservativeType(curBlock, curKey,
                                currentConservative);
                        try {
                            widestType = widestType.getFirstCommonSuperclass(
                                newConservative);
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                    }
                    break;
                case Constants.GETFIELD:
                    FieldInstruction fieldRef = (FieldInstruction) instr;
                    ObjectType fieldType = (ObjectType) Type.getType(
                        (String) fieldRef.getSignature(cpg));
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            fieldType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    break;
                case Constants.GETSTATIC:
                    fieldRef = (FieldInstruction) instr;
                    fieldType = (ObjectType) Type.getType(
                        (String) fieldRef.getSignature(cpg));
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            fieldType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    break;
                case Constants.INVOKESPECIAL:
                    INVOKESPECIAL invokeSpecial = (INVOKESPECIAL) instr;
                    ObjectType classType =
                        (ObjectType) invokeSpecial.getReferenceType(cpg);
                    try {
                        if (invokeSpecial.getMethodName(cpg).equals("<init>")
                                && classType.subclassOf(Type.THROWABLE)) {
                            widestType =
                                widestType.getFirstCommonSuperclass(classType);
                            break;
                        }
                    }
                    catch (NullPointerException e) {
                        // BCEL throws a useless and uninformative exception
                        // if it cannot load the class referenced by the
                        // constructor
                        throw new NoClassDefFoundError("Introspection on " +
                            "creation of new object failed (" + classType +
                            "): classpath is probably incomplete");
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                case Constants.INVOKEINTERFACE:
                case Constants.INVOKESTATIC:
                case Constants.INVOKEVIRTUAL:
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    Type retType = invoke.getReturnType(cpg);
                    if (retType instanceof ObjectType) {
                        ObjectType retOType = (ObjectType) retType;
                        try {
                            if (retOType.subclassOf(Type.THROWABLE)) {
                                widestType =
                                    widestType.getFirstCommonSuperclass(
                                        retOType);
                            }
                        }
                        catch (ClassNotFoundException e) {
                            throw new IncompleteClasspathException(e);
                        }
                    }
                    break;
                case Constants.CHECKCAST:
                    ObjectType castType = ((CHECKCAST) instr)
                                              .getLoadClassType(cpg);
                    try {
                        widestType = widestType.getFirstCommonSuperclass(castType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    break;
                default:
                    throw new SofyaError("Instruction is not a stack " +
                                         "producer");
                }
            }
            return (ObjectType) Type.getType(widestType.getSignature());
        }
        /*else if (instr.getOpcode() == Constants.ATHROW) {
            throw new ClassFormatError("Method attempts to throw from " +
                                       "empty stack");
        }*/
        if (DEBUG) {
            System.out.println("  imprecise: could not locate all producers");
        }
        return infProducers.conservativeType;
    }

    /***************************************************************************
     * Performs a reverse control flow search for all of the instructions that
     * could produce the current operand on the stack. It is intended that the
     * operand will be an instance of an exception that may eventually be
     * thrown at the instruction which initiated the type inference.
     *
     * <p>This is a driver method for the recursive implementation found in
     * {@link TypeInferrer#findProducers(Block, InstructionHandle,
     * JVMStackReverser, ObjectType, Map, Set)}.</p>
     *
     * @param b Basic block from which the producer search will begin.
     * @param startHandle Instruction within the block from which the search
     * will begin.
     * @param conservativeType Current narrowest estimate of the type of
     * the exception.
     * @param producers Map which records all the instructions which may
     * produce the given operand on the stack as keys to the basic blocks
     * in which they are found. This is the primary output of the method.
     * @param visited Set which records blocks that have already been
     * visited to avoid duplication of effort.
     *
     * @return A data container class which indicates whether the type
     * inference is still precise and stores the updated most conservative
     * estimate of the possible type of the exception. The type inference
     * is no longer precise if a producer cannot be located along a given
     * path because the beginning of an exception handler was reached
     * (the exception is placed on the stack by the runtime system).
     */
    private TypeResult findProducers(Block b, InstructionHandle startHandle,
                                     ObjectType conservativeType,
                                     Map<Object, Object> producers,
                                     Set<Object> visited) {
        if (DEBUG) System.out.println("findProducers(5): " + b);
        JVMStackReverser rStack = new JVMStackReverser(cpg);

        if (startHandle == b.getStartRef()) {
            CodeExceptionGen handler;
            if ((startHandle.getInstruction() instanceof ASTORE) &&
                    ((handler = getHandler(startHandle)) != null)) {
                ObjectType catchType = handler.getCatchType();
                return new TypeResult(false, (catchType == null)
                                             ? Type.THROWABLE
                                             : catchType);
            }

            if (b.getPredecessorCount() == 0) {
                // We reached the start of the method without finding
                // a producer
                throw new ClassFormatError("Method consumes from empty stack");
            }
            else {
                // Search predecessors
                Iterator predecessors = b.getPredecessorsList().iterator();
                while (predecessors.hasNext()) {
                    Block predBlock = (Block) predecessors.next();
                    TypeResult infProducers = findProducers(predBlock,
                        (InstructionHandle) predBlock.getEndRef(),
                        rStack.copy(),
                        conservativeType,
                        producers,
                        visited);
                    if (!infProducers.isPrecise) {
                        return new TypeResult(false, conservativeType);
                    }
                }
                return new TypeResult(true, conservativeType);
            }
        }
        else {
            return findProducers(b, startHandle.getPrev(), rStack,
                conservativeType, producers, visited);
        }
    }

    /***************************************************************************
     * Recursive implementation of the reverse control flow search for
     * all of the instructions that could produce the current operand on
     * the stack.
     *
     * @param b Basic block from which the producer search should proceed.
     * @param startHandle Instruction within the block from which the search
     * should proceed.
     * @param rjs Instance of a {@link sofya.base.JVMStackReverser} which
     * is used to locate the producers by simulating instruction
     * exection in reverse.
     * @param conservativeType Current narrowest estimate of the type of
     * the exception.
     * @param producers Map which records all the instructions which may
     * produce the given operand on the stack as keys to the basic blocks
     * in which they are found. This is the primary output of the method.
     * @param visited Set which records blocks that have already been
     * visited to avoid duplication of effort.
     *
     * @return A data container class which indicates whether the type
     * inference is still precise and stores the updated most conservative
     * estimate of the possible type of the exception. The type inference
     * is no longer precise if a producer cannot be located along a given
     * path because the beginning of an exception handler was reached
     * (the exception is placed on the stack by the runtime system).
     */
    private TypeResult findProducers(Block b, InstructionHandle startHandle,
                                     JVMStackReverser rStack,
                                     ObjectType conservativeType,
                                     Map<Object, Object> producers,
                                     Set<Object> visited) {
        if (DEBUG) System.out.println("findProducers(6): " + b);
        if (visited.contains(b)) {
            // The first traversal will properly indicate if the
            // inference can be precise
            return new TypeResult(true, conservativeType);
        }
        else {
            visited.add(b);
        }

        InstructionHandle cur_ih = startHandle;
        while (true) {
            Instruction instr = cur_ih.getInstruction();
            short opcode = instr.getOpcode();

            if (rStack.runInstruction(instr)) {
                if (INVALID_PRODUCERS.contains(opcode)) {
                    throw new ClassFormatError("Top stack operand is not of " +
                        "type reference at ATHROW");
                }

                producers.put(cur_ih, b);
                return new TypeResult(true, conservativeType);
            }

            if (cur_ih == b.getStartRef()) {
                CodeExceptionGen handler;
                if (rStack.atPossibleProducer() &&
                        ((handler = getHandler(cur_ih)) != null)) {
                    ObjectType catchType = handler.getCatchType();
                    return new TypeResult(false, (catchType == null)
                                                 ? Type.THROWABLE
                                                 : catchType);
                }

                if (b.getPredecessorCount() == 0) {
                    // We reached the start of the method without
                    // finding a producer
                    throw new ClassFormatError("Method consumes from " +
                        "empty stack");
                }
                else if (b.getPredecessorCount() == 1) {
                    // A single predecessor means linear control flow - allow
                    // narrowing of the conservative type estimate as we
                    // continue backward
                    Block predBlock = (Block) (b.getPredecessors()[0]);
                    return findProducers(predBlock,
                        (InstructionHandle) predBlock.getEndRef(),
                        rStack,
                        conservativeType,
                        producers,
                        visited);
                }
                else {
                    // There are multiple predecessors, continue searching
                    // the paths recursively but do not allow the conservative
                    // type estimate to be narrowed since the most conservative
                    // estimate on one path may not be valid for all paths
                    Iterator predecessors = b.getPredecessorsList().iterator();
                    while (predecessors.hasNext()) {
                        Block predBlock = (Block) predecessors.next();
                        TypeResult infProducers = findProducers(predBlock,
                            (InstructionHandle) predBlock.getEndRef(),
                            rStack.copy(),
                            conservativeType,
                            producers,
                            visited);
                        if (!infProducers.isPrecise) {
                            return new TypeResult(false, conservativeType);
                        }
                    }
                    return new TypeResult(true, conservativeType);
                }
            }

            cur_ih = cur_ih.getPrev();
        }
    }

    /***************************************************************************
     * Performs a reverse control flow search for all of the instructions
     * which assign to a given local variable.
     *
     * @param b Basic block from which the search should proceed.
     * @param startHandle Instruction within the basic block from which the
     * search should proceed.
     * @param lvIndex Index of the local variable for which value assignments
     * are to be located.
     * @param assigns <b>Out parameter</b>: Map which records all the
     * instructions which may store to the given variable as keys to the basic
     * blocks in which  they are found.
     * @param visited Set which records blocks that have already been
     * visited to avoid duplication of effort.
     *
     * @return A boolean value indicating whether the search result is
     * precise. The search is considered imprecise if assignments to
     * the variable cannot be found on all reverse flow paths.
     */
    private boolean findStoreLocals(Block b, InstructionHandle startHandle,
                                    int lvIndex, Map<Object, Object> assigns,
                                    Set<Object> visited) {
        if (DEBUG) System.out.println("findStoreLocals: " + b);
        if (visited.contains(b)) {
            // The first traversal will properly indicate if the
            // inference can be precise
            return true;
        }
        else {
            visited.add(b);
        }

        InstructionHandle cur_ih = startHandle;
        while (true) {
            Instruction instr = cur_ih.getInstruction();

            if (instr instanceof ASTORE) {
                ASTORE storeInstr = (ASTORE) instr;
                if (storeInstr.getIndex() == lvIndex) {
                    assigns.put(cur_ih, b);
                    // If the type being stored to the local variable can be
                    // inferred precisely, the type inference will be
                    // precise along this path
                    return true;
                }
            }

            if (cur_ih == b.getStartRef()) {
                if (b.getPredecessorCount() == 0) {
                    // The start of the method was reached, so the type
                    // inference cannot be precise. (If searching for
                    // an ASTORE, we assume that the ALOAD
                    // which triggered the search must reference an
                    // argument to the method)
                    if (DEBUG) {
                        System.out.println("  imprecise: reached start of " +
                            "method");
                    }
                    return false;
                }
                else {
                    // There are predecessors, continue searching
                    // the paths recursively.
                    Iterator predecessors = b.getPredecessorsList().iterator();
                    boolean isPrecise = true;
                    while (predecessors.hasNext()) {
                        Block predBlock = (Block) predecessors.next();
                        isPrecise &= findStoreLocals(predBlock,
                            (InstructionHandle) predBlock.getEndRef(),
                            lvIndex,
                            assigns,
                            visited);
                    }
                    if (DEBUG && !isPrecise) {
                        System.out.println("  imprecise: could not find all " +
                            "assignments");
                    }
                    return isPrecise;
                }
            }

            cur_ih = cur_ih.getPrev();
        }
    }

    /**
     * Class used to record a snapshot of the progress of the type inference
     * algorithm on a given method.
     */
    private static class InferenceState {
        public MethodGen methodGen;
        public MethodSignature methodSignature;
        public Block currentBlock;
        public Iterator blockIterator;
        public TIntObjectHashMap offsetMap;
        public Map<Object, Object> inferredTypeSets;
        public Map<Object, Object> precisionData;

        public InferenceState(MethodGen mg, MethodSignature mSig,
                              Block cb, Iterator bi, TIntObjectHashMap om,
                              Map<Object, Object> its,
                              Map<Object, Object> pd) {
            methodGen = mg;
            methodSignature = mSig;
            offsetMap = om;
            inferredTypeSets = its;
            precisionData = pd;
            currentBlock = cb;
            blockIterator = bi;
        }
    }

    /**
     * Special data structure class for auto-propagating type information
     * to interested receivers (typically callers).
     *
     * <p>Receivers register themselves with an instance of this class
     * created for a given method. When new types are found in the
     * method, they are then automatically propagated to all
     * receivers. This primarily reduces the complexity of dealing with
     * recursive call chains.</p>
     */
    @SuppressWarnings("unchecked")
    private static class MethodExceptionData {
        /** Signature of the method for which exception type information
            is being collected. */
        private MethodSignature method = null;
        /** Exception types found for this method. */
        protected Set<Object> types = new THashSet();
        /** Receivers registered with this method. */
        private Set<Object> receivers = new THashSet();

        /**
         * Creates a new instance to record information for the method with
         * the given signature.
         *
         * @param mSig Signature of the method for which this instance
         * will collect exception type data.
         */
        public MethodExceptionData(MethodSignature mSig) {
            this.method = mSig;
        }

        /**
         * Gets the signature of the method for which this exception type
         * collector is gathering information.
         *
         * @return Signature of the method for which this exception type
         * collector is gathering information.
         */
        public MethodSignature getSignature() {
            return method;
        }

        /**
         * Adds an exception to the types of exceptions created in this
         * method.
         *
         * @param type Type of exception to be added to the set of
         * exception types created in this method.
         */
        public void add(ObjectType type) {
            if (type == null) throw new NullPointerException();

            types.add(type);
            for (Iterator rs = receivers.iterator(); rs.hasNext(); ) {
                MethodExceptionData receiver =
                    (MethodExceptionData) rs.next();
                if (!receiver.types.contains(type)) {
                    receiver.add(type);
                }
            }
        }

        /**
         * Adds all of the exceptions in the given set to the types of
         * exceptions created in this method.
         *
         * @param types Types of exceptions to be added to the set of
         * exception types created in this method.
         */
        public void addAll(Set types) {
            for (Iterator ts = types.iterator(); ts.hasNext(); ) {
                add((ObjectType) ts.next());
            }
        }

        /**
         * Gets the types of exceptions associated with this method.
         *
         * @return The types of exceptions that are associated with this
         * method.
         */
        public Set getTypes() {
            return types;
        }

        /**
         * Gets an iterator over the types of exceptions associated with
         * this method.
         *
         * @return An iterator over the types of exceptions associated with
         * this method.
         */
        public Iterator typeIterator() {
            return types.iterator();
        }

        /**
         * Registers a new receiver with this method.
         *
         * @param Exception type collector for a method which wants to
         * receive type information from this method.
         */
        public void addReceiver(MethodExceptionData receiver) {
            receivers.add(receiver);
        }

        /**
         * Removes a method as a receiver from this method.
         *
         * @param Exception type collector for a method to be removed as
         * a receiver of type information from this method.
         */
        public void removeReceiver(MethodExceptionData receiver) {
            receivers.remove(receiver);
        }

        /**
         * Gets a string representation of this exception type collector.
         *
         * @return A string representation of this exception type collector,
         * consisting of the signature of the method and the exception types
         * associated with the method.
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(method);
            sb.append(": (");
            for (Iterator it = types.iterator(); it.hasNext(); ) {
                sb.append(" ");
                sb.append(it.next());
            }
            sb.append(" )");

            /*sb.append(Handler.LINE_SEP);
            for (Iterator it = callers.iterator(); it.hasNext(); ) {
                sb.append("  ");
                sb.append(it.next().toString());
            }*/

            return sb.toString();
        }
    }

    /**
     * Special extension of the automatic type-propagating data structure
     * for use in the thrown-by-call analysis. May be used to filter
     * propagation based on whether a class of exception actually
     * 'escapes' a particular callsite or is trapped by an exception
     * handler. Additionally records whether the current results for the
     * method are considered to be 'complete'.
     */
    private static class MethodThrowsData extends MethodExceptionData {
        /** Set containing classes of exceptions caught by handlers
            enclosing a particular callsite for this method. Used
            to filter those classes of exceptions from the automatic
            propagation as 'non-escaping' exceptions. */
        private Set<Object> handlers;

        /** The most conservative thrown type associated with the method,
            carried around here essentially as a bookeeping convenience
            because of the partial computations and resumptions that occur
            in the face of recursive call chains. */
        private ObjectType conservativeType;

        // Many of these may exist in a large program with recursion, so
        // yes, I am going to be so stingy as to ensure that only a single
        // byte is used for the flag, since many compilers like to promote
        // booleans to ints out of laziness
        private byte complete = 0;

        @SuppressWarnings("unchecked")
        MethodThrowsData(MethodSignature mSig) {
            super(mSig);
            this.handlers = Collections.EMPTY_SET;
        }

        MethodThrowsData(MethodSignature mSig, Set<Object> handlers) {
            super(mSig);

            if (handlers == null) throw new NullPointerException();
            this.handlers = handlers;
        }

        public void add(ObjectType type) {
            if (handlers.contains(type)) {
                types.add(type);
                return;
            }

            int size = handlers.size();
            Iterator iterator = handlers.iterator();
            for (int i = size; i-- > 0; ) {
                ObjectType catchType = (ObjectType) iterator.next();
                try {
                    if (type.subclassOf(catchType)) {
                        handlers.add(type);
                        types.add(type);
                        return;
                    }
                }
                catch (ClassNotFoundException e) {
                    throw new IncompleteClasspathException(e);
                }
            }

            super.add(type);
        }

        public ObjectType getConservativeType() {
            return conservativeType;
        }

        public void setConservativeType(ObjectType t) {
            conservativeType = t;
        }

        public boolean isComplete() {
            return (complete == 1);
        }

        public void setComplete(boolean c) {
            complete = (byte) (c ? 1 : 0);
        }
    }
}
