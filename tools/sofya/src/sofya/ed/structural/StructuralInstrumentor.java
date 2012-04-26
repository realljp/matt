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

import java.io.FileNotFoundException;
import java.io.IOException;

import sofya.base.SConstants.BlockType;
import sofya.base.SConstants.TraceObjectType;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.ed.Instrumentor;
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFHandler;
import static sofya.base.SConstants.*;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

/**
 * A structural instrumentor is responsible for inserting some type of
 * instrumentation to enable observation of events indicating the
 * execution of structural entities in a program (e.g. basic blocks,
 * branches, etc.).
 *
 * <p><i>Special note regarding instrumentation of Sofya as a subject</i>: If
 * you change the mode of instrumentation (compatible, normal optimized,
 * etc...) of the instrumentation applied to Sofya as a subject, make
 * sure to delete the 'bootstrap.jar' file in the <code>inst</code>
 * directory of the subject Sofya to force the event dispatcher to rebuild it
 * using the newly instrumented SocketProbe. Otherwise the event dispatcher may
 * refuse to run if the old mode of instrumentation that was applied to
 * SocketProbe in the jar file is not compatible with a form that
 * it accepts.</p>
 * 
 * @author Alex Kinneer
 * @version 02/28/2007
 */
public abstract class StructuralInstrumentor extends Instrumentor {
    /** Socket port to be used for instrumentation. */
    protected int port = -1;
    /** Flag indicating whether the port will be selected automatically. */
    protected boolean useDefaultPort = true;
    /** Bit vector representing types of blocks that will be instrumented. */
    protected int typeFlags = 0x00000000;
    /** Integer code indicating type of instrumentation to be inserted. */
    protected int instMode = INST_OPT_NORMAL;
    /** Flag indicating that the instrumentation is targeted for a
        JUnit event dispatcher. */
    protected boolean targetJUnit = false;
    /** Handler for class method CFGs. */
    protected CFHandler classGraphs = new CFHandler();
    /** Tag associated with the class database files. */
    protected String tag = null;
    /** CFG for the method currently being instrumented. */
    protected CFG methodCFG = null;
    
    /** Name of the class upon which instrumentation methods are to be
        invoked. */
    protected String instClassRef;

    /** Reference to the instruction to load the reference to the
        probe interface. */
    protected GETSTATIC instFieldLoad;
    
    /** Reference to the <code>SocketProbe.start</code> method invocation
        instruction. */
    protected InvokeInstruction startMethodCall;
    /** Reference to the method invocation instruction to record a probe,
        if any. */ 
    protected InvokeInstruction traceMethodCall;
    /** Reference to the method invocation instruction to be called upon
        entry to a method, if any. */
    protected InvokeInstruction methodEntryCall;
    
    /** Fully qualified name of the class holding the sequence trace
        data array. */
    protected String seqArrayRef;
    
    /** Flag indicating whether the class is an event dispatcher. */
    protected boolean classIsDispatcher = false;
    /** Flag indicating whether the class is a probe class. */
    protected boolean isProbeClass = false;
    /** Flag indicating whether the class implements the probe interface. */
    protected boolean implementsProbeIfc = false;
    
    /** Index to a local variable in the instrumented method used to hold the
        hit-object array (used by optimized normal and JUnit
        instrumentation). */
    protected int arrayVarref;
    /** Flag used to control whether the summary exit node will be marked on an
        exceptional exit. If the exceptional exit is associated with a
        precisely known control flow path, this flag is set, which signals the
        summary exit node handler not to mark the summary node. */
    protected int excExitBooleanVar;
    
    /** Short name of the class holding the sequence trace data array. */
    protected static final String SEQUENCE_ARRAY_HOLDER = "SequenceProbe";
    
    protected StructuralInstrumentor() {
    }

