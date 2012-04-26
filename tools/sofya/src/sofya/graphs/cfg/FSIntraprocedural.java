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

import sofya.base.SConstants;
import sofya.base.MethodSignature;
import sofya.base.JVMStackReverser;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.LocalVariable;

import gnu.trove.THashSet;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * Implementation of the flow-sensitive intraprocedural type inference
 * algorithm as described by Sinha and Harrold (with necessary
 * interpretations).
 *
 * @author Alex Kinneer
 * @version 06/09/2006
 *
 * @see sofya.graphs.cfg.TypeInferrer
 * @see sofya.graphs.cfg.CFG
 */
@SuppressWarnings("unchecked")
class FSIntraprocedural extends TypeInferenceAlgorithm {
    /** Data structure used to record new control flow determined during
        iterative type inference. New control flow edges cannot be
        immediately added to the graph because the edges must be sorted
        based on the inheritance relationship of the exception types. */
    private Map<Object, Set<Object>> workingCF = new THashMap();

    /** Conditional compilation flag specifying whether debugging information
        is to be printed. */
    private static final boolean DEBUG = false;

    /**
     * Creates an instance of the flow-sensitive intraprocedural type
     * inference module.
     */
    public FSIntraprocedural() { }

    // JavaDoc will be inherited (see TypeInferenceAlgorithm)
    @SuppressWarnings("unchecked")
    public void inferTypes(MethodGen mg, List<Object> blocks,
                           TIntObjectHashMap offsetMap,
                           Map<Object, Object> inferredTypeSets,
                           Map<Object, Object> precisionData)
                throws TypeInferenceException {
        if (DEBUG)
            System.out.println("----- inferTypes(3) : " +
                mg.getClassName() + "." + mg.getName());

        init(mg);
        workingCF.clear();

        Map<Object, Map<Object, Object>> successorsMap = new THashMap();
        Block currentBlock = null;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Iterator throwers = blocks.iterator(); throwers.hasNext(); ) {
                currentBlock = (Block) throwers.next();
                if (DEBUG) System.out.println("  ++ inferTypes(3) " +
                    currentBlock);

                Set<Object> inferredTypes =
                    (Set) inferredTypeSets.get(currentBlock);
                if (inferredTypes == null) {
                    inferredTypes = new THashSet();
                }

                int prevSize = inferredTypes.size();
                precisionData.put(currentBlock, inferTypes(currentBlock,
                    (InstructionHandle) currentBlock.getEndRef(),
                    Type.THROWABLE, inferredTypes));
                if (prevSize == inferredTypes.size()) {
                    continue;
                }
                else {
                    inferredTypeSets.put(currentBlock, inferredTypes);
                }

                changed = true;
                for (Iterator types = inferredTypes.iterator();
                        types.hasNext(); ) {
                    ObjectType curType = (ObjectType) types.next();

                    try {
                        if (!curType.subclassOf(Type.THROWABLE)) {
                            throw new ClassFormatError("Illegal class: " +
                                "attempts to throw non-throwable object " +
                                "(" + curType + ")");
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }

                    CodeExceptionGen handler = matchHandler(
                        (InstructionHandle) currentBlock.getEndRef(),
                        curType);
                    if (handler != null) {
                        Block newSuccessor = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        if (workingCF.containsKey(newSuccessor)) {
                            workingCF.get(newSuccessor).add(currentBlock);
                        }
                        else {
                            Set<Object> predecessors = new THashSet();
                            predecessors.add(currentBlock);
                            workingCF.put(newSuccessor, predecessors);
                        }

                        if (successorsMap.containsKey(currentBlock)) {
                            successorsMap.get(currentBlock).put(curType,
                                newSuccessor);
                        }
                        else {
                            Map<Object, Object> successors = new THashMap();
                            successors.put(curType, newSuccessor);
                            successorsMap.put(currentBlock, successors);
                        }
                    }
                }
            }
        }

