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

/************************************************************************
 * A mutation that actually implements a classfile transformation; this
 * provides a distinction between such mutations and those that are
 * actually just containers for other mutations, such as
 * {@link MutationGroup}.
 *
 * @author Alex Kinneer
 * @version 09/28/2005
 */
public abstract class MutationImpl implements Mutation {
    /** ID assigned to this mutation. */
    private MutationID id;
    
    protected MutationImpl() {
    }
    
    /**
     * Gets the ID associated with this mutation.
     *
     * @return A unique identifer associated with this mutation by the mutation
     * framework.
     */
    public MutationID getID() {
        return id;
    }
    
    /**
     * Sets the ID associated with this mutation; this method is called
     * automatically by the framework and should never be called elsewhere.
     *
     * @param id Unique identifier to be associated with this mutation.
     */
    public void setID(MutationID id) {
        this.id = id;
    }
}
