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

package sofya.mutator.verifier;

/**
 * An exception to indicate that an error has occurred in the verifier
 * component.
 *
 * @author Alex Kinneer
 * @version 10/19/2005
 */
public class VerifierException extends Exception {

    private static final long serialVersionUID = -3598664533757193436L;
    
    /** The source of the error, if applicable. */
    private Throwable cause = null;
    
    /**
     * Creates a verifier exception.
     */
    public VerifierException() {
        super();
    }
    
    /**
     * Creates a new verifier exception with a given message.
     *
     * @param msg Message describing the error.
     */
    public VerifierException(String msg) {
        super(msg);
    }
    
    /**
     * Creates a new verifier exception with a message and encapsulating an
     * original cause for failure.
     *
     * @param msg Message describing the error.
     * @param cause Exception which indicated the error.
     */
    public VerifierException(String msg, Throwable cause) {
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
