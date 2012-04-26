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

package sofya.mutator.operators;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;

import sofya.mutator.*;
import sofya.base.JVMStackReverser;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;
import static org.apache.bcel.Constants.*;

import gnu.trove.THashMap;
import gnu.trove.TShortHashSet;

/**
 * This class implements the Argument Order Change operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 08/11/2006
 */
public class AOC implements MutationOperator {

    public AOC() {
    }

    public String name() {
        return "AOC";
    }

    public String description() {
        return "argument order change";
    }

    public void generateMutants(MutationTable mt, ClassGen cg) {
        Method[] methods = cg.getMethods();
        ConstantPoolGen cpg = cg.getConstantPool();
        String className = cg.getClassName();


        for (int n = 0; n < methods.length; n++) {
            if (methods[n].isAbstract()) {
                continue;
            }

            MethodGen mg = new MethodGen(methods[n], className, cpg);

            MutationGroup group = new MutationGroup(className, mg.getName(),
                mg.getSignature());

            InstructionList il = mg.getInstructionList();
            int offset = 0;

            InstructionHandle ih = il.getStart();
            for ( ; ih != null; ih = ih.getNext()) {
                Instruction instruction = ih.getInstruction();
                short opcode = instruction.getOpcode();

                if (instruction instanceof InvokeInstruction) {
                    Type[] argTypes =
                        ((InvokeInstruction) instruction).getArgumentTypes(cpg);

                    for (int i = 0; i < argTypes.length; i++) {
                        for (int j = i + 1; j < argTypes.length; j++) {
                            if (argTypes[i].equals(argTypes[j])) {
                                group.addMutation(new AOCMutation(
                                    cg.getClassName(), mg.getName(),
                                    mg.getSignature(), ih.getPosition(),
                                    offset, opcode, i, j));
                            }
                        }
                    }

                    offset += 1;
                }
            }

            if (group.size() > 0) {
                if (group.size() > 1) {
                    mt.addMutation(group);
                }
                else {
                    int size = group.size();
                    Iterator iterator = group.iterator();
                    for (int i = size; i-- > 0; ) {
                        mt.addMutation((Mutation) iterator.next());
                    }
                }
            }
        }
    }

    public static class AOCMutation extends MethodMutation
            implements GroupableMutation {
        private int codeOffset;
        private int relOffset;
        private short opcode;
        private short firstArgIndex;
        private short secondArgIndex;

        private Method undoMethod;
        private int undoPos;

        private MutationGroup parent;
        private Map<Object, Object> linkData;

        private Map<Object, Object> undoLinkData;
        private List<Object> undoIl;
        private int undoMaxLocals = -1;

        private static TShortHashSet ABS_JUMPS = new TShortHashSet(
            new short[]{ GOTO, GOTO_W, JSR, JSR_W });
        private static TShortHashSet ABS_EXITS = new TShortHashSet(
            new short[]{ IRETURN, LRETURN, FRETURN, DRETURN, ARETURN,
                         RETURN, ATHROW, RET });

        private AOCMutation() {
        }

        protected AOCMutation(String className, String methodName,
                String signature, int codeOffset, int relOffset, short opcode,
                int firstArgIndex, int secondArgIndex) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.codeOffset = codeOffset;
            this.relOffset = relOffset;
            this.opcode = opcode;
            this.firstArgIndex = (short) firstArgIndex;
            this.secondArgIndex = (short) secondArgIndex;
        }

        public String getType() {
            return "AOC";
        }

        public MutationGroup getParent() {
            return parent;
        }

        public void setParent(MutationGroup mg) {
            parent = mg;
        }

        public Variant getDefaultVariant() {
            return null;
        }

        public Variant[] getVariants() {
            return new Variant[0];
        }

        public Variant getVariant() {
        	return null;
        }
        public int getRelOffset() {
        	return relOffset;
        }
        public void accept(MutationVisitor visitor) throws MutationException {
            if (parent != null) {
                visitor.visit((GroupableMutation) this);
            }
            else {
                visitor.visit((MethodMutation) this);
            }
        }

        public void apply(ClassGen cg, Variant variant)
                throws MutationException {
            if (!cg.getClassName().equals(className)) {
                throw new MutationException("Wrong class loaded");
            }

            Method method = cg.containsMethod(methodName, signature);
            if (method == null) {
                throw new MutationException("Could not find intended method " +
                    "for mutation");
            }
            undoMethod = method;

            MethodGen mg =
                new MethodGen(method, className, cg.getConstantPool());
            InstructionList il = mg.getInstructionList();

            apply(cg, mg, il, false);
        }

        public void apply(ClassGen cg, MethodGen mg, InstructionList il,
                Map<Object, Object> linkData, Variant variant) throws MutationException {
            this.linkData = linkData;
            apply(cg, mg, il, true);
            this.linkData = null;
        }

