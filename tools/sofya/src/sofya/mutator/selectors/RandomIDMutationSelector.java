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

import sofya.mutator.MutationSelector;
import sofya.mutator.Mutation;
import sofya.mutator.Mutation.Variant;

/**
 * Mutation selector that randomly selects by ID.
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
public class RandomIDMutationSelector implements MutationSelector {
    /** Number of mutations to randomly select. */
    private int randCount = -1;
    /** Random sequence generator for generating selected IDs. */
    private RandomSequence sequence = new RandomSequence();
    
    private RandomIDMutationSelector() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation selector to select a given number of mutations
     * randomly by ID.
     *
     * @param randCount Number of IDs to be selected at random.
     */
    public RandomIDMutationSelector(int randCount) {
        this.randCount = randCount;
    }
    
    public boolean isSelected(Mutation mutation) {
        return sequence.contains(mutation.getID().asInt());
    }
    
    public Variant getVariant(Mutation mutation) {
        return mutation.getDefaultVariant();
    }
    
    public int getRequestedVariant(Mutation mutation) {
    	return 0;  // stub this for now...
    }
    
    public void setMutationCount(int mutationCount) {
        sequence.newSequence(randCount, mutationCount);
    }
}
