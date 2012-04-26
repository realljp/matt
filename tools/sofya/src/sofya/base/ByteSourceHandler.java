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

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;

import sofya.base.exceptions.MethodNotFoundException;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import gnu.trove.THashMap;

/**
 * The ByteSourceHandler provides routines to retrieve bytecode information
 * from Java binary class files.
 *
 * @author Alex Kinneer
 * @version 05/03/2005
 *
 * @see sofya.viewers.ByteSourceViewer
 */
@SuppressWarnings("unchecked")
public class ByteSourceHandler extends Handler {
    /** BCEL representation of class file being read. */
    private JavaClass classFile;
    /** BCEL representation of the class constant pool. */
    private ConstantPoolGen cpg;
    /** Map which links method names to BCEL method objects. */
    private Map<Object, Object> methodsMap = new THashMap();
    /** Map which uses a formal object representation of method signatures
        as keys to BCEL method objects. */
    private Map<Object, Object> sigKeyMap = new THashMap();

    /** Name of class file being read. */
    private String className;


    /*************************************************************************
     * Reads bytecode source from file.
     *
     * <p>The bytecode source is read from the class file and stored to
     * internal data structures. This data can then be retrieved via other
     * accessor functions in this class, such as {@link #getInstructions}.</p>
     *
     * @param className Name of Java class file from which bytecode source
     * data is to be read.
     *
     * @throws IOException If there is an error reading the class file.
     */
    public void readSourceFile(String className) throws IOException {
        methodsMap.clear();

        classFile = parseClass(className);

        initClass();
    }

    /*************************************************************************
     * Reads bytecode source for a class from a given location.
     *
     * <p>The location provided to this method may be a directory or
     * jar file. This method will not attempt to load a class from the
     * classpath.</p>
     *
     * @param location Name of the directory or jar file from which the
     * class should be loaded.
     * @param className Name of the class which is to be loaded. This
     * parameter should include any additional path information relative
     * to the <code>location</code> parameter that is required to find
     * the class, using path separator characters (not &apos;.&apos;).
     *
     * @throws IOException If there is an error reading the class file.
     */
    public void readSourceFile(String location, String className)
            throws IOException {
        if (!location.endsWith(".jar")) {
            if (!location.endsWith(String.valueOf(File.separatorChar))) {
                location += File.separatorChar;
            }
            readSourceFile(location + className);
            return;
        }

        methodsMap.clear();

        JarFile source = new JarFile(location);

        BufferedInputStream entryStream = null;
        try {
            for (Enumeration e = source.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                String entryName = ze.getName();

                if (entryName.equals(className)) {
                    entryStream =
                        new BufferedInputStream(source.getInputStream(ze));
                    classFile =
                        new ClassParser(entryStream, entryName).parse();
                    break;
                }
            }
        }
        finally {
            try {
                if (entryStream != null) entryStream.close();
            }
            catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }

        initClass();
    }

    /**
     * Initializes internal data structures containing information about the
     * currently loaded class.
     */
    private void initClass() {
        this.cpg = new ConstantPoolGen(classFile.getConstantPool());
        this.className = classFile.getClassName();

        Method[] methods = classFile.getMethods(); 
        for (int i = 0; i < methods.length; i++) {
            methodsMap.put(formatSignature(this.className,
                                        methods[i].toString(), '.'),
                        methods[i]);
            sigKeyMap.put(new MethodSignature(methods[i], this.className),
                        methods[i]);
        }
    }

    /*************************************************************************
     * Gets the list of methods read from the class file.
     *
     * @return List of names of the methods read from the class file.
     */
    public String[] getMethodList() {
        String[] methodList = (String[]) methodsMap.keySet().toArray(
            new String[methodsMap.size()]);
        Arrays.sort(methodList);
        return methodList;
    }

    /*************************************************************************
     * Gets the list of signatures of methods read from the class file.
     *
     * @return List of signatures of the methods read from the class file.
     */ 
    public MethodSignature[] getSignatureList() {
        MethodSignature[] sigKeys = (MethodSignature[]) sigKeyMap.keySet()
            .toArray(new MethodSignature[sigKeyMap.size()]);
        Arrays.sort(sigKeys, new MethodSignature.NameComparator());
        return sigKeys;
    }

