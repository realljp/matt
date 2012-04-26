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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import sofya.ed.semantic.EventSpecification.ArrayElementBounds;

/**
 * An interval list maintains a direct double linked list of nodes, each
 * of which describes a span from the set {0...Integer.MAX_VALUE}.
 * 
 * <p>Nodes in the list are eagerly merged or removed as new
 * intervals are added, such that the list always maintains the minimal
 * set of interval nodes necessary to describe the set of all intervals
 * that have been added to the list. An interval list supports only
 * additive modification -- intervals cannot be removed.</p>
 * 
 * <p>An interval list always has a special sentinel node that
 * identifies the end of the list. The maximum value for the interval
 * associated with this node may be -1, signifying no upper bound.
 * If the minimum value for the interval on the tail node is not -1,
 * the tail represents the unbounded interval from the minimum to
 * the maximum representable value, otherwise the tail node is purely
 * a sentinel marking the end of the list. The head node of the list
 * may have a minimum value of -1 for its associated interval. If the
 * maximum value of the interval associated with the head node is
 * not -1, the head node represents the interval from 0 to the maximum
 * value. The head node and tail node may be the same node, and
 * the tail node is permitted to specify an unbounded upper interval
 * in this case.</p>
 * 
 * <p>This class is especially useful for generating the minimum
 * number of conditional checks necessary to determine whether a given
 * value falls within any of a set of specified intervals, and is
 * used for this purpose to generate the constraint checks on
 * array element event indices provided by EDL.</p>
 * 
 * @author Alex Kinneer
 * @version 01/04/2007
 */
final class IntervalList {
    /** A special flag to signify that the list must permanently
        describe an unbounded interval (all possible values). This
        flag may be set when it is possible to determine that the
        intervals specifically added to the list describe the
        entire possible number space. */
    private boolean unbounded = false;
    
    /** Conditional compilation flag to enable assertions. */
    private static final boolean ASSERTS = true;
    /** Conditional compilation flag to enable debug outputs. */
    private static final boolean DEBUG = false;
    
    /**
     * A doubly-linked node that describes an interval (range
     * of numbers).
     */
    static class IntervalNode {
        int min;
        int max;

        IntervalNode prev;
        IntervalNode next;

        private IntervalNode() {
            throw new AssertionError("Illegal constructor");
        }

        IntervalNode(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public String toString() {
            return "{ " + min + ":" + max + " }";
        }
    }

    final IntervalNode TAIL = new IntervalNode(-1, -1);
    IntervalNode head = TAIL;

    /**
     * Creates a new interval list that includes all possible
     * values by default.
     */
    IntervalList() {
    }
    
    /**
     * Adds an interval to this list that matches the index bounds
     * described by an array element bounds object.
     * 
     * <p>It is possible that the <code>unbounded</code> flag may
     * be set as a consequence of calling this method.</p>
     * 
     * @param bounds Object describing bounds on the indexes of
     * elements in an array, to be interpreted as an interval to
     * be added to this list.
     */
    public final void addInterval(ArrayElementBounds bounds) {
        if (unbounded) return;
        
        if (bounds.min == ArrayElementBounds.NO_BOUND) {
            if (bounds.max == ArrayElementBounds.NO_BOUND) {
                unbounded(true);
            }
            else {
                addBelow(bounds.max);
            }
        }
        else if (bounds.max == ArrayElementBounds.NO_BOUND) {
            if (bounds.min == ArrayElementBounds.NO_BOUND) {
                unbounded(true);
            }
            else {
                addAbove(bounds.min);
            }
        }
        else {
            if (bounds.min > bounds.max) {
                addBelow(bounds.max);
                addAbove(bounds.min);
            }
            else {
                addInterval(bounds.min, bounds.max);
            }
        }
    }
    
    /**
     * Reports whether this interval list is permanently unbounded.
     * 
     * @return <code>true</code> if this list has been marked as
     * permanently unbounded; that is, the set of calls to
     * {@link #addInterval(ArrayElementBounds)} already observed
     * has been determined to describe all possible values.
     */
    public final boolean unbounded() {
        return unbounded;
    }
    
    /**
     * Specifies whether this interval list is permanently unbounded.
     * 
     * @param set <code>true</code> to mark this list as unbounded,
     * clearing the list, setting it to represent the unbounded
     * interval, and preventing any subsequent requests to add new
     * intervals from modifying the list.
     */
    public final void unbounded(boolean set) {
        if (set) {
            unbounded = true;
            TAIL.prev = null;
            TAIL.min = TAIL.max = -1;
            head = TAIL;
        }
        else {
            unbounded = false;
        }
    }