        // This mutation is applied as follows: we find the producer(s) of the
        // second argument to be swapped on the stack. A sequence of
        // instructions is appended after each such producer that stores the
        // appropriate number of arguments present between the two arguments
        // to be swapped to local variables above the range in use by the
        // normal code of the method, then loads the second argument, loads
        // the intervening arguments, and loads the first argument.
        // Adjustments are also made as necessary for wide arguments
        // (longs, doubles).
        @SuppressWarnings("unchecked")
        private void apply(ClassGen cg, MethodGen mg, InstructionList il,
                boolean grouped) throws MutationException {
            int relOffset;
            InstructionHandle ih;
            if (grouped) {
                undoLinkData = new THashMap();
                undoIl = new ArrayList<Object>();

                Integer relOffsetKey = (Integer) linkData.get("AOC.relOffset");
                undoLinkData.put("AOC.relOffset", relOffsetKey);
                if (relOffsetKey == null) {
                    relOffset = 0;
                }
                else {
                    relOffset = relOffsetKey.intValue();
                }

                ih = (InstructionHandle) linkData.get("AOC.ih");
                undoLinkData.put("AOC.ih", ih);
                if (ih == null) {
                    ih = il.getStart();
                }
            }
            else {
                relOffset = 0;
                ih = il.getStart();
            }

            ConstantPoolGen cpg = cg.getConstantPool();
            boolean mutated = false;
            int maxSwapDist = 0;
            int lvStoreOffset = 0;
            int maxLocals = -1;

            for ( ; ih != null; ih = ih.getNext()) {
                Instruction instruction = ih.getInstruction();
                short curOpcode = instruction.getOpcode();

                if (instruction instanceof InvokeInstruction) {
                    InvokeInstruction ii = (InvokeInstruction) instruction;
                    Type[] argTypes = ii.getArgumentTypes(cpg);
                    LocalVariableInstruction lvOp;

                    if ((curOpcode == opcode) &&
                            (relOffset == this.relOffset)) {
                        if (grouped) {
                            Integer maxLocalsKey = (Integer) linkData.get(ih);
                            undoLinkData.put(ih, maxLocalsKey);
                            if (maxLocalsKey != null) {
                                maxLocals = maxLocalsKey.intValue();
                            }
                            else {
                                Integer baseMaxLocalsKey =
                                    (Integer) linkData.get("AOC.baseMaxLocals");
                                if (baseMaxLocalsKey == null) {
                                    maxLocals = mg.getMaxLocals();
                                    linkData.put("AOC.baseMaxLocals",
                                        new Integer(maxLocals));
                                }
                                else {
                                    maxLocals = baseMaxLocalsKey.intValue();
                                }
                            }
                        }
                        else {
                            maxLocals = mg.getMaxLocals();
                        }

                        int swapDist = secondArgIndex - firstArgIndex;
                        if (swapDist > maxSwapDist) {
                            maxSwapDist = swapDist;
                        }

                        InstructionList patch = new InstructionList();
                        if ((swapDist == 1)
                                && (argTypes[secondArgIndex].getSize() == 1)) {
                            patch.append(new SWAP());
                        }
                        else {
                            lvStoreOffset = 0;
                            for (int i = 0; i <= swapDist; i++) {
                                lvOp = InstructionFactory.createStore(
                                    argTypes[secondArgIndex - i],
                                    maxLocals + i + lvStoreOffset);
                                patch.append(lvOp);
                                switch (lvOp.getOpcode()) {
                                case LSTORE: case LSTORE_0: case LSTORE_1:
                                case LSTORE_2: case LSTORE_3:
                                case DSTORE: case DSTORE_0: case DSTORE_1:
                                case DSTORE_2: case DSTORE_3:
                                    lvStoreOffset += 1;
                                    break;
                                default:
                                    break;
                                }
                            }

                            int lvLoadOffset = lvStoreOffset;
                            patch.append(InstructionFactory.createLoad(
                                argTypes[secondArgIndex],
                                maxLocals));
                            if (argTypes[secondArgIndex].getSize() == 2) {
                                lvLoadOffset -= 1;
                            }
                            for (int i = swapDist - 1; i > 0; i--) {
                                Type argType = argTypes[secondArgIndex - i];
                                if (argType.getSize() == 2) {
                                    lvLoadOffset -= 1;
                                }
                                patch.append(InstructionFactory.createLoad(argType,
                                    maxLocals + i + lvLoadOffset));
                            }
                            patch.append(InstructionFactory.createLoad(
                                argTypes[firstArgIndex],
                                maxLocals + swapDist +
                                lvStoreOffset - lvLoadOffset));
                        }

                        InstructionHandle[] secondArgProducers =
                            findProducers(il, ih, ii, secondArgIndex, cpg);
                        for (int i = 0; i < secondArgProducers.length; i++) {
                            InstructionList insertPatch = patch.copy();
                            if (grouped) {
                                undoIl.add(insertPatch.getStart());
                                undoIl.add(insertPatch.getEnd());
                            }
                            il.append(secondArgProducers[i], insertPatch);
                        }

                        mutated = true;
                        break;
                    }

                    relOffset += 1;
                }
            }

            if (mutated) {
                int newMaxLocals = maxLocals + maxSwapDist + lvStoreOffset + 1;

                if (grouped) {
                    linkData.put("AOC.relOffset", new Integer(relOffset));
                    linkData.put("AOC.ih", ih);
                    linkData.put(ih, new Integer(newMaxLocals));

                    int oldMaxLocals = mg.getMaxLocals();
                    if (newMaxLocals > oldMaxLocals) {
                        mg.setMaxLocals(newMaxLocals);
                        undoMaxLocals = oldMaxLocals;
                    }
                }
                else {
                    il.setPositions();
                    mg.setMaxLocals(newMaxLocals);
                    mg.setInstructionList(il);

                    // Find the position of the method
                    Method[] methods = cg.getMethods();
                    int i = 0;
                    for ( ; i < methods.length; i++) {
                        // We are assuming that 'undoMethod' has been set
                        // by the call to the non-grouped apply method
                        if (methods[i] == undoMethod) {
                            break;
                        }
                    }

                    if (i == methods.length) {
                        throw new MutationException("Could not find position " +
                            "of method in class");
                    }
                    undoPos = i;

                    // Commit the changed method
                    cg.setMethodAt(mg.getMethod(), i);

                    il.dispose();
                }
            }
            else {
                throw new MutationException("Failed to apply mutation");
            }
        }

