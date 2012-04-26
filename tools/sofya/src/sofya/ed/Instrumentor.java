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

import sofya.base.Handler;
import sofya.base.exceptions.*;
import sofya.graphs.irg.IRG;
import sofya.graphs.irg.IRG.ClassNode;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.Repository;
import static org.apache.bcel.Constants.INVOKESTATIC;

import org.apache.commons.collections.map.ReferenceMap;

import gnu.trove.THashMap;

/**
 * Abstract base class for all instrumentors, which instrument Java class
 * files for execution by an event dispatcher.
 *
 * @author Alex Kinneer
 * @version 01/19/2007
 *
 * @see sofya.ed.cfInstrumentor
 * @see sofya.ed.semantic.SemanticInstrumentor
 */
@SuppressWarnings("unchecked")
public abstract class Instrumentor implements InstructionConstants {
    /** BCEL data structure representing the class. */
    protected JavaClass javaClass;
    /** BCEL data structure allowing more advanced modifications to class. */
    protected ClassGen javaClassFile;
    /** BCEL data structure representing the class constant pool. */
    protected ConstantPoolGen cpg;
    /** Collection of uninstrumented methods in the class. */
    protected Method[] methods;
    /** Factory object used to generate more complex instructions. */
    protected InstructionFactory iFactory;

    /** Name of the class currently loaded by the instrumentor, as provided
        in the constructor. */
    protected String className = null;
    /** Fully qualified name of the class currently loaded by the
        instrumentor. */
    protected String fullClassName;
    /** Name of method last instrumented. */
    protected String lastInstrumented;

    /** Flag indicating whether the class has a <code>main</code> method. */
    protected boolean classHasMain = false;
    /** Flag indicating whether the class has a static initializer
        (<code>&lt;clinit&gt;</code> method). */
    protected boolean classHasStaticInit = false;
    /** Flag indicating whether the call to <code>SocketProbe.start</code> has
        been inserted. */
    protected boolean starterInserted = false;

    /** Signature of the method currently referenced by instrumentation. */
    protected String mSignature;

    /** Cache of instruction handles that begin exception handlers, used
        by sequence instrumentation to guarantee that exceptional returns
        from calls will properly record the change in method to the trace. */
    protected Map<Object, Object> handlerStarts = new THashMap();
    
    /** Memory-sensitive cache that records the results of querying whether
        a method is <code>native</code>. */
    private static Map<Object, Object> nativeMethodsCache =
        new ReferenceMap(ReferenceMap.SOFT, ReferenceMap.HARD);
    
    /*************************************************************************
     * Default constructor.
     */
    protected Instrumentor() { }

    /*************************************************************************
     * Parses the class, making it ready for instrumentation.
     *
     * <p>Uses BCEL to parse the class file and initialize the internal data
     * structures used for inserting instrumentation.</p>
     *
     * @param className Name of the class to be parsed.
     * @param source Stream from which the class should be parsed. May be
     * <code>null</code>, in which case this method will attempt to load
     * the class from the classpath or the filesystem (in that order).
     *
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    protected void parseClass(String className, InputStream source)
                   throws BadFileFormatException, IOException,
                          ClassFormatError {
        if (source != null) {
            javaClass = new ClassParser(source, className).parse();
        }
        else {
            javaClass = Handler.parseClass(className);
        }

        if (javaClass.isInterface()) {
            throw new InterfaceClassfileException("Cannot instrument an " +
                "interface (" + className + ")");
        }

        Repository.addClass(javaClass);
        this.className = className;
        this.fullClassName = javaClass.getClassName();
        javaClassFile = new ClassGen(javaClass);
        cpg = javaClassFile.getConstantPool();
        methods = javaClassFile.getMethods();
        iFactory = new InstructionFactory(cpg);
        starterInserted = false;
        lastInstrumented = null;
    }

    /*************************************************************************
     * Initializes the instrumentor, making it ready to instrument methods in
     * the class.
     *
     * Adds the instrumentation method references to the class constant
     * pool. Determines if the class has a static initializer and/or
     * <code>main</code> method.
     */
    protected void init() throws IOException {
        // Make sure '.class' extension is not attached to class name
        if (className.endsWith(".class")) {
            className =
                className.substring(0, className.lastIndexOf(".class"));
        }

        classHasMain = classHasStaticInit = false;
        for (int i = 0; i < methods.length; i++) {
            if (!classHasMain) {
                classHasMain = methods[i].getName().equals("main");
            }
            if (!classHasStaticInit) {
                classHasStaticInit =
                    methods[i].getName().equals("<clinit>");
            }
            if (classHasMain && classHasStaticInit) break;
        }
    }

