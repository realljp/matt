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

package sofya.base;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Replicates character data received from an input stream to one or
 * multiple output streams. The input stream is typically expected to
 * be standard input, connected to a console.
 * 
 * <p>A replicator can be put into a mode in which it responds to special
 * &quot;command&quot; directives. These directives enable the replicator
 * to be controlled from the console using in-stream commands sent via
 * standard input. A command is signalled by the character '%', followed
 * by a command sequence. The following are the currently recognized
 * commands:</p>
 * <ul>
 * <li><code>%b</code> : Put the replicator into &quot;broadcast&quot;
 * mode. In this mode, inputs received are copied to all associated output
 * streams.</li>
 * <li><code>%c</code> : Report the currently selected stream. This is
 * the stream to which inputs are currently being copied.</li>
 * <li><code>%d</code> : Disable in-stream command handling. No further
 * command signals will be processed in the input.</li>
 * <li><code>%l</code> : List the ids of all streams currently available
 * for selection.</li>
 * <li><code>%q</code> : Disconnect from standard input and allow the
 * replicator to terminate.</li>
 * <li><code>%&lt;id&gt;</code> : Select the stream associated with
 * <code>id</code> to be the new target of input replication. Broadcast
 * mode is disabled, if currently enabled.</li>
 * <li><code>%%</code> : Send the literal character '%' to the selected
 * output stream(s).</li>
 * </ul>
 * 
 * <p>Currently, normal outputs of the replicator are sent to
 * <code>System.out</code>.</p>
 * 
 * @author Alex Kinneer
 * @version 08/17/2007
 */
public final class StreamReplicator implements Runnable {
    /** Character data input source. */
    private final BufferedReader in;
    /** Stream to which error outputs should be sent. */
    private PrintStream stderr;
    /** Flag indicating whether the replicator is still connected to
        the input source. */
    private boolean connected = true;
    
    /** Table of streams currently connected to the replicator;
        streams are selected by integer identifiers. */
    private final Map<Integer, PrintStream> streams =
            new HashMap<Integer, PrintStream>();
    /** Set of connected streams that should be closed when the
        replicator is shut down (the input source is closed). */
    private final Set<PrintStream> shutdownStreams =
            new HashSet<PrintStream>();
    /** Counter for ids to be automatically assigned to connected
        streams. */
    private int newId = 1;

    /** Id of the currently selected stream. */
    private Integer selectedId;
    /** The currently selected stream. */
    private PrintStream selectedStream;
    /** Flag specifying whether the replicator is in broadcast mode,
        where inputs are replicated to all connected streams. */
    private boolean broadcast = false;
    /** Flag specifying whether the replicator performs in-stream command
        handling; this is usually only appropriate for an interactive
        input source (e.g. standard input connected to a console). */
    private boolean enableStreamCmds = true;
    
    /** The special input command character used to issue commands to
        the replicator via the input source. To actually send the character
        to the output, it must appear twice consecutively in the input. */
    private static final char CMD_CHAR = '%';

    // Internal control codes
    
    /** Control code to signal that the end of the input stream
        was reached. */
    private static final int EOF_STOP = -1;
    /** Control code to indicate that the current character not be
        copied to the output stream(s). */
    private static final int SKIP_CHAR = 0;
    /** Control code to indicate that the current character should be
        copied to the output stream(s). */
    private static final int ECHO_CHAR = 1;
    /** Control code to indicate that another command should be
        processed (this handles the case where commands appear
        consecutively without an intervening newline). */
    private static final int NEXT_CMD = 2;

    private StreamReplicator() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new stream replicator.
     * 
     * <p>A replicator is not initially connected to any streams, and any
     * input that is received is discarded.</p>
     * 
     * <p>The input source is assumed to be interactive (e.g. standard
     * input connected to a console), so in-stream command handling is
     * enabled by default.</p>
     * 
     * @param source Stream from which data will be read.
     * @param stderr Stream to which error outputs related to the operation
     * of the replicator itself should be sent.
     */
    public StreamReplicator(InputStream source, PrintStream stderr) {
        this.in = new BufferedReader(new InputStreamReader(source));
        this.enableStreamCmds = true;

        if (stderr != null) {
            this.stderr = stderr;
        }
        else {
            this.stderr = System.err;
        }
    }
    