        /**
         * Performs a flow-sensitive search to find the instruction(s) that
         * produced a method argument on the stack.
         *
         * @param il Instruction list of the method.
         * @param call Instruction handle of the call instruction for which
         * the argument has been placed on the stack.
         * @param ii Call instruction for which the argument has been placed
         * on the stack.
         * @param argIndex Index of the argument for which to find the
         * producing instruction.
         * @param cpg Constant pool for the class.
         *
         * @return An array of handles to all the instructions that possibly
         * place the given argument on the stack.
         *
         * @throws MutationException If a producing instruction cannot be
         * found on an accessible flow path.
         */
        @SuppressWarnings("unchecked")
        private InstructionHandle[] findProducers(InstructionList il,
                InstructionHandle call, InvokeInstruction ii,
                short argIndex, ConstantPoolGen cpg)
                throws MutationException {
            //System.out.println("findProducer: " + call + ", index: " + argIndex);
            List<Object> producers = new ArrayList<Object>();

            Type[] argTypes = ii.getArgumentTypes(cpg);
            int argCount = argTypes.length;
            //System.out.println("argCount: " + argCount);

            int wideArgOffset = 0;
            for (int i = argCount - 1; i > argIndex; i--) {
                wideArgOffset += argTypes[i].getSize() - 1;
            }
            //System.out.println("wideArgOffset: " + wideArgOffset);

            int argDepth = argCount - 1 - argIndex + wideArgOffset;
            //System.out.println("argDepth: " + argDepth);
            JVMStackReverser rStack = new JVMStackReverser(cpg, argDepth);

            Map<Object, List<Object>> targeters = new THashMap();
            for (InstructionHandle ih = il.getStart(); ih != null;
                    ih = ih.getNext()) {
                if (ih instanceof BranchHandle) {
                    InstructionHandle target_ih =
                        ((BranchHandle) ih).getTarget();
                    List<Object> targetersList = targeters.get(target_ih);
                    if (targetersList == null) {
                        targetersList = new ArrayList<Object>(4);
                        targeters.put(target_ih, targetersList);
                    }
                    targetersList.add(ih);
                }
            }

            findProducers(call, producers, rStack, targeters);

            return (InstructionHandle[]) producers.toArray(
                new InstructionHandle[producers.size()]);
        }

