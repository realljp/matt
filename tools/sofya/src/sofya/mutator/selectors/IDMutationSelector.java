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

import gnu.trove.TIntIntHashMap;

/**
 * Mutation selector that selects by ID.
 *
 * @author Alex Kinneer
 * @version 05/23/2006
 */
public class IDMutationSelector implements MutationSelector {
    /** Set of selected IDs. */
    private TIntIntHashMap ids;

    private IDMutationSelector() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new mutation selector.
     *
     * @param ids Array of selected IDs.
     */
    public IDMutationSelector(ID[] ids) {
        int length = ids.length;
        for (int i = length - 1; i-- >= 0; ) {
            this.ids.put(ids[i].mId, ids[i].variant);
        }
    }

    /**
     * Creates a new mutation selector.
     *
     * @param ids Set of selected IDs, where each selected mutant
     * is mapped to a selected variant, if applicable.
     */
    public IDMutationSelector(TIntIntHashMap ids) {
        this.ids = ids;
    }

    /**
     * reports whether the the specified mutation is
     * the mutation requested on the command line.
     * 
     * @return true if the specified mutation was the
     * mutation requested.
     */
    public boolean isSelected(Mutation mutation) {
        return ids.containsKey(mutation.getID().asInt());
    }

    /**
     * gets the mutation Variant object for the specified
     * mutation argument.  This is used for mutation group
     * objects in the Mutator when a specific mutation is
     * desired from a mutation group.
     * @return Variant object of the specified mutation
     */
    public Variant getVariant(Mutation mutation) {
        int variant = ids.get(mutation.getID().asInt());
        Variant[] vs = mutation.getVariants();
        if ((variant < 1) || (variant > vs.length)) {
            return mutation.getDefaultVariant();
        }
        else {
            return vs[variant - 1];
        }
    }
    
    /**
     * Gets the real requested variant number from the MutationGroup
     * When the -ids flag of the command line is used in the N:M format
     * the values in the selector determine the offset into the set
     * of mutations in the group.  If the -ids flag used the absolute
     * mutation number (N) format, the keys that are present indicate
     * the real mutation number.  This method only returns the first
     * because typically you only want a single mutation applied in
     * a group despite the ability of the mutator to insert multiple
     * mutations in a single pass.
     * @return real variant number requested on the command line
     */
    
    public int getRequestedVariant(Mutation mutation) {
    	if(ids.get(mutation.getID().asInt()) == 0) {
    		int[] keys = ids.keys();
    		return (keys[0] - mutation.getID().asInt()); // return only the first found
    		                // minus the mutation number to form an index into the MutGroup
    						// revisit this when you want to support > 1 mutant per
    						//class, although it will require intfc contract change to int[]
    	}
    	else return ids.get(mutation.getID().asInt());
    }

    public void setMutationCount(int count) {
        // This selector doesn't care
    }

    /**
     * Utility class to correlate mutants with selected variants.
     *
     * <p>Once created, an ID is immutable.</p>
     */
    public static final class ID {
        /** ID of the selected mutant. */
        public final int mId;
        /** Selected variant of the mutant, if applicable. */
        public final int variant;

        private ID() {
            throw new AssertionError("Illegal constructor");
        }

        /**
         * Creates a new mutant selection ID.
         *
         * @param mId Id of the selected mutant; the default variant
         * will be used if applicable.
         */
        public ID(int mId) {
            this.mId = mId;
            this.variant = 0;
        }

        /**
         * Creates a new mutant selection ID.
         *
         * @param mId Id of the selected mutant.
         * @param variant Selected variant of the mutant, if applicable.
         */
        public ID(int mId, int variant) {
            this.mId = mId;
            this.variant = variant;
        }

        public String toString() {
            return "[" + mId + ":" + variant + "]";
        }
    }

}
