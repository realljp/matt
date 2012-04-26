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

import java.io.File;
import java.io.RandomAccessFile;
import java.io.DataOutput;
import java.io.IOException;

import gnu.trove.TIntObjectIterator;

/************************************************************************
 * Wrapper that provides write access to serialize a mutation table
 * to file.
 *
 * <p>This class transparently handles encoding of strings in the file and
 * generation of the corresponding string table.</p>
 *
 * @author Alex Kinneer
 * @version 09/27/2005
 *
 * @see MutationFileReader
 * @see StringTable
 */
public class MutationFileWriter implements DataOutput {
    /** The underlying file. */
    private RandomAccessFile file;
    /** String table generated during serialization. */
    private StringTable stringTable;
    
    // A random access file must be used so we can write the string table at
    // the end of the file, then seek back and write a pointer to it at the
    // head of the file. This is because we won't know until the end how
    // many unique strings have been encoded and thus how much space the
    // string table requires.
    
    /**
     * Creates a new mutation file writer to write a mutation table.
     *
     * @param file File to which the mutation table will be written.
     *
     * @throws IOException If the specified file cannot be created.
     */
    public MutationFileWriter(File file) throws IOException {
        this(file, new StringTable());
    }
    
    /**
     * Creates a new mutation file writer to write a mutation table.
     *
     * @param name Name of the file to which the mutation table will be
     * written.
     *
     * @throws IOException If the specified file cannot be created.
     */
    public MutationFileWriter(String name) throws IOException {
        this(name, new StringTable());
    }
    
    /**
     * Creates a new mutation file writer to write a mutation table.
     *
     * @param name Name of the file to which the mutation table will be written.
     * @param st String table to be used/extended when writing the mutation
     * table. This provides persistence of the encoding if a file is read
     * and then written back.
     *
     * @throws IOException If the specified file cannot be created.
     */
    public MutationFileWriter(String name, StringTable st) throws IOException {
        this(new File(name), st);
    }
    
    /**
     * Creates a new mutation file writer to write a mutation table.
     *
     * @param file File to which the mutation table will be written.
     * @param st String table to be used/extended when writing the mutation
     * table. This provides persistence of the encoding if a file is read
     * and then written back.
     *
     * @throws IOException If the specified file cannot be created.
     */
    public MutationFileWriter(File file, StringTable st) throws IOException {
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                throw new IOException("Could not overwrite existing file");
            }
        }
        
        this.file = new RandomAccessFile(file, "rw");
        stringTable = st;
        this.file.seek(12);
    }
    
    /**
     * Gets the string table generated for this file.
     *
     * @return The string table storing the encoding of strings in this file.
     * If the file is still open, the table may be incomplete.
     */
    StringTable getStringTable() {
        return stringTable;
    }
    
    public void writeUTF(String str) throws IOException {
        file.writeInt(stringTable.addString(str));
    }
    
    public void close(int size) throws IOException {
        // Set the pointer to the string table
        long curPos = file.getFilePointer();
        file.seek(0);
        file.writeLong(curPos);
        
        // Write the table size
        file.writeInt(size);
        
        // Write the string table
        file.seek(curPos);
        size = stringTable.size();
        file.writeInt(size);
        TIntObjectIterator iterator = stringTable.indexIterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            file.writeInt(iterator.key());
            file.writeUTF((String) iterator.value());
        }
        
        file.close();
    }
    
    public void write(byte[] b) throws IOException {
        file.write(b);
    }
    public void write(byte[] b, int off, int len) throws IOException {
        file.write(b, off, len);
    }
    public void write(int b) throws IOException {
        file.write(b);
    }
    public void writeBoolean(boolean v) throws IOException {
        file.writeBoolean(v);
    }
    public void writeByte(int v) throws IOException {
        file.writeByte(v);
    }
    public void writeBytes(String s) throws IOException {
        file.writeBytes(s);
    }
    public void writeChar(int v) throws IOException {
        file.writeChar(v);
    }
    public void writeChars(String s) throws IOException {
        file.writeChars(s);
    }
    public void writeDouble(double v) throws IOException {
        file.writeDouble(v);
    }
    public void writeFloat(float v) throws IOException {
        file.writeFloat(v);
    }
    public void writeInt(int v) throws IOException {
        file.writeInt(v);
    }
    public void writeLong(long v) throws IOException {
        file.writeLong(v);
    }
    public void writeShort(int v) throws IOException {
        file.writeShort(v);
    }
}
