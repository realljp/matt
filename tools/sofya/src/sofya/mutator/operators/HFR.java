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

import sofya.mutator.*;
import sofya.base.exceptions.IncompleteClasspathException;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

/**
 * This class implements the Hiding Field variable Removal operator.
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 06/09/2006
 */
public class HFR implements MutationOperator {

    public HFR() {
    }

    public String name() {
        return "HFR";
    }

    public String description() {
        return "hiding field variable removal";
    }

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

        for (int i = 0; i < fields.length; i++) {
            Field field = (Field) fields[i];
            String name = field.getName();
            String signature = field.getSignature();

            boolean isHiding = false;
            String overriddenParent = null;

            // Search all superclasses first
            for (int n = 0; (n < supers.length) && !isHiding; n++) {
                Field[] super_fields = supers[n].getFields();

                for (int j = 0; j < super_fields.length; j++) {
                    Field sField = super_fields[j];

                    if (sField.getName().equals(name)) {
                        // A private field can't be hidden
                        if (sField.isPrivate()) {
                            continue;
                        }

                        // Package-public - if the superclass is in
                        // a different package the field wouldn't be
                        // visible
                        if ((sField.getAccessFlags() & (0x00000007)) == 0) {
                            if (!clazz.getPackageName().equals(
                                    supers[n].getPackageName())) {
                                continue;
                            }
                        }

                        // If we delete a non-final field to reveal a final
                        // field, it is highly likely to cause problems
                        if (sField.isFinal() && !field.isFinal()) {
                            continue;
                        }

                        // Matching the signature is not strictly necessary,
                        // but presumably screens out type errors that would
                        // likely result from the deletion
                        if (!sField.getSignature().equals(signature)) {
                            continue;
                        }

                        isHiding = true;
                        overriddenParent = supers[n].getClassName();
                        break;
                    }
                }
            }

            // Search all interfaces (if necessary). Note that an
            // interface field is always public, static and final.
            if (field.isFinal()) {
                for (int n = 0; (n < interfaces.length) && !isHiding; n++) {
                    Field[] ifc_fields = interfaces[n].getFields();

                    for (int j = 0; j < ifc_fields.length; j++) {
                        Field ifcField = ifc_fields[j];

                        if (ifcField.getName().equals(name)) {
                            if (!ifcField.getSignature().equals(signature)) {
                                continue;
                            }

                            isHiding = true;
                            overriddenParent = interfaces[n].getClassName();
                            break;
                        }
                    }
                }
            }

            if (isHiding) {
                mt.addMutation(new HFRMutation(cg.getClassName(),
                    name, overriddenParent));
            }
        }
    }

    public static class HFRMutation extends ClassMutation {
        private String className;
        private String fieldName;
        private String overriddenParent;

        private Field undoField;

        private HFRMutation() {
        }

        protected HFRMutation(String className, String fieldName,
                String overriddenParent) {
            this.className = className;
            this.fieldName = fieldName;
            this.overriddenParent = overriddenParent;
        }

        public String getType() {
            return "HFR";
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

            Field field = cg.containsField(fieldName);
            if (field == null) {
                throw new MutationException("Could not find intended field " +
                    "for mutation");
            }
            undoField = field;

            cg.removeField(field);
        }

        public void undo(ClassGen cg) {
            cg.addField(undoField);
        }

        public void serialize(DataOutput out) throws IOException {
            out.writeUTF(className);
            out.writeUTF(fieldName);
            out.writeUTF(overriddenParent);
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            HFRMutation mutation = new HFRMutation();

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
