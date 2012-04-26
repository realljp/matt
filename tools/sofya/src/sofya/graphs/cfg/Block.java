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

package sofya.graphs.cfg;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;

import sofya.graphs.Node;
import static sofya.base.SConstants.*;

/**
 * A block represents a basic block of a control flow graph. A basic block is
 * one in which control enters at one end and exits at the other end without
 * being broken. A block knows about its label, type, subtype, offset where it
 * starts, and offset where it ends.
 *
 * @author Alex Kinneer
 * @version 09/24/2004
 */
public class Block extends Node {
    /** The integer mapping to the type of this block. */
    protected BlockType type;
    /** The integer mapping to the subtype of this block. */
    protected BlockSubType subType;
    /** The character label for the node type of this block. */
    protected BlockLabel label;
    /** The start offset of this block. */
    protected int startOffset;
    /** The end offset of this block. */
    protected int endOffset;
    /** Reference to a relevant entity at the start of the block.
        This field is not preserved when the CFG is written to file. */
    protected Object startRef;
    /** Reference to a relevant entity at the end of the block.
        This field is not preserved when the CFG is written to file. */
    protected Object endRef;
    
    /** Zero-length block array useful for specifying array cast types
        to methods such {@link Node#getSuccessors(Node[])}. */
    public static final Block[] ZL_ARRAY = new Block[0];
    
    /**
     * Creates a new block with fields initialized to default values.
     */
    public Block() {
        super();
        this.type = BlockType.BLOCK;
        this.subType = BlockSubType.DONTCARE;
        this.label = BlockLabel.BLOCK;
        this.startOffset = 0;
        this.endOffset = 0;
        this.startRef = null;
        this.endRef = null;
    }
    
    /**
     * Creates a new block with a given ID and all other fields initialized
     * to default values.
     *
     * @param id ID to be associated with the new block.
     */
    public Block(int id) {
        super(id);
        this.type = BlockType.BLOCK;
        this.subType = BlockSubType.DONTCARE;
        this.label = BlockLabel.BLOCK;
        this.startOffset = 0;
        this.endOffset = 0;
        this.startRef = null;
        this.endRef = null;
    }
    
    /**
     * Creates a new block with a given ID, type, and sub-type. Other
     * fields are initialized to default values.
     *
     * @param id ID to be associated with the new block.
     * @param type Main type of the new block.
     * @param subType Secondary type of the new block.
     */
    public Block(int id, BlockType type, BlockSubType subType) {
        super(id);
        this.type = type;
        this.subType = subType;
        this.label = BlockLabel.BLOCK;
        this.startOffset = 0;
        this.endOffset = 0;
        this.startRef = null;
        this.endRef = null;
    }
    
    /**
     * Creates a new block.
     *
     * @param id ID to be associated with the new block.
     * @param type Main type of the new block.
     * @param subType Secondary type of the new block.
     * @param label Label (character) associated with the new block.
     */
    public Block(int id, BlockType type, BlockSubType subType,
                 BlockLabel label) {
        super(id);
        this.type = type;
        this.subType = subType;
        this.label = label;
        this.startOffset = 0;
        this.endOffset = 0;
        this.startRef = null;
        this.endRef = null;
    }
    
    /**
     * Creates a new block.
     *
     * @param id ID to be associated with the new block.
     * @param type Main type of the new block.
     * @param subType Secondary type of the new block.
     * @param label Label (character) associated with the new block.
     * @param startOffset Offset of the first bytecode instruction in
     * the new block.
     * @param endOffset Offset of the last bytecode instruction in the
     * new block.
     */
     public Block(int id, BlockType type, BlockSubType subType,
                  BlockLabel label, int startOffset, int endOffset) {
        super(id);
        this.type = type;
        this.subType = subType;
        this.label = label;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startRef = null;
        this.endRef = null;
    }
   
    /**
     * Creates a new block.
     *
     * @param id ID to be associated with the new block.
     * @param type Main type of the new block.
     * @param subType Secondary type of the new block.
     * @param label Label (character) associated with the new block.
     * @param startOffset Offset of the first bytecode instruction in
     * the new block.
     * @param startRef Reference to an object with some meaningful correlation
     * to the start of the new block; typically used to reference the BCEL
     * instruction handle of the first instruction in the block.
     * @param endRef Reference to an object with some meaningful correlation
     * to the end of the new block; typically used to reference the BCEL
     * instruction handle of the last instruction in the block.
     */
     public Block(int id, BlockType type, BlockSubType subType,
                  BlockLabel label, int startOffset, int endOffset,
                  Object startRef, Object endRef) {
        super(id);
        this.type = type;
        this.subType = subType;
        this.label = label;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startRef = startRef;
        this.endRef = endRef;
    }

    /*************************************************************************
     * Sets the block type to the given value.
     *
     * @param bt Block type to assign to this block.
     */
    public void setType(BlockType bt) {
        type = bt;
    }

