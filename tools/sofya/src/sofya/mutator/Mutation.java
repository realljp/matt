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

import java.io.DataOutput;
import java.io.IOException;

import org.apache.bcel.generic.ClassGen;

/************************************************************************
 * A mutation implements the transformation(s) that create a
 * &quot;mutated&quot; variant of a Java class.
 *
 * <p>A mutation class should also provide a static method named
 * <code>deserialize</code> that takes a single parameter of type
 * <code>DataInput</code> and returns a Mutation. This method is
 * used by the framework to provide transparent automatic support
 * for new mutation operators, though it cannot be enforced by
 * the interface due to the need for a static method.</p>
 *
 * @author Alex Kinneer
 * @version 05/23/2006
 *
 * @see MutationOperator
 * @see MutationGenerator
 * @see Mutator
 */
public interface Mutation {
    /**
     * Accepts a mutation visitor to perform some operation using this mutation.
     *
     * @param visitor Mutation visitor that is visiting this mutation.
     *
     * @throws MutationException If an action performed on this mutation by the
     * visitor fails (this permits the exception to propagate).
     */
    void accept(MutationVisitor visitor) throws MutationException;

    /**
     * Applies this mutation to the class file.
     *
     * @param cg BCEL classfile object for the class being mutated.
     * @param variant Index of the variant of the mutation to be applied.
     *
     * @throws MutationException If application of this mutation fails for any
     * reason.
     */
    void apply(ClassGen cg, Variant variant) throws MutationException;

    /**
     * Reverses the actions performed to apply this mutation. This method is
     * used by the {@link Mutator} to automatically screen out illegal
     * mutants when mutant verification is enabled.
     *
     * @param cg BCEL classfile object for the class to which this mutation
     * was applied.
     */
    void undo(ClassGen cg);

    /**
     * Gets all of the possible variants of this mutation.
     *
     * <p>A variant is one of set of mutually exclusive mutations that can be
     * applied at the same code location. The simplest example of this is an
     * operator mutation, where mutliple alternate operators can be
     * substituted to produce a mutant.</p>
     *
     * @return An array of the possible variants of this mutation.
     */
    Variant[] getVariants();

    /**
     * Gets the default variant selected by the mutation generator when the
     * mutation table was generated.
     *
     * <p>A default variant ensures that a mutation can be applied without
     * requiring user intervention. The policy for selecting an initial
     * default is determined by the generating mutation operator, and is
     * typically random.</p>
     *
     * @return A variant of this mutation selected during mutation table
     * generation. <strong>Mutations that do not support variants may return
     * <code>null</code> from this method.</strong>
     */
    Variant getDefaultVariant();

    /**
     * Gets the operator type abbreviation associated with this mutation.
     *
     * @return The operator type that generated this mutation.
     */
    String getType();

    /**
     * Gets the ID associated with this mutation.
     *
     * <p>Mutation IDs are automatically assigned and managed by the
     * framework.</p>
     *
     * @return The ID of this mutation.
     */
    MutationID getID();

    /**
     * Sets the ID associated with this mutation.
     *
     * <p>This method is called automatically by the framework,
     * and should not be called anywhere else.</p>
     *
     * @param mid ID to be assigned to this mutation.
     */
    void setID(MutationID mid);

    /**
     * Serializes this mutation.
     *
     * <p>A mutation class should also provide a static method named
     * <code>deserialize</code> that takes a single parameter of type
     * <code>DataInput</code> and returns a Mutation. This method is
     * used by the framework to provide transparent automatic support
     * for new mutation operators, though it cannot be enforced by
     * the interface due to the need for a static method.</p>
     *
     * @param out Data output sink to which the mutation should be serialized.
     *
     * @throws IOException If serialization of this mutation fails for any
     * I/O related reason.
     */
    void serialize(DataOutput out) throws IOException;

    /**
     * Prints this mutation in a format suitable for display.
     *
     * @return A string giving information about the mutation in a format
     * suitable for display.
     */
    String print();

    /**
     * A variant of a mutation representing one of a number of mutually
     * exclusive substitutions that can be used to produce a mutant.
     *
     * <p>The simplest example of a mutation that yields variants is an
     * an operator mutation, where multiple alternate operators can be
     * substituted to produce a mutant.</p>
     */
    static interface Variant {
        String toString();
    }
}
