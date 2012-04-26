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

package sofya.apps.dejavu;

import java.io.File;
import java.io.IOException;

import sofya.base.SConstants;
import sofya.base.ByteSourceHandler;
import sofya.base.exceptions.*;
import sofya.graphs.Node;
import sofya.graphs.cfg.Block;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;

/**
 * Implementation of {@link NodeComparer} which uses the bytecode
 * instruction lists of two nodes to compare them for equivalence.
 *
 * @author Sharat Narayan, Sriraam Natarajan
 * @author Alex Kinneer
 * @version 10/13/2005
 */
public class BytecodeNodeComparer extends NodeComparer {
    /** Bytesource loader for the old version of the class. */
    private ByteSourceHandler bsHandlerOld;
    /** Bytesource loader for the new version of the class. */
    private ByteSourceHandler bsHandlerNew;
    /** Constant pool for the old classfile. */
    private ConstantPoolGen cpgOld;
    /** Constant pool for the new classfile. */
    private ConstantPoolGen cpgNew;
    /** Stores currently loaded class, to avoid reloading if subsequent
        requests are for the same class. */
    private ClassPair loadedClass = null;
    
    public BytecodeNodeComparer() {
        bsHandlerOld = new ByteSourceHandler();
        bsHandlerNew = new ByteSourceHandler();
    }

    /**
     * Sets the class from which node instruction lists are read
     * for comparison.
     *
     * @param clazz Structure containing the name of the class and
     * the locations from which the class can be loaded in the respective
     * versions of the program.
     *
     * @throws IOException If both versions of the specified class cannot
     * be read. Either the name of the class is invalid or a path to one
     * or both of the versions of the class is invalid.
     */
    public void setComparisonClass(ClassPair clazz) throws IOException {
        if (loadedClass == clazz) {
            return;
        }
        
        super.setComparisonClass(clazz);
        bsHandlerOld.readSourceFile(clazz.oldLocation,
            clazz.name.replace('.', File.separatorChar) + ".class");
        bsHandlerNew.readSourceFile(clazz.newLocation,
            clazz.name.replace('.', File.separatorChar) + ".class");
        cpgOld = bsHandlerOld.getConstantPool();
        cpgNew = bsHandlerNew.getConstantPool();
        
        loadedClass = clazz;
    }
    
    /**
     * Retrieves the name of the method called by a call node.
     *
     * @param methodName Name of the method containing the call node.
     * @param oldCallNode Call node in the original graph from which the name
     * of the called method should be retrieved. May be <code>null</code> if
     * <code>newCallNode</code> is non-<code>null</code>.
     * @param newCallNode  Call node in the modified graph from which the name
     * of the called method should be retrieved. May be <code>null</code> if
     * <code>oldCallNode</code> is non-<code>null</code>.
     *
     * @return The name of the method which is called by the invoke instruction
     * contained in one of the given call nodes.
     *
     * @throws MethodNotFoundException If <code>methodName</code> does not
     * refer to a method that can be found in either the original or modified
     * versions of the class.
     * @throws IllegalArgumentException If a node is not a call node.
     * @throws NullPointerException If both <code>oldCallNode</code> <i>and</i>
     * <code>newCallNode</code> are <code>null</code>.
     */
    public String getCallMethodName(String methodName, Node oldCallNode,
                                    Node newCallNode)
                  throws MethodNotFoundException, IllegalArgumentException,
                         NullPointerException {
        ConstantPoolGen cpg;
        Instruction is[];

        Block oldBlock = (Block) oldCallNode;
        Block newBlock = (Block) newCallNode;
        if (oldBlock != null) {
            cpg = cpgOld;
            is = bsHandlerOld.getInstructionList(methodName,
                oldBlock.getStartOffset(), oldBlock.getStartOffset());
        }
        else if (newBlock != null) {
            cpg = cpgNew;
            is = bsHandlerNew.getInstructionList(methodName,
                newBlock.getStartOffset(), newBlock.getStartOffset());
        }
        else {
            throw new NullPointerException();
        }

        if (is[0] instanceof FieldOrMethod) {
            return ((FieldOrMethod) is[0]).getName(cpg);
        }
        else {
            throw new IllegalArgumentException("Node is not a call node");
        }
    }

    /**
     * Compares two blocks from the same method in different versions
     * of the program.
     *
     * @param methodName Name of the method containing the blocks
     * to be compared.
     * @param oldNode Block in the old version of the program to
     * be compared to <code>newNode</code>.
     * @param newNode Block in the new version of the program to
     * be compared to <code>oldNode</code>.
     *
     * @return <code>true</code> if the two blocks are identical,
     * <code>false</code> otherwise.
     *
     * @throws MethodNotFoundException If <code>methodName</code> does not
     * refer to a valid method in the class.
     * @throws NullPointerException If either <code>oldNode</code> or
     * <code>newNode</code> is <code>null</code>.
     */
    public boolean compareNodes(String methodName, Node oldNode, Node newNode)
                   throws MethodNotFoundException, NullPointerException {
        Instruction[] oldNodeCode;
        Instruction[] newNodeCode;
        Block oldBlock = (Block) oldNode;
        Block newBlock = (Block) newNode;
        
        // Ignore exit and return blocks; they are 'dummy' blocks. Any
        // differences will be detected at the real code blocks
        // on the source of the edges leading to them.
        if ((oldBlock.getType() == SConstants.BlockType.EXIT) &&
                (newBlock.getType() == SConstants.BlockType.EXIT)) {
            return true;
        }
        else if ((oldBlock.getType() == SConstants.BlockType.RETURN) &&
                (newBlock.getType() == SConstants.BlockType.RETURN)) {
            return true;
        }

        oldNodeCode = bsHandlerOld.getInstructionList(methodName,
                          oldBlock.getStartOffset(),
                          oldBlock.getEndOffset());//According to the new bytecode.
        newNodeCode = bsHandlerNew.getInstructionList(methodName,
                          newBlock.getStartOffset(),
                          newBlock.getEndOffset());
        
        return compare(oldNodeCode, newNodeCode);
    }

