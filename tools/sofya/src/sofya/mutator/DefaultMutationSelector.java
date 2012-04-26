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
 * Mutation selector that selects all mutations.
 *
 * @author Alex Kinneer
 * @version 05/12/2006
 */
public class DefaultMutationSelector implements MutationSelector {
    /**
     * Creates a new mutation selector.
     */
    public DefaultMutationSelector() {
    }
    
    public boolean isSelected(Mutation mutation) {
        return true;
    }
    
    public Variant getVariant(Mutation mutation) {
        return mutation.getDefaultVariant();
    }
    
    public void setMutationCount(int count) {
        // This selector doesn't care
    }
    public int getRequestedVariant(Mutation mutation) {
    	return 0; // default is no variant spec'd
    }
}
