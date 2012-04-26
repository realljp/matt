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

import java.util.*;
import java.io.IOException;
//import java.lang.reflect.Array;

import sofya.base.exceptions.*;
import sofya.graphs.*;
import sofya.graphs.cfg.Block;
import sofya.apps.dejavu.EdgeSelector.*;

import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;

/**
 * This class traverses two graphs using a node comparer to build a list
 * of dangerous edges.
 *
 * <p>A dangerous edge is one which:
 * <ol>
 * <li>Has successor nodes which differ between the two graphs.</li>
 * <li>Is present in the first graph but not the second.</li>
 * <li>Is an edge in the first graph that would be traversed instead
 * of a new edge found in the second graph.</li>
 * </ol>
 * </p>
 *
 * <p>The traverser is designed to be completely polymorphic, in that it
 * operates on any {@link sofya.graphs.Graph} or subclass, and uses an
 * externally specified node comparer which presumably is appropriate
 * for the type of graph being traversed. Generally it is expected
 * that nodes encode type information consistent with the types
 * defined in {@link sofya.base.SConstants}, which is to say node type
 * information that should be relevant to any type of graph constructed
 * from Java code. In the absence of certain expected but optional type
 * information, the traverser will emit appropriate warnings but
 * will not terminate.</p>
 *
 * @author Rogan Creswick, Sharat Narayan, Sriraam Natarajan
 * @author Alex Kinneer
 * @version 05/13/2005
 */
@SuppressWarnings("unchecked")
public class GraphTraverser {
    /** Graph for the original program. */
    private Graph oldGraph;
    /** Graph for the modified program. */
    private Graph newGraph;
    /** Node comparer used to test nodes for equivalence. */
    private NodeComparer comparer;
    /** Edge selector used to match edges and control the traversal at
        certain points. */
    private EdgeSelector edgeSelector;
    /** Name of the last method to be traversed. */
    private String methodName;

    /** Contains the list of dangerous edges, which will be incomplete until
        the graph traverser finishes recursing. Storing it globally is cheaper
        than passing the reference through every recursive call. It is cleared
        each time {@link GraphTraverser#buildDangerousEdges} - the driver
        method for the recursive graph traverser - is called. Using a set is
        just a convenient way to automatically screen out duplicate
        selections. */
    private Set<Edge> dangerousEdges = new THashSet();
    private ArrayList<Edge> coveredinNCFG=new ArrayList();

    /** Maps each node in G to its G'-visited nodes set. As with
        {@link GraphTraverser#dangerousEdges}, it is stored globally
        for efficiency and cleared on each call to
        {@link GraphTraverser#buildDangerousEdges}. */
    private TIntObjectHashMap visitedNodes = new TIntObjectHashMap();

    /**
     * Protected constructor for subclasses only.
     */
    protected GraphTraverser() { }

    /**
     * Standard constructor, initializes the traverser with the given
     * node comparer.
     *
     * @param nc Node comparer which will be used to test nodes in the
     * graph for equivalence.
     */
    public GraphTraverser(NodeComparer nc, EdgeSelector es) {
        comparer = nc;
        edgeSelector = es;
    }

    /**
     * Returns the node comparer which the traverser is currently
     * set to use to test node equivalence.
     *
     * @return The node comparer currently in use by the traverser.
     */
    public NodeComparer getNodeComparer() {
        return comparer;
    }

    /**
     * Sets the node comparer which the traverser should use to
     * test node equivalence.
     *
     * <p><strong>Note:</strong> If the node comparer does not
     * handle the type of node contained in the supplied graphs,
     * the traverser will fail.</p>
     *
     * @param nc New node comparer to be used by the traverser
     * when checking nodes for equivalence.
     */
    public void setNodeComparer(NodeComparer nc) {
        comparer = nc;
    }

