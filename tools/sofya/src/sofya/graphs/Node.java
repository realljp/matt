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

package sofya.graphs;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Class to represent a node in a graph. Every node has an ID, a list
 * of predecessors, and a list of of successors.
 *
 * <p>A node P is a predecessor to this node if there is an edge from P to
 * this node. A node S is a successor to this node if there is an edge from
 * this node to S.</p>
 *
 * @author Alex Kinneer
 * @version 09/22/2004
 *
 * @see sofya.graphs.Graph
 */
public class Node {
    /** The node's unique ID. */
    protected int nodeID;
    /** Collection of the node's successors. */
    protected List<Node> successors = new ArrayList<Node>(4);
    /** Collection of the node's predecessors. */
    protected List<Node> predecessors = new ArrayList<Node>(4);
    
    /** Zero-length node array useful for specifying array cast types
        to methods such {@link Node#getSuccessors(Node[])}. */
    public static final Node[] ZL_ARRAY = new Node[0];
    
    /*************************************************************************
     * Creates a new node with ID zero.
     */
    public Node() {
        this.nodeID = 0;
    }
    
    /*************************************************************************
     * Creates a new node with a given ID.
     */
    public Node(int id) {
        this.nodeID = id;
    }

    /*************************************************************************
     * Sets this node's ID.
     *
     * @param n ID to be assigned to this node.
     */
    public void setID(int n) {
        nodeID = n;
    }

    /*************************************************************************
     * Gets this node's ID.
     *
     * @return The ID assigned to this node. 
     */
    public int getID() {
        return nodeID;
    }
    
    /*************************************************************************
     * Adds a node to this node's successor list.
     *
     * @param n Node to be added to the successor list.
     */
    public void addSuccessor(Node n) {
        successors.add(n);
    }

    /*************************************************************************
     * Removes a node from this node's successor list.
     *
     * @param n Node to be removed from the successor list. 
     */
    public void removeSuccessor(Node n) {
        successors.remove(n);
    }

    /*************************************************************************
     * Gets this node's successor list.
     *
     * @return An array of this node's sucessors. 
     */
    public Node[] getSuccessors() {
        return (Node[]) successors.toArray(new Node[successors.size()]);
    }
    
    /*************************************************************************
     * Gets this node's successors in a type-specific array.
     
     * @param a Array which will be used to determine the runtime type
     * of the array returned by this method.
     *
     * @return An array of this node's sucessors. 
     */
    public Node[] getSuccessors(Node[] a) {
        return (Node[]) successors.toArray(a);
    }
    
    /*************************************************************************
     * Gets this node's successors as a <code>java.util.List</code>
     * (unmodifiable).
     *
     * @return A list of this node's successors. 
     */
    public List<Node> getSuccessorsList() {
        return Collections.unmodifiableList(successors);
    }

    /*************************************************************************
     * Adds a node to this node's predecessor list.
     *
     * @param n Node to be added to the predecessor list. 
     */
    public void addPredecessor(Node n) {
        predecessors.add(n);
    }

    /*************************************************************************
     * Removes a node from this node's predecessor list.
     *
     * @param n Node to be removed from the predecessor list. 
     */
    public void removePredecessor(Node n) {
        predecessors.remove(n);
    }

    /*************************************************************************
     * Gets this node's predecessor list.
     *
     * @return An array of this node's predecessors. 
     */
    public Node[] getPredecessors() {
        return (Node[]) predecessors.toArray(new Node[predecessors.size()]);
    }
    
    
    /*************************************************************************
     * Gets this node's predecessors in a type-specific array.
     
     * @param a Array which will be used to determine the runtime type
     * of the array returned by this method.
     *
     * @return An array of this node's sucessors. 
     */
    public Node[] getPredecessors(Node[] a) {
        return (Node[]) predecessors.toArray(a);
    }
    
    /*************************************************************************
     * Gets this node's predecessors as a <code>java.util.List</code>
     * (unmodifiable).
     *
     * @return A list of this node's predecessors. 
     */
    public List<Node> getPredecessorsList() {
        return Collections.unmodifiableList(predecessors);
    }

    /*************************************************************************
     * Gets the number of predecessors to this node.
     *
     * @return The number of nodes which are the source of a directed edge
     * to this node (only immediate predecessors are counted). 
     */
    public int getPredecessorCount() {
        return predecessors.size();
    }
    
    /*************************************************************************
     * Gets the number of successors of this node.
     *
     * @return The number of nodes which are the target of a directed edge
     * from this node (only immediate successors are counted). 
     */
    public int getSuccessorCount() {
        return successors.size();
    }

    /*************************************************************************
     * Returns a string representation of this node, which consists of the
     * node's ID.
     *
     * @return The node's ID as a string. 
     */
    public String toString() {
        return String.valueOf(nodeID);
    }

    /*************************************************************************
     * Test driver for the Node class.
     */
    public static void main(String args[])
    {
        Node node = new Node(1);
        System.out.println(node.getID());
        node.setID(4);
        System.out.println(node.getID());
    }
}

/****************************************************************************/
