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

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/************************************************************************
 * An iterator over a mutation table stored in a file.
 *
 * <p>This iterator provides the capability to read a mutation table
 * from file progressively, avoiding the need to preload an entire table
 * into memory.</p>
 *
 * @author Alex Kinneer
 * @version 09/27/2005
 */
public final class MutationIterator implements Iterator<Mutation> {
    /** Reader for the mutation table file. */
    private MutationFileReader mFile;
    /** Number of mutations in the iteration. */
    private int count;
    /** Current position in the iteration. */
    private int pos = 0;
    
    private MutationIterator() {
    }
    
    /**
     * Creates a mutation iterator.
     *
     * @param mFile File containing the mutation table over which to iterate.
     *
     * @throws IOException If the specified file cannot be found or opened.
     */
    MutationIterator(MutationFileReader mFile) throws IOException {
        this.mFile = mFile;
        this.count = mFile.getMutationCount();
    }
    
    /**
     * Gets the number of mutations in this iteration.
     *
     * @return The number of iterations that will be returned by this iterator.
     */
    public int count() {
        return count;
    }
    
    /**
     * Reports whether the iteration has more mutations.
     *
     * @return <code>true</code> if the iteration has more mutations.
     */
    public boolean hasNext() {
        return (pos != count);
    }
   
    /**
     * Gets the next mutation in the iteration.
     *
     * @return The next mutation from the iteration.
     *
     * @throws NoSuchElementException If the iteration does not contain
     * any more mutations, or if an error reading the underlying file
     * prevents the iterator from returning the next mutation.
     */
    public Mutation next() {
        if (pos == count) {
            throw new NoSuchElementException();
        }

        try {
            Mutation m = MutationHandler.readMutation(mFile);
            pos += 1;
            
            if (pos == count) {
                close();
            }
            
            return m;
        }
        catch (IOException e) {
            close();
            
            NoSuchElementException nsee = new NoSuchElementException(
                "Exception in iterator");
            nsee.initCause(e);
            throw nsee;
        }
    }
    
    /**
     * Closes the underlying mutation file.
     *
     * <p>This function is provided to permit cleanup on premature failure
     * of the iteration. The mutation file is automatically closed when the
     * end of the iteration is reached normally.</p>
     */
    public void close() {
        try {
            mFile.close();
        }
        catch (IOException e) {
            // What would a caller do with it anyway?
        }
    }
    
    /**
     * <strong>Unsupported</strong>. This iterator does not permit
     * removal of elements.
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
