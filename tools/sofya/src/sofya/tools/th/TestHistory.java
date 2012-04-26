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

package sofya.tools.th;

import java.util.*;

/**
 * A TestHistory encapsulates test history information for a traced
 * method.
 *
 * <p>This class is modeled loosely upon the <code>dbh_th_info</code> struct
 * found in the Aristotle system, with appropriate affordances for
 * object-oriented design. Test history information is stored per block
 * in bit vectors, which are allocated on a need basis to reduce the
 * memory footprint. The usual methods for accessing and building test
 * history information for the method are provided. The
 * {@link sofya.tools.th.TestHistoryHandler} coordinates the management
 * of test histories for every method in a class.</p>
 *
 * <p>Once constructed, the number of method blocks in a test history cannot
 * be changed. The number of tests associated with a block can grow as
 * needed, however.</p>
 *
 * @author Alex Kinneer
 * @version 07/21/2005
 *
 * @see sofya.tools.TestHistoryBuilder
 * @see sofya.tools.th.TestHistoryHandler
 * @see sofya.viewers.TestHistoryViewer
 * @see sofya.ed.structural.ProgramEventDispatcher
 */
public class TestHistory {
    /** The array of bit vectors containing test history information
        for each block. */
    private BitSet[] blockHistories;
    /** The highest block ID, invariant once object is constructed. */
    private int highestBlockID;
    /** The highest possible test number. */
    private int highestTestID;
    /** Records the number of tests mapped to each block. Used to support
        the sparse test history file format. The zero'th element of the
        array records the number of tests mapped to the <em>method</em>. */
    private int[] blockTestCounts;
    
    /**************************************************************************
     * Default constructor is useless, since test history will have
     * no blocks allocated, so prevent its use.
     */
    private TestHistory() { }
    
    /**************************************************************************
     * Default constructor, creates a test history with the specified
     * number of method blocks and an initial number of tests.
     *
     * @param highestBlockID Highest possible block number in the method,
     * cannot be changed after instantiation.
     * @param highestTestID Initial number of tests to be associated with
     * the test history.
     */
    public TestHistory(int highestBlockID, int highestTestID) {
        this.highestBlockID = highestBlockID;
        this.highestTestID = highestTestID;
        
        // The zero'th element of the histories array is unused. This
        // is purely for indexing consistency.
        blockHistories = new BitSet[highestBlockID + 1];
        blockTestCounts = new int[highestBlockID + 1];
    }
    
    /**************************************************************************
     * Gets the highest block ID present in the method.
     *
     * @return The highest ID of any block found in the method.
     */
    public int getHighestBlockID() {
        return highestBlockID;
    }
    
    /**************************************************************************
     * Gets the current highest test number.
     *
     * @return The highest test number associated with this test history.
     */
    public int getHighestTestID() {
        return highestTestID;
    }
    
    /**************************************************************************
     * Reports whether the entire test history is empty, that is, no tests
     * traversed any blocks in the method.
     *
     * @return <code>true</code> if no tests hit any block in the method,
     * <code>false</code> otherwise.
     */
    public boolean isHistoryEmpty() {
        return (blockTestCounts[0] == 0);
    }
    
    /**************************************************************************
     * Reports whether the history for a block is empty, that is, no tests
     * traversed that block.
     *
     * @param blockID ID of the block to be checked.
     *
     * @return <code>true</code> if no tests hit the specified block in the
     * method, <code>false</code> otherwise.
     */
    public boolean isEmpty(int blockID) {
        return (blockTestCounts[blockID] == 0);
    }
    
    /**************************************************************************
     * Marks that a block is exercised by a given test.
     *
     * @param blockID ID of the block for which a new test is being added.
     * @param testID Number of the new test that exercised the block.
     */
    public void set(int blockID, int testID) {
        if ((blockID > highestBlockID) || (blockID < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                "block " + blockID);
        }
        if (testID < 0) {
            throw new IndexOutOfBoundsException("Invalid test number");
        }
        
        BitSet testList = blockHistories[blockID];
        if (testList == null) {
            testList = new BitSet(highestTestID);
            blockHistories[blockID] = testList;
        }
        testList.set(testID);
        
        blockTestCounts[blockID] += 1;
        blockTestCounts[0] += 1;
        
        if (testID > highestTestID) highestTestID = testID;
    }
    
