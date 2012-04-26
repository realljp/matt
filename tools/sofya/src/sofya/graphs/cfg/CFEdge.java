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

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.Iterator;
import java.util.Collection;

import sofya.graphs.Edge;
import static sofya.base.SConstants.*;

import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.ObjectType;

import gnu.trove.THashMap;

/**
 * A control flow edge is an edge in a Sofya control flow graph.
 * It records additional information required for handling exceptional
 * constructs and tracking branch flow in the CFG.
 *
 * @author Alex Kinneer
 * @version 11/16/2004
 *
 * @see sofya.graphs.Graph
 * @see sofya.graphs.Node
 */
@SuppressWarnings("unchecked")
public class CFEdge extends Edge {
    /** Control flow branch IDs assigned to the edge. Note that the
        branch types associated with IDs are only guaranteed to be
        valid on edges whose predecessor has multiple successors. */    
    protected SortedSet<BranchID> branchIDs = new TreeSet<BranchID>();
    /** Maps edge branch types to their associated branch IDs. Note that
        if an edge is entirely encapsulated by another &quot;logical&quot;
        edge or edges of the same type, only the mapping to the most
        specific (&quot;nearest&quot;, or this edge's) branch ID is
        retained, since that is what we want to observe. We will already
        know about the enclosing edge(s). */
    protected Map<Object, Object> typeIDMap = new THashMap();
    
    /** Secondary label information (used with JSR and RET edges). */
    protected String auxLabel;
    /** ID of a node which has some special relationship to this edge.
        Edges from JSR instructions will set this to the ID of the node for
        the corresponding RET. */
    protected int specialNodeID;
    /** Type associated with the label, if appropriate. */
    protected Type labelType = Type.UNKNOWN;
    
    /** Zero-length control flow edge array useful for specifying array cast
        types to methods such
        {@link sofya.graphs.Graph#getEdges(Node,Node,Edge[])}. */
    public static final CFEdge[] ZL_ARRAY = new CFEdge[0];

    /*************************************************************************
     * Creates an edge with ID zero, an empty label, and the successor and
     * predecessor nodes set to zero.
     */
    public CFEdge() {
        super();
        auxLabel = null;
        specialNodeID = -1;
    }

    /*************************************************************************
     * Creates an edge with the given label, ID, successor, and predecessor
     * nodes.
     *
     * @param id ID of the new edge.
     * @param s ID of the new edge's successor node.
     * @param p ID of the new edge's predecessor node.
     * @param str Label to be assigned to the new edge.
     */
    public CFEdge(int id, int s, int p, String str) {
        super(id, s, p, str);
        auxLabel = null;
        specialNodeID = -1;
    }
    
    /*************************************************************************
     * Creates an edge with the given label, ID, successor and predecessor
     * nodes, secondary label, and special relationship node.
     *
     * @param id ID of the new edge.
     * @param s ID of the new edge's successor node.
     * @param p ID of the new edge's predecessor node.
     * @param label Label to be assigned to the new edge.
     * @param auxLabel Secondary label to be assigned to the new edge.
     * @param spNode ID of node to be marked as having a special relationship
     * to this edge.
     */
    public CFEdge(int id, int s, int p, String label, String auxLabel,
                  int spNode) {
        super(id, s, p, label);
        this.auxLabel = auxLabel;
        specialNodeID = spNode;
    }
    
    /*************************************************************************
     * Creates an edge with the given label, ID, successor and predecessor
     * nodes, and associated label type.
     *
     * @param id ID of the new edge.
     * @param s ID of the new edge's successor node.
     * @param p ID of the new edge's predecessor node.
     * @param type Type which is associated with this edge.
     */
    public CFEdge(int id, int s, int p, ObjectType type) {
        super(id, s, p, null);
        label = (type == null) ? "<any>" : type.toString();
        auxLabel = null;
        specialNodeID = -1;
        labelType = type;
    }
    
