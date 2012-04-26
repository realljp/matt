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

package sofya.mutator.selectors;

import java.util.Random;
import java.util.BitSet;
import java.util.NoSuchElementException;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

/**
 * A random sequence of non-repeating integer values.
 *
 * @author Alex Kinneer
 * @version 10/01/2005
 */
class RandomSequence {
    /** Hash to store the random values in the sequence; used only when
        it yields a savings in memory consumption. */
    private TIntHashSet selHash = null;
    /** Bitfield storing the random values in the sequence; values in the
        sequence are identified by setting the bit at the index
        corresponding the value minus one. */
    private BitSet selBits = new BitSet(1);
    /** Length of the sequence. */
    private int length = 0;
    /** Range of the sequence; the largest value that may appear in the
        sequence. */
    private int range = 0;
    
    // protected int dbgIterations = 0;
    
    RandomSequence() {
    }
    
    /**
     * Creates a new random sequence.
     *
     * @param length Length of the new sequence to be created.
     * @param range Range of the sequence to be created; e.g. the largest
     * value that may appear in the sequence.
     */
    RandomSequence(int length, int range) {
        newSequence(length, range);
    }
    
    /**
     * Generates a sequence of non-repeating random numbers between 1 and
     * a specified value; results of this method are stored internally
     * for efficient querying.
     *
     * <p>There is strong evidence to support the following claims about
     * the behavior of the provided algorithm:
     * <ul>
     * <li>It will select every value within the specified range with
     * equal probability.</li>
     * <li>The maximum number of internal iterations required to select
     * the specified quantity of numbers is bounded by the range. In
     * other words, the runtime of the algorithm is linear in the size
     * of the given range.</li>
     * <li>The algorithm is fast even for large sequence lengths and
     * value ranges.</li>
     * </ul>
     *
     * @param length Length of the sequence of numbers to be generated.
     * @param range Maximum value of a number to be generated in the
     * sequence.
     */
    protected void generateSequence(int length, int range) {
        // The algorithm chooses a random bit, then requests the next
        // clear (unselected bit). It then generates a random number
        // in the given range. If the number is less than or equal to
        // the count, it sets the bit, selecting the number corresponding
        // to the index of the bit (plus 1). In other words, it randomly
        // chooses bits and then sets them with a probability of
        // (count / range). This process is repeated until the desired
        // quantity of numbers have been selected. Note that if
        // count = range, the probability of setting a bit is 1, and the
        // algorithm will require a maximum of range iterations to
        // set all of the bits.
        
        Random rand = new Random();
        BitSet selRands = new BitSet(range);
        int genLength = 0;
        
        // dbgIterations = 0;
        while (genLength < length) {
            // dbgIterations += 1;
            int fBit = rand.nextInt(range);
            fBit = selRands.nextClearBit(fBit);
            if (fBit == range) {
                fBit = selRands.nextClearBit(0);
                if (fBit == range) {
                    throw new IllegalStateException("count > range");
                }
            }
            int randVal = rand.nextInt(range) + 1;
            if (randVal <= length) {
                selRands.set(fBit);
                genLength += 1;
            }
        }
        
        // If the space required to record the selected numbers as a bitset
        // is greater than the space required to store the selected numbers
        // as integers (32-bit), we convert it into a hash set containing
        // only the selected numbers.
        if (genLength < (selRands.size() / 32)) {
            TIntHashSet genRands = new TIntHashSet();
            for (int i = 0; i < selRands.size(); i++) {
                if (selRands.get(i)) {
                    genRands.add(i + 1);
                }
            }
            selHash = genRands;
            selBits = null;
        }
        else {
            selBits = selRands;
            selHash = null;
        }
        
        this.length = genLength;
        this.range = range;
    }
    
    /**
     * Resets this sequence to an new random sequence.
     *
     * @param length Length of the new sequence to be created.
     * @param range Range of the sequence to be created; e.g. the largest
     * value that may appear in the sequence.
     *
     * @return A reference to this changed random sequence.
     */
    public RandomSequence newSequence(int length, int range) {
        generateSequence(length, range);
        return this;
    }
    
