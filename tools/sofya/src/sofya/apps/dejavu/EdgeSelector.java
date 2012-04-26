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

import java.util.Set;
import java.util.List;
import java.util.LinkedList;

import sofya.graphs.*;

/**
 * The edge selector retrieves and matches edges from the graphs being
 * traversed, using this information to control how the graph traverser
 * walks the graphs.
 *
 * <p>The graph traverser raises &apos;event&apos; methods defined by
 * this class to indicate when it is at certain points in the traversal.
 * This provides an edge selector the opportunity to guide the graph
 * traverser based on knowledge it has about the specific types of
 * edges (and nodes) contained in the graphs being walked, and special
 * conditions which may apply to the particular type of graph. An
 * edge selector is also responsible for determining how to handle
 * new edges found in the graph for a new version of a method.</p>
 *
 * @author Alex Kinneer
 * @version 02/23/2005
 */
public abstract class EdgeSelector {
    /** Graph for the method from the old version of the program. */
    protected Graph oldGraph;
    /** Graph for the method from the new version of the program. */
    protected Graph newGraph;
    
    public EdgeSelector() { }
    
    /**
     * Event raised by the traverser when it reaches a node in
     * the graph which has not been visited.
     *
     * <p>This event provides an opportunity for subclasses to produce
     * edge data and control the traversal based on information that
     * may be available from specific types of edges contained
     * in the graphs.</p>
     *
     * @param oldNode Node in the graph for the old version of the method.
     * @param newNode Node in the graph for the new version of the method
     * which has been determined to correspond to <code>oldNode</code>.
     *
     * @return A data object containing outgoing edges from the given
     * nodes in each graph and an object instructing the traverser
     * how to proceed.
     */
    public abstract EdgeMatchData beginVisit(Node oldNode, Node newNode);
    
    /**
     * Event raised by the traverser immediately after comparison of
     * two successor nodes when it is in the process of following all
     * outgoing edges from a node.
     *
     * <p>This event provides an opportunity for subclasses to provide
     * special case control over the traversal based on information that
     * may be available from specific types of edges and nodes contained
     * in the graphs.</p>
     *
     * @param oldEdge Edge which was followed in the old graph to reach
     * the node from that graph which was used in the comparison.
     * @param newEdge Edge which was followed in the new graph to reach
     * the node from that graph which was used in the comparison.
     *
     * @return An object instruction the traverser how to proceed.
     */
    public abstract EdgeTraversal nodesCompared(Edge oldEdge, Edge newEdge);
    
    /**
     * Event raised by the traverser when it has determined that the
     * graph for the new version of the method contains new edges out
     * of a given node.
     *
     * <p>This event provides an opportunity for subclasses to specify
     * how the new edges impact the dangerous edge set based on information
     * that may be available from specific types of edges and nodes
     * contained in the graphs.</p>
     *
     * @param oldNode Node from the graph of the old version of the method
     * from which new outgoing edges were found in the graph for the new
     * version of the method.
     * @param newEdges List of the new outgoing edges found in the graph
     * for the new version of the method at the given node.
     * @param edgeData Data about outgoing edges in both graphs, as
     * determined by the last call to {@link EdgeSelector#beginVisit}.
     * @param dangerousEdges Set containing the dangerous edges identified
     * by the traversal up to the current point.
     */
    public abstract void newEdges(Node oldNode, List<Edge> newEdges,
        EdgeMatchData edgeData, Set<Edge> dangerousEdges);
    
    /**
     * Sets the graphs for the old and new versions of the methods.
     *
     * @param oldGraph Graph corresponding to the old version of the method.
     * @param newGraph Graph corresponding to the new version of the method.
     */
    public void setGraphs(Graph oldGraph, Graph newGraph) {
        this.oldGraph = oldGraph;
        this.newGraph = newGraph;
    }
    
    /**
     * Gets the graphs currently in use.
     *
     * @return A two-element array, where the first element contains the graph
     * for the old version of the method and the second element contains the
     * graph for the new version of the method.
     */
    public Graph[] getGraphs() {
        Graph[] theGraphs = new Graph[2];
        theGraphs[0] = oldGraph;
        theGraphs[1] = newGraph;
        return theGraphs;
    }
    
