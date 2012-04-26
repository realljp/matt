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
import sofya.base.exceptions.IncompleteClasspathException;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;

import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;

/**
 * The basic (conservative) type inference algorithm which only determines
 * the types of those exceptions created at the throw points and estimates
 * all types for all other thrown exceptions based on contextual information.
 *
 * <p>Types for calls and exceptions not created at the throw point are
 * estimated using the exception types caught by enclosing handlers and/or
 * declared as thrown by either the current method, and in the case of
 * calls, by the called method.</p>
 *
 * @author Alex Kinneer
 * @version 06/09/2006
 *
 * @see sofya.graphs.cfg.TypeInferrer
 */
class SimpleInference extends TypeInferenceAlgorithm {
    /** Lookup table which maps bytecode offsets to blocks, used to optimize
        retrieval of blocks. */
    private TIntObjectHashMap offsetMap = null;

    /**
     * Creates an instance of the simple type inference module.
     */
    protected SimpleInference() { }

    // JavaDoc will be inherited (see TypeInferenceAlgorithm)
    @SuppressWarnings("unchecked")
    protected void inferTypes(MethodGen mg, List<Object> blocks,
                              TIntObjectHashMap offsetMap,
                              Map<Object, Object> inferredTypeSets,
                              Map<Object, Object> precisionData)
                   throws TypeInferenceException {
        init(mg);
        this.offsetMap = offsetMap;

        TypeResult instanceType = new TypeResult(false, Type.THROWABLE);
        for (Iterator throwers = blocks.iterator(); throwers.hasNext(); ) {
            Block thrower = (Block) throwers.next();
            InstructionHandle ih = (InstructionHandle) thrower.getEndRef();
            Instruction instr = ih.getInstruction();
            SortedEdgeSet edgeSet = new SortedEdgeSet();

            switch (instr.getOpcode()) {
            case Constants.ATHROW: {
                if (inferImmediate(ih, instanceType)) {
                    CFEdge edge = new CFEdge(0, -1, thrower.getID(),
                                             instanceType.conservativeType);
                    CodeExceptionGen handler = matchHandler(ih,
                        instanceType.conservativeType);
                    if (handler != null) {
                        Block handlerBlock = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        edge.setSuccNodeID(handlerBlock.getID());
                    }
                    edgeSet.add(edge);
                    inferredTypeSets.put(thrower, edgeSet);
                    precisionData.put(thrower,
                        new TypeResult(instanceType.isPrecise,
                                       instanceType.conservativeType));
                }
                else {
                    ObjectType widestType =
                        estimateTypes(ih, thrower, edgeSet);
                    inferredTypeSets.put(thrower, edgeSet);
                    precisionData.put(thrower,
                                      new TypeResult(false, widestType));
                }
                break;
            }
            case Constants.INVOKESPECIAL:
            case Constants.INVOKEVIRTUAL:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEINTERFACE: {
                Set<Object> thrownTypes = new THashSet();
                MethodSignature invoked =
                    new MethodSignature((InvokeInstruction) instr, cpg);
                ObjectType widestType =
                    getConservativeThrown(ih, invoked, thrownTypes);

                Iterator types = thrownTypes.iterator();
                while (types.hasNext()) {
                    ObjectType objType = (ObjectType) types.next();
                    CFEdge edge = new CFEdge(0, -1, thrower.getID(), objType);
                    CodeExceptionGen handler = matchHandler(ih, objType);
                    if (handler != null) {
                        Block newSuccessor = (Block) offsetMap.get(
                            handler.getHandlerPC().getPosition());
                        edge.setSuccNodeID(newSuccessor.getID());
                    }
                    edgeSet.add(edge);
                }

                // The "<any>" edge
                CFEdge edge =
                    new CFEdge(0, -1, thrower.getID(), (ObjectType) null);
                CodeExceptionGen handler = matchHandler(ih, null);
                if (handler != null) {
                    Block newSuccessor = (Block) offsetMap.get(
                        handler.getHandlerPC().getPosition());
                    edge.setSuccNodeID(newSuccessor.getID());
                }
                edgeSet.add(edge);


                inferredTypeSets.put(thrower, edgeSet);
                precisionData.put(thrower,
                                  new TypeResult(false, widestType));
                break;
            }
            default:
                throw new TypeInferenceException("Block does not contain " +
                    "exception throwing instruction");
            }
        }
    }

    /**
     * Obtains a conservative estimate of the types of exceptions that may
     * be thrown by an <code>ATHROW</code> instruction by checking for
     * enclosing handlers and types declared as thrown by the method.
     *
     * <p>This method is an optimization which generates the edges associated
     * with the estimated types so that searches for matching handlers to
     * not have to be repeated later.</p>
     *
     * @param ih Handle to the instruction for which the conservative
     * estimate of thrown types is to be determined, must be an
     * <code>ATHROW</code> instruction.
     * @param thrower Block containing the <code>ATHROW</code> instruction.
     * @param typeEdges <strong>[out]</strong> Set to which the method
     * will put the edges created based on the conservative estimate of
     * thrown exception types.
     *
     * @return The widest class of exception which covers all of the possible
     * exception types in the conservative estimate, e.g. the first common
     * superclass.
     */
    private ObjectType estimateTypes(InstructionHandle ih,
                                     Block thrower, SortedEdgeSet typedEdges) {
        ReferenceType widestType = Type.NULL;
        boolean isFinallyHandler = false;

        int offset = ih.getPosition();
        for (int i = 0; i < exceptions.length; i++) {
            if ((exceptions[i].getStartPC().getPosition() <= offset) &&
                    (offset <= exceptions[i].getEndPC().getPosition())) {
                ObjectType exceptionType = exceptions[i].getCatchType();
                Block handlerBlock = (Block) offsetMap.get(
                    exceptions[i].getHandlerPC().getPosition());
                CFEdge edge = new CFEdge(0, handlerBlock.getID(),
                                         thrower.getID(), exceptionType);
                typedEdges.add(edge);
                // Stop if we reach a finally-block 'catch-all' handler,
                // since no subsequent handlers can be matched. Since we
                // search the handlers table exactly the same way as the
                // runtime system, we are guaranteed to estimate only
                // legal handlers
                if (exceptionType == null) {
                    isFinallyHandler = true;
                    widestType = Type.THROWABLE;
                    break;
                }
                else {
                    try {
                        widestType =
                            widestType.getFirstCommonSuperclass(exceptionType);
                    }
                    catch (ClassNotFoundException e) {
                        throw new IncompleteClasspathException(e);
                    }
                }
            }
        }

        for (int i = 0; i < thrownExceptions.length; i++) {
            CFEdge edge =
                new CFEdge(0, -1, thrower.getID(), thrownExceptions[i]);
            // We do not want to create exceptional exit edges for classes
            // of exceptions which were bound to handlers
            if (!typedEdges.contains(edge)) {
                typedEdges.add(edge);
                try {
                    widestType =
                        widestType.getFirstCommonSuperclass(
                            thrownExceptions[i]);
                }
                catch (ClassNotFoundException e) {
                    throw new IncompleteClasspathException(e);
                }
            }
            /*else {
                System.out.println("INFO: skipped handled type");
            }*/
        }

        // Add the generic "<any>" edge for runtime exceptions, etc.
        // if appropriate
        if (!isFinallyHandler) {
            typedEdges.add(
                new CFEdge(0, -1, thrower.getID(), (ObjectType) null));
        }

        return (widestType == Type.NULL) ? Type.THROWABLE
                                         : (ObjectType) widestType;
    }
}
