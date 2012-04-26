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

import java.util.*;

import sofya.base.Handler;
import sofya.base.exceptions.*;
import sofya.graphs.Graph;
import sofya.graphs.Edge;
import sofya.graphs.Node;
import sofya.graphs.cfg.CFEdge.BranchID;
import static sofya.base.SConstants.*;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

/**
 * The branch flow processor is a transformer which determines the conditional
 * and decision based control flow in the graph and annotates the edges in
 * the graph with numeric IDs that identify those branch dominated paths in
 * the control flow graph.
 *
 * @author Alex Kinneer
 * @version 11/16/2004
 */
@SuppressWarnings("unchecked")
public class BranchFlowProcessor implements CFGTransformer {
    /** CFG on which the transformer is currently operating. */
    private CFG cfg = null;
    /** Next available branch ID. */
    private int nextBranchID = 1;

    /** Records branch IDs which should not be propagated; these are branch
        IDs induced at certain branching nodes to distinguish immediate
        outgoing edges from larger encapsulating logical edges
        (e.g. &apos;side loops&apos;).
      */
    private Set<Object> dnProp = new THashSet();

    /** Conditional-compilation flag controlling whether additional
        debugging output is displayed. */
    private static final boolean DEBUG = false;

    /**
     * Creates a branch flow processor.
     */
    public BranchFlowProcessor() { }

    /**
     * Determines and propagates branch edge IDs through the graph.
     *
     * @param cfg CFG for which to compute branch IDs.
     *
     * @throws TransformationException If the CFG is malformed in
     * such a way that the algorithm cannot complete.
     */
    @SuppressWarnings("unchecked")
    public void transformCFG(CFG cfg) throws TransformationException {
        this.cfg = cfg;
        ReductionRules joinRules = new ReductionRules();
        Set<Edge> knownEdges = new THashSet();
        nextBranchID = 1;
        dnProp.clear();
        assignBranchIDs(cfg.blockList(), joinRules, knownEdges);
        cfg.setNumberOfBranches(nextBranchID - 1);
    }