    /**
     * Gets the list of dangerous edges by invoking the traverser
     * to walk the graphs.
     *
     * @param method Method pair that supplies the names and
     * control flow graphs for the two methods to be compared.
     *
     * @return The list of dangerous edges selected by the graph
     * traversal.
     *
     * @throws MethodNotFoundException If a method specified in
     * <code>method</code> cannot be found in a class file loaded
     * by the traverser.
     * @throws IOException For any IO error that prevents the
     * traverser from successfully reading a required input file.
     */
    public Edge[] getDangerousEdges(MethodPair method)
                  throws MethodNotFoundException, IOException {
        this.oldGraph = method.oldGraph;
        this.newGraph = method.newGraph;
        methodName = method.name;
        comparer.setComparisonClass(method.class_);
        edgeSelector.setGraphs(oldGraph, newGraph);

        // Clear global data structures for new run
        dangerousEdges.clear();
        visitedNodes.clear();

        // Traverse the graphs starting from the root nodes
        traverseGraph(oldGraph.getRootNode(), newGraph.getRootNode());

        return (Edge[]) dangerousEdges.toArray(
            new Edge[dangerousEdges.size()]);
    }

    /**
     * Recursively traverses two graphs from the given start nodes
     * in each graph respectively.
     *
     * <p>The graph is traversed until all paths have reached an
     * exit node or a changed node. There is no guarantee that the
     * graph is traversed completely or that every node is
     * visited.</p>
     *
     * @param oldNode Node in the old graph from which to start
     * the traversal.
     * @param newNode Node in the new graph from which to start
     * the traversal.
     *
     * @throws MethodNotFoundException If the method currently
     * being traversed cannot be found in the classfile currently
     * loaded by the node comparer.
     */
    @SuppressWarnings("unchecked")
    protected void traverseGraph(Node oldNode, Node newNode)
                   throws MethodNotFoundException {
        // Mark node N "N'-visited"
        int nodeKey = oldNode.getID();
        if (visitedNodes.containsKey(nodeKey)) {
            ((TIntHashSet) visitedNodes.get(nodeKey)).add(newNode.getID());
        }
        else {
            TIntHashSet visitedSet = new TIntHashSet();
            visitedSet.add(newNode.getID());
            visitedNodes.put(nodeKey, visitedSet);
        }

        EdgeMatchData edgeData = edgeSelector.beginVisit(oldNode, newNode);
        EdgeTraversal traversal = edgeData.traversal();

        if (!traversal.traverseAll()) {
            Node oldSuccessor = traversal.oldSuccessor();
            Node newSuccessor = traversal.newSuccessor();

            if (!comparer.compareNodes(methodName,
                    oldSuccessor, newSuccessor)) {
                dangerousEdges.add(edgeData.oldEdgeMatch());
            }
            else {
                nodeKey = oldSuccessor.getID();
                if (!(visitedNodes.containsKey(nodeKey) &&
                        ((TIntHashSet) visitedNodes.get(nodeKey))
                        .contains(newSuccessor.getID()))) {
                    traverseGraph(oldSuccessor, newSuccessor);
                }
            }
        }
        else {
            Node[] oldSuccessors = oldNode.getSuccessors();
            Edge[] oldOutEdges = edgeData.oldOutEdges();
            Edge[] newOutEdges = edgeData.newOutEdges();

            List<Edge> newSuccessorEdges;
            if (newOutEdges != null) {
                newSuccessorEdges = new LinkedList<Edge>(
                    Arrays.asList(newOutEdges));
            }
            else {
                newSuccessorEdges = new LinkedList<Edge>();
            }

            Map<Object, Object> multEdgeMapOld = new THashMap();

            for (int i = 0; i < oldSuccessors.length; i++) {
                // For additional details on the following hash map, see
                // footer comment #1
                Edge oldEdgeMatch;
                if (!multEdgeMapOld.containsKey(oldSuccessors[i])) {
                    List matchingEdgesOld = edgeSelector.findEdges(
                        oldOutEdges, oldNode, oldSuccessors[i]);
                    oldEdgeMatch = (Edge) matchingEdgesOld.remove(0);
                    if (matchingEdgesOld.size() > 0) {
                        multEdgeMapOld.put(oldSuccessors[i], matchingEdgesOld);
                    }
                }
                else {
                    oldEdgeMatch = (Edge) ((LinkedList)
                        multEdgeMapOld.get(oldSuccessors[i])).removeFirst();
                }
                String oldBaseLabel = oldEdgeMatch.getLabel();

                Edge newEdgeMatch =
                    edgeSelector.matchLabelToEdge(newOutEdges, oldBaseLabel);
                if (newEdgeMatch == null) {
                    // Edge was deleted
                    dangerousEdges.add(oldEdgeMatch);
                    continue;
                }

                Node newSuccessor = newGraph.getNode(
                    newEdgeMatch.getSuccNodeID());
                boolean removed = newSuccessorEdges.remove(newEdgeMatch);
                assert removed : "Edge found in G' was not removed " +
                    "from working copy of edge list";

                // Do the actual comparison
                if (!comparer.compareNodes(methodName, oldSuccessors[i],
                        newSuccessor)) {
                    dangerousEdges.add(oldEdgeMatch);
                    continue;
                }

                traversal =
                    edgeSelector.nodesCompared(oldEdgeMatch, newEdgeMatch);

                nodeKey = oldSuccessors[i].getID();
                if (!(visitedNodes.containsKey(nodeKey) &&
                        ((TIntHashSet) visitedNodes.get(nodeKey))
                        .contains(newSuccessor.getID()))) {
                    traverseGraph(oldSuccessors[i], newSuccessor);
                }
                else if (!traversal.traverseAll()) {
                    traverseGraph(traversal.oldSuccessor(),
                                  traversal.newSuccessor());
                }
            }

            if (newSuccessorEdges.size() > 0) {
                // Edges have been added
                edgeSelector.newEdges(oldNode, newSuccessorEdges,
                                      edgeData, dangerousEdges);
            }
        }
    }
 
