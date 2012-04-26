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

/**
 * Mutation selector that randomly selects by method name and signature.
 *
 * @author Alex Kinneer
 * @version 10/01/2005
 */
public class RandomMethodMutationSelector extends MethodMutationSelector {
    private RandomMethodMutationSelector() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation selector to select a given number of methods
     * randomly; mutations occurring in selected methods will be selected.
     *
     * @param methodNames {@inheritDoc}
     * @param num Number of methods to be randomly selected. 
     */
    public RandomMethodMutationSelector(String[] methodNames, int num) {
        RandomSequence sequence = new RandomSequence(num, methodNames.length);
        IntSequenceIterator iterator = sequence.iterator();
        while (iterator.hasNext()) {
            addMethod(methodNames[iterator.nextInt() - 1]);
        }
    }
    
    /**
     * Creates a new mutation selector to select a given number of methods
     * randomly; mutations occurring in selected methods will be selected.
     *
     * @param methodNames List of method descriptions supplied by the diffing
     * tool, as strings. Mutations occurring in the selected methods will be
     * selected. For best efficiency, the list should support random access.
     * @param num Number of methods to be randomly selected. 
     */
    public RandomMethodMutationSelector(List<String> methodNames, int num) {
        RandomSequence sequence = new RandomSequence(num, methodNames.size());
        IntSequenceIterator iterator = sequence.iterator();
        while (iterator.hasNext()) {
            addMethod(methodNames.get(iterator.nextInt() - 1));
        }
    }
}