    /**************************************************************************
     * Queries whether a block is exercised by a given test.
     *
     * @param blockID ID of the method block to be queried.
     * @param testID Number of the test for which is it is being checked
     * whether it exercises the given block.
     *
     * @return <code>true</code> if the test exercises the given block,
     * <code>false</code> otherwise.
     */
    public boolean query(int blockID, int testID) {
        if ((blockID > highestBlockID) || (blockID < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                "block " + blockID);
        }
        if ((testID < 0) || (testID > highestTestID)) {
            throw new IndexOutOfBoundsException("Invalid test number");
        }
        
        if (blockHistories[blockID] == null) {
            return false;
        }
        else {
            return blockHistories[blockID].get(testID);
        }
    }
    
    /**************************************************************************
     * Marks that a block is not exercised by a given test.
     *
     * @param blockID ID of the block for which a test is being removed.
     * @param testID Number of the test that no longer exercises the block.
     */
    public void unset(int blockID, int testID) {
        if ((blockID > highestBlockID) || (blockID < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                "block " + blockID);
        }
        if (testID < 0) {
            throw new IndexOutOfBoundsException("Invalid test number");
        }
        
        if (blockHistories[blockID] != null) {
            blockHistories[blockID].clear(testID);
        }
        
        blockTestCounts[blockID] -= 1;
        blockTestCounts[0] -= 1;
        
        if (testID > highestTestID) highestTestID = testID;
    }
    
    /**************************************************************************
     * Clears the test history for every method block.
     */
    public void clear() {
        BitSet testList = null;
        
        for (int blockID = 1; blockID < blockHistories.length; blockID++) {
            testList = blockHistories[blockID];
            if (testList != null) {
                testList = null;
            }
            
            blockTestCounts[blockID] = 0;
        }
        
        blockTestCounts[0] = 0;
    }
    
    /**************************************************************************
     * Sets the test history for a block to the specified bit vector,
     * represented by a hexadecimal string.
     *
     * <p>The hexadecimal string should be a contiguous string of hexadecimal
     * digits with no special formatting. Any existing test history
     * associated with the block will be replaced.</p>
     *
     * <p>This method will <i>not</i> reset the highest test ID for the
     * test history. If the specified bit vector is larger than the number
     * of tests currently set in the test history, excess tests will be
     * inaccessible ({@link TestHistory#query} will throw an
     * IndexOutOfBoundsException for those indices) and will be ignored when
     * the test history file is written. This is in part to mask the padding
     * required internally for test counts which are not multiples of four.</p>
     *
     * @param blockID ID of the block for which the test history is being
     * specified.
     * @param hexString New test history bit vector, in hexadecimal form.
     */
    void setTestVector(int blockID, String hexString) {
        if ((blockID > highestBlockID) || (blockID < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                "block " + blockID);
        }
        
        BitSet curList = blockHistories[blockID];
        if (curList != null) {
            int curCount = curList.cardinality();
            blockTestCounts[blockID] -= curCount;
            blockTestCounts[0] -= curCount;
        }
        
        BitSet testList = TestHistoryHandler.toBinary(hexString);
        int count = testList.cardinality();
        if (count != 0) {
            blockHistories[blockID] = testList;
        }
        else {
            blockHistories[blockID] = null;
        }
        
        blockTestCounts[blockID] += count;
        blockTestCounts[0] += count;
    }
    
    /**************************************************************************
     * Gets the test history for a block, represented as a hexadecimal string.
     *
     * <p>The bit vector will be end-padded to the nearest multiple of four
     * and converted to a contiguous string of hexadecimal digits.</p>
     *
     * @param blockID ID of the block for which the test history is being
     * retrieved.
     *
     * @return The block's test history bit vector, represented as a string
     * of contiguous hexadecimal digits.
     */
    String getTestVector(int blockID) {
        if ((blockID > highestBlockID) || (blockID < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " + 
                "block " + blockID);
        }
        
        BitSet testList = blockHistories[blockID];
        if (testList == null) {
            testList = new BitSet(highestTestID);
        }
        
        return TestHistoryHandler.toHex(testList, highestTestID);
    }
    
