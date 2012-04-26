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

package sofya.ed;

/**
 * <p>A basic block event listener is notified by an event dispatcher each
 * time a basic block is executed.</p>
 *
 * @author Alex Kinneer
 * @version 03/17/2006
 */
public interface BlockEventListener {
    /**
     * <p>Initializes the event listener. Listeners that need to perform
     * configuration or setup of state that will persist across multiple
     * event streams should implement this method.</p>
     *
     * <p>This method is called automatically by a
     * {@link sofya.ed.structural.JUnitEventDispatcher} prior to beginning
     * execution of test cases. It is the responsibility of the applications
     * using other event dispatchers to call this method at the appropriate
     * time.</p>
     */
    void initialize();

    /**
     * <p>Notification that a new basic block event stream is starting.</p>
     *
     * @param streamId Identifier associated with the event stream, such as
     * a test case number.
     */
    void newEventStream(int streamId);

    /**
     * <p>Notification that a basic block event stream has completed.</p>
     *
     * @param streamId Identifier associated with the finished event stream,
     * such as a test case number.
     */
    void commitEventStream(int streamId);

    /**
     * <p>Notification that a new method has been entered.</p>
     *
     * <p>This method provides the listener an opportunity to perform some
     * action before beginning processing of basic block events in a new
     * method, such as setting up data structures.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method entered.
     * @param blockCount The number of basic blocks in the method.
     */
    void methodEnterEvent(String classAndSignature, int blockCount);

    /**
     * <p>Notification that a general basic block was executed.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * basic block was executed.
     * @param id Identifier of the basic block that was executed. Correlates
     * to the basic block identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void codeBlockExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a call block was executed.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * basic block was executed.
     * @param id Identifier of the basic block that was executed. Correlates
     * to the basic block identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void callBlockExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a return block was executed.</p>
     *
     * <p>A return block is a virtual block that does not correlate to any
     * code in the executing program. It serves only as a marker that
     * execution has returned to a caller following execution of a call
     * block.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * basic block was executed.
     * @param id Identifier of the basic block that was executed. Correlates
     * to the basic block identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void returnBlockExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that an entry block was executed.</p>
     *
     * <p>An entry block is a virtual block that does not correlate to any
     * code in the executing program. It serves only as a marker that
     * execution has entered a new method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * basic block was executed.
     * @param id Identifier of the basic block that was executed. Correlates
     * to the basic block identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void entryBlockExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that an exit block was executed.</p>
     *
     * <p>An exit block is a virtual block that does not correlate to any
     * code in the executing program. It serves only as a marker that
     * execution has exited a method.</p>
     *
     * <p>There is only one normal exit block in a method, but there may
     * be multiple exceptional exit blocks in a method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * basic block was executed.
     * @param id Identifier of the basic block that was executed. Correlates
     * to the basic block identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void exitBlockExecuteEvent(String classAndSignature, int id);
}