    /**
     * Adds an interval to this list.
     * 
     * @param min Minimum value (inclusive) of the interval to be
     * added to the list.
     * @param max Maximum value (inclusive) of the interval to be
     * added to the list.
     */
    public final void addInterval(int min, int max) {
        if (ASSERTS) {
            assert min <= max;
        }

        if (DEBUG) {
            System.out.println(" add: " + min + ":" + max);
        }
        
        if (head == TAIL) {
            IntervalNode newHead = new IntervalNode(min, max);
            newHead.next = TAIL;
            TAIL.prev = newHead;
            head = newHead;
            return;
        }

        // Handle head node
        if (min < head.min) {
            // +1 ensures we glue together adjacent intervals
            if ((max + 1) < head.min) {
                IntervalNode insertNode = new IntervalNode(min, max);
                insertNode.next = head;
                head.prev = insertNode;
                head = insertNode;
                return;
            }

            head.min = min;

            // Short circuit optimizations
            if (max <= head.max) {
                return;
            }
            else if (head.next == TAIL) {
                if (max > head.max) {
                    head.max = max;
                    return;
                }
            }

            resolveNext(head, max);
            return;
        }

        IntervalNode node = head;
        while (true) {
            if ((min - 1) <= node.max) {
                if (min >= node.min) {
                    // Extend previous node
                    if (DEBUG) {
                        System.out.println("    <EXTEND_PREV> " + node);
                    }
                    resolveNext(node, max);
                    return;
                }
                else {
                    if (DEBUG) {
                        System.out.println("    <INSERT> " + node);
                    }
                    IntervalNode insertNode = new IntervalNode(min, max);
                    insertNode.prev = node.prev;
                    insertNode.next = node;
                    node.prev.next = insertNode;
                    //node.prev = insertNode;
                    resolveNext(insertNode, max);
                    return;
                }
            }
            else {
                if (node.next != TAIL) {
                    node = node.next;
                }
                else {
                    // Append to end of list
                    if (DEBUG) {
                        System.out.println("    <END>");
                    }
                    IntervalNode appendNode = new IntervalNode(min, max);
                    appendNode.next = TAIL;
                    appendNode.prev = node;
                    node.next = appendNode;
                    TAIL.prev = appendNode;
                    return;
                }
            }
        }
    }

    /**
     * Internal helper method to determine the next node describing
     * an interval disjoint from, adjacent to, or subsuming the new
     * maximum to which a current interval is to be extended,
     * minimizing the node list in the process.
     * 
     * Any nodes describing intervals completely subsumed by extending
     * the current interval to the new maximum are discarded. If the
     * new maximum adjoins directly to the next interval, or is subsumed
     * by the next interval, that node is extended to include the
     * full interval and the current node to be extended is discarded
     * instead.
     * 
     * @param node Node from which to begin searching.
     * @param max New maximum to which the interval represented
     * by the starting node should be extended.
     */
    private final void resolveNext(IntervalNode node, int max) {
        IntervalNode updateNode = node;
        while (true) {
            if (DEBUG) {
                System.out.println("    " + node.toString());
            }
            if (node.next == TAIL) {
                updateNode.max = (max > node.max) ? max : node.max;
                updateNode.next = TAIL;
                TAIL.prev = updateNode;
                return;
            }
            else {
                // +1 ensures we glue together adjacent intervals
                if ((max + 1) >= node.next.min) {
                    node = node.next;
                }
                else {
                    updateNode.max = (max > node.max) ? max : node.max;
                    updateNode.next = node.next;
                    node.next.prev = updateNode;
                    return;
                }
            }
        }
    }

    /**
     * Adds to the list an interval extending from the smallest possible
     * value up to the given maximum.
     * 
     * @param max Maximum (inclusive) to which the added interval extends.
     */
    public void addBelow(int max) {
        if (DEBUG) {
            System.out.println(" add below: " + max);
        }
        
        // If value exceeds tail minimum, merge list back down to
        // include all, containing only tail as sentinel
        if ((TAIL.min != -1) && ((max + 1) >= TAIL.min)) {
            TAIL.prev = null;
            TAIL.min = -1;
            head = TAIL;
            return;
        }
        
        IntervalNode newHead = new IntervalNode(-1, max);
        newHead.next = head;
        head.prev = newHead;
        head = newHead;
        resolveNext(head, max);
    }

