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

import java.util.NoSuchElementException;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntIterator;
import gnu.trove.TIntObjectIterator;

/************************************************************************
 * Table that maps strings to integer indices to provide string compaction,
 * primarily for use in writing strings to files.
 *
 * @author Alex Kinneer
 * @version 09/27/2005
 */
public final class StringTable {
    /** Forward mapping from strings to indices. */
    private TObjectIntHashMap stringTable = new TObjectIntHashMap();
    /** Reverse mapping from indices to strings. */
    private TIntObjectHashMap indexTable = new TIntObjectHashMap();
    /** Next available index, used during table construction. */
    private int nextIndex = 0;
    
    /**
     * Creates a new string table.
     */
    public StringTable() {
    }
    
    /**
     * Adds a string to this table.
     *
     * @param str String to be added to this table.
     *
     * @return Index assigned to the string.
     */
    public int addString(String str) {
        if (stringTable.containsKey(str)) {
            return stringTable.get(str);
        }
        else {
            int newIndex = nextIndex;
            stringTable.put(str, newIndex);
            indexTable.put(newIndex, str);
            nextIndex += 1;
            return newIndex;
        }
    }
    
    /**
     * Gets the index of a string, if it has been added to this table.
     *
     * @param str String for which to retrieve the assigned index from
     * this table.
     *
     * @return The index assigned to the string in this table.
     *
     * @throws NoSuchElementException If the specified string has not been
     * added to this table.
     */
    public int lookupString(String str) throws NoSuchElementException {
        if (stringTable.containsKey(str)) {
            return stringTable.get(str);
        }
        else {
            throw new NoSuchElementException();
        }
    }
    
    /**
     * Gets the string associated with a given index in this table, if
     * the index exists in the table.
     *
     * @param index Index for which to find the associated string.
     *
     * @return The string associated with the given index in this table.
     *
     * @throws NoSuchElementException If the specified index does not have
     * a mapping in this table.
     */
    public String lookupIndex(int index) throws NoSuchElementException {
        if (indexTable.containsKey(index)) {
            return (String) indexTable.get(index);
        }
        else {
            throw new NoSuchElementException();
        }
    }
    
    /**
     * Gets the number of strings indexed by this table.
     *
     * @return The number of strings stored in this table.
     */
    public int size() {
        return stringTable.size();
    }
    
    /**
     * Adds a string using a specified index.
     *
     * <p>This method is used internally to read a string table from
     * file.</p>
     *
     * @param str String to be added to this table.
     * @param index Index to associated with the added string.
     */
    void addString(String str, int index) {
        stringTable.put(str, index);
        indexTable.put(index, str);
    }
    
    /**
     * Gets a forward lookup iterator over the strings in this table.
     *
     * @return A map iterator that returns strings as keys and indices
     * as values.
     */
    TObjectIntIterator stringIterator() {
        return stringTable.iterator();
    }
    
    /**
     * Gets a reverse lookup iterator over the strings in this table.
     *
     * <p>This method is used internally to write a string table
     * to file.</p>
     *
     * @return A map iterator that returns indices as keys and strings
     * as values.
     */
    TIntObjectIterator indexIterator() {
        return indexTable.iterator();
    }
}
