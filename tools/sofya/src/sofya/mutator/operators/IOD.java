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
 * This class implements the Overriding Method Deletion operator.
 *
 * <p>This operator will delete overriding static methods, but does not
 * attempt to mutate <code>INVOKESTATIC</code> instructions referencing
 * those methods (an operation that must be applied globally to all
 * classes). The Java Virtual Machine specification does not strictly
 * permit this, however in practice the classes pass verification
 * (at least on Sun JVMs). However, this will cause failures when
 * automatic verification of mutants is enabled. A workaround is to
 * apply all other mutants in a first pass, then apply mutants
 * deleting overriding static methods in a second pass with
 * verification disabled.</p>
 *
 * @author Hyunsook Do
 * @author Alex Kinneer
 * @version 06/09/2006
 */
public class IOD implements MutationOperator {

    public IOD() {
    }

    public String name() {
        return "IOD";
    }

    public String description() {
        return "overriding method deletion";
    }

    public void generateMutants(MutationTable mt, ClassGen cg) {
        JavaClass clazz = cg.getJavaClass();
        JavaClass[] supers = null;
        Method[] methods = cg.getMethods();

        try {
            supers = clazz.getSuperClasses();
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }

        for (int n = 0; n < supers.length; n++) {
            Method[] super_methods = supers[n].getMethods();

            for (int i = 0; i < super_methods.length; i++) {
                Method sMethod = super_methods[i];

                String sName = sMethod.getName();
                String sSignature = sMethod.getSignature();

                // If the method in the superclass is private,
                // an initializer, or a main method, ignore since
                // it can't be overridden
                if (sMethod.isPrivate() || sName.equals("main") ||
                        sName.equals("<init>") || sName.equals("<clinit>")) {
                    continue;
                }

                // Package-public - if the superclass is in a different
                // package it is not an override
                if ((sMethod.getAccessFlags() & (0x00000007)) == 0) {
                    if (!clazz.getPackageName().equals(
                            supers[n].getPackageName())) {
                        continue;
                    }
                }

                // If there is an override of the method in this class,
                // record the mutant
                for (int j = 0; j < methods.length; j++) {
                    Method method = methods[j];

                    if (method.getName().equals(sName)
                            && method.getSignature().equals(sSignature)) {
                        mt.addMutation(new IODMutation(cg.getClassName(),
                            sName, sSignature, supers[n].getClassName()));
                        break;
                    }
                }
            }
        }
    }

    public static class IODMutation extends ClassMutation {
        private String className;
        private String methodName;
        private String signature;
        private String superClass;

        private Method undoMethod;

        private IODMutation() {
        }

        protected IODMutation(String className, String methodName,
                String signature, String superClass) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.superClass = superClass;
        }

        public String getType() {
            return "IOD";
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

            Method m = cg.containsMethod(methodName, signature);
            if (m == null) {
                throw new MutationException("Could not find intended method " +
                    "for mutation");
            }
            undoMethod = m;

            cg.removeMethod(m);
        }

        public void undo(ClassGen cg) {
            cg.addMethod(undoMethod);
        }

        public void serialize(DataOutput out) throws IOException {
            out.writeUTF(className);
            out.writeUTF(methodName);
            out.writeUTF(signature);
            out.writeUTF(superClass);
        }

        public static Mutation deserialize(DataInput in) throws IOException {
            IODMutation mutation = new IODMutation();

            mutation.className = in.readUTF();
            mutation.methodName = in.readUTF();
            mutation.signature = in.readUTF();
            mutation.superClass = in.readUTF();

            return mutation;
        }

        public String print() {
            StringBuffer sb = new StringBuffer();
            if (getID() != null) {
                sb.append(getID().asInt());
                sb.append(": ");
            }
            else {
                sb.append("-: ");
            }
            sb.append(getType());
            sb.append(": ");
            sb.append(className);
            sb.append(": ");
            sb.append(methodName);
            sb.append(": ");
            sb.append(signature);
            sb.append(": ");
            sb.append(superClass);
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
            sb.append("\n\tdefining superclass: ");
            sb.append(superClass);
            sb.append("\n}");
            return sb.toString();
        }
    }
}
