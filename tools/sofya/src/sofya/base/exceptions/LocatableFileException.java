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
 * Exception that indicates that a format error was encountered
 * during the reading of a database file that can be traced to a
 * specific location in that file.
 *
 * @author Alex Kinneer
 * @version 11/04/2004
 */
public class LocatableFileException extends BadFileFormatException {
    private static final long serialVersionUID = -7666146462165320062L;
    
    /** Line number at which the error occurred. */
    private int lineNum = -1;

    /**
     * Creates a new locatable file exception.
     *
     * @param lineNum Line at which the format error occurred.
     */
    public LocatableFileException(int lineNum) {
        super();
        this.lineNum = lineNum;
    }

    /**
     * Creates a new locatable file exception with a message.
     *
     * @param msg Message associated with this exception.
     * @param lineNum Line at which the format error occurred.
     */
    public LocatableFileException(String msg, int lineNum) {
        super(msg);
        this.lineNum = lineNum;
    }

    /**
     * Gets the line number at which the format error was encountered.
     *
     * @return The line number of the badly formatted line.
     */
    public int getLineNumber() {
        return lineNum;
    }

    /**
     * Displays the message associated with this exception.
     *
     * @return The message associated with this exception.
     */
    public String getMessage() {
        return super.getMessage() + ", line " + lineNum;
    }

    /**
     * Converts this exception to string representation.
     *
     * @return The string representation of this exception.
     */
    public String toString() {
        return getMessage();
    }
}
