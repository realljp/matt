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

import java.io.IOException;

/**
 * Exception that indicates that a given file contains data
 * that is malformed, corrupted, not the output of a Sofya tool, or
 * otherwise unreadable by the file handler.
 *
 * @author Hitesh Sharma
 * @version 08/19/2005
 */
public class BadFileFormatException extends IOException {

    private static final long serialVersionUID = 7745013348106196477L;
    
    private Throwable cause = null;

    /**
     * Creates new instance of this exception using the default message.
     */
    public BadFileFormatException() {
        super("Bad file format");
    }

    /**
     * Creates new instance of this exception with a given message.
     *
     * @param msg Message to be associated with this exception in place
     * of the default message.
     */
    public BadFileFormatException(String msg) { 
        super(msg) ;
    }

    /**
     * Creates an instance with the given message wrapping another
     * exception.
     */
    public BadFileFormatException(String s, Throwable e) {
        super(s);
        cause = e;
    }

    /** Gets the exception that is the original source of the problem
        (may be <code>null</code>). */
    public Throwable getCause() {
        return cause;
    }
}

/*
  $Log: BadFileFormatException.java,v $
  Revision 1.8  2007/07/30 16:18:40  akinneer
  Updated year in copyright notice.

  Revision 1.7  2006/09/08 21:30:00  akinneer
  Updated copyright notice.

  Revision 1.6  2006/09/08 20:18:47  akinneer
  Added generated serialUID fields.

  Revision 1.5  2006/07/25 21:44:11  akinneer
  Miscellaneous cleanup of source formatting and javadocs.

  Revision 1.4  2006/03/21 21:49:41  kinneer
  Fixed JavaDoc references to reflect post-refactoring package organization.
  Various minor code cleanups. Updated copyright notice.

  Revision 1.3  2005/10/12 19:05:38  kinneer
  Added wrapped exceptions.

  Revision 1.2  2005/06/06 18:47:11  kinneer
  Added copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.6  2003/08/18 18:42:34  kinneer
  See v2.1.0 release notes for details.

  Revision 1.5  2003/08/13 18:28:36  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.4  2003/08/01 17:10:45  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.3  2002/07/04 06:56:47  sharmahi
  galileo/src/handlers/AbstractFile.java

  Revision 1.2  2002/06/25 09:09:56  sharmahi
  Added Package name "handlers"

*/