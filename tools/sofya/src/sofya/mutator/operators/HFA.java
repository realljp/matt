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
import java.util.Set;

import sofya.mutator.*;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.base.exceptions.SofyaError;

import org.apache.bcel.Repository;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;
import static org.apache.bcel.Constants.*;

import gnu.trove.THashSet;

/**
 * This class implements the Hiding Field variable Addition operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 06/09/2006
 */
public class HFA implements MutationOperator {
    /** Stores fields for which a mutation has already been generated. */
    private Set<Object> mutFieldNames;

    public HFA() {
    }

    public String name() {
        return "HFA";
    }

    public String description() {
        return "hiding field variable addition";
    }

    @SuppressWarnings("unchecked")
    public void generateMutants(MutationTable mt, ClassGen cg) {
        JavaClass clazz = cg.getJavaClass();
        JavaClass[] supers = null;
        JavaClass[] interfaces = null;

        try {
            supers = clazz.getSuperClasses();
            interfaces = clazz.getAllInterfaces();
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }

        Field[] fields = cg.getFields();
        mutFieldNames = new THashSet();

        // Look for superclass fields to hide
        for (int n = 0; n < supers.length; n++) {
            Field[] super_fields = supers[n].getFields();

            superSearchLoop:
            for (int i = 0; i < super_fields.length; i++) {
                Field sField = super_fields[i];
                String sName = sField.getName();

                // Can't hide a private variable
                if (sField.isPrivate()) {
                    continue;
                }

                // Can't hide a package-public variable if we're
                // in a different package
                if ((sField.getAccessFlags() & (0x00000007)) == 0) {
                    if (!clazz.getPackageName().equals(
                            supers[n].getPackageName())) {
                        continue;
                    }
                }

                // Check whether there is already a hiding field
                for (int j = 0; j < fields.length; j++) {
                    Field field = fields[j];

                    if (field.getName().equals(sName)) {
                        continue superSearchLoop;
                    }
                }

                // A field might be declared in multiple superclasses
                // or interfaces, so skip it if we've already generated
                // a mutation for it
                if (mutFieldNames.contains(sName)) {
                    continue;
                }

                mt.addMutation(new HFAMutation(cg.getClassName(),
                    sName, supers[n].getClassName()));

                mutFieldNames.add(sName);
            }
        }

        for (int n = 0; n < interfaces.length; n++) {
            Field[] ifc_fields = interfaces[n].getFields();

            ifcSearchLoop:
            for (int i = 0; i < ifc_fields.length; i++) {
                Field ifcField = ifc_fields[i];
                String ifcName = ifcField.getName();

                // Check whether there is already a hiding field
                for (int j = 0; j < fields.length; j++) {
                    Field field = fields[j];

                    if (field.getName().equals(ifcName)) {
                        continue ifcSearchLoop;
                    }
                }

                if (mutFieldNames.contains(ifcName)) {
                    continue;
                }

                mt.addMutation(new HFAMutation(cg.getClassName(),
                    ifcName, interfaces[n].getClassName()));

                mutFieldNames.add(ifcName);
            }
        }

        mutFieldNames = null;
    }


    public static class HFAMutation extends ClassMutation {
        private String className;
        private String fieldName;
        private String overriddenParent;

        private Field undoField;
        //private TObjectIntHashMap undoInits;

        private HFAMutation() {
        }

        protected HFAMutation(String className, String fieldName,
                String overriddenParent) {
            this.className = className;
            this.fieldName = fieldName;
            this.overriddenParent = overriddenParent;
        }

        public String getType() {
            return "HFA";
        }

        public Variant getDefaultVariant() {
            return null;
        }

        public Variant[] getVariants() {
            return new Variant[0];
        }

        public void accept(MutationVisitor visitor) throws MutationException {
            visitor.visit(this);
        }

        public void apply(ClassGen cg, Variant variant)
                throws MutationException {
            if (!cg.getClassName().equals(className)) {
                throw new MutationException("Wrong class loaded");
            }

            // Load the class containing the field to be hidden
            JavaClass superClass = null;
            try {
                superClass = Repository.lookupClass(overriddenParent);
            }
            catch (ClassNotFoundException e) {
                throw new MutationException("Could not load class containing " +
                    "variable to be hidden");
            }

            // Find the field to be hidden
            Field[] super_fields = superClass.getFields();
            Field targetField = null;
            for (int i = 0; i < super_fields.length; i++) {
                if (super_fields[i].getName().equals(fieldName)) {
                    targetField = super_fields[i];
                    break;
                }
            }

            if (targetField == null) {
                throw new MutationException("Could not find field to be " +
                    "hidden");
            }

            // Copy the field and add it to the subclass
            FieldGen fg = new FieldGen(targetField.getAccessFlags(),
                targetField.getType(), targetField.getName(),
                cg.getConstantPool());
            Field newField = fg.getField();
            cg.addField(newField);
            undoField = newField;

            // Patch initializers
            /*undoInits = new TObjectIntHashMap();
            Method[] methods = cg.getMethods();

            if (targetField.isStatic()) {
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i].getName().equals("<clinit>")) {
                        insertInitialization(cg, newField, methods[i], i);
                        undoInits.put(methods[i], i);
                        break;
                    }
                }
            }
            else {
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i].getName().equals("<init>")) {
                        insertInitialization(cg, newField, methods[i], i);
                        undoInits.put(methods[i], i);
                    }
                }
            }*/
        }

