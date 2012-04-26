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

import java.io.IOException;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.exceptions.*;
import sofya.graphs.Graph;
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.CFEdge;
import static sofya.base.SConstants.*;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

/**
 * The Sofya basic block instrumentor.  Classes instrumented by this
 * instrumentor can be executed by event dispatchers using basic block
 * processing strategies.
 *
 * @author Alex Kinneer
 * @version 02/28/2007
 *
 * @see sofya.ed.cfInstrumentor
 */
public class BlockInstrumentor extends StructuralInstrumentor {
    /** Number of blocks in the method currently referenced by
        instrumentation. */
    protected int blockCount;
    
    protected GlobalIndexer globalIndexer = null;

    /*************************************************************************
     * Protected default constructor, to prevent unsafe instances of
     * the instrumentor from being created.
     */
    protected BlockInstrumentor() { }

    /*************************************************************************
     * <b>See</b>
     * {@link StructuralInstrumentor#StructuralInstrumentor(String[])}.
     */
    public BlockInstrumentor(String[] argv)
           throws IllegalArgumentException, IOException,
                  ClassFormatError, Exception {
        super(argv);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * with the given block types activated for instrumentation and using the
     * default port.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit mask representing the types of blocks to be
     * instrumented. Can be any bitwise combination of the following
     * (See {@link sofya.base.SConstants}):
     * <ul>
     * <li><code>SConstants.BlockType.MASK_BASIC</code></li>
     * <li><code>SConstants.BlockType.MASK_ENTRY</code></li>
     * <li><code>SConstants.BlockType.MASK_EXIT</code></li>
     * <li><code>SConstants.BlockType.MASK_CALL</code></li>
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
    public BlockInstrumentor(String className, int typeFlags)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, typeFlags);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * with the given block types activated for instrumentation and using the
     * given port.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit vector representing the types of blocks to be
     * instrumented (see {@link #BlockInstrumentor(String,int)}).
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
    public BlockInstrumentor(String className, int typeFlags, int port)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, typeFlags, port);
    }

