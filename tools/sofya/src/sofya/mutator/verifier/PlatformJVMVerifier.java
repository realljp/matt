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

package sofya.mutator.verifier;

import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.lang.reflect.Method;

import sofya.base.Utility;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import gnu.trove.THashMap;

/**
 * Adapter to use the runtime verifier of the current JVM to verify
 * class files.
 *
 * <p>This verifier has the desirable property of indicating whether or
 * not a mutation verifies on the particular JVM you intend to use
 * to run the mutated class. In an ideal world, this would make no
 * sense, but in the real world some verifiers are more
 * forgiving and/or buggy than others.</p>
 *
 * <p>For each class, this verifier uses a new class loader to load the
 * mutated bytecodes, triggering the JVM verifier to verify the class.
 * The same class loader is used until a new mutated class is loaded,
 * which permits transitive class verification.</p>
 *
 * <p>This verifier has limited reporting capabilities. It will only
 * report verification success or failure; specific failure causes
 * are not reported in a manner easily handled programmatically. The
 * quality of the messages on verification failure depend on the
 * quality of the messages produced by the JVM verifier. If the result
 * is a failure, the verification result can report whether the failure
 * occurred before or after pass 3, but it cannot distinguish between
 * passes 1 and 2, or passes 3a and 3b.</p>
 *
 * @author Alex Kinneer
 * @version 10/20/2005
 */
public final class PlatformJVMVerifier implements Verifier {
    /** Special class loader used to force loading of the mutated bytecode
        from memory. */
    private LocalClassLoader loader;
    
    public PlatformJVMVerifier() {
    }
    
    public void loadClass(JavaClass clazz) throws VerifierException {
        loadClass(clazz.getClassName(), clazz.getBytes());
    }
    
    public void loadClass(String className, byte[] classBytes)
            throws VerifierException {
        loader = new LocalClassLoader(className, classBytes);
    }
    
    public VerificationResult verify(String className, String methodName,
            String signature, Pass level) throws VerifierException {
        Class cl = null;
        try {
            cl = loader.loadClass(className);
        }
        catch (ClassNotFoundException e) {
            throw new VerifierException("Could not load class");
        }
        catch (LinkageError e) {
            return new JVMVerifierResult(1, e.getMessage(), Verifier.Pass.TWO);
        }
            
        if ((level == Pass.THREE_A) || (level == Pass.THREE_B)) {
            // This triggers verification of the method
            try {
                @SuppressWarnings("unused")
                Method m = cl.getDeclaredMethod(methodName,
                    Utility.typesToClasses(
                        Type.getArgumentTypes(signature)));
            }
            catch (ClassNotFoundException e) {
                throw new VerifierException("Could not convert " +
                    "parameter types");
            }
            catch (NoSuchMethodException e) {
                throw new VerifierException("Could not load method");
            }
            catch (LinkageError e) {
                return new JVMVerifierResult(1, e.getMessage(), level);
            }
        }
        
        return new JVMVerifierResult(0, "Verified OK", level);
    }
    
    public VerificationResult verify(String className, Pass level)
            throws VerifierException {
        Class cl = null;
        try {
            cl = loader.loadClass(className);
        }
        catch (ClassNotFoundException e) {
            throw new VerifierException("Could not load class");
        }
        catch (LinkageError e) {
            return new JVMVerifierResult(1, e.getMessage(), Verifier.Pass.TWO);
        }
        
        if ((level == Pass.THREE_A) || (level == Pass.THREE_B)) {
            try {
                // This triggers verification of the method
                @SuppressWarnings("unused")
                Method[] ms = cl.getDeclaredMethods();
            }
            catch (LinkageError e) {
                return new JVMVerifierResult(1, e.getMessage(), level);
            }
        }
        
        return new JVMVerifierResult(0, "Verified OK", level);
    }
    
    @SuppressWarnings("unchecked")
    private final class LocalClassLoader extends ClassLoader {
        /** Caches already loaded instances, for class identity coherency. */
        private Map<Object, Object> classCache = new THashMap();
        /** Name of the class to be loaded from memory. */
        private String mutatedClass;
        /** Bytecode of class to be loaded from memory. */
        private byte[] classBytes;
        /** Classpath to be used to search for all other classes. */
        private String[] classPath;
        
        private LocalClassLoader() {
        }
        
        LocalClassLoader(String mutatedClass, byte[] classBytes) {
            super();
            
            this.mutatedClass = mutatedClass;
            this.classBytes = classBytes;
            
            init();
        }
        
        /**
         * Initializes the classpath; we simply inherit the system classpath.
         */
        private void init() {
            List<String> pathList = new ArrayList<String>();
            String sep = File.pathSeparator;
            String userClasspath = System.getProperty("java.class.path", ".");
            StringTokenizer stok = new StringTokenizer(userClasspath, sep);
            while (stok.hasMoreTokens()) {
                pathList.add(stok.nextToken());
            }
            classPath = (String[]) pathList.toArray(
                new String[pathList.size()]);
            
        }
        
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return loadClass(name, true);
        }

        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            Class cl = (Class) classCache.get(name);
        
            if (cl == null) {
                try {
                    cl = findClass(name);
                }
                catch (ClassNotFoundException e) {
                    //System.out.println("finding system class: " + name);
                    cl = findSystemClass(name);
                }
                classCache.put(name, cl);
            }

            if (resolve) resolveClass(cl); 

            return cl; 
        }
        
        protected synchronized Class<?> findClass(String name)
                throws ClassNotFoundException {
            //System.out.println("findClass: " + name);
            if (name.equals(mutatedClass)) {
                try {
                    //System.out.println("loaded from memory");
                    return defineClass(name, classBytes, 0, classBytes.length);
                }
                finally {
                    mutatedClass = null;
                    classBytes = null;
                }
            }
            else {
                for (int i = 0; i < classPath.length; i++) {
                    File classFile = new File(classPath[i] +
                        File.separatorChar +
                        name.replace('.', File.separatorChar) + ".class");
                    
                    if (classFile.exists()) {
                        byte[] classData = loadClassBytes(classFile);
                        if (classData == null)
                            throw new ClassNotFoundException(name);
                    
                        Class cl =
                            defineClass(name, classData, 0, classData.length);
                        if (cl == null) throw new ClassNotFoundException(name);
    
                        return cl;
                    }
                }
                throw new ClassNotFoundException(name);
            }
        }
       
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
    
    private static final class JVMVerifierResult implements VerificationResult {
        private int resultCode;
        private String message;
        private Verifier.Pass level;
    
        private JVMVerifierResult() {
        }
        
        JVMVerifierResult(int resultCode, String message, Verifier.Pass level) {
            this.resultCode = resultCode;
            this.message = message;
            this.level = level;
        }
        
        public int getResult() {
            return resultCode;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Verifier.Pass getLevel() {
            return level;
        }
    }
}
