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

import java.util.Map;

import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;

/************************************************************************
 * Marks a mutation as <em>groupable</em>, which indicates that the
 * mutation can be applied to the bytecode of a particular method at the
 * same time as other mutations (usually of the same type).
 *
 * <p>Implementing this interface allows suitable mutations to be applied
 * more efficiently, typically by allowing all of the mutations to be
 * applied in one pass over the method's instruction list, rather than
 * repeatedly seeking to the right position, applying the mutation, and
 * committing the changed bytecode. Mutations implementing this interface
 * are of course permitted to take advantage of other optimizations that
 * may be feasible as well.</p>
 *
 * <p>Note that a mutation implementing this interface must be added
 * to a {@link MutationGroup} or the efficiency benefit is lost.</p>
 *
 * @author Alex Kinneer
 * @version 05/09/2006
 */
public interface GroupableMutation extends Mutation {
    /**
     * Applies the mutation; provides access to data to enable more
     * efficient application.
     *
     * @param cg BCEL classfile object for the class being mutated.
     * @param mg BCEL method object for the method being mutated.
     * @param il BCEL instruction list for the method being mutated.
     * @param linkData Map to permit grouped mutations to pass information to
     * each other.
     * @param variant Index of the variant of the mutation to be applied.
     *
     * @throws MutationException If application of this mutation fails for any
     * reason.
     */
    void apply(ClassGen cg, MethodGen mg, InstructionList il,
            Map<Object, Object> linkData, Variant variant)
            throws MutationException;

    /**
     * Reverses the actions performed to apply this mutation; provides access
     * to the additional data used by this mutation for appropriate
     * reversal of changes. This method is used by the {@link Mutator} to
     * automatically screen out illegal mutants when mutant verification
     * is enabled.
     *
     * @param cg BCEL classfile object for the class to which this mutation
     * was applied.
     * @param mg BCEL method object for the method to which this mutation
     * was applied.
     * @param il BCEL instruction list for the method to which this mutation
     * was applied.
     * @param linkData Map used to permit grouped mutations to pass information
     * to each other.
     */
    void undo(ClassGen cg, MethodGen mg, InstructionList il,
            Map<Object, Object> linkData);

    /**
     * Gets the parent mutation group of which this mutation is a member.
     *
     * @return The mutation group containing this mutation. This may be
     * <code>null</code>, in which case efficiency benefits associated
     * with implementing the {@link GroupableMutation} interface will
     * be lost.
     */
    MutationGroup getParent();

    /**
     * Sets the parent mutation group of which this mutation is a member.
     *
     * <p>This method is automatically invoked when this mutation is added
     * to a mutation group. It should not normally be called otherwise.</p>
     *
     * @param mg Mutation group that now contains this mutation.
     */
    void setParent(MutationGroup mg);
    
    /**
     * gets the base variant (typically as an instruction type variant
     * e.g. ROPVariant) for this groupable mutation.  The simple get
     * getVariants() call doesn't support giving only the variants and
     * requires a lot of logic to retrieve the desired mutation from
     * the grouped mutations.  This is intended for use in group mutation
     * enumeration.
     * @return Variant object within this groupable mutation
     * Added 8/26/2011 wdm
     */
    Variant getVariant();

}
