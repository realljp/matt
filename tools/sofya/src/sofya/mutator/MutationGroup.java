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

package sofya.mutator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.bcel.generic.ClassGen;

import sofya.mutator.Mutation.Variant;

/************************************************************************
 * A group of related mutations that can be applied together.
 *
 * <p>A mutation group also has an associated ID. When selecting mutations
 * by ID, mutations in a mutation group can only be selected if the
 * ID of the mutation group is also selected. However, selecting a mutation
 * group does not automatically cause all member mutations to be selected
 * (mutations in the group can be selectively applied).</p>
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
public class MutationGroup implements Mutation {
    /** List of mutations contained in this group. */
    private List<Mutation> mutations = new ArrayList<Mutation>();
    /** Name of the class to which the contained mutants apply. */
    private String className;
    /** Name of the method to which the contained mutants may apply. */
    private String methodName;
    /** Signature of the method to which the contained mutants may apply. */
    private String signature;
    /** ID associated with this mutation group. */
    private MutationID id;
    /** Flag indicating whether a visitor has requested that the mutations in
        this group be visited. */
    private boolean visitMembers = true;
    public int requestedVariant = 0;

    private MutationGroup() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new mutation group for mutations that apply to a given class.
     *
     * @param className Name of the class to which the contained mutants will
     * apply.
     */
    public MutationGroup(String className) {
        this.className = className;
    }

    /**
     * Creates a new mutation group for mutations that apply to a given method
     * in a class.
     *
     * @param className Name of the class to which the contained mutants will
     * apply.
     * @param methodName Name of the method to which the contained mutants
     * will apply.
     * @param signature Signature of the method to which the contained mutants
     * will apply.
     */
    public MutationGroup(String className, String methodName,
            String signature) {
        this(className);
        this.methodName = methodName;
        this.signature = signature;
    }

    /**
     * Adds a mutation to this mutation group.
     *
     * @param mutation Mutation to be added to this group.
     */
    public void addMutation(GroupableMutation mutation) {
        mutation.setParent(this);
        mutations.add(mutation);
    }

    /**
     * Gets the class name to which the contained mutants apply.
     *
     * @return The name of the class to which the mutations in this group
     * are applied.
     */
    public String getClassName() {
        return className;
    }

    /**
     * Gets the method name to which the contained mutants apply.
     *
     * @return The name of the method to which the mutations in this group
     * are applied.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the signature of the method to which the contained mutants apply.
     *
     * @return The signature of the method to which the mutations in this group
     * are applied.
     */
    public String getSignature() {
        return signature;
    }

    /** 
     * Gets the number of mutations in this group.
     *
     * @return The number of mutation in this group.
     */
    public int size() {
        return mutations.size();
    }

    /**
     * Returns an iterator over the mutations in this group.
     *
     * @return An iterator over the mutations in this group.
     */
    public Iterator<Mutation> iterator() {
        return mutations.iterator();
    }

    /**
     * Gets the type string associated with a mutation group.
     *
     * @return The string &quot;group&quot;.
     */
    public String getType() {
        return "group";
    }

    public MutationID getID() {
        return id;
    }

    public void setID(MutationID id) {
        this.id = id;
    }
    
    /**
     * sets the variant requested by the user on the command-line.
     * This variant should be the absolute mutant variant as
     * reported by the sofya.mutator.MutationGenerator
     * @param m integer value of the mutant requested in this group
     */
    public void setRequestedVariant(int m) {
    	requestedVariant = m;
    }

    public void visitMembers(boolean b) {
        this.visitMembers = b;
    }

    public void accept(MutationVisitor visitor) throws MutationException {
        visitor.visit(this, true);

        if (visitMembers) {
            int size = mutations.size();
            Iterator iterator = mutations.iterator();
            for (int i = size; i-- > 0; ) {
                GroupableMutation gm = (GroupableMutation) iterator.next();
                // requestedVariant == -1 implies this is a print all variants case
                if(!(gm.getID().asInt() == requestedVariant) && requestedVariant != -1) {
                	continue;
                }
                gm.accept(visitor);
            }

            visitor.visit(this, false);
        }
    }

    public Variant getDefaultVariant() {
        return null;
    }
    /**
     * get the set of variants applicable in this mutation group.
     * @return array of Variant objects in this mutation group
     */
    public Variant[] getVariants() {
        int size = mutations.size();
        Iterator iterator = mutations.iterator();
        Variant[] retv = new Variant[size];
        LinkedHashMap<Integer, Variant> variant_map = new LinkedHashMap<Integer, Variant>();
        for (int i = size; i-- > 0; ) {
        	GroupableMutation gm = (GroupableMutation) iterator.next();
        	Variant real_var = ((GroupableMutation) gm).getVariant();
        	variant_map.put(new Integer(gm.getID().asInt()), real_var);
        }
        Iterator<Variant> variter = variant_map.values().iterator();
        int i = 0;
        while (variter.hasNext()) {
        	retv[i] = variter.next();
        	i++;
        }
        return retv;
    }

    /**
     * The mutations contained in this group can be selected and applied
     * individually, therefore this method does nothing.
     *
     * @param cg BCEL classfile object for the class being mutated.
     * @param variant Variant of the mutation to be applied.
     *
     * @throws MutationException Never.
     */
    public void apply(ClassGen cg, Variant variant) throws MutationException {
    }

    /**
     * The mutations contained in this group can be selected and applied
     * individually, therefore this method does nothing.
     *
     * @param cg BCEL classfile object for the class being mutated.
     */
    public void undo(ClassGen cg) {
    }

    public void serialize(DataOutput out)
            throws IOException {
        out.writeUTF(className);
        if (methodName != null) {
            out.writeByte(1);
            out.writeUTF(methodName);
        }
        else {
            out.writeByte(0);
        }
        if (signature != null) {
            out.writeByte(1);
            out.writeUTF(signature);
        }

        int count = mutations.size();
        out.writeInt(count);

        for (int i = 0; i < count; i++) {
            Mutation mutation = (Mutation) mutations.get(i);
            MutationHandler.writeMutation(out, mutation);
        }
    }

    public static MutationGroup deserialize(DataInput in) throws IOException {
        String className = in.readUTF();
        String methodName = null;
        String signature = null;
        if (in.readByte() == 1) {
            methodName = in.readUTF();
        }
        if (in.readByte() == 1) {
            signature = in.readUTF();
        }

        MutationGroup mg = new MutationGroup(className, methodName, signature);

        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            mg.addMutation(
                (GroupableMutation) MutationHandler.readMutation(in));
        }

        return mg;
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
        sb.append("group:");
        sb.append(size());
        sb.append(":");
        sb.append(className);
        sb.append(":");
        sb.append(methodName);
        sb.append(":");
        sb.append(signature);
        return sb.toString();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Mutation group {\n");
        if (getID() != null) {
            sb.append("\tid: ");
            sb.append(getID().asInt());
        }
        else {
            sb.append("\tid not assigned");
        }
        sb.append("\n\tsize: ");
        sb.append(size());
        sb.append("\n\tclass: ");
        sb.append(className);
        sb.append("\n\tmethod: ");
        sb.append(methodName);
        sb.append("\n\tsignature: ");
        sb.append(signature);
        sb.append("\n}");
        return sb.toString();
    }
}
