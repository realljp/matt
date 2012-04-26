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

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.ProtectedJarOutputStream;
import sofya.base.exceptions.*;
import sofya.ed.structural.*;
import static sofya.base.SConstants.*;

import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.classfile.ClassFormatException;

/**
 * Front end to the Sofya structural instrumentors, used to instrument subject
 * class files.
 *
 * <p>Usage:<br><code>java sofya.ed.cfInstrumentor [-port <i>n</i>] [-so]
 *    [-cl <i>listfile</i>] [-t <i>inst_type</i>]
 *    &lt;-branch|-&lt;B|E|X|C&gt;&gt;
 *    &lt;<i>classname|jarfile|listfile</i>&gt;
 *    [<i>classname|jarfile</i> ...]
 * </code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>-port <i>n</i> : Instrument subject to
 * send trace statements on port number <i>n</i></code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>-so : Insert only the SocketProbe start
 * call in the appropriate location</code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>-cl <i>listfile</i> : Instrument only
 * those classes which are found in the given .prog file<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(primarily useful with jar files)
 * </code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>-type <i>inst_type</i> : Specifies type
 * of instrumentation to be inserted. Must be one of the given values:<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;comp : Compatible instrumentation,
 * works with all processing strategies for the program event dispatcher<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;norm : Normal (optimized)
 * instrumentation<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;junit : JUnit instrumentation,
 * works only with the JUnit event dispatcher and processing strategies<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;seq: Sequence instrumentation<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Additionally, each type of instrumentation
 * may also be qualified with the addition of &quot;,junit&quot;<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(as in &quot;-t seq,junit&quot;) to signify
 * that it is intended for use with the JUnit event dispatcher. The
 * unqualified<br> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;use of &quot;junit&quot;
 * (e.g. &quot;-t junit&quot;) is retained for compatibility with existing
 * scripts<br> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;and is equivalent to
 * &quot;-t norm,junit&quot;.<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;The default instrumentation type is
 * normal.</code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>&lt;-branch|-&lt;B|E|X|C&gt&gt; : If
 * the option &apos;-branch&apos; is specified, branch instrumentation will
 * be inserted. Otherwise basic block instrumentation is inserted and the
 * block types to be instrumented must be specified using any
 * permutation of the following : B-Basic,E-Entry,X-Exit,C-Call</code>
 * </p>
 *
 * <p><strong>Special note on thread safety:</strong> The following
 * considerations hold with respect to multi-threaded subjects:
 * <ul>
 * <li>Generation of coverage traces for non-JUnit subjects is always thread
 * safe - certain race conditions may exist but they only result in redundant
 * work, which is considered preferable to the cost of additional
 * synchronization.</li>
 * <li>For reasons related to the design of the JUnit framework, multi-threaded
 * modes of JUnit test execution should <strong>not</strong> be used with
 * instrumentation and {@link sofya.ed.structural.JUnitEventDispatcher}!</li>
 * <li>Sequence instrumentation is <strong>not</strong> thread-safe! Compatible
 * mode instrumentation should be used instead.</li>
 * <li>When sequence tracing a subject with compatible mode instrumentation,
 * it can only be guaranteed that no block will be erroneously traced
 * multiple times for a single traversal of the block during execution. No
 * guarantee can be made that all blocks will be traced in the exact order
 * in which they were traversed during execution of one thread relative to
 * blocks that may be traversed during execution of other threads.</li>
 * </ul></p>
 *
 * @author Alex Kinneer
 * @version 02/28/2007
 */
public final class cfInstrumentor {
    /** Instrumentor for performing actual instrumentation. */
    private static StructuralInstrumentor inst = null;
    /** Flag controlling whether to insert full instrumentation or just the
        call(s) to <code>start</code> (used for test drivers). */
    private static boolean starterOnly = false;

    /** Private default constructor does not permit instantiation. */
    private cfInstrumentor() { }

