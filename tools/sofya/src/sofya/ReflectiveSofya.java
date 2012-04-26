
        package sofya;
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

//package sofya; // For Eclipse, remove for ant command-line compilation

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.Permission;

/**
 * A front-end wrapper for Sofya which provides the ability to run Sofya
 * analysis tools on themselves.
 *
 * <p>This program utilizes a custom class loader to ignore the system classpath
 * and execute a trusted "clean" Sofya codebase on a subject Sofya.  The system
 * classpath should point to the location of the subject Sofya. <i>It is not
 * necessory or desirable to use this class to run event dispatcher classes on
 * other event dispatcher classes as subjects. The event dispatcher and
 * instrumentor classes have been specially engineered to address this need.</i>
 *
 * <p>Usage:<br><code>java ReflectiveSofya [-o <i>OutputFile</i>]
 * &lt;Subject Class&gt; &lt;Subject Arguments&gt;</code><br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>-o <i>OutputFile</i> :
 * If the subject is gInstrumentor, redirect the output to <i>OutputFile</i>
 * </code></p>
 *
 * @author Alex Kinneer
 * @version 03/21/2006
 */
public final class ReflectiveSofya {
    /** The custom class loader which ignores the system classpath
        as appropriate. */
    private static SofyaClassLoader cLoader;
    /** Debug flag to control printing of debug trace statements. */
    protected static boolean debug = false;
    /** File to which output is redirected if <code>-o</code> option is used. */
    protected static String fileDestination = null;

    /** Number of (optional) command-line switches specific to this front-end */
    private static final int NUM_SWITCHES = 2;

    /**
     * Entry point for the Sofya front-end.
     *
     * <p>The desired Sofya tool is dynamically loaded from the immediate local
     * Sofya directory tree and invoked with the specified arguments. It is
     * expected that the arguments will consist of a Sofya subject class and
     * any parameters required by that subject class.</p>
     */
    public static void main(String argv[]) {
        System.setSecurityManager(new SofyaSecurityManager());
        cLoader = new SofyaClassLoader();

        try {
            if (argv.length == 0) {
                printUsage();
            }

            // Check for front-end specific switches. These are assumed to be
            // contiguous - once the application to be invoked is identified,
            // detection of switches for this front-end terminates. This is
            // necessary to avoid confusion with the switches used by
            // invoked tools.
            int i = 0;
            while ((i < NUM_SWITCHES) && argv[i].startsWith("-")) {
                switch (argv[i].charAt(1)) {
                    case 'd':
                        debug = true;
                        break;
                    case 'o':
                        if (i + 1 < argv.length) {
                            fileDestination = argv[i+1];
                            i++;
                        }
                        else {
                            printUsage();
                        }
                        break;
                    default:
                        printUsage();
                }
                i++;
            }

            if (i == argv.length) {
                printUsage();
            }

            // Load the 'clean' version of the class file of the tool to be run
            Class mainClass = cLoader.loadClass(argv[i++]);

            if ((i + 2 < argv.length) && argv[i+1].endsWith("Filter") &&
                    (argv[i+i].indexOf("inst") != -1)) {
                System.err.println("Do not use this tool to run a filter on " +
                    "another filter!");
                System.exit(1);
            }

            // Shift the arguments array to eliminate front-end
            // specific switches
            String params[] = new String[argv.length - i];
            for ( int n = i ; i < argv.length; i++) {
                params[i - n] = argv[i];
            }

            if (debug) {
                for (int n = 0; n < params.length; n++) {
                    System.out.println("Subject param " + n + ": " + params[n]);
                }
            }

            // Invoke main on the loaded class to initiate execution
            mainClass.getMethod("main", new Class[]{
                (new String[]{}).getClass()})
                .invoke(null, new Object[]{params});

            // If running instrumentor and relocation of output file was
            // requested, do the relocation now
            if ((mainClass.getName().indexOf("cfInstrumentor") != -1)
                    && (fileDestination != null)) {
                String userDir = System.getProperty("user.dir",".");

                String[] fileList = (new File(userDir)).list();
                File outputFile = null;
                for (i = 0; i < fileList.length; i++) {
                    if (fileList[i].endsWith(".class")
                            && (fileList[i].indexOf(params[1]) != -1)) {
                        outputFile = new File(
                            userDir + File.separatorChar + fileList[i]);
                        break;
                    }
                }
                if (outputFile == null) {
                    System.err.println("Unable to find file to relocate!");
                    return;
                }

                if (!outputFile.renameTo(new File(fileDestination))) {
                    System.err.println("Error relocating output file!");
                }
            }
        }
        catch(ClassNotFoundException e) {
            System.out.println("Classfile does not exist");
            e.printStackTrace();
        }
        catch(NoSuchMethodException e){
            System.out.println("Method does not exist in the class");
            e.printStackTrace();
        }
        catch(SecurityException e){
            System.out.println("Security violation, cannot execute " +
                "the main method");
        }
        catch(IllegalArgumentException e){
            e.printStackTrace();
        }
        catch(IllegalAccessException e){
            System.out.println("Method is inaccessible, try making the " +
                "class public");
            e.printStackTrace();
        }
        catch(InvocationTargetException e){
            System.out.println("Exception thrown by executing method " +
                "in classfile:");
            e.getTargetException().printStackTrace();
        }
    }