    /**************************************************************************
     * Reports whether the classfile contains a given method.
     *
     * @param methodName Name of the method which the handler should
     * check for existence.
     *
     * @return <code>true</code> if a method of the given name exists in
     * the classfile, <code>false</code> otherwise.
     */
    public boolean containsMethod(String methodName) {
        return methodsMap.containsKey(methodName);
    }

    /**************************************************************************
     * Reports whether the classfile contains a given method.
     *
     * @param signature Signature of the method which the handler should
     * check for existence.
     *
     * @return <code>true</code> if a method with the given signature exists in
     * the classfile, <code>false</code> otherwise.
     */
    public boolean containsMethod(MethodSignature signature) {
        return sigKeyMap.containsKey(signature);
    }

    /*************************************************************************
     * Gets bytecode source for a method in human-readable form.
     *
     * @param methodName Name of the method for which the bytecode source is
     * to be retrieved.
     *
     * @return String containing list of instructions constituting the
     * specified method, in human-readable format using Java Virtual Machine
     * specification assembly mnemonics for instructions, or <code>null</code>
     * if the specified method cannot be found.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method of the specified name.
     */
    public String getSource(String methodName) throws MethodNotFoundException {
        Code code = null;
        Method m = (Method) methodsMap.get(methodName);
        if (m != null) {
            code = m.getCode();
            if (code != null) {
                return code.toString();
            }
        }
        throw new MethodNotFoundException(methodName);
    }

    /*************************************************************************
     * Gets bytecode source for a method in human-readable form.
     *
     * @param signature Signature of the method for which the bytecode source
     * is to be retrieved.
     *
     * @return String containing list of instructions constituting the
     * specified method, in human-readable format using Java Virtual Machine
     * specification assembly mnemonics for instructions, or <code>null</code>
     * if the specified method cannot be found.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method with the specified signature.
     */
    public String getSource(MethodSignature signature)
                  throws MethodNotFoundException {
        Code code = null;
        Method m = (Method) sigKeyMap.get(signature);
        if (m != null) {
            code = m.getCode();
            if (code != null) {
                return code.toString();
            }
        }
        throw new MethodNotFoundException(signature.toString());
    }

    /*************************************************************************
     * Gets bytecode source for a method as a list of string representations
     * of the bytecode instructions.
     *
     * @param methodName Name of the method for which the bytecode source is
     * to be retrieved.
     *
     * @return List of strings constituting the instructions of the specified
     * method, which may be zero-length if the instruction is
     * <code>abstract</code> or otherwise contains no instructions.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method of the specified name.
     */
    public String[] getInstructions(String methodName)
                    throws MethodNotFoundException {
        Method m = (Method) methodsMap.get(methodName);
        if (m == null) throw new MethodNotFoundException(methodName);
        return getInstructions(m);
    } 

    /*************************************************************************
     * Gets bytecode source for a method as a list of string representations
     * of the bytecode instructions.
     *
     * @param signature Signature of the method for which the bytecode source
     * is to be retrieved.
     *
     * @return List of strings constituting the instructions of the specified
     * method, which may be zero-length if the instruction is
     * <code>abstract</code> or otherwise contains no instructions.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method with the specified signature.
     */
    public String[] getInstructions(MethodSignature signature)
                    throws MethodNotFoundException {
        Method m = (Method) sigKeyMap.get(signature);
        if (m == null) throw new MethodNotFoundException(signature.toString());
        return getInstructions(m);
    }

    /*************************************************************************
     * Retrieves the source for a method as a list of strings, used by
     * {@link #getInstructions(String)} and
     * {@link #getInstructions(MethodSignature)}.
     *
     * @param m BCEL representation of method for which source is to be
     * retrieved.
     *
     * @return List of strings constituting the instructions in the specified
     * method, which may be zero-length if the instruction is
     * <code>abstract</code> or otherwise contains no instructions.
     */
    private String[] getInstructions(Method m) {
        InstructionList il = new MethodGen(m, className, cpg)
                                          .getInstructionList();
        if ((il == null) || (il.getLength() == 0)) return new String[0];

        String[] instrList = new String[il.getLength()];
        InstructionHandle ih = il.getStart();
        int index = 0;
        while (ih != null) {
            instrList[index] = ih.getInstruction().toString();
            ih = ih.getNext();
            index++;
        }
        return instrList;
    }

