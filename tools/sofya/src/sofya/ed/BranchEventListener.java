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
 * <p>A branch event listener is notified by an event dispatcher each
 * time a branch is executed.</p>
 *
 * @author Alex Kinneer
 * @version 03/17/2006
 */
public interface BranchEventListener {
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
     * <p>Notification that a new branch event stream is starting.</p>
     *
     * @param streamId Identifier associated with the event stream, such as
     * a test case number.
     */
    void newEventStream(int streamId);

    /**
     * <p>Notification that a branch event stream has completed.</p>
     *
     * @param streamId Identifier associated with the finished event stream,
     * such as a test case number.
     */
    void commitEventStream(int streamId);

    /**
     * <p>Notification that a new method has been entered.</p>
     *
     * <p>This method provides the listener an opportunity to perform some
     * action before beginning processing of branch events in a new
     * method, such as setting up data structures.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method entered.
     * @param branchCount The number of branches in the method.
     */
    void methodEnterEvent(String classAndSignature, int branchCount);

    /**
     * <p>Notification that an <code>if</code> branch was executed.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void ifBranchExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a <code>switch</code> branch was executed.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void switchBranchExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a <code>throws</code> branch was executed.</p>
     *
     * <p>Note that a particular <code>throws</code> statement is <em>not</em>
     * considered a branch if the control flow graph builder determines that
     * only one type of exception can ever be thrown.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void throwBranchExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a <code>call</code> branch was executed.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void callBranchExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that an <code>entry</code> branch was executed.</p>
     *
     * <p>Method entry is not actually a branch, however, this event is
     * provided for the benefit of analyses that may wish to have information
     * about method entry inlined in the event stream as a type-compatible
     * entity.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void entryBranchExecuteEvent(String classAndSignature, int id);

    /**
     * <p>Notification that a <code>summary</code> branch was executed.</p>
     *
     * <p>A summary branch is used to capture any branching of execution
     * that causes exit from the currently executing method. This is normally
     * associated with asynchronous exceptions and runtime exceptions that
     * can potentially be raised at too many locations to be practically
     * encoded in a control flow representation.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method in which the
     * branch was executed.
     * @param id Identifier of the branch that was executed. Correlates
     * to the branch identifiers reported in a
     * {@link sofya.graphs.cfg.CFG}.
     */
    void otherBranchExecuteEvent(String classAndSignature, int id);
}
