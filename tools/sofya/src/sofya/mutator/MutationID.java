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

/**
 * A unique identifer associated with a {@link Mutation}.
 *
 * @author Alex Kinneer
 * @version 10/01/2005
 */
public final class MutationID {
    /** The ID of the mutation. */
    private final int id;
    
    /**
     * The framework assigns IDs automatically, so we prefer not to permit
     * external creation of IDs.
     */
    private MutationID() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation ID.
     *
     * @param id ID to be created.
     */
    MutationID(int id) {
        this.id = id;
    }
    
    /**
     * Gets this ID as a primitive integer.
     *
     * @return This mutation ID as a primitive integer.
     */
    public int asInt() {
        return id;
    }
    
    /**
     * Gets this ID as a string.
     *
     * @return This mutation ID as a string.
     */
    public String toString() {
        return String.valueOf(id);
    }
}
