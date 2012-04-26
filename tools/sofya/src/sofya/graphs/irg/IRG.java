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

package sofya.graphs.irg;

import java.util.*;
import java.io.*;

import sofya.base.*;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;

import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;

/* Once constructed, an IRG is immutable. Primarily to enforce this
 * immutability, this class does not extend from the Graph class
 * defined in the sofya.graphs package, since it is impossible to
 * reconstitute a graph where nodes reference other nodes (as done in
 * CFGs) from a file in a handler without exposing mutating methods
 * in the public interface of the class. An IRG also lacks any
 * notion of explicit edges.
 */

/**
 * Class which represents an Interclass Relation Graph for a given set
 * of Java classes, as described by Orso, Shi, and Harrold (<i>Scaling
 * Regression Testing to Large Software Systems</i>), extended to
 * additionally support Java interfaces. This is effectively a class
 * hierarchy graph which supports the modeling of class relationships
 * introduced by the semantics of Java interfaces.
 *
 * @author Alex Kinneer
 * @version 01/15/2007
 *
 * @see sofya.graphs.irg.IRGHandler
 */
public class IRG {
    /** Maps class names to indices used for internal storage and
        serialization to file. Using numeric indices greatly improves
        both file and memory space efficiency. */
    private String[] nameTable = null;
    /** Maps class names to class relations data nodes. */
    private Map<Object, Object> classDataMap = null;
    /** Flag specifying whether to compute used classes in the graph.
        Setting to <code>true</code> makes the IRG consistent with the
        data structure described by Orso, Shi, and Harrold. */
    private boolean findUses = false;

    /** Special index indicating that a class has no superclass. This is
        true for interfaces and <code>java.lang.Object</code> (interfaces
        support direct multiple inheritance, thus subinterfaces do not
        have direct references to their superinterfaces in the graph. They
        do however implement their superinterface(s), so they appear in
        their superinterfaces' implementors list(s)). */
    public static final int NO_SUPERCLASS = 0x0000FFFF;

    /** Maximum number of entries permitted in the name table. */
    public static final int MAX_NAME_TABLE_SIZE = 65534;

    /** Identifier returned if the superclass is requested for the primordial
        class (<code>java.lang.Object</code>). */
    public static final String BASE_ID = "<base class>";
    /** Identifier returned if the superclass is requested for a class outside
        of the set of classes for which the IRG was constructed. */
    public static final String UNDEF_ID = "<undefined>";


    /**************************************************************************
     * Constructs an interclass relation graph from the given name/index
     * table and set of class relations data nodes.
     *
     * <p>This constructor is for use by
     * {@link sofya.handlers.IRGHandler}.</p>
     *
     * @param nameTable Table mapping class names to their internal
     * numeric indices.
     * @param cdm Map of class names to their class relations data nodes.
     */
    IRG(String[] nameTable, Map<Object, Object> cdm) {
        this.nameTable = nameTable;
        classDataMap = cdm;
    }

    /**************************************************************************
     * Constructs an interclass relation graph from the list of classes
     * specified by the given &apos;<code>.prog</code>&apos; file.
     *
     * @param progFile A &apos;<code>.prog</code>&apos; file in the
     * database containing the list of classes for which the graph
     * is to be built.
     * @param tag Database tag associated with the file. May be
     * <code>null</code>, in which case the file is assumed to be
     * located in the root database directory.
     *
     * @throws FileNotFoundException If a class named in the &apos;prog&apos;
     * file cannot be found.
     * @throws IOException If an error occurs while attempting to read an
     * input class for analysis.
     * @throws ClassFormatError If an input class is not a valid Java class.
     */
    @SuppressWarnings("unchecked")
    public IRG(String progFile, String tag)
           throws FileNotFoundException, IOException, ClassFormatError {
        List<ProgramUnit> programUnits = new ArrayList<ProgramUnit>();
        Handler.readProgFile(progFile, tag, programUnits);

        List<String> classList = new ArrayList<String>();
        int size = programUnits.size();
        Iterator iterator = programUnits.iterator();
        for (int i = size; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) iterator.next();
            classList.addAll(pUnit.classes);
        }

