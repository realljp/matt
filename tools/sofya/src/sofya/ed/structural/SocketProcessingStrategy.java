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

package sofya.ed.structural;

import java.nio.channels.SocketChannel;
import java.io.IOException;

import sofya.ed.structural.AbstractEventDispatcher.ExecException;

/**
 * <p>A socket processing strategy implements a strategy to be used by a
 * {@link ProgramEventDispatcher} to receive probes received from a subject
 * communicating using a socket connection and dispatch them as events.</p>
 *
 * @author Alex Kinneer
 * @version 10/31/2006
 */
public interface SocketProcessingStrategy extends ActiveComponent {
    /*************************************************************************
     * Message sent by the event dispatcher to indicate that it is about to
     * start receiving data to publish its event stream.
     *
     * <p>This message provides the processing strategy an opportunity to
     * take some action prior to handling the event stream, such as
     * issuing a message to listeners.</p>
     */
    void dispatcherStarting();

    /*************************************************************************
     * Message sent by the event dispatcher to indicate that it has stopped
     * receiving data used to publish its event stream.
     *
     * <p>This message provides the processing strategy an opportunity to
     * take some action after handling the event stream, such as issuing
     * a message to listeners.</p>
     */
    void dispatcherStopped();

    /*************************************************************************
     * Executes the handshake procedure with the socket probe.
     *
     * <p>This method should check whether the type of instrumentation indicated
     * by the socket probe is appropriate for this processing strategy. If it is
     * not, an error response should be sent to the socket probe and an
     * exception thrown. Otherwise, the integer code for the form of
     * instrumentation to be processed should be returned.</p>
     *
     * @param commChannel Main communications socket which is connected to the
     * socket probe.
     *
     * @throws ExecException If the instrumentation in the subject is not
     * appropriate for this type of filter.
     * @throws IOException If there is any error communicating through
     * the socket.
     */
    void doHandshake(SocketChannel commChannel)
            throws ExecException, IOException;

    /*************************************************************************
     * The standard trace processing loop, used for all subjects except
     * other event dispatchers.
     *
     * <p>The run loop waits for the subject to negotiate a socket connection
     * and then begins processing trace messages until the socket stream is
     * closed. If the subject is not instrumented, this loop will be killed
     * while waiting for the subject to connect when the main thread calls
     * {@link ProgramEventDispatcher#stopServer}.</p>
     *
     * <p>A standard trace processing loop is provided distinct from the
     * synchronized version so that the cost of synchronization is not
     * incurred for all subjects. This also avoids complications related to
     * determining when to strip timestamps from trace messages. The cost of
     * this implementation is that you must remember to update both this
     * method and {@link #processProbesSynchronized} when
     * making changes to how trace messages are processed.</p>
     */
    void processProbes(SocketChannel recvChannel, ControlData cntrl);

    /*************************************************************************
     * The synchronized trace processing loop.
     *
     * <p>The processing performed by this run loop should be functionally
     * equivalent to that of {@link #processProbes}. The only
     * difference is that this loop should synchronize access to the
     * listeners.</p>
     *
     * <p>Note: This method is only expected to synchronize two threads.
     * The event dispatcher will never execute this method from more than
     * two threads, as there is no reason to do so.</p>
     */
    void processProbesSynchronized(SocketChannel recvChannel,
            ControlData cntrl);
 }
