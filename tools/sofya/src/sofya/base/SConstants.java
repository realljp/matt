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

import sofya.base.exceptions.SofyaError;

/**
 * Sofya constants.
 *
 * @version 02/28/2007
 */
public final class SConstants {
    /*
     * Most of the enumerations defined here publicly export their internal
     * primitive values. This design is intentional to allow switch statements
     * to operate on those enumerated types, such as in the instrumentor and
     * event dispatcher classes, and is a critical aspect of execution
     * performance. This is particularly true of the event dispatcher classes,
     * which must switch on the type of each trace object witnessed during a
     * program execution to properly record dispatch events, an action which
     * may often occur millions of times during a program execution. Access
     * to the primitive values of enumerated types is also necessary in some
     * cases for efficient serialization of type data to database files. The
     * type safety advantage of these enumerations is still retained, however,
     * by requiring an instance element of the enumeration where appropriate.
     */
    
    private SConstants() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Type-safe enumeration for block types.
     */
    public static final class BlockType {
        private int typeCode = IBLOCK;

        /** Integer constant for <i>entry</i> block */
        public static final int IENTRY = 45;
        /** Integer constant for <i>exit</i> block */
        public static final int IEXIT = 46;
        /** Integer constant for <i>call</i> block */
        public static final int ICALL = 44;
        /** Integer constant for <i>return</i> block (return from call) */
        public static final int IRETURN = 50;
        /** Integer constant for generic block */
        public static final int IBLOCK = 54;

        /** Type-safe constant for <i>entry</i> block. */
        public static final BlockType ENTRY = new BlockType(IENTRY);
        /** Type-safe constant for <i>exit</i> block. */
        public static final BlockType EXIT = new BlockType(IEXIT);
        /** Type-safe constant for <i>call</i> block. */
        public static final BlockType CALL = new BlockType(ICALL);
        /** Type-safe constant for <i>return</i> block. */
        public static final BlockType RETURN = new BlockType(IRETURN);
        /** Type-safe constant for <i>basic</i> block. */
        public static final BlockType BLOCK = new BlockType(IBLOCK);

        /** Bitmask to enable basic blocks. */
        public static final int MASK_BASIC   = 0x00000001;
        /** Bitmask to enable entry blocks. */
        public static final int MASK_ENTRY   = 0x00000002;
        /** Bitmask to enable exit blocks. */
        public static final int MASK_EXIT    = 0x00000004;
        /** Bitmask to enable call blocks. */
        public static final int MASK_CALL    = 0x00000008;
        /** Bitmask to enable return blocks. */
        public static final int MASK_RETURN  = 0x00000010;
        /** Bitmask for screening for valid block type bits. */
        public static final int MASK_VALID   = 0x0000001F;

        private BlockType() { }
        private BlockType(int typeCode) {
            this.typeCode = typeCode;
        }

        /**
         * Converts this type-safe constant to a bitmask.
         */
        public int toMask() {
            switch (typeCode) {
            case IBLOCK: return MASK_BASIC;
            case IENTRY: return MASK_ENTRY;
            case IEXIT: return MASK_EXIT;
            case ICALL: return MASK_CALL;
            case IRETURN: return MASK_RETURN;
            default: throw new SofyaError();
            }
        }

        /**
         * Converts this type-safe constant to an integer (for serialization).
         */
        public int toInt() {
            return typeCode;
        }

