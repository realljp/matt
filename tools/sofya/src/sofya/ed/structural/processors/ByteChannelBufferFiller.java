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

package sofya.ed.structural.processors;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A byte channel buffer filler provides services to ensure that a
 * byte buffer contains at least an expected amount of data, read from
 * a ReadableByteChannel source.
 * 
 * @author Alex Kinneer
 * @version 11/03/2006
 */
class ByteChannelBufferFiller {
    /** The byte source. */
    protected ReadableByteChannel recvChannel;
    /** The buffer to fill. */
    protected ByteBuffer recvBuffer;
    
    private static final boolean DEBUG = false;

    private ByteChannelBufferFiller() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new buffer filler to read from a given byte source
     * into a given buffer.
     * 
     * @param recvChannel Source of bytes.
     * @param recvBuffer Buffer to fill.
     */
    protected ByteChannelBufferFiller(ReadableByteChannel recvChannel,
            ByteBuffer recvBuffer) {
        this.recvChannel = recvChannel;
        this.recvBuffer = recvBuffer;
    }
    
    /**
     * Get a reference to the target buffer.
     * 
     * @return The byte buffer into which bytes are read.
     */
    final ByteBuffer getBuffer() {
        return recvBuffer;
    }
    
    /**
     * Ensures that an exact number of bytes are available to read from
     * the buffer.
     * 
     * <p>If the desired number of bytes are already available, this method
     * returns immediately. Otherwise it only reads the exact number of
     * bytes necessary to ensure that the requested number of bytes are
     * available.</p>
     * 
     * @param numBytes The number of bytes that must be available for
     * reading from the buffer when this method returns.
     * 
     * @throws EOFException If the end of the byte source is reached
     * before the requested number of bytes can be read into the buffer.
     * @throws IOException On any other I/O error that prevents the
     * reading of the requested number of bytes.
     */
    final void ensureAvailableOnly(int numBytes)
            throws EOFException, IOException {
        if (DEBUG) System.out.println("numBytes=" + numBytes);
        
        int remaining = recvBuffer.remaining();
        if (DEBUG) System.out.println("remaining=" + remaining);
        if (remaining >= numBytes) return;
        
        if (remaining > 0) {
            recvBuffer.compact();
        }
        else {
            recvBuffer.clear();
        }
        
        recvBuffer.limit(numBytes);
        if (DEBUG) System.out.println("limit=" + recvBuffer.limit());
        
        int bytesIn = recvBuffer.position();
        if (DEBUG) System.out.println("bytesIn=" + bytesIn);
        while (true) {
            int curBytes = recvChannel.read(recvBuffer);
            if (curBytes == -1) {
                throw new EOFException();
            }
            bytesIn += curBytes;
            if (bytesIn >= numBytes) {
                recvBuffer.flip();
                return;
            }
        }
    }
}
