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

package sofya.apps.atomicity;

import java.util.List;
import java.io.IOException;

import sofya.base.ProgramUnit;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.ed.Instrumentor;
import sofya.ed.semantic.AllModuleEvents;
import sofya.graphs.irg.IRG;
import static sofya.ed.semantic.SemanticConstants.TYPE_ANY;
import static sofya.ed.semantic.EventSpecification.ArrayElementBounds.NO_BOUND;

import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

/**
 * Specification which instructs an
 * {@link sofya.ed.semantic.SemanticEventDispatcher} to dispatch
 * events for atomicity checking.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public class AtomicityEvents extends AllModuleEvents {
    /** Controls whether debug outputs are displayed. */
    private static final boolean DEBUG = true;
    
    /** Flag to control whether or not breakpoint-based field events are
        entirely disabled. */
    private static final boolean ENABLE_FIELD_EVENTS = true;
    /** Flag to control whether or not array element events are entirely
        disabled. */
    private static final boolean ENABLE_ARRAY_EVENTS = true;
    /** Maximum index of array elements to be observed. */
    public static final int ARRAY_CUTOFF = 2;
    
    /** Constant for the <code>java.lang.Thread</code> type, used for
        determining which classes are thread classes. */
    private static final ObjectType THREAD_TYPE =
        new ObjectType("java.lang.Thread");
    
    /** Interclass relation graph used to heuristically decide whether
        a method may bind to a native implementation, and thus whether
        calls  to the method should be captured using interceptors
        (to enable arguments to be marked as escaped).  */
    private IRG irg;

    /**
     * Creates a new atomicity events specification.
     *
     * <p>The resulting specification will not define any classes comprising
     * the system or module. This constructor is provided only to support
     * deserialization.</p>
     */
    public AtomicityEvents() {
        super();
        useFieldBreakpoints(true);
    }

    /**
     * Creates a new all atomicity events specification.
     *
     * @param systemUnitList List of classes comprising the entire sytem.
     * @param moduleUnitList List of classes comprising the module
     * on which atomicity events are to be observed (this can be the same as
     * the system class list).
     * @param allSystemMonitors Flag that specifies whether all monitor
     * events in the entire system should be included. This is provided
     * since many analyses are interested in all lock related events
     * despite only being interested in module related events otherwise.
     */
    public AtomicityEvents(List<ProgramUnit> systemUnitList,
            List<ProgramUnit> moduleUnitList, boolean allSystemMonitors) {
        super(systemUnitList, moduleUnitList, allSystemMonitors);
        useFieldBreakpoints(true);
        
        try {
            irg = new IRG(this.getSystemClassNames(null));
        }
        catch (IOException e) {
            throw new IncompleteClasspathException(e);
        }
    }
    
    public String getKey() {
        return "Atomicity_Events";
    }

    @Override
    public boolean witnessCall(InvokeInstruction call, ConstantPoolGen cpg,
            MethodGen inMethod) {
        ObjectType classType = call.getLoadClassType(cpg);
        String methodName = call.getMethodName(cpg);

        try {
            if (classType.subclassOf(THREAD_TYPE)) {
                if (methodName.equals("start") || methodName.equals("join")) {
                    return true;
                }
            }
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }

        //###############################
        // if (className.equals("java.lang.System")) {
        //     return true;
        // }
        //###############################
        
        if (super.witnessCall(call, cpg, inMethod)) {
            try {
                return Instrumentor.isPossiblyNative(call, cpg, irg);
            }
            catch (ClassNotFoundException e) {
                throw new IncompleteClasspathException(e);
            }
        }
        else {
            return false;
        }
    }

    @Override
    public boolean useCallInterceptor(InvokeInstruction call,
            ConstantPoolGen cpg) {
        ObjectType classType = call.getLoadClassType(cpg);
        String methodName = call.getMethodName(cpg);

        try {
            if (classType.subclassOf(THREAD_TYPE)) {
                if (methodName.equals("start") || methodName.equals("join")) {
                    return true;
                }
            }
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }

        //###############################
        // if (className.equals("java.lang.System")) {
        //     return true;
        // }
        //###############################

        try {
            return Instrumentor.isPossiblyNative(call, cpg, irg);
        }
        catch (ClassNotFoundException e) {
            throw new IncompleteClasspathException(e);
        }
    }
    
    @Override
    public boolean witnessArrayElement(ArrayInstruction ai,
            ConstantPoolGen cpg, MethodGen inMethod,
            ArrayElementType elemActionType,
            List<ArrayElementBounds> witnessed) {
        if (!ENABLE_ARRAY_EVENTS) {
            return false;
        }
        
        /*#ifdef FOR_MOLDYN
        // A particular configuration used for the JavaGrande "moldyn"
        // benchmark, to exclude capture of certain events in accordance
        // with the way it has been analyzed for other publications
        // in the literature.
        if (inMethod.getName().startsWith("synthetic$")) {
            if (DEBUG) {
                System.out.println("Ignoring array element in " +
                    inMethod.getName());
            }
            return false;
        }
        /*#endif*/
        
        witnessed.add(new ArrayElementBounds(
            TYPE_ANY, NO_BOUND, ARRAY_CUTOFF));
        
        return true;
    }
    
    @Override
    public boolean witnessField(FieldInstruction field,
            ConstantPoolGen cpg, FieldType fType, MethodGen inMethod) {
        if (!ENABLE_ARRAY_EVENTS) {
            if (field.getFieldType(cpg) instanceof ArrayType) {
                if (DEBUG) {
                    System.out.println("Skipping: " +
                        field.getReferenceType(cpg).toString() + "." +
                        field.getFieldName(cpg));
                }
                return false;
            }
        }
        
        /*#ifdef FOR_MOLDYN
        if (inMethod.getName().startsWith("synthetic$")) {
            return false;
        }
        
        if (field.getFieldName(cpg).equals("nthreads")) {
            if (DEBUG) {
                System.out.println("Skipping 'nthreads' field");
            }
            return false;
        }
        /*#endif*/
        
        if (ENABLE_FIELD_EVENTS) {
            return super.witnessField(field, cpg, fType, inMethod);
        }
        else {
            return false;
        }
    }
    
    @Override
    public boolean witnessField(String fieldName, FieldType fType,
            com.sun.jdi.Type javaType, String className, String methodName,
            String signature) {
        if (!ENABLE_ARRAY_EVENTS) {
            if (javaType instanceof com.sun.jdi.ArrayType) {
                if (DEBUG) {
                    System.out.println("Skipping: " + fieldName);
                }
                return false;
            }
        }
        
        /*#ifdef FOR_MOLDYN
        if (methodName.startsWith("synthetic$")) {
            if (DEBUG) {
                System.out.println("Skipping " + fieldName +
                    " field in " + methodName);
            }
            return false;
        }
        
        if (fieldName.endsWith("nthreads")) {
            if (DEBUG) {
                System.out.println("Skipping 'nthreads' field");
                return false;
            }
        }
        /*#endif*/
        
        return super.witnessField(fieldName, fType, javaType, className,
            methodName, signature);
    }

    @Override
    public int witnessField(String fieldName, boolean isStatic,
            String javaType) {
        return 0;
    }
    
    @Override
    public int witnessField(String fieldName, boolean isStatic,
            Type javaType) {
        if (!ENABLE_ARRAY_EVENTS) {
            if (javaType instanceof ArrayType) {
                if (DEBUG) {
                    System.out.println("Skipping: " + fieldName);
                }
                return 0;
            }
        }
        
        /*#ifdef FOR_MOLDYN
        if (fieldName.endsWith("nthreads")) {
            if (DEBUG) {
                System.out.println("Skipping 'nthreads' field");
                return 0;
            }
        }
        /*#endif*/
        
        if (ENABLE_FIELD_EVENTS) {
            return super.witnessField(fieldName, isStatic, javaType);
        }
        else {
            return 0;
        }
    }
    
    @Override
    public boolean witnessMethodEntry(MethodGen mg) {
        /*#ifdef FOR_MOLDYN
        if (mg.getName().startsWith("synthetic$")) {
            if (DEBUG) {
                System.out.println("Ignoring method entry " +
                    mg.getName());
            }
            return false;
        }
        /*#endif*/
        
        return super.witnessMethodEntry(mg);
    }

    @Override
    public boolean witnessMethodExit(MethodGen mg) {
        /*#ifdef FOR_MOLDYN
        if (mg.getName().startsWith("synthetic$")) {
            if (DEBUG) {
                System.out.println("Ignoring method exit " + mg.getName());
            }
            return false;
        }
        /*#endif*/
        
        return super.witnessMethodEntry(mg);
    }
}