        /**
         * Converts an integer to its equivalent type-safe block type
         * constant (for deserialization).
         */
        public static BlockType fromInt(int i) {
            switch (i) {
            case IBLOCK: return BLOCK;
            case IENTRY: return ENTRY;
            case IEXIT: return EXIT;
            case ICALL: return CALL;
            case IRETURN: return RETURN;
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Returns a string representation of the block type.
         */
        public String toString() {
            switch (typeCode) {
            case IBLOCK: return "basic";
            case IENTRY: return "entry";
            case IEXIT: return "exit";
            case ICALL: return "call";
            case IRETURN: return "return";
            default: throw new SofyaError();
            }
        }

        /**
         * Converts a bitmask encoding enabled block types into a string
         * representation of the enabled types.
         */
        public static String toString(int bitMask) {
            StringBuilder sb = new StringBuilder();
            if ((bitMask & MASK_VALID) == 0) {
                throw new IllegalArgumentException("Invalid object " +
                                                   "type flags");
            }
            if ((bitMask & MASK_BASIC) == MASK_BASIC) {
                sb.append("Basic ");
            }
            if ((bitMask & MASK_ENTRY) == MASK_ENTRY) {
                sb.append("Entry ");
            }
            if ((bitMask & MASK_EXIT) == MASK_EXIT) {
                sb.append("Exit ");
            }
            if ((bitMask & MASK_CALL) == MASK_CALL) {
                sb.append("Call ");
            }
            if ((bitMask & MASK_RETURN) == MASK_RETURN) {
                sb.append("Return ");
            }
            return sb.substring(0, sb.length() - 1);
        }
    }

    /**
     * Type-safe enumeration for block subtypes
     */
    public static final class BlockSubType {
        private int typeCode = -100;

        /** Integer constant for subtype <i>if</i> */
        public static final int IIF = 71;
        /** Integer constant for subtype <i>goto</i> */
        public static final int IGOTO = 65;
        /** Integer constant for subtype <i>jsr</i> (enter finally block) */
        public static final int IJSR = 68;
        /** Integer constant for subtype <i>switch</i> */
        public static final int ISWITCH = 75;
        /** Integer constant for subtype <i>return to caller</i> */
        public static final int IRETURN = 61;
        /** Integer constant for subtype <i>finally-return</i> */
        public static final int IFINALLY = 95;
        /** Integer constant for subtype <i>throw</i> */
        public static final int ITHROW = 96;
        /** Integer constant for subtype <i>summary throw</i> (aggregate of
            all thrown types which cannot be precisely determined) */
        public static final int ISUMMARYTHROW = 99;
        /** Integer constant for subtype <i>System.exit</i> */
        public static final int ISYSTEMEXIT = 97;
        /** Integer constant for subtype <i>don't care</i> */
        public static final int IDONTCARE = -100;

        /** Type-safe constant for <i>if</i> subtype */
        public static final BlockSubType IF = new BlockSubType(IIF);
        /** Type-safe constant for <i>goto</i> subtype */
        public static final BlockSubType GOTO = new BlockSubType(IGOTO);
        /** Type-safe constant for <i>jsr (Jump Subroutine)</i> subtype */
        public static final BlockSubType JSR = new BlockSubType(IJSR);
        /** Type-safe constant for <i>Switch</i> subtype */
        public static final BlockSubType SWITCH = new BlockSubType(ISWITCH);
        /** Type-safe constant for <i>Return</i> subtype */
        public static final BlockSubType RETURN = new BlockSubType(IRETURN);
        /** Type-safe constant for <i>Finally (RET)</i> subtype */
        public static final BlockSubType FINALLY = new BlockSubType(IFINALLY);
        /** Type-safe constant for <i>Throw</i> subtype */
        public static final BlockSubType THROW = new BlockSubType(ITHROW);
        /** Type-safe constant for block subtype associated with the node
            representing all exceptional exits which are not precisely
            represented in the control flow (such as exceptional exits
            caused by operators or array operations) */
        public static final BlockSubType SUMMARYTHROW =
            new BlockSubType(ISUMMARYTHROW);
        /** Type-safe constant for <i>System.exit</i> subtype */
        public static final BlockSubType SYSTEMEXIT =
            new BlockSubType(ISYSTEMEXIT);
        /** Type-safe constant for <i>don't care</i> about block subtype */
        public static final BlockSubType DONTCARE =
            new BlockSubType(IDONTCARE);

        private BlockSubType() { }
        private BlockSubType(int typeCode) {
            this.typeCode = typeCode;
        }

        /**
         * Converts this type-safe constant to an integer (for serialization).
         */
        public int toInt() {
            return typeCode;
        }

        /**
         * Converts an integer to its equivalent type-safe block subtype
         * constant (for deserialization).
         */
        public static BlockSubType fromInt(int i) {
            switch (i) {
            case IIF: return IF;
            case IGOTO: return GOTO;
            case IJSR: return JSR;
            case ISWITCH: return SWITCH;
            case IRETURN: return RETURN;
            case IFINALLY: return FINALLY;
            case ITHROW: return THROW;
            case ISUMMARYTHROW: return SUMMARYTHROW;
            case ISYSTEMEXIT: return SYSTEMEXIT;
            case IDONTCARE: return DONTCARE;
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Returns a string representation of the block subtype.
         */
        public String toString() {
            switch (typeCode) {
            case IIF: return "if";
            case IGOTO: return "goto";
            case IJSR: return "jsr";
            case ISWITCH: return "switch";
            case IRETURN: return "return";
            case IFINALLY: return "ret";
            case ITHROW: return "throw";
            case ISUMMARYTHROW: return "sumthrow";
            case ISYSTEMEXIT: return "exit";
            case IDONTCARE: return "dontcare";
            default: throw new SofyaError();
            }
        }
    }

    /**
     * Type-safe enumeration for block labels.
     */
    public static final class BlockLabel {
        private char label = 'K';

        private BlockLabel() { }
        private BlockLabel(char label) {
            this.label = label;
        }

        /**
         * Converts this type-safe constant to a character (for serialization).
         */
        public char toChar() {
            return label;
        }

        /**
         * Converts a character to its equivalent type-safe block label
         * constant (for deserialization).
         */
        public static BlockLabel fromChar(char c) {
            switch (c) {
            case 'K': return BLOCK;
            case 'E': return ENTRY;
            case 'X': return EXIT;
            case 'F': return CALL;
            case 'T': return RETURN;
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Returns a string representation of the block label.
         */
        public String toString() {
            return String.valueOf(label);
        }

        /** Character label for block type <i>Entry</i> */
        public static final BlockLabel ENTRY = new BlockLabel('E');
        /** Character label for block type <i>Exit</i> */
        public static final BlockLabel EXIT = new BlockLabel('X');
        /** Character label for block type <i>Call</i> */
        public static final BlockLabel CALL = new BlockLabel('F');
        /** Character label for block type <i>Return</i> */
        public static final BlockLabel RETURN = new BlockLabel('T');
        /** Character label for block type <i>Basic</i> */
        public static final BlockLabel BLOCK = new BlockLabel('K');
    }

    /**
     * Type-safe enumeration for branch object types.
     */
    public static final class BranchType {
        private int typeCode = MASK_IF;

        /** Bitmask to enable <code>if</code> branches. */
        public static final int MASK_IF     = 0x00000001;
        /** Bitmask to enable <code>switch</code> branches. */
        public static final int MASK_SWITCH = 0x00000002;
        /** Bitmask to enable <code>throw</code> branches. */
        public static final int MASK_THROW  = 0x00000004;
        /** Bitmask to enable call branches. */
        public static final int MASK_CALL   = 0x00000008;
        /** Bitmask to enable entry branches. */
        public static final int MASK_ENTRY  = 0x00000010;
        /** Bitmask to enable other branches
            (such as summary exit branches). */
        public static final int MASK_OTHER  = 0x00000020;
        /** Bitmask for screening for valid branch type bits. */
        public static final int MASK_VALID  = 0x0000003F;

        /** Integer constant for <i>if</i> */
        public static final int IIF     = MASK_IF;
        /** Integer constant for <i>switch</i> */
        public static final int ISWITCH = MASK_SWITCH;
        /** Integer constant for <i>throw</i> */
        public static final int ITHROW  = MASK_THROW;
        /** Integer constant for <i>call</i> */
        public static final int ICALL   = MASK_CALL;
        /** Integer constant for <i>entry</i> */
        public static final int IENTRY  = MASK_ENTRY;
        /** Integer constant for <i>other</i> */
        public static final int IOTHER  = MASK_OTHER;

        public static final int IDONTCARE = -1;

        /** Type-safe constant for <i>if</i> */
        public static final BranchType IF = new BranchType(MASK_IF);
        /** Type-safe constant for <i>switch</i> */
        public static final BranchType SWITCH = new BranchType(MASK_SWITCH);
        /** Type-safe constant for <i>throw</i> */
        public static final BranchType THROW = new BranchType(MASK_THROW);
        /** Type-safe constant for <i>call</i> */
        public static final BranchType CALL = new BranchType(MASK_CALL);
        /** Type-safe constant for <i>entry</i> */
        public static final BranchType ENTRY = new BranchType(MASK_ENTRY);
        /** Type-safe constant for <i>other</i> */
        public static final BranchType OTHER = new BranchType(MASK_OTHER);

        public static final BranchType DONTCARE = new BranchType(IDONTCARE);

        private BranchType() { }
        private BranchType(int typeCode) {
            this.typeCode = typeCode;
        }

        /**
         * Converts this type-safe constant to a bitmask.
         */
        public int toMask() {
            switch (typeCode) {
            case IIF: return MASK_IF;
            case ISWITCH: return MASK_SWITCH;
            case ITHROW: return MASK_THROW;
            case ICALL: return MASK_CALL;
            case IENTRY: return MASK_ENTRY;
            case IOTHER: return MASK_OTHER;
            case IDONTCARE: return MASK_VALID;
            default: throw new SofyaError();
            }
        }

        /**
         * Converts this type-safe constant to an integer
         * (for serialization).
         */
        public int toInt() {
            return typeCode;
        }

        /**
         * Converts an integer to its equivalent type-safe branch type
         * constant (for deserialization).
         */
        public static BranchType fromInt(int i) {
            switch(i) {
            case IIF: return IF;
            case ISWITCH: return SWITCH;
            case ITHROW: return THROW;
            case ICALL: return CALL;
            case IENTRY: return ENTRY;
            case IOTHER: return OTHER;
            case IDONTCARE: return DONTCARE;
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Returns a string representation of the branch type.
         */
        public String toString() {
            switch(typeCode) {
            case IIF: return "if";
            case ISWITCH: return "switch";
            case ITHROW: return "throw";
            case ICALL: return "call";
            case IENTRY: return "entry";
            case IOTHER: return "other";
            case IDONTCARE: return "dontcare";
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Converts a bitmask encoding enabled branch types into a string
         * representation of the enabled types.
         */
        public static String toString(int bitMask) {
            StringBuilder sb = new StringBuilder();
            if ((bitMask & MASK_VALID) == 0) {
                throw new IllegalArgumentException("Invalid object type flags");
            }
            if ((bitMask & MASK_IF) == MASK_IF) {
                sb.append("If ");
            }
            if ((bitMask & MASK_SWITCH) == MASK_SWITCH) {
                sb.append("Switch ");
            }
            if ((bitMask & MASK_THROW) == MASK_THROW) {
                sb.append("Throw ");
            }
            if ((bitMask & MASK_CALL) == MASK_CALL) {
                sb.append("Call ");
            }
            if ((bitMask & MASK_ENTRY) == MASK_ENTRY) {
                sb.append("Entry ");
            }
            if ((bitMask & MASK_OTHER) == MASK_OTHER) {
                sb.append("Other ");
            }
            return sb.substring(0, sb.length() - 1);
        }
    }

    /**
     * Base class for type-safe class constants encoding the types of
     * program entities (trace objects) that can be instrumented and traced.
     */
    public abstract static class TraceObjectType {
        /** Integer constant for the basic block trace object type. */
        public static final int IBASIC_BLOCK = BlockObjectType.objCode;
        /** Integer constant for the branch edge trace object type. */
        public static final int IBRANCH_EDGE = BranchObjectType.objCode;
        /** Integer constant for the semantic event object type. */
        public static final int ISEMANTIC_EVENT = EDObjectType.objCode;

        /** Type-safe constant for the basic block trace object type. */
        public static final TraceObjectType BASIC_BLOCK =
            new BlockObjectType();
        /** Type-safe constant for the branch edge trace object type. */
        public static final TraceObjectType BRANCH_EDGE =
            new BranchObjectType();
        /** Type-safe constant for the semantic event trace object type. */
        public static final TraceObjectType SEMANTIC_EVENT =
            new EDObjectType();

        private TraceObjectType() { }

        /**
         * Converts this type-safe constant to an integer
         * (for serialization).
         */
        public abstract int toInt();

        /**
         * Converts an integer to its equivalent type-safe trace object
         * type constant (for deserialization).
         */
        public static TraceObjectType fromInt(int i) {
            switch (i) {
            case BlockObjectType.objCode: return BASIC_BLOCK;
            case BranchObjectType.objCode: return BRANCH_EDGE;
            case EDObjectType.objCode: return SEMANTIC_EVENT;
            default: throw new IllegalArgumentException();
            }
        }

        /**
         * Returns the bitmask with all valid bits for this trace object
         * type set to 1 (one).
         */
        public abstract int validMask();

        /**
         * Converts a bitmask for this trace object type into a string
         * representation indicating the associated types of entities
         * marked as enabled in the bitmask.
         */
        public abstract String toString(int bitMask);
    }

    /**
     * Type-safe class constant to represent basic blocks as the object
     * type for instrumentation and tracing.
     */
    public static final class BlockObjectType extends TraceObjectType {
        private static final int objCode = 20;

        private BlockObjectType() { }

        public int toInt() {
            return objCode;
        }

        public int validMask() {
            return BlockType.MASK_VALID;
        }

        /**
         * Returns a string representation of the basic block object type.
         */
        public String toString() {
            return "Basic Block";
        }

        public String toString(int bitMask) {
            return BlockType.toString(bitMask);
        }
    }

    /**
     * Type-safe class constant to represent branch edges as the object
     * type for instrumentation and tracing.
     */
    public static final class BranchObjectType extends TraceObjectType {
        private static final int objCode = 21;

        private BranchObjectType() { }

        public int toInt() {
            return objCode;
        }

        public int validMask() {
            return BranchType.MASK_VALID;
        }

        /**
         * Returns a string representation of the branch edge object type.
         */
        public String toString() {
            return "Branch Edge";
        }

        public String toString(int bitMask) {
            return BranchType.toString(bitMask);
        }
    }

    /**
     * Type-safe class constant to represent semantic events
     * as the object type for instrumentation and tracing.
     */
    public static final class EDObjectType extends TraceObjectType {
        private static final int objCode = 22;

        private EDObjectType() { }

        public int toInt() {
            return objCode;
        }

        public int validMask() {
            return 0;
        }

        /**
         * Returns a string representation of the semantic event
         * object type.
         */
        public String toString() {
            return "Semantic Event";
        }

        public String toString(int bitMask) {
            return "";
        }
    }

    /** Default port used by socket-based event dispatchers
        and instrumentors. */
    public static final int DEFAULT_PORT = 27369;	// Arbitrary
    /** Signal used in synchronized execution of the block sequence
        processor to force a handshake between the event dispatcher
        and the subject SocketProbe. */
    public static final int SIG_ECHO = 0;
    /** Signal used in synchronized execution of the block sequence
        processor to test whether the subject is still running. */
    public static final int SIG_CHKALIVE = 1;

    /** Flag that indicates that the instrumentation
        is no longer supported. */
    public static final int INST_OLD_UNSUPPORTED = -2;
    /** Flag that indicates that the instrumentation
        is compatible with all tracers. */
    public static final int INST_COMPATIBLE = 1;
    /** Flag that indicates that the instrumentation
        is optimized for coverage traces. */
    public static final int INST_OPT_NORMAL = 2;
    /** Flag that indicates that the instrumentation
        is optimized for sequence traces. */
    public static final int INST_OPT_SEQUENCE = 4;
    /** Flag that indicates that the instrumentation is to be
        used to generate trace hashes. */
    public static final int INST_TRACE_HASHING = 5;
}
