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

package sofya.viewers;

/**
 * An exception that indicates that a given file name is already in use.
 *
 * @author Hitesh Sharma
 * @version 07/08/2002
 */
public class SameFileNameException extends java.io.IOException {

    private static final long serialVersionUID = 5489167872737452740L;

    /*************************************************************************
     * Creates new instance of this exception using the default message.
     */
    public SameFileNameException() {
        super("Output file has same name") ;
    } 

    /*************************************************************************
     * Creates new instance of this exception with a given message.
     *
     * @param msg Message to be associated with this exception in place
     * of the default message.
     */
    public SameFileNameException(String msg) { 
        super(msg) ;
    }
}



/*****************************************************************************/

/*
  $Log: SameFileNameException.java,v $
  Revision 1.7  2007/07/30 15:58:45  akinneer
  Updated year in copyright notice.

  Revision 1.6  2006/09/08 21:30:16  akinneer
  Updated copyright notice.

  Revision 1.5  2006/09/08 20:54:04  akinneer
  "Generified". Cleaned up imports. Removed dead variables. Added
  serialUID fields to exception classes.

  Revision 1.4  2006/03/21 21:51:01  kinneer
  Updated JavaDocs to reflect post-refactoring package organization.
  Various minor code cleanups. Modified copyright notice.

  Revision 1.3  2005/10/12 19:08:59  kinneer
  Minor JavaDoc revision.

  Revision 1.2  2005/06/06 18:48:08  kinneer
  Added copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.4  2003/08/18 18:43:33  kinneer
  See v2.1.0 release notes for details.

  Revision 1.3  2003/08/13 18:28:53  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.2  2003/08/01 17:13:54  kinneer
  Viewers interface deprecated. Viewer abstract class introduced. See
  release notes for additional details.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.1  2003/03/03 20:41:00  aristot
  Moved SameFileNameException to viewers dir

  Revision 1.2  2002/07/08 05:45:36  sharmahi
  Added package name

  Revision 1.1  2002/07/03 06:16:16  sharmahi
  galileo/src/handlers/AbstractFile.java

  Revision 1.2  2002/06/25 09:09:56  sharmahi
  Added Package name "handlers"

*/