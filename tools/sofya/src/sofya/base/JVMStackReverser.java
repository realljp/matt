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

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.Instruction;

/**
 * This class simulates the behavior of the Java stack for a sequence
 * of instructions executed in reverse order. More precisely, it starts
 * with the assumption that the stack contains one operand, and for
 * each instruction that it is given it computes what the state of
 * the stack would have been prior to execution of that instruction,
 * in terms of the number of operands on the stack.
 *
 * <p>Starting from an instruction where the stack does contain at least
 * one operand and assuming a legal Java bytecode sequence, when fed
 * the sequence of preceding instructions this class should always
 * eventually indicate which of those instructions placed that operand
 * on the top of the stack. This is accomplished primarily by
 * inverting the effect of instructions on the stack operand count
 * (which may of course result in the inference of additional
 * operands).</p>
 *
 * <p>Note that this class knows no more than the number of operands on
 * the stack at any given time. Any additional information would be
 * very difficult (probably NP-hard) to determine. It also may not
 * maintain a &quot;true&quot; count of the number of operands on the
 * stack, as it will &quot;discard&quot; instructions that it
 * determines cannot have any impact on determining which instruction
 * produced the operand on the top of the stack (such as is the
 * case with certain <code>DUP</code> instructions).</p>
 *
 * @author Alex Kinneer
 * @version 04/19/2004
 */
public final class JVMStackReverser implements org.apache.bcel.Constants {
    /** Constant pool for the class from which instructions are being
        read. */
    private ConstantPoolGen cpg;
    /** Marks how many stack producers are required to generate the
        operand on the top of the stack at the beginning of execution,
        relative to the position of the operand from the top of
        the stack. */
    private int matchPointer = 0;
    /** Marks how far the operand of interest is from the top of the
        stack. When this value equals <code>matchPointer</code>, the
        producing instruction has been found. The difference between
        the two values can be thought of as the number of stack
        producers needed to generate the given operand. */
    private int stackTopDist = 0;
    
    /**
     * Creates a reverse stack simulator.
     *
     * @param cpg Constant pool of the class from which instructions
     * are being &quot;executed&quot;.
     */
    public JVMStackReverser(ConstantPoolGen cpg) {
        this.cpg = cpg;
    }
    
    /**
     * Creates a reverse stack simulator.
     *
     * @param cpg Constant pool of the class from which instructions
     * are being &quot;executed&quot;.
     * @param operandDepth How far the operand of interest is offset
     * from the top of the stack.
     */
    public JVMStackReverser(ConstantPoolGen cpg, int operandDepth) {
        this(cpg);
        stackTopDist = operandDepth;
    }
    