    /*************************************************************************
     * Adds a branch ID to this edge.
     *
     * @param id Branch ID to be added to this edge's branch ID set.
     */
    BranchID addBranchID(int id, BranchType type) {
        BranchID bid = new BranchID(id, type);
        branchIDs.add(bid);
        typeIDMap.put(type, bid);
        return bid;
    }
    
    /*************************************************************************
     * Removes a branch ID from this edge.
     *
     * @param id Branch ID to be removed from this edge's branch ID set.
     */
    boolean removeBranchID(BranchID id) {
        BranchType type = id.getType();
        if (typeIDMap.containsKey(type)) {
            typeIDMap.remove(type);
        }
        return branchIDs.remove(id);
    }
    
    /*************************************************************************
     * Gets a branch ID from this edge's branch ID set by index.
     *
     * <p>Branch IDs are stored in numerical order. This method iterates
     * over the ID set and returns the ID produced by the iterator after
     * <code>index</code> iterations.</p>
     *
     * @param index Index of the branch ID to be retrieved from the ID set.
     *
     * @return The branch ID at the given index in this edge's branch ID set.
     */
    public BranchID getBranchID(int index) {
        if (index < 0 || index > branchIDs.size() - 1) {
            throw new IndexOutOfBoundsException();
        }
        
        if (index == 0) {
            return (BranchID) branchIDs.first();
        }
        else if (index == branchIDs.size() - 1) {
            return (BranchID) branchIDs.last();
        }
        else {
            int i = 0;
            int size = branchIDs.size();
            Iterator ids = branchIDs.iterator();
            for (int j = size ; j-- > 0; i++) {
                BranchID bid = (BranchID) ids.next();
                if (i == index) {
                    return bid;
                }
            }
            throw new IndexOutOfBoundsException();
        }
    }
    
    /*************************************************************************
     * Gets the branch ID associated with the given branch type.
     *
     * <p>If there is more than one branch ID with the same associated type,
     * only the most specific branch ID is returned. In other words, the
     * branch ID associated with this particular branch decision is returned
     * rather than the ID of any logical branch edge encapsulating the
     * current edge. This allows proper instrumentation and observation of
     * decisions made inside of &apos;side loops&apos;.</p>
     *
     * @param type Type of the branch for which the associated branch ID
     * is to be retrieved.
     *
     * @return The branch ID on this edge which is associated with the
     * specified branch type.
     */
    public BranchID getBranchID(BranchType type) {
        return (BranchID) typeIDMap.get(type);
    }
    
    /*************************************************************************
     * Sets the branch IDs associated with this edge to those found in
     * the given collection of branch IDs.
     *
     * <p><b>Note:</b> The objects found in the collection <i>must</i> be
     * of type {@link BranchID}, or this method will fail.</p>
     *
     * @param ids Collection of {@link BranchID} objects
     * specifying the branch IDs to be associated with this edge.
     */
    void setBranchIDs(Collection ids) {
        branchIDs.clear();
        addBranchIDs(ids);
    }
    
    /*************************************************************************
     * Adds the branch IDs in the given collection to the set of IDs
     * associated with this edge.
     *
     * <p><b>Note:</b> The objects found in the collection <i>must</i> be
     * of type {@link BranchID}, or this method will fail.</p>
     *
     * @param ids Collection of {@link BranchID} objects specifying the
     * branch IDs to be added to this edge.
     */
    void addBranchIDs(Collection ids) {
        Iterator iterator = ids.iterator();
        for (int i = ids.size(); i-- > 0; ) {
            BranchID bid = (BranchID) iterator.next();
            branchIDs.add(bid);
            typeIDMap.put(bid.getType(), bid);
        }
    }
    
    /*************************************************************************
     * Gets the branch IDs associated with this edge as an array.
     *
     * <p>The branch IDs stored in the array will be sorted numerically.</p>
     *
     * @return A sorted array of the branch IDs associated with this edge.
     */
    public BranchID[] getBranchIDArray() {
        return (BranchID[]) branchIDs.toArray(
            new BranchID[branchIDs.size()]);
    }
    