    /*************************************************************************
     * Loads a new class into the instrumentor.
     *
     * <p><b>Note:</b> If {@link Instrumentor#writeClass} is not called before
     * this method, any instrumentation performed on the last loaded class
     * will be lost.</p>
     *
     * @param className Name of the class to be loaded.
     *
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to load the
     * class.
     */
    public void loadClass(String className)
                throws BadFileFormatException, FileNotFoundException,
                       IOException, ClassFormatError, Exception {
        parseClass(className, null);
        init();
    }

    /*************************************************************************
     * Loads a new class into the instrumentor, from a given stream.
     *
     * <p><b>Note:</b> If {@link Instrumentor#writeClass} is not called before
     * this method, any instrumentation performed on the last loaded class
     * will be lost.</p>
     *
     * @param className Name of the class to be loaded.
     * @param source Stream from which the class should be read.
     *
     * @throws BadFileFormatException If the specified class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If BCEL cannot parse the class.
     * @throws Exception If any other error is raised attempting to
     * load the class.
     */
    public void loadClass(String className, InputStream source)
                throws BadFileFormatException, IOException,
                       ClassFormatError, Exception {
        if (source == null) {
            throw new NullPointerException();
        }
        parseClass(className, source);
        init();
    }

    /*************************************************************************
     * Reloads the current class from file, destroying any instrumentation
     * that has been performed.
     *
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public void reloadClass() throws IOException, ClassFormatError, Exception {
        loadClass(this.className);
    }

    /*************************************************************************
     * Reloads a method, destroying any instrumentation previously applied to
     * it.
     *
     * @param methodName Name of the method to be reloaded.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method. Together
     * with the return type, these constitute the signature which will be used
     * to identify a match.
     *
     * @throws MethodNotFoundException If no method matching the specified name
     * and signature can be found.
     */
    public void reloadMethod(String methodName, Type returnType,
                             Type[] argumentTypes)
                             throws MethodNotFoundException {
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodName) &&
                Type.getMethodSignature(returnType, argumentTypes)
                    .equals(methods[i].getSignature()))
            {
                javaClassFile.setMethodAt(methods[i], i);
                return;
            }
        }
        throw new MethodNotFoundException(methodName);
    }

    /*************************************************************************
     * Gets the name of the class which is currently loaded in the
     * instrumentor.
     *
     * @return The name of the class which is being handled by this
     * instrumentor.
     */
    public String getClassName() {
        return className;
    }

    /*************************************************************************
     * Gets the fully qualified name of the class (includes package name)
     * which is currently loaded in the instrumentor.
     *
     * @return The fully qualified name of the class which is being handled
     * by this instrumentor.
     */
    public String getQualifiedName() {
        return fullClassName;
    }

    /*************************************************************************
     * Gets the number of methods in the class.
     *
     * @return The number of methods found in the class.
     */
    public int getMethodCount() {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }
        return methods.length;
    }

    /*************************************************************************
     * Gets the name of a method in the class method array.
     *
     * @param methodIndex Index of the method whose name is to be retrieved.
     *
     * @return The name of the method at the given location in the method
     * array.
     */
    public String getMethodName(int methodIndex) {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }
        if (methodIndex < 0 || methodIndex >= methods.length) {
            throw new IllegalArgumentException("Method index out of range");
        }
        return methods[methodIndex].getName();
    }

    /*************************************************************************
     * Gets the signature of a method in the class method array, as a string.
     *
     * @param methodIndex Index of the method whose name is to be retrieved.
     *
     * @return The signature of the method at the given location in the method
     * array.
     */
    public String getMethodSignature(int methodIndex) {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }
        if (methodIndex < 0 || methodIndex >= methods.length) {
            throw new IllegalArgumentException("Method index out of range");
        }
        return methods[methodIndex].getSignature();
    }

    /*************************************************************************
     * Gets the name of the last method instrumented.
     *
     * <p>This may be useful to determine when a method is actually
     * instrumented. Methods declared as <code>native</code>,
     * <code>abstract</code>, or which have no method body will not cause
     * the value returned by this method to be updated.</p>
     *
     * @return The name of the last method successfully instrumented, or
     * <code>null</code> if no method has been instrumented yet.
     */
    public String getLastInstrumented() {
        return lastInstrumented;
    }

    /*************************************************************************
     * Reports whether the class has a main method.
     *
     * @return <code>true</code> if the class has a <code>main</code> method,
     * <code>false</code> otherwise.
     */
    public boolean hasMain() {
        return classHasMain;
    }

    /*************************************************************************
     * Reports whether the class has a static initializer.
     *
     * @return <code>true</code> if the class has a
     * <code>&lt;clinit&gt;</code> method, <code>false</code> otherwise.
     */
    public boolean hasStaticInit() {
        return classHasStaticInit;
    }

    /*************************************************************************
     * Instruments every method in the class using the current instrumentation
     * settings. This will overwrite any instrumentation previously applied to
     * the methods.
     *
     * <p>This method will automatically determine the correct method into
     * which to insert the call to <code>SocketProbe.start</code>.</p>
     */
    public void instrumentAll() {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        starterInserted = false;

        // Iterates through each method to perform instrumentation.
        for (int i = 0; i < methods.length; i++) {
            javaClassFile.setMethodAt(instrument(methods[i], i, false), i);
        }
    }

    /*************************************************************************
     * Instruments the method with the given name and signature using the
     * current instrumentation settings. This will overwrite any
     * instrumentation previously applied to the method.
     *
     * @param methodName Name of the method to be reloaded.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method. Together
     * with the return type, these constitute the signature which will be used
     * to identify a match.
     * @param insertStarter Flag specifying whether call to
     * <code>SocketProbe.start</code> should be unconditionally inserted at
     * the beginning of this method. The <code>SocketProbe.start</code> method
     * is guarded against multiple invocations, such that only the first
     * occurrence during execution has any effect. Nonetheless, setting this
     * flag should be done with discretion and is generally discouraged. Note
     * also that the {@link Instrumentor#instrumentAll} method will cause the
     * call to be automatically inserted in the appropriate location.
     *
     * @throws MethodNotFoundException If no method matching the specified
     * name and signature can be found.
     */
    public void instrument(String methodName, Type returnType,
                           Type[] argumentTypes, boolean insertStarter)
                           throws MethodNotFoundException {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        String jniSignature =
            Type.getMethodSignature(returnType, argumentTypes);
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodName) &&
                    jniSignature.equals(methods[i].getSignature())) {
                javaClassFile.setMethodAt(instrument(methods[i], i,
                    insertStarter), i);
                return;
            }
        }
        throw new MethodNotFoundException(methodName);
    }

    /**
     * Instruments a method.
     *
     * <p>Performs the actual instrumentation and update of the method in the
     * class.</p>
     *
     * @param m Method to be instrumented.
     * @param methodIndex Index to the method in the class method array.
     * @param insertStarter If <code>true</code>, force the instrumentor to
     * insert a call to <code>SocketProbe.start</code> at the beginning of
     * this method, otherwise it will be determined automatically whether it
     * should be inserted.
     *
     * @return The instrumented method. If the method is <code>native</code>,
     * <code>abstract</code>, or has no body, it is returned unchanged.
     */
    protected abstract Method instrument(Method m, int methodIndex,
                                         boolean insertStarter);

    /*************************************************************************
     * Gets the instrumented class.
     *
     * @return The BCEL object representing the instrumented class.
     */
    public JavaClass generateClass() {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        // Commit the updated constant pool
        javaClassFile.setConstantPool(cpg);
        return javaClassFile.getJavaClass();
    }

    /*************************************************************************
     * Writes the binary instrumented class data to a stream.
     *
     * <p>The class data written constitutes a valid Java class file. In
     * other words, if the output stream is a file, the resulting file will
     * be a valid class file containing any instrumentation that was
     * applied.</p>
     *
     * @param dest Output stream to which class is to be written.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public void writeClass(OutputStream dest) throws IOException {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        // Commit the updated constant pool
        javaClassFile.setConstantPool(cpg);
        // Write the classfile to the given stream
        javaClassFile.getJavaClass().dump(dest);
    }

    /*************************************************************************
     * Reads the exception handlers associated with the method and caches
     * the starting instruction handle for each handler.
     *
     * <p>During the sequence instrumentation process, this cache is
     * consulted to determine whether a block is the lead block of an
     * exception handler. If it is, a call to record the possible change in
     * method (exceptional return) to the trace is inserted.</p>
     *
     * @param excHandlers Table of exception handlers attached to the method.
     */
    protected void cacheHandlerStarts(CodeExceptionGen[] excHandlers) {
        handlerStarts.clear();
        int len = excHandlers.length;
        for (int i = 0; i < len; i++) {
            handlerStarts.put(excHandlers[i].getHandlerPC(),
                excHandlers[i]);
        }
    }
    
    /**
     * Reports whether a static method is implemented in native code. The
     * answer will not be reliable in the presence of runtime reflective
     * class loading.
     *
     * <p><em>The result of this method cannot be trusted if called on a
     * non-static method!</em> No static guarantee can be made that a virtual
     * method will always call a native implementation, since it may
     * depend on the receiver type of the object.</p>
     *
     * @param call Invoke instruction for the call that is to be checked
     * for native implementation.
     * @param cpg Constant pool for the class in which the call resides.
     *
     * @return <code>true</code> if the static method binds to a native
     * implementation.
     *
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    public static final boolean isNative(INVOKESTATIC call,
            ConstantPoolGen cpg) throws ClassNotFoundException {
        Boolean cached = (Boolean) nativeMethodsCache.get(call);
        if (cached != null) {
            return cached.booleanValue();
        }

        boolean isNative = isNative(call.getReferenceType(cpg).toString(),
            call.getMethodName(cpg) + call.getSignature(cpg));
        nativeMethodsCache.put(call.copy(), Boolean.valueOf(isNative));

        return isNative;
    }

    /**
     * Recursive implementation of the static native method query. Searches
     * through the superclasses if necessary (pretty sure this isn't
     * technically legal in bytecode, but I think many compiler and
     * JVM implementations may allow a static method reference on a class
     * that does not implement the method itself, but rather inherits
     * from a superclass).
     * 
     * @param className Name of the class to start the search from.
     * @param nameAndSig JNI name and signature of the method to check
     * for native implementation.
     * 
     * @return <code>true</code> if the method binds to a native
     * implementation.
     * 
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    private static final boolean isNative(String className, String nameAndSig)
            throws ClassNotFoundException {
        JavaClass clazz = Repository.lookupClass(className);
        if (clazz == null) {
            throw new ClassNotFoundException(className);
        }
        else {
            Method[] methods = clazz.getMethods();
            for (int i = 0; i < methods.length; i++) {
                String curNameAndSig =
                    methods[i].getName() + methods[i].getSignature();
                if (nameAndSig.equals(curNameAndSig)) {
                    return methods[i].isNative();
                }
            }

            return isNative(clazz.getSuperclassName(), nameAndSig);
        }
    }
    
    /**
     * Reports whether a method call might possibly bind to a native
     * implementation.
     * 
     * <p>This method judges whether the call might possibly invoke
     * a native method by analyzing an interclass relation graph to
     * determine a conservative estimate of the possible bindings
     * for the call. If there are any classes in the system not
     * included in the IRG, or if the system makes use of reflective
     * class loading or dynamic class generation, the result of this
     * message is not guaranteed to be correct.</p>
     *
     * @param call Invoke instruction for the call that is to be checked.
     * @param cpg Constant pool for the class in which the call resides.
     * @param irg Interclass relation graph for the system, constituting
     * the class relation data that will be used to determine possible
     * call bindings.
     *
     * @return <code>true</code> if the call can possibly bind to a
     * native implementation of the method.
     *
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    public static final boolean isPossiblyNative(InvokeInstruction call,
            ConstantPoolGen cpg, IRG irg) throws ClassNotFoundException {
        if (call.getOpcode() == INVOKESTATIC) {
            return isNative((INVOKESTATIC) call, cpg);
        }

        Boolean cached = (Boolean) nativeMethodsCache.get(call);
        if (cached != null) {
            return cached.booleanValue();
        }
        
        String className = call.getReferenceType(cpg).toString();
        String nameAndSig = call.getMethodName(cpg) + call.getSignature(cpg);
        
        boolean result = isPossiblyNative(className, nameAndSig, irg);
        
        nativeMethodsCache.put(call.copy(), Boolean.valueOf(result));
       
        return result;
    }
    
    /**
     * Recursive helper method to support the virtual native method query.
     * 
     * <p>For any concrete class, we check whether it directly implements
     * the method. If it does not, we search until we find the nearest
     * superclass implementation. We also always query whether any
     * subclasses define an implementation, as we don't know for certain
     * what the receiver type will be on the call.</p>
     * 
     * <p>If the call is to an interface method, all known implementing
     * classes are searched. Concrete classes are handled as described
     * above, additional interfaces are queried recursively.</p>
     * 
     * @param className Name of the class at which to begin the search.
     * @param nameAndSig JNI name and signature of the method for which
     * to check for native implementations.
     * @param irg Interclass relation graph used to determine the
     * interface implementation and subclass relationships between
     * the known set of classes comprising the system.
     * 
     * @return <code>true</code> if any reachable implementation of the
     * method binds to a native implementation.
     * 
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    private static final boolean isPossiblyNative(String className,
            String nameAndSig, IRG irg) throws ClassNotFoundException {
        boolean result = false;
        
        JavaClass clazz = Repository.lookupClass(className);
        if (clazz == null) {
            throw new ClassNotFoundException(className);
        }
        else if (clazz.isInterface()) {
            checkForNativeImplementors(className, nameAndSig, irg);
        }
        else {  // Not an interface
            Method targetMethod = null;
            Method[] methods = clazz.getMethods();
            for (int i = 0; i < methods.length; i++) {
                String curNameAndSig =
                    methods[i].getName() + methods[i].getSignature();
                if (nameAndSig.equals(curNameAndSig)) {
                    targetMethod = methods[i];
                    break;
                }
            }

            if (targetMethod == null) {
                // The method isn't directly declared in the referenced
                // class, so it must be declared in a supertype
                result = isNative(clazz.getSuperclassName(), nameAndSig);
            }
            else {
                result = targetMethod.isNative();
            }
            
            // We can't know for certain what the receiver type is;
            // if it is a subclass, the call might bind to an override,
            // so we check the subclasses for any that declare
            // native overrides
            result |= checkForNativeOverrides(className, nameAndSig, irg);
        }
        
        return result;
    }
    
    /**
     * Searches subclasses for possible native implementations of a method.
     * 
     * @param className Name of the class at which to begin the search.
     * @param nameAndSig JNI name and signature of the method for which
     * to check for native implementations.
     * @param irg Interclass relation graph used to determine the
     * interface implementation and subclass relationships between
     * the known set of classes comprising the system.
     * 
     * @return <code>true</code> if any implementation of the method in
     * any subclass binds to a native implementation.
     * 
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    private static final boolean checkForNativeOverrides(String className,
            String nameAndSig, IRG irg) throws ClassNotFoundException {
        boolean result = false;
        
        ClassNode clData = irg.getClassRelationData(className);
        Iterator<String> subclasses = clData.subclassIterator();
        while (subclasses.hasNext()) {
            String subclassName = subclasses.next();
            
            JavaClass clazz = Repository.lookupClass(subclassName);
            if (clazz == null) {
                throw new ClassNotFoundException(subclassName);
            }
            
            Method[] methods = clazz.getMethods();
            for (int i = 0; i < methods.length; i++) {
                String curNameAndSig =
                    methods[i].getName() + methods[i].getSignature();
                if (nameAndSig.equals(curNameAndSig)) {
                    result |= methods[i].isNative();
                    break;
                }
            }
            
            result |= checkForNativeOverrides(subclassName, nameAndSig, irg);
        }
        
        return result;
    }
    
    /**
     * Searches classes implementing an interface for possible native
     * implementations of a method.
     * 
     * @param interfaceName Name of the interface for which to perform
     * the search.
     * @param nameAndSig JNI name and signature of the method for which
     * to check for native implementations.
     * @param irg Interclass relation graph used to determine the
     * interface implementation and subclass relationships between
     * the known set of classes comprising the system.
     * 
     * @return <code>true</code> if any class implementing the interface,
     * directly or indirectly, declares a native implementation of the
     * method.
     * 
     * @throws ClassNotFoundException If a class needed to determine the
     * result cannot be found on the classpath.
     */
    private static final boolean checkForNativeImplementors(
            String interfaceName, String nameAndSig, IRG irg)
            throws ClassNotFoundException {
        boolean result = false;
        
        ClassNode clData = irg.getClassRelationData(interfaceName);
        Iterator<String> implementors = clData.implementorIterator();
        while (implementors.hasNext()) {
            String implementorName = implementors.next();
            result |= isPossiblyNative(implementorName, nameAndSig, irg);
        }
        
        return result;
    }
}

/*****************************************************************************/