    /**
     * Creates a new stream replicator.
     * 
     * <p>A replicator is not initially connected to any streams, and any
     * input that is received is discarded.</p>
     * 
     * @param source Stream from which data will be read.
     * @param stderr Stream to which error outputs related to the operation
     * of the replicator itself should be sent.
     * @param enableStreamCmds Specifies whether the replicator should
     * respond to command sequences in the input. Typically this should
     * <code>false</code> for programmatic input to avoid unexpected
     * behavior or data mangling, and <code>true</code> for an interactive
     * input source.
     */
    public StreamReplicator(InputStream source, PrintStream stderr,
            boolean enableStreamCmds) {
        this.in = new BufferedReader(new InputStreamReader(source));
        this.enableStreamCmds = enableStreamCmds;

        if (stderr != null) {
            this.stderr = stderr;
        }
        else {
            this.stderr = System.err;
        }
    }
    
    /**
     * Prepares an output stream connected to the replicator.
     * 
     * @param sink Output stream to be prepared.
     * @param buffered Specifies whether the output stream should be
     * buffered by the replicator. Streams that are already buffered
     * will want to set this to <code>false</code>.
     */
    private PrintStream setupStream(OutputStream sink, boolean buffered) {
        if (buffered) {
            return new PrintStream(new BufferedOutputStream(sink));
        }
        else if (sink instanceof PrintStream) {
            return (PrintStream) sink;
        }
        else {
            return new PrintStream(sink);
        }
    }

    /**
     * Connects a stream to the replicator. A stream connected to the
     * replicator can be selected as a target to which the received
     * input should be sent.
     * 
     * @param sink Stream to connect to the replicator.
     * @param closeOnShutdown Specifies whether the stream should be
     * closed by the replicator when the input source is closed.
     * @param buffered Specifies whether the replicator should internally
     * buffer the stream.
     * 
     * @return An automatically assigned identifier that can be used to
     * select the stream.
     * 
     * @throws IllegalStateException If the replicator is no longer
     * connected to the input source.
     */
    public Integer connectStream(OutputStream sink, boolean closeOnShutdown,
            boolean buffered) {
        if (!connected) {
            throw new IllegalStateException("Input disconnected");
        }
        
        PrintStream targetStream = setupStream(sink, buffered);
        
        // Find an id that hasn't already been assigned (we could
        // experience a collision if the other connectStream method
        // has been used).
        Integer streamId;
        do {
            streamId = Integer.valueOf(newId++);
        }
        while (streams.containsKey(streamId));
        
        streams.put(streamId, targetStream);

        if (closeOnShutdown) {
            shutdownStreams.add(targetStream);
        }

        return streamId;
    }

    /**
     * Connects a stream to the replicator.
     * 
     * @param sink Stream to connect to the replicator.
     * @param closeOnShutdown Specifies whether the stream should be
     * closed by the replicator when the input source is closed.
     * @param buffered Specifies whether the replicator should internally
     * buffer the stream.
     * @param id Identifier to be used to select the stream.
     * 
     * @return <code>true</code> if the stream could be connected with
     * the requested <code>id</code>, <code>false</code> if the
     * <code>id</code> is already in use.
     * 
     * @throws IllegalStateException If the replicator is no longer
     * connected to the input source.
     */
    public boolean connectStream(OutputStream sink, boolean closeOnShutdown,
            boolean buffered, Integer id) {
        if (!connected) {
            throw new IllegalStateException("Input disconnected");
        }
        
        if (streams.containsKey(id)) {
            return false;
        }

        PrintStream targetStream = setupStream(sink, buffered);
        streams.put(id, targetStream);

        if (closeOnShutdown) {
            shutdownStreams.add(targetStream);
        }

        return true;
    }

    /**
     * Disconnects a stream from the replicator.
     * 
     * <p>If the disconnected stream is the currently selected stream,
     * the currently selected stream becomes null; Input received by
     * the replicator is discarded until a new stream is selected or
     * the replicator is put in broadcast mode (and there are any
     * remaining streams connected).</p>
     * 
     * @param streamId Id of the stream to be disconnected.
     * @param close Specifies whether the disconnected stream should also
     * be closed.
     * 
     * @return <code>true</code> if a stream associated with the given
     * identifier was disconnected, <code>false</code> otherwise.
     */
    public boolean disconnectStream(Integer streamId, boolean close) {
        PrintStream target = streams.remove(streamId);
        if (target == null) {
            return false;
        }
        shutdownStreams.remove(target);
        if (close) {
            target.close();
        }
        if (streamId.equals(selectedId)) {
            selectedStream = null;
            selectedId = null;
        }
        return true;
    }
    
    /**
     * Selects a stream to be the destination of received input.
     * 
     * @param streamId Identifier of the stream to which to copy received
     * input.
     * 
     * @return <code>true</code> if the specified stream was selected,
     * <code>false</code> if there is no stream associated with the
     * replicator by the given identifer.
     * 
     * @throws IllegalStateException If the replicator is no longer
     * connected to the input source.
     */
    public boolean selectStream(Integer streamId) {
        if (!connected) {
            throw new IllegalStateException("Input disconnected");
        }
        
        PrintStream reqStream = streams.get(streamId);
        if (reqStream != null) {
            selectedStream = reqStream;
            selectedId = streamId;
            broadcast = false;
            return true;
        }
        else {
            return false;
        }
    }
    
