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

package sofya.base;

import java.util.Comparator;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;

/**
 * This class represents a full method signature including class name. It
 * is primarily intended to serve as a consistent key for method lookups
 * in handlers.  Various constructors are provided to facilitate convenient
 * creation of instances of this class from several types of BCEL
 * method-related objects.
 *
 * <p>The <code>equals</code> and <code>hashCode</code> methods of this class
 * are implemented to strictly conform to all contracts. The hash code
 * algorithm is based on the definition found in the <i>Effective Java
 * Programming Language Guide</i>.</p>
 *
 * @author Alex Kinneer
 * @version 01/16/2007
 */
public final class MethodSignature {
    /** Name of the class. */
    private String className;
    /** Name of the method. */
    private String methodName;
    /** Return type of the method. */
    private Type returnType;
    /** Argument types. */
    private Type[] argTypes;

    /** Cached hash code for the signature. Since these objects are
        immutable the hash code is calculated once, on instantiation. */
    private int hashCode = 0;

    private MethodSignature() { }

    /**
     * Constructs a signature object from a BCEL <code>Method</code> object.
     *
     * @param m BCEL <code>Method</code> representation of a method.
     * @param className Name of the class implementing the method.
     */
    public MethodSignature(Method m, String className) {
        this.className = className;
        methodName = m.getName();
        returnType = m.getReturnType();
        argTypes = m.getArgumentTypes();
        computeHashCode();
    }

    /**
     * Constructs a signature object from a BCEL <code>MethodGen</code> object.
     *
     * @param mg BCEL <code>MethodGen</code> representation of a method.
     */
    public MethodSignature(MethodGen mg) {
        className = mg.getClassName();
        methodName = mg.getName();
        returnType = mg.getReturnType();
        argTypes = mg.getArgumentTypes();
        computeHashCode();
    }

    /**
     * Constructs a signature object from a BCEL <code>InvokeInstruction</code>
     * object.
     *
     * @param invoke BCEL <code>InvokeInstruction</code> representing a
     * method call.
     * @param cpg Constant pool from the class containing the invoke
     * instruction (BCEL representation).
     */
    public MethodSignature(InvokeInstruction invoke,
                           ConstantPoolGen cpg) {
        className = invoke.getReferenceType(cpg).toString();
        methodName = invoke.getMethodName(cpg);
        returnType = invoke.getReturnType(cpg);
        argTypes = invoke.getArgumentTypes(cpg);
        computeHashCode();
    }

    /**
     * Constructs a signature object from explicit signature constituents.
     *
     * @param className Name of the class implementing the method.
     * @param methodName Name of the method.
     * @param returnType Return type of the method.
     * @param argTypes Types of the method's arguments.
     */
    public MethodSignature(String className, String methodName,
                           Type returnType, Type[] argTypes) {
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.argTypes = new Type[argTypes.length];
        System.arraycopy(argTypes, 0, this.argTypes, 0, argTypes.length);
        computeHashCode();
    }

    /**
     * Constructs a signature object which represents the same method
     * bound to a different class.
     *
     * @param className Name of the new class which will be associated
     * with the method signature.
     * @param signature Existing signature to be copied to a new
     * class binding.
     */
    public MethodSignature(String className, MethodSignature signature) {
        this.className = className;
        this.methodName = signature.methodName;
        this.returnType = signature.returnType;
        this.argTypes = new Type[signature.argTypes.length];
        System.arraycopy(signature.argTypes, 0, argTypes, 0,
                         signature.argTypes.length);
        computeHashCode();
    }

    /**
     * Constructs a signature object from explicit signature constituents.
     *
     * @param className Name of the class implementing the method.
     * @param methodName Name of the method.
     * @param jniSignature JNI type signature of the method (see
     * <a href="http://java.sun.com/docs/books/vmspec/2nd-edition/html/ClassFile.doc.html#1169">
     * Java Language Specification, Section 4.3</a>).
     */
    public MethodSignature(String className, String methodName,
            String jniSignature) {
        this.className = className;
        this.methodName = methodName;
        this.returnType = Type.getReturnType(jniSignature);
        this.argTypes = Type.getArgumentTypes(jniSignature);
        computeHashCode();
    }

    /**
     * Gets the name of the class implementing the method.
     *
     * @return The name of the class that would be used as the basis
     * for dynamic binding of the method.
     */
    public String getClassName() {
        return className;
    }

    /**
     * Gets the method's name.
     *
     * @return The name of the method represented by this signature.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the method's return type.
     *
     * @return The return type of the method represented by this signature.
     */
    public Type getReturnType() {
        return returnType;
    }

    /**
     * Gets the <code>java.lang.Class</code> for the method's return type.
     *
     * @return The class object for the return type of the method
     * represented by this signature.
     */
    public Class getReturnClass() throws ClassNotFoundException {
        return Utility.typeToClass(returnType);
    }

