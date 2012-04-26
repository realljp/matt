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

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.ListIterator;

import sofya.base.ByteSourceHandler;
import sofya.base.MethodSignature;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.base.exceptions.IncompleteClasspathException;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.LocalVariableTable;

import org.apache.commons.collections.map.ReferenceMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TShortHashSet;

/**
 * Abtract base class for all type inference algorithms to be made
 * available through the {@link sofya.graphs.cfg.TypeInferrer}.
 *
 * <p>Defines common data structures and a variety of utility methods
 * and classes useful for all type inference algorithms.</p>
 *
 * @author Alex Kinneer
 * @version 06/09/2006
 *
 * @see sofya.graphs.cfg.TypeInferrer
 * @see sofya.graphs.cfg.CFG
 */
@SuppressWarnings("unchecked")
abstract class TypeInferenceAlgorithm {
    /** BCEL representation of the constant pool for the class from
        which the code under analysis originates. */
    protected ConstantPoolGen cpg;
    /** BCEL representation of the local variable table for the method. */
    protected LocalVariableTable localVars;
    /** BCEL representation of the exception handlers associated with
        the method. */
    protected CodeExceptionGen[] exceptions;
    /** Set of exceptions declared as thrown by the method under analysis. */
    protected ObjectType[] thrownExceptions;
    /** Stores the widest common superclass of exceptions types that are
        declared as throwable by the method under analysis. */
    protected ObjectType widestThrown;

    /** Soft memory cache which maps class names to previously created
        instances of {@link sofya.base.ByteSourceHandler} with the
        class loaded (eliminates a great deal of redundant file I/O). */
    private Map<Object, Object> classCache = new ReferenceMap();
    /** <code>ByteSourceHandler</code> currently available for use in
        retrieving class bytecodes. */
    protected ByteSourceHandler classHandler = new ByteSourceHandler();
    /** Class which is loaded in {@link FIInterprocedural#classHandler}. */
    private String loadedClass = null;

    /** Constant representing the Java <code>Error</code> class, a
        base class for <em>unchecked</em> exceptions. */
    private static final ObjectType typeError =
        new ObjectType("java.lang.Error");
    /** Constant representing the Java <code>RuntimeException</code> class,
        a base class for <em>unchecked</em> exceptions. */
    private static final ObjectType typeRuntimeException =
        new ObjectType("java.lang.RuntimeException");

    /** Set of JVM stack operand producers that do not produce a legal
        type to be thrown as an exception. */
    protected static final TShortHashSet INVALID_PRODUCERS = new TShortHashSet(
        new short[]{ Constants.ANEWARRAY, Constants.MULTIANEWARRAY,
            Constants.NEWARRAY, Constants.LDC, Constants.LDC_W,
            Constants.LDC2_W });

    /** Conditional compilation flag specifying whether debugging information
        is to be printed. */
    private static final boolean DEBUG = false;

