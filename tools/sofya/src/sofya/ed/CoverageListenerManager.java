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

import sofya.ed.BlockCoverageListener;
import sofya.ed.BranchCoverageListener;

/**
 * <p>A coverage listener manager acts as a server to attach a single
 * coverage listener to a
 * {@link sofya.ed.structural.ProgramEventDispatcher} using a coverage
 * processing strategy during dispatch of coverage events for a particular
 * method. This is a special design to provide maximum performance during
 * coverage event monitoring. If it is desired to dispatch coverage events
 * to multiple coverage listeners simultaneously, the coverage listener
 * manager can serve listeners that perform this service by relaying
 * events to listeners of their own.</p>
 *
 * @see sofya.ed.structural.processors.BlockCoverageProcessingStrategy
 * @see sofya.ed.structural.processors.BranchCoverageProcessingStrategy
 * @see sofya.ed.structural.processors.JUnitBlockCoverageProcessingStrategy
 * @see sofya.ed.structural.processors.JUnitBranchCoverageProcessingStrategy
 *
 * @author Alex Kinneer
 * @version 11/07/2006
 */
public interface CoverageListenerManager {
    /**
     * <p>Initializes the coverage listener manager. Listener managers that
     * need to perform configuration or setup of state that will persist
     * across multiple event streams should implement this method.</p>
     *
     * <p>This method is called automatically by a
     * {@link sofya.ed.structural.JUnitEventDispatcher} prior to beginning
     * execution of test cases. It is the responsibility of the applications
     * using other event dispatchers to call this method at the appropriate
     * time.</p>
     */
    void initialize();
    
    /**
     * Specifies whether the listener manager synchronizes against multiple
     * thread accesses as necessary. This is <code>false</code> by
     * default, and should only be true when the object program is
     * another Sofya event dispatcher.
     * 
     * @param synch <code>true</code> to have the listener manager
     * synchronize against multiple thread accesses.
     */
    void setSynchronized(boolean synch);
    
    /**
     * Reports whether the listener manager synchronizes against multiple
     * thread accesses.
     * 
     * @return <code>true</code> if the listener manager synchronizes
     * against multiple thread accesses as necessary. This is
     * <code>false</code> by default.
     */
    boolean isSynchronized();

    /**
     * <p>Notification that a new event stream is starting.</p>
     *
     * @param streamId Identifier associated with the event stream, such as
     * a test case number.
     */
    void newEventStream(int streamId);

    /**
     * <p>Notification that an event stream has completed.</p>
     *
     * @param streamId Identifier associated with the finished event stream,
     * such as a test case number.
     */
    void commitCoverageResults(int streamId);

    /**
     * <p>Notification to initialize a basic block listener for a method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method for which a basic
     * block listener is to be initialized.
     * @param blockCount The number of basic blocks in the method.
     */
    void initializeBlockListener(String classAndSignature, int blockCount);

    /**
     * <p>Notification to initialize a branch listener for a method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method for which a branch
     * listener is to be initialized.
     * @param branchCount The number of basic blocks in the method.
     */
    void initializeBranchListener(String classAndSignature, int branchCount);

    /**
     * <p>Gets the basic block listener for a particular method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method for which a basic
     * block listener is to be retrieved.
     */
    BlockCoverageListener getBlockCoverageListener(String classAndSignature);

    /**
     * <p>Gets the branch listener for a particular method.</p>
     *
     * @param classAndSignature Concatenation of the fully qualified class
     * name, method name, and JNI signature of the method for which a branch
     * listener is to be retrieved.
     */
    BranchCoverageListener getBranchCoverageListener(String classAndSignature);
}
