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
 * <p>Indicates that a required parameter value was not provided to a
 * command line parameter.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public class ParameterValueAbsentException extends RuntimeException {

    private static final long serialVersionUID = 1428563523977834092L;

    public ParameterValueAbsentException() {
        super();
    }

    public ParameterValueAbsentException(String message) {
        super(message);
    }

    public ParameterValueAbsentException(Throwable cause) {
        super(cause);
    }

    public ParameterValueAbsentException(String message, Throwable cause) {
        super(message, cause);
    }
}
