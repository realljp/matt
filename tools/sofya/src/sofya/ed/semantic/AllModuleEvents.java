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

package sofya.ed.semantic;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.jdi.Type;

import sofya.base.ProgramUnit;

import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;

import gnu.trove.THashSet;

/**
 * Specification which instructs an {@link SemanticEventDispatcher} to
 * generate all possible module events.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public class AllModuleEvents extends AbstractEventSpecification {
    /** The set of classes which comprise the entire system. */
    private final Set<ProgramUnit> systemUnits;
    /** The set of classes for which events are dispatched if
        no other constraints are supplied. */
    private final Set<ProgramUnit> moduleUnits;
    /** The set of classes for which events are dispatched if
        no other constraints are supplied, as strings. */
    private final Set<String> moduleClasses;
    /** Flag specifying whether all monitor events in the entire system
        should be witnessed. */
    private boolean allSystemMonitors;
    /** The policy for how to observe field events. */
    private boolean useFieldBreakpoints = false;

    /**
     * Creates a new all module events specification.
     *
     * <p>The resulting specification will not define any classes comprising
     * the system or module. This constructor is provided only to support
     * deserialization.</p>
     */
    @SuppressWarnings("unchecked")
    protected AllModuleEvents() {
        this.systemUnits = new THashSet();
        this.moduleUnits = new THashSet();
        this.moduleClasses = new THashSet();
        this.allSystemMonitors = false;
    }

    /**
     * Creates a new all module events specification.
     *
     * @param systemUnitList List of classes comprising the entire sytem.
     * @param moduleUnitList List of classes comprising the module
     * on which all events are to be observed (this can be the same as
     * the system class list).
     * @param allSystemMonitors Flag that specifies whether all monitor
     * events in the entire system should be included. This is provided
     * since many analyses are interested in all lock related events
     * despite only being interested in module related events otherwise.
     */
    @SuppressWarnings("unchecked")
    public AllModuleEvents(List<ProgramUnit> systemUnitList,
            List<ProgramUnit> moduleUnitList, boolean allSystemMonitors) {
        this.systemUnits = new THashSet(systemUnitList);
        if (systemUnitList == moduleUnitList) {
            this.moduleUnits = this.systemUnits;
        }
        else {
            this.moduleUnits = new THashSet(moduleUnitList);
        }
        this.moduleClasses = unitsToStrings(moduleUnits, new THashSet());
        this.allSystemMonitors = allSystemMonitors;
    }
    
    /**
     * Specifies whether field events are to be captured using breakpoints
     * (as opposed to watchpoints).
     * 
     * @param useBreakpoints <code>true</code> to make this specification
     * specify the use of breakpoints to capture field events,
     * <code>false</code> to have it specify that watchpoints be used.
     */
    protected void useFieldBreakpoints(boolean useBreakpoints) {
        this.useFieldBreakpoints = useBreakpoints;
    }
    
    public String getKey() {
        return "All_Module_Events";
    }
    
    public Set<ProgramUnit> getSystemClassUnits(Set<ProgramUnit> intoSet) {
        if (intoSet == null) {
            return Collections.unmodifiableSet(systemUnits);
        }
        else {
            intoSet.addAll(systemUnits);
            return intoSet;
        }
    }
    
    @SuppressWarnings("unchecked")
    public Set<String> getSystemClassNames(Set<String> intoSet) {
        if (intoSet == null) {
            return unitsToStrings(systemUnits, new THashSet());
        }
        else {
            return unitsToStrings(systemUnits, intoSet);
        }
    }
    
    public Set<ProgramUnit> getModuleClassUnits(Set<ProgramUnit> intoSet) {
        if (intoSet == null) {
            return Collections.unmodifiableSet(moduleUnits);
        }
        else {
            intoSet.addAll(moduleUnits);
            return intoSet;
        }
    }
    
    public Set<String> getModuleClassNames(Set<String> intoSet) {
        if (intoSet == null) {
            return Collections.unmodifiableSet(moduleClasses);
        }
        else {
            intoSet.addAll(moduleClasses);
            return intoSet;
        }
    }

    private Set<String> unitsToStrings(Set<ProgramUnit> classUnits,
            Set<String> strSet) {
        int size = classUnits.size();
        Iterator<ProgramUnit> iterator = classUnits.iterator();
        for (int i = size; i-- > 0; ) {
            strSet.addAll(iterator.next().classes);
        }
        return strSet;
    }

    public boolean witnessNewObject(String newClass, MethodGen inMethod) {
        return moduleClasses.contains(newClass);
    }

    public boolean witnessConstructorEntry(MethodGen mg) {
        return moduleClasses.contains(mg.getClassName());
    }

    public boolean witnessConstructorExit(MethodGen mg) {
        return moduleClasses.contains(mg.getClassName());
    }

    public boolean witnessField(FieldInstruction fi,
            ConstantPoolGen cpg, FieldType fType, MethodGen inMethod) {
        if (moduleClasses.contains(fi.getReferenceType(cpg).toString())) {
            return useFieldBreakpoints;
        }
        else {
            return false;
        }
    }

    public boolean witnessField(String fieldName, FieldType fType,
            Type javaType, String className, String methodName,
            String signature) {
        return moduleClasses.contains(className);
    }

    public int witnessField(String fieldName, boolean isStatic,
            String javaType) {
        if (!useFieldBreakpoints) {
            if (moduleClasses.contains(
                    fieldName.substring(0, fieldName.lastIndexOf('.')))) {
                return FIELD_WITNESS_READ | FIELD_WITNESS_WRITE;
            }
        }
        
        return 0;
    }
    
    public int witnessField(String fieldName, boolean isStatic,
            org.apache.bcel.generic.Type javaType) {
        if (useFieldBreakpoints) {
            if (moduleClasses.contains(
                    fieldName.substring(0, fieldName.lastIndexOf('.')))) {
                return FIELD_WITNESS_READ | FIELD_WITNESS_WRITE;
            }
        }
        
        return 0;
    }

    public boolean witnessCall(InvokeInstruction call, ConstantPoolGen cpg,
            MethodGen inMethod) {
        return moduleClasses.contains(call.getReferenceType(cpg).toString());
    }

    public boolean useCallInterceptor(InvokeInstruction call,
            ConstantPoolGen cpg) {
        return false;
    }

    public boolean witnessMethodEntry(MethodGen mg) {
        return moduleClasses.contains(mg.getClassName());
    }

    public boolean witnessMethodExit(MethodGen mg) {
        return moduleClasses.contains(mg.getClassName());
    }

    public boolean witnessAnyMonitor(MonitorType type, MethodGen inMethod) {
        return true;
    }

    public boolean witnessMonitor(String className, MonitorType type) {
        if (allSystemMonitors) {
            return true;
        }
        return moduleClasses.contains(className);
    }

    public boolean witnessThrow(String exceptionClass, String className,
            String methodName, String signature) {
        return moduleClasses.contains(exceptionClass);
    }

    public boolean witnessThrow(String exceptionClass) {
        return moduleClasses.contains(exceptionClass);
    }

    public boolean witnessCatch(String exceptionClass, MethodGen inMethod) {
        return moduleClasses.contains(exceptionClass);
    }

    public boolean witnessCatch(String exceptionClass) {
        return moduleClasses.contains(exceptionClass);
    }

    public boolean witnessStaticInitializerEntry(String className) {
        return moduleClasses.contains(className);
    }
    
    public boolean witnessArrayElement(ArrayInstruction ai,
            ConstantPoolGen cpg, MethodGen inMethod,
            ArrayElementType elemActionType,
            List<ArrayElementBounds> witnessed) {
        if (moduleClasses.contains(ai.getType(cpg).toString())
                || moduleClasses.contains(inMethod.getClassName())) {
            return true;
        }
        else {
            return false;
        }
    }

    public void serialize(DataOutputStream stream) throws IOException {
        stream.writeBoolean(useFieldBreakpoints);
        stream.writeInt(systemUnits.size());
        for (Iterator i = systemUnits.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }

        stream.writeInt(moduleUnits.size());
        for (Iterator i = moduleUnits.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }

        stream.writeBoolean(allSystemMonitors);
    }

    public EventSpecification deserialize(DataInputStream stream)
             throws IOException {
        useFieldBreakpoints = stream.readBoolean();
        int size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.systemUnits.add(deserializeProgramUnit(stream));
        }

        size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.moduleUnits.add(deserializeProgramUnit(stream));
        }

        this.allSystemMonitors = stream.readBoolean();

        unitsToStrings(this.moduleUnits, this.moduleClasses);

        return this;
    }
}