    /**
     * Utility method to find the first edge with the given source
     * and sink nodes in a list of edges.
     *
     * @param edgeList List of edges to be searched, as an array.
     * @param sourceNode Node which must be the predecessor node of
     * a matching edge.
     * @param sinkNode Node which must be the successor node of a
     * matching edge.
     *
     * @return The first matching edge in the list, or <code>null</code>
     * if no matching edge can be found.
     */
    protected Edge findEdge(Edge[] edgeList, Node sourceNode, Node sinkNode) {
        for (int i = 0; i < edgeList.length; i++) {
            if ((edgeList[i].getPredNodeID() == sourceNode.getID())
                    && (edgeList[i].getSuccNodeID() == sinkNode.getID())) {
                return edgeList[i];
            }
        }
        // Null flags are generally bad, but this method is in a critical
        // region as far as efficiency is concerned, so we'll avoid the
        // exception handling overhead
        return null;
    }
    
    /**
     * Utility method to find all edges with the given source and sink
     * nodes in a list of edges.
     *
     * @param edgeList List of edges to be searched, as an array.
     * @param sourceNode Node which must be the predecessor node of
     * a matching edge.
     * @param sinkNode Node which must be the successor node of a
     * matching edge.
     *
     * @return A list of all matching edges found in the provided
     * edge list, which may be of length zero if no matching edges
     * were found.
     */
    protected List<Edge> findEdges(Edge[] edgeList,
                             Node sourceNode, Node sinkNode) {
        List<Edge> matching = new LinkedList<Edge>();
        for (int i = 0; i < edgeList.length; i++) {
            if ((edgeList[i].getPredNodeID() == sourceNode.getID())
                    && (edgeList[i].getSuccNodeID() == sinkNode.getID())) {
                matching.add(edgeList[i]);
            }
        }
        return matching;
    }
    
    /**
     * Utility method to find an edge with a given basic label in
     * a list of edges.
     *
     * @param edgeList List of edges to be searched, as an array.
     * @param edgeLabel Basic label of the edge to found. (Auxiliary
     * label information is ignored).
     *
     * @return The edge in the edge list with the given basic label,
     * or <code>null</code> if no such edge exists.
     */
    protected Edge matchLabelToEdge(Edge[] edgeList, String edgeLabel) {
        for (int i = 0; i < edgeList.length; i++) {
            if (edgeLabel == null) {
                if (edgeList[i].getLabel() == null) {
                    if (edgeList.length > 1) {
                        System.err.println("WARNING: Graph may be invalid - " +
                            "null edge label detected in multiple edge set");
                    }
                    return edgeList[i];
                }
            }
            else if (edgeLabel.equals(edgeList[i].getLabel())) {
                return edgeList[i];
            }
        }
        return null;
    }
    
    /**
     * Data container class which records information about the outgoing edges
     * from nodes in each graph and the path (or paths) the traverser should
     * follow next.
     *
     * <p>A subclass of EdgeTraverser can use knowledge of the particular
     * types of edges and nodes contained in the graphs to produce the edges
     * and traversal data contained in an object of this class. It will
     * generally also want to ensure that edges recorded by an object of this
     * class are cast to the most specific runtime type of which it is aware
     * (so that the type can be safely recovered with a cast at a future
     * point).</p>
     */
    public static class EdgeMatchData {
        /** Edge traversal that the graph traverser should execute next. */
        private EdgeTraversal traversal = null;
        /** Edge which the traverser should follow in the old graph, relevant
            only if the traverser is not instructed to follow all edges. */
        private Edge oldEdgeMatch = null;
        /** Edge which the traverser should follow in the new graph, relevant
            only if the traverser is not instructed to follow all edges. */
        private Edge newEdgeMatch = null;
        
