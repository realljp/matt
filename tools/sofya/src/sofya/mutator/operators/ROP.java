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
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;
import static org.apache.bcel.Constants.*;

import gnu.trove.TShortObjectHashMap;

/**
 * This class implements the Relational Operator Change operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 08/11/2006
 */
public class ROP implements MutationOperator {

    static final short[] ifIntOps = {IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE};
    static final short[] ifRefOps = {IFNULL, IFNONNULL};
    static final short[] icmpOps  = {IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT,
                                     IF_ICMPGE, IF_ICMPGT, IF_ICMPLE};
    static final short[] acmpOps  = {IF_ACMPEQ, IF_ACMPNE};

    Random rand = new Random();

    public ROP() {
    }

    public String name() {
        return "ROP";
    }

    public String description() {
        return "relational operator change";
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
            int relOffset = 0;

            MutationGroup group = new MutationGroup(className, mg.getName(),
                mg.getSignature());

            InstructionList il = mg.getInstructionList();
                    InstructionHandle ih = il.getStart();
            for ( ; ih != null; ih = ih.getNext()) {
                Instruction instruction = ih.getInstruction();
                short opcode = instruction.getOpcode();

                if (((opcode >= 153) && (opcode <= 166)) || (opcode == 198) ||
                        (opcode == 199)) {
                    short newOpcode = 0;

                    switch (opcode) {
                    case IFEQ: case IFNE: case IFLT: case IFGE:
                    case IFGT: case IFLE:
                        newOpcode = randomOpcode(ifIntOps, opcode);
                        break;
                    case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT:
                    case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
                        newOpcode = randomOpcode(icmpOps, opcode);
                        break;
                    case IF_ACMPEQ:
                        newOpcode = IF_ACMPNE;
                        break;
                    case IF_ACMPNE:
                        newOpcode = IF_ACMPEQ;
                        break;
                    case IFNULL:
                        newOpcode = IFNONNULL;
                        break;
                    case IFNONNULL:
                        newOpcode = IFNULL;
                        break;
                    default:
                        throw new AssertionError();
                    }

                    group.addMutation(new ROPMutation(cg.getClassName(),
                        mg.getName(), mg.getSignature(), ih.getPosition(),
                        relOffset, opcode, newOpcode));

                    relOffset += 1;
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
        case IFEQ: case IF_ICMPEQ: case IF_ACMPEQ: case IFNULL:
            return "==";
        case IFNE: case IF_ICMPNE: case IF_ACMPNE: case IFNONNULL:
            return "!=";
        case IFLT: case IF_ICMPLT:
            return "<";
        case IFGE: case IF_ICMPGE:
            return ">=";
        case IFGT: case IF_ICMPGT:
            return ">";
        case IFLE: case IF_ICMPLE:
            return "<=";
        default:
            return "Opcode not associated with " +
                "arithmetic operator instruction";
        }
    }

    public static class ROPMutation extends MethodMutation
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

        private BranchHandle undoIh;

        private static final TShortObjectHashMap relOps =
            new TShortObjectHashMap();

        static {
            for (int i = 0; i < 6; i++) {
                short opcode = (short) (IFEQ + i);
                relOps.put(opcode, new ROPVariant(opcode));
                opcode = (short) (IF_ICMPEQ + i);
                relOps.put(opcode, new ROPVariant(opcode));
            }
            for (int i = 0; i < 2; i++) {
                short opcode = (short) (IFNULL + i);
                relOps.put(opcode, new ROPVariant(opcode));
                opcode = (short) (IF_ACMPEQ + i);
                relOps.put(opcode, new ROPVariant(opcode));
            }
        }

        private ROPMutation() {
        }

        protected ROPMutation(String className, String methodName,
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
            return "ROP";
        }

        public MutationGroup getParent() {
            return parent;
        }

        public void setParent(MutationGroup mg) {
            parent = mg;
        }

        public Variant getDefaultVariant() {
            return (Variant) relOps.get(mutatedOpcode);
        }
        /**
         * get the variant associated with this groupable mutation object
         * Since groupable mutation objects are actually ROP objects
         * this takes an existing ROP and gives back a corresponding ROPVariant
         * of this mutation.
         * @return ROPVariant equivalent to this groupable mutation ROP
         * Added by wdm 8/26/2011 to support jar file mutation
         */
        public Variant getVariant() {
        	ROPVariant rv = new ROPVariant(this.mutatedOpcode);
        	return rv;
        }
        
        public int getRelOffset() {
        	return relOffset;
        }
        
        public Variant[] getVariants() {
            Variant[] variants = null;
            int index;

            switch (origOpcode) {
            case IFNULL:  case IFNONNULL:
            	Variant[] retv = new Variant[1];
            	retv[0] = new ROPVariant(mutatedOpcode);
            	return retv;
                //return new Variant[0];
            case IF_ACMPEQ: case IF_ACMPNE:
                return new Variant[0];
            case IFEQ: case IFNE: case IFLT:
            case IFGE: case IFGT: case IFLE:
                variants = new Variant[5];
                index = 0;
                for (short opcode = IFEQ; opcode <= IFLE; opcode++) {
                    if (opcode != origOpcode) {
                        variants[index++] = (Variant) relOps.get(opcode);
                    }
                }
                return variants;
            case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT:
            case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
                variants = new Variant[5];
                index = 0;
                for (short opcode = IF_ICMPEQ; opcode <= IF_ICMPLE; opcode++) {
                    if (opcode != origOpcode) {
                        variants[index++] = (Variant) relOps.get(opcode);
                    }
                }
                return variants;
            default:
                throw new SofyaError();
            }
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
                Integer relOffsetKey = (Integer) linkData.get("ROP.relOffset");
                if (relOffsetKey == null) {
                    relOffset = 0;
                }
                else {
                    relOffset = relOffsetKey.intValue();
                }

                ih = (InstructionHandle) linkData.get("ROP.ih");
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

                if (((opcode >= 153) && (opcode <= 166)) || (opcode == 198) ||
                        (opcode == 199)) {
                    if (relOffset == this.relOffset) {
                        if (this.origOpcode == opcode) {
                            BranchHandle bh = (BranchHandle) ih;
                            InstructionHandle target = bh.getTarget();
                            ih.setInstruction(getBranchInstruction(
                                ((ROPVariant) variant).getOpcode(), target));
                            undoIh = bh;
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
                    linkData.put("ROP.relOffset", new Integer(relOffset));
                    linkData.put("ROP.ih", ih.getNext());
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
                InstructionHandle target = undoIh.getTarget();
                undoIh.setInstruction(getBranchInstruction(origOpcode, target));
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
                out.writeShort(((ROPVariant) appliedVariant).getOpcode());
            }
            else {
                out.writeShort(mutatedOpcode);
            }
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            ROPMutation mutation = new ROPMutation();

            mutation.className = in.readUTF();
            mutation.methodName = in.readUTF();
            mutation.signature = in.readUTF();
            mutation.codeOffset = in.readInt();
            mutation.relOffset = in.readInt();
            mutation.origOpcode = in.readShort();
            mutation.mutatedOpcode = in.readShort();

            return mutation;
        }

        private BranchInstruction getBranchInstruction(short opcode,
                InstructionHandle target) throws MutationException {
            switch (opcode) {
            case IFEQ:      return new IFEQ(target);
            case IFNE:      return new IFNE(target);
            case IFLT:      return new IFLT(target);
            case IFGE:      return new IFGE(target);
            case IFGT:      return new IFGT(target);
            case IFLE:      return new IFLE(target);
            case IF_ICMPEQ: return new IF_ICMPEQ(target);
            case IF_ICMPNE: return new IF_ICMPNE(target);
            case IF_ICMPLT: return new IF_ICMPLT(target);
            case IF_ICMPGE: return new IF_ICMPGE(target);
            case IF_ICMPGT: return new IF_ICMPGT(target);
            case IF_ICMPLE: return new IF_ICMPLE(target);
            case IF_ACMPEQ: return new IF_ACMPEQ(target);
            case IF_ACMPNE: return new IF_ACMPNE(target);
            case IFNULL:    return new IFNULL(target);
            case IFNONNULL: return new IFNONNULL(target);
            default:
                throw new MutationException("Opcode not associated with " +
                    "branch instruction");
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
            if (vs.length > 0) {
                for (int i = 0; i < vs.length; i++) {
                    sb.append(i + 1).append(",");
                    sb.append(vs[i].toString());
                    if (i != (vs.length - 1)) {
                        sb.append(";");
                    }
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
            Variant[] vs = getVariants();
            if (vs.length > 0) {
                sb.append("\n\tvariants: { ");
                for (int i = 0; i < vs.length; i++) {
                    sb.append(i + 1).append(": ");
                    sb.append(vs[i].toString());
                    if (i != (vs.length - 1)) {
                        sb.append(", ");
                    }
                }
                sb.append(" }");
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    public static class ROPVariant implements Variant {
        private final short opcode;

        private ROPVariant() {
            throw new SofyaError();
        }

        ROPVariant(short opcode) {
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