    /*************************************************************************
     * Creates am instrumentor configured by a list of command line
     * parameters.
     *
     * @param argv Set of command line parameters, such as would be obtained
     * from {@link sofya.ed.cfInstrumentor}.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public StructuralInstrumentor(String[] argv)
            throws IllegalArgumentException, IOException,
                   ClassFormatError, Exception {
        parseCommandLine(argv);
        if (className != null) {
            try {
                loadClass(className);
            }
            catch (BadFileFormatException e) {
                System.err.println(e.getMessage());
            }
            catch (FileNotFoundException e) {
                System.err.println("Cannot find class " + className);
            }
        }
    }


    /*************************************************************************
     * Creates an instrumentor for the specified class with the given object
     * types activated for instrumentation and using the default port.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit mask representing the types of objects to be
     * instrumented.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public StructuralInstrumentor(String className, int typeFlags)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                                               "specified");
        }
        
        this.typeFlags = typeFlags;
        this.useDefaultPort = true;
        parseClass(className, null);
        init();
    }

    /*************************************************************************
     * Creates an instrumentor for the specified class with the given object
     * types activated for instrumentation and using the given port.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit mask representing the types of objects to be
     * instrumented.
     * @param port Port to which instrumentation should be set. The valid
     * range is 1024 to 65535.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public StructuralInstrumentor(String className, int typeFlags, int port)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                "specified!");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Port " + port + " out of " +
                "range (valid range is 1024-65535)");
        }
        
        this.typeFlags = typeFlags;
        this.port = port;
        this.useDefaultPort = false;
        
        parseClass(className, null);
        init();
    }


    /*************************************************************************
     * Creates an instrumentor for the specified class with the given object
     * types activated for instrumentation, using the given port, and
     * activating the specified mode of instrumentation.
     *
     * @param className Name of the class to be instrumented.
     * @param typeFlags Bit mask representing the types of objects to be
     * instrumented.
     * @param port Port to which instrumentation should be set. The valid
     * range is 1024 to 65535.
     * @param instMode Integer flag indicating the form of instrumentation to
     * be inserted. Acceptable values are the following:
     * <ul>
     * <li>{@link sofya.base.SConstants#INST_COMPATIBLE}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_NORMAL}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_SEQUENCE}</li>
     * </ul>
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     * @throws BadFileFormatException If the class is an interface.
     * @throws IOException If there is an error reading the class file.
     * @throws ClassFormatError If the class file cannot be parsed.
     * @throws Exception If any other error is raised attempting to parse the
     * class.
     */
    public StructuralInstrumentor(String className, int typeFlags,
                                  int port, int instMode)
           throws BadFileFormatException, IllegalArgumentException,
                  IOException, ClassFormatError, Exception {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid block type " +
                "specified");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Port " + port + " out of " +
                "range (valid range is 1024-65535)");
        }
        if ((instMode < 1) || (instMode > 5)) {
            throw new IllegalArgumentException("Instrumentation mode is " +
                "invalid");
        }
        
        this.typeFlags = typeFlags;
        this.instMode = instMode;
        this.port = port;
        this.useDefaultPort = false;
        
        parseClass(className, null);
        init();
    }

    /*************************************************************************
     * Parses command line parameters that control aspects of the internal
     * state of the instrumentor.
     *
     * <p><b>Note:</b> Some parameters may need to be processed prior to
     * invocation of this method, such as is done by
     * {@link sofya.ed.cfInstrumentor}, to implement behaviors that should
     * not be reflected in the internal state of the instrumentor itself.</p>
     *
     * @param argv Command line parameters to be parsed.
     *
     * @throws IllegalArgumentException If required parameters are missing,
     * invalid parameters are encountered, or data required for optional
     * parameters is missing.
     */
    protected void parseCommandLine(String[] argv)
                   throws IllegalArgumentException {
        boolean traceTypeSet = false;
        boolean portSet = false;

        int i = 0;
        while (i < argv.length) {
            if (argv[i].startsWith("-")) {
                if (argv[i].equals("-port")) {
                    if (i + 1 < argv.length) {
                        try {
                            this.port = Integer.parseInt(argv[i + 1]);
                            useDefaultPort = false;
                        }
                        catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Port " +
                                "argument must be numeric value");
                        }
                        i += 1;
                        portSet = true;
                    }
                    else {
                        throw new IllegalArgumentException("Port number not " +
                            "supplied");
                    }
                }
                else if (argv[i].equals("-t")) {
                    if (i + 1 < argv.length) {
                        String param1 = argv[i + 1], param2 = null;
                        int delimPos = -1;
                        if ((delimPos = param1.indexOf(',')) != -1) {
                            param2 = param1.substring(delimPos + 1,
                                                      param1.length());
                            param1 = param1.substring(0, delimPos);
                        }

                        if (param1.equals("comp")) {
                            this.instMode = INST_COMPATIBLE;
                        }
                        else if (param1.equals("norm")) {
                            this.instMode = INST_OPT_NORMAL;
                        }
                        else if (param1.equals("junit")) {
                            this.instMode = INST_OPT_NORMAL;
                            this.targetJUnit = true;
                        }
                        else if (param1.equals("seq")) {
                            this.instMode = INST_OPT_SEQUENCE;
                        }
                        else if (param1.equals("hash")) {
                            this.instMode = INST_TRACE_HASHING;
                        }
                        else {
                            throw new IllegalArgumentException(
                                "Instrumentation type '" + argv[i + 1] +
                                "' not recognized");
                        }

                        if ((param2 != null) && param2.equals("junit")) {
                            this.targetJUnit = true;
                        }
                        i += 1;
                    }
                    else {
                        throw new IllegalArgumentException("Instrumentation " +
                            "type not supplied");
                    }
                }
                else if (argv[i].equals("-tag")) {
                    if (i + 1 < argv.length) {
                        tag = argv[i + 1];
                    }
                    else {
                        throw new IllegalArgumentException("Tag value not " +
                            "specified");
                    }
                    i += 1;
                }
                /*else if (argv[i].equals("-o")) {
                    if (i + 1 < argv.length) {
                        outputFile = argv[i + 1];
                        i += 1;
                    }
                    else {
                        throw new IllegalArgumentException("Output file not " +
                            "specified");
                    }
                }*/
                else if ((argv[i].length() <= 7) && (argv[i].length() > 1)
                            && Character.isUpperCase(argv[i].charAt(1))) {
                    traceTypeSet = parseTypeCodes(argv[i]);
                }
                else {
                    throw new IllegalArgumentException("Unrecognized " +
                        "parameter");
                }
            }
            else {
                break;
            }
            i += 1;
        }

        if (!traceTypeSet && (getObjectType() == TraceObjectType.BASIC_BLOCK)) {
            throw new IllegalArgumentException("No object type(s) specified");
        }

        // Check that the user didn't specify a negative port number (negative
        // values are used as an internal signal to determine the port
        // automatically).
        if (portSet && (port < 0)) {
            throw new IllegalArgumentException("Port " + port + " out of " +
                "range (valid range is 1024-65535)");
        }

        if (i < argv.length) {
            this.className = argv[i];
        }
    }

    /*************************************************************************
     * Parses the command-line parameter that specifies the types of objects
     * to be instrumented (eg &apos;-BEXC&apos;, etc.).
     *
     * @param typeCodes Parameter read from command-line, including the
     * leading dash.
     *
     * @return <code>true</code> object types were read from the parameter,
     * <code>false</code> if no types were given.
     *
     * @throws IllegalArgumentException If an unrecognized object type
     * character is encountered.
     */
    protected abstract boolean parseTypeCodes(String typeCodes)
                               throws IllegalArgumentException;


    /*************************************************************************
     * Gets the bit mask controlling the object types that are currently set
     * to be instrumented by this instrumentor.
     *
     * @return Bit mask controlling what types of objects are instrumented.
     */
    public int getTypeFlags() {
        return typeFlags;
    }

    /*************************************************************************
     * Sets the bit mask controlling what object types are to be
     * instrumented by this instrumentor.
     *
     * @param typeFlags Bit mask representing the types of objects to be
     * instrumented.
     *
     * @throws IllegalArgumentException If the bit mask doesn't have a set
     * bit which corresponds to a valid object type.
     */
    public abstract void setTypeFlags(int typeFlags);

    /*************************************************************************
     * Gets the structural entity that is instrumented by this instrumentor.
     * This allows the abstract superclass to implement some general
     * functions, simply using dynamic binding to the actual current object
     * to determine proper actions and values when necessary.
     *
     * @return The structural entity (e.g. block, edge) that this instrumentor
     * knows how to instrument.
     */
    protected abstract TraceObjectType getObjectType();

    /*************************************************************************
     * Gets the name of the class referenced by probes inserted by this
     * instrumentor.
     *
     * @return The name of the probe class used by this instrumentor.
     */
    protected abstract String getProbeClassName();

    /**
     * <p>Analyzes the class and sets various flags used to control
     * instrumentation. Determines if the subject is a filter class
     * or {@link sofya.ed.structural.SocketProbe}. Determines the default
     * port or checks that the port number is in range if one is
     * specified.</p>
     */
    @Override
    protected void init() throws IOException {
        super.init();
        
        // No special handling for event dispatchers or SocketProbe if
        // instrumenting for JUnit (there will be no infinitely recursive
        // class reference issues)
        
        boolean isLegacyProbeClass;
        
        checkForDispatcher:
        if (!targetJUnit) {
            ObjectType loadedType = new ObjectType(fullClassName);
            
            isLegacyProbeClass = "SocketProbe".equals(fullClassName);
            isProbeClass = ProbeMetadata.ALL_CLASSES.contains(fullClassName); 
            
            try {
                implementsProbeIfc = loadedType.isCastableTo(
                    new ObjectType(ProbeMetadata.PACKAGE_PREFIX +
                        ProbeMetadata.INTERFACE_NAME));
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }

            // Perform various tests to determine if the class is an event
            // dispatcher class

            // Check for known classes that are built on the event dispatchers
            if ("sofya.ed.BBTracer".equals(className)
                    || "sofya.ed.BBSequenceTracer".equals(className)
                    || "sofya.ed.BranchTracer".equals(className)
                    || "sofya.ed.BranchSequenceTracer".equals(className)) {
                classIsDispatcher = true;
                break checkForDispatcher;
            }

            ObjectType dispatcherClass;
            if (fullClassName.startsWith("galileo")) {
                isLegacyProbeClass = fullClassName.endsWith("SocketProbe");
                
                try {
                    Repository.lookupClass("galileo.inst.AbstractFilter");

                    // Later versions of Galileo had AbstractFilter as the
                    // root of the hierarchy, as with Sofya.
                    dispatcherClass =
                        new ObjectType("galileo.inst.AbstractFilter");
                }
                catch (ClassNotFoundException e) {
                    // Earlier versions of Galileo had Filter as the root
                    // of the hierarchy.
                    dispatcherClass = new ObjectType("galileo.inst.Filter");
                }
            }
            else {
                // Initial versions of Sofya had AbstractFilter at the
                // base of the event dispatcher class hierarchy
                try {
                    Repository.lookupClass("sofya.inst.AbstractFilter");

                    dispatcherClass =
                            new ObjectType("sofya.inst.AbstractFilter");
                }
                catch (ClassNotFoundException e) {
                    // AbstractFilter would have to be on the classpath to
                    // run the subject if it was an older version of Sofya,
                    // so if it is not, we know the subject isn't old Sofya
                    classIsDispatcher = false;
                    break checkForDispatcher;
                }
            }

            try {
                classIsDispatcher = loadedType.subclassOf(dispatcherClass);
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
        }
        else {
            isLegacyProbeClass = false;
        }
        
        String probeClassName = getProbeClassName();
        instClassRef = (isProbeClass)
            ? probeClassName + "Alt"
            : probeClassName;
        
        isProbeClass |= isLegacyProbeClass;
        
        if (!targetJUnit) {
            if (instMode != INST_TRACE_HASHING) {
                startMethodCall = iFactory.createInvoke(instClassRef, "start",
                    Type.VOID, new Type[]{ Type.INT, Type.INT, Type.BOOLEAN,
                    Type.BOOLEAN, Type.INT }, Constants.INVOKEINTERFACE);
            }
            else {
                startMethodCall = iFactory.createInvoke(instClassRef,
                     "initialize", Type.VOID, new Type[0],
                     Constants.INVOKESTATIC);
            }
        }
        else {
            startMethodCall = null;
        }

        instFieldLoad = iFactory.createGetStatic(instClassRef, "probe",
                new ObjectType(instClassRef));
        
        seqArrayRef = instClassRef + "$" + SEQUENCE_ARRAY_HOLDER;
                         
        switch (instMode) {
        case INST_COMPATIBLE:
            methodEntryCall = iFactory.createInvoke(instClassRef,
                "writeObjectCount", Type.VOID, new Type[] {
                Type.STRING, Type.INT }, Constants.INVOKEINTERFACE);
            traceMethodCall = iFactory.createInvoke(instClassRef,
                "writeTraceMessage", Type.VOID, new Type[] {
                Type.INT, Type.STRING }, Constants.INVOKEINTERFACE);
            break;
        case INST_OPT_NORMAL:
            methodEntryCall = iFactory.createInvoke(instClassRef,
                "getObjectArray", new ArrayType(Type.BYTE, 1),
                new Type[] { Type.STRING, Type.INT },
                Constants.INVOKEINTERFACE);
            traceMethodCall = null;
            break;
        case INST_OPT_SEQUENCE:
            methodEntryCall = iFactory.createInvoke(instClassRef,
                "markMethodInSequence", Type.VOID, new Type[] {
                Type.STRING, Type.INT }, Constants.INVOKEINTERFACE);
            traceMethodCall = iFactory.createInvoke(instClassRef,
                "writeSequenceData", Type.VOID, new Type[0],
                Constants.INVOKEINTERFACE);
            break;
        case INST_TRACE_HASHING:
            // No method entry call for this type
            traceMethodCall = iFactory.createInvoke(instClassRef,
                "blockEvent", Type.VOID, new Type[] { Type.INT },
                Constants.INVOKESTATIC);
            break;
        }

        if (useDefaultPort) {
            if (classIsDispatcher || isProbeClass) {
                this.port = DEFAULT_PORT + 1;
            }
            else {
                this.port = DEFAULT_PORT;
            }
        }
        else {
            if (this.port < 1024 || this.port > 65535) {
                throw new IllegalArgumentException("Port " + port + " out " +
                    "of range (valid range is 1024-65535)");
            }
        }
        
        try {
            classGraphs.readCFFile(fullClassName + ".java", tag);
        }
        catch (IOException e) {
            throw new FileNotFoundException("Could not load required " +
                "database file: " + e.getMessage());
        }
    }
    
    /*************************************************************************
     * Inserts call to <code>SocketProbe.start</code> unconditionally at
     * the beginning of the specified method.
     *
     * <p>The <code>SocketProbe.start</code> method is guarded against
     * multiple invocations, such that only the first occurrence during
     * execution has any effect. Nonetheless, this method should be used with
     * discretion and such use is generally discouraged. Note also that the
     * {@link Instrumentor#instrumentAll} method will cause the call to be
     * automatically inserted in the appropriate location.
     *
     * @param methodName Name of the method into which to insert call.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method. Together
     * with the return type, these constitute the signature which will be used
     * to identify a match.
     *
     * @throws MethodNotFoundException If no method matching the specified
     * name and signature can be found.
     */
    public void insertStarter(String methodName, Type returnType,
                              Type[] argumentTypes)
            throws MethodNotFoundException {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        Method[] instMethods = javaClassFile.getMethods();
        for (int i = 0; i < instMethods.length; i++) {
            if (instMethods[i].getName().equals(methodName) &&
                Type.getMethodSignature(returnType, argumentTypes)
                    .equals(instMethods[i].getSignature()))
            {
                MethodGen mg =
                    new MethodGen(instMethods[i], fullClassName, cpg);
                InstructionList il = mg.getInstructionList();
                insertProbeStartCall(il);
                mg.setInstructionList(il);
                mg.setMaxStack();
                javaClassFile.setMethodAt(mg.getMethod(), i);
                il.dispose();
                return;
            }
        }
        throw new MethodNotFoundException(methodName);
    }
    
    /*************************************************************************
     * Inserts call to <code>SocketProbe.finish</code> unconditionally at
     * the exit points of the specified method.
     *
     * <p><b>Warning:</b> <i>Use this method with extreme caution.</i> The
     * <code>SocketProbe.finish</code> method causes the SocketProbe to
     * transmit all remaining trace data and close its socket(s). Thus it
     * should be called only once, and after all possible instrumentation
     * has been executed, or errors and data loss will occur. Note also
     * that the {@link Instrumentor#instrumentAll} method will cause the
     * call to be automatically inserted in the appropriate location.</p>
     *
     * @param methodName Name of the method into which to insert call.
     * @param returnType Return type of the method.
     * @param argumentTypes The types of the arguments to the method. Together
     * with the return type, these constitute the signature which will be used
     * to identify a match.
     *
     * @throws MethodNotFoundException If no method matching the specified
     * name and signature can be found.
     */
    public void insertFinisher(String methodName, Type returnType,
                               Type[] argumentTypes)
                               throws MethodNotFoundException {
        if (javaClass == null) {
            throw new IllegalStateException("No class loaded for " +
                "instrumentation");
        }

        Method[] instMethods = javaClassFile.getMethods();
        for (int i = 0; i < instMethods.length; i++) {
            if (instMethods[i].getName().equals(methodName) &&
                Type.getMethodSignature(returnType, argumentTypes)
                    .equals(instMethods[i].getSignature()))
            {
                MethodGen mg =
                    new MethodGen(instMethods[i], fullClassName, cpg);
                InstructionList il = mg.getInstructionList();
                addDefaultHandler(mg, il);
                insertProbeFinishCall(il);
                mg.setInstructionList(il);
                mg.setMaxStack();
                javaClassFile.setMethodAt(mg.getMethod(), i);
                il.dispose();
                return;
            }
        }
        throw new MethodNotFoundException(methodName);
    }

    /*************************************************************************
     * Inserts call to
     * {@link sofya.ed.structural.SocketProbe#start(int,int,boolean,boolean,int)}
     * at beginning of method.
     *
     * <p>The call to
     * {@link sofya.ed.structural.SocketProbe#start(int,int,boolean,boolean,int)}
     * will be the very first instruction in the method. It should be inserted
     * into the static initializer (<i>&lt;clinit&gt;</i>), or the main
     * method if no static initializer is present. Otherwise, instrumentation
     * calls to SocketProbe may fail.</p>
     *
     * @param il The instruction list of the method into which the call is
     * to be inserted.
     */
    protected void insertProbeStartCall(InstructionList il) {
        if (startMethodCall == null) {
            if (!targetJUnit) {
                System.err.println("WARNING: Ignoring request to insert " +
                    "start method call (It is unneccessary for the kind of " +
                    "instrumentation requested)");
            }
            return;
        }

        InstructionHandle startIh = il.getStart();
        InstructionList patch = new InstructionList();

        if (instMode != INST_TRACE_HASHING) {
            patch.append(instFieldLoad);
            patch.append(new PUSH(cpg, this.port));
            patch.append(new PUSH(cpg, this.instMode));
            // If the subject is an event dispatcher or probe class, and we are
            // instrumenting in compatible mode, set the flags to indicate that
            // trace messages should be timestamped, and the signal socket
            // should be opened. (Optimized sequence mode is not safe when the
            // subject is an event dispatcher).
            if ((classIsDispatcher || isProbeClass)
                    && (instMode == INST_COMPATIBLE)) {
                patch.append(new PUSH(cpg, 1));
                patch.append(new PUSH(cpg, 1));
            }
            else {
                patch.append(new PUSH(cpg, 0));
                patch.append(new PUSH(cpg, 0));
            }
            patch.append(new PUSH(cpg, getObjectType().toInt()));
        }
        
        patch.append(startMethodCall);

        il.insert(startIh, patch);
        il.setPositions();
        patch.dispose();
    }

    /*************************************************************************
     * Inserts call to {@link sofya.ed.structural.SocketProbe#finish}
     * immediately prior to any <code>return</code> instruction in the method.
     *
     * @param il The instruction list of the method into which the call is to
     * be inserted.
     */
    protected void insertProbeFinishCall(InstructionList il) {
        if (targetJUnit || (instMode == INST_TRACE_HASHING)) {
            // JUnit components have to manage capture of trace
            // data based on the lifecycle of test cases.
            // Trace hashing just uses a shutdown hook.
            return;
        }
        
        InstructionHandle ih = il.getStart();
        for ( ; ih != null; ih = ih.getNext()) {
            if (ih.getInstruction() instanceof ReturnInstruction) {
                InstructionHandle newTarget = il.insert(ih, instFieldLoad);
                il.insert(ih, iFactory.createInvoke(instClassRef, "finish",
                    Type.VOID, new Type[0], Constants.INVOKEINTERFACE));
                // Update targets of any instructions pointing to the return
                // instruction
                InstructionTargeter[] targeters = ih.getTargeters();
                if (targeters != null) {
                    for (int t = 0; t < targeters.length; t++) {
                        if (targeters[t] instanceof CodeExceptionGen) {
                            CodeExceptionGen exceptionHandler =
                                (CodeExceptionGen) targeters[t];
                            if ((exceptionHandler.getStartPC() == ih) ||
                                    (exceptionHandler.getEndPC() == ih)) {
                                continue;
                            }
                        }
                        targeters[t].updateTarget(ih, newTarget);
                    }
                }
            }
        }
    }

    /*************************************************************************
     * Modifies the <code>main</code> method, if necessary, to prevent
     * unhandled exceptions from being thrown.
     *
     * <p>This method wraps the entire <code>main</code> method with a generic
     * handler for any <code>java.lang.Throwable</code> and eliminates the
     * <code>throws</code> declarations. The handler will emulate the behavior
     * of the <code>ThreadGroup</code> class's <code>uncaughtException</code>
     * method. This is effectively a workaround for a stream-redirection
     * related bug in the SDK or JVM implementation that results in
     * non-deterministic display of subject outputs through Filter when
     * exceptions are thrown from the <code>main</code> method.
     *
     * @param mg The BCEL mutable representation of the method, required to
     * manipulate the throws clauses and handlers associated with the method.
     * @param il The instruction list of the method, used to insert the
     * actual handler instructions.
     */
    protected void addDefaultHandler(MethodGen mg, InstructionList il) {
        /* Note: the default handler is now always added, for the following
           two reasons: 1) It imposes the same simulated determinism
           on unchecked exceptions propagating from main, and 2) It
           provides a convenient mechanism for ensuring
           SocketProbe.finish() is called on any exceptional exit
           from main. */

        // A local variable must be available to hold the exception object
        // received by the catch block. To make sure we don't have any
        // conflicts, we'll just allocate an additional local variable above
        // of any existing ones and use that.
        int maxLocals = mg.getMaxLocals();
        mg.setMaxLocals(maxLocals + 1);

        // Remove all thrown exceptions, since they'll be caught inside the
        // method now
        mg.removeExceptions();

        // If the main method ends in an ATHROW or GOTO, append a regular
        // return. The handler will then get inserted in front of the
        // return and the other instruction will be enclosed in the
        // guarded region.
        InstructionHandle origEnd;
        switch (il.getEnd().getInstruction().getOpcode()) {
        case Constants.ATHROW:
        case Constants.GOTO:
            origEnd = il.getEnd();
            il.append(new RETURN());
            break;
        default:
            origEnd = il.insert(il.getEnd(), new GOTO(il.getEnd()));
            if (origEnd.getPrev() != null) origEnd = origEnd.getPrev();
            break;
        }

        // Build handler instruction sequence
        InstructionList defaultHandler = new InstructionList();
        defaultHandler.append(new ASTORE(maxLocals));
        defaultHandler.append(iFactory.createGetStatic("java.lang.System",
            "out", new ObjectType("java.io.PrintStream")));
        defaultHandler.append(new PUSH(cpg, "Exception in thread " + "\"main" +
            "\" "));
        defaultHandler.append(iFactory.createInvoke("java.io.PrintStream",
            "print", Type.VOID, new Type[]{new ObjectType("java.lang.String")},
            Constants.INVOKEVIRTUAL));
        defaultHandler.append(new ALOAD(maxLocals));
        defaultHandler.append(iFactory.createInvoke("java.lang.Throwable",
            "printStackTrace", Type.VOID, new Type[]{},
            Constants.INVOKEVIRTUAL));

        // Insert handler instructions
        InstructionHandle handlerStart = il.insert(il.getEnd(), defaultHandler);

        il.setPositions();

        // Add handler
        mg.addExceptionHandler(il.getStart(), origEnd, handlerStart,
            (ObjectType) null); // Null catches any type
        defaultHandler.dispose();
    }

    /*************************************************************************
     * Loads the instruction handles corresponding to the bytecode offsets
     * encoded in the given blocks into the reference fields for those
     * blocks, using the supplied BCEL instruction list to find the
     * handles.
     *
     * <p><b>Warning:</b> This method must be called before any changes are
     * made to the instruction list! By ensuring this condition, this
     * method can use what is presumed to be a faster lookup method
     * to locate handles in the list.</p>
     *
     * @param blocks List of blocks for which the reference fields should
     * be assigned the instruction handles corresponding to the start
     * and end offsets of the block.
     * @param il BCEL instruction list from which instruction handles
     * are to be loaded.
     */
    protected void loadBlockRefs(Block[] blocks, InstructionList il) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i].setStartRef(il.findHandle(blocks[i].getStartOffset()));
            blocks[i].setEndRef(il.findHandle(blocks[i].getEndOffset()));
        }
    }

    /*************************************************************************
     * Gets the socket port being used for instrumentation.
     *
     * @return The port which is currently being used for instrumentation.
     */
    public int getPort() {
        return port;
    }

    /*************************************************************************
     * Sets the socket port to be used in instrumentation.
     *
     * <p><b>Warning:</b> This method should be used with caution. Only the
     * first occurrence of an invocation of <code>SocketProbe.start</code>
     * during execution will cause the port to be set. The method where the
     * call to <code>SocketProbe.start</code> has been inserted must be
     * re-instrumented for the change to take effect. The
     * {@link Instrumentor#hasMain} and {@link Instrumentor#hasStaticInit}
     * methods can be used to determine which method in the class represents
     * the first executable code that can be run by the class, if that is the
     * preferred location for the call to <code>SocketProbe.start</code>. A
     * call to {@link Instrumentor#instrumentAll} will also cause the change
     * to take effect.
     *
     * @param port Port which is to be used for instrumentation. The valid
     * range is 1024 to 65535.
     * @param auto If <code>true</code>, set to the default port.
     */
    public void setPort(int port, boolean auto) {
        if (auto) {
            useDefaultPort = true;
            if (classIsDispatcher) {
                this.port = DEFAULT_PORT + 1;
            }
            else {
                this.port = DEFAULT_PORT;
            }
        }
        else if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port " + port +
                " out of range (valid range is 1024-65535)");
        }
        else {
            useDefaultPort = false;
            this.port = port;
        }
    }

    /*************************************************************************
     * Gets the form of instrumentation that the instrumentor is currently
     * set to insert, which will be one of the following:
     * <ul>
     * <li>{@link sofya.base.SConstants#INST_COMPATIBLE}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_NORMAL}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_SEQUENCE}</li>
     * </ul>
     *
     * @return The form of instrumentation which the instrumentor is set to
     * insert, indicated by one of the above constants.
     */
    public int getInstMode() {
        return instMode;
    }

    /*************************************************************************
     * Sets the form of instrumentation that the instrumentor is to
     * insert.
     *
     * @param instMode Integer flag indicating the form of instrumentation to
     * be inserted. Acceptable values are the following:
     * <ul>
     * <li>{@link sofya.base.SConstants#INST_COMPATIBLE}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_NORMAL}</li>
     * <li>{@link sofya.base.SConstants#INST_OPT_SEQUENCE}</li>
     * </ul>
     *
     * @throws IllegalStateException If methods in the class have already
     * been instrumented using a different form of instrumentation. Call
     * {@link Instrumentor#reloadClass} to begin instrumenting the current
     * class with a different form of instrumentation.
     * @throws IllegalArgumentException If the instrumentation mode is
     * invalid.
     */
    public void setInstMode(int instMode)
                throws IllegalStateException, IllegalArgumentException {
        if ((this.instMode != instMode) && (lastInstrumented != null)) {
            throw new IllegalStateException("Cannot use two modes of " +
                "instrumentation in one class");
        }
        if ((instMode < 1) || (instMode == 3) || (instMode > 5)) {
            throw new IllegalArgumentException("Instrumentation mode is " +
                "invalid");
        }
        this.instMode = instMode;
    }
    
    // For subclasses to override
    public void prepareForExit() throws Exception {
    }
}