    /**
     * Adds to the list an interval extending from the largest possible
     * value down to the given minimum.
     * 
     * @param min Minimum (inclusive) to which the added interval extends.
     */
    public void addAbove(int min) {
        if (DEBUG) {
            System.out.println(" add above: " + min);
        }

        // Handle tail explicitly
        if (min < TAIL.min) {
            if (TAIL.prev == null) {
                if (DEBUG) {
                    System.out.println("<TAIL_MOD>");
                }
                TAIL.min = min;
                return;
            }
        }
        else if (TAIL.min == -1) {
            if (TAIL.prev == null) {
                if (DEBUG) {
                    System.out.println("<TAIL_MOD>");
                }
                TAIL.min = min;
                return;
            }
        }
        else {
            if (DEBUG) {
                System.out.println("<TAIL_NOCHANGE>");
            }
            return;
        }

        IntervalNode node = TAIL.prev;
        while (true) {
            if ((min - 1) > node.max) {
                // We cannot extend the node, but we need to bring
                // tail down to truncate the list to this interval
                node.next = TAIL;
                TAIL.prev = node;
                TAIL.min = min;
                break;
            }
            else {
                if (min >= node.min) {
                    // New min is within current node's interval,
                    // so replace it with tail
                    if (DEBUG) {
                        System.out.println("<TAIL_EXT> " + node);
                    }
                    if (node.prev != null) {
                        node.prev.next = TAIL;
                        TAIL.prev = node.prev;
                    }
                    else {
                        // Reached head, clear list...
                        TAIL.prev = null;
                        head = TAIL;
                    }
                    TAIL.min = node.min;
                    break;
                }
                else {
                    node = node.prev;
                }
            }
        }
    }
    
    /**
     * Serializes this interval list to an output stream.
     * 
     * @param out Stream to which the list will be serialized (as
     * binary data).
     * 
     * @throws IOException On any I/O error that prevents successful
     * serialization of the list.
     */
    public void serialize(DataOutputStream out) throws IOException {
        IntervalNode node = head;
        while (node != TAIL) {
            out.writeByte((byte) 0);
            out.writeInt(node.min);
            out.writeInt(node.max);
        }
        out.writeByte((byte) 1);
        out.writeInt(node.min);
        out.writeInt(node.max);
    }
    
    /**
     * Deserialize an interval list from an input stream.
     * 
     * <p><strong>Any contents of the current list will be
     * destroyed!</strong></p>
     * 
     * @param in Stream from which the list will be deserialized.
     * 
     * @throws IOException On any I/O error that prevents successful
     * deserialization of the list.
     */
    public void deserialize(DataInputStream in) throws IOException {
        IntervalNode lastNode;
        
        byte tailFlag = in.readByte();
        if (tailFlag == 1) {
            TAIL.min = in.readInt();
            TAIL.max = in.readInt();
            head = TAIL;
            return;
        }
        else {
            head = lastNode = new IntervalNode(in.readInt(), in.readInt());
        }
        
        while ((tailFlag = in.readByte()) != 1) {
            IntervalNode curNode =
                new IntervalNode(in.readInt(), in.readInt());
            lastNode.next = curNode;
            curNode.prev = lastNode;
            lastNode = curNode;
        }
        
        TAIL.min = in.readInt();
        TAIL.max = in.readInt();
        lastNode.next = TAIL;
        TAIL.prev = lastNode;
    }

    /**
     * Creates a string representation of this interval list.
     * 
     * @return A string containing a typical collections-style view
     * constructed from a forward traversal of the list.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[ ");
        IntervalNode node = head;
        while (node != null) {
            sb.append(node.min);
            sb.append(":");
            sb.append(node.max);
            sb.append(" ");
            node = node.next;
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Creates a string representation of this interval list from the
     * start to the end and back to the start.
     * 
     * <p>This is principally useful for checking the link consistency
     * of the list, to aid in validation and debugging.</p>
     * 
     * @return A string containing two views of the list, one constructed
     * from a forward traversal of the list, and one constructed from
     * a backward traversal of the list.
     */
    private String toStringForwardAndBack() {
        if (head == null) return "[ ]\n[ ]";

        StringBuilder sb = new StringBuilder();

        sb.append("[ ");
        IntervalNode node = head;
        while (true) {
            sb.append(node.min);
            sb.append(":");
            sb.append(node.max);
            sb.append(" ");

            if (node.next == null) {
                break;
            }
            else {
                node = node.next;
            }
        }
        sb.append("]\n");

        sb.append("[ ");
        while (true) {
            sb.append(node.min);
            sb.append(":");
            sb.append(node.max);
            sb.append(" ");

            if (node.prev == null) {
                break;
            }
            else {
                node = node.prev;
            }
        }
        sb.append("]");

        return sb.toString();
    }

    /** For testing. */
    static void main(String[] argv) {
        IntervalList theList = new IntervalList();
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(34, 37);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(22, 24);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(18, 21);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(13, 15);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(11, 26);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(7, 10);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(39, 42);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(28, 32);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(43, 48);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(30, 35);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(57, 62);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(54, 56);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(49, 53);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(4);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(6);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(26);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(68, 73);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(80, 82);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(89);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(83);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(70);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(97);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(25);
        System.out.println(theList.toStringForwardAndBack());

        theList.addInterval(30, 40);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(45);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(50);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(30);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(60);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(59);
        System.out.println(theList.toStringForwardAndBack());

        theList.addBelow(30);
        System.out.println(theList.toStringForwardAndBack());

        theList.addAbove(31);
        System.out.println(theList.toStringForwardAndBack());
    }
}

