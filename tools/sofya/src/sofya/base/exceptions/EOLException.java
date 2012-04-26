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
 * Exception that indicates that a line ending was encountered
 * prematurely in a database file.
 *
 * @author Alex Kinneer
 * @version 11/04/2004
 */
public class EOLException extends LocatableFileException {

    private static final long serialVersionUID = 6940014084021378307L;

    /**
     * Creates a new end of line exception.
     *
     * @param lineNum Line which terminated prematurely.
     */
    public EOLException(int lineNum) {
        super(lineNum);
    }

    /**
     * Creates a new end of line exception with a message.
     *
     * @param msg Message associated with this exception.
     * @param lineNum Line which terminated prematurely.
     */
    public EOLException(String msg, int lineNum) {
        super(msg, lineNum);
    }

    /**
     * Displays the message associated with this exception.
     *
     * @return The message associated with this exception.
     */
    public String getMessage() {
        return super.getMessage() + ": Unexpected end of line";
    }
}