        @SuppressWarnings("unused")
        private void insertInitialization(ClassGen cg, Field f, Method m, int mPos) {
            ConstantPoolGen cpg = cg.getConstantPool();
            MethodGen mg = new MethodGen(m, className, cpg);
            InstructionList il = mg.getInstructionList();

            InstructionFactory iFactory = new InstructionFactory(cg);
            InstructionList patch = new InstructionList();

            if (f.isStatic()) {
                patch.append(defaultValue(f, cpg));
                patch.append(iFactory.createPutStatic(className, fieldName,
                    f.getType()));

                // Insert the patch at the beginning of the method
                il.insert(patch);
            }
            else {
                patch.append(new ALOAD(0));
                patch.append(defaultValue(f, cpg));
                patch.append(iFactory.createPutField(className, fieldName,
                    f.getType()));

                // Find correct insertion point (after call to superclass
                // constructor)
                InstructionHandle ih = il.getStart();
                for ( ; ih != null; ih = ih.getNext()) {
                    if (ih.getInstruction().getOpcode() == INVOKESPECIAL) {
                        il.append(ih, patch);
                        break;
                    }
                }
            }

            il.setPositions();
            mg.setInstructionList(il);
            mg.setMaxStack();
            cg.setMethodAt(mg.getMethod(), mPos);
            patch.dispose();
        }

        @SuppressWarnings("unused")
        private void copyInitValue(FieldGen sField, FieldGen field) {
            String initValue = sField.getInitValue();
            if (initValue == null) return;

            byte type = sField.getType().getType();
            switch (type) {
            case T_BOOLEAN:
                field.setInitValue(Boolean.valueOf(initValue).booleanValue());
                break;
            case T_CHAR:
                field.setInitValue(initValue.charAt(0));
                break;
            case T_BYTE:
                field.setInitValue(Byte.parseByte(initValue));
                break;
            case T_SHORT:
                field.setInitValue(Short.parseShort(initValue));
                break;
            case T_INT:
                field.setInitValue(Integer.parseInt(initValue));
                break;
            case T_LONG:
                field.setInitValue(Long.parseLong(initValue));
                break;
            case T_FLOAT:
                field.setInitValue(Float.parseFloat(initValue));
                break;
            case T_DOUBLE:
                field.setInitValue(Double.parseDouble(initValue));
                break;
            default:
                throw new SofyaError("Field type cannot have initial value");
            }
        }

        private Instruction defaultValue(Field f, ConstantPoolGen cpg) {
            byte type = f.getType().getType();
            switch (type) {
            case T_BOOLEAN:
                return new PUSH(cpg, false).getInstruction();
            case T_CHAR:
                return new PUSH(cpg, '\0').getInstruction();
            case T_BYTE:
            case T_SHORT:
            case T_INT:
                return new PUSH(cpg, 0).getInstruction();
            case T_LONG:
                return new PUSH(cpg, (long) 0).getInstruction();
            case T_FLOAT:
                return new PUSH(cpg, (float) 0).getInstruction();
            case T_DOUBLE:
                return new PUSH(cpg, (double) 0).getInstruction();
            case T_ARRAY:
            case T_OBJECT:
                return new ACONST_NULL();
            default:
                throw new SofyaError("Illegal field type");
            }
        }

        public void undo(ClassGen cg) {
            cg.removeField(undoField);
            /*int size = undoInits.size();
            TObjectIntIterator iterator = undoInits.iterator();
            for (int i = size; i-- > 0; ) {
                iterator.advance();
                cg.setMethodAt((Method) iterator.key(), iterator.value());
            }*/
        }

        public void serialize(DataOutput out) throws IOException {
            out.writeUTF(className);
            out.writeUTF(fieldName);
            out.writeUTF(overriddenParent);
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            HFAMutation mutation = new HFAMutation();

            mutation.className = in.readUTF();
            mutation.fieldName = in.readUTF();
            mutation.overriddenParent = in.readUTF();

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
            sb.append(overriddenParent);
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
            sb.append("\n\tdefining superclass: ");
            sb.append(overriddenParent);
            sb.append("\n}");
            return sb.toString();
        }
    }
}