    /*************************************************************************
     * Gets bytecode source for a method as a BCEL
     * <code>InstructionList</code>.
     *
     * @param methodName Name of the method for which the bytecode source is
     * to be retrieved.
     *
     * @return BCEL <code>InstructionList</code> object containing the
     * bytecode instructions that constitute the specified method.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method of the specified name.
     */
    public InstructionList getInstructionList(String methodName)
                           throws MethodNotFoundException {
        Method m = (Method) methodsMap.get(methodName);
        if (m == null) throw new MethodNotFoundException(methodName);
        return getInstructionList(m);
    }

    /*************************************************************************
     * Gets bytecode source for a method as a BCEL
     * <code>InstructionList</code>.
     *
     * @param signature Signature of the method for which the bytecode source
     * is to be retrieved.
     *
     * @return BCEL <code>InstructionList</code> object containing the
     * bytecode instructions that constitute the specified method.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method with the specified signature.
     */
    public InstructionList getInstructionList(MethodSignature signature)
                           throws MethodNotFoundException {
        Method m = (Method) sigKeyMap.get(signature);
        if (m == null) throw new MethodNotFoundException(signature.toString());
        return getInstructionList(m);
    }

    /*************************************************************************
     * Retrieves the source for a method as a BCEL
     * <code>InstructionList</code>, used by
     * {@link #getInstructionList(String)} and
     * {@link #getInstructionList(MethodSignature)}.
     *
     * @param m BCEL representation of method for which source is to be
     * retrieved.
     *
     * @return BCEL <code>InstructionList</code> object containing the
     * bytecode instructions that constitute the specified method.
     */
    private InstructionList getInstructionList(Method m) {
        InstructionList il = new MethodGen(m, className, cpg)
                                          .getInstructionList();
        if (il == null) {  // Abstract or native?
            return new InstructionList();
        }
        else {
            return il;
        }
    }

    /*************************************************************************
     * Gets the bytecode source in a method between two instruction offsets,
     * inclusive.
     *
     * @param methodName Name of the method for which the bytecode source is
     * to be retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return List of strings representing the instructions in the specified
     * range, which will be of length zero if the method cannot be found
     * or is <code>abstract</code>.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method of the specified name.
     */
    public String[] getInstructions(String methodName, int startOffset,
                        int endOffset) throws MethodNotFoundException {
        if (!methodsMap.containsKey(methodName)) {
            throw new MethodNotFoundException(methodName);
        }
        return getInstructions((Method) methodsMap.get(methodName),
                               startOffset, endOffset);
    }

    /*************************************************************************
     * Gets the bytecode source in a method between two instruction offsets,
     * inclusive.
     *
     * @param signature Signature of the method for which the bytecode source
     * is to be retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return List of strings representing the instructions in the specified
     * range, which will be of length zero if the method cannot be found
     * or is <code>abstract</code>.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method with the specified signature.
     */
    public String[] getInstructions(MethodSignature signature, int startOffset,
                        int endOffset) throws MethodNotFoundException {
        if (!sigKeyMap.containsKey(signature)) {
            throw new MethodNotFoundException(signature.toString());
        }
        return getInstructions((Method) sigKeyMap.get(signature),
                               startOffset, endOffset);
    }

    /*************************************************************************
     * Retrieves the source in a method between two offsets, used by
     * {@link #getInstructions(String)} and
     * {@link #getInstructions(MethodSignature)}.
     *
     * @param m BCEL representation of method for which source is to be
     * retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return List of strings representing the instructions in the specified
     * range, which will be of length zero if the method cannot be found
     * or is <code>abstract</code>.
     */
    private String[] getInstructions(Method m, int startOffset, int endOffset) {
        InstructionList il = new MethodGen(m, className, cpg)
                                          .getInstructionList();
        if ((il == null) || (il.getLength() == 0)) return new String[0];
        ArrayList<Object> instructions = new ArrayList<Object>();

        InstructionHandle ih = il.findHandle(startOffset);
        InstructionHandle ihEnd = il.findHandle(endOffset);
        if (ihEnd == null) ihEnd = il.getEnd();

        while (ih != null) {
            if (ih == ihEnd) {
                instructions.add(ih.getInstruction().toString());
                break;
            }
            instructions.add(ih.getInstruction().toString());
            ih = ih.getNext();
        }
        return (String[]) instructions.toArray(new String[instructions.size()]);
    }

