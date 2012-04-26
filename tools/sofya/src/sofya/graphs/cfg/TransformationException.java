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

package sofya.graphs.cfg;

/**
 * Exception which is raised by a {@link CFGTransformer} if it cannot
 * successfully apply its transformation for some reason.
 *
 * @author Alex Kinneer
 * @version 11/29/2004
 */
public class TransformationException extends Exception {

    private static final long serialVersionUID = 6601603393173331831L;
    
    /** Wrapped exception which indicates the original reason for failure
        (if applicable). */
    private Throwable cause = null;
    
    /**
     * Creates a transformation exception with no message or causing
     * exception.
     */
    public TransformationException() {
        super();
    }
    
    /**
     * Creates a transformation exception with the specified message and
     * no causing exception.
     * 
     * @param msg Message associated with this exception.
     */
    public TransformationException(String msg) {
        super(msg);
    }
    
    /**
     * Creates a transformation exception with the specified message and
     * causing exception.
     */
    public TransformationException(String msg, Throwable cause) {
        super(msg);
        this.cause = cause;
    }
    
    /**
     * Gets the wrapped exception indicating the original cause for failure.
     *
     * @return The original exception which caused this type inference
     * exception to be raised, may be <code>null</code>.
     */
    public Throwable getCause() {
        return cause;
    }
}