    /**
     * Compares two arrays of instructions for equality.
     *
     * @param oldNodeInst Array of instructions from the block in
     * the old version of the program.
     * @param newNodeInst Array of instructions from the block in
     * the new version of the program.
     *
     * @return <code>true</code> if the instruction arrays are equal,
     * <code>false</code> otherwise.
     */
    private boolean compare(Instruction[] oldNodeInst,
                            Instruction[] newNodeInst) {
        if (oldNodeInst.length != newNodeInst.length) {
            return false;
        }

        for (int i = 0; i < oldNodeInst.length; i++) {
            Instruction iOld = oldNodeInst[i];
            Instruction iNew = newNodeInst[i];
            int oldOpcode = iOld.getOpcode();
            int newOpcode = iNew.getOpcode();

            if (oldOpcode != newOpcode) {
                return false;
            }

            // Above check guarantees opcodes are equal, so we can switch
            // off of one instruction's opcode
            switch (oldOpcode) {
            case Constants.IINC:
                IINC iincOld = (IINC) iOld;
                IINC iincNew = (IINC) iNew;
                
                if (iincOld.getIncrement() != iincNew.getIncrement()) {
                    return false;
                }
            case Constants.ALOAD:
            case Constants.DLOAD:
            case Constants.FLOAD:
            case Constants.ILOAD:
            case Constants.LLOAD:
            case Constants.ASTORE:
            case Constants.DSTORE:
            case Constants.FSTORE:
            case Constants.ISTORE:
            case Constants.LSTORE:
                LocalVariableInstruction lviOld =
                    (LocalVariableInstruction) iOld;
                LocalVariableInstruction lviNew =
                    (LocalVariableInstruction) iNew;
                    
                if (lviOld.getIndex() != lviNew.getIndex()) {
                    return false;
                }
                break;
            case Constants.BIPUSH:
            case Constants.SIPUSH:
                ConstantPushInstruction cpushOld =
                    (ConstantPushInstruction) iOld;
                ConstantPushInstruction cpushNew =
                    (ConstantPushInstruction) iNew;
                
                if (!cpushOld.getValue().equals(cpushNew.getValue())) {
                    return false;
                }
                break;
            case Constants.GETSTATIC:
            case Constants.PUTSTATIC:
            case Constants.GETFIELD:
            case Constants.PUTFIELD:
            case Constants.INVOKESPECIAL:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEINTERFACE:
            case Constants.INVOKEVIRTUAL:
                FieldOrMethod fomOld = (FieldOrMethod) iOld;
                FieldOrMethod fomNew = (FieldOrMethod) iNew;
                
                // Check name
                if (!fomOld.getName(cpgOld).equals(fomNew.getName(cpgNew))) {
                    return false;
                }
                // Check signature
                if (!fomOld.getSignature(cpgOld).equals(
                        fomNew.getSignature(cpgNew))) {
                    return false;
                }
                // Check receiver type
                if (!fomOld.getReferenceType(cpgOld).equals(
                        fomNew.getReferenceType(cpgNew))) {
                    return false;
                }
                
                break;
            case Constants.LDC:
            case Constants.LDC_W:
                LDC ldcOld = (LDC) iOld;
                LDC ldcNew = (LDC) iNew;
                
                //compare if the type are equal
                if (!ldcOld.getType(cpgOld).getSignature().equals(
                        ldcNew.getType(cpgNew).getSignature())) {
                    return false;
                }
                //compare if values are equal
                if (!ldcOld.getValue(cpgOld).toString().equals(
                        ldcNew.getValue(cpgNew).toString())) {
                    return false;
                }
                break;
            case Constants.LDC2_W:
                LDC2_W ldc2Old = (LDC2_W) iOld;
                LDC2_W ldc2New = (LDC2_W) iNew;
                
                //compare if the type are equal
                if (!ldc2Old.getType(cpgOld).getSignature().equals(
                        ldc2New.getType(cpgNew).getSignature())) {
                    return false;
                }
                //compare if values are equal
                if (!ldc2Old.getValue(cpgOld).toString().equals(
                        ldc2New.getValue(cpgNew).toString())) {
                    return false;
                }
                break;
            case Constants.MULTIANEWARRAY:
                MULTIANEWARRAY mNewArrOld = (MULTIANEWARRAY) iOld;
                MULTIANEWARRAY mNewArrNew = (MULTIANEWARRAY) iNew;

                if (mNewArrOld.getDimensions()
                        != mNewArrNew.getDimensions()) {
                    return false;
                }
            case Constants.ANEWARRAY:
            case Constants.CHECKCAST:
            case Constants.INSTANCEOF:
            case Constants.NEW: {
                CPInstruction cpOld = (CPInstruction) iOld;
                CPInstruction cpNew = (CPInstruction) iNew;
                
                Type t1 = cpOld.getType(cpgOld);
                Type t2 = cpNew.getType(cpgNew);
                if (!t1.equals(t2)) {
                    return false;
                }
                break;
              }
            case Constants.NEWARRAY: {
                NEWARRAY newArrOld = (NEWARRAY) iOld;
                NEWARRAY newArrNew = (NEWARRAY) iNew;
                
                Type t1 = newArrOld.getType();
                Type t2 = newArrNew.getType();
                if (!t1.equals(t2)) {
                    return false;
                }
                break;
              }
            }
        }
        return true;
    }
}