    /**
     * Simulates execution of an instruction in reverse and reports
     * whether the instruction produced the operand that was on
     * top of the stack when the first instruction was simulated.
     *
     * @param instr Instruction (BCEL) to be simulated.
     *
     * @return <code>true</code> if the instruction produced the
     * operand of interest, <code>false</code> otherwise.
     */
    public boolean runInstruction(Instruction instr) {
        int pushCount = instr.produceStack(cpg);
        int popCount = instr.consumeStack(cpg);
        int changeCount = Math.abs(pushCount - popCount);
        
        if ((pushCount == UNPREDICTABLE) || (popCount == UNPREDICTABLE)) {
            throw new IllegalArgumentException("Illegal instruction: " +
                instr + "\nCannot determine effect on stack");
        }
        
        boolean isTargetProducer = (stackTopDist == matchPointer);

        // Ich bin ein uberswitch: this is ultimately much faster than
        // a bunch of 'instanceof's, and not much more tedious to code
        // anyway. Heck, it only had to be coded once...
        switch (instr.getOpcode()) {
        case AALOAD:
            if (isTargetProducer) {
                return true;
            }
            break;
        case ACONST_NULL:
            if (isTargetProducer) {
                return true;
            }
            break;
        case ALOAD: case ALOAD_0: case ALOAD_1: case ALOAD_2: case ALOAD_3:
            if (isTargetProducer) {
                return true;
            }
            break;
        case GETFIELD:
        case GETSTATIC:
        case INVOKEINTERFACE:
        case INVOKESPECIAL:
        case INVOKESTATIC:
        case INVOKEVIRTUAL:
            if (isTargetProducer) {
                return true;
            }
            break;
        case CHECKCAST:
            if (isTargetProducer) {
                return true;
            }
            break;
        case ANEWARRAY: case MULTIANEWARRAY: case NEWARRAY:
            if (isTargetProducer) {
                return true;
            }
            break;
        case ARETURN: case DRETURN: case FRETURN: case IRETURN:
        case LRETURN: case RETURN:
            throw new IllegalStateException("Control flow from producer " +
                "cannot pass through return instruction");
        case ATHROW:
            throw new IllegalStateException("Control flow from producer " +
                "cannot pass directly through throw instruction");
        case NEW:
            if (isTargetProducer) {
                throw new ClassFormatError("Stack operand is " +
                    "uninitialized object");
            }
            break;
        case ARRAYLENGTH:
        case BALOAD: case CALOAD: case DALOAD: case FALOAD:
        case IALOAD: case LALOAD: case SALOAD:
        case BIPUSH: case SIPUSH:
        case D2F: case D2I: case D2L:
        case DADD: case DDIV: case DMUL: case DNEG: case DREM: case DSUB:
        case DCMPL: case DCMPG:
        case DCONST_0: case DCONST_1:
        case DLOAD: case DLOAD_0: case DLOAD_1: case DLOAD_2: case DLOAD_3:
        case F2D: case F2I: case F2L:
        case FADD: case FDIV: case FMUL: case FNEG: case FREM: case FSUB:
        case FCMPL: case FCMPG:
        case FCONST_0: case FCONST_1: case FCONST_2:
        case FLOAD: case FLOAD_0: case FLOAD_1: case FLOAD_2: case FLOAD_3:
        case I2B: case I2C: case I2D: case I2F: case I2L: case I2S:
        case IADD: case IDIV: case IMUL: case INEG: case IREM: case ISUB:
        case IAND: case IOR: case ISHL: case ISHR: case IUSHR: case IXOR:
        case ICONST_M1: case ICONST_0: case ICONST_1: case ICONST_2:
        case ICONST_3: case ICONST_4: case ICONST_5:
        case ILOAD: case ILOAD_0: case ILOAD_1: case ILOAD_2: case ILOAD_3:
        case INSTANCEOF:
        case L2D: case L2F: case L2I:
        case LADD: case LDIV: case LMUL: case LNEG: case LREM: case LSUB:
        case LAND: case LOR: case LSHL: case LSHR: case LUSHR: case LXOR:
        case LCMP:
        case LCONST_0: case LCONST_1:
        case LLOAD: case LLOAD_0: case LLOAD_1: case LLOAD_2: case LLOAD_3:
        case LDC: case LDC_W: case LDC2_W:
            if (isTargetProducer) {
                return true;
            }
            break;
        case JSR: case JSR_W:
            if (isTargetProducer) {
                throw new ClassFormatError("Stack operand is of type " +
                    "returnAddress");
            }
            break;
        case DUP:
            if (stackTopDist > 1) {
                if (matchPointer == stackTopDist - 2) {
                    matchPointer = stackTopDist - 1;
                }
                stackTopDist -= 1;
            }
            return false;
        case DUP_X1:
            if (stackTopDist > 2) {
                if (matchPointer == stackTopDist - 3) {
                    matchPointer = stackTopDist - 1;
                }
                stackTopDist -= 1;
            }
            return false;
        case DUP_X2:
            if (stackTopDist > 3) {
                if (matchPointer == stackTopDist - 4) {
                    matchPointer = stackTopDist - 1;
                }
                stackTopDist -= 1;
            }
            return false;
        case DUP2:
            if (stackTopDist > 2) {
                if (matchPointer == stackTopDist - 3) {
                    matchPointer = stackTopDist - 1;
                }
                else if (matchPointer == stackTopDist - 4) {
                    matchPointer = stackTopDist - 2;
                }
                if (stackTopDist > 3) {
                    stackTopDist -= 2;
                }
                else {
                    stackTopDist -= 1;
                }
            }
            return false;
        case DUP2_X1:
            if (stackTopDist > 3) {
                if (matchPointer == stackTopDist - 4) {
                    matchPointer = stackTopDist - 1;
                }
                else if (matchPointer == stackTopDist - 5) {
                    matchPointer = stackTopDist - 2;
                }
                if (stackTopDist > 4) {
                    stackTopDist -= 2;
                }
                else {
                    stackTopDist -= 1;
                }
            }
            return false;
        case DUP2_X2:
            if (stackTopDist > 4) {
                if (matchPointer == stackTopDist - 5) {
                    matchPointer = stackTopDist - 1;
                }
                else if (matchPointer == stackTopDist - 6) {
                    matchPointer = stackTopDist - 2;
                }
                if (stackTopDist > 5) {
                    stackTopDist -= 2;
                }
                else {
                    stackTopDist -= 1;
                }
            }
            return false;
        case AASTORE: case BASTORE: case CASTORE: case DASTORE: case FASTORE:
        case IASTORE: case LASTORE: case SASTORE:
        case ASTORE: case ASTORE_0: case ASTORE_1:
        case ASTORE_2: case ASTORE_3:
        case DSTORE: case DSTORE_0: case DSTORE_1:
        case DSTORE_2: case DSTORE_3:
        case FSTORE: case FSTORE_0: case FSTORE_1:
        case FSTORE_2: case FSTORE_3:
        case GOTO: case GOTO_W:
        case IF_ACMPEQ: case IF_ACMPNE:
        case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT:
        case IF_ICMPLE: case IF_ICMPGT: case IF_ICMPGE:
        case IFEQ: case IFNE: case IFLT: case IFLE: case IFGT: case IFGE:
        case IFNONNULL: case IFNULL:
        case IINC:
        case ISTORE: case ISTORE_0: case ISTORE_1:
        case ISTORE_2: case ISTORE_3:
        case LOOKUPSWITCH: case TABLESWITCH:
        case LSTORE: case LSTORE_0: case LSTORE_1:
        case LSTORE_2: case LSTORE_3:
        case MONITORENTER: case MONITOREXIT:
        case NOP:
        case POP: case POP2:
        case PUTFIELD: case PUTSTATIC:
        case RET:
        case WIDE:
            break;
        case SWAP:
            if (stackTopDist < 2) {
                for (int i = 0; i < 2 - stackTopDist; i++) {
                    stackTopDist += 1;
                }
            }
            else {
                if (matchPointer == stackTopDist - 1) {
                    matchPointer -= 1;
                }
                else if (matchPointer == stackTopDist - 2) {
                    matchPointer += 1;
                }
            }
            return false;
        default:
            throw new ClassFormatError("Illegal instruction opcode");
        }
        
        if (pushCount > popCount) {
            for (int i = 0; i < changeCount; i++) {
                stackTopDist -= 1;
            }
        }
        else if (pushCount < popCount) {
            for (int i = 0; i < changeCount; i++) {
                stackTopDist += 1;
            }
        }
        
        return false;
    }

    /**
     * Reports whether the next instruction to be passed to the reverse
     * stack simulator may potntially be identified as the producer of
     * the stack operand of interest.
     *
     * @return <code>true</code> if the next instruction simulated by the
     * stack reverser is an operand producing instruction and the
     * simulator has determined that it produces the operand of interest.
     */
    public boolean atPossibleProducer() {
        return (stackTopDist == matchPointer);
    }

    /**
     * Creates a new reverse stack simulator which is in the same state as
     * this one.
     *
     * <p>This method should be used to prevent confusion when simulating
     * instruction sequences that contain branches (technically joins
     * following branches, in terms of proper forward execution). The
     * user of this object is responsible for maintaining some notion
     * of such control flow constructs, copying the stack for each
     * &quot;branch&quot; and taking the union of the results in some
     * manner.</p>
     *
     * @return A new reverse java stack which is in the same state as the
     * current stack; simulation of instructions on the new stack will not
     * affect the original stack.
     */
    public JVMStackReverser copy() {
        JVMStackReverser theClone = new JVMStackReverser(this.cpg);
        theClone.matchPointer = this.matchPointer;
        theClone.stackTopDist = this.stackTopDist;
        return theClone;
    }
}
