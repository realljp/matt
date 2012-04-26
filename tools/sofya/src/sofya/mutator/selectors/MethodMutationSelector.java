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

package sofya.mutator.selectors;

import java.util.Collection;
import java.util.Map;
import java.util.Iterator;
import java.util.StringTokenizer;

import sofya.mutator.MutationSelector;
import sofya.mutator.Mutation;
import sofya.mutator.MethodMutation;
import sofya.mutator.Mutation.Variant;

import org.apache.bcel.generic.Type;

import gnu.trove.THashMap;

/**
 * Mutation selector that selects by method name and signature.
 *
 * <p>This selector will only select mutations of the {@link MethodMutation}
 * type. It parses method name and signature information extracted
 * from source code by a certain method diffing tool, and uses that
 * information to compare against method names and signatures supplied
 * by BCEL to implement the selection.</p>
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
@SuppressWarnings("unchecked")
public class MethodMutationSelector implements MutationSelector {
    /** Map that stores dot-concatenated class name and method names as keys
        to <code>TypeSignature</code> values recording the formal argument
        and return types. The keys are the nearest 'meeting point' between
        the format supplied by the diff tool and that supplied by BCEL,
        allowing the fastest initial lookup to determine if the mutation is
        selected before applying the more costly signature comparison. */
    protected Map<Object, Object> nameSigTable = new THashMap();

    /**
     * Data class that records signature information about a selected method.
     */
    protected static class TypeSignature {
        /** Argument types of the selected method. */
        public final String argumentTypes;
        /** Return type of the selected method. */
        public final String returnType;

        private TypeSignature() {
            throw new AssertionError("Illegal constructor");
        }

        /**
         * Creates a new type signature for a method.
         *
         * @param argTypes Argument types of the method.
         * @param retType Return type of the method.
         */
        TypeSignature(String argTypes, String retType) {
            this.argumentTypes = argTypes;
            this.returnType = retType;
        }
    }

    protected MethodMutationSelector() {
    }

    /**
     * Creates a new mutation selector.
     *
     * @param methodNames Array of method descriptions supplied by the
     * diffing tool. Mutations occurring in the selected methods will
     * be selected.
     */
    public MethodMutationSelector(String[] methodNames) {
        for (int i = 0; i < methodNames.length; i++) {
            addMethod(methodNames[i]);
        }
    }

    /**
     * Creates a new mutation selector.
     *
     * @param methodNames Collection of method descriptions supplied by the
     * diffing tool, as strings. Mutations occurring in the selected methods
     * will be selected.
     */
    public MethodMutationSelector(Collection<String> methodNames) {
        int size = methodNames.size();
        Iterator<String> iterator = methodNames.iterator();
        for (int i = size; i-- > size; ) {
            addMethod(iterator.next());
        }
    }

    /** 
     * {@inheritDoc}.
     *
     * <p>Argument and return types are compared using an ends-with test
     * against the string conversion of the types retrieved from the
     * mutation. This is because the method description format it is
     * being compared against (apparently) originates from source code
     * where fully qualified type names are rarely used (only if
     * disambiguation is required). Note that this approach is NOT
     * implemented commutatively, since we know that BCEL will always
     * return the fully qualified type string (thus an unqualified class
     * name is considered a separate type from one with package qualifiers,
     * a distinction permitted by the Java type system as a consequence
     * of the implicit namespaces created by Java packages). The safety
     * of this approach is guaranteed by the requirement that ambiguous
     * type names be fully qualified in the source code, as mentioned
     * above.</p>
     *
     * @param mutation {@inheritDoc}
     */
    public boolean isSelected(Mutation mutation) {
        if (!(mutation instanceof MethodMutation)) {
            return false;
        }

        MethodMutation mm = (MethodMutation) mutation;

        String nameKey = mm.getClassName() + "." + mm.getMethodName();

        TypeSignature tSig = (TypeSignature) nameSigTable.get(nameKey);
        if (tSig == null) {
            return false;
        }

        String mutSignature = mm.getSignature();
        Type mutReturnType = Type.getReturnType(mutSignature);
        Type[] mutArgTypes = Type.getArgumentTypes(mutSignature);

        if (!mutReturnType.toString().endsWith(tSig.returnType)) {
            return false;
        }

        int i = 0;
        StringTokenizer argTypes = new StringTokenizer(tSig.argumentTypes, "_");
        for ( ; argTypes.hasMoreTokens(); i++) {
            String argType = argTypes.nextToken();
            // System.out.println(descArgType);
            // System.out.println(mutArgTypes[i].toString());

            if (i >= mutArgTypes.length) {
                return false;
            }

            if (!mutArgTypes[i].toString().endsWith(argType)) {
                return false;
            }
        }

        if (i < mutArgTypes.length) {
            return false;
        }

        return true;
    }

    public Variant getVariant(Mutation mutation) {
        return mutation.getDefaultVariant();
    }
    
    public int getRequestedVariant(Mutation mutation) {
    	return 0; // stub this for now...
    }

    public void setMutationCount(int mutationCount) {
        // This selector doesn't care
    }

    /**
     * Adds a selected method.
     *
     * <p>This method is responsible for parsing a textual method description
     * received from the diffing tool.</p>
     */
    protected void addMethod(String methodDesc) {
        // No spaces are allowed in class or method names, so seek the
        // first underscore (space substitute), then seek backwards
        // to the last dot to find the end of the class name and
        // extract it
        int classEnd = methodDesc.indexOf('_');
        String descClassName = methodDesc.substring(0, classEnd);
        classEnd = descClassName.lastIndexOf('.');
        descClassName = descClassName.substring(0, classEnd);

        // Parse the argument types using the locations of the parentheses
        int argStart = methodDesc.indexOf('(');
        int argEnd = methodDesc.indexOf(')');
        String descArgs = methodDesc.substring(argStart + 1, argEnd);

        // Because the method name cannot contain spaces, seek the last
        // underscore before the start position of the arguments to extract
        // the method name (this prevents the access qualifiers and return
        // type from being erroneously included)
        String attrName = methodDesc.substring(classEnd + 1, argStart);
        int nameStart = attrName.lastIndexOf('_');
        String descMethodName = attrName.substring(nameStart + 1);

        // Seek the last underscore before the start position of the method
        // name to extract the return type (this prevents the access
        // qualifiers from being erroneously included)
        String descAttr = attrName.substring(0, nameStart);
        int retStart = descAttr.lastIndexOf('_');
        String descRet = descAttr.substring(retStart + 1);

        // System.out.println("class name: " + descClassName);
        // System.out.println("method name: " + descMethodName);
        // System.out.println("return type: " + descRet);
        // System.out.println("arg types: " + descArgs);

        nameSigTable.put(descClassName + "." + descMethodName,
            new TypeSignature(descArgs, descRet));
    }

    /**
     * Test driver to verify the textual method parsing and comparison
     * function.
     */
    public static void main(String[] argv) {
        MethodMutationSelector mms = new MethodMutationSelector(new String[]{
            "org.apache.tools.ant.taskdefs.Execute$Java13CommandLauncher."+
            "public_java.lang.Process_exec("+
            "edu.unl.Project_String[]_String[]_File)"}
          );

        System.out.println(
            mms.isSelected(
                new MethodMutation() {
                    public String getClassName() {
                        return "org.apache.tools.ant.taskdefs.Execute$" +
                            "Java13CommandLauncher";
                    }

                    public String getMethodName() {
                        return "exec";
                    }

                    public String getSignature() {
                        return "(Ledu/unl/Project;[Ljava/lang/String;" +
                            "[Ljava/lang/String;Ljava/io/File;)" +
                            "Ljava/lang/Process;";
                    }

                    public String getType() {
                        return "test";
                    }

                    public Variant getDefaultVariant() {
                        return null;
                    }

                    public Variant[] getVariants() {
                        return new Variant[0];
                    }

                    public void apply(org.apache.bcel.generic.ClassGen cg,
                                      Variant variant) {
                        throw new UnsupportedOperationException("Anonymous " +
                            "test class only");
                    }

                    public void undo(org.apache.bcel.generic.ClassGen cg) {
                        throw new UnsupportedOperationException("Anonymous " +
                            "test class only");
                    }

                    public void serialize(java.io.DataOutput out) {
                        throw new UnsupportedOperationException("Anonymous " +
                            "test class only");
                    }

                    public void accept(
                            sofya.mutator.MutationVisitor visitor) {
                    }

                    public String print() {
                        return "test class only";
                    }
                }
            )
        );
    }
}
