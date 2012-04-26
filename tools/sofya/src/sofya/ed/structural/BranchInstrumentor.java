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

package sofya.ed.structural;

import java.util.Collections;
import java.util.Set;
import java.util.Iterator;
import java.io.IOException;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.exceptions.*;
import sofya.graphs.Graph;
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.CFEdge;
import static sofya.base.SConstants.*;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import gnu.trove.THashSet;

/**
 * The Sofya branch edge instrumentor.  Classes instrumented by this
 * instrumentor can be executed by event dispatchers using branch
 * processing strategies.
 *
 * @author Alex Kinneer
 * @version 11/21/2006
 *
 * @see sofya.ed.cfInstrumentor
 */
public class BranchInstrumentor extends StructuralInstrumentor {
    /** Exception handlers associated with the method currently being
        instrumented. */
    protected CodeExceptionGen[] exceptionHandlers;

    /** &quot;Null&quot; branch handle - setting the target of this handle
        is meaningless; this can be used to avoid tests inside of loops that
        would only be necessary on the first iteration. */
    private BranchHandle nullBh =
        (new InstructionList()).append(new GOTO(null));

    // The summary throw handler uses the following flag values to decide
    // what action to take. A flag value of 0 causes the handler to take
    // no action other than rethrowing the exception.
    /** Flag value to indicate that the summary throw handler should witness
        the summary exit branch. */
    private static final int SUMMARY_EXIT_FLAG = 1;
    /** Flag value to indicate that the summary throw handler should add
        a method exit marker to the sequence array, if sequence tracing. */
    private static final int EXC_EXIT_CALL_FLAG = 2;

    /*************************************************************************
     * Protected default constructor, to prevent unsafe instances of
     * the instrumentor from being created.
     */
    protected BranchInstrumentor() { }

