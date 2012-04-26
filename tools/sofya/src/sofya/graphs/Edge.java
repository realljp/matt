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

/**
 * An edge is a component of a graph that represents the flow of control
 * from one node to another. Every edge maintains the IDs of its source
 * and sink nodes.
 *
 * @author Alex Kinneer
 * @version 09/16/2004
 *
 * @see sofya.graphs.Graph
 * @see sofya.graphs.Node
 */
public class Edge {
    /** The edge's ID. */
    protected int edgeID;
    /** ID of the node on which this edge is incident. */
    protected int succNodeID;
    /** ID of the node from which this edge originates. */
    protected int predNodeID;
    /** The edge's label. */ 
    protected String label;
    
    /** Zero-length edge array useful for specifying array cast types
        to methods such {@link Graph#getEdges(Node,Node,Edge[])}. */
    public static final Edge[] ZL_ARRAY = new Edge[0];
    
    /*************************************************************************
     * Creates an edge with ID zero, an empty label, and the successor and
     * predecessor nodes set to zero.
     */
    public Edge() {
        edgeID = 0;
        succNodeID = 0;
        predNodeID = 0;
        label = null;
    }

    /*************************************************************************
     * Creates an edge with the given label, ID, successor, and predecessor
     * nodes.
     *
     * @param id ID of the new edge.
     * @param s ID of the new edge's successor node.
     * @param p ID of the new edge's predecessor node.
     * @param label Label to be assigned to the new edge.
     */
    public Edge(int id, int s, int p, String label) {
        edgeID = id;
        succNodeID = s;
        predNodeID = p;
        this.label = label;
    }

    /*************************************************************************
     * Sets the ID of this edge.
     *
     * @param id New ID to assign to this edge.
     */
    public void setID(int id) {
        edgeID = id;
    }

    /*************************************************************************
     * Gets the ID of this edge.
     *
     * @return The ID assigned to this edge.
     */
    public int getID() {
        return edgeID;
    }

    /*************************************************************************
     * Sets the successor node of this edge.
     *
     * @param s ID of the node that is to be the new successor of this edge.
     */
    public void setSuccNodeID(int s)
    {
        succNodeID = s;
    }

    /*************************************************************************
     * Gets the successor node of this edge. 
     *
     * @return The ID of the successor node of this edge.
     */
    public int getSuccNodeID()
    {
        return succNodeID ;
    }

    /*************************************************************************
     * Sets the predecessor node of this edge.
     *
     * @param p ID of the node that is to be the new predecessor node of this
     * edge.
     */
    public void setPredNodeID(int p)
    {
        predNodeID = p;
    }

    /*************************************************************************
     * Gets the predecessor node of this edge. 
     *
     * @return The ID of the predecessor node of this edge.
     */
    public int getPredNodeID()
    {
        return predNodeID ;
    }

    /*************************************************************************
     * Sets the label for this edge.
     *
     * <p>Assigning version-dependent values to this field should be
     * avoided.</p>
     *
     * @param s New label to be assigned to this edge.
     */
    public void setLabel(String s) {
        label = s;
    }

    /*************************************************************************
     * Gets the label for this edge.
     *
     * @return The label assigned to this edge.
     */
    public String getLabel(){
        return label;
    }

    /*************************************************************************
     * Returns a string representation of this edge in the form:<br>
     * <code>label: sourceNodeID -&gt; sinkNodeID</code>.
     * <p>If the edge is unlabeled, the special token "<code>(nl)</code>" is
     * used for the label.</p>
     *
     * @return This edge represented as a string. 
     */
    public String toString() {
        if ((label == null) || (label.length() == 0)) {
            return new String("(nl): " + predNodeID + " -> " + succNodeID);
        }
        else {
            return new String(label + ": " + predNodeID + " -> " + succNodeID);
        }
    }

    /*************************************************************************
     * Test driver for the Edge class.
     */
    public static void main(String[] args){
        Edge e = new Edge();
        System.out.println(e.label);
        e.setLabel("t");
        System.out.println(e.getLabel());
    }

}

/****************************************************************************/
