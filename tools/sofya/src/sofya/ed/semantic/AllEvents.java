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
 * generate all possible events.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
public class AllEvents extends AbstractEventSpecification {
    /** The set of classes which comprise the entire system. */
    private final Set<ProgramUnit> systemClasses;
    /** The set of classes which for which events are dispatched if
        no other constraints are supplied. */
    private final Set<ProgramUnit> moduleClasses;

    /**
     * Creates a new all events specification.
     *
     * <p>The resulting specification will not define any classes comprising
     * the system or module. This constructor is provided only to support
     * deserialization.</p>
     */
    @SuppressWarnings("unchecked")
    protected AllEvents() {
        this.systemClasses = new THashSet();
        this.moduleClasses = new THashSet();
    }

    /**
     * Creates a new all events specification.
     *
     * @param systemClassList List of classes comprising the entire sytem.
     * @param moduleClassList List of classes comprising the module
     * on which all events are to be observed (this can be the same as
     * the system class list).
     */
    @SuppressWarnings("unchecked")
    public AllEvents(List<ProgramUnit> systemClassList,
            List<ProgramUnit> moduleClassList) {
        this.systemClasses = new THashSet(systemClassList);
        if (systemClassList == moduleClassList) {
            this.moduleClasses = this.systemClasses;
        }
        else {
            this.moduleClasses = new THashSet(moduleClassList);
        }
    }
    
    public String getKey() {
        return "All_Events";
    }

    public Set<ProgramUnit> getSystemClassUnits(Set<ProgramUnit> intoSet) {
        if (intoSet == null) {
            return Collections.unmodifiableSet(systemClasses);
        }
        else {
            intoSet.addAll(systemClasses);
            return intoSet;
        }
    }
    
    @SuppressWarnings("unchecked")
    public Set<String> getSystemClassNames(Set<String> intoSet) {
        Set<String> strSet;
        if (intoSet == null) {
            strSet = new THashSet();
        }
        else {
            strSet = intoSet;
        }
        
        int size = systemClasses.size();
        Iterator<ProgramUnit> iterator = systemClasses.iterator();
        for (int i = size; i-- > 0; ) {
            strSet.addAll(iterator.next().classes);
        }
        return strSet;
    }
    
    public Set<ProgramUnit> getModuleClassUnits(Set<ProgramUnit> intoSet) {
        if (intoSet == null) {
            return Collections.unmodifiableSet(moduleClasses);
        }
        else {
            intoSet.addAll(moduleClasses);
            return intoSet;
        }
    }
    
    @SuppressWarnings("unchecked")
    public Set<String> getModuleClassNames(Set<String> intoSet) {
        Set<String> strSet;
        if (intoSet == null) {
            strSet = new THashSet();
        }
        else {
            strSet = intoSet;
        }
        
        int size = moduleClasses.size();
        Iterator<ProgramUnit> iterator = moduleClasses.iterator();
        for (int i = size; i-- > 0; ) {
            strSet.addAll(iterator.next().classes);
        }
        return strSet;
    }

    public boolean witnessNewObject(String newClass, MethodGen inMethod) {
        return true;
    }

    public boolean witnessConstructorEntry(MethodGen mg) {
        return true;
    }

    public boolean witnessConstructorExit(MethodGen mg) {
        return true;
    }

    public boolean witnessField(FieldInstruction fi,
            ConstantPoolGen cpg, FieldType fType, MethodGen inMethod) {
        // Default is to use watchpoints
        return false;
    }

    public boolean witnessField(String fieldName, FieldType fType,
            Type javaType, String className, String methodName,
            String signature) {
        return true;
    }

    public int witnessField(String fieldName, boolean isStatic,
            String javaType) {
        return FIELD_WITNESS_READ | FIELD_WITNESS_WRITE;
    }
    
    public int witnessField(String fieldName, boolean isStatic,
            org.apache.bcel.generic.Type javaType) {
        return FIELD_WITNESS_READ | FIELD_WITNESS_WRITE;
    }

    public boolean witnessCall(InvokeInstruction call, ConstantPoolGen cpg,
            MethodGen inMethod) {
        return true;
    }

    public boolean useCallInterceptor(InvokeInstruction call,
            ConstantPoolGen cpg) {
        return false;
    }

    public boolean witnessMethodEntry(MethodGen mg) {
        return true;
    }

    public boolean witnessMethodExit(MethodGen mg) {
        return true;
    }

    public boolean witnessAnyMonitor(MonitorType type, MethodGen inMethod) {
        return true;
    }

    public boolean witnessMonitor(String className, MonitorType type) {
        return true;
    }

    public boolean witnessThrow(String exceptionClass, String className,
            String methodName, String signature) {
        return true;
    }

    public boolean witnessThrow(String exceptionClass) {
        return true;
    }

    public boolean witnessCatch(String exceptionClass, MethodGen inMethod) {
        return true;
    }

    public boolean witnessCatch(String exceptionClass) {
        return true;
    }

    public boolean witnessStaticInitializerEntry(String className) {
        return true;
    }
    
    public boolean witnessArrayElement(ArrayInstruction ai,
            ConstantPoolGen cpg, MethodGen inMethod,
            ArrayElementType elemActionType,
            List<ArrayElementBounds> witnessed) {
        return true;
    }

    public void serialize(DataOutputStream stream) throws IOException {
        stream.writeInt(systemClasses.size());
        for (Iterator i = systemClasses.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }

        stream.writeInt(moduleClasses.size());
        for (Iterator i = moduleClasses.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }
    }

    public EventSpecification deserialize(DataInputStream stream)
             throws IOException {
        int size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.systemClasses.add(deserializeProgramUnit(stream));
        }

        size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.moduleClasses.add(deserializeProgramUnit(stream));
        }

        return this;
    }
}