    /**
     * Reports whether the replicator is in broadcast mode. In broadcast
     * mode, the replicator copies the input to all connected streams.
     * 
     * @return <code>true</code> if the replicator is in broadcast
     * mode, <code>false</code> otherwise.
     */
    public boolean broadcast() {
        return broadcast;
    }
    
    /**
     * Specifies whether the replicator is in broadcast mode. In broadcast
     * mode, the replicator copies the input to all connected streams.
     * 
     * @param state <code>true</code> to put the replicator in broadcast
     * mode, <code>false</code> to disable broadcast mode and return
     * to using the last selected stream.
     * 
     * @return Whether the replicator was already in broadcast mode.
     * 
     * @throws IllegalStateException If the replicator is no longer
     * connected to the input source.
     */
    public boolean broadcast(boolean state) {
        if (!connected) {
            throw new IllegalStateException("Input disconnected");
        }
        
        boolean alreadyWas = broadcast;
        broadcast = state;
        return alreadyWas;
    }
    
    /**
     * Reports whether the replicator is set to handle in-stream commands.
     * 
     * <p>In-stream commands are primarily useful if the replicator is
     * connected to multiple streams, and the input source is interactive
     * (such as a console). The commands can be used to select the target
     * stream to receive inputs, put the replicator in broadcast mode,
     * and query for available streams. If the input source is not
     * interactive, however, this may cause unexpected behavior or
     * data mangling.</p>
     * 
     * @return <code>true</code> if the replicator is set to handle
     * in-stream commands, <code>false</code> otherwise.
     */
    public boolean streamCommandsEnabled() {
        return enableStreamCmds;
    }
    
    /**
     * Specifies whether the replicator is set to handle in-stream commands.
     * 
     * <p>In-stream commands are primarily useful if the replicator is
     * connected to multiple streams, and the input source is interactive
     * (such as a console). The commands can be used to select the target
     * stream to receive inputs, put the replicator in broadcast mode,
     * and query for available streams. If the input source is not
     * interactive, however, this may cause unexpected behavior or
     * data mangling.</p>
     * 
     * @param state <code>true</code> to activate handling of in-stream
     * commands, <code>false</code> to transmit all input verbatim.
     * 
     * @return Whether the replicator was already set to handle in-stream
     * commands.
     * 
     * @throws IllegalStateException If the replicator is no longer
     * connected to the input source.
     */
    public boolean enableStreamCommands(boolean state) {
        if (!connected) {
            throw new IllegalStateException("Input disconnected");
        }
        
        boolean alreadyWas = enableStreamCmds;
        enableStreamCmds = state;
        return alreadyWas;
    }
    
    /**
     * Copies data from an input source to one or all connected streams
     * in accordance with the replicator configuration.
     */
    public void run() {
        int ch;

        try {
            read:
            while (true) {
                ch = in.read();
                switch (ch) {
                case -1:
                    break read;
                case CMD_CHAR:
                    if (enableStreamCmds) {
                        int act = handleCmd();
                        switch (act) {
                        case EOF_STOP:
                            break read;
                        case SKIP_CHAR:
                            break;
                        case ECHO_CHAR:
                            if (!broadcast) {
                                if (selectedStream != null) {
                                    selectedStream.print(CMD_CHAR);
                                }
                            }
                            else {
                                for (PrintStream stream : streams.values()) {
                                    stream.print(CMD_CHAR);
                                }
                            }
                            break;
                        default:
                            break;
                        }
                    }
                    else {
                        if (!broadcast) {
                            if (selectedStream != null) {
                                selectedStream.print(CMD_CHAR);
                            }
                        }
                        else {
                            for (PrintStream stream : streams.values()) {
                                stream.print(CMD_CHAR);
                            }
                        }
                    }
                    break;
                default:
                    if (!broadcast) {
                        if (selectedStream != null) {
                            selectedStream.print((char) ch);
                        }
                    }
                    else {
                        for (PrintStream stream : streams.values()) {
                            stream.print((char) ch);
                        }
                    }
                    break;
                }
                if (!in.ready()) {
                    if (!broadcast) {
                        if (selectedStream != null) {
                            selectedStream.flush();
                        }
                    }
                    else {
                        for (PrintStream stream : streams.values()) {
                            stream.flush();
                        }
                    }
                }
            }

            if (!broadcast) {
                if (selectedStream != null) {
                    selectedStream.flush();
                }
            }
            else {
                for (PrintStream stream : streams.values()) {
                    stream.flush();
                }
            }
            in.close();

            for (PrintStream toClose : shutdownStreams) {
                toClose.close();
            }
            shutdownStreams.clear();
            streams.clear();
        }
        catch (Exception e) {
            e.printStackTrace(stderr);
            stderr.println("Abnormal exception in stream " +
                "redirection thread \"" + Thread.currentThread().getName() +
                "\"!");
        }
        finally {
            connected = false;
        }
    }