    /**
     * Prints the Sofya wrapper usage message and exits.
     */
    private static void printUsage() {
        System.err.println("Usage:\njava ReflectiveSofya [-o OutputFile] " +
            "<Subject Class> <Subject Arguments>");
        System.err.println("-o <OutputFile> : If the subject is " +
            "jInstrumentor, redirect the output to <OutputFile>");
        System.exit(1);
    }

    /**
     * Custom security manager used to gain access to the runtime callstack.
     *
     * <p>This is used so we can determine based on the caller's context
     * whether we are loading classes from the protected cache of 'clean' Sofya
     * classes or whether we should delegate responsibility for loading the
     * class to the primordial system class loader (which will use the system
     * CLASSPATH). Note that this is normally an extremely bad idea, however
     * the requirements for this application are not typical.</p>
     */
    protected static class SofyaSecurityManager extends SecurityManager {
        /**
         * Check a permission.
         *
         * <p>We are not actually concerned with the security aspects of the
         * SecurityManager, so overriding this method with a guaranteed return
         * effectively disables security checks.</p>
         *
         * <p>Returns void, always. (No security exceptions will be
         * thrown).</p>
         */
        public void checkPermission(Permission perm) {
            return;
        }

        /**
         * Return the size of the runtime callstack.
         *
         * @return A primitive int representing the number of java.lang.Class
         * objects currently on the callstack.
         */
        public int getClassContextSize() {
            return getClassContext().length;
        }

        /**
         * Return the <code>java.lang.Class</code> that called the method which
         * is calling this method.
         *
         * <p>This is defined to be the first Class located on the callstack
         * which is three frames prior to the first instance of the
         * SofyaClassLoader on the callstack. The two intervening frames will
         * always correspond to the SofyaClassLoader Class which is checking
         * the callstack and the SofyaSecurityManager that is loaded to invoke
         * this method.  The first instance of the SofyaClassLoader is used
         * because the ClassLoader invokes itself recursively under some
         * circumstances, so we do not want to mistakenly identify itself as
         * the caller.</p>
         *
         * @return An array of two Objects. The first object will be the
         * <code>java.lang.Class</code> object corresponding to the identified
         * caller. The second object will be an Integer containing the index to
         * the location in the callstack of the  caller.  Returns {null, -1} if
         * the callstack is too small - this indicates that the caller is the
         * first class loaded by the runtime system on JVM initialization,
         * typically the main application class.
         */
        public Object[] getCallingClass() {
            Class[] classes = getClassContext();

            //System.err.println(" class context:");
            //for (int i = 0; i < classes.length; i++) {
            //    System.err.println("  " + classes[i]);
            //}

            if (debug) {
                for (int i = 0; i < classes.length; i++) {
                    System.out.println(i + ": " + classes[i]);
                }
            }

            for (int i = classes.length - 1; i >= 0; i--) {
                if (classes[i].getName()
                        .equals("ReflectiveSofya$SofyaClassLoader")) {
                    if (i+2 < classes.length)
                        return new Object[]{classes[i+2], new Integer(i+2)};
                    else
                        return new Object[]{null, new Integer(-1)};
                }
            }

            /** Shouldn't be here **/
            throw new Error();
        }

