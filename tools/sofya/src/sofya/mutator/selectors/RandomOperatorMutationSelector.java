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

import java.util.List;
import java.util.Set;

import sofya.mutator.MutationSelector;
import sofya.mutator.Mutation;
import sofya.mutator.Mutation.Variant;

import gnu.trove.THashSet;

/**
 * Mutation selector that randomly selects by mutation operator.
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
@SuppressWarnings("unchecked")
public class RandomOperatorMutationSelector implements MutationSelector {
    /** Set of randomly selected mutation operators. */
    private Set<String> ops = new THashSet();
    
    private RandomOperatorMutationSelector() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation selector to select a given number of mutation
     * operators randomly; mutations corresponding to selected operators
     * will be selected.
     *
     * @param opChoices Array of available mutation operators, as operator
     * abbreviation strings (e.g. &quot;AOC&quot;, &quot;AOP&quot;, etc.).
     * @param num Number of mutation operators to be randomly selected. 
     */
    public RandomOperatorMutationSelector(String[] opChoices, int num) {
        RandomSequence sequence = new RandomSequence(num, opChoices.length);
        IntSequenceIterator iterator = sequence.iterator();
        while (iterator.hasNext()) {
            this.ops.add(opChoices[iterator.nextInt() - 1]);
        }
    }
    
    /**
     * Creates a new mutation selector to select a given number of mutation
     * operators randomly; mutations corresponding to selected operators
     * will be selected.
     *
     * @param opChoices List of selected mutation operators, as operator
     * abbreviation strings (e.g. &quot;AOC&quot;, &quot;AOP&quot;, etc.).
     * For best efficiency, the list should support random access.
     * @param num Number of mutation operators to be randomly selected. 
     */
    public RandomOperatorMutationSelector(List<String> opChoices, int num) {
        RandomSequence sequence = new RandomSequence(num, opChoices.size());
        IntSequenceIterator iterator = sequence.iterator();
        while (iterator.hasNext()) {
            this.ops.add(opChoices.get(iterator.nextInt() - 1));
        }
    }
    
    public boolean isSelected(Mutation mutation) {
        String type = mutation.getType();
        if (type.equals("group")) return true;
        return ops.contains(type);
    }
    
    public Variant getVariant(Mutation mutation) {
        return mutation.getDefaultVariant();
    }
    
    public int getRequestedVariant(Mutation mutation) {
    	return 0; // stub this for now...
    }
    
    public void setMutationCount(int count) {
        // This selector doesn't care
    }
}
