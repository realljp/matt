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
 * A virtually limited byte channel buffer filler provides services to
 * ensure that a byte buffer contains at least an expected amount of data,
 * read from a ReadableByteChannel source, while eagerly reading data
 * into the buffer based on a target limit that may exceed the size of
 * the actual underlying buffer.
 * 
 * @author Alex Kinneer
 * @version 11/03/2006
 */
final class VirtualLimitBufferFiller extends ByteChannelBufferFiller {
    /** The current maximum number of bytes to try to eagerly read. */
    private long virtualLimit;
    /** The real capacity of the physical buffer. */
    private int capacity;
    
    /**
     * Creates a new buffer filler to read from a given byte source
     * into a given buffer. The virtual limit is initialized to
     * 0 (zero).
     * 
     * @param recvChannel Source of bytes.
     * @param recvBuffer Buffer to fill.
     */
    VirtualLimitBufferFiller(ReadableByteChannel recvChannel,
            ByteBuffer recvBuffer) {
        super(recvChannel, recvBuffer);
        this.capacity = recvBuffer.capacity();
    }
    
    /**
     * Sets the virtual limit.
     * 
     * <p>Whenever more bytes need to be read from the byte source to
     * satisfy a request to ensure that a given number of bytes are
     * available, the filler will eagerly attempt to fill as much of
     * the buffer as possible until the total number of bytes read
     * matches the amount specified on the last call to this method.</p>
     *  
     * @param limit Virtual limit on the number of bytes to read into
     * the buffer, which may exceed the physical buffer capacity.
     */
    void virtualLimit(long limit) {
        this.virtualLimit = limit;
    }
    
    /**
     * Gets the number of bytes still available to read before the
     * virtual limit is reached.
     * 
     * @return The number of bytes the filler will still attempt to read
     * before the virtual limit needs to be set again.
     */
    long remaining() {
        return virtualLimit;
    }
    
    /**
     * Reports whether any bytes are left to be read.
     * 
     * @return <code>true</code> if the filler can still attempt to read
     * bytes into the buffer to reach the virtual limit, <code>false</code>
     * otherwise.
     */
    boolean hasRemaining() {
        return virtualLimit > 0;
    }
    
    /**
     * Ensures that at least a given number of bytes are available for
     * reading from the buffer.
     * 
     * <p>If the desired number of bytes are already available, this method
     * returns immediately. Otherwise it attempts to read more bytes from
     * the byte source until the buffer is filled again, or the total
     * number of bytes read from the byte source is equal to the amount
     * specified on the last call to {@link #virtualLimit(long)}.</p>
     * 
     * @param numBytes The number of bytes that must be available for
     * reading from the buffer when this method returns.
     * 
     * @throws IllegalStateException If the number of bytes requested
     * are not available, and the filler would have to exceed the last
     * specified virtual limit to make that number of bytes available.
     * @throws EOFException If the end of the byte source is reached
     * before the requested number of bytes can be read into the buffer.
     * @throws IOException On any other I/O error that prevents the
     * reading of the requested number of bytes.
     */
    void ensureAvailable(int numBytes) throws IllegalStateException,
            EOFException, IOException {
        int remaining = recvBuffer.remaining();
        if (remaining > numBytes) {
            virtualLimit -= numBytes;
            return;
        }
        
        if (numBytes > virtualLimit) {
            throw new IllegalStateException("Number of bytes requested " +
                "exceeds known bytes available");
        }
        
        if (remaining > 0) {
            recvBuffer.compact();
        }
        else {
            recvBuffer.clear();
        }
        
        int bytesIn = recvBuffer.position();
        int fillLimit = (virtualLimit > capacity)
            ? capacity
            : (int) virtualLimit;
        recvBuffer.limit(fillLimit);
        
        while (true) {
            int curBytes = recvChannel.read(recvBuffer);
            if (curBytes == -1) {
                throw new EOFException();
            }
            bytesIn += curBytes;
            if (bytesIn >= numBytes) {
                recvBuffer.flip();
                virtualLimit -= numBytes;
                return;
            }
        }
    }
}
