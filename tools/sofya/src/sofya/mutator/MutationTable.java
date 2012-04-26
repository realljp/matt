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

import java.util.Iterator;

/************************************************************************
 * A table recording the possible mutations generated for a Java
 * class.
 *
 * @author Alex Kinneer
 * @version 10/04/2005
 */
public abstract class MutationTable {
    protected MutationTable() {
    }
    
    /**
     * Adds a mutation to this mutation table.
     *
     * @param m Mutation to be added to this table.
     *
     * @return <code>true</code> if the mutation was successfully added to
     * this table.
     */
    public abstract boolean addMutation(Mutation m);
    
    /**
     * Gets an iterator over the mutations stored in this mutation table
     * (optional operation).
     *
     * @return An iterator over the mutations in this mutation table.
     */
    public abstract Iterator<Mutation> iterator();
    
    /**
     * Gets the size of this mutation table.
     *
     * @return The number of mutations stored in this mutation table.
     */
    public abstract int size();
    
    /**
     * Gets the string table that may be used to compact strings in the
     * mutation table (optional operation).
     *
     * <p>In some situations, this method assists in supporting persistence
     * between file reads and writes. Implementations that do not wish
     * to support this functionality should return <code>null</code>.</p>
     *
     * @return The string table used to compact strings in the mutation
     * table.
     */
    public abstract StringTable getStringTable();
}
