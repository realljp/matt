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

import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * 
 * Class to represent a basic graph, constructed of a set of nodes
 * connected by directed edges. Any specialized graph should be built by
 * deriving from this class.
 *
 * @author Alex Kinneer
 * @version 09/16/2004
 */
public abstract class Graph {
    /** The collection of nodes in the graph. */
    protected List<Node> nodes = new ArrayList<Node>();
    /** The collection of edges in the graph. */
    protected List<Edge> edges = new ArrayList<Edge>();

    /** For use with {@link Graph#getEdges(Node,int,Edge[])}, specifies that
        outgoing edges from the node will be matched. */
    public static final int MATCH_OUTGOING = 0;
    /** For use with {@link Graph#getEdges(Node,int,Edge[])}, specifies that
        incoming edges to the node will be matched. */
    public static final int MATCH_INCOMING = 1;

    /*************************************************************************
     * Inserts a node into the graph.
     *
     * @param n {@link sofya.graphs.Node} to be inserted into the graph.
     */
    protected void addNode(Node n) {
        nodes.add(n);
    }
    
    /*************************************************************************
     * Gets a node by index.  Nodes should be accessed using start-from-one
     * numbering.
     *
     * @param nodeID ID of the node to be retrieved, where the first node in
     * the graph is indexed as 1 (one).
     *
     * @return The node in the graph with the given ID (index).
     */
    public Node getNode(int nodeID) {
        return (Node) nodes.get(nodeID - 1);
    }

    /*************************************************************************
     * Gets the &apos;root&apos; node of the graph, which is defined to be
     * the node with ID 1.  <strong>This is a convenience method only</strong>,
     * the semantics of the method <i>are not</i> strictly enforced and it is
     * perfectly possible for a graph to be constructed where this
     * definition of a root node is meaningless or even misleading.
     *
     * @return The &apos;root&apos; node of the graph.
     */
    public Node getRootNode() {
        return (Node) nodes.get(0);
    }

    /*************************************************************************
     * Removes a node from the graph.
     *
     * @param n {@link sofya.graphs.Node} to be removed from the graph.
     */
    protected void removeNode(Node n) {
        nodes.remove(n);
    }

    /*************************************************************************
     * Adds an edge to the graph.
     *
     * @param e {@link sofya.graphs.Edge} to be added to the graph.
     */
    protected void addEdge(Edge e) {
        edges.add(e);
    }
    
    /*************************************************************************
     * Removes an edge from the graph.
     *
     * @param e {@link sofya.graphs.Edge} to be removed from the graph.
     */
    protected void removeEdge(Edge e) {
        edges.remove(e);
    }

    /*************************************************************************
     * Gets an edge in the graph. This method will match only the
     * <strong>first</strong> edge which is found to have the given source
     * and sink nodes. Note that edges are searched in the order in which
     * they were added to the graph.
     *
     * @param sourceNode Source node of the edge to be matched.
     * @param sinkNode Sink node of the edge to be matched.
     *
     * @return The edge in the control flow graph with the specified source
     * node and sink node, if any.
     *
     * @throws NoSuchElementException If a matching edge cannot be found in
     * the graph.
     */
    public Edge getEdge(Node sourceNode, Node sinkNode) {
        for (int i = 0; i < edges.size(); i++) {
            Edge e = (Edge) edges.get(i);
            if ((e.predNodeID == sourceNode.nodeID)
                    && (e.succNodeID == sinkNode.nodeID)) {
                return e;
            }
        }
        throw new NoSuchElementException("No matching edge exists in graph");
    }
    
    /*************************************************************************
     * Gets all of the edges in the graph, in the order in which they
     * were added to the graph.
     *
     * @param a Array which will be used to determine the runtime type
     * of the array returned by this method.
     *
     * @return The complete set of edges in the graph, in order of addition.
     */
    public Edge[] getEdges(Edge[] a) {
        return (Edge[]) edges.toArray(a);
    }

