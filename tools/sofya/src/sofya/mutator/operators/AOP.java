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

/**
 * This class implements the Arithmetic Operator Change operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 08/11/2006
 */
public class AOP implements MutationOperator {

    static final short[] iArithmeticOps = {IADD, ISUB, IMUL, IDIV, IREM};
    static final short[] lArithmeticOps = {LADD, LSUB, LMUL, LDIV, LREM};
    static final short[] fArithmeticOps = {FADD, FSUB, FMUL, FDIV, FREM};
    static final short[] dArithmeticOps = {DADD, DSUB, DMUL, DDIV, DREM};

    Random rand = new Random();

    public AOP() {
    }

    public String name() {
        return "AOP";
    }

    public String description() {
        return "arithmetic operator change";
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

                if ((opcode >= 96) && (opcode <= 115)) {
                    short newOpcode = 0;

                    switch (opcode) {
                    case IADD: case ISUB: case IMUL: case IDIV: case IREM:
                        newOpcode = randomOpcode(iArithmeticOps, opcode);
                        break;
                    case LADD: case LSUB: case LMUL: case LDIV: case LREM:
                        newOpcode = randomOpcode(lArithmeticOps, opcode);
                        break;
                    case FADD: case FSUB: case FMUL: case FDIV: case FREM:
                        newOpcode = randomOpcode(fArithmeticOps, opcode);
                        break;
                    case DADD: case DSUB: case DMUL: case DDIV: case DREM:
                        newOpcode = randomOpcode(dArithmeticOps, opcode);
                        break;
                    default:
                        throw new SofyaError("" + opcode);
                    }

                    group.addMutation(new AOPMutation(cg.getClassName(),
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
        case IADD: case LADD: case FADD: case DADD:
            return "+";
        case ISUB: case LSUB: case FSUB: case DSUB:
            return "-";
        case IMUL: case LMUL: case FMUL: case DMUL:
            return "*";
        case IDIV: case LDIV: case FDIV: case DDIV:
            return "/";
        case IREM: case LREM: case FREM: case DREM:
            return "%";
        default:
            return "Opcode not associated with " +
                "arithmetic operator instruction";
        }
    }

    public static class AOPMutation extends MethodMutation
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

        private static final Variant[][] arithmeticOps = new Variant[4][5];

        static {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 5; j++) {
                    arithmeticOps[i][j] =
                        new AOPVariant((short) (IADD + i + (j * 4)));
                }
            }
        }

        private AOPMutation() {
        }

        protected AOPMutation(String className, String methodName,
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
            return "AOP";
        }

        public MutationGroup getParent() {
            return parent;
        }

        public void setParent(MutationGroup mg) {
            parent = mg;
        }

        public Variant getDefaultVariant() {
            int type = (mutatedOpcode - IADD) % 4;
            int op = (mutatedOpcode - IADD) / 4;
            return arithmeticOps[type][op];
        }

        /**
         * stub to support grouped mutations.
         * @return the variant that this mutation specifies
         * as a Variant object.
         */
        public Variant getVariant() {
        	return getDefaultVariant();
        }
        
        public int getRelOffset() {
        	return relOffset;
        }
        
        public Variant[] getVariants() {
            Variant[] variants = new Variant[4];
            int type = (origOpcode - IADD) % 4;
            int skipOp = (origOpcode - IADD) / 4;
            int index = 0;
            for (int op = 0; op < 5; op++) {
                if (op != skipOp) {
                    variants[index++] = arithmeticOps[type][op];
                }
            }
            return variants;
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
                Integer relOffsetKey = (Integer) linkData.get("AOP.relOffset");
                if (relOffsetKey == null) {
                    relOffset = 0;
                }
                else {
                    relOffset = relOffsetKey.intValue();
                }

                ih = (InstructionHandle) linkData.get("AOP.ih");
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

                if ((opcode >= 96) && (opcode <= 115)) {
                    if (relOffset == this.relOffset) {
                        if (this.origOpcode == opcode) {
                            ih.setInstruction(getArithmeticInstruction(
                                ((AOPVariant) variant).getOpcode()));
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
                    linkData.put("AOP.relOffset", new Integer(relOffset));
                    linkData.put("AOP.ih", ih);
                }
                else {
                    il.setPositions();
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
                undoIh.setInstruction(getArithmeticInstruction(origOpcode));
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
                out.writeShort(((AOPVariant) appliedVariant).getOpcode());
            }
            else {
                out.writeShort(mutatedOpcode);
            }
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            AOPMutation mutation = new AOPMutation();

            mutation.className = in.readUTF();
            mutation.methodName = in.readUTF();
            mutation.signature = in.readUTF();
            mutation.codeOffset = in.readInt();
            mutation.relOffset = in.readInt();
            mutation.origOpcode = in.readShort();
            mutation.mutatedOpcode = in.readShort();

            return mutation;
        }

        private ArithmeticInstruction getArithmeticInstruction(short opcode)
                throws MutationException {
            switch (opcode) {
            case IADD: return new IADD();
            case LADD: return new LADD();
            case FADD: return new FADD();
            case DADD: return new DADD();
            case ISUB: return new ISUB();
            case LSUB: return new LSUB();
            case FSUB: return new FSUB();
            case DSUB: return new DSUB();
            case IMUL: return new IMUL();
            case LMUL: return new LMUL();
            case FMUL: return new FMUL();
            case DMUL: return new DMUL();
            case IDIV: return new IDIV();
            case LDIV: return new LDIV();
            case FDIV: return new FDIV();
            case DDIV: return new DDIV();
            case IREM: return new IREM();
            case LREM: return new LREM();
            case FREM: return new FREM();
            case DREM: return new DREM();
            default:
                throw new MutationException("Opcode not associated with " +
                    "arithmetic operator instruction");
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

    public static class AOPVariant implements Variant {
        private final short opcode;

        private AOPVariant() {
            throw new SofyaError();
        }

        AOPVariant(short opcode) {
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
