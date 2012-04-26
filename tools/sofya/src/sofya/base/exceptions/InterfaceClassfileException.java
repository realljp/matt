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
 * Defines an exception that indicates that a class file defines an
 * interface, and the current tool cannot operate on an interface.
 *
 * @author Alex Kinneer
 * @version 07/25/2006
 */
public class InterfaceClassfileException extends BadFileFormatException {

    private static final long serialVersionUID = 4048323352829837122L;

    /**
     * Creates new instance of this exception using the default message.
     */
    public InterfaceClassfileException() {
        super("Classfile defines an interface");
    }

    /**
     * Creates new instance of this exception with a given message.
     *
     * @param msg Message to be associated with this exception in place
     * of the default message.
     */
    public InterfaceClassfileException(String msg) { 
        super(msg) ;
    }

    /**
     * Creates an instance with the given message wrapping another
     * exception.
     *
     * @param msg Message describing the error.
     * @param cause Exception that indicated the error.
     */
    public InterfaceClassfileException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