    private final int handleCmd() throws IOException {
        int ch = in.read();
        for (;;) {
            switch (ch) {
            case -1:
                return EOF_STOP;
            case CMD_CHAR:
                return ECHO_CHAR;
            case 'b':
                broadcast = true;
                System.out.print("broadcast> ");
                return chopNewline();
            case 'c':
                if (broadcast) {
                    System.out.println("[broadcasting]");
                }
                else {
                    System.out.println("[stream " + selectedId + "]");
                    System.out.print(selectedId + "> ");
                }
                return chopNewline();
            case 'd':
                enableStreamCmds = false;
                System.out.println("[disable stream commands]");
                return chopNewline();
            case 'l':
                Iterator<Integer> iter = streams.keySet().iterator();
                System.out.print("[ ");
                while (iter.hasNext()) {
                    System.out.print(iter.next() + " ");
                }
                System.out.println("]");
                return chopNewline();
            case 'q':
                System.out.println("[disconnect input]");
                return EOF_STOP;
            default:
                int act = readAndSetStream(ch);
                if (act == NEXT_CMD) {
                    ch = in.read();
                }
                else {
                    return act;
                }
                break;
            }
        }
    }

    private final int readAndSetStream(int ch) throws IOException {
        int act = SKIP_CHAR;
        StringBuilder sb = new StringBuilder();

        read:
        for (;;) {
            switch (ch) {
            case -1:
                return EOF_STOP;
            case ' ':
                break;
            case '%':
                act = NEXT_CMD;
                break read;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                sb.append((char) ch);
                break;
            case '\n':
                break read;
            case '\r':
                if (in.ready()) {
                    in.mark(1);
                    ch = in.read();
                    switch (ch) {
                    case -1:
                        return EOF_STOP;
                    case '\n':
                        break read;
                    default:
                        in.reset();
                        break read;
                    }
                }
                else {
                    break read;
                }
            default:
                stderr.println("[unknown command]");
                return chopNewline();
            }
            ch = in.read();
        }

        Integer reqId = Integer.valueOf(sb.toString());
        if (selectStream(reqId)) {
            System.out.print(reqId + "> ");
        }
        else {
            stderr.println("[unknown stream id: " + reqId + "]");
        }

        return act;
    }

    private final int chopNewline() throws IOException {
        if (in.ready()) {
            in.mark(1);
            int ch = in.read();
            switch (ch) {
            case '\n':
                return SKIP_CHAR;
            case '\r':
                if (in.ready()) {
                    in.mark(1);
                    ch = in.read();
                    switch (ch) {
                    case -1:
                        return EOF_STOP;
                    case '\n':
                        return SKIP_CHAR;
                    default:
                        in.reset();
                        return SKIP_CHAR;
                    }
                }
                else {
                    return SKIP_CHAR;
                }
            default:
                in.reset();
                return SKIP_CHAR;
            }
        }
        else {
            return SKIP_CHAR;
        }
    }

    /**
     * Basic test driver code.
     */
    static void main(String[] args) throws Exception {
        StreamReplicator stdin = new StreamReplicator(System.in, System.err);

        Thread stdinThread = new Thread(stdin);
        stdinThread.setDaemon(true);
        stdinThread.start();

        Thread.sleep(10000);
        System.out.println("Connecting stdout");
        stdin.connectStream(System.out, false, false);

        Thread.sleep(10000);
        System.out.println("Connecting stream");
        stdin.connectStream(new java.io.FileOutputStream("stream2.txt"),
                true, true);

        Thread.sleep(25000);
        System.out.println("Connecting stream");
        stdin.connectStream(new java.io.FileOutputStream("stream3.txt"),
                true, true);

        Thread.sleep(25000);
        System.out.println("Connecting stream");
        stdin.connectStream(new java.io.FileOutputStream("stream4.txt"),
                true, true);

        java.io.PipedOutputStream cmdStream = new java.io.PipedOutputStream();
        java.io.PipedInputStream waitStream =
            new java.io.PipedInputStream(cmdStream);

        stdin.connectStream(cmdStream, true, false);
        
        BufferedReader in = new BufferedReader(
                new InputStreamReader(waitStream));
        String ln;
        while ((ln = in.readLine()) != null) {
            if (ln.equals("STOP")) {
                return;
            }
        }
    }
}
