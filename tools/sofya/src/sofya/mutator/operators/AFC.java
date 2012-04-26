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

import sofya.mutator.*;
import sofya.mutator.Mutation.Variant;
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.Utility;
import org.apache.bcel.generic.ClassGen;

/**
 * This class implements the Access Flag Change operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 05/23/2006
 */
public class AFC implements MutationOperator {
    private Random rand = new Random();

    /**
     * Access flags:
     * 0 - package public
     * 1 - public
     * 2 - private
     * 4 - protected
     */
    static final byte[] accessFlags = {0, 1, 2, 4};

    public AFC() {
    }

    public String name() {
        return "AFC";
    }

    public String description() {
        return "access flag change";
    }

    public void generateMutants(MutationTable mt, ClassGen cg) {
        Field[] fields = cg.getFields();

        for (int i = 0; i < fields.length; i++) {
            Field field = (Field) fields[i];
            int origAccessFlags = field.getAccessFlags();

            byte excludeFlag = (byte) (origAccessFlags & 0x00000007);
            byte defaultMutationFlag = randomFlag(excludeFlag);

            mt.addMutation(new AFCMutation(cg.getClassName(), field.getName(),
                origAccessFlags, defaultMutationFlag));
        }
    }

    private byte randomFlag(byte exclude) {
        int index = rand.nextInt(accessFlags.length);
        if (accessFlags[index] == exclude) {
            index = (index + 1) % accessFlags.length;
        }

        return accessFlags[index];
    }

    public String toString() {
        return name() + "(" + description() + ")";
    }

    public static class AFCMutation extends ClassMutation {
        private String className;
        private String fieldName;
        private int origAccessFlags;
        private byte defaultMutationFlag;
        private Variant appliedVariant;

        private Field undoField;

        private static final Variant[] flagVariants = new Variant[5];

        static {
            for (int i = 3; i >= 0; i--) {
                flagVariants[4 >> i] = new AFCVariant((byte) (4 >> i));
            }
        }

        private AFCMutation() {
        }

        protected AFCMutation(String className, String fieldName,
                int origAccessFlags, byte defaultMutationFlag) {
            this.className = className;
            this.fieldName = fieldName;
            this.origAccessFlags = origAccessFlags;
            this.defaultMutationFlag = defaultMutationFlag;
        }

        public String getType() {
            return "AFC";
        }

        public Variant getDefaultVariant() {
            return flagVariants[defaultMutationFlag];
        }

        public Variant[] getVariants() {
            Variant[] variants = new Variant[3];
            int skipFlag = origAccessFlags & 0x00000007;
            int index = 0;
            for (int i = 3; i >= 0; i--) {
                int variantFlag = 4 >> i;
                if (variantFlag != skipFlag) {
                    variants[index++] = flagVariants[variantFlag];
                }
            }
            return variants;
        }

        public void accept(MutationVisitor visitor) throws MutationException {
            visitor.visit(this);
        }

        public void apply(ClassGen cg, Variant variant)
                throws MutationException {
            if (!cg.getClassName().equals(className)) {
                throw new MutationException("Wrong class loaded");
            }

            Field field = cg.containsField(fieldName);
            if (field == null) {
                throw new MutationException("Could not find intended field " +
                    "for mutation");
            }

            byte mutantAccessFlag = ((AFCVariant) variant).getAccessFlag();
            field.setAccessFlags(
                (origAccessFlags & 0xFFFFFFF8) | (int) mutantAccessFlag);
            undoField = field;

            appliedVariant = variant;
        }

        public void undo(ClassGen cg) {
            undoField.setAccessFlags(origAccessFlags);
            appliedVariant = null;
        }

        public void serialize(DataOutput out) throws IOException {
            out.writeUTF(className);
            out.writeUTF(fieldName);
            out.writeInt(origAccessFlags);
            if (appliedVariant != null) {
                out.writeByte(((AFCVariant) appliedVariant).getAccessFlag());
            }
            else {
                out.writeByte(defaultMutationFlag);
            }
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            AFCMutation mutation = new AFCMutation();

            mutation.className = in.readUTF();
            mutation.fieldName = in.readUTF();
            mutation.origAccessFlags = in.readInt();
            mutation.defaultMutationFlag = in.readByte();

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
            sb.append(getType());
            sb.append(":");
            sb.append(className);
            sb.append(":");
            sb.append(fieldName);
            sb.append(":");
            sb.append(Utility.accessToString(origAccessFlags));
            sb.append(":");
            sb.append(Utility.accessToString(
                (origAccessFlags & 0xFFFFFFF8) | defaultMutationFlag));
            sb.append(":");
            Variant[] vs = getVariants();
            for (int i = 0; i < vs.length; i++) {
                sb.append(i + 1);
                sb.append(",");
                sb.append(((AFCVariant) vs[i]).toString(origAccessFlags));
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
            sb.append("\n\tfield: ");
            sb.append(fieldName);
            sb.append("\n\toriginal access flags: ");
            sb.append(Utility.accessToString(origAccessFlags));
            sb.append("\n\tmutated access flags: ");
            sb.append(Utility.accessToString(
                (origAccessFlags & 0xFFFFFFF8) | defaultMutationFlag));
            sb.append("\n\tvariants: ");
            Variant[] vs = getVariants();
            for (int i = 0; i < vs.length; i++) {
                sb.append("\n\t\t");
                sb.append(((AFCVariant) vs[i]).toString(origAccessFlags));
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    public static class AFCVariant implements Variant {
        private final byte accessFlag;

        private AFCVariant() {
            throw new SofyaError();
        }

        AFCVariant(byte accessFlag) {
            this.accessFlag = accessFlag;
        }

        byte getAccessFlag() {
            return accessFlag;
        }

        public String toString() {
            return Utility.accessToString(accessFlag);
        }

        public String toString(int otherFlags) {
            String result = Utility.accessToString(
                (otherFlags & 0xFFFFFFF8) | accessFlag);
            if ((accessFlag == 0) && (result.length() == 0)) {
                return "(package public)";
            }
            else {
                return result;
            }
        }
    }
}