    /*************************************************************************
     * Gets the branch IDs associated with this edge as an unmodifiable
     * sorted set.
     *
     * @return A sorted set containing the branch IDs associated with this
     * edge.
     */
    public SortedSet<BranchID> getBranchIDSet() {
        TreeSet<BranchID> ts = new TreeSet<BranchID>();
        ts.addAll(branchIDs);
        return ts;
    }
    
    /*************************************************************************
     * Sets the secondary label for this edge.
     *
     * <p>This field is used to record additional unique identifying
     * information related to JSR and RET edges. It may be specific to the
     * particular version of a class from which a CFG is built.</p>
     *
     * @param s New secondary label to be assigned to this edge.
     */
    public void setAuxLabel(String s) {
        auxLabel = s;
    }
    
    /*************************************************************************
     * Gets the secondary label for this edge.
     *
     * @return The secondary label assigned to this edge. There is no
     * general contract which requires that this label be version-independent.
     */
    public String getAuxLabel() {
        return auxLabel;
    }
    
    /*************************************************************************
     * Sets the ID of a node which has a special relationship to this edge.
     *
     * <p>This field is used in CFGs to store a reference in JSR edges to the
     * block that contains the matching RET instruction.</p>
     *
     * @param n ID of the node to be marked as having a special association
     * with this edge.
     */
    public void setSpecialNodeID(int n) {
        specialNodeID = n;
    }
    
    /*************************************************************************
     * Gets the ID of a node which has a special relationship to this edge.
     *
     * <p>For JSR edges, this is expected to be the ID of the node containing
     * the corresponding RET instruction.</p>
     *
     * @return The ID of a node that has been marked as having a special
     * relationship to this edge.
     */
    public int getSpecialNodeID() {
        return specialNodeID;
    }
    
    /*************************************************************************
     * Gets the type associated with this edge's label.
     *
     * @return The type of the object that would cause this edge to be taken.
     */
    public Type getLabelType() {
        return labelType;
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
        System.out.println(e.getLabel());
        e.setLabel("t");
        System.out.println(e.getLabel());
    }

    /**
     * A branch ID associated with an edge in a CFG.
     */
    public static class BranchID implements Comparable {
        /** The branch ID. */
        private int id = -1;
        /** The nearest type associated with the branch. */
        private BranchType type;
        
        private BranchID() { }
        
        /**
         * Creates a new branch ID.
         *
         * @param id ID of the branch.
         * @param type Nearest type associated with the branch.
         */
        public BranchID(int id, BranchType type) {
            this.id = id;
            this.type = type;
        }
        
        /**
         * Gets the branch ID.
         */
        public int getID() {
            return id;
        }
        
        /**
         * Gets the nearest branch type.
         */
        public BranchType getType() {
            return type;
        }
        
        /**
         * Compares two branch IDs for equality; only the integer IDs
         * are compared, types are ignored.
         */
        public boolean equals(Object obj) {
            if (this == obj) return true;
            
            if (!this.getClass().equals(obj.getClass())) {
                return false;
            }
            
            BranchID otherBid = (BranchID) obj;
            return this.id == otherBid.id;
        }
        
        /**
         * Returns the hash code for this branch ID, which is simply the
         * integer ID (branch type is not part of the computation).
         */
        public int hashCode() {
            return id;
        }
        
        /**
         * Compares this branch ID to another branch ID, ordering them
         * by their integer IDs only.
         */
        public int compareTo(Object obj) {
            if ((this == obj) || this.equals(obj)) return 0;
            
            BranchID otherBid = (BranchID) obj;
            if (this.id < otherBid.id) {
                return -1;
            }
            else if (this.id > otherBid.id) {
                return 1;
            }
            else {
                return 0;
            }
        }
        
        /**
         * Gets a string representation of the branch ID, including
         * its nearest branch type.
         */
        public String toString() {
            return "{ " + Integer.toString(id) + ", " + type.toString() + " }";
        }
    }
}

/****************************************************************************/