   public Edge[] getUncoveredEDges(MethodPair method,ArrayList<Edge> covered, Edge[] notcovered, Map<String, Map<Integer, Integer>> mb)
    throws MethodNotFoundException, IOException{
//covered edges represent the edges in old graph
        this.oldGraph = method.oldGraph;
        this.newGraph = method.newGraph;
        methodName = method.name;
        //ArrayList<Edge> covered=new ArrayList();
        Edge[] noedge=null;
        comparer.setComparisonClass(method.class_);
        edgeSelector.setGraphs(oldGraph, newGraph);
       
       
        // Clear global data structures for new run
        dangerousEdges.clear();
        visitedNodes.clear();
        Map<Integer,Integer> oldnew=mb.get(methodName);
        if(oldnew==null){
            oldnew=new THashMap();
        }
        // Traverse the graphs starting from the root nodes
        //boolean a=coveredEdges.containsKey(methodName);
        //System.out.println(a);
       
        //covered=(ArrayList)coveredEdges.get(methodName);
        if(covered!=null){

           
            ArrayList<Edge> saveany=new ArrayList<Edge>();
            Iterator il=covered.iterator();
            while(il.hasNext()){
                Edge a =(Edge)il.next();
                if(a.getLabel()!=null&&a.getLabel().equals("<any>")){
                    saveany.add(a);
                }
            }
            Iterator ill=saveany.iterator();
            while(ill.hasNext()){
                Edge a =(Edge)ill.next();
                if(covered.contains(a)){
                    covered.remove(a);
                }
            }
           
        ArrayList<Edge> refinedcovered=Findcovered(covered);//a trace
       
        coveredinNCFG.clear();
       
        traverseGraph1(oldGraph.getRootNode(), newGraph.getRootNode(), refinedcovered);
        //map refinedcovered with coveredinNCFG
        for(int i=0;i<refinedcovered.size();i++){
            int preold=refinedcovered.get(i).getPredNodeID();
            int sucold=refinedcovered.get(i).getSuccNodeID();
           
            int prenew=coveredinNCFG.get(i).getPredNodeID();
            int sucnew=coveredinNCFG.get(i).getSuccNodeID();
           
            if(!oldnew.containsKey(preold)){
                oldnew.put(preold, prenew);
            }
           
            if(!oldnew.containsKey(sucold)){
                oldnew.put(sucold, sucnew);
            }
        }
        mb.put(methodName, oldnew);
       
        coveredinNCFG.addAll(saveany);
       
       
        Edge[] coveredN=(Edge [])coveredinNCFG.toArray(new Edge[coveredinNCFG.size()]);
       
        //System.out.println(coveredN);
        //Edge[] b=new Edge[0];
        Edge[] allinN=notcovered;
        for(int k=0;k<coveredN.length;k++){
            for(int l=0;l<allinN.length;l++){
                if((allinN[l]!=null)&&allinN[l].toString().equals(coveredN[k].toString())){
                    allinN[l]=null;
                }
            }
        }
        ArrayList<Edge> notcoveredinNCFG=new ArrayList();
        for(int l=0;l<allinN.length;l++){
            if(allinN[l]!=null){
                notcoveredinNCFG.add(allinN[l]);
            }
        }
        return (Edge[]) notcoveredinNCFG.toArray(
                new Edge[notcoveredinNCFG.size()]);
        }
        return noedge;

    }
   
   

private ArrayList<Edge> Findcovered(ArrayList<Edge> covered) {
    // TODO Auto-generated method stub
    ArrayList<Edge> refine=new ArrayList<Edge>();
   
    //now covered does not contain the "<any>" type edge
    int tmp=6666;
    for(int i=1;i<covered.size();i++){
        Edge a=covered.get(i-1);
        Edge b=covered.get(i);
        if(a.getPredNodeID()!=0){
            if(a.getPredNodeID()!=b.getPredNodeID()){
                refine.add(a);
            }
            else {
                Edge c=covered.get(i+1);
                if(a.getSuccNodeID()==c.getPredNodeID()){
                    refine.add(a);
                    tmp=b.getPredNodeID();
                    b.setPredNodeID(0);
                   
   
                   
                }
                else{
                    //refine.add(b);
                    //a.setPredNodeID(0);

                }
            }
        }
        else{
            a.setPredNodeID(tmp);
        }
    }
   
    refine.add(covered.get(covered.size()-1));
    return refine;
}

protected void traverseGraph1(Node oldNode, Node newNode, ArrayList<Edge> cvd1)
                throws MethodNotFoundException {
    //Edge[] cvd=(Edge[])cvd1.toArray( );
    Edge[] cvd=cvd1.toArray(new Edge[0]);
  
    int nodekey=cvd[0].getPredNodeID();
   
   
    if(nodekey==oldNode.getID()){
    for(int i=0;i<cvd.length;i++){
        @SuppressWarnings("unused")
        Node edgepre=new Node();
        edgepre.setID(cvd[i].getPredNodeID());
        Node edgesuc=new Node();
        edgesuc.setID(cvd[i].getSuccNodeID());
       
        EdgeMatchData edgeData=edgeSelector.beginVisit(oldNode, newNode);
        Node[] oldsuccessors=oldNode.getSuccessors();
        Edge[] oldOutEdges=edgeData.oldOutEdges();
        Edge oldOutEdge=new Edge();
        for(int j=0;j<oldOutEdges.length;j++){
            if(oldOutEdges[j].getSuccNodeID()==edgesuc.getID()){
                oldOutEdge=oldOutEdges[j];
                break;
            }
        }
        for(int k=0;k<oldsuccessors.length;k++){
            if(oldsuccessors[k].getID()==edgesuc.getID()){
                edgesuc=oldsuccessors[k];
                oldNode=edgesuc;
                break;
            }
        }
       
       
       
        Edge[] newOutEdges=edgeData.newOutEdges();
       
        List<Edge> newSuccessorEdges;
       
        if (newOutEdges!=null){
            newSuccessorEdges=new LinkedList<Edge>(Arrays.asList(newOutEdges));
        }
        else{
            newSuccessorEdges=new LinkedList<Edge>();
        }
        Edge oldEdgeMatch=oldOutEdge;
       
        String oldBaseLabel=oldEdgeMatch.getLabel();
       
        Edge newEdgeMatch=edgeSelector.matchLabelToEdge(newOutEdges, oldBaseLabel);
        if(newEdgeMatch!=null){
        Node newsuccessor=newGraph.getNode(newEdgeMatch.getSuccNodeID());
        newNode=newsuccessor;
        boolean removed=newSuccessorEdges.remove(newEdgeMatch);
        assert removed:"Edge found in G' was not removed "+ "from working copy of edge list";
       
        if(comparer.compareNodes(methodName, edgesuc, newsuccessor)){
            coveredinNCFG.add(newEdgeMatch);
        }
        }
       
       
    }
}
    else{
        System.out.println("Error: NodeID mismatch!");
    }
}
}

/* Extended comment #1:
   It is possible for some nodes to have multiple edges to the same
   successor, with different labels. Since the labels are important
   for matching edges correctly, we must traverse them all. So
   the first time we look up edges for a successor, if more than
   one is found, we cache the result list (minus the first match).
   Each time a subsequent successor node is actually
   the same node as a previous successor, we retrieve the list and
   remove the next entry from the front. This way every edge is
   traversed.
*/