        classDataMap = new THashMap();
        buildIRG(classList);
    }

    /**************************************************************************
     * Constructs an interclass relation graph from a list of classes.
     *
     * @param classList List of classes for which to construct an interclass
     * relation graph.
     *
     * @throws IOException If an error occurs while attempting to read an
     * input class for analysis.
     * @throws ClassFormatError If an input class is not a valid Java class.
     */
    public IRG(Collection<String> classList)
            throws IOException, ClassFormatError {
        this(classList, false);
    }

    /**************************************************************************
     * Constructs an interclass relation graph from a list of classes.
     *
     * @param classList List of classes for which to construct an interclass
     * relation graph.
     * @param findUses Flag specifying whether to compute used classes in
     * the graph. Setting to <code>true</code> makes the IRG consistent with
     * the data structure described by Orso, Shi, and Harrold.
     *
     * @throws IOException If an error occurs while attempting to read an
     * input class for analysis.
     * @throws ClassFormatError If an input class is not a valid Java class.
     */
    @SuppressWarnings("unchecked")
    public IRG(Collection<String> classList, boolean findUses)
            throws IOException, ClassFormatError {
        this.findUses = findUses;
        classDataMap = new THashMap();
        buildIRG(classList);
    }

    /**************************************************************************
     * Builds the interclass relation graph.
     *
     * @param classList List of fully qualified class names from which the
     * IRG will be built. Class relationship data will only be computed for
     * those classes found in this list, although superclasses
     * &apos;external&apos; to the list will be properly identified.
     *
     * @throws IOException If an error occurs while attempting to read an
     * input class for analysis.
     * @throws ClassFormatError If an input class is not a valid Java class.
     */
    @SuppressWarnings("unchecked")
    protected void buildIRG(Collection<String> classList)
            throws IOException, ClassFormatError {
        TObjectIntHashMap nameIndexMap = new TObjectIntHashMap();
        int nextNameIndex = 0;

        // Keep a copy of the input class list. Used among other things to
        // screen out 'external' classes when calculating 'use' dependencies
        Set<String> inputClasses = new THashSet();
        Iterator<String> li = classList.iterator();
        int size = classList.size();
        for (int i = size; i-- > 0; ) {
            inputClasses.add(li.next());
        }

        for (Iterator names = inputClasses.iterator(); names.hasNext(); ) {
            JavaClass clazz = loadClass((String) names.next());
            String className = clazz.getClassName();
            String[] interfaces = clazz.getInterfaceNames();
            String superclassName = clazz.getSuperclassName();

            int classIndex, superclassIndex;
            ClassNode clNode = null;

            // Create mappings to numeric indices for the class and
            // superclass if necessary, or retrive the existing mappings
            if (!nameIndexMap.containsKey(className)) {
                classIndex = nextNameIndex++;
                nameIndexMap.put(className, classIndex);
            }
            else {
                classIndex = nameIndexMap.get(className);
            }

            if (clazz.isInterface()) {
                superclassIndex = NO_SUPERCLASS;
            }
            else {
                if (!nameIndexMap.containsKey(superclassName)) {
                    superclassIndex = nextNameIndex++;
                    nameIndexMap.put(superclassName, superclassIndex);
                }
                else {
                    superclassIndex = nameIndexMap.get(superclassName);
                }
            }

            // If a class relationship data node already exists for this class,
            // retrieve it and set its superclass correctly, otherwise,
            // create the node
            if (!classDataMap.containsKey(className)) {
                clNode = new ClassNode(classIndex, superclassIndex);
                classDataMap.put(className, clNode);

            }
            else {
                clNode = (ClassNode) classDataMap.get(className);
                clNode.setSuperclass(superclassIndex);
            }

            // Add this class to the subclasses list of the superclass,
            // creating a node for the superclass if necessary
            if (!clazz.isInterface()) {
                if (!classDataMap.containsKey(superclassName)) {
                    clNode = new ClassNode(superclassIndex, NO_SUPERCLASS);
                    classDataMap.put(superclassName, clNode);
                }
                else {
                    clNode = (ClassNode) classDataMap.get(superclassName);
                }
                if (inputClasses.contains(superclassName)) {
                    clNode.addSubclass(classIndex);
                }
            }

            // Add this class to the implementors list for each implemented
            // interface, creating nodes for the interfaces if necessary
            for (int n = 0; n < interfaces.length; n++) {
                if (!classDataMap.containsKey(interfaces[n])) {
                    int interfaceIndex = nextNameIndex++;
                    nameIndexMap.put(interfaces[n], interfaceIndex);

                    clNode = new ClassNode(interfaceIndex, NO_SUPERCLASS);
                    classDataMap.put(interfaces[n], clNode);
                }
                else {
                    clNode = (ClassNode) classDataMap.get(interfaces[n]);
                }

                if (!inputClasses.contains(interfaces[n])) {
                    continue;
                }
                clNode.addImplementor(classIndex);
            }

            // Compute which classes are used by this class. Only classes which
            // are considered part of the program (present in the .prog file)
            // are included in the computation. May also be disabled by
            // conditional compilation for better efficiency.
            if (findUses) {
                if (clazz.isInterface()) continue;

                // Every class used by this class must have a name reference in
                // the constant pool, so we simply search for those entries
                // which intersect with the names in the .prog file and add
                // this class to the uses list for each matching entry
                ConstantPool cp = clazz.getConstantPool();
                Constant[] classConstants = cp.getConstantPool();
                String usedClassName = null;
                for (int n = 0; n < classConstants.length; n++) {
                    if (classConstants[n] == null) {
                        continue;
                    }

                    if (classConstants[n].getTag()
                            == Constants.CONSTANT_Class) {
                        ConstantUtf8 nameConstant = (ConstantUtf8)
                            cp.getConstant(((ConstantClass)
                                classConstants[n]).getNameIndex());
                        usedClassName =
                            nameConstant.getBytes().replace('/', '.');
                    }
                    else {
                       continue;
                    }

                    // Ignore the self-reference, and any classes not in
                    // the .prog file
                    if ((usedClassName.indexOf(className) != -1) ||
                            !inputClasses.contains(usedClassName)) {
                        continue;
                    }

                    // Add to the uses list for each matching class,
                    // creating nodes for the classes if necessary
                    if (!classDataMap.containsKey(usedClassName)) {
                        int userIndex = nextNameIndex++;
                        nameIndexMap.put(usedClassName, userIndex);

                        clNode = new ClassNode(userIndex, NO_SUPERCLASS);
                        classDataMap.put(usedClassName, clNode);
                    }
                    else {
                        clNode = (ClassNode) classDataMap.get(usedClassName);
                    }
                    clNode.addUser(classIndex);
                }
            }
        }

        // Finalize the name/index mappings into the table - since the number
        // of mappings is fixed from this point on, an array is faster for
        // lookups later.
        nameTable = new String[nameIndexMap.size()];
        TObjectIntIterator names = nameIndexMap.iterator();
        while (names.hasNext()) {
            names.advance();
            String className = (String) names.key();
            int index = names.value();
            nameTable[index] = className;
            ((ClassNode) classDataMap.get(className)).setNameTable(nameTable);
        }
    }

    /**************************************************************************
     * Loads a class with BCEL.
     *
     * @param className Name of the class to be loaded for analysis.
     *
     * @return The BCEL representation of the class to be used to
     * retrieve interclass relationship information.
     *
     * @throws IOException If an error occurs while attempting to read the
     * class.
     * @throws ClassFormatError If the class is not a valid Java class.
     */
    private JavaClass loadClass(String className)
                      throws IOException, ClassFormatError {
        return Handler.parseClass(className);
    }

    /**************************************************************************
     * Gets a copy of the name/index table. The copy cannot be used to change
     * the internal table, by design.
     *
     * <p>This method is intended primarily for use by
     * {@link sofya.graphs.irg.IRGHandler}.</p>
     *
     * @return A deep copy of the table recording mappings of names to their
     * internal numeric indices for this IRG.
     */
    public String[] getNameTable() {
        String[] tableCopy = new String[nameTable.length];
        System.arraycopy(nameTable, 0, tableCopy, 0, nameTable.length);
        return tableCopy;
    }

    /**************************************************************************
     * Gets a reference to the name/index table, for use by the handler.
     *
     * @return A direct reference to the name table for this IRG.
     */
    String[] nameTable() {
        return nameTable;
    }

    /**************************************************************************
     * Gets the node containing the interclass relations for a given class.
     *
     * @param className Name of the class for which the interclass relations
     * data node is to be retrieved.
     *
     * @return A data node which provides access to the interclass relationship
     * data computed for the specified class.
     *
     * @throws ClassNotFoundException If no node can be found in the IRG for
     * the specified class.
     */
    public ClassNode getClassRelationData(String className)
                     throws ClassNotFoundException {
        if (!classDataMap.containsKey(className)) {
            throw new ClassNotFoundException(className);
        }
        else {
            return (ClassNode) classDataMap.get(className);
        }
    }

    /**************************************************************************
     * Test driver for the IRG class.
     */
    public static void main(String[] argv) throws Exception {
        IRG irg = new IRG("listtest.prog", null);
        ClassNode cn = null;
        String testClass = "galileo.base.GConstants";

        if (argv.length > 0) {
            testClass = argv[0];
        }

        /*String[] classList = irg.getNameTable();
        for (int i = 0; i < classList.length; i++) {
            System.out.println(classList[i]);
        }
        System.out.println();*/
        System.out.println(testClass + ":");
        cn = irg.getClassRelationData(testClass);
        System.out.println("  superclass: " + cn.getSuperclass());
        System.out.println("  subclasses: ");
        for (Iterator it = cn.subclassIterator(); it.hasNext(); ) {
            System.out.println("    " + (String) it.next());
        }
        System.out.println("  implementors: ");
        /*String[] imps = cn.getImplementors();
        for (int i = 0; i < imps.length; i++) {
            System.out.println("    " + imps[i]);
        }
        System.out.println("    ----------");*/
        for (Iterator it = cn.implementorIterator(); it.hasNext(); ) {
            System.out.println("    " + (String) it.next());
        }
    }


    //=========================================================================

    /**************************************************************************
     * This class represents a node in the IRG which encapsulates the
     * interclass relations for a particular class.
     *
     * <p>A node records the name of a class, its superclass, subclasses,
     * implementors (for interfaces), and classes which use it (if computation
     * of uses is enabled). To reduce memory footprint all of these references
     * are stored as numeric indices, and the node retains a shallow reference
     * to the name table of the enclosing graph for resolving the names.</p>
     */
    public static class ClassNode {
        /** Reference to the name table of the enclosing IRG. */
        private String[] nameTable = null;

        /** Name of the class. */
        private int className = -1;
        /** Name of the superclass. */
        private int superclass = -1;

        /** Names of the class's subclasses. */
        private TIntArrayList subclasses = null;
        /** Names of the classes implementing this interface,
            if appropriate. */
        private TIntArrayList implementors = null;
        /** Names of the classes using this class. */
        private TIntArrayList users = null;

        /** Constant used to request retrieval of the superclass
            index (see {@link IRG.ClassNode#getIndices(int)}). */
        static final int ISUPERCLASS = 1;
        /** Constant used to request retrieval of the subclass
            index list (see {@link IRG.ClassNode#getIndices(int)}). */
        static final int ISUBCLASSES = 2;
        /** Constant used to request retrieval of the implementors
            index list (see {@link IRG.ClassNode#getIndices(int)}). */
        static final int IIMPLEMENTORS = 3;
        /** Constant used to request retrieval of the users
            index list (see {@link IRG.ClassNode#getIndices(int)}). */
        static final int IUSERS = 4;

        /**
         * Constructs a partially initialized class relations node.
         *
         * <p>This is intended for internal use during IRG construction;
         * it enables nodes to be aggressively created when classes reveal
         * dependencies on other classes not yet analyzed. This in turn
         * enables a more efficient single pass analysis.</p>
         *
         * @param name Index to the name table entry containing the name
         * of the class represented by this node.
         * @param superclass Index to the name table entry containing
         * the name of the superclass of this node's class.
         */
        protected ClassNode(int name, int superclass) {
            this.className = name;
            this.superclass = superclass;

            this.subclasses = new TIntArrayList(4);
            this.implementors = new TIntArrayList();
            this.users = new TIntArrayList(15);
        }

        /**
         * Constructs a class relations node.
         *
         * @param name Index to the name table entry containing the name
         * of the class represented by this node.
         * @param superclass Index to the name table entry containing
         * the name of the superclass of this node's class.
         * @param subclasses List of name table indexes for the subclasses
         * of this node's class.
         * @param implementors List of name table indexes to classes
         * implementing this node's interface (will be empty for concrete
         * (classes).
         * @param users List of name table indexes to classes which
         * use this node's class.
         * @param nameTable Reference to the name table of the containing
         * IRG, used to resolve names.
         */
        protected ClassNode(int name, int superclass, TIntArrayList subclasses,
                            TIntArrayList implementors, TIntArrayList users,
                            String[] nameTable) {
            this.nameTable = nameTable;

            this.className = name;
            this.superclass = superclass;

            this.subclasses = subclasses;
            this.implementors = implementors;
            this.users = users;
        }

        /**
         * Gets the name of the class represented by this node.
         *
         * @return This node's class name.
         */
        public String getName() {
            return nameTable[className];
        }

        /**
         * Gets the superclass of this node's class.
         *
         * @return The superclass of this class.
         */
        public String getSuperclass() {
            if (superclass == NO_SUPERCLASS) {
                if (nameTable[className].equals("java.lang.Object")) {
                    return BASE_ID;
                }
                else {
                    return UNDEF_ID;
                }
            }
            else {
                return nameTable[superclass];
            }
        }

        /**
         * Gets a list of the names of subclasses of this node's class.
         *
         * @return The list of this class's subclasses.
         */
        public String[] getSubclasses() {
            return resolveList(subclasses);
        }

        /**
         * Gets a list of the names of classes implementing this node's
         * interface.
         *
         * @return The list of classes implementing this class's interface.
         * This list will be empty if the node does not represent an interface.
         */
        public String[] getImplementors() {
            return resolveList(implementors);
        }

        /**
         * Gets a list of the names of classes which use this node's class.
         *
         * @return The list of classes which use this class.
         */
        public String[] getUsers() {
            return resolveList(users);
        }

        /**
         * Gets the number of subclasses of this node's class.
         *
         * @return Number of subclasses of this class.
         */
        public int getSubclassCount() {
            return subclasses.size();
        }

        /**
         * Gets the number of classes implementing this node's interface.
         *
         * @return Number of classes implementing this interface (0 if
         * the node does not represent an interface).
         */
        public int getImplementorCount() {
            return implementors.size();
        }

        /**
         * Gets the number of classes using this node's class.
         *
         * @return Number of classes which use this class (0 if the
         * IRG is not compiled to collect use information).
         */
        public int getUserCount() {
            return users.size();
        }

        /**
         * Returns an iterator over the names of the subclasses of this
         * node's class.
         *
         * @return A (read-only) iterator over the names of this
         * class's subclasses.
         */
        public Iterator<String> subclassIterator() {
            return new NameIterator(subclasses, nameTable, subclasses.size());
        }

        /**
         * Returns an iterator over the names of the implementors of this
         * node's interface.
         *
         * @return A (read-only) iterator over the names of classes
         * implementing this interface, which will return no elements if
         * this node does not represent an interface.
         */
        public Iterator<String> implementorIterator() {
            return new NameIterator(implementors, nameTable,
                                    implementors.size());
        }

        /**
         * Returns an iterator over the names of the users of this node's
         * class.
         *
         * @return A (read-only) iterator over the names of classes which
         * use this class, which will return no elements if the IRG is
         * not compiled to collect use information.
         */
        public Iterator<String> userIterator() {
            return new NameIterator(users, nameTable, users.size());
        }

        /**
         * Gets an array copy of the name table indices for a given field, for
         * use by {@link sofya.graphs.irg.IRGHandler} in writing the IRG
         * to file.
         *
         * @param field Constant value specifying which field's indices are
         * to be retrieved (see constants declared in class).
         *
         * @return An array containing the name table indices for the
         * requested field.
         */
        int[] getIndices(int field) {
            TIntArrayList indexList = null;

            switch (field) {
            case ISUPERCLASS:
                return new int[]{superclass};
            case ISUBCLASSES:
                indexList = subclasses;
                break;
            case IIMPLEMENTORS:
                indexList = implementors;
                break;
            case IUSERS:
                indexList = users;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized field");
            }

            /*int[] indexArray = new int[indexList.size()];
            int i = 0;
            ListIterator li;
            for (li = indexList.listIterator(); li.hasNext(); i++) {
                indexArray[i] = ((Integer) li.next()).intValue();
            }*/

            return indexList.toNativeArray();
        }

        /**
         * Sets the shallow reference to the IRG name table to be used to
         * resolve indices to names.
         *
         * @param nameTable IRG name table used to resolve names.
         */
        void setNameTable(String[] nameTable) {
            this.nameTable = nameTable;
        }

        /**
         * Sets the superclass of this node's class.
         *
         * @param superclass Index to the name of the this class's superclass.
         */
        void setSuperclass(int superclass) {
            this.superclass = superclass;
        }

        /**
         * Adds a subclass to the list of subclasses for this node's class.
         *
         * @param subclass Index to the name of the subclass to be added.
         */
        void addSubclass(int subclass) {
            subclasses.add(subclass);
        }

        /**
         * Adds an implementor to the list of classes implementing this
         * node's interface.
         *
         * @param implementor Index to the name of the implementor to be added.
         */
        void addImplementor(int implementor) {
            implementors.add(implementor);
        }

        /**
         * Adds a user to the list of classes using this node's class.
         *
         * @param usingClass Index to the name of the using class to be added.
         */
        void addUser(int usingClass) {
            users.add(usingClass);
        }

        /**
         * Converts a list of name table indices to an array of the
         * corresponding class/interface names.
         *
         * @param indexList List of indices to be resolved to names.
         *
         * @return Array of strings which are the names corresponding
         * to the given list of name table indices.
         */
        private String[] resolveList(TIntArrayList indexList) {
            String[] resolved = new String[indexList.size()];

            for (int i = 0; i < indexList.size(); i++) {
                resolved[i] = nameTable[indexList.getQuick(i)];
            }
            return resolved;
        }
    }

    /**
     * An iterator which iterates over class/interface names in a
     * {@link ClassNode} field by wrapping the iterator obtained from
     * the index list for that field and performing the name mapping via
     * an IRG name table.
     */
    private static class NameIterator implements Iterator<String> {
        /** Wrapped iterator of the field's index list. */
        private TIntArrayList theList = null;
        /** Reference to the containing IRG's name table. */
        private String[] nameTable = null;
        /** Number of names in the iteration. */
        private int size = 0;
        /** Position of the next element to be returned by the iteration. */
        private int position = 0;

        /** Immutable shared instance of an empty iterator. */
        private static final NameIterator emptyIterator =
            new NameIterator(new TIntArrayList(0), null, 0);

        /** Cannot use no-argument constructor. */
        private NameIterator() { }

        /**
         * Constructs an iterator to return names mapped from the
         * given iterator.
         *
         * @param it Iterator over the list of indices to be mapped.
         * @param nameTable Reference to the name table to be used to
         * perform the mapping.
         * @param size Number of names in the iteration.
         */
        public NameIterator(TIntArrayList list, String[] nameTable, int size) {
            theList = list;
            this.nameTable = nameTable;
            this.size = size;
        }

        /**
         * Reports whether there are more names in the iteration.
         *
         * @return <code>true</code> if the iterator contains more
         * names, <code>false</code> otherwise.
         */
        public boolean hasNext() {
            return (position < theList.size());
        }

        /**
         * Returns the next name from the iteration..
         *
         * @return The next available class/interface name.
         *
         * @throws NoSuchElementException If there are no further names
         * in the iteration.
         */
        public String next() {
            return nameTable[theList.getQuick(position++)];
        }

        /**
         * This read-only iterator does not support this operation.
         *
         * @throws UnsupportedOperationException <b>Always.</b>
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns the number of names in the iteration.
         *
         * @return The number of names that can be obtained through the
         * iterator.
         */
        public int getElementCount() {
            return size;
        }

        /**
         * Factory method which returns an empty iteration.
         *
         * @return An iterator which contains no names (<code>hasNext</code>
         * returns <code>false</code> immediately).
         */
        protected static NameIterator emptyIterator() {
            return emptyIterator;
        }
    }
}
