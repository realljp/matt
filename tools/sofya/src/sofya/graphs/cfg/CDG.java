

package sofya.graphs.cfg;

import java.util.*;

import sofya.base.MethodSignature;
import sofya.graphs.*;
import static sofya.base.SConstants.*;

import gnu.trove.TIntObjectHashMap;


public class CDG extends Graph {
    /** Signature of the method for which the control flow graph is built. */
    protected MethodSignature methodSignature;
    /** Human-friendly string for the method for which this CFG was built. */
    protected String displayString;  // Also the legacy key for handlers

    /** Lookup table which maps bytecode offsets to blocks, used to optimize
        retrieval of successor blocks when constructing edges. */
    protected TIntObjectHashMap blockOffsetMap = new TIntObjectHashMap();

    /** Number of branch IDs in the CFG. */
    private int branchCount = 1;
    /** ID of the branch which summarizes all exceptional exits from the method
        represented by the CFG which cannot be precisely identified. */
    private int summaryBranchID = -1;

    /** Records the next available edge ID. Only used internally by the CFG
        builder and type inference module. Not stored to file.*/
    int nextEdgeID = -1;

    /** Conditional compilation debug flag. */
    @SuppressWarnings("unused")
	private static final boolean DEBUG = false;

    /** Used by cache retrieval. */
    public CDG() { }

    /*************************************************************************
     * Creates a control flow graph.
     *
     * @param signature Signature of the method for which the control flow
     * graph will be built.
     * @param displayString Human-friendly string to represent the method
     * for which the control flow graph will be built.
     */
    protected CDG(MethodSignature signature, String displayString) {
        this.methodSignature = signature;
        this.displayString = displayString;
    }

    /*************************************************************************
     * Creates a control flow graph.
     *
     * <p>Used by the {@link MapHandler} primarily to provide legacy
     * support.</p>
     *
     * @param displayString Human-friendly string to represent the method
     * for which the control flow graph is/was built.
     */
    protected CDG(String displayString) {
        this.methodSignature = null;
        this.displayString = displayString;
    }

    /*************************************************************************
     * Gets the signature of the method that this CFG models.
     *
     * @return The signature of the method for which this CFG was constructed.
     */
    public MethodSignature getSignature() {
        return methodSignature;
    }

    /*************************************************************************
     * Sets the signature of the method that this CFG models.
     *
     * @param ms The signature of the method for which this CFG has
     * been constructed.
     */
    protected void setSignature(MethodSignature ms) {
        this.methodSignature = ms;
    }

    /*************************************************************************
     * Gets the basic blocks in this control flow graph.
     *
     * @return An array containing the basic blocks identified in the method.
     */
    public Block[] getBasicBlocks() {
        Block b[] = new Block[nodes.size()];
        nodes.toArray(b);
        return b;
    }

    /*************************************************************************
     * Gets the basic blocks in this control flow graph corresponding
     * to selected types.
     *
     * @param typeMask Bitmask indicating the types of basic blocks to
     * be retrieved.
     *
     * @return An array containing the basic blocks of the given type(s)
     * identified in the method.
     */
    public Block[] getBasicBlocks(int typeMask) {
        List<Block> resultList = new ArrayList<Block>(nodes.size());

        Iterator iterator = nodes.iterator();
        int size = nodes.size();
        for (int i = 0; i < size; i++) {
            Block b = (Block) iterator.next();
            if ((b.getType().toMask() & typeMask) > 0) {
                resultList.add(b);
            }
        }

        return (Block[]) resultList.toArray(new Block[resultList.size()]);
    }

    /*************************************************************************
     * Gets a block from the node list.
     *
     * @param id ID of the block to be retrieved.
     *
     * @return The block in the node list with the specified ID, if any.
     */
    public Block getBlock(int id) {
        // Block IDs should be assigned contiguously and normally are
        // expected to correspond to their index (offset by 1) in the
        // block list.
        if (id > nodes.size()) {
            System.err.println("WARNING: CDG may contain non-contiguous " +
                "block IDs!");
            return findBlock(id);
        }

        // If the expected preconditions are honored, we should normally
        // be done here
       
        
        Block block = (Block) nodes.get(id -1);
        if (block.getID() == id) {
            return block;
        }
        else {
            // If the expected preconditions are not honored,
            // attempt to recover by sorting the node list on the
            // block IDs
            Collections.sort(nodes,
                new Comparator<Object>() {
                    public int compare(Object o1, Object o2) {
                        if (o1 == o2) return 0;
                        if (!o1.getClass().equals(Block.class) ||
                                !o2.getClass().equals(Block.class)) {
                            throw new ClassCastException();
                        }
                        int id1 = ((Block) o1).getID();
                        int id2 = ((Block) o2).getID();
                        if (id1 < id2) {
                            return -1;
                        }
                        else if (id1 == id2) {
                            return 0;
                        }
                        else {
                            return 1;
                        }
                    }
                }
            );
            // Retrieve and verify again
            block = (Block) nodes.get(id-1 );
            if (block.getID() == id) {
                return block;
            }
            else {
                // Major problem with block list, attempt a linear
                // search anyway
                System.err.println("WARNING: CFG contains non-contiguous or " +
                    "redundant block IDs - first match will be returned!");
                return findBlock(id);
            }
        }
    }
    public Edge getEdge(Node sourceNode, Node sinkNode) {
        for (int i = 0; i < edges.size(); i++) {
            Edge e = (Edge) edges.get(i);
            if ((e.getPredNodeID() == sourceNode.getID())
                    && (e.getSuccNodeID() == sinkNode.getID())) {
                return e;
            }
        }
        return null;
    }
    private Block findBlock(int id) {
        Iterator iterator = nodes.iterator();
        for (int i = nodes.size(); i-- > 0; ) {
            Block block = (Block) iterator.next();
            if (block.getID() == id) {
                return block;
            }
        }
        throw new NoSuchElementException();
    }