    /**
     * Gets the length of this sequence.
     *
     * @return The length of this random sequence.
     */
    public int length() {
        return length;
    }
    
    /**
     * Gets the range of this sequence.
     *
     * @return The range of this random sequence (the largest number that
     * may appear in the sequence).
     */
    public int range() {
        return range;
    }
    
    /**
     * Queries whether a value appears in this sequence.
     *
     * @param val Value to check for existence in this random sequence.
     *
     * @return <code>true</code> if the given value appears in this random
     * sequence.
     */
    public boolean contains(int val) {
        if (selBits == null) {
            return selHash.contains(val);
        }
        else {
            return selBits.get(val - 1);
        }
    }
    
    /**
     * Gets an iterator over the values in this sequence.
     *
     * @return An iterator over the values in this random sequence.
     */
    public IntSequenceIterator iterator() {
        if (selBits == null) {
            return new TIntIteratorDecorator(selHash.iterator());
        }
        else {
            return new SequenceBitsIterator(selBits);
        }
    }
    
    /**
     * An iterator to return the values of the sequence from the underlying
     * bitfield.
     */
    private static class SequenceBitsIterator implements IntSequenceIterator {
        /** Bitfield storing the sequence being iterated. */
        private BitSet sequenceBits;
        /** Position of the iterator. */
        private int pos;
        
        private SequenceBitsIterator() {
            throw new AssertionError("Illegal constructor");
        }
        
        /**
         * Creates a new sequence interator.
         *
         * @param sequenceBits Bitfield storing the sequence to be iterated.
         */
        public SequenceBitsIterator(BitSet sequenceBits) {
            this.sequenceBits = sequenceBits;
            this.pos = sequenceBits.nextSetBit(0);
        }
        
        public boolean hasNext() {
            return (pos != -1);
        }
        
        public int nextInt() {
            if (pos == -1) {
                throw new NoSuchElementException();
            }
            int curPos = pos + 1;
            pos = sequenceBits.nextSetBit(pos + 1);
            return curPos;
        }
        
        public void remove() {
            sequenceBits.clear(pos);
        }
    }
    
    /**
     * Decorates a <code>trove TIntIterator</code> to support the
     * {@link IntSequenceIterator} interface.
     */
    private static class TIntIteratorDecorator implements IntSequenceIterator {
        /** Decorated <code>TIntIterator</code>. */
        private TIntIterator iterator;
        
        private TIntIteratorDecorator() {
            throw new AssertionError("Illegal constructor");
        }
        
        /**
         * Creates a new decorated for an <code>TIntIterator</code>.
         *
         * @param iterator Iterator to be decorated.
         */
        public TIntIteratorDecorator(TIntIterator iterator) {
            this.iterator = iterator;
        }
        
        public boolean hasNext() {
            return iterator.hasNext();
        }
        
        public int nextInt() {
            return iterator.next();
        }
        
        public void remove() {
            iterator.remove();
        }
    }
    
    /**
     * Test driver to check to behavior of the random sequence generator.
     */
    public static void main(String argv[]) {
        RandomSequence rs = new RandomSequence();
        int[] counters = new int[240];
        //int iterations = 0;
        
        TIntHashSet dupCheck = new TIntHashSet();
        for (int n = 0; n < 100000; n++) {
            rs.newSequence(35, 240);
            //iterations += rids.dbgIterations;
            IntSequenceIterator iterator = rs.iterator();
            int length = rs.length();
            for (int i = length; i-- > 0; ) {
                int val = iterator.nextInt();
                
                if (dupCheck.contains(val)) {
                    System.err.println("Duplicate");
                }
                
                //System.out.print(i + " ");
                dupCheck.add(val);
                counters[val - 1] += 1;
            }
            dupCheck.clear();
        }
        
        //System.out.println("Avg number of iterations: " +
        //        (iterations / 100000));
        
        for (int n = 0; n < 240; n++) {
            System.out.println(n + ": " + counters[n] + " (" +
                ((counters[n] / 3500000.0) * 100.0) + "%)");
        }
        
        System.out.println();
    }
}
