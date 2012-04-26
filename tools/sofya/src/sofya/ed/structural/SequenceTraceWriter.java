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

import java.net.Socket;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;

import sofya.base.Handler;
import sofya.ed.BlockEventListener;
import sofya.ed.BranchEventListener;
import sofya.ed.ParameterValueAbsentException;
import sofya.ed.structural.AbstractEventDispatcher.TraceFileException;

/**
 * <p>The sequence trace writer is responsible for generating basic block
 * and branch edge sequence trace files from the event streams produced
 * by a structural event dispatcher.</p>
 *
 * @author Alex Kinneer
 * @version 04/24/2006
 */
public final class SequenceTraceWriter
        implements ActiveComponent, BlockEventListener, BranchEventListener {
    /** Default trace file name. */
    private String trName = "instout";
    /** Flag specifying whether the trace writer should append current trace
        data to any existing trace file of the same name. */
    private boolean appendToTrace = false;
    /** Stream to which trace handler error outputs will be printed. */
    private PrintStream stderr = System.err;
    /** Flag indicating whether trace files are being generated from
        JUnit test cases. */
    private boolean forJUnit = false;

    /** Socket to which processed trace information should be relayed. */
    private static Socket relaySocket = null;
    /** Machine address to which the relay socket will attempt to connect. */
    public static final String relaySocketAddr = "127.0.0.1";
    /** Port to which the relay socket will attempt to connect. */
    public static final int relaySocketPort = 9288;

    /** String containing data to be recorded prior to trace data. */
    private static String preData = null;
    /** String containing data to be recorded after processing of trace data. */
    private static String postData = null;
    /** Flag which controls whether processed trace data is relayed to another
        socket or written to a file. */
    private static boolean toSocket = false;

    /** Writer for sequence output file. */
    private static PrintWriter pw;

    /** Flag indicating whether the trace writer has been initialized. */
    private boolean initialized;

    /**
     * Creates a new sequence trace writer using the default configuration.
     */
    public SequenceTraceWriter() {
    }

    /**
     * Creates a new sequence trace writer.
     *
     * @param sendToSocket Flag to indicate whether trace information should be
     * related to a socket.
     * @param prependData Data to be inserted at the beginning of each trace.
     * @param appendData Data to be appended at the end of each trace.
     * @param trName Name of the trace file to be created.
     * @param appendToTrace Flag specifying whether the trace writer should
     * append current trace data to any existing trace file of the same name.
     */
    public SequenceTraceWriter(boolean sendToSocket, String prependData,
            String appendData, String trName, boolean appendToTrace) {
        toSocket = sendToSocket;
        preData = prependData;
        postData = appendData;
        this.trName = trName;
        this.appendToTrace = appendToTrace;
    }

    public void register(EventDispatcherConfiguration edConfig) {
        stderr = edConfig.getStandardError();
    }

    public List<String> configure(List<String> params) {
        Iterator<String> li = params.iterator();
        while (li.hasNext()) {
            String param = li.next();
            
            if (param.startsWith("-")) {
                if (param.equals("-at")) {
                    li.remove();
                    appendToTrace = true;
                }
                else if (param.equals("-trname")) {
                    li.remove();
                    if (li.hasNext()) {
                        trName = (String) li.next();
                        li.remove();
                    }
                    else {
                        throw new ParameterValueAbsentException(
                            "Trace file name not specified");
                    }
                }
                else if (param.equals("-relay")) {
                    li.remove();
                    toSocket = true;
                }
                else if (param.equals("-pre")) {
                    li.remove();
                    if (li.hasNext()) {
                        preData = (String) li.next();
                        li.remove();
                    }
                    else {
                        throw new ParameterValueAbsentException(
                                "Pre-trace data not specified");
                    }
                }
                else if (param.equals("-post")) {
                    li.remove();
                    if (li.hasNext()) {
                        postData = (String) li.next();
                        li.remove();
                    }
                    else {
                        throw new ParameterValueAbsentException(
                                "Post-trace data not specified");
                    }
                }
            }
        }

        return params;
    }

    public void reset() {
        preData = null;
        postData = null;
    }

    public boolean isReady() {
        return initialized;
    }

    public void initialize() {
        if (toSocket) {
            if (relaySocket == null) {
                try {
                    relaySocket = new Socket(relaySocketAddr,
                                             relaySocketPort);
                    pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             relaySocket.getOutputStream())));
                }
                catch (Exception e) {
                    e.printStackTrace(stderr);
                    throw new TraceFileException("Could not create " +
                        "relay socket");
                }
            }
        }
        initialized = true;
    }

    public void release() {
        reset();

        if (toSocket) {
            try {
                relaySocket.close();
            }
            catch (IOException e) {
                stderr.println("WARNING: Attempt to close relay socket " +
                               " failed.");
            }
        }

        toSocket = false;
        this.initialized = false;
    }

    /*************************************************************************
     * Reports whether the trace writer is set to append the trace data for
     * the current event stream to any existing trace file or whether it
     * will overwrite it.
     *
     * @return <code>true</code> if the trace writer will append the trace
     * data for the current event stream to any existing trace file,
     * <code>false</code> otherwise.
     */
    public boolean isAppending() {
        return appendToTrace;
    }

    /*************************************************************************
     * Sets whether the trace writer will append the trace data for
     * the current event stream to any existing trace file or whether it
     * will overwrite it.
     *
     * @param enable <code>true</code> if the trace writer is to append the
     * trace data for the current event stream to any existing trace file,
     * <code>false</code> otherwise.
     */
    public void setAppending(boolean enable) {
        appendToTrace = enable;
    }

    /*************************************************************************
     * Gets the name of the trace file that will be written.
     *
     * @return The name of the trace file.
     */
    public String getTraceFileName() {
        return trName;
    }

    /*************************************************************************
     * Sets the name of the trace file to be written.
     *
     * @param value The name of the trace file to be written.
     */
    public void setTraceFileName(String value) {
        if ((value == null) || (value.length() == 0)) {
            throw new IllegalArgumentException("Trace file name must be " +
                "specified");
        }
        trName = value;
    }

    /*************************************************************************
     * Reports whether the trace writer will relay trace information to
     * a socket.
     *
     * @return <code>true</code> if trace information will be relayed to
     * a socket, <code>false</code> otherwise.
     */
    public boolean usingRelaySocket() {
        return toSocket;
    }

    /*************************************************************************
     * Sets whether the trace writer is to relay trace information to
     * a socket.
     *
     * @param enable <code>true</code> to specify that trace data should
     * be sent to a socket, <code>false</code> to specify that trace data
     * should be written to file.
     */
    public void useRelaySocket(boolean enable) throws IllegalStateException {
        toSocket = enable;
    }

    /*************************************************************************
     * Gets the data set to be inserted at the beginning of the trace.
     *
     * @return Data which will be inserted at the beginning of the trace.
     */
    public String getPreData() {
        return preData;
    }

    /*************************************************************************
     * Sets the data to be inserted at the beginning of the trace.
     *
     * @param data Data which will be inserted at the beginning of the
     * trace.
     */
    public void setPreData(String data) throws IllegalStateException {
        preData = data;
    }

    /*************************************************************************
     * Gets the data set to be inserted at the end of the trace.
     *
     * @return Data which will be inserted at the end of the trace.
     */
    public String getPostData() {
        return postData;
    }

    /*************************************************************************
     * Sets the data to be inserted at the end of the trace.
     *
     * @param data Data which will be inserted at the end of the trace.
     */
    public void setPostData(String data) throws IllegalStateException {
        postData = data;
    }

    /*************************************************************************
     * Reports whether trace files are being generated from JUnit test
     * cases.
     *
     * @return <code>true</code> if trace data is being generated from JUnit
     * test cases, <code>false</code> otherwise.
     */
    public boolean isForJUnit() {
        return forJUnit;
    }

    /*************************************************************************
     * Specifies whether trace files are being generated from JUnit test
     * cases.
     *
     * @param enable <code>true</code> if trace data is being generated from
     * JUnit test cases, <code>false</code> otherwise.
     */
    public void setForJUnit(boolean enable) {
        this.forJUnit = enable;
    }

    /**
     * Notification that a new event stream is starting.
     *
     * <p>Note that if the trace writer is generating trace files, this is
     * the point at which a new trace file is opened for output. Any
     * previous trace file must be moved or copied before this event is
     * received unless the trace writer is configured to append to the
     * existing trace. This is handled automatically by when the trace
     * writer is being used by a JUnit <code>SelectiveTestRunner</code>
     * and the &apos;-o&apos; option was passed to the test runner.</p>
     *
     * @param streamId {@inheritDoc}
     */
    public void newEventStream(int streamId) {
        if (!toSocket) {
            try {
                pw = new PrintWriter(
                     new BufferedWriter(
                     new OutputStreamWriter(Handler.openOutputFile(
                         trName + ".seq", null, appendToTrace))));
            }
            catch (Exception e) {
                e.printStackTrace(stderr);
                throw new TraceFileException("Could not create trace file");
            }
        }

        if (forJUnit) {
            pw.print(streamId);
        }

        if (preData != null) {
            pw.print(preData);
            if (pw.checkError()) {
                throw new TraceFileException("Error writing pre-trace " +
                    "data");
            }
        }
    }

    /**
     * Notification that an event stream has completed.
     *
     * <p>Note that if the trace writer is generating trace files, this is
     * the point at which the output stream to the trace file is closed.</p>
     *
     * @param streamId {@inheritDoc}
     */
    public void commitEventStream(int streamId) {
        if (pw != null) {
            if (forJUnit) {
                pw.print(")x");
                if (toSocket) {
                    pw.print("\n");
                }
                else {
                    pw.print(" ");
                }
                pw.flush();
            }

            if (postData != null) {
                pw.print(postData + "\n");
            }
            pw.print("\n");
            pw.flush();  // JLaw, 1/21/2004: Why? Paranoia. Raging paranoia.

            if (!toSocket) {
                pw.close();
                pw = null;
            }
        }

        if ((pw != null) && pw.checkError()) {
            throw new TraceFileException("Error writing finishing data " +
                "to trace");
        }
    }

    /**
     * Notification that a new method has been entered.
     *
     * @param classAndSignature {@inheritDoc}
     * @param count The number of structural entities in the method.
     */
    public void methodEnterEvent(String classAndSignature, int count) {
        // This event is not raised when processing sequence traces
    }

    public void codeBlockExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + "\tbasic block: " + id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void callBlockExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + "\tcall block: " + id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void returnBlockExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + "\treturn block: " + id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void entryBlockExecuteEvent(String classAndSignature, int id) {
        pw.print(classAndSignature.replace(' ','^'));
        if (toSocket) {
            pw.print("\n");
        }
        else {
            pw.print(" ");
        }
        // pw.println(classMethodName + "\tentry block: " +
        //     blockID);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void exitBlockExecuteEvent(String classAndSignature, int id) {
        pw.print(")r");
        if (toSocket) {
            pw.print("\n");
        }
        else {
            pw.print(" ");
        }
        // pw.println(classMethodName + "\texit block: " +
        //     blockID);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void ifBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void switchBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void throwBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void callBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void entryBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }

    public void otherBranchExecuteEvent(String classAndSignature, int id) {
        pw.println(classAndSignature + " " +  id);
        if (pw.checkError()) {
            throw new TraceFileException("Error writing to trace file");
        }
    }
}
