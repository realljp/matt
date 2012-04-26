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
import java.util.ArrayList;
import java.util.ListIterator;

import sofya.base.exceptions.SofyaError;
import sofya.graphs.*;
import sofya.graphs.cfg.CFEdge;
import sofya.graphs.cfg.Block;
import static sofya.base.SConstants.*;

/**
 * Implementation of the edge selection module in the DejaVu graph traversal
 * algorithm which understands the control flow edges found in Sofya control
 * flow graphs.
 *
 * <p>This module can perform the appropriate type casting of edges returned
 * by the traversal algorithm to control flow edges. It also understands
 * and implements certain special edge selection behaviors related to
 * the representation of exceptional control flow and <code>finally</code>
 * blocks in Sofya control flow graphs.</p>
 *
 * @author Alex Kinneer
 * @version 11/30/2004
 */
public class CFEdgeSelector extends EdgeSelector {
    // Lists are used to achieve the stack behavior because the Java
    // collections Stack class has the undocumented 'feature' of being
    // synchronized. We don't need that extra overhead reducing performance.
        
    /** Stack used to match JSR to finally-return edges in the
        original graph. */
    private List<Object> jsrStackOld = new ArrayList<Object>(4);
    /** Stack used to match JSR to finally-return edges in the
        new graph. */
    private List<Object> jsrStackNew = new ArrayList<Object>(4);
    
    /**
     * Creates a new edge selector.
     */
    public CFEdgeSelector() { }
    
    public void setGraphs(Graph oldGraph, Graph newGraph) {
        super.setGraphs(oldGraph, newGraph);
        jsrStackOld.clear();
        jsrStackNew.clear();
    }
    
