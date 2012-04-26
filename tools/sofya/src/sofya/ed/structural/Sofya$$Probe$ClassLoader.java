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

package sofya.ed.structural;

import java.util.Map;
import java.util.HashMap;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;

/**
 * The probe class loader is used to load the probe implementation and
 * its dependent classes.
 * 
 * <p>This class loader functions as an isolating class loader. By
 * loading the probe implementation through this class loader, it and
 * all of its dependent classes are loaded in isolation from the main
 * application class loader. Thus classes, including external library
 * classes, required by the probe implementation will not interfere
 * with the loading of any application classes. This prevents
 * undesired interaction between the application and the probe
 * implementation.</p>
 * 
 * <p>This class is included in the bootstrap classpath of a subject
 * program launched by a {@link ProgramEventDispatcher}, so that it
 * can be located and used by the bootstrap class loader during
 * initialization of the probe interface.</p>
 * 
 * @author Alex Kinneer
 * @version 11/10/2006
 */
public class Sofya$$Probe$ClassLoader extends ClassLoader {
    /** Cache of classes loaded by this classloader, to ensure
        consistency. */
    private Map<Object, Object> classCache = new HashMap<Object, Object>();
    /** Library classpath, used to locate library classes if
        needed; read from a system property. */
    private String[] libPath;
    
    /** Name of the primary source for probe classes; all attempts to
        load classes start by searching this source first. This
        source should be present on the bootstrap classpath. */
    public static final String PRIMARY_SOURCE = "bootstrap.jar";
    /** Prefix used within the primary source to hide classes from
        the normal bootstrap class loader; only this class loader knows
        how to find classes stored with this prefix. */
    public static final String SOURCE_PREFIX = "inst/";

    /** Compile-time flag to control whether debug statements are
        included in the class. */
    private static final boolean DEBUG = true;
    
    {
        // The instance initializer checks for the library classpath
        // system property, and stores the value if it is set. Only
        // this class loader will know how to load classes from that
        // classpath (unless it is also provided in a standard
        // way to the current VM).
        
        String libProp = System.getProperty("PROBE_LIB_PATH");
        if (libProp != null) {
            libPath = libProp.split(File.pathSeparator);
            System.clearProperty("PROBE_LIB_PATH");
        }
    }

    /**
     * Creates a new probe class loader that delegates only to the
     * bootstrap class loader (by design).
     */
    Sofya$$Probe$ClassLoader() {
        super(null);
    }
    
    protected synchronized Class<?> findClass(String name)
            throws ClassNotFoundException {
        if (DEBUG) {
            System.out.println("Sofya$$Probe$ClassLoader::findClass(" +
                name + ")");
        }

        Class cl = (Class) classCache.get(name);
        if (cl != null) {
            return cl;
        }

        try {
            cl = loadFromJar(name, PRIMARY_SOURCE, SOURCE_PREFIX);
            if (cl == null) {
                if (libPath != null) {
                    cl = loadFromLibraryPath(name);
                }
            }
        }
        catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }

        if (cl == null) {
            throw new ClassNotFoundException(name);
        }

        classCache.put(name, cl);
        if (DEBUG) {
            System.out.println("Loaded: " + name);
        }
        
        return cl;
    }
    
    private Class<?> loadFromLibraryPath(String name) throws IOException {
        Class cl = null;
        int pathLen = libPath.length;
        searchLoop:
        for (int i = 0; i < pathLen; i++) {
            if (libPath[i].endsWith(".jar")) {
                cl = loadFromJar(name, libPath[i], null);
            }
            else {
                StringBuilder prepName = new StringBuilder(libPath[i]);
                if (!libPath[i].endsWith("/")) {
                    prepName.append("/");
                }
                prepName.append(name.replace('.', '/'));
                prepName.append(".class");
                String clPathName = prepName.toString();
                
                if (DEBUG) {
                    System.out.println("dir clPathName: " + clPathName);
                }

                File clFile = new File(clPathName);
                if (clFile.exists()) {
                    cl = loadFromDirectory(name, clFile);
                }
            }
            if (cl != null) {
                break searchLoop;
            }
        }
        return cl;
    }
    
    private Class<?> loadFromJar(String name, String jarName, String prefix)
            throws IOException {
        if (DEBUG) {
            System.out.println("loadFromJar: " + jarName);
        }
        
        StringBuilder prepName;
        if (prefix == null) {
            prepName = new StringBuilder();
        }
        else {
            prepName = new StringBuilder(prefix);
        }
        prepName.append(name.replace('.', '/'));
        prepName.append(".class");
        String searchName = prepName.toString();
        
        if (DEBUG) {
            System.out.println("jar searchName: " + searchName);
        }

        JarFile sourceJar = new JarFile(jarName);
        ZipEntry ze = sourceJar.getEntry(searchName);
        if (ze == null) {
            return null;
        }

        BufferedInputStream clStream = new BufferedInputStream(
            sourceJar.getInputStream(ze));

        byte[] clBytes;
        int clSize = (int) ze.getSize();
        //clSize = -1;
        if (clSize != -1) {
            clBytes = new byte[clSize];
            int read = 0;
            while ((read += clStream.read(clBytes, read, (clSize - read)))
                    < clSize);
        }
        else {
            //System.out.println("Reading");
            //clBytes = new byte[1024];
            clBytes = new byte[32768];
            clSize = 0;
            int read;
            while ((read = clStream.read(
                    clBytes, clSize, clBytes.length - clSize)) != -1) {
                clSize += read;
                if (clSize == clBytes.length) {
                    //System.out.println("expanding");
                    //byte[] tmp = new byte[clBytes.length + 4096];
                    byte[] tmp = new byte[clBytes.length + 32768];
                    System.arraycopy(clBytes, 0, tmp, 0, clSize);
                    clBytes = tmp;
                }
            }
        }

        return defineClass(name, clBytes, 0, clSize);
    }

    private Class<?> loadFromDirectory(String name, File clFile)
            throws IOException {
        int clSize = (int) clFile.length();
        assert (clSize > 0);
        byte[] clBytes = new byte[clSize];
        ByteBuffer fileBuffer = ByteBuffer.wrap(clBytes);
        FileChannel fileChannel = (new FileInputStream(clFile)).getChannel();

        try {
            int read = 0;
            while ((read += fileChannel.read(fileBuffer)) < clSize);
        }
        finally {
            fileChannel.close();
        }

        return defineClass(name, clBytes, 0, clBytes.length);
    }
}