    /*************************************************************************
     * Gets the bytecode source in a method between two instruction offsets,
     * inclusive.
     *
     * @param methodName Name of the method for which the bytecode source is
     * to be retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return Array of BCEL <code>Instruction</code> objects representing
     * the bytecode in the given range.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method of the specified name.
     */
    public Instruction[] getInstructionList(String methodName, int startOffset,
                             int endOffset) throws MethodNotFoundException {
        if (!methodsMap.containsKey(methodName)) {
            throw new MethodNotFoundException(methodName);
        }
        return getInstructionList((Method) methodsMap.get(methodName),
                                  startOffset, endOffset);
    }

    /*************************************************************************
     * Gets the bytecode source in a method between two instruction offsets,
     * inclusive.
     *
     * @param signature Signature of the method for which the bytecode source
     * is to be retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return Array of BCEL <code>Instruction</code> objects representing
     * the bytecode in the given range.
     *
     * @throws MethodNotFoundException If the handler has no bytecode listing
     * associated with a method with the specified signature.
     */
    public Instruction[] getInstructionList(MethodSignature signature,
            int startOffset, int endOffset) throws MethodNotFoundException {
        if (!sigKeyMap.containsKey(signature)) {
            throw new MethodNotFoundException(signature.toString());
        }
        return getInstructionList((Method) sigKeyMap.get(signature),
                                  startOffset, endOffset);
    }

    /*************************************************************************
     * Retrieves the source in a method between two offsets, used by
     * {@link #getInstructionList(String)} and
     * {@link #getInstructionList(MethodSignature)}.
     *
     * @param m BCEL representation of method for which source is to be
     * retrieved.
     * @param startOffset Offset to the first instruction to be retrieved.
     * @param endOffset Offset to the last instruction to be retrieved.
     *
     * @return Array of BCEL <code>Instruction</code> objects representing
     * the bytecode in the given range.
     */
    private Instruction[] getInstructionList(Method m, int startOffset,
                                             int endOffset) {
        InstructionList il = new MethodGen(m, className, cpg)
                                          .getInstructionList();
        if ((il == null) || (il.getLength() == 0)) return new Instruction[0];
        ArrayList<Object> instrList = new ArrayList<Object>(); 

        InstructionHandle ih = il.findHandle(startOffset);
        InstructionHandle ihEnd = il.findHandle(endOffset);
        if (ihEnd == null) ihEnd = il.getEnd();

        while (ih != null) {
            if (ih == ihEnd) {
                instrList.add(ih.getInstruction());
                break;
            }
            instrList.add(ih.getInstruction());
            ih = ih.getNext();
        }
        return (Instruction[]) instrList.toArray(
                new Instruction[instrList.size()]);
    }

    /*************************************************************************
     * Retrieves the BCEL full representation of a method present in
     * the class file.
     *
     * @param methodName Name of the method to be retrieved.
     *
     * @return The BCEL abstract representation of the requested method.
     */
    public MethodGen getMethod(String methodName)
                      throws MethodNotFoundException {
        if (!methodsMap.containsKey(methodName)) {
            throw new MethodNotFoundException(methodName);
        }
        return new MethodGen((Method) methodsMap.get(methodName),
                             className, getConstantPool());
    }

    /*************************************************************************
     * Retrieves the BCEL full representation of a method present in
     * the class file.
     *
     * @param signature Signature of the method to be retrieved.
     *
     * @return The BCEL abstract representation of the requested method.
     */
    public MethodGen getMethod(MethodSignature signature)
                      throws MethodNotFoundException {
        if (!sigKeyMap.containsKey(signature)) {
            throw new MethodNotFoundException(signature.toString());
        }
        return new MethodGen((Method) sigKeyMap.get(signature),
                             className, getConstantPool());

    }

    /*************************************************************************
     * Returns an unmodifiable view of the BCEL ConstantPoolGen object
     * for the class file loaded in the handler, which is required to obtain
     * information about certain types of instructions.
     *
     * @return An unmodifiable view of the constant pool for the class
     * currently loaded by the handler. Calling a method on the returned
     * object that would cause a change to the constant pool will result
     * in an <code>UnsupportedOperationException</code> being thrown.
     */
    public ConstantPoolGen getConstantPool() {
        return new UnmodifiableCPG(cpg);
    }