    /*************************************************************************
     * Standard constructor, constructs instrumentor for the specified class
     * with the given block types activated for instrumentation, using the
     * given port, and activating the specified mode of instrumentation.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit vector representing the types of blocks to be
     * instrumented. (see {@link #BlockInstrumentor(String,int)}).
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
    public BlockInstrumentor(String className, int typeFlags,
                             int port, int instMode)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        super(className, typeFlags, port, instMode);
    }

    protected boolean parseTypeCodes(String typeCodes)
                      throws IllegalArgumentException {
        boolean typesSet = false;
        for (int j = 1; j < typeCodes.length(); j++) {
            switch(typeCodes.charAt(j)) {
            case 'B':
                typeFlags |= BlockType.MASK_BASIC;
                break;
            case 'E':
                typeFlags |= BlockType.MASK_ENTRY;
                break;
            case 'X':
                typeFlags |= BlockType.MASK_EXIT;
                break;
            case 'C':
                typeFlags |= BlockType.MASK_CALL;
                break;
            case 'R':
                typeFlags |= BlockType.MASK_RETURN;
                break;
            default:
                throw new IllegalArgumentException("Invalid trace type");
            }
            typesSet = true;
        }
        return typesSet;
    }

    /*************************************************************************
     * Sets the bit vector controlling what block types are to be instrumented
     * by this instrumentor.
     *
     * @param typeFlags Bit mask representing the types of blocks to be
     * instrumented. Can be any bitwise combination of the following
     * (See {@link sofya.base.SConstants}):
     * <ul>
     * <li><code>SConstants.BlockType.MASK_BASIC</code></li>
     * <li><code>SConstants.BlockType.MASK_ENTRY</code></li>
     * <li><code>SConstants.BlockType.MASK_EXIT</code></li>
     * <li><code>SConstants.BlockType.MASK_CALL</code></li>
     * </ul>
     *
     * @throws IllegalArgumentException If the bit vector doesn't have a set
     * bit which corresponds to a valid block type.
     */
    public void setTypeFlags(int typeFlags) throws IllegalArgumentException {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type specified");
        }
        this.typeFlags = typeFlags;
    }

    /*************************************************************************
     * Reports that this instrumentor operates on basic blocks.
     *
     * @return The type code for the basic block entity.
     */
    protected TraceObjectType getObjectType() {
        return TraceObjectType.BASIC_BLOCK;
    }

    protected String getProbeClassName() {
        // This method is called from init(), which is guaranteed not
        // to be called before instMode is set.
        if (instMode != INST_TRACE_HASHING) {
            return "sofya.ed.structural.Sofya$$Probe$10_36_3676__";
        }
        else {
            return "sofya.ed.structural.HashingProbe";
        }
    }

    /*************************************************************************
     * Reports whether the instrumentor is currently set to instrument basic
     * blocks.
     *
     * @return <code>true</code> if basic blocks are to be instrumented,
     * <code>false</code> otherwise.
     */
    public boolean isTypeBasic() {
        return (typeFlags & BlockType.MASK_BASIC) != 0;
    }

    /*************************************************************************
     * Sets whether the instrumentor is to instrument basic blocks.
     *
     * @param enable <code>true</code> to enable basic block instrumentation,
     * <code>false</code> to disable.
     */
    public void setTypeBasic(boolean enable) {
        typeFlags = (enable) ? typeFlags | BlockType.MASK_BASIC
                             : typeFlags & 0xFFFFFFFE;
    }

    /*************************************************************************
     * Reports whether the instrumentor is currently set to instrument entry
     * blocks.
     *
     * @return <code>true</code> if entry blocks are to be instrumented,
     * <code>false</code> otherwise.
     */
    public boolean isTypeEntry() {
        return (typeFlags & BlockType.MASK_ENTRY) != 0;
    }

    /*************************************************************************
     * Sets whether the instrumentor is to instrument entry blocks.
     *
     * @param enable <code>true</code> to enable entry block instrumentation,
     * <code>false</code> to disable.
     */
    public void setTypeEntry(boolean enable) {
        typeFlags = (enable) ? typeFlags | BlockType.MASK_ENTRY
                             : typeFlags & 0xFFFFFFFD;
    }

    /*************************************************************************
     * Reports whether the instrumentor is currently set to instrument exit
     * blocks.
     *
     * @return <code>true</code> if exit blocks are to be instrumented,
     * <code>false</code> otherwise.
     */
    public boolean isTypeExit() {
        return (typeFlags & BlockType.MASK_EXIT) != 0;
    }

    /*************************************************************************
     * Sets whether the instrumentor is to instrument exit blocks.
     *
     * @param enable <code>true</code> to enable exit block instrumentation,
     * <code>false</code> to disable.
     */
    public void setTypeExit(boolean enable) {
        typeFlags = (enable) ? typeFlags | BlockType.MASK_EXIT
                             : typeFlags & 0xFFFFFFFB;
    }

    /*************************************************************************
     * Reports whether the instrumentor is currently set to instrument call
     * blocks.
     *
     * @return <code>true</code> if call blocks are to be instrumented,
     * <code>false</code> otherwise.
     */
    public boolean isTypeCall() {
        return (typeFlags & BlockType.MASK_CALL) != 0;
    }

    /*************************************************************************
     * Sets whether the instrumentor is to instrument call blocks.
     *
     * @param enable <code>true</code> to enable call block instrumentation,
     * <code>false</code> to disable.
     */
    public void setTypeCall(boolean enable) {
        typeFlags = (enable) ? typeFlags | BlockType.MASK_CALL
                             : typeFlags & 0xFFFFFFF7;
    }

    /*************************************************************************
     * Reports whether the instrumentor is currently set to instrument return
     * blocks.
     *
     * @return <code>true</code> if return blocks are to be instrumented,
     * <code>false</code> otherwise.
     */
    @SuppressWarnings("unused")
    private boolean isTypeReturn() {
        return (typeFlags & BlockType.MASK_RETURN) != 0;
    }

    /*************************************************************************
     * Sets whether the instrumentor is to instrument return blocks.
     *
     * @param enable <code>true</code> to enable return block instrumentation,
     * <code>false</code> to disable.
     */
    @SuppressWarnings("unused")
    private void setTypeReturn(boolean enable) {
        typeFlags = (enable) ? typeFlags | BlockType.MASK_RETURN
                             : typeFlags & 0xFFFFFFEF;
    }
    
    @Override
    protected void init() throws IOException {
        super.init();
        if ((instMode == INST_TRACE_HASHING) && (globalIndexer == null)) {
            globalIndexer = new GlobalIndexer();
        }
    }

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

        Block[] methodBlocks = methodCFG.getBasicBlocks();
        blockCount = methodBlocks.length;
        Block summaryThrowNode = null;

        // Reload the instruction handle references in the blocks from the
        // instruction list, using the offsets. This is done before any changes
        // are made to the instruction list, because it is strongly suspected
        // that the lookup is faster under this circumstance (BCEL provides two
        // methods for lookup). More importantly, the working instruction list
        // doesn't have to be searched during instrumentation of every block
        // below, which was found to yield approximately a 10% speedup.
        loadBlockRefs(methodBlocks, il);

        // Create an extra local variable which will be used for the temporary
        // storage of exception objects when adding exceptional exit nodes
        arrayVarref = mg.getMaxLocals();
        int exceptionVar = mg.getMaxLocals() + 1;
        excExitBooleanVar = mg.getMaxLocals() + 2;
        mg.setMaxLocals(mg.getMaxLocals() + 3);

        // Iterate over the blocks in the method
        InstructionHandle ih;
        for (int i = 0; i < methodBlocks.length; i++) {
            switch (methodBlocks[i].getType().toInt()) {
            case BlockType.IBLOCK:
                // Basic block instrumentation
                if ((typeFlags & BlockType.MASK_BASIC)
                        == BlockType.MASK_BASIC) {
                    ih = (InstructionHandle) methodBlocks[i].getStartRef();
                    insertProbe(methodBlocks[i], il, ih);
                }
                // Add exceptional exits to throw nodes if appropriate
                if ((methodBlocks[i].getSubType() == BlockSubType.THROW) &&
                       ((typeFlags & BlockType.MASK_EXIT) ==
                          BlockType.MASK_EXIT)) {
                    ih = (InstructionHandle) methodBlocks[i].getEndRef();
                    insertExitNodes(methodBlocks[i], il, ih, exceptionVar);
                }
                break;
            case BlockType.IENTRY:
                // Entry instrumentation
                if ((typeFlags & BlockType.MASK_ENTRY) ==
                        BlockType.MASK_ENTRY) {
                    sofya.graphs.Node[] succNodes =
                        methodBlocks[i].getSuccessors();
                    Block succBlock;
                    for (int j = 0; j < succNodes.length; j++){
                        succBlock = (Block) succNodes[j];
                        ih = (InstructionHandle) succBlock.getStartRef();
                        // Entry block is virtual, so we must instrument the
                        // successor blocks, however the instrumentation
                        // statement should still identify the node ID of the
                        // virtual block that caused the instrumentation, NOT
                        // the block that actually got instrumented!
                        insertProbe(methodBlocks[i], il, ih);
                    }
                }
                break;
            case BlockType.IEXIT:
                // Exit instrumentation
                if (((typeFlags & BlockType.MASK_EXIT) == BlockType.MASK_EXIT)
                        || (instMode == INST_OPT_SEQUENCE)) {
                    if (methodBlocks[i].getSubType() == BlockSubType.THROW) {
                        // Ignore exceptional exits, they are handled at the
                        // throw and call nodes
                        continue;
                    }
                    if (methodBlocks[i].getSubType()
                            == BlockSubType.SUMMARYTHROW) {
                        if (summaryThrowNode != null) {
                            System.err.println("WARNING: Multiple summary " +
                                "nodes for exceptional exits encountered " +
                                "(using last one)");
                        }
                        summaryThrowNode = methodBlocks[i];
                        continue;
                    }
                    // Instrument the end of all predecessor nodes to the exit
                    // block
                    sofya.graphs.Node[] predNodes =
                        methodBlocks[i].getPredecessors();
                    Block predBlock;
                    for (int j = 0; j < predNodes.length; j++) {
                        predBlock = (Block) predNodes[j];
                        ih = (InstructionHandle) predBlock.getEndRef();
                        // Exit block is virtual, so we must instrument the
                        // predecessor blocks,
                        // however the instrumentation statement should still
                        // identify the node ID of the virtual block that
                        // caused the instrumentation, NOT the block that
                        // actually got instrumented!
                        insertProbe(methodBlocks[i], il, ih);
                    }
                }
                break;
            case BlockType.ICALL:
                // Call instrumentation
                if ((typeFlags & BlockType.MASK_CALL) == BlockType.MASK_CALL) {
                    ih = (InstructionHandle) methodBlocks[i].getStartRef();
                    insertProbe(methodBlocks[i], il, ih);
                }
                // Attach the intercepting handler for exceptions so we can
                // detect exceptional exits resulting from the propagation of
                // exceptions by the called method
                if ((typeFlags & BlockType.MASK_EXIT) == BlockType.MASK_EXIT) {
                    if (methodBlocks[i].getSuccessorCount() > 1) {
                        InstructionHandle last_ih =
                            (InstructionHandle) methodBlocks[i].getEndRef();
                        insertCallHandler(methodBlocks[i], mg, il, last_ih,
                            exceptionVar);
                    }
                }
                break;
            case BlockType.IRETURN:
                // Return instrumentation
                if ((typeFlags & BlockType.MASK_RETURN)
                        == BlockType.MASK_RETURN) {
                    ih = ((InstructionHandle) methodBlocks[i].getEndRef())
                         .getNext();
                    insertProbe(methodBlocks[i], il, ih);
                }
                break;
            }
        }
        il.setPositions();

        // Insert call to method that should be executed on any method entry
        // (if any)
        if (methodEntryCall != null) {
            InstructionList patch = new InstructionList();
            patch.append(instFieldLoad);
            switch (instMode) {
            case INST_COMPATIBLE:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodBlocks.length));
                patch.append(methodEntryCall);
                break;
            case INST_OPT_NORMAL:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodBlocks.length));
                patch.append(methodEntryCall);
                patch.append(new ASTORE(arrayVarref));
                break;
            case INST_OPT_SEQUENCE:
                patch.append(new PUSH(cpg, mSignature));
                patch.append(new PUSH(cpg, methodBlocks.length));
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
                    //    insertInstFlag(il);
                    // }
                }
            }
            else if (m.getName().equals("main")) {
                insertProbeStartCall(il);
                starterInserted = true;
            }
        }

        // Add the handler to catch all 'summarized' exceptions
        // (operator, array-bounds, etc...). Effectively a
        // try-finally wrapped around the whole method.
        if (summaryThrowNode != null) {
            il.insert(origStart, new ICONST(0));
            il.insert(origStart, new ISTORE(excExitBooleanVar));
            addSummaryThrowHandler(summaryThrowNode, mg, il, origStart,
                                   origEnd, exceptionVar);
        }

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
            // 1) guaranteed to return from main, and 2) does not cause a call
            // to Runtime.exit() or Runtime.halt().
            insertProbeFinishCall(il);
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
     * Inserts the bytecode sequence to witness a block.
     *
     * <p>The exact bytecode that is inserted will depend on the
     * instrumentation mode.</p>
     *
     * @param node Block which will be marked in the trace.
     * @param il Instruction list for the method into which the trace code
     * (instrumentation) is being inserted.
     * @param ih Instruction handle before which the trace code is to be
     * inserted.
     */
    protected void insertProbe(Block node, InstructionList il,
                               InstructionHandle ih) {
        InstructionList patch = new InstructionList();

        if (node.getType().toInt() > 63) {
            throw new SofyaError("Block type code is too large (> 63)");
        }
        else if (node.getID() > 67108863) {
            throw new SofyaError("Block ID is too large (> (2 ^ 26) - 1)");
        }

        switch (instMode) {
        case INST_COMPATIBLE:
            patch.append(instFieldLoad);
            patch.append(new PUSH(cpg, (node.getType().toInt() << 26) +
                                       node.getID()));
            patch.append(new PUSH(cpg, mSignature));
            patch.append(traceMethodCall);
            break;
        case INST_OPT_NORMAL:
            patch.append(new ALOAD(arrayVarref));
            patch.append(new PUSH(cpg, node.getID() - 1));
            patch.append(new PUSH(cpg, (byte) node.getType().toInt()));
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
            bh.setTarget(patch.append(iFactory.createGetStatic(seqArrayRef,
               "sequenceArray", new ArrayType(Type.INT, 1))));
            patch.append(iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.append(new DUP());
            patch.append(new ICONST(1));
            patch.append(new IADD());
            patch.append(iFactory.createPutStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.append(new PUSH(cpg, (node.getType().toInt() << 26) +
                node.getID()));
            patch.append(new IASTORE());
            break;
        case INST_TRACE_HASHING:
            int nodeIndex = globalIndexer.indexFor(mSignature, node.getID());
            patch.append(new PUSH(cpg, nodeIndex));
            patch.append(traceMethodCall);
            break;
        }
        InstructionHandle new_ih = il.insert(ih, patch);
        patch.dispose();

        // Do not update targeters if one of the following two conditions is
        // true:
        // 1) It is an entry block - it should only be traversed once per
        //    method entry
        // 2) It is a return block - it should only be traversed immediately
        //    following the preceding call instruction
        if ((node.getType() == BlockType.ENTRY)
                || (node.getType() == BlockType.RETURN)) {
            return;
        }

        // Otherwise, update targeters, which includes all affected branches
        // and exception handlers
        InstructionTargeter[] targeters = ih.getTargeters();
        if (targeters != null) {
            for (int t = 0; t < targeters.length; t++) {
                /* We only want to update the start offsets to _handler_ blocks
                   of exception handlers. The offsets to the start and end
                   instructions of the region watched for exceptions should not
                   be changed, otherwise there are circumstances where we may
                   actually shift the watched region such that the code that
                   is supposed to be protected in fact no longer is. */
                if (targeters[t] instanceof CodeExceptionGen) {
                    CodeExceptionGen exceptionHandler =
                        (CodeExceptionGen) targeters[t];
                    if ((exceptionHandler.getStartPC() == ih) ||
                        (exceptionHandler.getEndPC() == ih)) {
                        continue;
                    }
                }
                targeters[t].updateTarget(ih, new_ih);
            }
        }
    }

    /*************************************************************************
     * Inserts instrumentation to properly simulate exceptional exit nodes
     * attached to <code>ATHROW</code> instructions.
     *
     * <p>The method constructs the bytecode equivalent of an
     * <code>if-elseif-...-elseif</code> which detects the class of exception
     * using <code>instanceof</code> and pushes the correct exit node ID onto
     * the stack. A single action to record/transmit the node ID is at the
     * end, followed by the original throw instruction. This mechanism is
     * also used indirectly for call nodes, which calls this method to create
     * the contents of the intercept handler that is attached to the
     * invoke instruction.</p>
     *
     * @param b Block containing the throw instruction for which associated
     * exceptional exit nodes are being created.
     * @param il The instruction list of the method, into which the
     * exceptional exit node code will be inserted.
     * @param ih Instruction in front of which the exceptional exit node code
     * will be inserted - this should normally be an <code>ATHROW</code>
     * instruction.
     * @param localVar Index of the local variable which will be used for
     * temporary storage of the exception object.
     */
    protected void insertExitNodes(Block b, InstructionList il,
                                   InstructionHandle ih, int localVar) {
        Block[] succNodes = (Block[]) b.getSuccessors(Block.ZL_ARRAY);
        if (succNodes.length == 0) return;
        outer: {
            for (int i = 0; i < succNodes.length; i++) {
                if (succNodes[i].getType() == BlockType.EXIT) {
                    break outer;
                }
            }
            return;
        }

        CFEdge[] edges = (CFEdge[]) methodCFG.getEdges(b,
            Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
        InstructionList patch = new InstructionList();
        int classRef, n;
        boolean loadAdded = false;

        // The bytecode sequence is constructed in reverse, which makes the
        // following code a bit strange to read, but makes it much more easy
        // and efficient to set the necessary jump targets for branches and
        // gotos.
        InstructionHandle insert_ih, end_ih = null;
        if ((ih.getPrev() != null)
                && (ih.getPrev().getInstruction() instanceof ALOAD)) {
            localVar = ((ALOAD) ih.getPrev().getInstruction()).getIndex();
            insert_ih = ih.getPrev();
        }
        else {
            loadAdded = true;
            insert_ih = il.insert(ih, new ALOAD(localVar));
        }

        // Set flag to indicate an exceptional exit has occurred. The handler
        // associated with the exceptional exit summary node checks this
        // flag and does not mark the summary node if it is set.
        patch.insert(new ISTORE(excExitBooleanVar));
        patch.insert(new ICONST(1));

        // Some stack manipulation to execute the trace commit action
        // depending on the instrumentation mode
        switch (instMode) {
        case INST_COMPATIBLE:
            patch.insert(traceMethodCall);
            end_ih = patch.insert(new PUSH(cpg, mSignature));
            break;
        case INST_OPT_NORMAL:
            patch.insert(new BASTORE());
            patch.insert(new POP());
            patch.insert(new DUP_X2());
            end_ih = patch.insert(new ALOAD(arrayVarref));
            break;
        case INST_OPT_SEQUENCE:
            // For a somewhat more lucid presentation of what the
            // following sequence is doing to the Java stack, see
            // footer comment #1.
            patch.insert(new IASTORE());
            patch.insert(new SWAP());
            patch.insert(iFactory.createPutStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.insert(new IADD());
            patch.insert(new ICONST(1));
            patch.insert(new DUP());
            patch.insert(iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.insert(new SWAP());
            end_ih = patch.insert(iFactory.createGetStatic(seqArrayRef,
                "sequenceArray", new ArrayType(Type.INT, 1)));
            patch.insert(traceMethodCall);
            patch.insert(instFieldLoad);
            patch.insert(new IF_ICMPGT(end_ih));
            patch.insert(iFactory.createGetStatic(seqArrayRef,
                "sequenceIndex", Type.INT));
            patch.insert(new ARRAYLENGTH());
            end_ih = patch.insert(iFactory.createGetStatic(seqArrayRef,
                "sequenceArray", new ArrayType(Type.INT, 1)));
            break;
        case INST_TRACE_HASHING:
            end_ih = patch.insert(traceMethodCall);
            break;
        }

        int nodeIndex = succNodes.length - 1;

        if (succNodes[nodeIndex].getType().toInt() > 63) {
            throw new SofyaError("Block type code is too large (> 63)");
        }
        else if (succNodes[nodeIndex].getID() > 67108863) {
            throw new SofyaError("Block ID is too large (> (2 ^ 26) - 1)");
        }

        for (n = edges.length - 1; n >= 0; n--) {
            if (edges[n].getSuccNodeID() == succNodes[nodeIndex].getID()) {
                break;
            }
        }

        // If the successor is an exit node, insert the code to mark the trace.
        // Otherwise we jump to the end and simply rethrow the exception, so as
        // to not mark the block twice (relevant for sequence traces).
        // We still must do the instanceof test for each class of
        // exception, otherwise exit nodes for superclass exceptions
        // will be erroneously marked (in the absence of specific
        // superclasses, the catch-all exit node would get marked).
        if (succNodes[nodeIndex].getType() == BlockType.EXIT) {
            switch (instMode) {
            case INST_COMPATIBLE:
                patch.insert(new PUSH(cpg,
                    (succNodes[nodeIndex].getType().toInt() << 26) +
                    succNodes[nodeIndex].getID()));
                patch.insert(instFieldLoad);
                break;
            case INST_OPT_SEQUENCE:
                patch.insert(new PUSH(cpg,
                    (succNodes[nodeIndex].getType().toInt() << 26) +
                    succNodes[nodeIndex].getID()));
                break;
            case INST_TRACE_HASHING:
                int globalIndex = globalIndexer.indexFor(mSignature,
                    succNodes[nodeIndex].getID());
                patch.insert(new PUSH(cpg, globalIndex));
                break;
            default:
                patch.insert(new PUSH(cpg,
                    (byte) succNodes[nodeIndex].getType().toInt()));
                patch.insert(new PUSH(cpg,
                    succNodes[nodeIndex].getID() - 1));
                break;
            }
        }
        else {
            patch.insert(new GOTO(insert_ih));
        }

        patch.insert(new IFEQ(insert_ih));
        if (edges[n].getLabel().equals("<any>")) {
            classRef = cpg.addClass("java.lang.Throwable");
        }
        else {
            classRef = cpg.addClass(edges[n].getLabel());
        }
        patch.insert(new INSTANCEOF(classRef));
        edges[n] = null;
        InstructionHandle lastTarget = patch.insert(new ALOAD(localVar));
        for (--nodeIndex; nodeIndex >= 0; nodeIndex--) {
            for (n = edges.length - 1; n >= 0; n--) {
                if ((edges[n] != null) && (edges[n].getSuccNodeID()
                        == succNodes[nodeIndex].getID())) {
                    break;
                }
            }
            if (!edges[n].getLabel().equals("<r>")) {
                if (succNodes[nodeIndex].getType().toInt() > 63) {
                    throw new SofyaError(
                        "Block type code is too large (> 63)");
                }
                else if (succNodes[nodeIndex].getID() > 67108863) {
                    throw new SofyaError(
                        "Block ID is too large (> (2 ^ 26) - 1)");
                }

                if (succNodes[nodeIndex].getType() == BlockType.EXIT) {
                    patch.insert(new GOTO(end_ih));
                    switch (instMode) {
                    case INST_COMPATIBLE:
                        patch.insert(new PUSH(cpg, (succNodes[nodeIndex]
                            .getType().toInt() << 26) +
                            succNodes[nodeIndex].getID()));
                        patch.insert(instFieldLoad);
                        break;
                    case INST_OPT_SEQUENCE:
                        patch.insert(new PUSH(cpg, (succNodes[nodeIndex]
                            .getType().toInt() << 26) +
                            succNodes[nodeIndex].getID()));
                        break;
                    case INST_TRACE_HASHING:
                        int globalIndex = globalIndexer.indexFor(mSignature,
                            succNodes[nodeIndex].getID());
                        patch.insert(new PUSH(cpg, globalIndex));
                        break;
                    default:
                        patch.insert(new PUSH(cpg, (byte)
                            succNodes[nodeIndex].getType().toInt()));
                        patch.insert(new PUSH(cpg,
                            succNodes[nodeIndex].getID() - 1));
                        break;
                    }
                }
                else {
                    patch.insert(new GOTO(insert_ih));
                }

                patch.insert(new IFEQ(lastTarget));
                classRef = cpg.addClass(edges[n].getLabel());
                edges[n] = null;
                patch.insert(new INSTANCEOF(classRef));
                lastTarget = patch.insert(new ALOAD(localVar));
            }
        }
        if (loadAdded) patch.insert(new ASTORE(localVar));

        il.insert(insert_ih, patch);
        il.setPositions();
        patch.dispose();
    }

    /*************************************************************************
     * Inserts an intercepting exception handler to detect and record
     * exceptions propagated by called methods, including those which are
     * rethrown.
     *
     * <p>A 'catch-all' exception handler (equivalent to those created for
     * <code>finally</code> blocks) is attached to the call instruction.
     * The catch block for the handler simulates exceptional exit nodes as
     * described for {@link #insertExitNodes} and then rethrows the exception.
     * Note that on the normal (non-exceptional) path of execution, the
     * handler is never invoked and execution continues as usual.</p>
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
    protected void insertCallHandler(Block b, MethodGen mg, InstructionList il,
                                     InstructionHandle ih, int localVar) {
        // Insert GOTO that will jump handler by default
        InstructionHandle origEnd = ih.getNext();
        il.insert(origEnd, new GOTO(origEnd));

        // Build handler instruction sequence
        InstructionList handlerCode = new InstructionList();
        InstructionHandle insert_ih = handlerCode.append(new ATHROW());
        insertExitNodes(b, handlerCode, insert_ih, localVar);

        // Insert handler instructions
        InstructionHandle handlerStart = il.insert(origEnd, handlerCode);
        il.setPositions();

        // Add handler - 'null' catches any type
        mg.addExceptionHandler(ih, ih, handlerStart, (ObjectType) null);
        handlerCode.dispose();
    }

    /*************************************************************************
     * Attaches an intercepting exception handler to the method which
     * marks the summary exceptional exit node to signify exceptional
     * exit from the method caused by any type of unchecked exception for
     * which precise control flow modeling is too costly (such as operator
     * and array-related exceptions).
     *
     * @param b Virtual block which is used as the summary exceptional
     * exit node.
     * @param mg The BCEL mutable representation of the method, required to
     * manipulate the exception handlers associated with the method.
     * @param il The instruction list of the method, into which the
     * exceptional exit node code will be inserted. This parameter is also
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
     * mark the block.
     * @param regEnd Original end instruction of the method. Used to
     * avoid interactions with other handlers added by the instrumentor.
     * @param exceptionVar Index of the local variable which will be used for
     * temporary storage of the exception object in the handler.
     */
    protected void addSummaryThrowHandler(Block b, MethodGen mg,
            InstructionList il, InstructionHandle regStart,
            InstructionHandle regEnd, int exceptionVar) {
        // Insert GOTO that will jump handler by default
        InstructionHandle origEnd = il.insert(regEnd, new GOTO(regEnd));
        if (regStart == regEnd) { regStart = origEnd; }

        // Build handler instruction sequence
        InstructionList handlerCode = new InstructionList();
        handlerCode.append(new ASTORE(exceptionVar));
        handlerCode.append(new ILOAD(excExitBooleanVar));
        BranchHandle bh = handlerCode.append(new IFNE(null));
        InstructionHandle insert_ih = handlerCode.append(
            new ALOAD(exceptionVar));
        handlerCode.append(new ATHROW());
        insertProbe(b, il, insert_ih);
        bh.setTarget(insert_ih);

        // Insert handler instructions
        InstructionHandle handlerStart = il.insert(regEnd, handlerCode);
        il.setPositions();

        // Add handler - null catches any type
        mg.addExceptionHandler(regStart, origEnd, handlerStart,
            (ObjectType) null);
        handlerCode.dispose();
    }
    
    @Override
    public void prepareForExit() throws Exception {
        if (instMode == INST_TRACE_HASHING) {
            globalIndexer.writeMappingFile("global_indexes.txt", true);
        }
    }
}

/* Footer comment #1: After the if...elseif set of instanceof checks is
 * complete, the correct block ID is left on top of the stack. The
 * following is a more graphical view of what the subsequent bytecodes
 * do to the stack in optimized sequence-instrumentation mode:

        Instruction                     Stack
        --------------------------------------------
        (on top of stack)               blockID

        ALOAD                           blockID        # Index pointer bounds
                                        arrayref       # check

        ARRAYLENGTH                     blockID
                                        array.length

        GETSTATIC sequenceIndex         blockID
                                        array.length
                                        sequenceIndex

        IFGT #TARG                      blockID        # Commit array if
                                                       # index is past
        INVOKESTATIC writeData          blockID        # bounds

        TARG: ALOAD                     blockID
                                        arrayref

        SWAP                            arrayref       # Re-order for
                                        blockID        # the IASTORE


        GETSTATIC sequenceIndex         arrayref       # This and following
                                        blockID        # sequence are to write
                                        sequenceIndex  # to the array

        DUP                             arrayref
                                        blockID
                                        sequenceIndex
                                        sequenceIndex

        ICONST_1                        arrayref       # Postfix increment
                                        blockID        # of index pointer
                                        sequenceIndex
                                        sequenceIndex
                                        1

        IADD                            arrayref
                                        blockID
                                        sequenceIndex
                                        sequenceIndex + 1

        PUTSTATIC sequenceIndex         arrayref       # Commit updated
                                        blockID        # index pointer
                                        sequenceIndex

        SWAP                            arrayref       # Re-order for
                                        sequenceIndex  # the IASTORE
                                        blockID

        IASTORE                                        # The actual array
                                                       # write
 */
