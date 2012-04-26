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

import java.io.*;

/*************************************************************************
 * Class which reads from an input stream and pipes the data to a
 * specified output stream.
 *
 * <p>All input is immediately flushed, as it is possible (although
 * uncommon) for a subject to be designed to act on input without waiting
 * for a newline. Note however that all streams are buffered, so if input
 * occurs while this thread is not running it will be stored, and will be
 * written as a block before being flushed when the thread runs again. If
 * there is no input to be read, the thread will simply sleep.</p>
 *
 * <p>The thread will terminate automatically if either the input stream or
 * output stream is closed, so it is not necessary to explicitly signal this
 * thread when the subject terminates. It can however be manually terminated
 * by calling the <code>stop</code> method.</p>
 *
 * <p>The primary intent of this class is to provide a means for redirecting
 * input on stdin to a subject class. It is also used to prevent deadlock
 * when a subject is not instrumented.</p>
 */
public class StreamRedirector implements Runnable {
    /** Stream to which input will be piped. */
    private PrintStream out = null;
    /** Stream from which input will be piped. */
    private BufferedReader in = null;
    /** Flag specifying whether to close the output stream when the end of
        the input stream is reached. */
    private boolean closeOutOnFinish = false;
    /** Flag to explicitly kill thread. */
    private boolean forceStop = false;

    private PrintStream stderrStream;

    /***********************************************************************
        * Standard constructor, creates a buffered connection from the
        * specified input stream to the specified output stream.
        *
        * @param source Input stream from which data will be redirected to the
        * output stream.
        * @param sink Output stream to which input stream data will be
        * redirected.
        * @param closeOutOnFinish Specifies whether the output stream should be
        * closed if the end of the input stream is reached. This is true, for
        * example, when redirecting input to a subject.
        */
    public StreamRedirector(InputStream source, OutputStream sink,
                            boolean closeOutOnFinish,
                            PrintStream stderrStream) {
        this.in = new BufferedReader(new InputStreamReader(source));
        if (sink instanceof PrintStream) {
            this.out = (PrintStream) sink;
        }
        else {
            this.out = new PrintStream(new BufferedOutputStream(sink));
        }
        this.closeOutOnFinish = closeOutOnFinish;

        if (stderrStream != null) {
            this.stderrStream = stderrStream;
        }
        else {
            this.stderrStream = System.err;
        }
    }

    /***********************************************************************
        * Stops the thread.
        */
    public void stop() {
        forceStop = true;
    }

    /***********************************************************************
        * Pipes data from input stream to output stream.
        *
        * Loops reading the input stream and copying the data to the output
        * stream until one of the following events occurs:
        * <ul>
        *   <li>The input stream is closed.</li>
        *   <li>The output stream is closed.</li>
        *   <li>The <code>stop</code> method is called.</li>
        * </ul>
        *
        * Closure of the output stream is reported (not thrown) as an
        * exception.
        */
    public void run() {
        int ch;

        try {
            while (!forceStop) {
                ch = in.read();
                if (ch == -1) {
                    break;
                }
                out.print((char) ch);
                if (!in.ready()) {
                    out.flush();
                }
            }
            out.flush();
            in.close();
            if (closeOutOnFinish) out.close();
        }
        catch (Exception e) {
            e.printStackTrace(stderrStream);
            stderrStream.println("Abnormal exception in stream " +
                "redirection thread \"" + Thread.currentThread().getName() +
                "\"!");
        }
    }
}