    /**
     * Reports whether the loaded class represents an interface.
     *
     * @return <code>true</code> if the class is an interface,
     * <code>false</code> otherwise.
     */
    public boolean classIsInterface() {
        return classFile.isInterface();
    }

    /**
     * Reports whether the loaded class is an abstract class.
     *
     * @return <code>true</code> if the class is abstract,
     * <code>false</code> otherwise.
     */
    public boolean classIsAbstract() {
        return classFile.isAbstract();
    }

    /*************************************************************************
     * Wrapper which provides an unmodifiable view of a BCEL ConstantPoolGen
     * object.
     *
     * <p>It may seem strange to make an object intended for building a
     * constant pool unmodifiable, however the ConstantPoolGen class is
     * required as an argument to numerous accessor methods on Instruction
     * objects. Therefore to allow access to necessary information about
     * instructions, we must expose a reference to the ConstantPoolGen for
     * the class, but want to do so in a safe manner.</p>
     */
    @SuppressWarnings("serial")
    private class UnmodifiableCPG extends ConstantPoolGen {
        ConstantPoolGen cpg;
        private UnmodifiableCPG() { }
        public UnmodifiableCPG(ConstantPoolGen cpg) {
            if (cpg == null) throw new NullPointerException();
            this.cpg = cpg;
        }
        public int addArrayClass(ArrayType type) {
            throw new UnsupportedOperationException();
        }
        public int addClass(ObjectType type) {
            throw new UnsupportedOperationException();
        }
        public int addClass(String str) {
            throw new UnsupportedOperationException();
        }
        public int addConstant(Constant c, ConstantPoolGen cp) {
            throw new UnsupportedOperationException();
        }
        public int addDouble(double n) {
            throw new UnsupportedOperationException();
        }
        public int addFieldref(String class_name, String field_name,
                               String signature) {
            throw new UnsupportedOperationException();
        }
        public int addFloat(float n) {
            throw new UnsupportedOperationException();
        }
        public int addInteger(int n) {
            throw new UnsupportedOperationException();
        }
        public int addInterfaceMethodref(MethodGen method) {
            throw new UnsupportedOperationException();
        }
        public int addInterfaceMethodref(String class_name,
                                         String method_name,
                                         String signature) {
            throw new UnsupportedOperationException();
        }
        public int addLong(long n) {
            throw new UnsupportedOperationException();
        }
        public int addMethodref(MethodGen method) {
            throw new UnsupportedOperationException();
        }
        public int addMethodref(String class_name, String method_name,
                                String signature) {
            throw new UnsupportedOperationException();
        }
        public int addNameAndType(String name, String signature) {
            throw new UnsupportedOperationException();
        }
        public int addString(String str) {
            throw new UnsupportedOperationException();
        }
        public int addUtf8(String n) {
            throw new UnsupportedOperationException();
        }
        public Constant getConstant(int i)
            { return cpg.getConstant(i); }
        public ConstantPool getConstantPool()
            { return cpg.getConstantPool(); }
        public ConstantPool getFinalConstantPool()
            { return cpg.getFinalConstantPool(); }
        public int getSize()
            { return cpg.getSize(); }
        public int lookupClass(String str)
            { return cpg.lookupClass(str); }
        public int lookupDouble(double n)
            { return cpg.lookupDouble(n); }
        public int lookupFieldref(String class_name, String field_name,
                                  String signature) {
            return cpg.lookupFieldref(class_name, field_name, signature);
        }
        public int lookupFloat(float n)
            { return cpg.lookupFloat(n); }
        public int lookupInteger(int n)
            { return cpg.lookupInteger(n); }
        public int lookupInterfaceMethodref(MethodGen method) {
            return cpg.lookupInterfaceMethodref(method);
        }
        public int lookupInterfaceMethodref(String class_name,
                                            String method_name,
                                            String signature) {
            return cpg.lookupInterfaceMethodref(class_name,
                method_name, signature);
        }
        public int lookupNameAndType(String name, String signature) {
            return cpg.lookupNameAndType(name, signature);
        }
        public int lookupString(String str)
            { return cpg.lookupString(str); }
        public int lookupUtf8(String n)
            { return cpg.lookupUtf8(n); }
        public void setConstant(int i, Constant c) {
            throw new UnsupportedOperationException();
        }
        public String toString()
            { return cpg.toString(); }
    }

