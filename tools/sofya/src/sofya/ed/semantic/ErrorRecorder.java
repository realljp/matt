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

/**
 * Interface which marks an object as having error recording and reporting
 * capabilities.
 *
 * <p>Objects which need to report error conditions to a controlling
 * object, and to defer error handling by capturing and storing exceptions in
 * lieu of handling them immediately, should implement this interface. It is
 * intended primarily for use by event listeners which interact with a stateful
 * and potentially unreliable underlying object, such as a file stream.</p>
 *
 * @author Alex Kinneer
 * @version 02/04/2005
 */
public interface ErrorRecorder {
    /**
     * Reports whether the object is in an error state.
     *
     * @return <code>true</code> if an error was raised at some point
     * during the processing performed by the object.
     */
    public boolean inError();
    
    /**
     * Rethrows the originating exception if this object
     * in an error state; does nothing if in a normal state.
     *
     * @throws Exception If an error was raised at some point during the
     * processing performed by the object.
     */
    public void rethrowError() throws Throwable;
    
    /**
     * Gets the exception that put the object in an error state.
     *
     * @return The first unrecoverable exception that put the object
     * in an error state.
     */
    public Throwable getError();
}