        for (Iterator throwers = blocks.iterator(); throwers.hasNext(); ) {
            currentBlock = (Block) throwers.next();
            Set inferred = (Set) inferredTypeSets.get(currentBlock);
            Map successors = (Map) successorsMap.get(currentBlock);
            SortedEdgeSet edges = new SortedEdgeSet();

            if (inferred != null) {
                for (Iterator iter = inferred.iterator(); iter.hasNext(); ) {
                    ObjectType objType = (ObjectType) iter.next();
                    int successorID = -1;
                    if (successors != null) {
                        Block newSuccessor = (Block) successors.get(objType);
                        successorID = (newSuccessor == null)
                                      ? -1 : newSuccessor.getID();
                    }
                    edges.add(new CFEdge(0,
                                         successorID,
                                         currentBlock.getID(),
                                         objType));
                }

                // Add the "<any>" edge for calls
                if (currentBlock.getType() == SConstants.BlockType.CALL) {
                    CFEdge edge = new CFEdge(0, -1,
                        currentBlock.getID(), (ObjectType) null);
                    CodeExceptionGen handler = matchHandler(
                        (InstructionHandle) currentBlock.getEndRef(), null);
                    if (handler != null) {
                        Block newSuccessor = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        edge.setSuccNodeID(newSuccessor.getID());
                    }
                    edges.add(edge);
                }
            }
            else {
                TypeResult res = (TypeResult) precisionData.get(currentBlock);
                ObjectType objType = res.conservativeType;

                CFEdge edge =
                    new CFEdge(0, -1, currentBlock.getID(), objType);
                CodeExceptionGen handler = matchHandler(
                    (InstructionHandle) currentBlock.getEndRef(), objType);
                if (handler != null) {
                    Block newSuccessor = (Block) offsetMap.get(
                        handler.getHandlerPC().getPosition());
                    edge.setSuccNodeID(newSuccessor.getID());
                }
                edges.add(edge);
            }

            inferredTypeSets.put(currentBlock, edges);
        }
    }

    /**************************************************************************
     * Recursive implementation of the flow-sensitive intraprocedural type
     * inference algorithm.
     *
     * @param b Basic block from which the type inference should proceed.
     * @param ih Instruction handle within the basic block from which
     * the type inference should proceed.
     * @param conservativeType Narrowest type estimate that has so far
     * been determined for the exception.
     * @param inferredTypes Working set of inferred types that have so far
     * been determined. The contents of the set may be changed by the method.
     *
     * @return A data container class which indicates whether the type
     * inference is still precise and stores the updated most conservative
     * estimate of the possible type of the exception.
     */
    @SuppressWarnings("unchecked")
    private TypeResult inferTypes(Block b, InstructionHandle ih,
                                  ObjectType conservativeType,
                                  Set<Object> inferredTypes)
                       throws TypeInferenceException {
        if (DEBUG) System.out.println("inferTypes(4): " + b);

        Instruction instr = ih.getInstruction();
        if (instr instanceof InvokeInstruction) {
            // Simple solution: get all declared thrown types + types declared
            // by enlosing handlers
            MethodSignature invoked =
                new MethodSignature((InvokeInstruction) instr, cpg);
            ObjectType conservativeThrown =
                getConservativeThrown(ih, invoked, inferredTypes);
            return new TypeResult(false, conservativeThrown);
            // Harder solution: get all types attached to exceptional exit
            // nodes for all possible bindings (in eligible classes)
            // !! Requires IRG, stabilizing iteration
            // ?? Too *inter*procedural
        }

        boolean precise = true;
        Map<Object, Object> producers = new THashMap();
        Set<Object> visited = new THashSet();
        TypeResult infProducers =
            findProducers(b, ih, conservativeType, producers, visited);
        //System.out.println(infProducers + ": " + producers.size());
        ReferenceType widestType = Type.NULL;

        if (infProducers.isPrecise) {
            producersLoop:
            for (Iterator pIt = producers.keySet().iterator();
                    pIt.hasNext(); ) {
                InstructionHandle curKey = (InstructionHandle) pIt.next();
                Block curBlock = (Block) producers.get(curKey);
                instr = curKey.getInstruction();

                conservativeType = infProducers.conservativeType;
                Object matchData = null;
                int searchCode = -1;
                switch (instr.getOpcode()) {
                case Constants.ACONST_NULL:
                    ObjectType npException = (ObjectType)
                        Type.getType("Ljava.lang.NullPointerException;");
                    inferredTypes.add(npException);
                    precise &= true;
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            npException);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    continue;
                case Constants.AALOAD:
                    // Aack, exception was thrown from array. We would have
                    // to be able to determine the index from which it was thrown,
                    // which is not feasible, so return that inference was imprecise
                    precise &= false;
                    if (DEBUG) {
                        System.out.println("  imprecise: thrown from array " +
                            "reference");
                    }
                    widestType = Type.THROWABLE;
                    continue;
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
                            // A local variable index is sometimes reused for
                            // different purposes in the same method, so only
                            // use the officially listed type in the local
                            // variable if it is a throwable type
                            ObjectType lvoType = (ObjectType) lvType;
                            try {
                                if (lvoType.subclassOf(conservativeType)) {
                                    conservativeType = lvoType;
                                }
                            }
                            catch (ClassNotFoundException e) {
                                throw new IncompleteClasspathException(e);
                            }
                        }
                    }
                    matchData = new Integer(lvIndex);
                    searchCode = Constants.ASTORE;
                    break;
                case Constants.GETFIELD:
                    FieldInstruction fieldRef = (FieldInstruction) instr;
                    matchData = fieldRef.getSignature(cpg);
                    ObjectType fieldType =
                        (ObjectType) Type.getType((String) matchData);
                    try {
                        if (fieldType.subclassOf(conservativeType)) {
                            conservativeType = fieldType;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    searchCode = Constants.PUTFIELD;
                    break;
                case Constants.GETSTATIC:
                    fieldRef = (FieldInstruction) instr;
                    matchData = fieldRef.getSignature(cpg);
                    fieldType = (ObjectType) Type.getType((String) matchData);
                    try {
                        if (fieldType.subclassOf(conservativeType)) {
                            conservativeType = fieldType;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    searchCode = Constants.PUTSTATIC;
                    break;
                case Constants.INVOKESPECIAL:
                    INVOKESPECIAL invokeSpecial = (INVOKESPECIAL) instr;
                    ObjectType classType =
                        (ObjectType) invokeSpecial.getReferenceType(cpg);
                    try {
                        if (invokeSpecial.getMethodName(cpg).equals("<init>")
                                && classType.subclassOf(Type.THROWABLE)) {
                            inferredTypes.add(classType);
                            precise &= true;
                            widestType = widestType.getFirstCommonSuperclass(
                                classType);
                            continue;
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
                    precise &= false;
                    if (DEBUG) {
                        System.out.println("  imprecise: value produced " +
                            "by method call");
                    }
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
                    continue;
                }

                Map<Object, Object> assigns = new THashMap();
                visited.clear();
                if (!findAssigns(curBlock, curKey, searchCode, matchData,
                                 assigns, visited)) {
                    precise &= false;
                    if (DEBUG) {
                        System.out.println("  imprecise: could not locate " +
                            "all assignments for: " + curKey);
                    }
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            conservativeType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }

                for (Iterator aIt = assigns.keySet().iterator();
                        aIt.hasNext(); ) {
                    curKey = (InstructionHandle) aIt.next();
                    curBlock = (Block) assigns.get(curKey);
                    TypeResult inferred = inferTypes(curBlock, curKey,
                        conservativeType, inferredTypes);
                    try {
                        widestType = widestType.getFirstCommonSuperclass(
                            inferred.conservativeType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                    if (!inferred.isPrecise) {
                        precise &= false;
                        if (DEBUG) {
                            System.out.println("  imprecise: inference on " +
                                "assignment failed");
                        }
                        continue producersLoop;
                    }
                    else {
                        precise &= true;
                    }
                }
            }

            if (widestType == Type.NULL) {
                widestType = Type.THROWABLE;
            }

            return new TypeResult(precise, (ObjectType) Type.getType(
                widestType.getSignature()));
        }

        if (DEBUG) {
            System.out.println("  imprecise: could not locate all producers");
        }

        return new TypeResult(false, infProducers.conservativeType);
    }

    /**************************************************************************
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
    @SuppressWarnings("unchecked")
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

            Set<Object> workingPredecessors = workingCF.get(b);
            if (workingPredecessors == null)
                workingPredecessors = new THashSet();
            int count = b.getPredecessorCount() + workingPredecessors.size();
            if (count == 0) {
                // We reached the start of the method without finding
                // a producer
                throw new ClassFormatError("Method consumes from empty stack");
            }
            else {
                // Search predecessors
                workingPredecessors.addAll(b.getPredecessorsList());
                for (Iterator it = workingPredecessors.iterator();
                        it.hasNext(); ) {
                    Block predBlock = (Block) it.next();
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

    /**************************************************************************
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
    @SuppressWarnings("unchecked")
    private TypeResult findProducers(Block b, InstructionHandle startHandle,
            JVMStackReverser rStack, ObjectType conservativeType,
            Map<Object, Object> producers, Set<Object> visited) {
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

                if (opcode == Constants.CHECKCAST) {
                    ObjectType castType =
                        ((CHECKCAST) instr).getLoadClassType(cpg);
                    try {
                        if (castType.subclassOf(conservativeType)) {
                            conservativeType = castType;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }
                else {
                    producers.put(cur_ih, b);
                    return new TypeResult(true, conservativeType);
                }
            }

            if (cur_ih == b.getStartRef()) {
                CodeExceptionGen handler;
                //if ((startHandle.getInstruction() instanceof ASTORE) &&
                //        ((handler = getHandler(startHandle)) != null)) {
                if (rStack.atPossibleProducer()
                        && ((handler = getHandler(cur_ih)) != null)) {
                    ObjectType catchType = handler.getCatchType();
                    return new TypeResult(false, (catchType == null)
                                                 ? Type.THROWABLE
                                                 : catchType);
                }

                Set<Object> predecessors = new THashSet(b.getPredecessorsList());
                Set<Object> workingPredecessors = workingCF.get(b);
                if (workingPredecessors != null) {
                    predecessors.addAll(workingPredecessors);
                }
                if (predecessors.size() == 0) {
                    // We reached the start of the method without finding
                    // a producer
                    throw new ClassFormatError("Method consumes from " +
                                               "empty stack");
                }
                else if (predecessors.size() == 1) {
                    // A single predecessor means linear control flow - allow
                    // narrowing of the conservative type estimate as we
                    // continue backward
                    Block predBlock = (Block) predecessors.iterator().next();
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
                    for (Iterator it = predecessors.iterator(); it.hasNext(); ) {
                        Block predBlock = (Block) it.next();
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

    /**************************************************************************
     * Performs a reverse control flow search for all of the instructions
     * which assign a value to a given local, instance, or class variable.
     *
     * @param b Basic block from which the search should proceed.
     * @param startHandle Instruction within the basic block from which the
     * search should proceed.
     * @param instrType BCEL opcode constant indicating the type of variable
     * store instruction the search is attempting to locate (local, instance,
     * or class variable).
     * @param criteria Data used to determine whether a store instruction
     * refers to the correct variable. When searching for a local variable
     * store, this value is the integer indicating the index of the local
     * variable. Otherwise it is the signature of the instance or class
     * field.
     * @param assigns Map which records all the instructions which may
     * store to the given variable as keys to the basic blocks in which
     * they are found. This is the primary output of the method.
     * @param visited Set which records blocks that have already been
     * visited to avoid duplication of effort.
     *
     * @return A boolean value indicating whether the type inference is
     * still precise. The inference becomes imprecise if assignments to
     * the variable cannot be found on all reverse flow paths.
     */
    @SuppressWarnings("unchecked")
    private boolean findAssigns(Block b, InstructionHandle startHandle,
                                int instrType, Object criteria,
                                Map<Object, Object> assigns,
                                Set<Object> visited) {
        if (DEBUG) System.out.println("findAssigns: " + b);
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

            // If we hit a call we stop, since we cannot account for the
            // possibility that it assigns to a class or instance variable
            // without analyzing it, and this is an intraprocedural analysis
            // only. (Local variables are excluded, because their values
            // cannot be changed interprocedurally).
            if ((instrType != Constants.ASTORE)
                    && (instr instanceof InvokeInstruction)) {
                if (DEBUG) System.out.println("  imprecise: encountered call");
                return false;
            }

            switch (instrType) {
            case Constants.ASTORE:
                if (instr instanceof ASTORE) {
                    ASTORE storeInstr = (ASTORE) instr;
                    int lvIndex = ((Integer) criteria).intValue();
                    if (storeInstr.getIndex() == lvIndex) {
                        assigns.put(cur_ih, b);
                        // If the type being stored to the local variable can
                        // be inferred precisely, the type inference will be
                        // precise along this path
                        return true;
                    }
                }
                break;
            case Constants.PUTFIELD:
                if (instr instanceof PUTFIELD) {
                    PUTFIELD putField = (PUTFIELD) instr;
                    if (putField.getSignature(cpg).equals((String) criteria)) {
                        assigns.put(cur_ih, b);
                        return true;
                    }
                }
                break;
            case Constants.PUTSTATIC:
                if (instr instanceof PUTSTATIC) {
                    PUTSTATIC putStatic = (PUTSTATIC) instr;
                    if (putStatic.getSignature(cpg).equals(
                            (String) criteria)) {
                        assigns.put(cur_ih, b);
                        return true;
                    }
                }
                break;
            default:
                throw new SofyaError("Opcode " + instrType + " does not " +
                    "identify a variable assignment instruction");
            }

            if (cur_ih == b.getStartRef()) {
                Set<Object> workingPredecessors = workingCF.get(b);
                if (workingPredecessors == null)
                    workingPredecessors = new THashSet();
                int count =
                    b.getPredecessorCount() + workingPredecessors.size();
                if ((count == 0) && (cur_ih.getPrev() == null)) {
                    // The start of the method was reached, so the type
                    // inference cannot be precise. (If searching for
                    // an ASTORE, we assume that the ALOAD
                    // which triggered the search must reference an
                    // argument to the method)
                    if (DEBUG) System.out.println("  imprecise: reached " +
                                                  "start of method");
                    return false;
                }
                else {
                    // There are predecessors, continue searching
                    // the paths recursively.
                    workingPredecessors.addAll(b.getPredecessorsList());
                    boolean isPrecise = true;
                    for (Iterator it = workingPredecessors.iterator();
                            it.hasNext(); ) {
                        Block predBlock = (Block) it.next();
                        isPrecise &= findAssigns(predBlock,
                            (InstructionHandle) predBlock.getEndRef(),
                            instrType,
                            criteria,
                            assigns,
                            visited);
                    }
                    if (DEBUG && !isPrecise) {
                        System.out.println("  imprecise: could not find " +
                                           "all assignments");
                    }
                    return isPrecise;
                }
            }

            cur_ih = cur_ih.getPrev();
        }
    }

    /**************************************************************************
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
        for (int i = 0; i < exceptions.length; i++) {
            if (exceptions[i].getHandlerPC() == ih) {
                return exceptions[i];
            }
        }
        return null;
    }

    /**
     * Stack class used to keep track of the current position in a
     * set of nested finallys.
     *
     * <p>This class is not currently used by the type inferencer
     * but is retained for potential future use.</p>
     */
    @SuppressWarnings("unused")
    private static class JsrStack {
        private ArrayList<Object> stack;
        private boolean unmatchedRet = false;

        public JsrStack() {
            stack = new ArrayList<Object>(3);
        }

        public JsrStack(int initSize) {
            stack = new ArrayList<Object>(initSize);
        }

        public void push(InstructionHandle jsr) {
            stack.add(jsr);
        }

        public InstructionHandle pop() {
            if (stack.size() == 0)
                throw new IllegalStateException("Empty stack");
            return (InstructionHandle) stack.remove(stack.size() - 1);
        }

        public InstructionHandle top() {
            if (stack.size() == 0)
                throw new IllegalStateException("Empty stack");
            return (InstructionHandle) stack.get(stack.size() - 1);
        }

        public void setInFinally(boolean b) {
            unmatchedRet = b;
        }

        public boolean isInFinally() {
            return unmatchedRet;
        }
    }
}
