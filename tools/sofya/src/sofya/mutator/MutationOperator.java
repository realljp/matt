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

import org.apache.bcel.generic.ClassGen;

/************************************************************************
 * A mutation operator defines and implements a transformation that
 * can be applied to a Java class file to create a &quot;mutated&quot;
 * version of the class.
 *
 * @author Alex Kinneer
 * @version 09/20/2005
 *
 * @see Mutation
 * @see MutationGenerator
 * @see Mutator
 */
public interface MutationOperator {
    /**
     * Generates mutations of a class and stores them to a mutation
     * table; mutations are not actually applied.
     *
     * @param mt Mutation table that records the generated mutations.
     * @param cg BCEL representation of the class for which mutations
     * are being generated.
     */
    void generateMutants(MutationTable mt, ClassGen cg);
    
    /**
     * Gets the name of this mutation operator, in its abbreviated form.
     *
     * @return The name of this mutation operator.
     */
    String name();
    
    /**
     * Gets the descriptive name for this mutation operator.
     *
     * @return The descriptive name for this mutation operator.
     */
    String description();
}