    /***************************************************************************
     * Prints the cfInstrumentor usage message and exits.
     */
    private static void printUsage() {
        System.err.println("Usage:\njava sofya.ed.cfInstrumentor " +
            "[-port N] [-so]\n [-t <comp|norm|junit|seq>" +
            "[,junit]] <-branch|-<B|E|X|C>>\n <classname|jarfile|listfile> " +
            "[classname|jarfile ...]");
        System.err.println("    -port <N> : Instrument subject to send " +
            "trace statements on port number N");
        System.err.println("    -so : Insert only the SocketProbe start " +
            "call in the appropriate location");
        System.err.println("    -t <comp|norm|junit|seq>[,junit] : Type of " +
            "instrumentation to be used");
        System.err.println("    <-branch|-<B|E|X|C>> : Use '-branch' to " +
            "insert branch instrumentation.\n      Otherwise basic block " +
            "instrumentation is inserted and the block types must\n      be " +
            "chosen using a permutation of the following:\n      B-Basic, " +
            "E-Entry, X-Exit, C-Call");
        System.exit(1);
    }

    /**
     * Helper method which instruments a given class file.
     *
     * @param className Name of the class to be instrumented, which will
     * be loaded from the classpath or filesystem.
     */
    private static void instrumentClasses(ProgramUnit pUnit) throws Exception {
        int clCount = pUnit.classes.size();
        Iterator iterator = pUnit.classes.iterator();
        for (int i = clCount; i-- > 0; ) {
            String entry = (String) iterator.next();
            try {
                if (pUnit.useLocation) {
                    inst.loadClass(pUnit.location +
                        entry.replace('.', File.separatorChar) + ".class");
                }
                else {
                    inst.loadClass(entry);
                }
            }
            catch (BadFileFormatException e) {
                System.err.println(e.getMessage());
                continue;
            }
            catch (EmptyFileException e) {
                System.err.println("WARNING: " + e.getMessage());
                continue;
            }
            catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                continue;
            }

            BufferedOutputStream fout = null;
            try {
                fout = new BufferedOutputStream(new FileOutputStream(
                        inst.getClassName() + ".class"));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("Unable to create output file");
                System.exit(1);
            }

            try {
                instrumentClass(fout);
            }
            finally {
                try {
                    fout.close();
                }
                catch (IOException e) {
                    System.err.println("WARNING: Failed to close file \"" +
                        inst.getClassName() + "\"");
                }
            }
        }
    }

    /**
     * Helper method which instruments all of the class files found
     * in a jar file.
     *
     * @param jarName Name of the jar file to be instrumented.
     */
    private static void instrumentJar(ProgramUnit jarUnit) throws Exception {
        JarFile sourceJar = new JarFile(jarUnit.location);

        ProtectedJarOutputStream instJar = null;
        File f = new File(jarUnit.location + ".inst");
        try {
            instJar = new ProtectedJarOutputStream(
                      new BufferedOutputStream(
                      new FileOutputStream(f)));
        }
        catch (IOException e) {
            IOException ioe = new IOException("Could not create output jar " +
                "file");
            ioe.fillInStackTrace();
            throw ioe;
        }

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
                boolean copyOnly = false;

                ZipEntry ze = (ZipEntry) e.nextElement();
                if (ze.isDirectory() || !ze.getName().endsWith(".class")) {
                    copyOnly = true;
                }

                if (!copyOnly) {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    try {
                        inst.loadClass(ze.getName(), entryStream);
                    }
                    catch (BadFileFormatException exc) {
                        System.err.println(exc.getMessage());
                        copyOnly = true;
                    }
                    catch (FileNotFoundException exc) {
                        System.err.println("WARNING: " + exc.getMessage());
                        copyOnly = true;
                    }
                }

                instJar.putNextEntry(new JarEntry(ze.getName()));
                if (!copyOnly) {
                    instrumentClass(instJar);
                }
                else {
                    entryStream = new BufferedInputStream(
                        sourceJar.getInputStream(ze));
                    Handler.copyStream(entryStream, instJar, false, false);
                    entryStream.close();
                }
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
                instJar.closeStream();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }

        if (f.exists()) {
            if (!f.renameTo(new File(jarUnit.location))) {
                System.out.println("Instrumented jar file is named " +
                    f.getName());
            }
        }
    }

    /**
     * Helper method which instruments the currently loaded class and
     * dumps it to the given stream.
     *
     * @param fout Stream to which the instrumented class should be
     * written.
     */
    private static void instrumentClass(OutputStream fout) throws Exception {
        if (!starterOnly) {
            inst.instrumentAll();
        }
        else {
            if (inst.hasStaticInit()) {
                try {
                    inst.insertStarter("<clinit>", Type.VOID, new Type[]{});
                }
                catch (MethodNotFoundException cannotHappen) { }
            }
            if (inst.hasMain()) {
                if (!inst.hasStaticInit()) {
                    try {
                        inst.insertStarter("main", Type.VOID,
                                new Type[]{new ArrayType(Type.STRING, 1)});
                    }
                    catch (MethodNotFoundException cannotHappen) { }
                }
                if ((inst.getInstMode() == INST_OPT_NORMAL) ||
                        (inst.getInstMode() == INST_OPT_SEQUENCE)) {
                    try {
                        inst.insertFinisher("main", Type.VOID,
                                new Type[]{new ArrayType(Type.STRING, 1)});
                    }
                    catch (MethodNotFoundException cannotHappen) { }
                }
            }
        }
        inst.writeClass(fout);
    }

    /***************************************************************************
     * Entry point for cfInstrumentor.
     *
     * <p>Parses the command line parameters, creates the Instrumentor,
     * and instruments the specified class.</p>
     */
    public static void main(String[] argv) {
        /* If an argument doesn't start with '-', this list is searched for a
           match to the previous argument to decide whether to stop processing
           arguments and invoke the constructor. (The first argument that
           doesn't start with '-' and which doesn't follow an argument found
           in this list is assumed to be the name of the first class to be
           instrumented).
         */
        final String[] copyParams = new String[]{"-port", "-t", "-tag"};
        String tag = null;
        TraceObjectType objectType = TraceObjectType.BASIC_BLOCK;

        if (argv.length < 2) {
            printUsage();
        }

        // Copy arguments and first class name to be passed to constructor.
        // Will leave index pointing to second class name (if any).
        LinkedList<Object> initArgs = new LinkedList<Object>();
        int index;
        for (index = 0; index < argv.length; index++) {
            if (argv[index].equals("-so")) {
                starterOnly = true;
            }
            else if (argv[index].equals("-branch")) {
                objectType = TraceObjectType.BRANCH_EDGE;
            }
            else {
                if (!argv[index].startsWith("-")) {
                    if (index == 0) {
                        printUsage();
                    }
                    int n; for (n = 0; n < copyParams.length; n++) {
                        if (copyParams[n].equals(argv[index - 1])) {
                            break;
                        }
                    }
                    if (n == copyParams.length) {
                        break;
                    }
                }
                else if (argv[index].equals("-tag")) {
                    if (index + 1 < argv.length) {
                        tag = argv[index + 1];
                    }
                    else {
                        System.err.println("Tag value not specified");
                        System.exit(1);
                    }
                }
                initArgs.add(argv[index]);
            }
        }

        // Read the list file or list of classes
        LinkedList<ProgramUnit> inputList = new LinkedList<ProgramUnit>();
        if ((index < argv.length) && argv[index].endsWith(".prog")) {
            try {
                Handler.readProgFile(argv[index], tag, inputList);
            }
            catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            catch (BadFileFormatException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        else {
            ProgramUnit defaultUnit = new ProgramUnit();
            inputList.add(defaultUnit);

            for ( ; index < argv.length; index++) {
                if (argv[index].endsWith(".jar")) {
                    ProgramUnit jarUnit = new ProgramUnit(argv[index]);

                    try {
                        Handler.readJarClasses(argv[index], jarUnit.classes);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    inputList.add(jarUnit);
                }
                else {
                    defaultUnit.addClass(argv[index]);
                }
            }
        }

        try {
            switch (objectType.toInt()) {
            case TraceObjectType.IBASIC_BLOCK:
                inst = new BlockInstrumentor((String[]) initArgs.toArray(
                    new String[initArgs.size()]));
                break;
            case TraceObjectType.IBRANCH_EDGE:
                inst = new BranchInstrumentor((String[]) initArgs.toArray(
                    new String[initArgs.size()]));
                break;
            }

            @SuppressWarnings("unused")
            long startTime = System.currentTimeMillis();

            while (inputList.size() > 0) {
                ProgramUnit pUnit = (ProgramUnit) inputList.removeFirst();

                if (pUnit.isJar) {
                    instrumentJar(pUnit);
                }
                else {
                    instrumentClasses(pUnit);
                }
            }

            @SuppressWarnings("unused")
            long endTime = System.currentTimeMillis();
            //System.err.println("Time elapsed (ms): " + (endTime - startTime));
            
            inst.prepareForExit();
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            if (!e.getMessage().startsWith("Port")) {
                printUsage();
            }
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error reading from class or jar file");
            System.exit(1);
        }
        catch (ClassFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
/*****************************************************************************/
/*
   $Log: cfInstrumentor.java,v $
   Revision 1.7  2007/07/30 19:47:55  akinneer
   Added call to structural instrumentor shutdown notification method
   (to support post-instrumentation actions).
   Removed utility method that was replaced.
   Updated year in copyright notice.

   Revision 1.6  2006/10/30 22:16:48  akinneer
   Tweaked stream copy method

   Revision 1.5  2006/10/24 20:13:32  akinneer
   Unified various stream copying functionality into Handler class

   Revision 1.4  2006/09/25 20:24:34  akinneer
   Additional refactoring of the instrumentors, to remove structural
   specific instrumentation concerns from base instrumentor class.

   Revision 1.3  2006/09/08 21:30:01  akinneer
   Updated copyright notice.

   Revision 1.2  2006/09/08 20:42:17  akinneer
   Generified. Cleaned up imports. Added serialUIDs to exceptions.

   Revision 1.1  2006/03/21 22:11:07  kinneer
   Imported from other packages, or new as part of refactoring.

   Revision 1.4  2005/06/06 18:47:47  kinneer
   Minor revisions and added copyright notices.

   Revision 1.3  2005/05/13 21:32:05  kinneer
   Modified to support new prog file format.

   Revision 1.2  2005/05/03 15:35:44  kinneer
   Implemented bug fix to ensure that non-classfiles get properly copied
   back into instrumented jar files.

   Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
   Sofya Java Bytecode Instrumentation and Analysis System

   Revision 1.33  2004/05/26 23:46:50  kinneer
   Implemented modifications to allow selection of branch instrumentation.

   Revision 1.32  2004/04/02 23:52:04  kinneer
   Fixed bug in '-so' parameter which was causing trace data to be lost.
   Implemented usage support for extension of other types of instrumentation
   to work with JUnit.

   Revision 1.31  2004/03/12 22:25:40  kinneer
   Implemented support for .prog files, all-or-nothing support for .jar files.

   Revision 1.30  2004/02/02 19:14:16  kinneer
   Added documentation note regarding multithreaded subjects.

   Revision 1.29  2004/01/16 01:03:12  kinneer
   Added '-t' parameter to control selection of different modes of
   instrumentation. Removed '-junit' parameter (use '-t junit'
   instead).

   Revision 1.28  2003/10/10 22:23:23  kinneer
   Added support for '-junit' parameter.

   Revision 1.27  2003/10/06 17:41:06  kinneer
   Forgot to change the variable declaration type when I changed the
   FileOutputStream to a BufferedOutputStream.

   Revision 1.26  2003/10/06 17:24:46  kinneer
   Output stream to file is buffered now.

   Revision 1.25  2003/08/27 18:45:00  kinneer
   Release 2.2.0.  Additional details in release notes.

   Revision 1.23  2003/08/18 18:43:26  kinneer
   See v2.1.0 release notes for details.

   Revision 1.22  2003/08/13 18:28:46  kinneer
   Release 2.0, please refer to release notes for details.

   Revision 1.21  2003/08/01 17:13:14  kinneer
   Entry/exit blocks instrumented correctly. Additional bug fixes,
   see release notes.

   Revision 1.20  2003/07/24 23:28:52  kinneer
   Javadocs/comments updated.

   Revision 1.19  2003/07/22 21:28:41  kinneer
   Secondary revision to major reimplementation of Filter tool. The
   first reimplementation was severely performance impaired (on the
   order of 10x-25x slower than the original Filter). The socket
   mechanism has been revised to eliminate the primary bottleneck
   (transient connections for each trace statement). Other revisions
   as necessary. Note that the SocketProbe is now used for all subjects,
   not just legacy Filters.

   WARNING: This version is a stable code commit only! It is intended
   to ensure a current codebase is present in CVS. DO NOT depend on
   the documentation in this release, as it may be incomplete or
   incorrect! A documentation revision will be checked in shortly.

   Revision 1.18  2003/07/09 18:37:56  kinneer
   Improved usage information.

   Revision 1.17  2003/07/08 23:18:56  kinneer
   Instrumentation of exception handler blocks fixed.  Start instructions
   for exception handlers are now properly updated to ensure execution of
   the inserted instrumentation statement.

   Revision 1.16  2003/07/08 00:12:09  kinneer
   All classes JavaDoc commented.  Readability cleanup.

   Revision 1.15  2003/07/07 20:21:32  kinneer
   Completely new thread-safe implementation of Filter and related
   changes to Instrumentor.  This is an initial commit simply for the
   sake of ensuring the new codebase is in the repository.  More
   extensive comments to follow with subsequent revisions.

   Known issues:
   1.  Port numbers above 32767 generate a ldc_w instruction rather than
       an sipush instruction.  Under some (rare) circumstances this appears to
       cause instrumentation problems.  Currently believed to be a bug
       in BCEL (incorrect generation of indices into the constant pool).

   Issues inherited from previous versions:
   1.  Instrumentation of constructors may be incorrect
   2.  Instrumentation of exception handling blocks is definitely incorrect

   Resolved issues:
   1.  Multi-threaded subjects are now handled correctly
   2.  Subjects should never be incorrectly tagged as not instrumented
   3.  Subject system.exit calls no longer require special handling
   4.  Ability to run Filter on itself, both new and legacy versions

   Other notes:
   ExeClass, Probe, and InstrumentedClassLoader are now deprecated.
   Implications for SequenceFilter are unclear pending discussion.
   FilterProbe removed.  New class SocketProbe added.

   Revision 1.12  2003/03/17 09:54:42  thorat
   *** empty log message ***

   Revision 1.11  2002/11/15 02:07:58  thorat
   gInstrumentor.java

   Revision 1.10  2002/08/20 22:57:57  thorat
   changed in setMethodSignature and setmethodName to add @ in the classname@method signature#blockid

   Revision 1.9  2002/08/14 22:21:01  thorat
   changes fro System.exit

   Revision 1.8  2002/07/31 16:58:17  thorat
   with all the exceptions

   Revision 1.6  2002/07/17 09:19:38  thorat
   added debugging statements

   Revision 1.5  2002/07/10 08:12:44  thorat
   added some more comments

   Revision 1.4  2002/07/10 04:49:12  thorat
   removed the debugging print statements.

   Revision 1.3  2002/07/10 02:03:53  thorat
   set the maxstack for instrumented bytecode

   Revision 1.2  2002/07/08 03:32:31  thorat
   added package information and integrated with cfg and handlers

   Revision 1.1  2002/06/27 22:15:49  thorat
   *** empty log message ***

*/



