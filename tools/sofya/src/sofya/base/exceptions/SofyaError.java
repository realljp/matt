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

package sofya.base.exceptions;

/**
 * Error that indicates that a serious internal error has occurred in
 * the Sofya system.
 *
 * @author Alex Kinneer
 * @version 09/23/2004
 */
public class SofyaError extends Error {

    private static final long serialVersionUID = -5826796565428076734L;
    
    private Throwable cause = null;

    /**
     * Creates a new Sofya error.
     */
    public SofyaError() {
        super();
    }

    /**
     * Creates a new Sofya error with a given message.
     *
     * @param msg Message describing the error.
     */
    public SofyaError(String msg) {
        super(msg);
    }

    /**
     * Creates a new Sofya error with a message and encapsulating an
     * original cause for failure.
     *
     * @param msg Message describing the error.
     * @param cause Exception which indicated the error.
     */
    public SofyaError(String msg, Throwable cause) {
        super(msg);
        this.cause = cause;
    }

    /**
     * Gets the exception that is the original source of the error
     * (may be <code>null</code>).
     */
    public Throwable getCause() {
        return cause;
    }
}
