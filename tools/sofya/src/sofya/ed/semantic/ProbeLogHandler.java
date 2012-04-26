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

package sofya.ed.semantic;

import java.io.IOException;

/**
 * A probe log handler is responsible for saving and loading class
 * change logs (see {@link sofya.ed.semantic.ClassLog}) to and from
 * persistent storage.
 * 
 * @author Alex Kinneer
 * @version 09/29/2006
 */
interface ProbeLogHandler {
    /**
     * Initializes the log handler; provides the log handler an opportunity
     * to perform setup tasks, if necessary.
     * 
     * @throws IOException If an error occurs during handler initialization.
     */
    void initializeLogHandler() throws IOException;
    
    /**
     * Loads a {@link sofya.ed.semantic.ClassLog} from persistent storage.
     * 
     * @param className Name of the class for which to load the change log.
     * 
     * @return The log of changes made to the specified class by the
     * {@link SemanticInstrumentor}.
     * 
     * @throws IOException If a log for the specified class cannot
     * be found (a <code>FileNotFoundException</code> if backed by
     * a disk file), or on any error reading the log from persistent
     * storage.
     */
    ClassLog getLog(String className) throws IOException;
    
    /**
     * Saves a {@link sofya.ed.semantic.ClassLog} to persistent storage.
     * 
     * @param log Class log to be saved.
     * 
     * @throws IOException On any error saving the log to persistent
     * storage.
     */
    void saveLog(ClassLog log) throws IOException;
    
    /**
     * Releases this log handler; provides the log handler an opportunity
     * to perform cleanup and shutdown tasks, if necessary.
     * 
     * <p>This method serves a signal to the log handler that no further
     * requests will be sent to retrieve or save logs.</p>
     * 
     * @throws IOException If an error occurs during handler shutdown.
     */
    void shutdownLogHandler() throws IOException;
}