        /**
         * Return the <code>java.lang.Class</code> represented by the
         * specified index in the callstack.
         *
         * @return The <code>java.lang.Class</code> found at the specified
         * index in the callstack, or <code>null</code> if the index is
         * invalid (negative or larger than the size of the callstack).
         */
        public Class getCallingClass(int index) {
            Class[] classes = getClassContext();

            if ((index < 0) || (index > classes.length)) return null;

            return classes[index];
        }
    }

    /**
     * Custom ClassLoader which determines when to load Sofya classes from an
     * absolute internally specified location versus whether to load from the
     * system CLASSPATH.
     *
     * <p>This is accomplished by investigating the runtime callstack to
     * determine whether a class load request has originated from the executing
     * instance of Sofya which is expected to be &apos;clean&apos; or whether
     * it has originated from a non-Sofya class or Sofya class invoked as part
     * of testing. This class is dependent on
     * {@link ReflectiveSofya.SofyaSecurityManager}.</p>
     *
     * <p>Currently, the SofyaClassLoader expects to be instantiated by an
     * application running in the &apos;sofya&apos; directory, which is parent
     * to the &apos;graphs&apos;, &apos;base&apos;, &apos;inst&apos;,
     * &apos;viewers&apos;, and &apos;tools&apos; directories, which
     * constitutes the internal classpath from which the class loader attempts
     * to load &apos;clean&apos; Sofya class files.</p>
     */
    protected static class SofyaClassLoader extends ClassLoader {
        // HashMaps used to cache classes already loaded. One for classes
        // loaded from the system classpath and one for the 'clean' classpath.
        private Map<Object, Object> classes = new HashMap<Object, Object>();
        private Map<Object, Object> sofyaClasses = new HashMap<Object, Object>();

        // The internal classpath, expressed as directories relative to the
        // current working directory which are to be searched.
        private String[] path =
            new String[]{
              ".",
              "base",
              "base/exceptions",
              "graphs",
              "graphs/cfg",
              "graphs/irg",
              "ed",
              "ed/semantic",
              "ed/structural",
              "ed/structural/processors",
              "viewers",
              "tools",
              "tools/th",
              "apps",
              "apps/atomicity",
              "apps/dejavu"
            };

        /**
         * Attempt to load a class.
         *
         * <p>This method simply calls {@link #loadClass(String, boolean)}
         * with the parameter <code>resolve</code> set to <code>true</code>.</p>
         *
         * @return The requested <code>java.lang.Class</code>,
         * if it can be found.
         *
         * @throws ClassNotFoundException If the class cannot be found.
         */
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            //System.out.println("########## SCL public loadClass: " + name + " ##########");
            //return super.loadClass(name);
            return loadClass(name, true);
        }

