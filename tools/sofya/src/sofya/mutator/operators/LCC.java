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
import java.util.Random;
import java.util.Map;
import java.util.Iterator;

import sofya.mutator.*;
import sofya.mutator.Mutation.Variant;
import sofya.mutator.operators.ROP.ROPVariant;
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;
import static org.apache.bcel.Constants.*;

/**
 * This class implements the Logical Connector Change operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 08/11/2006
 */
public class LCC implements MutationOperator {

    static final short iLogicalOps[] = {IAND, IOR, IXOR};
    static final short lLogicalOps[] = {LAND, LOR, LXOR};

    Random rand = new Random();

    public LCC() {
    }

    public String name() {
        return "LCC";
    }

    public String description() {
        return "logical connector change";
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

            // We record the position of a mutatable instruction relative to
            // the number of mutatable instructions of the same type that
            // have already been seen. This allows us to correctly match the
            // instruction when actually applying the mutation regardless
            // of whether other mutations may have altered the absolute
            // offset of the mutatable instruction in the bytecode.
            int offset = 0;

            InstructionList il = mg.getInstructionList();
            InstructionHandle ih = il.getStart();
            for ( ; ih != null; ih = ih.getNext()) {
                Instruction instruction = ih.getInstruction();
                short opcode = instruction.getOpcode();

                if ((opcode >= 126) && (opcode <= 131)) {
                    short newOpcode = 0;

                    switch (opcode) {
                    case IAND:
                    case IOR:
                    case IXOR:
                        newOpcode = randomOpcode(iLogicalOps, opcode);
                        break;
                    case LAND:
                    case LOR:
                    case LXOR:
                        newOpcode = randomOpcode(lLogicalOps, opcode);
                        break;
                    default:
                        throw new SofyaError();
                    }

                    group.addMutation(new LCCMutation(cg.getClassName(),
                        mg.getName(), mg.getSignature(), ih.getPosition(),
                        offset, opcode, newOpcode));

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

    private short randomOpcode(short[] opcodes, int exclude) {
        int index = rand.nextInt(opcodes.length);
        if (opcodes[index] == exclude) {
            index = (index + 1) % opcodes.length;
        }

        return opcodes[index];
    }

    private static final String opcodeToOperatorString(short opcode) {
        switch (opcode) {
        case IAND: case LAND:
            return "&";
        case IOR: case LOR:
            return "|";
        case IXOR: case LXOR:
            return "^";
        default:
            return "Opcode not associated with " +
                "arithmetic operator instruction";
        }
    }

    public static class LCCMutation extends MethodMutation
            implements GroupableMutation {
        private int codeOffset;
        private int relOffset;
        private short origOpcode;
        private short mutatedOpcode;
        private Variant appliedVariant;

        private Method undoMethod;
        private int undoPos;

        private MutationGroup parent;
        private Map<Object, Object> linkData;

        private InstructionHandle undoIh;

        private static final Variant[][] logicalOps = new Variant[2][3];

        static {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 3; j++) {
                    logicalOps[i][j] =
                        new LCCVariant((short) (IAND + i + (j * 2)));
                }
            }
        }

        private LCCMutation() {
        }

        protected LCCMutation(String className, String methodName,
                String signature, int codeOffset, int relOffset,
                short origOpcode, short mutatedOpcode) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.codeOffset = codeOffset;
            this.relOffset = relOffset;
            this.origOpcode = origOpcode;
            this.mutatedOpcode = mutatedOpcode;
        }

        public String getType() {
            return "LCC";
        }

        public MutationGroup getParent() {
            return parent;
        }

        public void setParent(MutationGroup mg) {
            parent = mg;
        }

        public Variant getDefaultVariant() {
            int type = (mutatedOpcode - IAND) % 2;
            int op = (mutatedOpcode - IAND) / 2;
            return logicalOps[type][op];
        }

        /**
         * get an array of all variants (mutations) in a
         * LCC mutation group.
         * @return array of Variant objects contained in
         * this LCC mutation group.
         */
        
        public Variant[] getVariants() {
            Variant[] variants = new Variant[2];
            int type = (origOpcode - IAND) % 2;
            int skipOp = (origOpcode - IAND) / 2;
            int index = 0;
            for (int op = 0; op < 3; op++) {
                if (op != skipOp) {
                    variants[index++] = logicalOps[type][op];
                }
            }
            return variants;
        }
        /**
         * Gets a LCCVariant object based on this LCC mutation
         * object. 
         * Added to support grouped mutations - wdm 8/29/2011
         * @return Variant an LLCVariant object representing
         * the mutation enumerated by this object
         */        
        public Variant getVariant() {
        	LCCVariant lv = new LCCVariant(this.mutatedOpcode);
        	return lv;
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

            apply(cg, mg, il, variant, false);
        }

        public void apply(ClassGen cg, MethodGen mg, InstructionList il,
                Map<Object, Object> linkData, Variant variant)
                throws MutationException {
            this.linkData = linkData;
            apply(cg, mg, il, variant, true);
            this.linkData = null;
        }

        private void apply(ClassGen cg, MethodGen mg, InstructionList il,
                Variant variant, boolean grouped) throws MutationException {
            int relOffset;
            InstructionHandle ih;
            if (grouped) {
                Integer relOffsetKey = (Integer) linkData.get("LCC.relOffset");
                if (relOffsetKey == null) {
                    relOffset = 0;
                }
                else {
                    relOffset = relOffsetKey.intValue();
                }

                ih = (InstructionHandle) linkData.get("LCC.ih");
                if (ih == null) {
                    ih = il.getStart();
                }
            }
            else {
                relOffset = 0;
                ih = il.getStart();
            }

            boolean mutated = false;

            for ( ; ih != null; ih = ih.getNext()) {
                Instruction instruction = ih.getInstruction();
                short opcode = instruction.getOpcode();

                if ((opcode >= 126) && (opcode <= 131)) {
                    if (relOffset == this.relOffset) {
                        if (this.origOpcode == opcode) {
                            ih.setInstruction(getLogicalInstruction(
                                ((LCCVariant) variant).getOpcode()));
                            undoIh = ih;
                            mutated = true;
                            relOffset += 1;
                            break;
                        }
                        else {
                            throw new MutationException("Opcode mismatch:\n\t" +
                                className + "." + methodName + "." + signature +
                                "\n\t" + codeOffset + ":" + relOffset + ": " +
                                origOpcode + " != " + opcode);
                        }
                    }
                    else {
                        relOffset += 1;
                    }
                }
            }

            if (mutated) {
                if (grouped) {
                    linkData.put("LCC.relOffset", new Integer(relOffset));
                    linkData.put("LCC.ih", ih.getNext());
                }
                else {
                    il.setPositions();
                    mg.setInstructionList(il);

                    // Find the position of the method
                    Method[] methods = cg.getMethods();
                    int i = 0;
                    for ( ; i < methods.length; i++) {
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
                appliedVariant = variant;
            }
            else {
                throw new MutationException("Failed to apply mutation");
            }
        }

        public void undo(ClassGen cg) {
            cg.setMethodAt(undoMethod, undoPos);
            undoMethod = null;
            appliedVariant = null;
        }

        public void undo(ClassGen cg, MethodGen mg, InstructionList il,
                Map linkData) {
            try {
                undoIh.setInstruction(getLogicalInstruction(origOpcode));
            }
            catch (MutationException e) {
                throw new SofyaError("Illegal original opcode", e);
            }

            mg.setInstructionList(il);

            undoIh = null;
            appliedVariant = null;
        }

        public void serialize(DataOutput out) throws IOException {
            out.writeUTF(className);
            out.writeUTF(methodName);
            out.writeUTF(signature);
            out.writeInt(codeOffset);
            out.writeInt(relOffset);
            out.writeShort(origOpcode);
            if (appliedVariant != null) {
                out.writeShort(((LCCVariant) appliedVariant).getOpcode());
            }
            else {
                out.writeShort(mutatedOpcode);
            }
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            LCCMutation mutation = new LCCMutation();

            mutation.className = in.readUTF();
            mutation.methodName = in.readUTF();
            mutation.signature = in.readUTF();
            mutation.codeOffset = in.readInt();
            mutation.relOffset = in.readInt();
            mutation.origOpcode = in.readShort();
            mutation.mutatedOpcode = in.readShort();

            return mutation;
        }

        private ArithmeticInstruction getLogicalInstruction(short opcode)
                throws MutationException {
            switch (opcode) {
            case IAND: return new IAND();
            case LAND: return new LAND();
            case IOR:  return new IOR();
            case LOR:  return new LOR();
            case IXOR: return new IXOR();
            case LXOR: return new LXOR();
            default:
                throw new MutationException("Opcode not associated with " +
                    "logical operator instruction");
            }
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
            sb.append(OPCODE_NAMES[origOpcode]);
            sb.append("[");
            sb.append(opcodeToOperatorString(origOpcode));
            sb.append("]:");
            sb.append(OPCODE_NAMES[mutatedOpcode]);
            sb.append("[");
            sb.append(opcodeToOperatorString(mutatedOpcode));
            sb.append("]");
            sb.append(":");
            Variant[] vs = getVariants();
            for (int i = 0; i < vs.length; i++) {
                sb.append(i + 1).append(",");
                sb.append(vs[i].toString());
                if (i != (vs.length - 1)) {
                    sb.append(";");
                }
            }
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
            sb.append("\n\topcode mutation: ");
            sb.append(OPCODE_NAMES[origOpcode]);
            sb.append("[");
            sb.append(opcodeToOperatorString(origOpcode));
            sb.append("] -> ");
            sb.append(OPCODE_NAMES[mutatedOpcode]);
            sb.append("[");
            sb.append(opcodeToOperatorString(mutatedOpcode));
            sb.append("]");
            sb.append("\n\tvariants: { ");
            Variant[] vs = getVariants();
            for (int i = 0; i < vs.length; i++) {
                sb.append(i + 1).append(": ");
                sb.append(vs[i].toString());
                if (i != (vs.length - 1)) {
                    sb.append(", ");
                }
            }
            sb.append(" }\n}");
            return sb.toString();
        }
    }

    public static class LCCVariant implements Variant {
        private final short opcode;

        private LCCVariant() {
            throw new SofyaError();
        }

        LCCVariant(short opcode) {
            this.opcode = opcode;
        }

        short getOpcode() {
            return opcode;
        }

        public String toString() {
            return opcodeToOperatorString(opcode);
        }
    }
}
