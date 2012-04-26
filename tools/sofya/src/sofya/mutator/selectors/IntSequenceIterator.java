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

/**
 * An iterator over a sequence of integers.
 *
 * @author Alex Kinneer
 * @version 10/01/2005
 */
public interface IntSequenceIterator {
    /**
     * Reports whether the iteration has more elements.
     *
     * @return <code>true</code> if the iteration has more elements.
     */
    boolean hasNext();
    
    /**
     * Gets the next value from the iteration.
     *
     * @return The next integer from the iteration.
     */
    int nextInt();
    
    /**
     * Removes from the underlying sequence the last element returned
     * by the iterator (optional operation).
     */
    void remove();
}