    /*************************************************************************
     * Test driver for ByteSourceHandler.
     */ 
    public static void main(String args[]) {
        ByteSourceHandler src = new ByteSourceHandler();
        String[] methods;
        String[] srcLines;

        try {
            src.readSourceFile(args[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage()) ;
            System.exit(1);
        } 

        methods = src.getMethodList();
        for (int i = 0; i < methods.length; i++) {
            try {
                srcLines = src.getInstructions(methods[i]);
                /*Instruction[] il = src.getInstructionList(methods[i], 0, 9);
                for (int j = 0; j < il.length; j++) {
                    System.out.println(il[j].toString());
                }
                System.out.println("-----");*/
            }
            catch (MethodNotFoundException e) {
                System.err.println("Warning: handler falsely claimed to " +
                    "have instruction list for " + methods[i]);
                continue;
            }
            System.out.println("MethodName: " + methods[i]);
            System.out.println("Contents:");
            for (int j = 0; j < srcLines.length; j++) {
                System.out.println(srcLines[j]);
            }
        }

        // Check retrieval by signature keys
        MethodSignature[] keys = src.getSignatureList();
        for (int i = 0; i < keys.length; i++) {
            System.out.println(keys[i]);
        }
    }
}



/*****************************************************************************/

/*
  $Log: ByteSourceHandler.java,v $
  Revision 1.9  2007/07/30 16:20:04  akinneer
  Updated year in copyright notice.

  Revision 1.8  2006/09/08 21:29:59  akinneer
  Updated copyright notice.

  Revision 1.7  2006/09/08 20:20:52  akinneer
  "Generified". Cleaned up imports.

  Revision 1.6  2006/06/12 18:31:35  akinneer
  Updated to use BCEL 5.2

  Revision 1.5  2006/03/21 21:49:39  kinneer
  Fixed JavaDoc references to reflect post-refactoring package organization.
  Various minor code cleanups. Updated copyright notice.

  Revision 1.4  2005/06/06 18:47:02  kinneer
  Added new class and copyright notices.

  Revision 1.3  2005/05/03 15:32:46  kinneer
  Added new loading method to read a class from a jar file (or specific
  directory location).

  Revision 1.2  2005/03/25 15:07:38  kinneer
  Added new method to query whether a class is abstract.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.17  2004/09/08 19:05:44  kinneer
  Added methods to retrieve MethodGen objects from class files.

  Revision 1.16  2004/05/26 23:20:47  kinneer
  Added new access methods based on method signature objects.

  Revision 1.15  2004/02/18 19:03:34  kinneer
  Added method to test for presence of data for a method. Can be used instead
  of calling a getX method and handling an exception.

  Revision 1.14  2004/02/09 22:40:40  kinneer
  Modified to substitute '_' for spaces in method names.

  Revision 1.13  2004/02/02 19:10:55  kinneer
  All MethodNotFoundExceptions now include the name of the method
  in the exception message.

  Revision 1.12  2003/11/13 22:36:23  kinneer
  Added method to ByteSourceHandler to retrieve reference to constant pool.
  Trivial documentation fix in RegressionTestHandler.

  Revision 1.11  2003/11/12 18:28:10  kinneer
  Made method-name keys to hashmap consistent with CF/MapHandler.
  Changed return value of getInstructionList to an array of Instruction
  objects in an effort to fix problem with BCEL throwing exceptions
  under certain circumstances.

  Revision 1.10  2003/10/21 22:19:52  kinneer
  Fixed bug in checkException method.

  Revision 1.9  2003/10/10 18:46:26  kinneer
  Added methods to retrieve instructions as InstructionList objects
  rather than as arrays of strings.

  Revision 1.8  2003/09/25 16:38:37  kinneer
  Eliminated all null flags. Requesting objects for methods which do
  not exist now cause MethodNotFoundExceptions to be thrown.

  Revision 1.7  2003/08/27 18:44:05  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.6  2003/08/18 18:42:36  kinneer
  See v2.1.0 release notes for details.

  Revision 1.5  2003/08/13 18:28:36  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.4  2003/08/01 17:10:45  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.3  2002/09/03 21:00:02  sharmahi
  Added class name as prefix to method names.

  Revision 1.2  2002/08/21 06:27:20  sharmahi
  Included methods to read instructions for methods given offsets

  Revision 1.1  2002/08/07 07:56:47  sharmahi
  After adding comments

*/