    /*************************************************************************
     * <b>See</b>
     * {@link StructuralInstrumentor#StructuralInstrumentor(String[])}.
     */
    public BranchInstrumentor(String[] argv)
           throws IllegalArgumentException, IOException,
                  ClassFormatError, Exception {
        super(argv);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * using the default port.
     *
     * @param className Name of the class to be instrumented.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public BranchInstrumentor(String className)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, 0x00000000);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * using the given port.
     *
     * @param className Name of the class to be instrumented.
     * @param port Port to which instrumentation should be set. The valid
     * range is 1024 to 65535.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public BranchInstrumentor(String className, int port)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, 0x00000000, port);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * using the given port and activating the specified mode of
     * instrumentation.
     *
     * @param className Name of the class to be instrumented.
     * @param port Port to which instrumentation should be set. The valid
     * range is 1024 to 65535.
     * @param instMode Integer flag indicating the form of instrumentation to
     * be inserted. Acceptable values are the following:
     * <ul>
     * <li>{@link sofya.base.SConstants#INST_COMPATIBLE}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_NORMAL}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_SEQUENCE}</li>
     * </ul>
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public BranchInstrumentor(String className, int port, int instMode)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, 0x00000000, port, instMode);
    }

    /*************************************************************************
     * The branch instrumentor does not filter instrumentation based on
     * object types, so this method prints a warning and takes no action.
     *
     * @return <code>true</code>, always.
     */
    protected boolean parseTypeCodes(String typeCodes) {
        System.err.println("WARNING: Branch instrumentor does not use " +
            "trace object types - parameter will be ignored");
        return true;
    }

    /*************************************************************************
     * The branch instrumentor does not filter instrumentation based on
     * object types, so this method throws an exception.
     *
     * @throws UnsupportedOperationException Always.
     */
    public int getTypeFlags() {
        throw new UnsupportedOperationException();
    }

    /*************************************************************************
     * The branch instrumentor does not filter instrumentation based on
     * object types, so this method throws an exception.
     *
     * @throws UnsupportedOperationException Always.
     */
    public void setTypeFlags(int typeCodes) {
        throw new UnsupportedOperationException();
    }

    /*************************************************************************
     * Reports that this instrumentor operates on branch edges.
     *
     * @return The type code for the branch edge entity.
     */
    protected TraceObjectType getObjectType() {
        return TraceObjectType.BRANCH_EDGE;
    }

    protected String getProbeClassName() {
        return "sofya.ed.structural.Sofya$$Probe$10_36_3676__";
    }

    // JavaDoc will be inherited
    protected Method instrument(Method m, int methodIndex,
                                boolean insertStarter) {
        if (m.isNative() || m.isAbstract()) {
            return m;
        }
        
        MethodGen mg = new MethodGen(m, fullClassName, cpg);
        InstructionList il = mg.getInstructionList();

        if (il.isEmpty()) return m;

        //mSignature = Handler.formatSignature(fullClassName, m.toString(), '@');
        mSignature = Handler.packSignature(fullClassName, m);

        MethodSignature sigKey = new MethodSignature(mg);
        try {
            methodCFG = classGraphs.getCFG(sigKey);
        }
        catch (MethodNotFoundException e) {
            try {
                methodCFG = classGraphs.getCFG(mSignature.replace('@', '.'));
            }
            catch (MethodNotFoundException e2) {
                throw new SofyaError("Could not load CFG for method", e2);
            }
        }

        InstructionHandle origStart = il.getStart();
        InstructionHandle origEnd = il.getEnd();
        exceptionHandlers = mg.getExceptionHandlers();

        Block[] methodBlocks = methodCFG.getBasicBlocks();
        loadBlockRefs(methodBlocks, il);

        arrayVarref = mg.getMaxLocals();
        int exceptionVar = mg.getMaxLocals() + 1;
        excExitBooleanVar = mg.getMaxLocals() + 2;
        mg.setMaxLocals(mg.getMaxLocals() + 3);

        for (int i = 0; i < methodBlocks.length; i++) {
            BlockType blockType = methodBlocks[i].getType();

            switch (blockType.toInt()) {
            case BlockType.IENTRY: {
                CFEdge[] es = (CFEdge[]) methodCFG.getEdges(methodBlocks[i],
                    Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
                insertUnaryProbe(es[0].getBranchID(0).getID(), il,
                    (InstructionHandle) methodBlocks[i].getStartRef(),
                    BranchType.IENTRY);
                break;
              }
            case BlockType.IEXIT:
                if (instMode != INST_OPT_SEQUENCE) {
                    continue;
                }
                BlockSubType subType = methodBlocks[i].getSubType();
                if ((subType == BlockSubType.THROW)
                        || (subType == BlockSubType.SUMMARYTHROW)) {
                    // Ignore exceptional exits, they are handled at the
                    // throw and call nodes
                    continue;
                }
                // Instrument the end of all predecessor nodes to the exit
                // block
                sofya.graphs.Node[] predNodes =
                    methodBlocks[i].getPredecessors();
                Block predBlock;
                for (int j = 0; j < predNodes.length; j++) {
                    predBlock = (Block) predNodes[j];
                    // Exit block is virtual, so we must instrument the
                    // predecessor blocks,
                    // however the instrumentation statement should still
                    // identify the node ID of the virtual block that
                    // caused the instrumentation, NOT the block that
                    // actually got instrumented!
                    InstructionHandle ih =
                        (InstructionHandle) predBlock.getEndRef();
                    updateTargeters(ih, addExitMarker(il, ih, true),
                        Collections.EMPTY_SET);
                }
                break;
            case BlockType.IRETURN:
                continue;
            case BlockType.IBLOCK:
                switch (methodBlocks[i].getSubType().toInt()) {
                case BlockSubType.ITHROW:
                    // If a throw has only one successor, type inference
                    // has precisely identified that only one type of
                    // exception will ever be thrown, so it isn't a branch
                    if (methodBlocks[i].getSuccessorCount() == 1) {
                        // If it's thrown from the method, we need to
                        // mark it as a known exception so that
                        // the 'summary' exception edge doesn't get
                        // witnessed
                        Block successor =
                            (Block) methodBlocks[i].getSuccessors()[0];
                        if (successor.getType() == BlockType.EXIT) {
                            InstructionList patch = new InstructionList();
                            patch.append(new ICONST(0));
                            patch.append(new ISTORE(excExitBooleanVar));
                            if (instMode == INST_OPT_SEQUENCE) {
                                addExitMarker(patch, null, false);
                            }
                            il.insert((InstructionHandle)
                                methodBlocks[i].getEndRef(), patch);
                            patch.dispose();
                        }
                        break;
                    }
                    insertThrowerBranches(methodBlocks[i], il,
                        (InstructionHandle) methodBlocks[i].getEndRef(),
                        exceptionVar, BranchType.THROW);
                    break;
                case BlockSubType.ISWITCH:
                case BlockSubType.IIF:
                	if(methodBlocks[i].getSuccessorCount() > 1) {
                        insertProbe(methodBlocks[i], il,
                            (InstructionHandle) methodBlocks[i].getEndRef());
                	    break;
                    }
                	else if(methodBlocks[i].getSuccessorCount() == 1 && methodBlocks[i].getSubType().toInt() == BlockSubType.ISWITCH)
                		continue;  // handle JavaCC generated switch/case with only default case
            
                	else
                		throw new SofyaError("Invalid control flow: switch/if has " +
                            "fewer than two successors");
                default:
                    continue;
                }
                break;
            case BlockType.ICALL:
                // Calls should always have at least the "<r>" and
                // "<any>" edges
                if (methodBlocks[i].getSuccessorCount() < 2) {
                	throw new SofyaError("Invalid control flow: call has " +
                    "fewer than one successors");
                }

                insertCallHandler(methodBlocks[i], mg, il,
                    (InstructionHandle) methodBlocks[i].getEndRef(),
                    exceptionVar);

                break;
            default:
                 throw new SofyaError("Unrecognized block type in control " +
                     "flow graph");
            }
        }

        // Insert call to method that should be executed on any method entry
        // (if any)
        if (methodEntryCall != null) {
            InstructionList patch = new InstructionList();
            patch.append(instFieldLoad);
            switch (instMode) {
            case INST_COMPATIBLE:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodCFG.getNumberOfBranches()));
                patch.append(methodEntryCall);
                break;
            case INST_OPT_NORMAL:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodCFG.getNumberOfBranches()));
                patch.append(methodEntryCall);
                patch.append(new ASTORE(arrayVarref));
                break;
            case INST_OPT_SEQUENCE:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodCFG.getNumberOfBranches()));
                patch.append(methodEntryCall);
                break;
            }
            il.insert(il.getStart(), patch);
            il.setPositions();
            patch.dispose();
        }

        // If explicitly asked to insert call to SocketProbe.start
        if (insertStarter) {
            if (starterInserted) {
                System.err.println("Warning: start call already inserted!");
            }
            insertProbeStartCall(il);
            starterInserted = true;
        }
        // Otherwise, check to see whether the call to SocketProbe.start still
        // needs to be inserted, and insert it if we are in the appropriate
        // method.
        else if ((classHasMain || classHasStaticInit) && !starterInserted) {
            if (classHasStaticInit) {
                if (m.getName().equals("<clinit>")) {
                    insertProbeStartCall(il);
                    starterInserted = true;
                    // if (classIsFilter) {
                    //     insertInstFlag(il);
                    // }
                }
            }
            else if (m.getName().equals("main")) {
                insertProbeStartCall(il);
                starterInserted = true;
            }
        }

        // Remove and re-add all exception handlers previously attached
        // to the method, which causes all of the handlers which have been
        // added to raise call return events on exceptional return to bind
        // first (those handlers then transfer control back to the existing
        // handlers). This would be unnecessary if BCEL could *insert*
        // new exception handlers...
        for (int i = 0; i < exceptionHandlers.length; i++) {
            CodeExceptionGen handler = exceptionHandlers[i];
            mg.removeExceptionHandler(handler);
            mg.addExceptionHandler(handler.getStartPC(),
                                   handler.getEndPC(),
                                   handler.getHandlerPC(),
                                   handler.getCatchType());
        }

        // Add the handler to catch all 'summarized' exceptions
        // (operator, array-bounds, etc...). Effectively a
        // try-finally wrapped around the whole method.
        il.insert(origStart, new ICONST(SUMMARY_EXIT_FLAG));
        il.insert(origStart, new ISTORE(excExitBooleanVar));
        addSummaryThrowHandler(mg, il, origStart, origEnd, exceptionVar);

        if (classHasMain && m.getName().equals("main")) {
            // Attach special handler to emulate how unhandled exceptions
            // propagating from main are reported, deterministically on one
            // output stream
            addDefaultHandler(mg, il);

            // Ensure all trace data is sent by inserting calls to 'finish()'
            // immediately prior to every return instruction. The default
            // handler just inserted ensures that every exceptional exit is
            // also covered, because it will fall through the last return in
            // the method. Doing this is necessary because a normal exit is
            // 1) guaranteed to return from main, and 2) does not cause a
            // call to Runtime.exit() or Runtime.halt().
            if ((instMode == INST_OPT_NORMAL)
                    || (instMode == INST_OPT_SEQUENCE)) {
                insertProbeFinishCall(il);
            }
        }

        // Now if the subject is a probe class, ensure a call to the
        // alternately-named probe class's finish() method is inserted
        // into this probe's finish() method - which is effectively the
        // final exit point from the probe class. We won't worry about
        // exceptional exits here because if finish is throwing an
        // exception, something is terribly wrong in Sofya anyway.
        if (implementsProbeIfc && m.getName().equals("finish")) {
            insertProbeFinishCall(il);
        }

        // Insert the new instruction list into the method
        mg.setInstructionList(il);

        // As we have added new method calls (trace prints) to the
        // method, the operand stack size needs to be recalculated.
        // This is done by BCEL using control-flow analysis.
        mg.setMaxStack();

        Method mInstrumented = mg.getMethod();
        il.dispose();

        lastInstrumented = m.getName();
        return mInstrumented;
    }

    /*************************************************************************
     * Inserts the basic bytecode sequence to witness a single branch
     * edge.
     *
     * <p>This method is used to insert a probe where only a single branch
     * ID is possible. This includes the &quot;entry&quot; branch, the
     * <code>&lt;normal&gt;</code> branch from calls (the other probes are
     * inserted in the attached exception handler), and the &quot;summary
     * throw&quot; branch.</p>
     *
     * <p>The exact bytecode that is inserted will depend on the
     * instrumentation mode.</p>
     *
     * @param branchID Branch ID witnessed by the probe.
     * @param il BCEL instruction list into which the probe is to be
     * inserted.
     * @param insert_ih Instruction handle at which the probe is to be
     * inserted in <code>il</code>.
     * @param edgeType The type of the branch edge witnessed by the probe.
     *
     * @return The instruction handle of the first instruction in the
     * inserted probe.
     */
    protected InstructionHandle insertUnaryProbe(int branchID,
            InstructionList il, InstructionHandle insert_ih, int edgeType) {
        InstructionList patch = new InstructionList();
        switch (instMode) {
        case INST_COMPATIBLE:
            patch.append(instFieldLoad);
            patch.append(new PUSH(cpg, (edgeType << 26) + branchID));
            patch.append(new PUSH(cpg, mSignature));
            patch.append(traceMethodCall);
            break;
        case INST_OPT_NORMAL:
            patch.append(new ALOAD(arrayVarref));
            patch.append(new PUSH(cpg, branchID - 1));
            patch.append(new PUSH(cpg, edgeType));
            patch.append(new BASTORE());
            break;
        case INST_OPT_SEQUENCE:
            patch.append(iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.append(iFactory.createGetStatic(seqArrayRef,
                "sequenceArray", new ArrayType(Type.INT, 1)));
            patch.append(new ARRAYLENGTH());
            BranchHandle bh = patch.append(new IF_ICMPLT(null));
            patch.append(instFieldLoad);
            patch.append(traceMethodCall);
            bh.setTarget(patch.append(iFactory.createGetStatic(
                 seqArrayRef, "sequenceArray", new ArrayType(Type.INT, 1))));
            patch.append(iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.append(new DUP());
            patch.append(new ICONST(1));
            patch.append(new IADD());
            patch.append(iFactory.createPutStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.append(new PUSH(cpg, (edgeType << 26) + branchID));
            patch.append(new IASTORE());
            break;
        default:
            throw new ConfigurationError("Unknown or unsupported form of " +
                                         "instrumentation");
        }

        InstructionHandle ret_ih = il.insert(insert_ih, patch);
        patch.dispose();
        return ret_ih;
    }

    /*************************************************************************
     * Inserts the bytecode sequence to witness the execution of one edge
     * out of a set of branch edges.
     *
     * <p>The exact bytecode that is inserted will depend on the
     * instrumentation mode.</p>
     *
     * @param node Block at which the branching control flow occurs.
     * @param il Instruction list for the method into which the probe
     * is to be inserted.
     * @param ih Instruction handle before which the probe is to be
     * inserted.
     */
    protected void insertProbe(Block node, InstructionList il,
                               InstructionHandle ih) {
        InstructionList patch = new InstructionList();
        Instruction instr = ih.getInstruction();
        InstructionHandle retarget = ih;

        CFEdge[] edges = (CFEdge[]) methodCFG.getEdges(node,
            Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
        switch (node.getSubType().toInt()) {
        case BlockSubType.ISWITCH: {
            BranchType T_SWITCH = BranchType.SWITCH;
            Select switchInstr = (Select) instr;
            Select switchCopy = null;

            // The copy method in BCEL only returns a shallow copy,
            // and there are no deep-copy constructors.  So it
            // must be done manually...
            switch (switchInstr.getOpcode()) {
            case Constants.LOOKUPSWITCH:
                switchCopy = new LOOKUPSWITCH(switchInstr.getMatchs(),
                                              switchInstr.getTargets(),
                                              switchInstr.getTarget());
                break;
            case Constants.TABLESWITCH:
                switchCopy = new TABLESWITCH(switchInstr.getMatchs(),
                                             switchInstr.getTargets(),
                                             switchInstr.getTarget());
                break;
            default:
                throw new SofyaError("Invalid node: switch subtype " +
                    "is inconsistent with opcode");
            }

            switch (instMode) {
            case INST_COMPATIBLE:
                patch.append(instFieldLoad);
                patch.append(new SWAP());
                break;
            case INST_OPT_NORMAL:
                patch.append(new ALOAD(arrayVarref));
                patch.append(new SWAP());
                break;
            case INST_OPT_SEQUENCE:
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceArray", new ArrayType(Type.INT, 1)));
                patch.append(new ARRAYLENGTH());
                BranchHandle bh = patch.append(new IF_ICMPLT(null));
                patch.append(instFieldLoad);
                patch.append(traceMethodCall);
                bh.setTarget(patch.append(iFactory.createGetStatic(
                    seqArrayRef, "sequenceArray",
                    new ArrayType(Type.INT, 1))));
                patch.append(new SWAP());
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new DUP());
                patch.append(new ICONST(1));
                patch.append(new IADD());
                patch.append(iFactory.createPutStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new SWAP());
                break;
            }
            retarget = patch.append(switchCopy);

            InstructionHandle[] targets = switchInstr.getTargets();
            int[] matchVals = switchInstr.getMatchs();
            for (int i = 0; i < targets.length; i++) {
                CFEdge matchingEdge = null;
                for (int j = 0; j < edges.length; j++) {
                    if (Integer.parseInt(edges[j].getLabel()) == matchVals[i]) {
                        matchingEdge = edges[j];
                        break;
                    }
                }
                if (matchingEdge == null) {
                    throw new SofyaError("Invalid CFG: could not find " +
                        "matching edge label for switch case");
                }
                assert matchingEdge.getBranchIDSet().size() == 1 :
                    "Branch edge has improper number of IDs";
                assert matchingEdge.getBranchID(0).getID() <= 67108863 :
                    "Branch ID is too large (> (2 ^ 26) - 1)";

                targets[i].removeTargeter(switchInstr);

                // Note that when we retrieve the branch ID, we get the one
                // associated with the current branch type, since some
                // 'sideloops' may also be propagating IDs associated with
                // an enclosing edge of a different type. See also notes on
                // this issue in the BranchFlowProcessor and CFEdge.
                InstructionHandle newCase = null;
                switch (instMode) {
                case INST_COMPATIBLE:
                    newCase = patch.append(new PUSH(cpg,
                        (BranchType.ISWITCH << 26) +
                        matchingEdge.getBranchID(T_SWITCH).getID()));
                    patch.append(new PUSH(cpg, mSignature));
                    patch.append(traceMethodCall);
                    break;
                case INST_OPT_NORMAL:
                    newCase = patch.append(new PUSH(cpg,
                        matchingEdge.getBranchID(T_SWITCH).getID() - 1));
                    patch.append(new PUSH(cpg, BranchType.ISWITCH));
                    patch.append(new BASTORE());
                    break;
                case INST_OPT_SEQUENCE:
                    newCase = patch.append(new PUSH(cpg,
                        (BranchType.ISWITCH << 26) +
                        matchingEdge.getBranchID(T_SWITCH).getID()));
                    patch.append(new IASTORE());
                    break;
                }
                GOTO jumpToCase = new GOTO(targets[i]);
                patch.append(jumpToCase);
                targets[i].addTargeter(jumpToCase);
                switchCopy.setTarget(i, newCase);
            }

            InstructionHandle target = switchInstr.getTarget();
            target.removeTargeter(switchInstr);
            CFEdge matchingEdge = null;
            for (int i = 0; i < edges.length; i++) {
                if (edges[i].getLabel().equalsIgnoreCase("default")) {
                    matchingEdge = edges[i];
                    break;
                }
            }
            if (matchingEdge == null) {
                throw new SofyaError("Invalid CFG: could not find " +
                    "matching edge label for switch case");
            }
            assert matchingEdge.getBranchIDSet().size() == 1 :
                "Branch edge has improper number of IDs";
            assert matchingEdge.getBranchID(T_SWITCH).getID() <= 67108863 :
                "Branch ID is too large (> (2 ^ 26) - 1)";

            InstructionHandle newDefault = null;
            switch (instMode) {
            case INST_COMPATIBLE:
                newDefault = patch.append(new PUSH(cpg,
                    (BranchType.ISWITCH << 26) +
                    matchingEdge.getBranchID(T_SWITCH).getID()));
                patch.append(new PUSH(cpg, mSignature));
                patch.append(traceMethodCall);
                break;
            case INST_OPT_NORMAL:
                newDefault = patch.append(new PUSH(
                    cpg, matchingEdge.getBranchID(T_SWITCH).getID() - 1));
                patch.append(new PUSH(cpg, BranchType.ISWITCH));
                patch.append(new BASTORE());
                break;
            case INST_OPT_SEQUENCE:
                newDefault = patch.append(new PUSH(cpg,
                    (BranchType.ISWITCH << 26) +
                    matchingEdge.getBranchID(T_SWITCH).getID()));
                patch.append(new IASTORE());
                break;
            }
            switchCopy.setTarget(newDefault);
            GOTO jumpToCase = new GOTO(target);
            patch.append(jumpToCase);
            target.addTargeter(jumpToCase);

            InstructionHandle temp = ih.getNext();
            try {
                il.delete(ih);
            }
            catch (TargetLostException e) {
                InstructionHandle[] iTargets = e.getTargets();
                for (int i = 0; i < iTargets.length; i++) {
                    InstructionTargeter[] targeters =
                        iTargets[i].getTargeters();
                    for (int j = 0; j < targeters.length; j++) {
                        targeters[j].updateTarget(iTargets[i], retarget);
                    }
                }
            }
            ih = temp;
            break;
          }
        case BlockSubType.IIF: {
            BranchType T_IF = BranchType.IF;
            
            switch (instr.getOpcode()) {
            case Constants.IFEQ:
            case Constants.IFNE:
            case Constants.IFLT:
            case Constants.IFGE:
            case Constants.IFGT:
            case Constants.IFLE:
            case Constants.IFNULL:
            case Constants.IFNONNULL:
                patch.append(new DUP());
                break;
            default:
                patch.append(new DUP2());
                break;
            }
            BranchHandle brCopy =
                 patch.append((BranchInstruction) instr.copy());

            // Sanity checks (for now)
            assert edges.length == 2 : "More than 2 edges of out 'if'";

            if ((edges[0].getBranchIDSet().size() != 1) ||
                    (edges[1].getBranchIDSet().size() != 1)) {
                //System.err.println("WARNING: More than one ID on " +
                //    "'if' branch - ignoring all but first. This is\n" +
                //    "likely caused by an exception handler " +
                //    "associated with a call that is ignored\n" +
                //    "in the CFG");
            }
            if ((edges[0].getBranchID(T_IF).getID() > 67108863) ||
                    (edges[1].getBranchID(T_IF).getID() > 67108863)) {
                throw new SofyaError("Branch ID is too large " +
                                       "(> (2 ^ 26) - 1)");
            }

            int trueID, falseID;
            if (edges[0].getLabel().equalsIgnoreCase("f")) {
                trueID = edges[1].getBranchID(T_IF).getID();
                falseID = edges[0].getBranchID(T_IF).getID();
            }
            else {
                trueID = edges[0].getBranchID(T_IF).getID();
                falseID = edges[1].getBranchID(T_IF).getID();
            }

            BranchHandle elseJump = null;
            switch (instMode) {
            case INST_COMPATIBLE:
            case INST_OPT_SEQUENCE:
                patch.append(new PUSH(cpg, (BranchType.IIF << 26) + falseID));
                elseJump = patch.append(new GOTO(null));
                brCopy.setTarget(patch.append(
                    new PUSH(cpg, (BranchType.IIF << 26) + trueID)));
                break;
            case INST_OPT_NORMAL:
                patch.append(new PUSH(cpg, falseID - 1));
                elseJump = patch.append(new GOTO(null));
                brCopy.setTarget(patch.append(new PUSH(cpg, trueID - 1)));
                break;
            }

            switch (instMode) {
            case INST_COMPATIBLE:
                elseJump.setTarget(patch.append(instFieldLoad));
                patch.append(new SWAP());
                patch.append(new PUSH(cpg, mSignature));
                patch.append(traceMethodCall);
                break;
            case INST_OPT_NORMAL:
                elseJump.setTarget(patch.append(new ALOAD(arrayVarref)));
                patch.append(new SWAP());
                patch.append(new PUSH(cpg, BranchType.IIF));
                patch.append(new BASTORE());
                break;
            case INST_OPT_SEQUENCE:
                elseJump.setTarget(patch.append(iFactory.createGetStatic(
                     seqArrayRef, "sequenceIndex", Type.INT)));
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceArray", new ArrayType(Type.INT, 1)));
                patch.append(new ARRAYLENGTH());
                BranchHandle bh = patch.append(new IF_ICMPLT(null));
                patch.append(instFieldLoad);
                patch.append(traceMethodCall);
                bh.setTarget(patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceArray", new ArrayType(Type.INT, 1))));
                patch.append(new SWAP());
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new DUP());
                patch.append(new ICONST(1));
                patch.append(new IADD());
                patch.append(iFactory.createPutStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new SWAP());
                patch.append(new IASTORE());
                break;
            }
            break;
          }
        default:
            throw new IllegalArgumentException("Node type is not " +
                "appropriate for branch instrumentation");
        }

        InstructionHandle new_ih = il.insert(ih, patch);
        patch.dispose();

        updateTargeters(retarget, new_ih, Collections.EMPTY_SET);
    }

    /*************************************************************************
     * Inserts probe to properly witness a branch edge executed as a result
     * of exceptional control flow.
     *
     * <p>The method constructs the bytecode equivalent of an
     * <code>if-elseif-...-elseif</code> which detects the class of exception
     * using <code>instanceof</code> and pushes the correct branch ID onto
     * the stack. A single action to record/transmit the branch ID is at the
     * end, followed by the original throw instruction. This mechanism is
     * also used indirectly for call nodes, which calls this method to create
     * the contents of the intercept handler that is attached to the
     * invoke instruction.</p>
     *
     * @param b Block containing the throw instruction for which the probe
     * is to be inserted.
     * @param il The instruction list of the method, into which the
     * probe is to be inserted.
     * @param ih Instruction in front of which the probe is to be
     * inserted - this should normally be an <code>ATHROW</code>
     * instruction.
     * @param localVar Index of the local variable which will be used for
     * temporary storage of the exception object.
     * @param edgeType The type of the branch edges witnessed by the probe.
     */
    @SuppressWarnings("unchecked")
    protected void insertThrowerBranches(Block b, InstructionList il,
            InstructionHandle ih, int localVar, BranchType edgeType) {
        int typeCode = edgeType.toInt();

        CFEdge[] brEdges = (CFEdge[]) methodCFG.getEdges(b,
            Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
        assert brEdges.length > 0 : "No edges out of node";

        InstructionList patch = new InstructionList();
        Set<Object> correctTargeters = new THashSet();
        Set<Object> endGotos = new THashSet();

        InstructionHandle insert_ih;
        if ((ih.getPrev() != null)
                && (ih.getPrev().getInstruction() instanceof ALOAD)) {
            localVar = ((ALOAD) ih.getPrev().getInstruction()).getIndex();
            insert_ih = ih.getPrev();
        }
        else {
            insert_ih = il.insert(ih, new ALOAD(localVar));
            patch.append(new ASTORE(localVar));
        }

        BranchHandle lastIf = nullBh;
        int edgeIndex;
        for (edgeIndex = 0; edgeIndex < brEdges.length; edgeIndex++) {
            CFEdge curEdge = brEdges[edgeIndex];
            String edgeLabel = curEdge.getLabel();

            if (edgeLabel.equals("<r>")) {
                continue;
            }
            else if (edgeLabel.equals("<any>")) {
                edgeLabel = "java.lang.Throwable";
            }

            assert curEdge.getBranchIDSet().size() <= 1 :
                "Assert failed: branch has more than 1 ID: " +
                brEdges[edgeIndex];
            assert curEdge.getBranchID(edgeType).getID() <= 67108863 :
                "Branch ID is too large (> (2 ^ 26) - 1)";

            int classRef = cpg.addClass(edgeLabel);

            Block successor = methodCFG.getBlock(curEdge.getSuccNodeID());
            boolean isExit = successor.getType() == BlockType.EXIT;

            lastIf.setTarget(patch.append(new ALOAD(localVar)));
            patch.append(new INSTANCEOF(classRef));
            lastIf = patch.append(new IFEQ(null));

            if (isExit || (edgeType != BranchType.CALL)) {
                if (isExit) {
                    if (edgeType == BranchType.CALL) {
                        patch.append(new ICONST(EXC_EXIT_CALL_FLAG));
                        patch.append(new ISTORE(excExitBooleanVar));
                    }
                    else {
                        patch.append(new ICONST(0));
                        patch.append(new ISTORE(excExitBooleanVar));
                        if (instMode == INST_OPT_SEQUENCE) {
                            addExitMarker(patch, null, false);
                        }
                    }
                }

                switch (instMode) {
                case INST_COMPATIBLE:
                case INST_OPT_SEQUENCE:
                    patch.append(new PUSH(cpg, (typeCode << 26) +
                        curEdge.getBranchID(edgeType).getID()));
                    break;
                default:
                    patch.append(new PUSH(cpg,
                        curEdge.getBranchID(edgeType).getID() - 1));
                    patch.append(new PUSH(cpg, typeCode));
                    break;
                }
                endGotos.add(patch.append(new GOTO(null)));
            }
            else {
                InstructionHandle handlerPC = (InstructionHandle)
                    successor.getStartRef();
                InstructionHandle jih = patch.append(new ALOAD(localVar));
                patch.append(new CHECKCAST(classRef));
                patch.append(new GOTO(handlerPC));
                insertUnaryProbe(curEdge.getBranchID(edgeType).getID(),
                    patch, jih, typeCode);
            }
        }

        lastIf.setTarget(insert_ih);
        correctTargeters.add(lastIf.getInstruction());

        if (endGotos.size() > 0) {
            // Some stack manipulation to execute the trace commit action
            // depending on the instrumentation mode
            InstructionHandle gotoTarget;
            switch (instMode) {
            case INST_COMPATIBLE:
                gotoTarget = patch.append(instFieldLoad);
                patch.append(new SWAP());
                patch.append(new PUSH(cpg, mSignature));
                patch.append(traceMethodCall);
                break;
            case INST_OPT_NORMAL:
                gotoTarget = patch.append(new ALOAD(arrayVarref));
                patch.append(new DUP_X2());
                patch.append(new POP());
                patch.append(new BASTORE());
                break;
            case INST_OPT_SEQUENCE:
                // For some additional intuition regarding what the
                // following sequence is doing to the Java stack, see
                // footer comment #1 in BlockInstrumentor.java.
                gotoTarget = patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceArray", new ArrayType(Type.INT, 1)));
                patch.append(new ARRAYLENGTH());
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                BranchHandle skipIf = patch.append(new IF_ICMPGT(null));
                patch.append(instFieldLoad);
                patch.append(traceMethodCall);
                skipIf.setTarget(patch.append(iFactory.createGetStatic(
                    seqArrayRef, "sequenceArray",
                    new ArrayType(Type.INT, 1))));
                patch.append(new SWAP());
                patch.append(iFactory.createGetStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new DUP());
                patch.append(new ICONST(1));
                patch.append(new IADD());
                patch.append(iFactory.createPutStatic(seqArrayRef,
                    "sequenceIndex", Type.INT));
                patch.append(new SWAP());
                patch.append(new IASTORE());
                break;
            default:
                throw new ConfigurationError("Unknown or unsupported form " +
                    "of instrumentation");
            }

            for (Iterator i = endGotos.iterator(); i.hasNext(); ) {
                BranchHandle theGoto = (BranchHandle) i.next();
                theGoto.setTarget(gotoTarget);
            }
        }

        InstructionHandle new_ih = il.insert(insert_ih, patch);
        il.setPositions();
        patch.dispose();

        updateTargeters(insert_ih, new_ih, correctTargeters);
    }

    /*************************************************************************
     * Inserts an intercepting exception handler to detect and record
     * exceptions propagated by called methods, including those which are
     * rethrown.
     *
     * <p>A 'catch-all' exception handler (equivalent to those created for
     * <code>finally</code> blocks) is attached to the call instruction.
     * The catch block for the handler contains probes to witness any of
     * the exceptional branches. This method also inserts the probe to witness
     * the normal return from the method. Note that on the normal (non-
     * exceptional) path of execution, the handler is never invoked and
     * execution continues as usual.</p>
     *
     * @param b Block containing the call to which the handler is being
     * attached.
     * @param mg The BCEL mutable representation of the method, required to
     * manipulate the exception handlers associated with the method.
     * @param il Bytecode instruction list for the method being instrumented.
     * @param ih Handle to the call instruction.
     * @param localVar Index of the local variable used for temporary storage
     * of the exception object in the inserted handler.
     */
    protected CodeExceptionGen insertCallHandler(Block b, MethodGen mg,
            InstructionList il, InstructionHandle ih, int localVar) {
        InstructionHandle origEnd = ih.getNext();

        // Get the normal branch ID
        CFEdge[] edges = (CFEdge[]) methodCFG.getEdges(b,
            Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
        int normalBranchID = -1;
        for (int i = 0 ; i < edges.length; i++) {
            if (edges[i].getLabel().equals("<r>")) {
                assert edges[i].getBranchIDSet().size() <= 1 :
                    "Branch has more than 1 ID: " + edges[i];
                normalBranchID = edges[i].getBranchID(0).getID();
            }
        }
        if (normalBranchID == -1) {
            throw new SofyaError("Invalid control flow: no normal path " +
                "edge was found out of call node");
        }
        else if (normalBranchID > 67108863) {
            throw new SofyaError("Branch ID is too large (> (2 ^ 26) - 1)");
        }

        // Insert GOTO that will jump handler by default
        BranchHandle bh = il.insert(origEnd, new GOTO(null));

        // Build handler instruction sequence
        InstructionList handlerCode = new InstructionList();
        InstructionHandle insert_ih = handlerCode.append(new ATHROW());
        insertThrowerBranches(b, handlerCode, insert_ih,
                              localVar, BranchType.CALL);

        // Insert handler instructions
        InstructionHandle handlerStart = il.insert(origEnd, handlerCode);

        bh.setTarget(
            insertUnaryProbe(normalBranchID, il, origEnd,
                             BranchType.ICALL));

        il.setPositions();
        handlerCode.dispose();

        // Add handler - null catches any type
        return mg.addExceptionHandler(ih, ih, handlerStart, (ObjectType) null);
    }

    /*************************************************************************
     * Attaches an intercepting exception handler to the method which
     * marks the summary exceptional exit branch to signify exceptional
     * exit from the method caused by any type of unchecked exception for
     * which precise control flow modeling is too costly (such as operator
     * and array-related exceptions).
     *
     * @param mg The BCEL mutable representation of the method, required to
     * manipulate the exception handlers associated with the method.
     * @param il The instruction list of the method, into which the
     * exceptional exit probe code will be inserted. This parameter is also
     * required since the instruction list is in a working state and will
     * not necessarily have been committed to the MethodGen object provided
     * in the <code>mg</code> parameter.
     * @param regStart Original start instruction of the method (before any
     * modifications by the instrumentor). It cannot be the very first
     * instruction of the method, because by the time this method is called,
     * all of the instrumentation should have been inserted. Some modes
     * of instrumentation require an array reference which is retrieved
     * by the call invoked on method entry. If the handler watch region
     * encloses that call, then the reference will be considered out of
     * scope of the exception handler and it won't be able to use it to
     * mark the edge.
     * @param regEnd Original end instruction of the method. Used to
     * avoid interactions with other handlers added by the instrumentor.
     * @param exceptionVar Index of the local variable which will be used for
     * temporary storage of the exception object in the handler.
     */
    protected void addSummaryThrowHandler(MethodGen mg, InstructionList il,
            InstructionHandle regStart, InstructionHandle regEnd,
            int exceptionVar) {
        if (methodCFG.getSummaryBranchID() > 67108863) {
            throw new SofyaError("Branch ID is too large (> (2 ^ 26) - 1)");
        }

        // Insert GOTO that will jump handler by default
        InstructionHandle origEnd = il.insert(regEnd, new GOTO(regEnd));
        if (regStart == regEnd) { regStart = origEnd; }

        // Build handler instruction sequence
        InstructionList handlerCode = new InstructionList();
        handlerCode.append(new ASTORE(exceptionVar));
        handlerCode.append(new ILOAD(excExitBooleanVar));

        TABLESWITCH flagSwitch;
        InstructionHandle defaultCase = handlerCode.append(
            new ALOAD(exceptionVar));
        InstructionHandle caseOne = insertUnaryProbe(
            methodCFG.getSummaryBranchID(), handlerCode,
            defaultCase, BranchType.IOTHER);
        if (instMode == INST_OPT_SEQUENCE) {
            InstructionHandle caseTwo = addExitMarker(handlerCode,
                defaultCase, true);
            flagSwitch = new TABLESWITCH(new int[]{1, 2},
                new InstructionHandle[]{caseOne, caseTwo}, defaultCase);
        }
        else {
            flagSwitch = new TABLESWITCH(new int[]{1},
                new InstructionHandle[]{caseOne}, defaultCase);
        }
        handlerCode.insert(caseOne, flagSwitch);

        handlerCode.append(new ATHROW());

        // Insert handler instructions
        InstructionHandle handlerStart = il.insert(regEnd, handlerCode);
        il.setPositions();

        // Add handler - null catches any type
        mg.addExceptionHandler(regStart, origEnd, handlerStart,
            (ObjectType) null);
        handlerCode.dispose();
    }

    /*************************************************************************
     * Inserts the bytecode sequence to add a method exit marker to the
     * sequence array.
     *
     * <p>Naturally, this method should not be called when not instrumenting
     * for sequence tracing.</p>
     *
     * @param il Instruction list for the method into which the bytecode
     * is to be inserted.
     * @param ih Instruction handle before which the bytecode is to be
     * inserted; ignored if the <code>insert</code> parameter is
     * <code>true</code>.
     * @param insert <code>true</code> to insert the bytecode before
     * the given instruction handle in the instruction list,
     * <code>false</code> to append the bytecode to the end of the
     * instruction list.
     *
     * @return The instruction handle of the first instruction in the added
     * bytecode.
     */
    private InstructionHandle addExitMarker(InstructionList il,
            InstructionHandle ih, boolean insert) {
        InstructionList patch;
        if (insert) {
            patch = new InstructionList();
        }
        else {
            patch = il;
        }

        InstructionHandle first_ih = patch.append(
            iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
        patch.append(iFactory.createGetStatic(seqArrayRef,
            "sequenceArray", new ArrayType(Type.INT, 1)));
        patch.append(new ARRAYLENGTH());
        BranchHandle bh = patch.append(new IF_ICMPLT(null));
        patch.append(instFieldLoad);
        patch.append(traceMethodCall);
        bh.setTarget(patch.append(iFactory.createGetStatic(
             seqArrayRef, "sequenceArray", new ArrayType(Type.INT, 1))));
        patch.append(iFactory.createGetStatic(seqArrayRef,
            "sequenceIndex", Type.INT));
        patch.append(new DUP());
        patch.append(new ICONST(1));
        patch.append(new IADD());
        patch.append(iFactory.createPutStatic(seqArrayRef,
            "sequenceIndex", Type.INT));
        patch.append(new PUSH(cpg,
            Sofya$$Probe$10_36_3676__.BRANCH_EXIT_MARKER));
        patch.append(new IASTORE());

        if (insert) {
            il.insert(ih, patch);
            patch.dispose();
        }
        return first_ih;
    }

    /*************************************************************************
     * Helper method which updates all the targeters of a particular
     * instruction without incorrectly modifying the watch region of
     * exception handlers.
     *
     * @param oldTarget Instruction handle for which the targeters need to
     * be updated.
     * @param newTarget Instruction handle which is to be made the new target
     * of all instructions targeting <code>oldTarget</code>.
     * @param ignoreSet Targeters found in this set are not updated. This
     * is used in some cases to ignore targeters which have already been
     * correctly updated.
     */
    private void updateTargeters(InstructionHandle oldTarget,
                                 InstructionHandle newTarget,
                                 Set ignoreSet) {
        InstructionTargeter[] targeters = oldTarget.getTargeters();
        if (targeters != null) {
            for (int t = 0; t < targeters.length; t++) {
                /* We only want to update the start offsets to _handler_ blocks
                   of exception handlers. The offsets to the start and end
                   instructions of the region watched for exceptions should not
                   be changed, otherwise there are circumstances where we may
                   actually shift the watched region such that the code that is
                   supposed to be protected in fact no longer is. */
                if (targeters[t] instanceof CodeExceptionGen) {
                    CodeExceptionGen exceptionHandler =
                        (CodeExceptionGen) targeters[t];
                    if ((exceptionHandler.getStartPC() == oldTarget) ||
                        (exceptionHandler.getEndPC() == oldTarget)) {
                        continue;
                    }
                }
                else if (ignoreSet.contains(targeters[t])) {
                    continue;
                }
                targeters[t].updateTarget(oldTarget, newTarget);
            }
        }
    }
}