    /**
     * Gets the method's argument types.
     *
     * @return The types of the arguments to the method represented by this
     * signature.
     */
    public Type[] getArgumentTypes() {
        Type[] argumentTypes = new Type[argTypes.length];
        System.arraycopy(argTypes, 0, argumentTypes, 0, argTypes.length);
        return argumentTypes;
    }

    /**
     * Gets the method's argument types as an array of
     * <code>java.lang.Class</code> objects.
     *
     * @return The class objects for the types of the arguments to the method
     * represented by this signature.
     */
    public Class[] getArgumentClasses() throws ClassNotFoundException {
        return Utility.typesToClasses(argTypes);
    }

    /**
     * Gets the method's JNI type signature.
     *
     * @return The JNI type signature of the method (see
     * <a href="http://java.sun.com/docs/books/vmspec/2nd-edition/html/ClassFile.doc.html#1169">
     * Java Language Specification, Section 4.3</a>).
     */
    public String getTypeSignature() {
        return Type.getMethodSignature(returnType, argTypes);
    }

    /**
     * Tests whether this method signature represents the same method
     * as another method signature.
     *
     * @param obj Method signature against which this signature should
     * be tested for equivalence.
     *
     * @return <code>true</code> if this signature represents the same
     * method as the provided signature; that is, a virtual call would
     * bind to the same method for both signatures. Returns
     * <code>false</code> if the signatures do not represent the same
     * method, or the argument is an instance of <b>any</b> class other
     * than a <code>MethodSignature</code> (this preserves the contract
     * of the equals method).
     */
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (!this.getClass().equals(obj.getClass())) {
            return false;
        }

        MethodSignature otherSig = (MethodSignature) obj;
        if (!(this.className.equals(otherSig.className) &&
                this.methodName.equals(otherSig.methodName) &&
                this.returnType.equals(otherSig.returnType) &&
                (this.argTypes.length == otherSig.argTypes.length))) {
            return false;
        }

