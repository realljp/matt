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

package sofya.base;

import java.io.File;

/**
 * Utility class containing various constants, including the name of the
 * Sofya database directory.
 *
 * @author Alex Kinneer
 * @version 11/29/2004
 */
public class ProjectDescription {
    /** Name of the the Sofya database directory. */
    private static final String DBNAME = ".sofyadb";
    /** Path to the Sofya database directory. */
    public static final String dbDir;
    /** Version string for this build of Sofya. */
    public static final String versionString;
    /** Major release version number for this build. */
    public static final int RELEASE_VERSION = 2;
    /** Major revision number for this build. */
    public static final int MAJOR_REVISION = 1;
    /** Minor revision number for this build. */
    public static final int MINOR_REVISION = 2;
    /** Specifies whether branch ID extensions are enabled in this
        build of Sofya. */
    public static final boolean ENABLE_BRANCH_EXTENSIONS = true;
    
    /**
     * Constructs the absolute path to the database directory and the
     * version string.
     */
    static {
        dbDir = System.getProperty("user.home") + File.separatorChar + DBNAME;
        versionString = String.valueOf(RELEASE_VERSION) + "." +
                        String.valueOf(MAJOR_REVISION) + "." +
                        String.valueOf(MINOR_REVISION) + "-beta";
    }
    
    /************************************************************************
     * Private constructor.
     *
     * <p>Instantiation of this class is not permitted.</p>
     */
    private ProjectDescription() { }  
    
    /************************************************************************
     * Gets absolute path to the Sofya database directory.
     *
     * @return Absolute path to the Sofya database directory.
     */ 
    public static String getdbDir() {
        return dbDir;
    }
    
    /************************************************************************
     * Gets Sofya version string.
     *
     * @return The build version of this Sofya system, as a string.
     */
    public static String getVersionString() {
        return versionString;
    }
    
    /************************************************************************
     * Test driver for ProjectDescription.
     */
    public static void main(String[] args) {
        System.out.println("Sofya version: " +
            ProjectDescription.getVersionString());
        System.out.println("Release version = " +
            ProjectDescription.RELEASE_VERSION);
        System.out.println("Major revision = " +
            ProjectDescription.MAJOR_REVISION);
        System.out.println("Minor revision = " +
            ProjectDescription.MINOR_REVISION);
        System.out.println("Location of database directory: " +
            ProjectDescription.getdbDir());
    }
}



/*****************************************************************************/

/*
  $Log: ProjectDescription.java,v $
  Revision 1.8  2007/07/30 16:20:48  akinneer
  Minor version stepping.
  Updated year in copyright notice.

  Revision 1.7  2006/09/08 21:29:59  akinneer
  Updated copyright notice.

  Revision 1.6  2006/09/08 20:20:52  akinneer
  "Generified". Cleaned up imports.

  Revision 1.5  2006/04/17 18:32:13  akinneer
  Beta version stepping.

  Revision 1.4  2006/03/21 21:49:39  kinneer
  Fixed JavaDoc references to reflect post-refactoring package organization.
  Various minor code cleanups. Updated copyright notice.

  Revision 1.3  2005/11/17 20:14:46  kinneer
  Version stepping.

  Revision 1.2  2005/06/06 18:47:02  kinneer
  Added new class and copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.32  2004/09/14 19:15:43  kinneer
  Version stepping.

  Revision 1.31  2004/08/20 16:48:52  akinneer
  Defined new Galileo-wide flag to control computation of branch
  information.

  Revision 1.30  2004/06/28 18:44:58  kinneer
  Version stepping. 'Undeprecated' some methods - using method calls prevents
  compiler optimizations from eliminating conditional control flow in some
  circumstances (since the public fields are static and final). This is
  relevant to some test drivers which may need to check the values for a
  particular version to choose an appropriate action or configuration.

  Revision 1.29  2004/04/16 17:54:48  kinneer
  Version stepping.

  Revision 1.28  2004/02/18 19:01:59  kinneer
  Version stepping.

  Revision 1.27  2004/01/16 00:55:28  kinneer
  Version stepping to 3.0.0.

  Revision 1.26  2003/12/17 23:08:58  kinneer
  Made string fields final and public.  Method calls are no longer necessary.

  Revision 1.25  2003/12/16 18:12:51  kinneer
  Version stepping for 2.3.3.

  Revision 1.1.1.1  2003/11/07 02:14:59  creswick
  Library directories.


  Revision 1.24  2003/10/30 22:36:41  kinneer
  Version stepping.

  Revision 1.23  2003/10/23 01:10:32  kinneer
  Version stepping

  Revision 1.22  2003/10/20 23:08:45  kinneer
  Version stepping

  Revision 1.21  2003/10/14 00:05:41  kinneer
  Version stepping

  Revision 1.20  2003/10/11 00:01:54  kinneer
  Version stepping.

  Revision 1.19  2003/10/09 23:56:41  kinneer
  Version stepping.

  Revision 1.18  2003/10/03 17:38:34  kinneer
  Stepping to 2.2.2

  Revision 1.17  2003/09/25 16:37:42  kinneer
  Stepping to 2.3.0

  Revision 1.16  2003/09/17 00:02:18  kinneer
  *** empty log message ***

  Revision 1.15  2003/08/27 18:44:06  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.12  2003/08/19 00:01:49  kinneer
  Stepped version number.

  Revision 1.11  2003/08/13 18:28:37  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.10  2003/08/01 17:10:46  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.9  2002/07/04 06:56:48  sharmahi
  galileo/src/handlers/AbstractFile.java

  Revision 1.8  2002/06/25 09:09:57  sharmahi
  Added Package name "handlers"

  Revision 1.6  2002/06/09 08:45:27  sharmahi
  After first glance and review of fomrat, code style and file layout

  Revision 1.5  2002/01/21 22:16:05  sharmahi
  galileo/src/handlers/ProjectDescription.java

  Revision 1.4  2002/01/16 18:59:13  sharmahi
  galileo/src/handlers/ProjectDescription.java

  Revision 1.3  2002/01/16 18:58:50  sharmahi
  galileo/src/handlers/ProjectDescription.java
*/

