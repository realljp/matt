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
 * Error which indicates some inconsistency in the compile-time configuration
 * of the Sofya system.
 *
 * @author Alex Kinneer
 * @version 09/23/2004
 */
public class ConfigurationError extends SofyaError {

    private static final long serialVersionUID = 5925846106028203997L;

    /**
     * Creates a new error indicating a configuration problem.
     */
    public ConfigurationError() {
        super();
    }

    /**
     * Creates a new error indicating a configuration problem with
     * a message.
     *
     * @param msg Message describing the nature of the configuration
     * error.
     */
    public ConfigurationError(String msg) {
        super(msg);
    }

    /**
     * Creates a new error indicating a configuration problem with
     * a message and encapsulating an original cause for failure.
     *
     * @param msg Message describing the nature of the configuration
     * error.
     * @param cause Exception which indicated the configuration error.
     */
    public ConfigurationError(String msg, Throwable cause) {
        super(msg, cause);
    }
}
