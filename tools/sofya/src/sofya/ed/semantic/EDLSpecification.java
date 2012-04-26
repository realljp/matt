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

import java.io.*;
import java.util.*;

import com.sun.jdi.Method;

import sofya.base.ProgramUnit;
import sofya.base.exceptions.IncompleteClasspathException;
import sofya.base.exceptions.SofyaError;
import sofya.ed.semantic.ConditionTree.*;
import static sofya.ed.semantic.SemanticConstants.TYPE_ANY;

import org.apache.bcel.generic.*;

import gnu.trove.THashSet;
import gnu.trove.THashMap;

/**
 * Encapsulates the compiled representation of a module description file
 * which was supplied to the instrumentor.
 *
 * <p>Most of the methods in this class are able to report in constant
 * time whether a particular observable is defined as part of the module
 * (an important consideration because some of these methods are used
 * online by the {@link SemanticEventDispatcher}). In order to account for
 * wildcards in the specification, this is typically accomplished via the use
 * of rule tables keyed on strings containing a partial or full package and
 * name qualification. More formally, for methods of that type, the
 * runtime order to check inclusiveness of an observable is
 * characterized by: 2 * (maximum package depth).</p>
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 */
@SuppressWarnings("unchecked")
public class EDLSpecification extends AbstractEventSpecification
        implements AdaptiveEventSpecification {
    /** Conditional compilation flag to enable debugging outputs. */
    private static final boolean DEBUG = false;
    
    /** Identifying key associated with this specification. */
    public String specKey = "default";
    
    /** Records global constraints shared by all event specifications
        declared in a single EDL suite; this object is shared by
        all specification instances generated from the same suite. */
    GlobalConstraints globals = null;
    
    /** The set of components that comprise the entire system. */
    private Set<ProgramUnit> systemUnits = null;
    /** The set of classes that comprise the entire system. */
    private Set<String> systemClasses = null;

    /** The set of components for which observables are included if
        no other conditions are supplied. */
    private Set<ProgramUnit> moduleUnits = null;
    /** The set of classes for which observables are included if
        no other conditions are supplied. */
    private Set<String> moduleClasses = new THashSet();
    /** The subset of the module classes which are throwables. */
    private Set<String> moduleThrowables = new THashSet();

    /** Contains the rules that determine whether a NEW instruction
        creates a class defined within the module. */
    private Map<Object, Object> new_events = new THashMap();

    /** Contains the rules that determine whether a field is specified
        as part of the module. */
    private Map<Object, Object>[] field_events = new THashMap[4];

    /** Contains the rules that determine whether a call is to a method
        defined within the module. */
    private Map<Object, Object>[] call_events = new THashMap[4];

    /** Contains the rules that determine whether construction of an
        object is considered part of the module. */
    private Map<Object, Object>[] construct_events = new THashMap[2];
    private static final int CNS_ENTER = 0;
    private static final int CNS_EXIT  = 1;

    /** Contains the rules that determine whether entry to and
        exit from a method is considered part of the module. */
    private Map<Object, Object>[] method_events = new THashMap[4];

    /** Contains the rules that determine what monitor related events
        are included in the module. */
    private Map<Object, Object>[] monitor_events = new THashMap[4];

    /** Contains the rules that determine whether the throwing and
        catching of a throwable is considered part of the module. */
    private Map<Object, Object>[] throwable_events = new THashMap[2];
    /** Contains the set of throwables for which subclasses are also
        to be considered part of the module. */
    private Set<String>[] throwable_inc_subclass = new THashSet[2];
    /** Constant for checking throw event data structures. */
    private static final int THROW = 0;
    /** Constant for checking catch event data structures. */
    private static final int CATCH = 1;

    /** Contains the rules that determine what static initializer events
        are included in the module. */
    private Map<Object, Object> static_init_events = new THashMap();

    /** Contains the rules that determine whether array element accesses
        and stores are considered part of the module, including bounds
        on element indices. */
    private ArrayElementConditions[] array_elem_conditions =
        new ArrayElementConditions[2];

    /** Can be used to record properties relevant to certain events, such
        as whether a call should be witnessed using an interceptor. Utilizes
        the same matching mechanism as the name rule table. */
    private Map<Object, Map<Object, Object>> event_properties = new THashMap();
    
    /**
     * Encapsulates global constraints that are declared in the top-level
     * block for an entire EDL suite. These are shared by all event
     * specification instances that are generated from the same EDL suite.
     */
    static final class GlobalConstraints {
        /** Records the global bounds on the indexes of array elements
            for which events should be observed, which are used if no
            overriding bounds are declared in the event request in
            a specific EDL observables block. */
        final Map<Type, ArrayElementBounds>[] arrayElementBounds =
            new THashMap[2];
        
        GlobalConstraints() {
            for (int i = 1; i >= 0; i--) {
                arrayElementBounds[i] = new THashMap();
            }
        }
        
        void serialize(DataOutputStream out) throws IOException {
            for (int i = 0; i < 2; i++) {
                int size = arrayElementBounds[i].size();
                Iterator<Type> iter =
                    arrayElementBounds[i].keySet().iterator();
                out.writeInt(size);
                for (int j = size; j-- > 0; ) {
                    Type javaType = iter.next();
                    out.writeUTF(javaType.getSignature());
                    arrayElementBounds[i].get(javaType).serialize(out);
                }
            }
        }
        
        void deserialize(DataInputStream in) throws IOException {
            for (int i = 0; i < 2; i++) {
                int size = in.readInt();
                for (int j = size; j-- > 0; ) {
                    String typeStr = in.readUTF();
                    Type javaType;
                    if (typeStr.charAt(0) == '@') {
                        javaType = SemanticConstants.TYPE_ANY;
                    }
                    else {
                        javaType = Type.getType(typeStr);
                    }
                    ArrayElementBounds bounds = new ArrayElementBounds();
                    bounds.deserialize(in);
                    arrayElementBounds[i].put(javaType, bounds);
                }
            }
        }
    }
    
    /**
     * Creates a new module description containing no classes or rules;
     * provided for deserialization.
     */
    @SuppressWarnings("unchecked")
    protected EDLSpecification() {
        this.systemUnits = new THashSet();
        this.moduleUnits = new THashSet();
    }

    /**
     * Creates a new EDL specification.
     *
     * @param systemClassList List of classes comprising the entire system.
     * @param moduleClassList List of classes comprising the module.
     */
    @SuppressWarnings("unchecked")
    protected EDLSpecification(List<ProgramUnit> systemClassList,
            List<ProgramUnit> moduleClassList) {
        this("default", systemClassList, moduleClassList, null);
    }
    
    /**
     * Creates a new EDL specification.
     *
     * @param key Identifying key to be associated with this specification.
     * @param systemClassList List of classes comprising the entire system.
     * @param moduleClassList List of classes comprising the module.
     */
    protected EDLSpecification(String key, List<ProgramUnit> systemClassList,
            List<ProgramUnit> moduleClassList) {
        this(key, systemClassList, moduleClassList, null);
    }
    
    /**
     * Creates a new EDL specification.
     *
     * @param key Identifying key to be associated with this specification.
     * @param systemClassList List of classes comprising the entire system.
     * @param moduleClassList List of classes comprising the module.
     * @param globals Global constraints object to be used by this
     * specification; this may be shared with other edl specification
     * instances generated from a common EDL suite.
     */
    protected EDLSpecification(String key, List<ProgramUnit> systemClassList,
            List<ProgramUnit> moduleClassList, GlobalConstraints globals) {
        this.specKey = key;
        this.systemUnits = new THashSet(systemClassList);
        this.moduleUnits = new THashSet(moduleClassList);
        this.globals = globals;
        init();
    }

    /**
     * Does some processing on the initial class lists and creates data
     * structures.
     */
    @SuppressWarnings("unchecked")
    private void init() {
        initModuleClassData();

        forAllClasses(new AddNameRuleAction(new_events,
            new EventConditions(true, 0)));

        for (int i = 0; i < 4; i++) {
            field_events[i] = new THashMap();
            call_events[i] = new THashMap();
            method_events[i] = new THashMap();
            monitor_events[i] = new THashMap();

            forAllClasses(new AddNameRuleAction(field_events[i],
                new EventConditions(true, 0)));
            forAllClasses(new AddNameRuleAction(call_events[i],
                new EventConditions(true, 0)));
            forAllClasses(new AddNameRuleAction(method_events[i],
                new EventConditions(true, 0)));
            forAllClasses(new AddNameRuleAction(monitor_events[i],
                new EventConditions(true, 0)));
        }

        forAllClasses(new AddNameRuleAction(static_init_events,
            new EventConditions(true, 0)));

        for (int i = 0; i < 2; i++) {
            construct_events[i] = new THashMap();
            forAllClasses(new AddNameRuleAction(construct_events[i],
                new EventConditions(true, 0)));

            throwable_events[i] = new THashMap();
            throwable_inc_subclass[i] = new THashSet();

            forAllThrowables(new AddNameRuleAction(throwable_events[i],
                new EventConditions(true, 0)));
            
            array_elem_conditions[i] = new ArrayElementConditions(i);
            forAllClasses(new AddArrayElementAction(array_elem_conditions[i],
                new EventConditions(true, 0), new ArrayElementBounds()));
        }
    }

    /**
     * Initializes the module classes set and throwable classes set since
     * they are frequently used.
     */
    private void initModuleClassData() {
        int unitCount = moduleUnits.size();
        Iterator<ProgramUnit> unitIterator = moduleUnits.iterator();
        for (int i = unitCount; i-- > 0; ) {
            ProgramUnit pUnit = unitIterator.next();

            int classCount = pUnit.classes.size();
            Iterator<String> classIterator = pUnit.classes.iterator();
            for (int j = classCount; j-- > 0; ) {
                String className = classIterator.next();

                moduleClasses.add(className);

                try {
                    if ((new ObjectType(className)).subclassOf(
                            Type.THROWABLE)) {
                        moduleThrowables.add(className);
                    }
                }
                catch (ClassNotFoundException e) {
                    throw new IncompleteClasspathException(e);
                }
            }
        }
    }
    
    public String getKey() {
        return specKey;
    }
    
    /**
     * Sets the identifying key for this specification; should be used
     * only by the EDL parser.
     * 
     * @param key Key used to identify this specification.
     */
    void setSpecificationKey(String key) {
        this.specKey = key;
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
    
    public Set<String> getSystemClassNames(Set<String> intoSet) {
        if (systemClasses == null) {
            systemClasses = new THashSet();
            int size = systemUnits.size();
            Iterator<ProgramUnit> iterator = systemUnits.iterator();
            for (int i = size; i-- > 0; ) {
                systemClasses.addAll(iterator.next().classes);
            }
        }

        if (intoSet == null) {
            return Collections.unmodifiableSet(systemClasses);
        }
        else {
            intoSet.addAll(systemClasses);
            return intoSet;
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

    /**
     * Interface that defines the callback function to be used by the
     * &apos;forAll&apos;-classes methods.
     */
    private static interface ClassAction {
        /**
         * Callback function implementing the action to be performed for a
         * given class.
         *
         * @param className Name of the class for which an action will be
         * performed.
         *
         * @return <code>true</code> if the action was successfully performed.
         */
        boolean handleClass(String className);
    }

    /**
     * Automatically performs some action for all of the module classes.
     *
     * @param clAction Action to be performed for each module class.
     */
    private void forAllClasses(ClassAction clAction) {
        int size = moduleClasses.size();
        Iterator iterator = moduleClasses.iterator();
        for (int i = size; i-- > 0; ) {
            clAction.handleClass((String) iterator.next());
        }
    }

    /**
     * Automatically performs some action for all of the throwable classes
     * included in the module.
     *
     * @param clAction Action to be performed for each throwable class.
     */
    private void forAllThrowables(ClassAction clAction) {
        int size = moduleThrowables.size();
        Iterator iterator = moduleThrowables.iterator();
        for (int i = size; i-- > 0; ) {
            clAction.handleClass((String) iterator.next());
        }
    }

    /**
     * Adds supplied conditions on the observation of an event for a class.
     *
     * <p>This is used during initialization to create the implied rules
     * that include all observables in the module classes by default,
     * and when adding wildcard sets of classes to the specification.</p>
     */
    private static class AddNameRuleAction implements ClassAction {
        private Map<Object, Object> rule_map;
        private EventConditions ecs;

        /**
         * Creates a new action performer to add conditions on the observation
         * of an event to each handled class.
         *
         * @param rule_map Rule set to which the event conditions are to be
         * added for each class.
         * @param ecs Conditions constraining the observation of the event.
         */
        AddNameRuleAction(Map<Object, Object> rule_map, EventConditions ecs) {
            this.rule_map = rule_map;
            this.ecs = ecs;
        }

        public boolean handleClass(String className) {
            EventConditions cur_ecs = (EventConditions) rule_map.get(className);
            if (cur_ecs == null) {
                rule_map.put(className, new EventConditions(ecs));
            }
            else {
                cur_ecs.merge(ecs);
            }

            return true;
        }
    }

    /**
     * Modifies conditions on the observation of an event for a class;
     * used to support the adaptive instrumentation functionality.
     *
     * <p>This is used to modify conditions on events for a set
     * of classes when handling adaptive instrumentation requests.</p>
     */
    private static class ChangeNameRuleAction implements ClassAction {
        private Map<Object, Object> rule_map;
        private boolean inclusion;
        private List<Object> nodeKeys;
        private List<Object> conditionNodes;
        private boolean rootOnly;

        /**
         * Creates a new action performer to change the conditions on the
         * observation of an event for each handled class.
         *
         * @param rule_map Rule set containing the event conditions to be
         * modified for each class.
         * @param inclusion New event inclusion value to be applied to
         * each handled class.
         */
        ChangeNameRuleAction(Map<Object, Object> rule_map, boolean inclusion) {
            this.rule_map = rule_map;
            this.inclusion = inclusion;
            this.rootOnly = true;
        }

        /**
         * Creates a new action performer to change the conditions on the
         * observation of an event for each handled class.
         *
         * @param rule_map Rule set containing the event conditions to be
         * modified for each class.
         * @param inclusion New event inclusion value to be applied to
         * each handled class.
         * @param nodeKeys List of keys to nodes to be merged into the
         * condition tree for the current event conditions for each
         * handled class.
         * @param conditionNodes List of new nodes to be merged into the
         * condition tree for the current event conditions for each
         * handled class.
         */
        ChangeNameRuleAction(Map<Object, Object> rule_map, boolean inclusion,
                List<Object> nodeKeys, List<Object> conditionNodes) {
            this.rule_map = rule_map;
            this.inclusion = inclusion;
            this.nodeKeys = nodeKeys;
            this.conditionNodes = conditionNodes;
        }

        public boolean handleClass(String className) {
            EventConditions cur_ecs =
                (EventConditions) rule_map.get(className);
            if (cur_ecs == null) {
                cur_ecs = new EventConditions(inclusion, 1);
                rule_map.put(className, cur_ecs);
                if (rootOnly) return true;
            }
            RootNode newRoot = new RootNode(inclusion, 1);
            if (rootOnly) {
                cur_ecs.clear(newRoot);
            }
            else {
                cur_ecs.merge(newRoot, nodeKeys, conditionNodes, true);
            }
            return true;
        }
    }
    
    /**
     * Adds supplied conditions on the observation of array element
     * events for a class.
     *
     * <p>This is used during initialization to create the implied rules
     * that include all observables in the module classes by default,
     * and when adding wildcard sets of classes to the specification.</p>
     * 
     * <p>This special action class must be used because the internal
     * storage mechanism for recording array element event constraints
     * is different from that for most other events.</p>
     */
    private static class AddArrayElementAction implements ClassAction {
        private ArrayElementConditions elem_conds;
        private EventConditions ecs;
        private ArrayElementBounds bounds;

        /**
         * Creates a new action performer to add conditions on the observation
         * of an array element event to each handled class.
         *
         * @param elem_conds Data structure recording the conditions on
         * observation of array elements; this data structure aggregates
         * conditions on array element events for multiple array types.
         * @param ecs Conditions constraining the observation of the array
         * element events for any classes handled by this action performer.
         * @param bounds Bounds on the indexes of array elements to be
         * observed for each class handled by this action performer.
         */
        AddArrayElementAction(ArrayElementConditions elem_conds,
                EventConditions ecs, ArrayElementBounds bounds) {
            this.elem_conds = elem_conds;
            this.ecs = ecs;
            this.bounds = bounds;
        }

        public boolean handleClass(String className) {
            ArrayElementBounds boundsCopy = bounds.copy();
            boundsCopy.javaType = new ObjectType(className);
            elem_conds.addTypeConditions(boundsCopy, new EventConditions(ecs));

            return true;
        }
    }

    /**
     * Builds a rule key for a given method signature.
     *
     * @param className Class specified in the method signature.
     * @param methodSignature Name of the method.
     * @param argTypes Types of the arguments to the method.
     *
     * @return A consistent string key which can be used in the call maps.
     */
    private static String buildKeySignature(String className,
            String methodName, Type[] argTypes) {
        StringBuilder sb = new StringBuilder(className);
        sb.append(".");
        sb.append(methodName);
        if (argTypes != null) {
            sb.append(".");
            sb.append(Type.getMethodSignature(Type.VOID, argTypes));
        }
        else if (!methodName.equals("*")) {
            sb.append(".*");
        }
        return sb.toString();
    }

    /**
     * Builds a rule key for a given method signature.
     *
     * @param className Class specified in the method signature.
     * @param methodSignature Name of the method.
     * @param signature JNI signature string for the method.
     *
     * @return A consistent string key which can be used in the call maps;
     * it is equivalent to that produced by
     * {@link #buildKeySignature(String,String,Type[])}.
     */
    private static String buildKeySignature(String className,
            String methodName, String signature) {
        StringBuilder sb = new StringBuilder(className);
        sb.append(".");
        sb.append(methodName);
        if (signature != null) {
            sb.append(".");
            sb.append(signature);
        }
        return sb.toString();
    }

    /**
     * Adds an inclusion or exclusion rule for a named observable, and
     * associated conditions on the locations in which the observable
     * is to be witnessed.
     *
     * @param name Name of the observable for which the rule is defined,
     * may include a trailing wildcard.
     * @param rule_map Rule set for the type of observable for which
     * a rule is being added.
     * @param ecs Conditions constraining when the observable is to
     * be witnessed.
     */
    private void addNameRule(String name, Map<Object, Object> rule_map,
            EventConditions ecs) {
        if (name.endsWith(".*")) {
            name = name.substring(0, name.length() - 2);
        }

        EventConditions cur_ecs = (EventConditions) rule_map.get(name);
        if (cur_ecs == null) {
            rule_map.put(name, new EventConditions(ecs));
        }
        else {
            cur_ecs.merge(ecs);
        }
    }

    /**
     * Changes the inclusion or exclusion status for a named observable.
     *
     * @param name Name of the observable for which the rule is defined,
     * may include a trailing wildcard.
     * @param rule_map Rule set for the type of observable for which
     * a rule is being changed.
     * @param inclusion New inclusion or exclusion status to be set for
     * the given observable.
     */
    private void changeNameRule(String name, Map<Object, Object> rule_map,
            boolean inclusion) {
        if (name.endsWith(".*")) {
            name = name.substring(0, name.length() - 2);
        }

        EventConditions cur_ecs = (EventConditions) rule_map.get(name);
        if (cur_ecs == null) {
            cur_ecs = new EventConditions(inclusion, 1);
            rule_map.put(name, cur_ecs);
        }
        else {
            cur_ecs.clear(new RootNode(inclusion, 1));
        }
    }

    /**
     * Changes the inclusion or exclusion rule for a named observable and/or
     * associated conditions on the locations in which the observable
     * is to be witnessed.
     *
     * @param name Name of the observable for which the rule is defined,
     * may include a trailing wildcard.
     * @param rule_map Rule set for the type of observable for which
     * a rule is being changed.
     * @param inclusion New inclusion or exclusion status to be set for
     * the given observable.
     * @param nodeKeys List of keys to new condition nodes to be merged
     * into the condition tree associated with the observable.
     * @param conditionNodes List of new condition nodes to be merged
     * into the condition tree associated with the observable.
     */
    @SuppressWarnings("unused")
    private void changeNameRule(String name, Map<Object, Object> rule_map,
            boolean inclusion, List<Object> nodeKeys,
            List<Object> conditionNodes) {
        if (name.endsWith(".*")) {
            name = name.substring(0, name.length() - 2);
        }

        EventConditions cur_ecs = (EventConditions) rule_map.get(name);
        if (cur_ecs == null) {
            cur_ecs = new EventConditions(inclusion, 1);
            rule_map.put(name, cur_ecs);
        }
        cur_ecs.merge(new RootNode(inclusion, 1), nodeKeys, conditionNodes,
            true);
    }

    /**
     * Checks the name of an observable to determine whether it is included
     * in the specification.
     *
     * @param name Name of the observable to be tested for inclusion in
     * the module.
     * @param event_map Rule set holding the rules and associated conditions
     * for observables of the type being checked.
     * @param inLoc Fully qualified location in which the event being checked
     * occurred.
     */
    private boolean checkNameRule(String name, Map event_map, String inLoc) {
        EventConditions ecs = (EventConditions) event_map.get(name);

        Condition curCond;
        if (ecs != null) {
            curCond = ecs.checkConditions(inLoc);
        }
        else {
            curCond = Condition.DEFAULT_EXCLUDE;
        }

        int stopIndex = name.lastIndexOf('.');
        for (int i = 0; i <= stopIndex; i++) {
            if (name.charAt(i) == '.') {
                String s = name.substring(0, i);
                ecs = (EventConditions) event_map.get(s);

                if (ecs != null) {
                    Condition cond = (Condition) ecs.checkConditions(inLoc);
                    if (cond.rank > curCond.rank) {
                        curCond = cond;
                    }
                }
           }
        }

        return curCond.inclusion;
    }

    /**
     * Adds a property and value to be associated with an event key.
     *
     * @param key Event key with which the property is to be associated,
     * normally the name of the observable referenced by the event (the
     * same as a key that would be used in the name rule table).
     * @param property Name of the property to be set.
     * @param value Value to associate with the property.
     */
    @SuppressWarnings("unchecked")
    private void addProperty(String key, String property, String value) {
        if (key.endsWith(".*")) {
            key = key.substring(0, key.length() - 2);
        }

        Map<Object, Object> properties = event_properties.get(key);
        if (properties == null) {
            properties = new THashMap();
            event_properties.put(key, properties);
        }

        properties.put(property, value);
    }

    /**
     * Gets a property value associated with an event key.
     *
     * @param key Event key with which the property is associated,
     * normally the name of the observable referenced by the event (the
     * same as a key that would be used in the name rule table).
     * @param property Name of the property value to be retrieved.
     *
     * @return The value associated with the requested property.
     */
    private String getProperty(String key, String property) {
        int stopIndex = key.lastIndexOf('.');
        for (int i = 0; i <= stopIndex; i++) {
            if (key.charAt(i) == '.') {
                String s = key.substring(0, i);
                Map properties = (Map) event_properties.get(s);

                if (properties != null) {
                    return (String) properties.get(property);
                }
           }
        }

        return null;
    }

    /**
     * Checks the name of an exception related observable to determine whether
     * it is included in the specification.
     *
     * <p>This method checks for matching exception throw and catch observable
     * events. It checks for inclusion of subclasses, and it does
     * <strong>not</strong> permit wildcard queries.</p>
     *
     * @param name Name of the exception observable to be tested for inclusion
     * in the module.
     * @param int eventType Constant specifying which type of exception
     * observable is to be checked for inclusion.
     * @param inLoc Fully qualified location in which the throwable event being
     * checked occurred.
     */
    private boolean checkThrowableRule(String name, int eventType,
            String inLoc) {
        Map event_map = throwable_events[eventType];
        EventConditions ecs = (EventConditions) event_map.get(name);

        Condition curCond;
        if (ecs != null) {
            curCond = ecs.checkConditions(inLoc);
        }
        else {
            curCond = Condition.DEFAULT_EXCLUDE;
        }

        int size = throwable_inc_subclass[eventType].size();
        if (size > 0) {
            Iterator iterator = throwable_inc_subclass[eventType].iterator();
            for (int i = size; i-- > 0; ) {
                String className = (String) iterator.next();

                Class<?> checkClass, matchClass;
                try {
                    checkClass = Class.forName(name);
                    matchClass = Class.forName(className);
                }
                catch (ClassNotFoundException e) {
                    throw new SofyaError("Unable to determine subclass " +
                        "relationship", e);
                }

                if (matchClass.isAssignableFrom(checkClass)) {
                    ecs = (EventConditions) event_map.get(className);

                    Condition cond = (Condition) ecs.checkConditions(inLoc);
                    if (cond.rank > curCond.rank) {
                        curCond = cond;
                    }
                }
            }
        }

        return curCond.inclusion;
    }

    /**
     * Performs a heuristic check to determine whether any observable events
     * associated with a given observable name are considered included in
     * the specification.
     *
     * <p>This test should report whether the given observable <em>may</em>
     * be included in the specification. Conditions on the locations in which
     * events associated with the observable should be witnessed can prevent
     * a certainly correct answer to this query. This method should not,
     * however, report an observable as excluded if there is any possibility
     * that it is included.</p>
     *
     * <p>This method currently uses the following heuristic:
     * If there are no location conditions for the given observable,
     * it is reported as included in the specification if the highest ranked
     * rule pertaining to the observable is an inclusion rule. If there are
     * conditions on the given observable, it is always reported as
     * included on the assumption that a user would not purposefully
     * code conditions that are guaranteed to prevent the observable from
     * ever being witnessed.</p>
     *
     * @param name Fully qualified name of the observable to be checked for
     * inclusion in the specification.
     * @param event_map Rule set for the type of observable on which the
     * check is to be performed.
     *
     * @return <code>true</code> if the named observable may match an
     * inclusion condition of the specification.
     */
    private boolean anyRuleMatch(String name, Map event_map) {
        EventConditions ecs = (EventConditions) event_map.get(name);

        Condition curCond;
        if (ecs != null) {
            curCond = ecs.anyInclusions();
        }
        else {
            curCond = Condition.DEFAULT_EXCLUDE;
        }

        int stopIndex = name.lastIndexOf('.');
        for (int i = 0; i <= stopIndex; i++) {
            if (name.charAt(i) == '.') {
                String s = name.substring(0, i);
                ecs = (EventConditions) event_map.get(s);

                if (ecs != null) {
                    Condition cond = (Condition) ecs.anyInclusions();
                    if (cond.rank > curCond.rank) {
                        curCond = cond;
                    }
                }
           }
        }

        return curCond.inclusion;
    }

    public void removeAllEvents(String className) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(new_events, false));

            for (int i = 0; i < 4; i++) {
                forAllClasses(new ChangeNameRuleAction(field_events[i],
                    false));
                forAllClasses(new ChangeNameRuleAction(call_events[i],
                    false));
                forAllClasses(new ChangeNameRuleAction(method_events[i],
                    false));
                forAllClasses(new ChangeNameRuleAction(monitor_events[i],
                    false));
            }

            forAllClasses(new ChangeNameRuleAction(static_init_events,
                false));

            for (int i = 0; i < 2; i++) {
                forAllClasses(new ChangeNameRuleAction(construct_events[i],
                    false));

                throwable_inc_subclass[i].clear();
                forAllThrowables(new ChangeNameRuleAction(throwable_events[i],
                    false));
            }
        }
        else {
            changeNameRule(className, new_events, false);

            for (int i = 0; i < 4; i++) {
                changeNameRule(className, field_events[i], false);
                changeNameRule(className, call_events[i], false);
                changeNameRule(className, method_events[i], false);
                changeNameRule(className, monitor_events[i], false);
            }

            changeNameRule(className, static_init_events, false);

            for (int i = 0; i < 2; i++) {
                changeNameRule(className, construct_events[i], false);

                throwable_inc_subclass[i].remove(className);
                changeNameRule(className, throwable_events[i], false);
            }
        }
    }

    //private static final void printMap(Map map) {
    //    Iterator i = map.keySet().iterator();
    //    while (i.hasNext()) {
    //        String key = (String) i.next();
    //        System.out.println();
    //        System.out.println("[ " + key + " ]");
    //        System.out.println(map.get(key));
    //    }
    //}

    /**
     * Creates a request for a new object allocation observable.
     *
     * @param className Name of a class or a package of classes for which
     * new object allocations (NEW instructions) are to be included in or
     * excluded from specification.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     *
     * @return A new object allocation request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    NewObjectRequest createNewObjectRequest(String className,
            boolean inclusion, int rank) {
        return new NewObjectRequest(className,
                                    new EventConditions(inclusion, rank));
    }

    /**
     * Adds a new object allocation observable to the specification.
     *
     * @param request New object allocation observable request submitted for
     * addition to the specification.
     */
    void addNewObjectRequest(NewObjectRequest request) {
        if (request.name().equals("*")) {
            forAllClasses(new AddNameRuleAction(new_events,
                request.conditions()));
        }
        else {
            addNameRule(request.name(), new_events, request.conditions());
        }
    }

    public boolean witnessNewObject(String newClass, MethodGen inMethod) {
        String inLoc = buildKeySignature(inMethod.getClassName(),
            inMethod.getName(), inMethod.getArgumentTypes());
        return checkNameRule(newClass, new_events, inLoc);
    }

    /**
     * Creates a request for a field observable.
     *
     * @param fieldName Name of a field or qualified package or class
     * name for which fields should be included or excluded.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     * @param fType Type of the observable field (read instance field,
     * write instance field, etc...).
     *
     * @return A new field request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    FieldRequest createFieldRequest(String fieldName, boolean inclusion,
            int rank, FieldType fType) {
        return new FieldRequest(fieldName, fType,
                                new EventConditions(inclusion, rank));
    }

    /**
     * Adds a field observable to the specification.
     *
     * @param request Field observable request submitted for addition to
     * the specification.
     */
    void addFieldRequest(FieldRequest request) {
        EventConditions ecs = request.conditions();
        int fieldType = request.fieldType();

        if (request.name().equals("*")) {
            forAllClasses(new AddNameRuleAction(field_events[fieldType], ecs));
        }
        else {
            addNameRule(request.name(), field_events[fieldType], ecs);
        }
    }

    // TODO: There is currently no support in EDL for specifying
    // instrumentation-based field events. A custom EventSpecification
    // class must be written if this is needed.
    // When EDL syntax is provided, it will be necessary to query
    // different condition trees for this mechanism, or to enhance the
    // trees to support modifier annotations.
    public boolean witnessField(FieldInstruction fi,
            ConstantPoolGen cpg, FieldType fType, MethodGen inMethod) {
        //int fieldType = fType.toInt();
        //String fullName = fi.getReferenceType(cpg).toString() + "." +
        //    fi.getFieldName(cpg);
        //String inLoc = buildKeySignature(inMethod.getClassName(),
        //    inMethod.getName(), inMethod.getArgumentTypes());

        return false;
    }

    public boolean witnessField(String fieldName, FieldType fType,
            com.sun.jdi.Type javaType, String className, String methodName,
            String methodSignature) {
        int fieldType = fType.toInt();
        String inLoc =
            buildKeySignature(className, methodName, methodSignature);
        return checkNameRule(fieldName, field_events[fieldType], inLoc);
    }

    public int witnessField(String fieldName, boolean isStatic,
            String javaType) {
        int retMask = 0;

        if (isStatic) {
            if (anyRuleMatch(fieldName, field_events[FieldType.IGETSTATIC])) {
                retMask |= FIELD_WITNESS_READ;
            }
            if (anyRuleMatch(fieldName, field_events[FieldType.IPUTSTATIC])) {
                retMask |= FIELD_WITNESS_WRITE;
            }
        }
        else {
            if (anyRuleMatch(fieldName, field_events[FieldType.IGETFIELD])) {
                retMask |= FIELD_WITNESS_READ;
            }
            if (anyRuleMatch(fieldName, field_events[FieldType.IPUTFIELD])) {
                retMask |= FIELD_WITNESS_WRITE;
            }
        }

        return retMask;
    }
    
    // TODO: There is currently no support in EDL for specifying
    // instrumentation-based field events. A custom EventSpecification
    // class must be written if this is needed.
    public int witnessField(String fieldName, boolean isStatic,
            Type javaType) {
        return 0;
    }

    public void addFieldEvent(String fieldName, FieldType fType) {
        changeNameRule(fieldName, field_events[fType.toInt()], true);
    }

    public void removeFieldEvent(String fieldName, FieldType fType) {
        changeNameRule(fieldName, field_events[fType.toInt()], false);
    }

    /**
     * Creates a request for a call observable.
     *
     * @param className Class on which the call is invoked.
     * @param methodName Name of the invoked method.
     * @param argTypes Arguments types to the method.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     * @param cType Type of the observable call (static, virtual, etc.).
     *
     * @return A new call request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    CallRequest createCallRequest(String className, String methodName,
            Type[] argTypes, boolean inclusion, int rank, CallType cType,
            boolean intercept) {
        return new CallRequest(className, methodName, argTypes, cType,
                               new EventConditions(inclusion, rank),
                               intercept);
    }

    /**
     * Adds a call observable to the specification.
     *
     * @param request Call observable request submitted for addition to
     * the specification.
     */
    void addCallRequest(CallRequest request) {
        EventConditions ecs = request.conditions();
        String className = request.name();
        String methodName = request.methodName();
        Type[] argTypes = request.argTypes();
        int callType = request.callType();

        if (className.equals("*")) {
            forAllClasses(new AddNameRuleAction(call_events[callType], ecs));

            if (request.intercept) {
                forAllClasses(new ClassAction() {
                    public boolean handleClass(String className) {
                        addProperty(className, "call:use_intercept", "T");
                        return true;
                    }});
            }
        }
        else {
            String keyString =
                buildKeySignature(className, methodName, argTypes);
            addNameRule(keyString, call_events[callType], ecs);

            if (request.intercept) {
                addProperty(keyString, "call:use_intercept", "T");
            }
        }
    }

    public boolean witnessCall(InvokeInstruction call, ConstantPoolGen cpg,
            MethodGen inMethod) {
        int callType = CallType.mapFromInstruction(call, cpg);

        String keyString = buildKeySignature(
            call.getReferenceType(cpg).toString(),
            call.getMethodName(cpg),
            call.getArgumentTypes(cpg));

        String inLoc = buildKeySignature(inMethod.getClassName(),
            inMethod.getName(), inMethod.getArgumentTypes());
        return checkNameRule(keyString, call_events[callType], inLoc);
    }

    public boolean useCallInterceptor(InvokeInstruction call,
            ConstantPoolGen cpg) {
        String keyString = buildKeySignature(
            call.getReferenceType(cpg).toString(),
            call.getMethodName(cpg),
            call.getArgumentTypes(cpg));

        String propVal = getProperty(keyString, "call:use_intercept");
        if (propVal == null) {
            return false;
        }
        else {
            return (propVal.equals("T"));
        }
    }

    /**
     * Creates a request for a constructor entry observable.
     *
     * <p>This method does not return a request object to which conditions
     * can be added, since events associated with this type of observable can
     * only be raised in one location (per constructor).</p>
     *
     * @param className Name of a class or qualified package for which
     * entry into the object constructor(s) should be included or excluded.
     * @param argTypes Argument types to the constructor.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     */
    void addConstructorEntryRequest(String className, Type[] argTypes,
            boolean inclusion, int rank) {
        if (className.equals("*")) {
            forAllClasses(new AddNameRuleAction(construct_events[CNS_ENTER],
                new EventConditions(inclusion, rank)));
        }
        else {
            addNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_ENTER],
                new EventConditions(inclusion, rank));
        }
    }

    public boolean witnessConstructorEntry(MethodGen mg) {
        String keyString = buildKeySignature(
            mg.getClassName(),
            mg.getName(),
            mg.getArgumentTypes());

        return checkNameRule(keyString, construct_events[CNS_ENTER], "");
    }

    public void addConstructorEntry(String className, Type[] argTypes) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                construct_events[CNS_ENTER], true));
        }
        else {
            changeNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_ENTER], true);
        }
        //printMap(method_events[MethodAction.IVIRTUAL_ENTER]);
    }

    public void removeConstructorEntry(String className, Type[] argTypes) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                construct_events[CNS_ENTER], false));
        }
        else {
            changeNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_ENTER], false);
        }
    }

    /**
     * Creates a request for a constructor exit observable.
     *
     * <p>This method does not return a request object to which conditions
     * can be added, since events associated with this type of observable can
     * only be raised in one location (per constructor).</p>
     *
     * @param className Name of a class or qualified package for which
     * exit from the object constructor(s) should be included or excluded.
     * @param argTypes Argument types to the constructor.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     */
    void addConstructorExitRequest(String className, Type[] argTypes,
            boolean inclusion, int rank) {
        if (className.equals("*")) {
            forAllClasses(new AddNameRuleAction(construct_events[CNS_EXIT],
                new EventConditions(inclusion, rank)));
        }
        else {
            addNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_EXIT],
                new EventConditions(inclusion, rank));
        }
    }

    public boolean witnessConstructorExit(MethodGen mg) {
        String keyString = buildKeySignature(
            mg.getClassName(),
            mg.getName(),
            mg.getArgumentTypes());

        return checkNameRule(keyString, construct_events[CNS_EXIT], "");
    }

    public void addConstructorExit(String className, Type[] argTypes) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                construct_events[CNS_EXIT], true));
        }
        else {
            changeNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_EXIT], true);
        }
        //printMap(method_events[MethodAction.IVIRTUAL_ENTER]);
    }

    public void removeConstructorExit(String className, Type[] argTypes) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                construct_events[CNS_EXIT], false));
        }
        else {
            changeNameRule(buildKeySignature(className, "<init>", argTypes),
                construct_events[CNS_EXIT], false);
        }
    }

    /**
     * Creates a request for a method entry or exit observable.
     *
     * <p>This method does not return a request object to which conditions
     * can be added, since events associated with this type of observable can
     * only be raised in one location (per method).</p>
     *
     * @param className Name of a class or qualified package or class for which
     * method entry or exit should be included or excluded.
     * @param methodName Name of the entered or exited method.
     * @param argTypes Argument types to the method.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     * @param mAct Type-safe enumeration specifying the type of method
     * (static, virtual) and event (entry, exit) being added.
     */
    void addMethodChangeRequest(String className, String methodName,
            Type[] argTypes, boolean inclusion, int rank, MethodAction mAct) {
        if (className.equals("*")) {
            forAllClasses(new AddNameRuleAction(method_events[mAct.toInt()],
                new EventConditions(inclusion, rank)));
        }
        else {
            addNameRule(buildKeySignature(className, methodName, argTypes),
                method_events[mAct.toInt()],
                new EventConditions(inclusion, rank));
        }
    }

    public boolean witnessMethodEntry(MethodGen mg) {
        String keyString = buildKeySignature(
            mg.getClassName(),
            mg.getName(),
            mg.getArgumentTypes());
        int actionIndex = (mg.isStatic()) ? MethodAction.ISTATIC_ENTER
                                          : MethodAction.IVIRTUAL_ENTER;

        return checkNameRule(keyString, method_events[actionIndex], "");
    }

    public boolean witnessMethodEntry(String className, Method method) {
        String keyString = buildKeySignature(
            className,
            method.name(),
            method.signature());
        int actionIndex = (method.isStatic()) ? MethodAction.ISTATIC_ENTER
                                              : MethodAction.IVIRTUAL_ENTER;

        return checkNameRule(keyString, method_events[actionIndex], "");
    }

    public boolean witnessMethodExit(MethodGen mg) {
        String keyString = buildKeySignature(
            mg.getClassName(),
            mg.getName(),
            mg.getArgumentTypes());
        int actionIndex = (mg.isStatic()) ? MethodAction.ISTATIC_EXIT
                                          : MethodAction.IVIRTUAL_EXIT;

        return checkNameRule(keyString, method_events[actionIndex], "");
    }

    public void addMethodEvent(String className, String methodName,
            Type[] argTypes, MethodAction mAct) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                method_events[mAct.toInt()], true));
        }
        else {
            changeNameRule(buildKeySignature(className, methodName, argTypes),
                method_events[mAct.toInt()], true);
        }
        //printMap(method_events[MethodAction.IVIRTUAL_ENTER]);
    }

    public void removeMethodEvent(String className, String methodName,
            Type[] argTypes, MethodAction mAct) {
        if (className.equals("*")) {
            forAllClasses(new ChangeNameRuleAction(
                method_events[mAct.toInt()], false));
        }
        else {
            changeNameRule(buildKeySignature(className, methodName, argTypes),
                method_events[mAct.toInt()], false);
        }
    }

    /**
     * Creates a request for a monitor observable.
     *
     * @param className Name of the class or qualified package for
     * which activities relating to the monitor(s) owned by object
     * instances should be included or excluded.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     * @param mType Type of the monitor action (contend, acquire, etc.).
     *
     * @return A new monitor request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    MonitorRequest createMonitorRequest(String className, boolean inclusion,
            int rank, MonitorType mType) {
        return new MonitorRequest(className, mType,
                                  new EventConditions(inclusion, rank));
    }

    /**
     * Adds a monitor observable to the specification.
     *
     * @param request Monitor observable request submitted for addition to
     * the specification.
     */
    void addMonitorRequest(MonitorRequest request) {
        EventConditions ecs = request.conditions();
        String className = request.name();
        int monitorType = request.monitorType();

        if (className.equals("*")) {
            forAllClasses(
                new AddNameRuleAction(monitor_events[monitorType], ecs));
        }
        else {
            addNameRule(className, monitor_events[monitorType], ecs);
        }
    }

    public boolean witnessMonitor(String className, MonitorType mType) {
        int monitorType = mType.toInt();

        return checkNameRule(className, monitor_events[monitorType], "");
    }

    public boolean witnessAnyMonitor(MonitorType mType, MethodGen inMethod) {
        int monitorType = mType.toInt();

        String inLoc = buildKeySignature(inMethod.getClassName(),
            inMethod.getName(), inMethod.getArgumentTypes());

        int eventCount = monitor_events[monitorType].size();
        Iterator iterator = monitor_events[monitorType].keySet().iterator();
        for (int i = eventCount; i-- > 0; ) {
            String key = (String) iterator.next();
            if (checkNameRule(key, monitor_events[monitorType], inLoc)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a request for a thrown exception observable.
     *
     * @param exceptionClass Name of the class to be considered an
     * observable when thrown.
     * @param includeSubclasses Flag specifying whether subclasses of
     * <code>exceptionClass</code> should also be included as observables.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     *
     * @return A new throw request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    ThrowRequest createThrowRequest(String exceptionClass,
            boolean includeSubclasses, boolean inclusion, int rank) {
        if (includeSubclasses) {
            throwable_inc_subclass[THROW].add(exceptionClass);
        }

        return new ThrowRequest(exceptionClass,
            new EventConditions(inclusion, rank));
    }

    /**
     * Adds a thrown exception observable to the specification.
     *
     * @param request Throw exception observable request submitted for addition
     * to the specification.
     */
    void addThrowRequest(ThrowRequest request) {
        if (request.name().equals("*")) {
            forAllThrowables(new AddNameRuleAction(throwable_events[THROW],
                request.conditions()));
            //addAllThrowables(throwable_events[THROW], request.conditions());
        }
        else {
            addNameRule(request.name(), throwable_events[THROW],
                request.conditions());
        }
    }

    public boolean witnessThrow(String exceptionClass, String className,
            String methodName, String methodSignature) {
        String inLoc = buildKeySignature(className, methodName,
            methodSignature);
        return checkThrowableRule(exceptionClass, THROW, inLoc);
    }

    public boolean witnessThrow(String exceptionClass) {
        return checkThrowableRule(exceptionClass, THROW, "");
    }

    /**
     * Creates a request for a caught exception observable.
     *
     * @param exceptionClass Name of the class to be considered an
     * observable when caught.
     * @param includeSubclasses Flag specifying whether subclasses of
     * <code>exceptionClass</code> should also be included as observables.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     *
     * @return A new catch request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    CatchRequest createCatchRequest(String exceptionClass,
            boolean includeSubclasses, boolean inclusion, int rank) {
        if (includeSubclasses) {
            throwable_inc_subclass[CATCH].add(exceptionClass);
        }

        return new CatchRequest(exceptionClass,
            new EventConditions(inclusion, rank));
    }

    /**
     * Adds a caught exception observable to the specification.
     *
     * @param request Catch exception observable request submitted for addition
     * to the specification.
     */
    void addCatchRequest(CatchRequest request) {
        if (request.name().equals("*")) {
            forAllThrowables(new AddNameRuleAction(throwable_events[CATCH],
                request.conditions()));
        }
        else {
            addNameRule(request.name(), throwable_events[CATCH],
                request.conditions());
        }
    }

    public boolean witnessCatch(String exceptionClass, MethodGen inMethod) {
        String inLoc = buildKeySignature(inMethod.getClassName(),
            inMethod.getName(), inMethod.getArgumentTypes());
        return checkThrowableRule(exceptionClass, CATCH, inLoc);
    }

    public boolean witnessCatch(String exceptionClass) {
        return checkThrowableRule(exceptionClass, CATCH, "");
    }

    /**
     * Creates a request for a static initializer entry observable.
     *
     * <p>This method does not return a request object to which conditions
     * can be added, since events associated with this type of observable can
     * only be raised in one location (per initializer).</p>
     *
     * @param className Name of a class or qualified package or class for which
     * entry into the static initializer(s) should be included or excluded.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     */
    void addStaticInitializerEntryRequest(String className, boolean inclusion,
            int rank) {
        if (className.equals("*")) {
            forAllClasses(new AddNameRuleAction(static_init_events,
                new EventConditions(inclusion, rank)));
        }
        else {
            addNameRule(className, static_init_events,
                        new EventConditions(inclusion, rank));
        }
    }

    public boolean witnessStaticInitializerEntry(String className) {
        return checkNameRule(className, static_init_events, "");
    }
    
    /**
     * Creates a request for an array element observable.
     *
     * @param bounds Bounds on the indexes of the array elements for
     * which access/store events are to be included or excluded.
     * @param inclusion Specifies whether the rule is an inclusion or
     * exclusion rule.
     * @param rank The rank, or precedence, of the rule.
     * @param actionType Type of the array element event (access or store).
     *
     * @return A new array element request object that can be used
     * to add conditions constraining where the observable is to be
     * witnessed.
     */
    ArrayElementRequest createArrayElementRequest(
            ArrayElementBounds bounds, boolean inclusion,
            int rank, ArrayElementType actionType) {
        EventConditions ecs = new EventConditions(inclusion, rank);
        return new ArrayElementRequest(actionType, ecs, bounds);
    }
    
    /**
     * Adds an array element observable to the specification.
     *
     * @param request Array element observable request submitted for addition
     * to the specification.
     */
    void addArrayElementRequest(ArrayElementRequest request) {
        int actionType = request.actionType();
        array_elem_conditions[actionType].addTypeConditions(
            request.bounds(), request.conditions());
    }

    public boolean witnessArrayElement(ArrayInstruction ai,
            ConstantPoolGen cpg, MethodGen inMethod,
            ArrayElementType elemActionType,
            List<ArrayElementBounds> witnessed) {
        Type javaType = ai.getType(cpg);
        String inLoc = buildKeySignature(inMethod.getClassName(),
            inMethod.getName(), inMethod.getArgumentTypes());
        int actionType = elemActionType.toInt();

        return array_elem_conditions[actionType]
            .checkConditions(javaType, inLoc, witnessed);
    }

    public void serialize(DataOutputStream stream) throws IOException {
        stream.writeUTF(specKey);
        
        stream.writeInt(systemUnits.size());
        for (Iterator i = systemUnits.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }

        stream.writeInt(moduleUnits.size());
        for (Iterator i = moduleUnits.iterator(); i.hasNext(); ) {
            serializeProgramUnit(stream, (ProgramUnit) i.next());
        }
        
        if (globals != null) {
            stream.writeByte(1);
            globals.serialize(stream);
        }
        else {
            stream.writeByte(0);
        }

        serializeRuleMap(stream, new_events);

        for (int i = 0; i < 4; i++) {
            serializeRuleMap(stream, field_events[i]);
        }

        for (int i = 0; i < 4; i++) {
            serializeRuleMap(stream, call_events[i]);
        }

        for (int i = 0; i < 2; i++) {
            serializeRuleMap(stream, construct_events[i]);
        }

        for (int i = 0; i < 4; i++) {
            serializeRuleMap(stream, method_events[i]);
        }

        for (int i = 0; i < 4; i++) {
            serializeRuleMap(stream, monitor_events[i]);
        }

        for (int i = 0; i < 2; i++) {
            serializeRuleMap(stream, throwable_events[i]);
        }

        for (int i = 0; i < 2; i++) {
            serializeStrings(stream, throwable_inc_subclass[i]);
        }

        serializeRuleMap(stream, static_init_events);
        
        for (int i = 0; i < 2; i++) {
            array_elem_conditions[i].serialize(stream);
        }

        stream.writeInt(event_properties.size());
        for (Iterator i = event_properties.keySet().iterator(); i.hasNext(); ) {
            String key = (String) i.next();
            Map props = (Map) event_properties.get(key);

            stream.writeUTF(key);
            stream.writeInt(props.size());
            for (Iterator p = props.keySet().iterator(); p.hasNext(); ) {
                String propName = (String) p.next();
                stream.writeUTF(propName);
                stream.writeUTF((String) props.get(propName));
            }
        }
    }

    private void serializeRuleMap(DataOutputStream stream, Map<Object, Object> map)
            throws IOException {
        stream.writeInt(map.size());
        Iterator iterator = map.keySet().iterator();
        for (int i = map.size(); i-- > 0; ) {
            String key = (String) iterator.next();
            stream.writeUTF(key);
            EventConditions ecs = (EventConditions) map.get(key);
            ecs.serialize(stream);
        }
    }

    @SuppressWarnings("unchecked")
    public EventSpecification deserialize(DataInputStream stream)
            throws IOException {
        this.specKey = stream.readUTF();
        
        int size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.systemUnits.add(deserializeProgramUnit(stream));
        }

        size = stream.readInt();
        for (int i = 0; i < size; i++) {
            this.moduleUnits.add(deserializeProgramUnit(stream));
        }

        if (stream.readByte() == 1) {
            this.globals = new GlobalConstraints();
            this.globals.deserialize(stream);
        }
        
        this.new_events = deserializeRuleMap(stream);

        for (int i = 0; i < 4; i++) {
            this.field_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 4; i++) {
            this.call_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 2; i++) {
            this.construct_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 4; i++) {
            this.method_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 4; i++) {
            this.monitor_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 2; i++) {
            this.throwable_events[i] = deserializeRuleMap(stream);
        }

        for (int i = 0; i < 2; i++) {
            this.throwable_inc_subclass[i] =
                (Set<String>) deserializeStrings(stream, new THashSet());
        }

        this.static_init_events = deserializeRuleMap(stream);

        for (int i = 0; i < 2; i++) {
            ArrayElementConditions aec = new ArrayElementConditions(i);
            aec.deserialize(stream);
        }
        
        this.event_properties = new THashMap();
        size = stream.readInt();
        for (int i = 0; i < size; i++) {
            String key = stream.readUTF();
            Map<Object, Object> properties = new THashMap();
            this.event_properties.put(key, properties);

            int propCount = stream.readInt();
            for (int p = 0; p < propCount; p++) {
                properties.put(stream.readUTF(), stream.readUTF());
            }
        }

        initModuleClassData();

        return this;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> deserializeRuleMap(DataInputStream stream)
            throws IOException {
        Map<Object, Object> rule_map = new THashMap();

        int size = stream.readInt();
        for (int i = size; i-- > 0; ) {
            String key = stream.readUTF();
            EventConditions ecs = EventConditions.deserialize(stream);
            rule_map.put(key, ecs);
        }

        return rule_map;
    }

    abstract static class EGEventRequest {
        protected String name;
        protected EventConditions conditions;

        protected EGEventRequest() { }

        String name() {
            return name;
        }

        EventConditions conditions() {
            return conditions;
        }
    }

    final static class NewObjectRequest extends EGEventRequest {
        private NewObjectRequest() {
            throw new AssertionError("Illegal constructor");
        }

        NewObjectRequest(String className, EventConditions ecs) {
            this.name = className;
            this.conditions = ecs;
        }
    }

    final static class FieldRequest extends EGEventRequest {
        private final int fieldType;

        private FieldRequest() {
            throw new AssertionError("Illegal constructor");
        }

        FieldRequest(String fieldName, FieldType fieldType,
                     EventConditions ecs) {
            this.name = fieldName;
            this.conditions = ecs;
            this.fieldType = fieldType.toInt();
        }

        int fieldType() {
            return fieldType;
        }
    }

    final static class CallRequest extends EGEventRequest {
        private final String methodName;
        private final Type[] argTypes;
        private final int callType;
        private final boolean intercept;

        private CallRequest() {
            throw new AssertionError("Illegal constructor");
        }

        CallRequest(String className, String methodName, Type[] argTypes,
                CallType callType, EventConditions ecs, boolean intercept) {
            this.name = className;
            this.methodName = methodName;
            this.argTypes = argTypes;
            this.conditions = ecs;
            this.callType = callType.toInt();
            this.intercept = intercept;
        }

        String methodName() {
            return methodName;
        }

        Type[] argTypes() {
            return argTypes;
        }

        int callType() {
            return callType;
        }

        boolean intercept() {
            return intercept;
        }
    }

    final static class MonitorRequest extends EGEventRequest {
        private final int monitorType;

        private MonitorRequest() {
            throw new AssertionError("Illegal constructor");
        }

        MonitorRequest(String className, MonitorType monitorType,
                EventConditions ecs) {
            this.name = className;
            this.conditions = ecs;
            this.monitorType = monitorType.toInt();
        }

        int monitorType() {
            return monitorType;
        }
    }

    final static class ThrowRequest extends EGEventRequest {
        private ThrowRequest() {
            throw new AssertionError("Illegal constructor");
        }

        ThrowRequest(String exceptionName, EventConditions ecs) {
            this.name = exceptionName;
            this.conditions = ecs;
        }
    }

    final static class CatchRequest extends EGEventRequest {
        private CatchRequest() {
            throw new AssertionError("Illegal constructor");
        }

        CatchRequest(String exceptionName, EventConditions ecs) {
            this.name = exceptionName;
            this.conditions = ecs;
        }
    }
    
    final static class ArrayElementRequest extends EGEventRequest {
        private final Type javaType;
        private final int actionType;
        private final ArrayElementBounds bounds;
        
        private ArrayElementRequest() {
            throw new AssertionError("Illegal constructor");
        }
        
        ArrayElementRequest(ArrayElementType actionType,
                EventConditions ecs, ArrayElementBounds bounds) {
            this.name = bounds.javaType.getSignature();
            this.conditions = ecs;
            this.javaType = bounds.javaType;
            this.actionType = actionType.toInt();
            this.bounds = bounds;
        }
        
        Type javaType() {
            return javaType;
        }
        
        int actionType() {
            return actionType;
        }
        
        ArrayElementBounds bounds() {
            return bounds;
        }
    }

    static final class EventConditions {
        private final ConditionTree conditions;

        EventConditions(boolean inclusion, int rank) {
            this.conditions = new ConditionTree(inclusion, rank);
        }

        private EventConditions(EventConditions ecs) {
            this.conditions = ecs.conditions.copy();
        }

        private EventConditions(ConditionTree conditions) {
            this.conditions = conditions;
        }

        void addInCondition(String className, String methodName,
                Type[] argTypes, boolean inclusion, int rank) {
            conditions.addNode(
                buildKeySignature(className, methodName, argTypes),
                new InNode(inclusion, rank));
        }

        void addNotCondition(String className, String methodName,
                Type[] argTypes, boolean inclusion, int rank) {
            conditions.addNode(
                buildKeySignature(className, methodName, argTypes),
                new NotNode(inclusion, rank));
        }

        Condition checkConditions(String key) {
            return conditions.checkConditions(key);
        }

        Condition anyInclusions() {
            return conditions.anyInclusions();
        }

        void merge(EventConditions ecs) {
            conditions.merge(ecs.conditions);
        }

        void merge(RootNode newRoot, List nodeKeys, List conditionNodes,
                boolean force) {
            conditions.merge(newRoot, nodeKeys, conditionNodes, force);
        }

        void clear(RootNode newRoot) {
            conditions.clear(newRoot);
        }

        void serialize(DataOutputStream stream) throws IOException {
            conditions.serialize(stream);
        }

        static EventConditions deserialize(DataInputStream stream)
                throws IOException {
            ConditionTree conditions = ConditionTree.deserialize(stream);
            return new EventConditions(conditions);
        }

        public String toString() {
            return conditions.toString();
        }
    }
    
    @SuppressWarnings("unchecked")
    final class ArrayElementConditions {
        private final int actionType;
        
        private boolean wildcardIsSet = false;
        private Map<Type, EventConditions> typeConditions = new THashMap();
        private Map<Type, ArrayElementBounds> typeBounds = new THashMap();
        
        private static final boolean ASSERTS = true;
        
        private ArrayElementConditions() {
            throw new AssertionError("Illegal constructor");
        }
        
        private ArrayElementConditions(int actionType) {
            this.actionType = actionType;
        }
        
        void addTypeConditions(ArrayElementBounds bounds,
                EventConditions ecs) {
            if (typeConditions.containsKey(bounds.javaType)) {
                throw new UnsupportedOperationException("EDL currently " +
                    "only supports a single array element request " +
                    "per type");
            }
            
            if (bounds.javaType == TYPE_ANY) {
                clear();
                wildcardIsSet = true;
            }
            else {
                wildcardIsSet = false;
            }
            
            typeConditions.put(bounds.javaType, new EventConditions(ecs));
            typeBounds.put(bounds.javaType, bounds.copy());
        }

        boolean checkConditions(Type javaType, String key,
                List<ArrayElementBounds> results) {
            if (DEBUG) {
                System.out.println("EDLSpec:2085:javaType=" + javaType);
                System.out.println("EDLSpec:2085:key=" + key);
            }
            
            results.clear();
            
            boolean resultFlag = false;
            
            toReturn:
            if (Type.OBJECT.equals(javaType)) {
                EventConditions ecs;
                
                if (wildcardIsSet) {
                    ecs = typeConditions.get(TYPE_ANY);
                    
                    Condition cond = ecs.checkConditions(key);
                    
                    if (cond.inclusion) {
                        ArrayElementBounds resultBounds =
                            typeBounds.get(TYPE_ANY);

                        if (ASSERTS) {
                            assert resultBounds != null;
                        }
                        
                        ArrayElementBounds outBounds = resultBounds.copy();
                        results.add(outBounds);
                        
                        if (globals == null) {
                            resultFlag = true;
                            break toReturn;
                        }
                        
                        // Need to scan globals and set conditions as
                        // appropriate
                        int size =
                            globals.arrayElementBounds[actionType].size();
                        Iterator<ArrayElementBounds> iter =
                            globals.arrayElementBounds[actionType]
                                .values().iterator();
                        for (int i = size; i-- > 0; ) {
                            ArrayElementBounds globalBounds = iter.next();
                            if (TYPE_ANY.equals(globalBounds.javaType)) {
                                checkGlobals(TYPE_ANY, outBounds);
                            }
                            else if (globalBounds.javaType
                                    instanceof ReferenceType) {
                                // Arrays of primitives cannot be accessed
                                // through an Object[] reference
                                results.add(globalBounds.copy());
                            }
                        }
                        
                        resultFlag = true;
                    }
                }
                else {
                    Set<Object> processed = new THashSet();
                    boolean specWildcardExists =
                        typeConditions.containsKey(TYPE_ANY);
                    
                    // Add the known spec-specific types to the result list,
                    // merging any global bounds as we go
                    int size = typeConditions.size();
                    Iterator<Type> iter = typeConditions.keySet().iterator();
                    for (int i = size; i-- > 0; ) {
                        Type curType = iter.next();
                        
                        // Arrays of primitives cannot be accessed through
                        // an Object[] reference
                        if (curType != TYPE_ANY) {
                            if (!(curType instanceof ReferenceType)) {
                                continue;
                            }
                        }
                        
                        EventConditions curEcs = typeConditions.get(curType);
                        Condition cond = curEcs.checkConditions(key);
                        
                        if (cond.inclusion) {
                            ArrayElementBounds resultBounds =
                                typeBounds.get(curType);

                            if (ASSERTS) {
                                assert resultBounds != null;
                            }
                            
                            ArrayElementBounds outBounds = resultBounds.copy();
                            results.add(outBounds);
                            
                            if (globals == null) {
                                continue;
                            }
                            
                            if (checkGlobals(curType, outBounds)) {
                                processed.add(curType);
                            }
                        }
                    }
                    
                    // Now, if the wildcard was set in the specification,
                    // add any global constraints that might exist that
                    // didn't match spec-specific types
                    if (globals != null) {
                        size = globals.arrayElementBounds[actionType].size();
                        Iterator<ArrayElementBounds> boundsIter =
                            globals.arrayElementBounds[actionType]
                               .values().iterator();
                        for (int i = size; i-- > 0; ) {
                            ArrayElementBounds globalBounds =
                                boundsIter.next();
                            
                            if (processed.contains(globalBounds.javaType)) {
                                continue;
                            }
                            else if (specWildcardExists) {
                                Type t = globalBounds.javaType;
                                if ((t == TYPE_ANY) ||
                                        (t instanceof ReferenceType)) {
                                    results.add(globalBounds.copy());
                                }
                            }
                        }
                    }
                    
                    resultFlag = results.size() > 0;
                }
            }
            else {
                boolean typeMatch;
                EventConditions ecs;
                
                if (wildcardIsSet) {
                    ecs = typeConditions.get(TYPE_ANY);
                    typeMatch = false;
                }
                else {
                    ecs = typeConditions.get(javaType);
                    if (ecs == null) {
                        ecs = typeConditions.get(TYPE_ANY);
                        if (ecs == null) {
                            break toReturn;
                        }
                        else {
                            typeMatch = false;
                        }
                    }
                    else {
                        typeMatch = true;
                    }
                }
                
                Condition cond = ecs.checkConditions(key);
                
                if (cond.inclusion) {
                    ArrayElementBounds resultBounds = (typeMatch) ?
                        typeBounds.get(javaType) : typeBounds.get(TYPE_ANY);

                    if (ASSERTS) {
                        assert resultBounds != null;
                    }
                    
                    ArrayElementBounds outBounds = resultBounds.copy();
                    results.add(outBounds);
                    
                    if (globals != null) {
                        boolean globalTypeMatch =
                            checkGlobals(javaType, outBounds);
                        if (globalTypeMatch) {
                            outBounds.javaType = javaType;
                            typeMatch = true;
                        }
                    }
                    
                    resultFlag = true;
                }
            }
            
            if (DEBUG) {
                System.out.println("EDLSpec:2267:Flag: " + resultFlag);
                System.out.println("EDLSpec:2268:Matches:\n" + results);
            }
            
            return resultFlag;
        }
        
        private final boolean checkGlobals(Type javaType,
                ArrayElementBounds outBounds) {
            ArrayElementBounds specBounds = (ArrayElementBounds)
            globals.arrayElementBounds[actionType].get(javaType);
            if (specBounds == null) {
                specBounds = (ArrayElementBounds)
                    globals.arrayElementBounds[actionType].get(TYPE_ANY);
                if (specBounds != null) {
                    outBounds.mergeGlobal(specBounds);
                }
            }
            else {
                outBounds.mergeGlobal(specBounds);
                return true;
            }
            
            return false;
        }

        void clear() {
            typeConditions.clear();
            typeBounds.clear();
        }

        void serialize(DataOutputStream stream) throws IOException {
            stream.writeBoolean(wildcardIsSet);
            
            int size = typeConditions.size();
            stream.writeInt(size);
            Iterator<Type> condIter = typeConditions.keySet().iterator();
            for (int i = size; i-- > 0; ) {
                Type javaType = condIter.next();
                stream.writeUTF(javaType.getSignature());
                typeConditions.get(javaType).serialize(stream);
                typeBounds.get(javaType).serialize(stream);
            }
        }

        void deserialize(DataInputStream stream)
                throws IOException {
            wildcardIsSet = stream.readBoolean();
            
            int size = stream.readInt();
            for (int i = size; i-- > 0; ) {
                String typeSig = stream.readUTF();
                Type javaType;
                if (typeSig.charAt(0) == '@') {
                    // Ideally, we could define getSignature() and
                    // Type.getType(String) commutatively, but there's
                    // nothing we can do to change Type's static method
                    javaType = SemanticConstants.TYPE_ANY;
                }
                else {
                    javaType = Type.getType(typeSig);
                }
                typeConditions.put(javaType,
                    EventConditions.deserialize(stream));
                ArrayElementBounds bounds = new ArrayElementBounds();
                bounds.deserialize(stream);
                typeBounds.put(javaType, bounds);
            }
        }
    }
}

