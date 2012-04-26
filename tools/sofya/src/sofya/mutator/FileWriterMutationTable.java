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
import java.io.IOException;

/**
 * A mutation table that writes mutations to file as they are received.
 *
 * @author Alex Kinneer
 * @version 10/03/2005
 */
public class FileWriterMutationTable extends MutationTable {
    /** The file writer. */
    private MutationFileWriter out;
    /** Storage for exceptions raised during mutation serialization. */
    private IOException err;
    /** Size of the table, or number of mutations written so far. */
    private int size = 0;
    
    private FileWriterMutationTable() {
    }
    
    /**
     * Creates a new mutation table.
     *
     * @param outputName Name of the file to which the mutation table will
     * be written.
     */
    public FileWriterMutationTable(String outputName) throws IOException {
        this(outputName, new StringTable());
    }

    /**
     * Creates a new mutation table.
     *
     * @param outputName Name of the file to which the mutation table will
     * be written.
     * @param st String table to be used/extended as the mutation table
     * is written. This provides persistence of the string encodings if
     * this mutation table was previously read from file.
     */
    public FileWriterMutationTable(String outputName, StringTable st)
            throws IOException {
        this.out = new MutationFileWriter(outputName, st);
    }

    public boolean addMutation(Mutation m) {
        try {
            MutationHandler.writeMutation(out, m);
        }
        catch (IOException e) {
            err = e;
            return false;
        }
        
        size += 1;
        return true;
    }
    
    /**
     * <strong>Unsupported operation</strong>. It is not possible to iterate
     * over the mutations while the mutation table is still in the process
     * of being written.
     */
    public Iterator<Mutation> iterator() {
        throw new UnsupportedOperationException();
    }
    
    public int size() {
        return size;
    }
    
    public StringTable getStringTable() {
        return out.getStringTable();
    }
    
    /**
     * Closes the underlying file containing the mutation table;
     * <strong>this method must be called to successfully commit the mutation
     * table to file</strong>!
     *
     * @throws IOException If there is a stored error that was raised while
     * attempting to write a mutation to the file.
     */
    public void close() throws IOException {
        out.close(size);
        if (err != null) {
            throw err;
        }
    }
}