    /*************************************************************************
     * Gets the block type.
     *
     * @return The block type to which this block has been set.
     */
    public BlockType getType() {
        return type;
    }

    /*************************************************************************
     * Sets the block subtype to the given value.
     *
     * @param bst Block subtype to assign to this block.
     */
    public void setSubType(BlockSubType bst) {
        subType = bst;
    }

    /*************************************************************************
     * Gets the block subtype.
     *
     * @return The block subtype to which this block has been set.
     */
    public BlockSubType getSubType() {
        return subType;
    }
    
    /*************************************************************************
     * Sets the block label to the given value.
     *
     * @param bl Label which this block should be assigned .
     */
    public void setLabel(BlockLabel bl) {
        label = bl;
    }

    /*************************************************************************
     * Gets the block label.
     *
     * @return The label which has been assigned to this block.
     */
    public BlockLabel getLabel() {
        return label;
    }

    /*************************************************************************
     * Sets the start offset variable to the given value.
     *
     * @param i Integer representing the new start offset for this block.
     */
    public void setStartOffset(int i) {
        startOffset = i;
    }

    /*************************************************************************
     * Gets the start offset for this block.
     *
     * @return Integer representing the start offset for this block.
     */
    public int getStartOffset() {
        return startOffset;
    }

    /*************************************************************************
     * Sets the end offset variable to the given value.
     *
     * @param i Integer representing the new end offset for this block.
     */
    public void setEndOffset(int i) {
        endOffset = i;
    }

    /*************************************************************************
     * Gets the end offset for this block. 
     *
     * @return Integer representing the end offset for this block.
     */
    public int getEndOffset() {
        return endOffset;
    }
    
    /*************************************************************************
     * Sets a reference to an object of interest to be associated with
     * the start of the block.
     *
     * <p>For example, the {@link sofya.graphs.cfg.CFG} class stores a
     * reference to the BCEL <code>InstructionHandle</code> which represents
     * the beginning of the block to improve the performance of the
     * {@link sofya.ed.cfInstrumentor}.</p>
     *
     * @param ref Reference to object to be associated with the start of
     * the block.
     */
    public void setStartRef(Object ref) {
        startRef = ref;
    }
    
    /*************************************************************************
     * Gets the reference to an object of interest associated with the
     * start of the block.
     *
     * @return Reference to an object associated with the start of the
     * block.
     */
    public Object getStartRef() {
        return startRef;    
    }
    
    /*************************************************************************
     * Sets a reference to an object of interest to be associated with
     * the end of the block.
     *
     * <p>For example, the {@link sofya.graphs.cfg.CFG} class stores a
     * reference to the BCEL <code>InstructionHandle</code> which represents
     * the end of the block to improve the performance of the
     * {@link sofya.ed.cfInstrumentor}.</p>
     *
     * @param ref Reference to object to be associated with the end of
     * the block.
     */
    public void setEndRef(Object ref) {
        endRef = ref;
    }
    
    /*************************************************************************
     * Gets the reference to an object of interest associated with the
     * end of the block.
     *
     * @return Reference to an object associated with the end of the
     * block.
     */
    public Object getEndRef() {
        return endRef;
    }
    
    /**
     * Returns a direct reference to the list of predecessors, for use
     * by handlers.
     *
     * @return A direct reference to the list of predecessors to this
     * block.
     */
    List<Node> predecessorList() {
        return predecessors;
    }
    
    /**
     * Returns a direct reference to the list of successors, for use
     * by handlers.
     *
     * @return A direct reference to the list of successors to this
     * block.
     */
    List<Node> successorList() {
        return successors;
    }
    
    /*************************************************************************
     * Gets the successors of this block which are of a given type and
     * subtype.
     *
     * @param type Major type that must be matched for a successor to
     * be returned.
     * @param subType Subtype that must be matched for a successor to
     * be returned.
     *
     * @return An array of this node's successors which are of the given
     * type and subtype, which may have zero elements. 
     */
    public Block[] getSuccessors(BlockType type, BlockSubType subType) {
        ArrayList<Object> matches = new ArrayList<Object>();
        ListIterator li = successors.listIterator();
        int size = successors.size();
        for (int i = size; i-- > 0; ) {
            Block curSuccessor = (Block) li.next();
            if ((curSuccessor.type == type) &&
                    (curSuccessor.subType == subType)) {
                matches.add(curSuccessor);
            }
        }
        
        return (Block[]) matches.toArray(new Block[matches.size()]);
    }
    
    /*************************************************************************
     * Returns a string representation of this block in the form:<br>
     * <code>(nodeID, (nodeType, nodeSubType, nodeLabel),
     * [startOffset, endOffset])</code>.
     *
     * @return This block represented as a string.
     *
     * @see sofya.graphs.Node#toString
     */
    public String toString() {
        return new String("(" + super.toString() +
                          ", (" + type + ", " + subType + ", " + label +
                          "), " + "[" + startOffset + ", " + endOffset +
                          "])");
    }
}

/****************************************************************************/