        /**
         * Attempt to load a class.
         *
         * <p>Override of this method is intentional despite the delegation
         * model recommended by Sun. The objective is to prevent the system
         * classpath from being checked when we wish to load &apos;clean&apos;
         * Sofya classes, which cannot be accomplished by overriding just
         * <code>findClass()</code>.</p>
         *
         * @return The requested <code>java.lang.Class</code>,
         * if it can be found.
         *
         * @throws ClassNotFoundException If the class cannot be found.
         */
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                                     throws ClassNotFoundException {
            if (debug) {
                System.out.println("\n########## Load " + name + " ##########");
            }
            //if (debug) System.out.println(this);

            boolean checkSystemClasses = true;
            boolean useSofyaCache = false;

            SofyaSecurityManager sm =
                (SofyaSecurityManager) System.getSecurityManager();

            Object[] callerInfo = sm.getCallingClass();
            Class cl = (Class) callerInfo[0];
            //System.out.println(cl);
            int callerIndex = ((Integer) callerInfo[1]).intValue();
            //System.out.println(callerIndex);

            if (name.startsWith("sofya.") && (cl == null)) {
                //System.out.println("Cond1: " + name);
                useSofyaCache = true;
                checkSystemClasses = false;
            }
            else if (name.startsWith("sofya.")
                    && !(cl.getName().startsWith("sofya."))
                    && (sm.getClassContextSize() == callerIndex+4)
                    && sm.getCallingClass(callerIndex+3).getName()
                       .equals("ReflectiveSofya"))
            {
                //System.out.println("Cond2: " + name);
                useSofyaCache = true;
                checkSystemClasses = false;
            }
            else if (name.startsWith("sofya.")
                    && cl.getName().startsWith("sofya.")) {
                //System.out.println("Cond3: " + name);
                useSofyaCache = true;
                checkSystemClasses = false;
            }

            if (useSofyaCache) {
                if (debug) System.out.println("Using Sofya cache for: " + name);
                cl = (Class)sofyaClasses.get(name);
            }
            else {
                //System.out.println("Using system cache for: " + name);
                cl = (Class)classes.get(name);
            }

            if (cl == null) {
                if (checkSystemClasses) {
                    //System.err.println("Finding system class for: " + name);
                    try {
                        return findSystemClass(name);
                    }
                    catch (Exception e) { }
                }
                //System.err.println("Finding sofya class for: " + name);
                cl = findClass(name);
                if (useSofyaCache) {
                    sofyaClasses.put(name, cl);
                }
                else {
                    classes.put(name, cl);
                }
            }

            if (resolve) resolveClass(cl);

            return cl;
        }

        /**
         * Attempt to locate a class.
         *
         * <p>This method is responsible for searching the internally
         * specified classpath. It is called by this class loader when
         * the system classpath is not to be searched.</p>
         *
         * @return The requested <code>java.lang.Class</code>,
         * if it can be found.
         *
         * @throws ClassNotFoundException If the class cannot be found.
         */
        protected synchronized Class<?> findClass(String name)
                                     throws ClassNotFoundException {
            String simpleName = name.substring(name.lastIndexOf(".") + 1);
            String userDir = System.getProperty("user.dir", ".");

            for (int i = 0; i < path.length; i++) {
                File classFile = new File(userDir + File.separatorChar +
                                          path[i] + File.separatorChar +
                                          simpleName + ".class");
                if (classFile.exists()) {
                    byte[] classData = loadClassBytes(classFile);
                    if (classData == null)
                        throw new ClassNotFoundException(name);

                    Class cl =
                        defineClass(name, classData, 0, classData.length);
                    if (cl == null) throw new ClassNotFoundException(name);

                    //classes.put(name, cl);
                    return cl;
                }
            }
            throw new ClassNotFoundException(name);
       }

        /**
         * Read the specified classfile from the filesystem and convert it
         * into an array of bytes that can be used by a call to
         * <code>defineClass()</code>.
         *
         * @return A byte array containing the bytecodes of the specified
         * class file.
        */
        private byte[] loadClassBytes(File classFile) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(classFile);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int ch;
                while ((ch = in.read()) != -1) {
                    byte b = (byte)ch;
                    buffer.write(b);
                }
                in.close();
                return buffer.toByteArray();
            }
            catch (IOException e) {
                if (in != null) {
                    try {
                        in.close();
                    }
                    catch (IOException e2) { }
                }
                return null;
            }
        }
    }
}