        /**
         * Recursive implementation of the flow-sensitive search to find
         * the instruction(s) that produced a method argument on the stack.
         *
         * @param ih Handle to the instruction from which to search.
         * @param producers List of instructions found so far that produce
         * the desired stack operand.
         * @param rStack Reverse Java stack simulator used to drive the
         * flow sensitive search.
         * @param targeters Pre-computed map of branch instructions to their
         * targets, used to continue the search through jumps.
         *
         * @throws MutationException If a producing instruction cannot be
         * found on an accessible flow path.
         */
        private void findProducers(InstructionHandle ih, List<Object> producers,
                JVMStackReverser rStack, Map targeters)
                throws MutationException {
            InstructionHandle cur_ih = ih.getPrev();

            if (ih == null) {
                throw new MutationException("Could not locate " +
                    "producing instruction for method argument");
            }

            Instruction instruction = cur_ih.getInstruction();
            short opcode = instruction.getOpcode();

            boolean walkPrev = true;
            if (ABS_JUMPS.contains(opcode)) {
                BranchInstruction bi = (BranchInstruction) instruction;
                if (bi.getTarget() != ih) {
                    walkPrev = false;
                }
            }
            else if (ABS_EXITS.contains(opcode)) {
                walkPrev = false;
            }

            if (walkPrev) {
                if (rStack.runInstruction(instruction)) {
                    producers.add(cur_ih);
                }
                else {
                    findProducers(cur_ih, producers, rStack, targeters);
                }
            }

            if (targeters.containsKey(ih)) {
                List targetersList = (List) targeters.get(ih);
                int size = targetersList.size();
                Iterator iterator = targetersList.iterator();
                for (int i = size; i-- > 0; ) {
                    InstructionHandle prev_ih =
                        (InstructionHandle) iterator.next();
                    findProducers(prev_ih, producers, rStack.copy(), targeters);
                }
            }
        }

        public void undo(ClassGen cg) {
            cg.setMethodAt(undoMethod, undoPos);
            undoMethod = null;
        }

        public void undo(ClassGen cg, MethodGen mg, InstructionList il,
                Map<Object, Object> linkData) {
            linkData.putAll(undoLinkData);
            int size = undoIl.size();
            Iterator iterator = undoIl.iterator();
            for (int i = size; i > 0; i -= 2) {
                InstructionHandle start = (InstructionHandle) iterator.next();
                InstructionHandle end = (InstructionHandle) iterator.next();
                try {
                    il.delete(start, end);
                }
                catch (TargetLostException e) {
                    // No one will be targeting these instructions
                }
            }
            if (undoMaxLocals != -1) {
                mg.setMaxLocals(undoMaxLocals);
            }
            mg.setInstructionList(il);

            undoLinkData = null;
            undoIl = null;
            undoMaxLocals = -1;
        }

        public void serialize(DataOutput out)
                throws IOException {
            out.writeUTF(className);
            out.writeUTF(methodName);
            out.writeUTF(signature);
            out.writeInt(codeOffset);
            out.writeInt(relOffset);
            out.writeShort(opcode);
            out.writeShort(firstArgIndex);
            out.writeShort(secondArgIndex);
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            AOCMutation mutation = new AOCMutation();

            mutation.className = in.readUTF();
            mutation.methodName = in.readUTF();
            mutation.signature = in.readUTF();
            mutation.codeOffset = in.readInt();
            mutation.relOffset = in.readInt();
            mutation.opcode = in.readShort();
            mutation.firstArgIndex = in.readShort();
            mutation.secondArgIndex = in.readShort();

            return mutation;
        }

        public String print() {
            StringBuffer sb = new StringBuffer();
            if (getID() != null) {
                sb.append(getID().asInt());
                sb.append(":");
            }
            else {
                sb.append("-:");
            }
            if ((parent != null) && (parent.getID() != null)) {
                sb.append(parent.getID().asInt());
                sb.append(":");
            }
            else {
                sb.append("-:");
            }
            sb.append(getType());
            sb.append(":");
            sb.append(className);
            sb.append(":");
            sb.append(methodName);
            sb.append(":");
            sb.append(signature);
            sb.append(":");
            sb.append(codeOffset);
            sb.append(":");
            sb.append(relOffset);
            sb.append(":");
            sb.append(OPCODE_NAMES[opcode]);
            sb.append(":");
            sb.append(firstArgIndex);
            sb.append(",");
            sb.append(secondArgIndex);
            return sb.toString();
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(getType());
            sb.append(" {\n");
            if (getID() != null) {
                sb.append("\tid: ");
                sb.append(getID().asInt());
            }
            else {
                sb.append("\tid not assigned");
            }
            sb.append("\n\tclass: ");
            sb.append(className);
            sb.append("\n\tmethod: ");
            sb.append(methodName);
            sb.append("\n\tsignature: ");
            sb.append(signature);
            sb.append("\n\toriginal code offset: ");
            sb.append(codeOffset);
            sb.append("\n\trelative position offset: ");
            sb.append(relOffset);
            sb.append("\n\tcall opcode: ");
            sb.append(OPCODE_NAMES[opcode]);
            sb.append("\n\tswapped argument indexes: ");
            sb.append(firstArgIndex);
            sb.append(", ");
            sb.append(secondArgIndex);
            sb.append("\n}");
            return sb.toString();
        }
    }
}
