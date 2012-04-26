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

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A standard in-memory mutation table.
 *
 * <p>Large mutation tables may cause resource consumption problems.</p>
 *
 * @author Alex Kinneer
 * @version 10/03/2005
 */
public class StandardMutationTable extends MutationTable {
    /** List of mutations in this mutation table. */
    private List<Mutation> mutations = new ArrayList<Mutation>();
    /** String table associated with this mutation table. */
    private StringTable stringTable;
    
    /**
     * Creates a new mutation table.
     */
    public StandardMutationTable() {
        this(new StringTable());
    }
    
    /**
     * Creates a new mutation table.
     *
     * @param st String table to be used/extended when adding new mutations
     * to this table. Supports persistence of string encodings if this
     * table was previously read from file.
     */
    public StandardMutationTable(StringTable st) {
        stringTable = st;
    }
    
    public boolean addMutation(Mutation m) {
        return mutations.add(m);
    }
    
    public Iterator<Mutation> iterator() {
        return mutations.iterator();
    }
    
    public int size() {
        return mutations.size();
    }
    
    public StringTable getStringTable() {
        return stringTable;
    }
}
