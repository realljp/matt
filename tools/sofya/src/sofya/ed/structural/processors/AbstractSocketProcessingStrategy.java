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

import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.BindException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import sofya.ed.structural.SocketProcessingStrategy;
import sofya.ed.structural.EventDispatcherConfiguration;

/**
 * <p>Base class for strategies to receive and process probes for a
 * program event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 11/10/2006
 */
public abstract class AbstractSocketProcessingStrategy
        extends AbstractProcessingStrategy
        implements SocketProcessingStrategy {

    protected int recvBufferSize;
    protected int sendBufferSize;

    /** Local copy of the flag indicating whether the subject is itself
        an event dispatcher. */
    protected static boolean isSbjDispatcher;
    /** Lock used to synchronize access to the listener or listeners. */
    protected static Object traceLock = new Object();

    /** Conditional compilation flag indicating whether the JVM is a
        preemptive JVM. */
    protected static final boolean PREEMPTIVE = true;
    /** Conditional compilation flag to enable debug outputs. */
    private static final boolean DEBUG = false;
    
    protected AbstractSocketProcessingStrategy() {
        super();
    }

    public void register(EventDispatcherConfiguration edConfig) {
        super.register(edConfig);

        isSbjDispatcher = edConfig.isSubjectDispatcher();
    }

    public List<String> configure(List<String> parameters) {
        return parameters;
    }

    public void reset() {
        isSbjDispatcher = false;
    }

    public void release() {
        reset();
    }

    /*************************************************************************
     * Connects the signal socket used to allow basic communication with the
     * subject's SocketProbe.
     *
     * <p>This method attempts to find a free port and bind a server socket to
     * it. It then writes the chosen port onto the main socket connection for
     * the SocketProbe to read and waits for the incoming connection. Once the
     * connection is established, it returns the associated socket and no
     * further data is written to the main socket connection.</p>
     *
     * <p><b>Note:</b> Coverage processing strategies do not use the signal
     * socket, however we still negotiate the connection so the SocketProbe
     * doesn't get confused. The signal socket is intended for use by
     * sequence processing strategies.</p>
     *
     * @param msgChannel The main socket which will be used for trace messages,
     * which should be connected before calling this method.
     * @param buffer Provided buffer for communicating over the socket.
     *
     * @return A socket representing the signal connection.
     *
     * @throws IOException If an open port for the signal socket cannot be
     * found, there is an error connecting the socket, or there is an error
     * attempting to transmit the port number to the SocketProbe.
     */
    protected Socket openSignalSocket(SocketChannel msgChannel,
            ByteBuffer buffer) throws IOException {
        ServerSocket connectSocket = null;

        for (int cur_port = 1025; cur_port <= 65535; cur_port++) {
            try {
                connectSocket = new ServerSocket(cur_port, 1,
                    InetAddress.getByName("localhost"));
                break;
            }
            catch (BindException e) { }
        }

        if (connectSocket == null) {
            throw new IOException("Unable to create signal socket!");
        }

        if (DEBUG) stdout.println("Filter negotiating signal socket " +
            "connection on: " + connectSocket.getLocalPort());

        buffer.clear();
        buffer.putInt(connectSocket.getLocalPort());
        buffer.flip();
        int written = 0;
        while ((written += msgChannel.write(buffer)) < 4);
        buffer.clear();

        try {
            return connectSocket.accept();
        }
        catch (IOException e) {
            connectSocket.close();
            throw e;
        }
    }
}