    /**
     * Recursive implementation of branch ID propagation algorithm.
     *
     * @param nodeSet List of nodes for which branch IDs should be assigned
     * to or propagated along outgoing edges. This parameter receives subsets
     * of the nodes in the graph during recursion to compute IDs on back edges.
     * @param joinRules Rule &quot;table&quot; which maintains rules mapping
     * sets of branch IDs to sets of substitute branch IDs (typically a
     * smaller set). For example, a simple <code>if</code> might generate
     * a rule of the form [2,3]->[1], which indicates that if branch IDs
     * 2 and 3 are found on incoming edges, branch ID 1 will be substituted
     * when propagating IDs on the outgoing edges.
     * @param knownEdges Set of edges to which branch IDs have already
     * been assigned.
     *
     * @throws TransformationException If the CFG is malformed in
     * such a way that the algorithm cannot complete.
     */
    @SuppressWarnings("unchecked")
    private void assignBranchIDs(List<Node> nodeSet, ReductionRules joinRules,
                                 Set<Edge> knownEdges)
                 throws TransformationException {
        Iterator blocks = nodeSet.iterator();
        while (blocks.hasNext()) {
            Block curBlock = (Block) blocks.next();

            MinimalEdgeSet inSet = computeInSet(curBlock, joinRules,
                                                knownEdges);

            if (curBlock.getSuccessorCount() == 0) {
                if (curBlock.getType() != BlockType.EXIT) {
                    throw new TransformationException("Invalid class file " +
                        "or control flow: non-exit block has no successor: " +
                        Handler.LINE_SEP + cfg.displayString +
                        Handler.LINE_SEP + curBlock);
                }

                if (curBlock.getSubType() == BlockSubType.SUMMARYTHROW) {
                    cfg.setSummaryBranchID(inSet.getOnlyID());
                }

                continue;
            }

            if (curBlock.getSubType() == BlockSubType.FINALLY) {
                continue;
            }

            CFEdge[] outEdges = (CFEdge[]) cfg.getEdges(curBlock,
                Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
            List<Edge> workEdges = new ArrayList<Edge>(4);
            for (int i = 0; i < outEdges.length; i++) {
                if (knownEdges.contains(outEdges[i])) {
                    BranchID outID = outEdges[i].getBranchID(0);
                    if (inSet.contains(outID)) {
                        inSet.remove(outID);
                        continue;
                    }
                }
                workEdges.add(outEdges[i]);
            }

            int outCount = workEdges.size();
            if (outCount == 0) {
                continue;
            }
            else if (outCount == 1) {
                CFEdge e = (CFEdge) workEdges.iterator().next();
                e.setBranchIDs(inSet.asSet());

                // If there were more actual edges out of the node, but
                // only one work edge, we are going to propagate an
                // enclosing logical branch ID. Induce a new branch ID
                // that will not be propagated any further to distinguish
                // the immediate outgoing edge.
                if (outEdges.length > 1) {
                    dnProp.add(e.addBranchID(nextBranchID++,
                                             getBranchType(curBlock)));
                }

                assert inSet.asSet().size() != 0 :
                    "Branch ID inSet is empty at " + e;

                knownEdges.add(e);

                if (curBlock.getSubType() == BlockSubType.JSR) {
                    String matchLabel = e.getLabel() + "&&" + e.getAuxLabel();
                    Block finallyBlock = cfg.getBlock(e.getSpecialNodeID());

                    CFEdge[] retEdges = (CFEdge[]) cfg.getEdges(finallyBlock,
                        Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
                    for (int i = 0; i < retEdges.length; i++) {
                        String curLabel = retEdges[i].getLabel() + "&&" +
                                          retEdges[i].getAuxLabel();
                        if (curLabel.equals(matchLabel)) {
                            retEdges[i].setBranchIDs(inSet.asSet());
                            break;
                        }
                    }
                }
            }
            else {
                Set<Object> reductionSet = new THashSet();
                Iterator es = workEdges.iterator();
                while (es.hasNext()) {
                    CFEdge outEdge = (CFEdge) es.next();
                    if (!knownEdges.contains(outEdge)) {
                        BranchID bid = outEdge.addBranchID(nextBranchID,
                            getBranchType(curBlock));
                        reductionSet.add(bid);
                        nextBranchID += 1;
                        knownEdges.add(outEdge);
                    }
                    else {
                        reductionSet.addAll(outEdge.branchIDs);
                    }
                }
                Set<Object> substitution = inSet.asSet();
                joinRules.put(reductionSet, substitution);
            }
        }
    }

    /**
     * Computes the set of branch IDs propagated to a node by all of its
     * incoming edges.
     *
     * <p>The &quot;inSet&quot; is the minimized union of the branch IDs
     * found on all incoming edges. This method may cause calls to
     * {@link CFG#findBranchNode} and {@link CFG#assignBranchIDs} to
     * determine IDs for edges which do not yet have assigned IDs. Most
     * often this is the case for back-edges (edges which cause control
     * flow from a later node back to the current node), but may also
     * occur recursively under some circumstances. If the current node
     * has no predecessors, a new branch ID is assigned which becomes
     * the &quot;inSet&quot;</p>.
     *
     * @param blk Basic block for which the &quot;inSet&quot; is to be
     * determined.
     * @param joinRules Map of reduction rules for minimizing branch IDs
     * as a result of joining paths in the control flow.
     * @param knownEdges Set of edges which have already been processed.
     *
     * @return The minimized set of branch ID's which reach the given
     * node.
     *
     * @throws TransformationException If the CFG is malformed in
     * such a way that the algorithm cannot complete.
     */
    private MinimalEdgeSet computeInSet(Block blk, ReductionRules joinRules,
                                        Set<Edge> knownEdges)
                           throws TransformationException {
        MinimalEdgeSet inSet = new MinimalEdgeSet(joinRules);

        int predecessorCount = blk.getPredecessorCount();
        if (predecessorCount == 0) {
            inSet.add(new BranchID(nextBranchID++, getBranchType(blk)));
        }
        else {
            CFEdge[] inEdges = (CFEdge[])
                cfg.getEdges(blk, Graph.MATCH_INCOMING, CFEdge.ZL_ARRAY);
            for (int i = 0; i < inEdges.length; i++) {
                Set<BranchID> idsOnEdge = inEdges[i].getBranchIDSet();

                if (idsOnEdge.size() == 0) {
                    LinkedList<Node> blockList = new LinkedList<Node>();
                    boolean processList = findBranchNode(
                        cfg.getBlock(inEdges[i].getPredNodeID()), blk,
                        blockList, joinRules, knownEdges);
                    if (DEBUG) {
                        System.out.println("process: " + processList +
                            "  " + blockList);
                    }
                    if (processList) {
                        assignBranchIDs(blockList, joinRules, knownEdges);
                    }
                }

                idsOnEdge = inEdges[i].getBranchIDSet();
                idsOnEdge.removeAll(dnProp);
                inSet.addAll(idsOnEdge);
            }
        }

        return inSet;
    }

    /**
     * Finds the originating node of a branch edge and assigns an ID
     * (or IDs) to it (them) to be propagated from the target of the back-edge.
     *
     * @param blk Basic block at which the search should begin. This should be
     * the predecessor node of the basic block edge for which the branch IDs
     * need to be determined.
     * @param stopBlk Basic block at which the search should stop, which is
     * expected to be the basic block from which the search initiated. This
     * prevents infinite looping over the graph in cases where the control
     * flow does or appears to contain and infinite loop.
     * @param workList List into which the nodes traversed to find the origin
     * of the branch edge will be placed. The nodes in this list are guaranteed
     * to be completely dominated by the node at which the branch originates.
     * It is intended that the branch ID routine will then be called
     * recursively on this subset of nodes in the graph by
     * {@link #computeInSet}, which will ensure that they are processed before
     * work continues at the node which originally triggered the search.
     * @param joinRules Map of reduction rules for minimizing branch IDs.
     * @param knownEdges Set of edges which have already been processed.
     *
     * @return A boolean indicating whether the algorithm should propagate
     * IDs along the path starting at the located branch node. This is
     * <code>false</code> in some cases to prevent infinite recursion over
     * the graph.
     *
     * @throws TransformationException If the CFG is malformed in
     * such a way that the algorithm cannot complete.
     */
    @SuppressWarnings("unchecked")
    private boolean findBranchNode(Block blk, Block stopBlk,
            LinkedList<Node> workList, ReductionRules joinRules,
            Set<Edge> knownEdges)
            throws TransformationException {
        CFEdge followEdge = null;
        outer: {
            CFEdge[] es = null;
            try {
                es = (CFEdge[])
                    cfg.getEdges(blk, Graph.MATCH_INCOMING, CFEdge.ZL_ARRAY);
            }
            catch (NoSuchElementException e) {
                // Strictly speaking, this means the graph is invalid
                // (incomplete). However we can just induce a new branch ID
                // later on. It should never happen once all calls including
                // Java standard calls are properly recognized.
                break outer;
            }
            for (int i = 0; i < es.length; i++) {
                if (!knownEdges.contains(es[i])) {
                    followEdge = es[i];
                    break outer;
                }
            }
            workList.addFirst(blk);
            return true;
        }

        if (blk.getSuccessorCount() == 0) {
            throw new SofyaError("Required successors not found in graph");
        }
        else if (blk.getSuccessorCount() == 1) {
            if (blk == stopBlk) {
                Block loopEnd = (workList.size() > 0)
                                ? (Block) workList.getLast()
                                : stopBlk;
                System.err.println("WARNING: There is a high " +
                    "probability that the method:\n\"" + cfg.displayString +
                    "\"\ncontains an infinite loop\nfrom " +
                    stopBlk + " to " + loopEnd);
                return false;
            }
            else {
                if (blk.getPredecessorCount() == 0) {
                    Block successor = (Block) blk.successorList().get(0);
                    CFEdge e = (CFEdge) cfg.getEdge(blk, successor);
                    e.addBranchID(nextBranchID++, getBranchType(blk));
                    knownEdges.add(e);
                    return true;
                }
                else {
                   workList.addFirst(blk);
                   return findBranchNode(cfg.getBlock(
                       followEdge.getPredNodeID()),
                       stopBlk, workList, joinRules, knownEdges);
                }
            }
        }
        else {
            Block successor;
            if (workList.size() == 0) {
                successor = stopBlk;
            }
            else {
                successor = (Block) workList.getFirst();
            }

            CFEdge[] edges = (CFEdge[])
                cfg.getEdges(blk, Graph.MATCH_OUTGOING, CFEdge.ZL_ARRAY);
            for (int i = 0; i < edges.length; i++) {
                if (cfg.getBlock(edges[i].getSuccNodeID()) == successor) {
                    if (!knownEdges.contains(edges[i])) {
                        ((CFEdge) edges[i]).addBranchID(nextBranchID++,
                            getBranchType(blk));
                        knownEdges.add(edges[i]);
                    }
                }
            }

            Set<Object> reductionSet = new THashSet();
            for (int i = 0; i < edges.length; i++) {
                if (!knownEdges.contains(edges[i])) {
                    return true;
                }
                reductionSet.addAll(((CFEdge) edges[i]).branchIDs);
            }

            if (blk == stopBlk) {
                Set<Object> inSet = new THashSet();
                edges = (CFEdge[])
                    cfg.getEdges(blk, Graph.MATCH_INCOMING, CFEdge.ZL_ARRAY);
                for (int i = 0; i < edges.length; i++) {
                    inSet.addAll(((CFEdge) edges[i]).branchIDs);
                }
                joinRules.put(reductionSet, inSet);
                return false;
            }
            else {
                Set<Object> substitution =
                    computeInSet(blk, joinRules, knownEdges).asSet();
                if (substitution.containsAll(reductionSet)) {
                    System.err.println("WARNING: There is a high " +
                        "probability that the method:\n\"" +
                        cfg.displayString +
                        "\"\ncontains an infinite loop");
                    substitution.removeAll(reductionSet);
                }
                joinRules.put(reductionSet, substitution);
                return true;
            }
        }
    }

    /**
     * Determine the branch type based on the type of the block from
     * which it originates.
     *
     * @param b Block from which a branch edge whose type needs to
     * be determined originates.
     *
     * @return The type of a branch edge originating on the given
     * node.
     */
    private BranchType getBranchType(Block b) {
        switch (b.getType().toInt()) {
        case BlockType.IENTRY:
            return BranchType.ENTRY;
        case BlockType.ICALL:
            return BranchType.CALL;
        case BlockType.IBLOCK:
            switch (b.getSubType().toInt()) {
            case BlockSubType.IIF:
                return BranchType.IF;
            case BlockSubType.ISWITCH:
                return BranchType.SWITCH;
            case BlockSubType.ITHROW:
                return BranchType.THROW;
            case BlockSubType.ISUMMARYTHROW:
                return BranchType.OTHER;
            default:
                return BranchType.DONTCARE;
            }
        default:
            return BranchType.DONTCARE;
        }
    }

    /*************************************************************************
     * Representation of a set of edges using their branch ID values
     * which is always eagerly minimized based on currently active edge
     * join rules.
     */
    @SuppressWarnings("unchecked")
    private class MinimalEdgeSet {
        /** Set containing the edges represented as branch IDs. */
        private Set<Object> minEdges = new THashSet();
        /** Map containing joined edge ID reduction rules. */
        private ReductionRules minRules = null;

        /**
         * Constructs an edge set backed by the given reduction
         * rules for minimization.
         */
        public MinimalEdgeSet(ReductionRules minRules) {
            this.minRules = minRules;
        }

        /**
         * Adds a new edge to the set.
         *
         * <p>The new ID is added to the edge set, and then the reduction rules
         * are applied linearly to the set. If any matches are found, the given
         * IDs are joined and replaced with the IDs specified by the matching
         * rule.</p>
         *
         * @param edgeID Branch ID of the edge to be added.
         */
        public void add(BranchID edgeID) {
            minEdges.add(edgeID);
            minRules.reduce(minEdges);
        }

        /**
         * Adds all of the edges in a given set to the minimized set.
         *
         * @param ids Set containing branch IDs of all edges to be added.
         */
        public void addAll(Set<? extends Object> ids) {
            Iterator it = ids.iterator();
            int size = ids.size();
            for (int i = size; i-- > 0; ) {
                add((BranchID) it.next());
            }
        }

        /**
         * Removes an edge from the set.
         *
         * <p>Minimizations will <b>not</b> be reversed by this operation.
         * Removing edges from the set may have the effect of preventing
         * certain reduction rules from ever being applied in the future.</p>
         *
         * @param Branch ID of the edge to be removed.
         */
        public boolean remove(BranchID edgeID) {
            return minEdges.remove(edgeID);
        }

        /**
         * Removes all of the edges in a given set from the minimized set.
         *
         * @param ids Set containing branch IDs of all edges to be removed.
         */
        public void removeAll(Set<? extends Object> ids) {
            Iterator it = ids.iterator();
            int size = ids.size();
            for (int i = size; i-- > 0; ) {
                remove((BranchID) it.next());
            }
        }

        public boolean contains(BranchID edgeID) {
            return minEdges.contains(edgeID);
        }

        public int getOnlyID() {
            if (minEdges.size() > 1) {
                throw new IllegalStateException("Set contains more than " +
                    "one ID");
            }
            return ((BranchID) minEdges.iterator().next()).getID();
        }

        /**
         * Removes all of the edges from the set.
         */
        public void clear() {
            minEdges.clear();
        }

        /**
         * Returns a set view of the minimized edge set.
         *
         * @return A <code>Set</code> copy of the minimized edges currently
         * known. Changes to this copy will not affect the internal state
         * of this <code>MinimalEdgeSet</code>.
         */
        @SuppressWarnings("unchecked")
        public Set<Object> asSet() {
            return new THashSet(minEdges);
        }

        /**
         * Returns an iterator over the minimized edges currently in the set.
         *
         * @return An iterator over the minimized edges in the set.
         * <i>Using the <code>remove</code> method of the iterator <b>will</b>
         * affect the internal state of the set, in a manner equivalent to
         * calling {@link MinimalEdgeSet#remove(Integer)}.</i>.
         */
        public Iterator<Object> iterator() {
            return minEdges.iterator();
        }

        /**
         * Gets a string representation of the set of minimized edges.
         *
         * @return String representing the minimized edges in set notation.
         */
        public String toString() {
            return minEdges.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private class ReductionRules {
        private Map<Set<Object>, Set<Object>> rules = new THashMap();

        public ReductionRules() { }

        public void put(Set<Object> reduction, Set<Object> substitution) {
            Set<Set<Object>> keys = rules.keySet();
            Iterator<Set<Object>> it = keys.iterator();
            int size = keys.size();
            for (int i = size; i-- > 0; ) {
                Set<Object> rr = it.next();
                Set<Object> rs = get(rr);
                if (rs.containsAll(reduction)) {
                    rs.removeAll(reduction);
                    rs.addAll(substitution);
                }
                if (rs.containsAll(rr)) {
                    rs.removeAll(rr);
                }
                if (rs.size() == 0) {
                    it.remove();
                }
            }

            Collection<Set<Object>> values = rules.values();
            it = values.iterator();
            size = values.size();
            for (int i = size; i-- > 0; ) {
                Set<Object> rs = it.next();
                reduce(rs);
            }

            rules.put(reduction, substitution);
        }

        public Set<Object> get(Set<Object> reduction) {
            return rules.get(reduction);
        }

        public Set<Set<Object>> keySet() {
            return rules.keySet();
        }

        public Set<Object> reduce(Set<Object> s) {
            boolean changed = true;
            while (changed) {
                changed = false;
                Set<Set<Object>> keys = rules.keySet();
                Iterator<Set<Object>> it = keys.iterator();
                int size = keys.size();
                for (int i = size; i-- > 0; ) {
                    Set<Object> rs = it.next();
                    if (s.containsAll(rs)) {
                        s.removeAll(rs);
                        s.addAll(get(rs));
                        changed = true;
                    }
                }
            }
            return s;
        }

        public String toString() {
            return rules.toString();

            // Useful when rules are in a sequenced hash map to observe
            // how they are being generated during debugging
            /*StringBuffer sb = new StringBuffer();
            for (Iterator i = rules.keySet().iterator(); i.hasNext(); ) {
                Set key = (Set) i.next();
                Set value = (Set) get(key);
                sb.append(key.toString());
                sb.append("=");
                sb.append(value.toString());
                sb.append("  ");
            }
            return sb.toString();*/
        }
    }
}
