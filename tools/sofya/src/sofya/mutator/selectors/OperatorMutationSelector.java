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

import java.util.Set;

import sofya.mutator.MutationSelector;
import sofya.mutator.Mutation;
import sofya.mutator.Mutation.Variant;

import gnu.trove.THashSet;

/**
 * Mutation selector that selects by mutation operator.
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
public class OperatorMutationSelector implements MutationSelector {
    /** Set of selected mutation operators. */
    private Set<String> ops;
    
    private OperatorMutationSelector() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation selector.
     *
     * @param ops Array of selected mutation operators, as operator
     * abbreviation strings (e.g. &quot;AOC&quot;, &quot;AOP&quot;, etc.).
     */
    @SuppressWarnings("unchecked")
    public OperatorMutationSelector(String[] ops) {
        this.ops = new THashSet();
        
        for (int i = 0; i < ops.length; i++) {
            this.ops.add(ops[i]);
        }
    }
    
    /**
     * Creates a new mutation selector.
     *
     * @param ops Set of selected mutation operators, as operator
     * abbreviation strings (e.g. &quot;AOC&quot;, &quot;AOP&quot;, etc.).
     */
    public OperatorMutationSelector(Set<String> ops) {
        this.ops = ops;
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