    public EdgeMatchData beginVisit(Node oldNode, Node newNode) {
        Block oldBlock = (Block) oldNode;
        Block newBlock = (Block) newNode;
        
        // The outgoing edge lists are needed for various search and match
        // operations.  Since getting the list from the graph itself requires
        // worst case linear traversal of the entire list of edges in the
        // graph, we retrieve each outgoing edge list at the outset.
        // We can then use the much smaller lists for better efficiency.
        CFEdge[] oldOutEdges = null;
        CFEdge[] newOutEdges = null;
        if (oldBlock.getType() != BlockType.EXIT) {
            // Only exit nodes will have no outgoing edges (getEdges throws
            // an exception if this is the case, which is what we are
            // avoiding)
            oldOutEdges = (CFEdge[]) oldGraph.getEdges(oldBlock,
                Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
            newOutEdges = (CFEdge[]) newGraph.getEdges(newBlock,
                Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
        }
        
        BlockSubType oldSubtype = oldBlock.getSubType();
        
        EdgeTraversal traversal;
        CFEdge retEdgeOld = null, retEdgeNew = null;
        if (oldSubtype == BlockSubType.FINALLY) {
            // Pop stacks
            String retLabelOld =
                (String) jsrStackOld.remove(jsrStackOld.size() - 1);
            String retLabelNew =
                (String) jsrStackNew.remove(jsrStackNew.size() - 1);
            
            retEdgeOld = matchUniqueLabelToEdge(oldOutEdges, retLabelOld);
            retEdgeNew = matchUniqueLabelToEdge(newOutEdges, retLabelNew);
            traversal = new EdgeTraversal(
                oldGraph.getNode(retEdgeOld.getSuccNodeID()),
                newGraph.getNode(retEdgeNew.getSuccNodeID()));
        }
        else {
            traversal = new EdgeTraversal();
        }
        
        return new EdgeMatchData(traversal, retEdgeOld, retEdgeNew,
                                 oldOutEdges, newOutEdges);
    }
    
    public EdgeTraversal nodesCompared(Edge oldEdge, Edge newEdge) {
        CFEdge oldCFEdge = (CFEdge) oldEdge;
        CFEdge newCFEdge = (CFEdge) newEdge;
        Block oldPredNode = (Block) oldGraph.getNode(
            oldCFEdge.getPredNodeID());
        
        if (oldPredNode.getSubType() == BlockSubType.JSR) {
             // Stack pushes
            jsrStackOld.add(oldCFEdge.getLabel() + "&&" +
                            oldCFEdge.getAuxLabel());
            jsrStackNew.add(newCFEdge.getLabel() + "&&" +
                            newCFEdge.getAuxLabel());
            return new EdgeTraversal(
                oldGraph.getNode(oldCFEdge.getSpecialNodeID()),
                newGraph.getNode(newCFEdge.getSpecialNodeID()));
        }
        else {
            return new EdgeTraversal();
        }
    }
    
    public void newEdges(Node oldNode, List<Edge> newEdges,
                         EdgeMatchData edgeData, Set<Edge> dangerousEdges) {
        Block oldBlock = (Block) oldNode;
        BlockSubType oldSubType = oldBlock.getSubType();
        Edge[] oldOutEdges = edgeData.oldOutEdges();
        
        if (oldSubType == BlockSubType.SWITCH) {
            dangerousEdges.add(matchLabelToEdge(oldOutEdges, "Default"));
        }
        else if ((oldSubType == BlockSubType.THROW)
                || (oldBlock.getType() == BlockType.CALL)) {
            outer:
            for (ListIterator li = newEdges.listIterator(); li.hasNext(); ) {
                Edge newEdge = (Edge) li.next();
                // Get label of new edge. We know the edge will exist, and that
                // there will be only one, so a first-match 'search' method
                // is fine
                String edgeLabel = newEdge.getLabel();
                // Search for nearest superclass of the exception in the
                // original graph's edge set
                for (int n = 0; n < oldOutEdges.length; n++) {
                    String oldLabel = oldOutEdges[n].getLabel();
                    if (oldLabel.equals("<r>")) {
                        // Normal path couldn't have caught any exceptions
                        continue;
                    }
                    if (oldLabel.equals("<any>")) {
                        // Caught everything in the whole wide world
                        if (n < (oldOutEdges.length - 1)) {
                            throw new SofyaError("Invalid CFG: Edge for " +
                                "catch-all handler shadows other edges");
                        }
                        dangerousEdges.add(oldOutEdges[n]);
                        continue outer;
                    }
                    try {
                        if (Class.forName(oldLabel)
                                .isAssignableFrom(Class.forName(edgeLabel))) {
                            dangerousEdges.add(oldOutEdges[n]);
                            continue outer;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        throw new SofyaError("Reflection exception", e);
                    }
                }
                // No superclass was found, add all of the original edges as
                // dangerous
                System.out.println("Info: no exception superclass found, " +
                    "node " + newEdge.getPredNodeID());
                for (int n = 0; n < oldOutEdges.length; n++) {
                    dangerousEdges.add(oldOutEdges[n]);
                }
            }
        }
        else if (oldSubType == BlockSubType.FINALLY) {
            // Ignore
        }
        else {
            System.err.println("WARNING: Unexpected multiple edges " +
                "encountered at node " + oldBlock.getID() + " (type " +
                oldBlock.getType() + ", subtype " + oldSubType + ")");
        }
    }
    
    /**
     * Utility method to find an edge with a given unique label
     * (concatenation of the basic and auxiliary edge labels) in
     * a list of edges.
     *
     * @param edgeList List of edges to be searched, as an array.
     * @param edgeLabel Unique label of the edge to found. This
     * should be the concatenation of the edge's basic label,
     * "&&" and the edge's auxiliary label.
     *
     * @returns The edge in the edge list with the given unique
     * label, or <code>null</code> if no such edge exists.
     */
    private CFEdge matchUniqueLabelToEdge(CFEdge[] edgeList,
                                          String edgeLabel) {
        for (int i = 0; i < edgeList.length; i++) {
            if ((edgeList[i].getLabel() + "&&" + edgeList[i].getAuxLabel())
                    .equals(edgeLabel)) {
                return edgeList[i];
            }
        }
        return null;
    }
}