    /*************************************************************************
     * Adds an edge to the list of edges.
     *
     * @param e Edge to be added to the CDG.
     *
     * @see sofya.graphs.Edge
     */
    protected void addEdge(Edge e) {
        super.addEdge(e);
        Block fromBlock = getBlock(e.getPredNodeID());
        Block toBlock = getBlock(e.getSuccNodeID());
        fromBlock.addSuccessor(toBlock);
        toBlock.addPredecessor(fromBlock);
    }

    /*************************************************************************
     * Adds a node to the CFG, creating an entry in the offset map so the
     * block can be retrieved by its start offset later.
     *
     * <p>If the given node is not a {@link sofya.graphs.cfg.Block}, this
     * method behaves identically to the implementation in
     * {@link sofya.graphs.Graph}. 'Virtual' blocks (entry, exit, and return)
     * are not added to the offset map. This is because the offset map is an
     * optimization to speed up the creation of the most common types of edges
     * representing flow of control through actual code. Creation of edges
     * pointing to virtual nodes is handled explicitly as necessary.</p>
     *
     * @param n Node to be added to the graph, typically expected to be a
     * {@link sofya.graphs.cfg.Block}.
     */
    protected void addNode(Node n) {
        super.addNode(n);
       
    }

    /*************************************************************************
     * Adds a block to the CFG.
     *
     * @param b Block to be added to the CFG.
     *
     * @see sofya.graphs.cfg.Block
     */
    protected void addBlock(Block b) {
        addNode(b);
    }



    /*************************************************************************
     * Returns the number of nodes in the control flow graph.
     *
     * @return The number of nodes in the control flow graph.
     */
    public int getNumberOfNodes() {
        return nodes.size();
    }

    /*************************************************************************
     * Returns the number of edges in the control flow graph.
     *
     * @return The number of edges in the control flow graph.
     */
    public int getNumberOfEdges() {
        return edges.size();
    }


    /*************************************************************************
     * Returns the highest node ID in the control flow graph.
     *
     * @return The highest ID of any node currently in the control flow graph.
     */
    public int getHighestNodeId() {
        return nodes.size();
    }

    /*************************************************************************
     * Returns the name of the method with which this control flow graph
     * is associated.
     *
     * @return The name of the method for which this control flow graph
     * was built.
     */
    public String getMethodName() {
        return displayString;
    }

    /*************************************************************************
     * Returns the ID of the root node of the control flow graph.
     *
     * @return The ID of the node that is the root of the control flow graph.
     */
    public int getRootNodeID() {
        return (getBlock(1)).getID();
    }


    /*************************************************************************
     * Returns a direct reference to the list of basic blocks in the
     * CFG, for use by handlers.
     *
     * @return The list of basic blocks in the CFG.
     */
    List<Node> blockList() {
        return nodes;
    }

    /*************************************************************************
     * Returns a direct reference to the list of edges in the
     * CFG, for use by handlers.
     *
     * @return The list of edges in the CFG.
     */
    List<Edge> edgeList() {
        return edges;
    }

    /*************************************************************************
     * Returns string representation of the control flow graph, which
     * is a list of the edges that constitute the CFG.
     *
     * @return List of edges that constitute this control flow graph.
     *
     * @see sofya.graphs.cfg.Block#toString()
     * @see sofya.graphs.Edge#toString()
     */
    public String toString() {
        return edgesToString();
    }

    /*************************************************************************
     * Gets string representation of the edges that compose the control flow
     * graph for this method.
     */
    private String edgesToString() {
        StringBuilder sb = new StringBuilder();
        String label;
        Edge e;
        for (int i = 0; i < edges.size(); i++) {
            e  = (Edge) edges.get(i);
            label = e.getLabel();
            if ((label == null) || (label.length() == 0)) {
                sb.append("(nl): ");
            }
            else {
                sb.append(label + ": ");
            }
            sb.append(getBlock(e.getPredNodeID()).toString() + "  ->  " +
                      getBlock(e.getSuccNodeID()).toString() + "\n");
        }
        return sb.toString();
    }
}

/****************************************************************************/