    /**************************************************************************
     * Initializes a variety of data structures used by all type inference
     * algorithms using the given BCEL representation of the method.
     *
     * @param mg BCEL representation of the method on which type inference
     * is being performed.
     */
    protected void init(MethodGen mg) {
        this.cpg = mg.getConstantPool();
        this.localVars = mg.getLocalVariableTable(cpg);
        this.exceptions = mg.getExceptionHandlers();

        ReferenceType widest = Type.NULL;
        String[] thrown = mg.getExceptions();
        thrownExceptions = new ObjectType[thrown.length];
        for (int i = 0; i < thrown.length; i++) {
            thrownExceptions[i] = new ObjectType(thrown[i]);
            try {
                widest = widest.getFirstCommonSuperclass(thrownExceptions[i]);
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
        }
        if (widest.equals(Type.NULL)) {
            widestThrown = Type.THROWABLE;
        }
        else {
            widestThrown = (ObjectType) Type.getType(widest.getSignature());
        }
    }

    /**************************************************************************
     * Executes the type inference algorithm to compute the possible
     * types for each of the instructions in a given set of exception
     * throwing instructions.
     *
     * <p>This method is the interface through which the
     * {@link sofya.graphs.cfg.TypeInferrer} accesses all type
     * inference algorithms.</p>
     *
     * @param mg BCEL representation of the method on which type inference
     * is being performed.
     * @param blocks List of {@link sofya.graphs.cfg.Block}s containing
     * potential exception throwing instructions identified in the method.
     * @param offsetMap Maps bytecode offsets to basic blocks, used to
     * optimize block retrievals.
     * @param inferredTypeSets <strong>[out]</strong> Map which will
     * contain mappings from each block in <code>blocks</code> to the
     * set of exception types inferred for that throwing instruction.
     * @param precisionData <strong>[out]</strong> Map which will contain
     * mappings from each block in <code>blocks</code> to a
     * {@link TypeInferenceAlgorithm#TypeResult} indicating whether the
     * type inference for that throwing instruction was precise and the
     * narrowest conservative type that the algorithm could determine.
     *
     * @throws TypeInferenceException To indicate any number of possible
     * errors which prevent the type inference from completing successfully.
     * In many cases, another exception will be encapsulated which indicates
     * the specific cause of the failure (see
     * {@link TypeInferenceException#getCause}).
     */
    protected abstract void inferTypes(MethodGen mg, List<Object> blocks,
                                       TIntObjectHashMap offsetMap,
                                       Map<Object, Object> inferredTypeSets,
                                       Map<Object, Object> precisionData)
                            throws TypeInferenceException;

    /**************************************************************************
     * Determines whether the given instruction is throwing a newly created
     * exception and if so, infers the type of that exception.
     *
     * <p>This method performs the most basic form of type inference in the
     * context of Java bytecode. From the perspective of Java source code,
     * it isn't even really type inference.</p>
     *
     * @param ih BCEL handle to the instruction at which an exception is
     * being thrown. It is presumed that the instruction is an
     * <code>ATHROW</code>.
     * @param instanceType <strong>[out]</strong> Will be modified to
     * indicate the precision of the type inference and the narrowest
     * type of exception that could be determined. If precise, this
     * will always be that class of exception instantiated for the
     * throw. If imprecise, this will always be
     * <code>java.lang.Throwable</code>. This out parameter is provided
     * primarily as a convenience to grant extra flexibility to callers.
     *
     * @return <code>true</code> if the exception being thrown is newly
     * instantiated (the inference is precise), <code>false</code>
     * otherwise.
     */
    protected boolean inferImmediate(InstructionHandle ih,
                                     TypeResult instanceType) {
        // Easy type inference: was a new exception object just created,
        // and if so, what class of exception was it?
        ObjectType catchType = null;
        Instruction instr = ih.getPrev().getInstruction();
        if (instr instanceof INVOKESPECIAL) {
            INVOKESPECIAL invokeInstr = (INVOKESPECIAL) instr;
            ObjectType classType =
                (ObjectType) invokeInstr.getReferenceType(cpg);
            try {
                if (invokeInstr.getMethodName(cpg).equals("<init>")
                        && classType.subclassOf(Type.THROWABLE)) {
                    catchType = classType;
                }
            }
            catch (NullPointerException e) {
                // BCEL throws a useless and uninformative exception
                // if it cannot load the class referenced by the constructor
                // !! This may be obsolete as of BCEL 5.2
                System.err.println("Introspection on creation of new " +
                    "object failed (" + classType + "): classpath is " +
                    "probably incomplete");
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
        }

        if (catchType != null) {
            instanceType.isPrecise = true;
            instanceType.conservativeType = catchType;
            return true;
        }
        else {
            instanceType.isPrecise = false;
            instanceType.conservativeType = Type.THROWABLE;
            return false;
        }
    }

    /**************************************************************************
     * Matches an instruction to the nearest exception handler for a given
     * class of exception, in the same way that the search would be performed
     * by the virtual machine (linear top-to-bottom search of enclosing
     * handlers in the method exception table). 
     *
     * @param ih Instruction for which the matching exception handler is to
     * be returned.
     * @param catchType Class of exception which the handler must catch.
     *
     * @return The exception handler to which control would transfer if an
     * exception of the given type were thrown at the given instruction.
     */
    protected CodeExceptionGen matchHandler(InstructionHandle ih,
                                            ObjectType catchType) {
        int offset = ih.getPosition();
        for (int i = 0; i < exceptions.length; i++) {
            if ((exceptions[i].getStartPC().getPosition() <= offset) &&
                    (offset <= exceptions[i].getEndPC().getPosition())) {
                ObjectType exceptionType = exceptions[i].getCatchType();
                if (catchType == null) {
                    if (exceptionType == null) {
                        return exceptions[i];
                    }
                }
                else {
                    try {
                        if ((exceptionType == null) ||
                                catchType.subclassOf(exceptionType)) {
                            return exceptions[i];
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }
            }
        }
        return null;
    }

    /**************************************************************************
     * Determines all of the exception handlers to which control flow can
     * transfer from a given instruction.
     *
     * <p>The exception handlers table is searched in the same fashion as it
     * would be by the Java runtime system, and all enclosing handlers up to
     * and including the first &quot;<code>finally</code>&quot; handler are
     * returned. This is guaranteed to be safe because the compiler must
     * ensure that the nesting of finally blocks is respected when ordering
     * entries in the exception handlers table.</p>
     *
     * @param ih Instruction handle referencing the instruction for which all
     * eligible enclosing exception handlers are to be determined.
     *
     * @return A list of exception handlers to which control flow can legally
     * transfer from the given instruction.
     */
    protected List<Object> getAllHandlers(InstructionHandle ih) {
        List<Object> matches = new ArrayList<Object>(4);
        int offset = ih.getPosition();
        for (int i = 0; i < exceptions.length; i++) {
            if ((exceptions[i].getStartPC().getPosition() <= offset) &&
                    (offset <= exceptions[i].getEndPC().getPosition())) {
                ObjectType exceptionType = exceptions[i].getCatchType();
                if (exceptionType == null) {
                    break;
                }
                else {
                    matches.add(exceptions[i]);
                }
            }
        }
        return matches;
    }

    /**************************************************************************
     * Gets all of the classes of exceptions caught by handlers to which
     * control flow can transfer from a given instruction.
     *
     * @param ih Instruction handle referencing the instruction for which the
     * catch types of all eligible enclosing exception handlers are to be
     * determined.
     *
     * @return A set of classes of exceptions which are caught by handlers
     * enclosing the given instruction.
     */
    @SuppressWarnings("unchecked")
    protected Set<Object> getAllHandlerTypes(InstructionHandle ih) {
        Set<Object> types = new THashSet();
        int offset = ih.getPosition();
        for (int i = 0; i < exceptions.length; i++) {
            if ((exceptions[i].getStartPC().getPosition() <= offset) &&
                    (offset <= exceptions[i].getEndPC().getPosition())) {
                ObjectType exceptionType = exceptions[i].getCatchType();
                if (exceptionType == null) {
                    types.add(Type.THROWABLE);
                    break;
                }
                else {
                    types.add(exceptionType);
                }
            }
        }
        return types;
    }

    /**************************************************************************
     * Identifies and loads the interface which declares a given method, if
     * the method is in fact declared in an interface.
     *
     * <p>The interfaces implemented by the given initial class are searched
     * recursively to locate the interface which declares the given method,
     * assuming the method is declared in one of the interfaces implemented
     * by that class, either directly or indirectly (through interface
     * inheritance).</p>
     *
     * @param method Signature of the method for which an interface declaration
     * should be located, if any such declaration exists.
     * @param initClass Initial class from which the search will begin.
     *
     * @return A method signature for which a call to
     * {@link sofya.base.MethodSignature#getClass} will return the interface
     * which actually declares the method, or <code>null</code> if the method
     * is not declared by any interface implemented either directly or
     * indirectly by the given class. If a declaring interface is found, the
     * interface is loaded by the class handler as a side effect of this
     * method.
     *
     * @throws ClassNotFoundException If a class required during the search
     * cannot be loaded by the class loader.
     * @throws TypeInferenceException If there is an error when attempting
     * to load the declaring interface which is found, if applicable.
     */
    private MethodSignature loadDeclaringInterface(MethodSignature method,
            Class initClass) throws ClassNotFoundException,
                                    TypeInferenceException {
        Class[] implemented = initClass.getInterfaces();
        for (int i = 0; i < implemented.length; i++) {
            String className = implemented[i].getName();
            method = new MethodSignature(className, method);
            loadClass(className);
            if (classHandler.containsMethod(method)) {
                return method;
            }
            else {
                MethodSignature methodIDef =
                    loadDeclaringInterface(method, implemented[i]);
                if (methodIDef != null) {
                    return methodIDef;
                }
            }
        }

        return null;
    }

    /**************************************************************************
     * Identifies and loads the superclass or interface implemented by a
     * superclass which declares a given method, if the method is in fact
     * declared at one of those points in the class hierarchy.
     *
     * <p>The superclass hierarchy is searched from the given initial class
     * to locate the class or interface which declares the given method.
     * At each superclass, it is checked whether that class directly
     * declares the method, or whether any interfaces implemented either
     * directly or indirectly by that superclass declares the method.</p>
     *
     * @param method Signature of the method for which the declaring class
     * or interface in the superclass hierachy is to be determined.
     * @param initClass Initial class from which the search will begin.
     *
     * @return A method signature for which a call to
     * {@link sofya.base.MethodSignature#getClass} will return the class or
     * interface which actually declares the method, or <code>null</code> if
     * the method is not declared by any class or interface in the superclass
     * hierarchy from the given class. If a declaring class or interface is
     * found, it is loaded by the class handler as a side effect of this
     * method.
     *
     * @throws ClassNotFoundException If a class required during the search
     * cannot be loaded by the class loader.
     * @throws TypeInferenceException If there is an error when attempting
     * to load the declaring class or interface which is found, if applicable.
     */
    private MethodSignature loadDeclaringClass(MethodSignature method,
            Class initClass) throws ClassNotFoundException,
                                    TypeInferenceException {
        Class currentClass = initClass.getSuperclass();
        while (true) {
            if (currentClass == null) {
                return null;
            }
            else {
                String className = currentClass.getName();
                loadClass(className);
                method = new MethodSignature(className, method);
            }

            if (classHandler.containsMethod(method)) {
                return method;
            }
            else {
                MethodSignature methodIDef =
                    loadDeclaringInterface(method, currentClass);
                if (methodIDef == null) {
                    currentClass = currentClass.getSuperclass();
                }
                else {
                    return methodIDef;
                }
            }
        }
    }

    /**************************************************************************
     * Determines a conservative estimate of the types of exceptions that
     * can be thrown from a called method.
     *
     * <p>The estimate is obtained by taking a union of the types of exceptions
     * the called method declares as thrown, the types of exceptions caught
     * by all eligible handlers enclosing the call site, and the types of
     * exceptions declared as thrown by the caller. For enclosing handlers
     * and types thrown by the caller, only subclasses of those types declared
     * as thrown by the callee and unchecked exceptions are considered.
     * Unchecked exceptions which are not declared or explicitly handled
     * cannot be determined by this method.</p>
     *
     * @param call Instruction handle referencing the method invocation
     * for which a conservative estimate of possible types of thrown exceptions
     * is to be determined.
     * @param invoked Signature of the called method.
     * @param types <strong>[out]</strong> Set into which estimated exception
     * types will be written.
     *
     * @return The most conservative exception type which captures all of
     * the estimated exception types, e.g. the first common superclass of
     * all of the estimated types.
     */
    protected ObjectType getConservativeThrown(InstructionHandle call,
                                               MethodSignature invoked,
                                               Set<Object> types)
                         throws TypeInferenceException {
        types.clear();
        ReferenceType widestType = Type.NULL;
        MethodSignature origInvoked = invoked;

        String className = invoked.getClassName();
        loadClass(className);

        // The actual method definition/implementation may be in a superclass,
        // superinterface, or implemented interface
        if (!classHandler.containsMethod(invoked)) {
            try {
                Class theClass = Class.forName(className);

                invoked = loadDeclaringInterface(invoked, theClass);
                if ((invoked == null) && !theClass.isInterface()) {
                    invoked = loadDeclaringClass(origInvoked, theClass);
                }
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException("Inference failed on " +
                    "call: the declaring class or interface could not be " +
                    "found on the classpath\n(" + origInvoked + ")");
            }
        }

        if (invoked == null) {
            throw new TypeInferenceException("Inference failed on call: " +
                "unable to find declaration of method for use in " +
                "conservative estimate\n(" + origInvoked + ")");
        }

        String[] thrownByCallee = null;
        try {
            thrownByCallee = classHandler.getMethod(invoked).getExceptions();
        }
        catch (MethodNotFoundException e) {
            throw new TypeInferenceException("Class consistency error", e);
        }

        for (int i = 0; i < thrownByCallee.length; i++) {
            ReferenceType currentType = new ObjectType(thrownByCallee[i]);
            types.add(currentType);
            try {
                widestType = widestType.getFirstCommonSuperclass(currentType);
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
        }

        ObjectType widestThrown = null;
        if (!widestType.equals(Type.NULL)) {
            widestThrown =
                (ObjectType) Type.getType(widestType.getSignature());
        }

        List handlers = getAllHandlers(call);
        for (Iterator i = handlers.iterator(); i.hasNext(); ) {
            ObjectType catchType =
                ((CodeExceptionGen) i.next()).getCatchType();
            if (!types.contains(catchType)) {
                if (widestThrown == null) {
                    if (isUncheckedException(catchType)) {
                        types.add(catchType);
                    }
                }
                else {
                    try {
                        if (catchType.subclassOf(widestThrown)) {
                            types.add(catchType);
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }
            }
        }

        for (int i = 0; i < thrownExceptions.length; i++) {
            if (!types.contains(thrownExceptions[i])) {
                if (widestThrown == null) {
                    if (isUncheckedException(thrownExceptions[i])) {
                        types.add(thrownExceptions[i]);
                    }
                }
                else {
                    try {
                        if (thrownExceptions[i].subclassOf(widestThrown)) {
                            types.add(thrownExceptions[i]);
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }
            }
        }

        return widestThrown;
    }

    /**************************************************************************
     * Utility method which determines whether a type represents an
     * unchecked exception in the Java language.
     *
     * <p>An unchecked exception is defined to be any exception of type
     * <code>java.lang.Error</code>, <code>java.lang.RuntimeException</code>,
     * or any subclasses thereof.</p>
     *
     * @param type Type to be tested for membership in the set of unchecked
     * exceptions.
     *
     * @return <code>true</code> if the given type is an unchecked exception,
     * <code>false</code> otherwise.
     */
    protected boolean isUncheckedException(ObjectType type) {
        try {
            return (type.subclassOf(typeRuntimeException) ||
                        type.subclassOf(typeError));
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }
    }

    /**************************************************************************
     * Loads a class file and makes it available through a
     * {@link sofya.base.ByteSourceHandler}. Backed by a soft-memory
     * cache for quick retrieval of an existing handler if the class has
     * been previously loaded, memory allowing.
     *
     * @param className Name of the class to be loaded.
     *
     * @throws Exception If the requested class file cannot be loaded.
     */
    protected void loadClass(String className) throws TypeInferenceException {
        try {
            if (loadedClass != className) {
                classHandler = (ByteSourceHandler) classCache.get(className);
                if (classHandler == null) {
                    classHandler = new ByteSourceHandler();
                    classHandler.readSourceFile(className);
                    classCache.put(className, classHandler);
                }
                else if (DEBUG) System.out.println("Handler was cached");
                loadedClass = className;
            }
        }
        catch (java.io.IOException e) {
            throw new TypeInferenceException("I/O Error", e);
        }
    }

    /**************************************************************************
     * Container class to simplify returning additional data from the recursive
     * methods. Contains fields indicating whether the type inference is
     * precise, and the most conservative type estimate determined by the
     * algorithm (used if it is not precise).
     */
    static class TypeResult {
        /** Flag indicating whether type inference is precise. */
        public boolean isPrecise = false;
        /** Stores narrowest estimate of the type of the exception. */
        public ObjectType conservativeType = Type.THROWABLE;

        /**
         * Creates a new type result.
         *
         * @param p Boolean indicating whether the inference is still precise.
         * @param t BCEL representation of the narrowest conservative
         * estimate of the type.
         */
        public TypeResult(boolean p, ObjectType t) {
            isPrecise = p;
            conservativeType = t;
        }

        /**
         * Creates string representation of the TypeResult in the
         * form:
         *
         * <p>(<code>isPrecise</code>,<code>conservativeType</code>)</p>
         */
        public String toString() {
            // Convenient for debugging
            return "(" + isPrecise + ", " + conservativeType + ")";
        }
    }

    /**
     * Special set implementation to properly order collections of edges
     * with associated label types (e.g. by exception types). The
     * implementation guarantees that edges will always be ordered
     * correctly relative to other edges in the set with which a
     * relationship exists in the class hierarchy. That is,
     * subclasses are considered &apos;less than&apos; their superclasses.
     * During insertion, classes which do not have any relationship
     * to the class associated with the edge being inserted are ignored
     * and the search for the proper insertion point continues.
     */
    static class SortedEdgeSet {
        /** List to actually contain the edges. Should support efficient
            insertion at arbitrary positions via the list iterator. */
        private List<Object> edges = new LinkedList<Object>();

        public SortedEdgeSet() { }

        /**
         * Adds a new edge to the set.
         *
         * <p>The backing list is searched in reverse until the type of
         * exception associated with the new edge is found to be a superclass
         * of an edge already in the list, at which point it is inserted
         * after the existing element in the list. If the exception type
         * associated with an existing edge in the set does not have
         * any relationship with the added edge in the class hierarchy,
         * the search continues. Thus an added edge for which the associated
         * exception type has no association with any existing edges, or
         * is the deepest subclass yet seen, will 'sink' to the front of
         * the list. Edges with exception types already present in the list
         * are discarded, preserving the set behavior.
         *
         * @param e Control flow edge with an associated exception type
         * to be added to the set.
         *
         * @return <code>true<code>If the edge was added to the set,
         * <code>false</code> if the set already contained an edge with
         * the same associated exception type.
         */
        public boolean add(CFEdge e) {
            ObjectType addType = (ObjectType) e.getLabelType();

            if (addType == null) {
                int size = edges.size();
                if ((size == 0) || (edges.get(size - 1)) != null) {
                    edges.add(e);
                    return true;
                }
                else {
                    return false;
                }
            }

            ListIterator<Object> li = edges.listIterator(edges.size());
            while (li.hasPrevious()) {
                CFEdge cfe = (CFEdge) li.previous();

                ObjectType entryType = (ObjectType) cfe.getLabelType();

                if (entryType == null) {
                    continue;
                }

                if (entryType.equals(addType)) {
                    return false;
                }

                try {
                    if (entryType.subclassOf(addType)) {
                        li.next();
                        li.add(e);
                        li.previous();
                        return true;
                    }
                }
                catch (ClassNotFoundException exc) {
                    throw new IncompleteClasspathException(exc);
                }
            }
            li.add(e);
            return true;
        }

        /**
         * Checks whether the set contains a given control flow edge.
         *
         * @param e Control flow edge for which the set is being queried.
         *
         * @return <code>true</code> if the set contains the given edge.
         */
        public boolean contains(CFEdge e) {
            return edges.contains(e);
        }

        /**
         * Removes a control flow edge from the set.
         *
         * @param e Control flow edge to be removed from the set.
         *
         * @return <code>true</code> if the edge was removed from the set,
         * <code>false</code> if the set did not contain the given edge.
         */
        public boolean remove(CFEdge e) {
            return edges.remove(e);
        }

        /**
         * Gets the size of the edge set.
         *
         * @return The size of the edge set.
         */
        public int size() {
            return edges.size();
        }

        /**
         * Gets an iterator over the control flow edges in the set.
         *
         * @return An iterator over the control flow edges in the set.
         */
        public Iterator iterator() {
            return edges.iterator();
        }

        /**
         * Gets a string representation of the control flow edge set.
         */
        public String toString() {
            return edges.toString();
        }
    }
}