        /** Outgoing edges retrieved from the old graph. */
        private Edge[] oldOutEdges = null;
        /** Outgoing edges retrieved from the new graph. */
        private Edge[] newOutEdges = null;
        
        private EdgeMatchData() { }
        
        /**
         * Creates a new edge data object.
         *
         * @param traversal Edge traversal information which directs the
         * graph traverser how to proceed.
         * @param oem Matching edge to follow in the old graph.
         * @param nem Matching edge to follow in the new graph.
         * @param ooe Outgoing edges from a node in the old graph.
         * @param noe Outgoing edges from a node in the new graph.
         */
        protected EdgeMatchData(EdgeTraversal traversal, Edge oem, Edge nem,
                                Edge[] ooe, Edge[] noe) {
            if (traversal == null) {
                throw new NullPointerException();
            }
            this.traversal = traversal;
            oldEdgeMatch = oem;
            newEdgeMatch = nem;
            oldOutEdges = ooe;
            newOutEdges = noe;
        }
        
        /**
         * Gets the traversal instruction which directs the graph traverser
         * how to proceed.
         *
         * @return An object containing information which directs the graph
         * traverser how to proceed.
         */
        public EdgeTraversal traversal() {
            return traversal;
        }
        
        /**
         * Gets the matching edge from the old graph, which the traverser
         * should follow if it is not directed to follow all edges.
         *
         * @return Edge in the old graph which should be followed by the
         * traverser if it is not directed to follow all edges.
         */
        public Edge oldEdgeMatch() {
            return oldEdgeMatch;
        }
        
        /**
         * Gets the matching edge from the new graph, which the traverser
         * should follow if it is not directed to follow all edges.
         *
         * @return Edge in the new graph which should be followed by the
         * traverser if it is not directed to follow all edges.
         */
        public Edge newEdgeMatch() {
            return newEdgeMatch;
        }
        
        /**
         * Gets the outgoing edges from a node in the old graph.
         *
         * @return Outgoing edges in the old graph recorded by this object.
         */
        public Edge[] oldOutEdges() {
            return oldOutEdges;
        }
        
        /**
         * Gets the outgoing edges from a node in the new graph.
         *
         * @return Outgoing edges in the new graph recorded by this object.
         */
        public Edge[] newOutEdges() {
            return newOutEdges;
        }
    }
    
    /**
     * Data container class which is used to direct the behavior of
     * the graph traverser.
     */
    public static class EdgeTraversal {
        /** Flag indicating whether the traverser should follow all outgoing
            edges from its current point in the traversal. */
        private boolean traverseAll = true;
        /** Successor node in the old graph to which the traverser should
            proceed if <code>traverseAll</code> is false. */
        private Node oldSuccessor = null;
        /** Successor node in the new graph to which the traverser should
            proceed if <code>traverseAll</code> is false. */
        private Node newSuccessor = null;
        
        /**
         * Creates a traversal instruction which directs the graph
         * traverser to follow all outgoing edges.
         */
        protected EdgeTraversal() { }
        
        /**
         * Creates a traversal instruction with directs the graph
         * traverser to proceed to a particular successor.
         *
         * @param oldSuccessor Next node in the old graph to which
         * the traversal should proceed.
         * @param newSuccessor Next node in the new graph to which
         * the traversal should proceed.
         */
        protected EdgeTraversal(Node oldSuccessor, Node newSuccessor) {
            if ((oldSuccessor == null) || (newSuccessor == null)) {
                throw new NullPointerException();
            }
            this.oldSuccessor = oldSuccessor;
            this.newSuccessor = newSuccessor;
            this.traverseAll = false;
        }
        
        /**
         * Reports whether the graph traverser should follow all
         * outgoing edges.
         */
        public boolean traverseAll() {
            return traverseAll;
        }
        
        /**
         * Gets the next node in the old graph to which the traversal
         * should proceed.
         */
        public Node oldSuccessor() {
            return oldSuccessor;
        }
        
        /**
         * Gets the next node in the new graph to which the traversal
         * should proceed.
         */
        public Node newSuccessor() {
            return newSuccessor;
        }
    }
}
