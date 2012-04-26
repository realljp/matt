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
import java.io.DataInput;
import java.io.IOException;
import java.io.EOFException;
import java.util.NoSuchElementException;

import sofya.base.exceptions.BadFileFormatException;

/************************************************************************
 * Wrapper that provides read access to a mutation table serialized to
 * file.
 *
 * <p>This class transparently handles deserialization of the string table
 * and decoding of strings in the file.</p>
 *
 * @author Alex Kinneer
 * @version 08/10/2006
 *
 * @see MutationFileWriter
 * @see StringTable
 */
public class MutationFileReader implements DataInput {
    /** The underlying file. */
    private RandomAccessFile file;
    /** String table read from the file. */
    private StringTable stringTable = new StringTable();
    /** Mutation count read from the file. */
    private int mutationCount;

    private MutationFileReader() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new mutation file reader to read a mutation table.
     *
     * @param file File containing the mutation table to be read.
     *
     * @throws IOException If the specified file does not exist or does
     * not contain a mutation table.
     */
    public MutationFileReader(File file) throws IOException {
        this.file = new RandomAccessFile(file, "r");
        init();
    }

    /**
     * Creates a new mutation file reader to read a mutation table.
     *
     * @param name Name of the file containing the mutation table to be read.
     *
     * @throws IOException If the specified file does not exist or does
     * not contain a mutation table.
     */
    public MutationFileReader(String name) throws IOException {
        this.file = new RandomAccessFile(name, "r");
        init();
    }

    /**
     * Initialization to read the string table and mutation count.
     */
    private void init() throws IOException {
        long strTablePos = file.readLong();
        file.seek(strTablePos);

        try {
            int size = file.readInt();
            for (int i = 0; i < size; i++) {
                int index = file.readInt();
                String str = file.readUTF();
                stringTable.addString(str, index);
            }

            file.seek(8);
            mutationCount = file.readInt();
        }
        catch (EOFException e) {
            throw new BadFileFormatException("File does not contain " +
                "a mutation table, or is invalid");
        }
    }

    /**
     * Gets the string table read from the mutation file.
     *
     * @return The string table used to encode strings in the file.
     */
    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Gets the number of mutations stored in the mutation table.
     *
     * @return The number of mutations in the mutation table in this file.
     */
    public int getMutationCount() {
        return mutationCount;
    }

    public String readUTF() throws IOException {
        String str = null;

        try {
            str = stringTable.lookupIndex(file.readInt());
        }
        catch (NoSuchElementException e) {
            throw new IOException("File not positioned at string " +
                "or contains improper string encoding");
        }

        return str;
    }

    public void close() throws IOException {
        file.close();
    }

    public boolean readBoolean() throws IOException {
        return file.readBoolean();
    }
    public byte readByte() throws IOException {
        return file.readByte();
    }
    public char readChar() throws IOException {
        return file.readChar();
    }
    public double readDouble() throws IOException {
        return file.readDouble();
    }
    public float readFloat() throws IOException {
        return file.readFloat();
    }
    public void readFully(byte[] b) throws IOException {
        file.readFully(b);
    }
    public void readFully(byte[] b, int off, int len) throws IOException {
        file.readFully(b, off, len);
    }
    public int readInt() throws IOException {
        return file.readInt();
    }
    public String readLine() throws IOException {
        return file.readLine();
    }
    public long readLong() throws IOException {
        return file.readLong();
    }
    public short readShort() throws IOException {
        return file.readShort();
    }
    public int readUnsignedByte() throws IOException {
        return file.readUnsignedByte();
    }
    public int readUnsignedShort() throws IOException {
        return file.readUnsignedShort();
    }
    public int skipBytes(int n) throws IOException {
        return file.skipBytes(n);
    }
}