    /**************************************************************************
     * Creates a deep clone of this test history object.
     *
     * <p>This method is the object-oriented Java analogue to the Aristotle
     * <code>dbh_th_copy</code> procedure.</p>
     *
     * @return A new test history object with the same number of method blocks
     * and the same test histories associated with each block.
     */
    public Object clone() {
        TestHistory thClone = new TestHistory(this.highestBlockID,
                                              this.highestTestID);
        BitSet testList = null, cloneList = null;
        for (int blockID = 1; blockID < blockHistories.length; blockID++) {
            testList = blockHistories[blockID];
            if (testList != null) {
                cloneList = (BitSet) testList.clone();
                cloneList.or(testList);
                thClone.blockHistories[blockID] = cloneList;
            }
        }
        return thClone;
    }
    
    /**************************************************************************
     * Tests whether this test history object is equal to another test history.
     *
     * <p>This method first compares the number of method blocks and highest
     * test ID in this test history to that of the specified object. If both
     * are equal, it then performs a block-by-block comparison of the test
     * history bit vectors. The test histories are considered equal if and
     * only if every test history bit vector is equivalent.</p>
     *
     * <p>This method is the object-oriented Java analogue to the Aristotle
     * <code>dbh_th_equal</code> procedure.</p>
     *
     * @param obj Test history to which this test history should be
     * compared for equality.
     *
     * @return <code>true</code> if the specified test history is equal to
     * this test history, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (this == obj) return true;
        TestHistory th = (TestHistory) obj;
        if ((this.highestBlockID != th.highestBlockID) ||
            (this.highestTestID != th.highestTestID))
        {
            return false;
        }
        
        BitSet testList1 = null, testList2 = null;
        for (int blockID = 1; blockID < blockHistories.length; blockID++) {
            testList1 = this.blockHistories[blockID];
            testList2 = th.blockHistories[blockID];
            if ((testList1 == null) && (testList2 == null)) continue;
            if (((testList1 != null) && (testList2 == null)) ||
                ((testList1 == null) && (testList2 != null)) ||
                (!testList1.equals(testList2))) {
                return false;
            }
        }
        return true;
    }
    
    /**************************************************************************
     * Creates a test history that is the union of this test history and
     * another test history.
     *
     * <p>The specified test history must have the same number of method
     * blocks as this test history. The method will then take the logical
     * union of every test history bit vector in the two test histories.
     * In the event of a mismatch in the size of the bit vectors, the
     * resultant bit vector will always be the size of the larger vector.
     * The resulting test history will be an entirely new object, neither
     * the current test history or the specified test history will be
     * modified by this method.</p>
     *
     * <p>This method is the object-oriented Java analogue to the Aristotle
     * <code>dbh_th_union</code> procedure.</p>
     *
     * @param th Test history for which the union should be taken with
     * this test history.
     *
     * @return An entirely new test history object, representing the union
     * of this test history and the specified test history.
     */
    public TestHistory union(TestHistory th) {
        if (this.highestBlockID != th.highestBlockID) {
            throw new IllegalArgumentException("Methods do not have " +
               "matching blocks");
        }
        
        int maxTestID = (this.highestTestID >= th.highestTestID)
                        ? this.highestTestID
                        : th.highestTestID;
        
        TestHistory union_th = new TestHistory(this.highestBlockID, maxTestID);
        BitSet testList1 = null, testList2 = null, union_list = null;
        for (int blockID = 1; blockID < this.highestBlockID; blockID++) {
            testList1 = this.blockHistories[blockID];
            testList2 = th.blockHistories[blockID];
            if ((testList1 == null) && (testList2 == null)) {
                continue;
            }
            else if ((testList1 != null) && (testList2 == null)) {
                union_list = (BitSet) testList1.clone();
            }
            else if ((testList1 == null) && (testList2 != null)) {
                union_list = (BitSet) testList2.clone();
            }
            else {
                if (this.highestTestID == maxTestID) {
                    (union_list = (BitSet) testList1.clone()).or(testList2);
                }
                else {
                    (union_list = (BitSet) testList2.clone()).or(testList1);
                }
            }
            union_th.blockHistories[blockID] = union_list;
        }
        return union_th;
    }
}