        for (int i = 0; i < argTypes.length; i++) {
            if (!this.argTypes[i].equals(otherSig.argTypes[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the hash code for this method signature.
     *
     * @return The hash code computed from this method signature.
     */
    public int hashCode() {
        return hashCode;
    }

    /**
     * Computes the hash code for this signature. This method should be
     * called at the end of all constructors.
     */
    private void computeHashCode() {
        hashCode = 11;
        hashCode = (37 * hashCode) + className.hashCode();
        hashCode = (37 * hashCode) + methodName.hashCode();
        hashCode = (37 * hashCode) + returnType.hashCode();
        for (int i = 0; i < argTypes.length; i++) {
            hashCode = (37 * hashCode) + argTypes[i].hashCode();
        }
    }

    /**
     * Creates a new method signature object which is a copy of this
     * method signature.
     *
     * @return A completely new method signature object equivalent to
     * this method signature.
     */
    public MethodSignature copy() {
        MethodSignature newSig = new MethodSignature();
        newSig.className = this.className;
        newSig.methodName = this.methodName;
        newSig.returnType = this.returnType;
        newSig.argTypes = this.getArgumentTypes();  // Creates copy
        newSig.hashCode = this.hashCode;
        return newSig;
    }

    /**
     * Returns the string representation of this method signature.
     *
     * @return A string representation of the signature.
     */
    public String toString() {
        return className + "." + methodName +
            Type.getMethodSignature(returnType, argTypes);
    }
    
    /**
     * Returns the string representation of this method signature.
     *
     * @return A string representation of the signature, formatted
     * similar to the way the method declaration would appear
     * in source code.
     */
    public String toPrettyString() {
        StringBuilder str = new StringBuilder();
        str.append(returnType.toString());
        str.append(" ");
        str.append(className);
        str.append(".");
        str.append(methodName);
        str.append("(");
        if (argTypes.length > 0) {
            int i = 0;
            while (true) {
                str.append(argTypes[i].toString());
                str.append(" ");
                str.append("arg");
                str.append(i);
                
                i += 1;
                if (i == argTypes.length) {
                    break;
                }
                else {
                    str.append(", ");
                }
            }
        }
        str.append(")");
        return str.toString(); 
    }

    /**
     * A utility comparator which orders method signatures.
     *
     * <p>Method signatures are compared in the following manner:
     * <ol>
     * <li>The full names of the two signatures, including the fully
     * qualified class names, are compared lexically. If the two names
     * are not equal, the result of the comparison is returned as the
     * result of the signature comparison.</li>
     * <li>If the names are equal, and an &apos;equals relation&apos;
     * has been specified to the comparator, that value is returned.</li>
     * <li>If the names are equal, and no equals relation
     * has been specified, the number of arguments to each method is
     * compared. The method with more arguments is considered
     * &apos;greater than&apos; the other method.</li>
     * <li>If both methods have an equal number of arguments, the
     * type of each argument in each method is converted to a string
     * and lexically compared to its match. This proceeds until an
     * argument type comparison is non-equal, or all arguments have
     * been compared.</li>
     * <li>If all the argument types are lexically equal, the return
     * types of each method are converted to strings and lexically
     * compared. The result of this lexical comparison will be returned
     * as the result of the signature comparison.</li>
     * </ol></p>
     *
     * @author Alex Kinneer
     * @version 04/21/2004
     */
    public static class NameComparator implements Comparator<MethodSignature> {
        /** Flag which specifies what relation the comparator should
            report for equal objects. */
        private int equalsRelation = 0; 

        /**
         * Constructs a comparator which will operate in strict accordance
         * with the contract of the <code>compare</code> method.
         */
        public NameComparator() { }

        /**
         * Constructs a comparator which returns the specified relation
         * for equal objects.
         *
         * <p>This is provided to allow TreeSet/Maps to be used as sorted
         * lists by treating multiple equivalent values/keys as distinct
         * from each other, since the java collections do not provide any
         * true sorted list (bag) implementation. Note that for both data
         * structures an iterator must be obtained to extract multiple
         * redundant values/keys, as the <code>get</code> methods will
         * only return the first matching value/mapping. In the case of
         * a TreeMap, mappings must be removed after each value retrieval
         * using a redundant key or the first mapping encountered will be
         * retrieved every time.</p>
         *
         * @param equalsRelation Value specifying which ordering the
         * comparator should report for equal objects:
         * <ul>
         * <li>-1 specifies that the second object is to be ordered as
         * &apos;less than&apos; the first (the second object will
         * precede the first in the list)</li>
         * <li>1 specifies the inverse</li>
         * <li>0 retains the normal behavior of reporting equality (in
         * sets, the second object will replace the first).</li>
         * </ul>
         * <b>Be aware of the following</b>:
         * <ol>
         * <li>Specifying a value other than 0 will cause the
         * {@link MethodSignature.NameComparator#compare} method to
         * violate its contract (specifically transitivity of equality),
         * and become inconsistent with equals.</li>
         * <li>Specifying a value other than 0 may cause non-deterministic
         * orderings within equivalence classes of signatures when the source
         * of those signatures is disordered. This is because the ordering
         * of signatures determined to be equal will be dependent on the
         * order in which they are added. In particular, signatures taken
         * from hashed collections will not be consistently sorted.</li>
         * </ol>
         */
        public NameComparator(int equalsRelation) {
            if (equalsRelation < -1 || equalsRelation > 1) {
                throw new IllegalArgumentException("Relation must be in " +
                    "range [-1, 1]");
            }
            this.equalsRelation = equalsRelation;
        }

        /**
         * Compares two method signatures using the lexical alphabetical
         * relation of the two signatures' full names.
         *
         * @param o1 Method signature for comparison.
         * @param o2 Method signature for comparison to <code>o1</code>.
         *
         * @return -1 if the first signature is less than the second,
         * 0 if the two objects represent the same method signature or
         * have the name lexically, and 1 if the second signature is
         * greater than the first.
         *
         * @throws ClassCastException If either arguments is an instance of
         * <b>any</b> class (including a subclass) other than
         * <code>MethodSignature</code>.
         */
        public int compare(MethodSignature o1, MethodSignature o2) {
            if (o1 == o2) return equalsRelation;

            if (!(o1.getClass().equals(MethodSignature.class) &&
                    o2.getClass().equals(MethodSignature.class))) {
                throw new ClassCastException();
            }

            MethodSignature ms1 = (MethodSignature) o1;
            MethodSignature ms2 = (MethodSignature) o2;

            int result = (ms1.className + ms1.methodName).compareTo(
                (ms2.className + ms2.methodName));
            if (result == 0) {
                if (equalsRelation == 0) {
                    if (ms1.argTypes.length < ms2.argTypes.length) {
                        return -1;
                    }
                    else if (ms1.argTypes.length > ms2.argTypes.length) {
                        return 1;
                    }

                    for (int i = 0; i < ms1.argTypes.length; i++) {
                        int tCompare = ms1.argTypes[i].toString().compareTo(
                            ms2.argTypes[i].toString());
                        if (tCompare != 0) {
                            return tCompare;
                        }
                    }

                    // Overloaded methods cannot be legally distinguished
                    // by return types, so in reality this is more of a
                    // formality
                    return ms1.returnType.toString().compareTo(
                        ms2.returnType.toString());
                }
                else {
                    return equalsRelation;
                }
            }
            return result;
        }
    }
}
