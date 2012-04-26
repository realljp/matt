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

import sofya.mutator.Mutation.Variant;

/**
 * A mutation selector selects mutatations from a mutation table according
 * to some criteria.
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
public interface MutationSelector {
    /**
     * Reports whether a mutation is selected.
     *
     * @param mutation Mutation to be checked for selection.
     *
     * @return <code>true</code> if this selector implements a selection
     * criteria that the given mutation meets, <code>false</code>
     * otherwise.
     */
    boolean isSelected(Mutation mutation);

    /**
     * Gets the selected variant.
     *
     * <p>Some mutation operators, such as the arithmetic operator change
     * (AOP), generate multiple variants at a single location.</p>
     *
     * @param mutation Mutation for which to retrieve the selected variant.
     *
     * @return The selected mutation variant.
     */
    Variant getVariant(Mutation mutation);

    /**
     * Sets the total number of mutations that may be passed to this selector
     * (typically the number of mutations in a mutation table).
     *
     * <p>This is useful for some selectors, such as those that implement
     * random selection criteria.</p>
     *
     * @param count Total number of mutations from which this selector
     * may select.
     */
    void setMutationCount(int count);

    /**
     * used by group selector types to retrieve the variant number 
     * requested in this mutation group.  This is applicable when the
     * N:M form of mutation selection is used as well as when a mutation
     * within a mutation group is the selected target mutation.
     * @return integer value of the requested variant of the mutation group
     */
	int getRequestedVariant(Mutation mutation);
}