    /*************************************************************************
     * Gets all edges which have the given source and sink nodes.
     *
     * <p><i>Special explanatory note regarding CFGs:</i> Under some
     * circumstances, it is possible for switch statements and
     * finally-block returns to have multiple edges which point to the
     * same successor node (e.g. multiple cases point to the same block or
     * multiple exceptions return to the same location after handling).
     * The {@link Graph#getEdge(Node,Node)} method will only return the first
     * matching edge, which may be insufficient.</p> 
     *
     * @param sourceNode Source node of matching edges.
     * @param sinkNode Sink node of matching edges.
     * @param a Array which will be used to determine the runtime type
     * of the array returned by this method.
     *
     * @return The set of edges in the graph with the specified source node
     * and sink node, if any.
     *
     * @throws NoSuchElementException If no matching edges can be found in
     * the graph.
     */
    public Edge[] getEdges(Node sourceNode, Node sinkNode, Edge[] a) {
        List<Object> matchingEdges = new ArrayList<Object>();
        for (int i = 0; i < edges.size(); i++) {
            Edge e = (Edge) edges.get(i);
            if ((e.predNodeID == sourceNode.nodeID)
                    && (e.succNodeID == sinkNode.nodeID)) {
                matchingEdges.add(e);
            }
        }
        if (matchingEdges.size() > 0) {
            return (Edge[]) matchingEdges.toArray(a);
        }
        else {
            throw new NoSuchElementException("No matching edges " +
                "exist in graph");
        }
    }

    /*************************************************************************
     * Gets either all the edges which originate on a given node or which are
     * incident on a node.
     *
     * @param n Node for which associated edges will be returned.
     * @param matchType Constant indicating whether edges which start on the
     * node or which end on the node are to be returned. Acceptable values
     * are {@link Graph#MATCH_OUTGOING} and {@link Graph#MATCH_INCOMING}.
     * @param a Array which will be used to determine the runtime type
     * of the array returned by this method.
     *
     * @return Either the set of edges in the graph which start on the given
     * node or the set of edges which end on the node. 
     *
     * @throws NoSuchElementException If no matching edges can be found in
     * the graph.
     */
    public Edge[] getEdges(Node n, int matchType, Edge[] a) {
        List<Object> matchingEdges = new ArrayList<Object>();
        for (int i = 0; i < edges.size(); i++) {
            Edge e = (Edge) edges.get(i);
            if ((matchType == MATCH_OUTGOING) && (e.predNodeID == n.nodeID)) {
                matchingEdges.add(e);
            }
            else if ((matchType == MATCH_INCOMING)
                    && (e.succNodeID == n.nodeID)) {
                matchingEdges.add(e);
            }
        }
        if (matchingEdges.size() > 0) {
            return (Edge[]) matchingEdges.toArray(a);
        }
        else {
            throw new NoSuchElementException("No matching edges " +
                "exist in graph");
        }
    }
    
    /*************************************************************************
     * Gets the total number of nodes contained in the graph.
     *
     * @return The number of nodes in the graph.
     */
    public int getNodeCount() {
        return nodes.size();
    }
    
    /*************************************************************************
     * Gets the total number of edges contained in the graph.
     *
     * @return The number of edges in the graph.
     */
    public int getEdgeCount() {
        return edges.size();
    }
    
    /*************************************************************************
     * Clears the graph, such that it has no nodes or edges.
     */
    protected void clear() {
        nodes.clear();
        edges.clear();
    }

    /*************************************************************************
     * Returns string representation of the graph, which is a list of the
     * edges that constitute the graph.
     *
     * @return List of edges that constitute this graph.
     *
     * @see sofya.graphs.Edge#toString()
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < edges.size(); i++) {
            sb.append(((Edge) edges.get(i)).toString() + "\n");
        }
        return sb.toString();
    }
}

/****************************************************************************/

