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

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import sofya.base.Handler;
import sofya.base.exceptions.BadFileFormatException;

import gnu.trove.THashMap;

/**
 * A condition tree records information about the location conditions
 * constraining the observation of an event. It is optimized for fast
 * querying and support of wildcards.
 *
 * @author Alex Kinneer
 * @version 07/31/2006
 */
final class ConditionTree {
    private final RootNode root;

    private int size = 0;

    private transient boolean forceAdd = false;
    private transient int maxPathRank = 0;
    private transient final ArrayList<Object> nodePath =
        new ArrayList<Object>();

    private ConditionTree(RootNode node) {
        root = node;
    }

    ConditionTree(boolean inclusion, int rank) {
        root = new RootNode(inclusion, rank);
    }

    int size() {
        return size;
    }

    void addNode(String key, ConditionNode node) {
        if ((key == null) || (key.length() == 0)) {
            throw new IllegalArgumentException("Condition key cannot " +
                "be null or empty string");
        }

        if (key.endsWith(".*")) {
            key = key.substring(0, key.length() - 2);
        }

        ConditionNode currentNode = root;
        ConditionNode prevNode = null;
        ConditionNode nextNode = null;

        StringTokenizer stok = new StringTokenizer(key, ".");
        String nodeKey = null;
        while (stok.hasMoreTokens()) {
            nodeKey = stok.nextToken();

            if (node.rank > currentNode.maxChildRank) {
                currentNode.maxChildRank = node.rank;
            }
            maxPathRank = (currentNode.rank > maxPathRank)
                ? currentNode.rank : maxPathRank;

            nextNode = (ConditionNode) currentNode.children.get(nodeKey);
            if (nextNode == null) {
                ConditionNode insertNode;
                if (!key.endsWith(nodeKey)) {
                    insertNode = new ImpliedNode(node);
                }
                else {
                    insertNode = node.copy();
                }

                currentNode.children.put(nodeKey, insertNode);
                size += 1;

                if (currentNode instanceof ImpliedNode) {
                    ImpliedNode impNode = (ImpliedNode) currentNode;
                    if (forceAdd
                            && (insertNode.type() != ImpliedNode.TYPE)) {
                        if (impNode.rankingChild != null) {
                            insertNode.rank =
                                impNode.rankingChild.rank + 1;
                        }
                        else {
                            insertNode.rank = maxPathRank + 1;
                        }
                    }
                    if ((impNode.rankingChild == null) ||
                            (impNode.rankingChild.rank <= insertNode.rank)) {
                        impNode.rankingChildKey = nodeKey;
                        impNode.rankingChild = insertNode;
                    }
                }
                else if (forceAdd
                        && (insertNode.type() != ImpliedNode.TYPE)) {
                    insertNode.rank = maxPathRank + 1;
                }

                prevNode = currentNode;
                currentNode = insertNode;
            }
            else {
                prevNode = currentNode;
                currentNode = nextNode;
            }
            nodePath.add(prevNode);
        }

        if (nextNode != null) {
            if (forceAdd || (currentNode.rank <= node.rank)) {
                // Do not replace an explicit node with an implied node
                if ((currentNode.type() != ImpliedNode.TYPE) &&
                        (node.type() == ImpliedNode.TYPE)) {
                    return;
                }

                ConditionNode insertNode = node.copy();
                if (forceAdd
                        || (insertNode.rank > currentNode.maxChildRank)) {
                    // Prune the subtree from this node
                }
                else {
                    insertNode.children.putAll(currentNode.children);
                    insertNode.maxChildRank = currentNode.maxChildRank;
                }

                prevNode.children.put(nodeKey, insertNode);

                if (prevNode instanceof ImpliedNode) {
                    ImpliedNode impNode = (ImpliedNode) prevNode;
                    if (forceAdd) {
                        if ((impNode.rankingChild.rank == impNode.maxChildRank)
                                && impNode.rankingChildKey.equals(nodeKey)) {
                            insertNode.rank = impNode.rankingChild.rank;
                        }
                        else {
                            insertNode.rank =
                                impNode.rankingChild.rank + 1;
                        }
                    }
                    if ((impNode.rankingChild == null) ||
                            (impNode.rankingChild.rank <= insertNode.rank)) {
                        impNode.rankingChildKey = nodeKey;
                        impNode.rankingChild = insertNode;
                    }
                }
                else if (forceAdd) {
                    insertNode.rank = maxPathRank + 1;
                }

                currentNode = insertNode;
            }
        }

        if (forceAdd) {
            resolveMaxPathRank(currentNode);
        }

        maxPathRank = 0;
        nodePath.clear();

        //System.out.println(toString());
    }

    void addNode(String key, ConditionNode node, boolean force) {
        forceAdd = force;
        try {
            if (force) node.rank = root.rank;
            addNode(key, node);
        }
        finally {
            forceAdd = false;
        }
    }

    private final void resolveMaxPathRank(ConditionNode lastNode) {
//      System.out.println("==========================");
//      System.out.println(nodePath);
//      System.out.println("--");
//      System.out.println(lastNode);
//      System.out.println();

        int pathLen = nodePath.size();
        ListIterator pIt = nodePath.listIterator(pathLen);

        ConditionNode pathNode = (ConditionNode) pIt.previous();
        if (lastNode.rank > pathNode.maxChildRank) {
            pathNode.maxChildRank = lastNode.rank;
            lastNode = pathNode;
            pathLen -= 1;
        }
        else if (lastNode.rank == pathNode.maxChildRank) {
            return;
        }
        else {
            pIt.next();
        }

        for (int i = pathLen; i-- > 0; ) {
            pathNode = (ConditionNode) pIt.previous();
            if (pathNode.maxChildRank < lastNode.maxChildRank) {
                pathNode.maxChildRank = lastNode.maxChildRank;
            }
            else if (pathNode.maxChildRank == lastNode.maxChildRank) {
                return;
            }
            else { // pathNode.maxChildRank > lastNode.maxChildRank
                Collection children = pathNode.children.values();
                int count = children.size();
                Iterator cIt = children.iterator();
                int maxRank = 0;
                for (int j = count; j-- > 0; ) {
                    ConditionNode child = (ConditionNode) cIt.next();
                    if (child.maxChildRank > maxRank) {
                        maxRank = child.maxChildRank;
                    }
                    if (child.rank > maxRank) {
                        maxRank = child.rank;
                    }
                }
                if (maxRank <= pathNode.maxChildRank) {
                    pathNode.maxChildRank = maxRank;
                }
                else {
                    return;
                }
            }
            lastNode = pathNode;
        }
    }

    Condition checkConditions(String inLoc) {
        ConditionNode rankingNode = root;
        ConditionNode currentNode = root;

        StringTokenizer stok = new StringTokenizer(inLoc, ".");
        while (stok.hasMoreTokens()) {
            String s = stok.nextToken();

            ConditionNode nextNode =
                (ConditionNode) currentNode.children.get(s);
            if (nextNode == null) {
                break;
            }
            else {
                currentNode = nextNode;

                if (currentNode.rank >= rankingNode.rank) {
                    rankingNode = currentNode;
                }
            }
        }

        return rankingNode.condition();
    }

    Condition anyInclusions() {
        if (size == 0) {
            return new Condition(root.inclusion, root.rank);
        }

        return new Condition(true, root.rank);
    }

    /**
    * Creates a deep copy of this condition tree.
    *
    * @return A deep copy of the condition tree.
    */
    ConditionTree copy() {
        ConditionTree copy = new ConditionTree((RootNode) root.copy());
        copyNode(this.root, copy.root);
        return copy;
    }

    /**
    * Recursive implemention of the tree copy method.
    *
    * <p>This method copies all of the children from a node in one tree
    * to the corresponding node in another tree. This is a deep copy,
    * which triggers a recursive copying of all child nodes. Thus this
    * method will create a deep copy of the entire subtree rooted at
    * the specified node (or obviously the entire tree if starting from
    * the root node).</p>
    *
    * @param origNode Node in the original tree to be copied.
    * @param newNode Node in the target tree to which we are copying.
    */
    private void copyNode(ConditionNode origNode, ConditionNode newNode) {
        String origRankingChildKey = null;
        if (origNode instanceof ImpliedNode) {
            origRankingChildKey = ((ImpliedNode) origNode).rankingChildKey;
        }

        int count = origNode.children.size();
        Iterator iterator = origNode.children.keySet().iterator();
        for (int i = count; i-- > 0; ) {
            String childKey = (String) iterator.next();
            ConditionNode child =
                (ConditionNode) origNode.children.get(childKey);
            ConditionNode childCopy = child.copy();
            newNode.children.put(childKey, childCopy);

            if (childKey.equals(origRankingChildKey)) {
                ((ImpliedNode) newNode).rankingChild = child;
            }

            copyNode(child, childCopy);
        }
    }

    /**
    * Merges another condition tree into this tree.
    *
    * <p>Each node in the tree to be merged is added to this tree.
    * Only nodes of higher rank can replace nodes in this tree.
    * Nodes which are merged are deep copies, thus the argument
    * to this method remains an independent tree.</p>
    *
    * @param tree Tree to be merged into this tree.
    */
    void merge(ConditionTree tree) {
//      System.out.println("merge");
//      System.out.println("this tree: ");
//      System.out.println(toString());
//      System.out.println("merge tree: ");
//      System.out.println(tree.toString());
        mergeRoot(tree.root, false);
        mergeNode("", tree.root);

//      System.out.println("merged tree: ");
//      System.out.println(toString());
    }

    /**
    * Recursive implementation of the tree merge method.
    *
    * <p>This method recursively merges all of the children of the given
    * node into this tree. Thus this method will merge the entire subtree
    * rooted at the given node into this tree.</p>
    *
    * @param key Key associated with the node to be merged.
    * @param node Node to be merged.
    */
    private void mergeNode(String key, ConditionNode node) {
        int count = node.children.size();
        Iterator iterator = node.children.keySet().iterator();
        for (int i = count; i-- > 0; ) {
            String childKey = (String) iterator.next();
            ConditionNode child =
                (ConditionNode) node.children.get(childKey);
            String chainKey = key + "." + childKey;
            addNode(chainKey, child);
            mergeNode(chainKey, child);
        }
    }

    private void mergeRoot(RootNode newRoot, boolean force) {
        if (force || (newRoot.rank >= this.root.rank)) {
            this.root.inclusion = newRoot.inclusion;

            if (force || (newRoot.rank > this.root.maxChildRank)) {
                this.root.children.clear();
                this.root.rank = 1;
                this.root.maxChildRank = 0;
                this.root.rankingChildKey = null;
                this.root.rankingChild = null;
            }
            else {
                this.root.rank = newRoot.rank;
            }
        }
    }

    void merge(RootNode newRoot, List keys, List nodes,
            boolean force) {
        if (newRoot != null) {
            mergeRoot(newRoot, force);
        }
        int keyCnt = keys.size();
        Iterator keyIter = keys.iterator();
        Iterator nodeIter = nodes.iterator();
        for (int i = keyCnt; i-- > 0; ) {
            String key = (String) keyIter.next();
            ConditionNode node = null;
            try {
                node = (ConditionNode) nodeIter.next();
            }
            catch (NoSuchElementException e) {
                return;
            }
            addNode(key, node, force);
        }
    }

    void clear(RootNode newRoot) {
        mergeRoot(newRoot, true);
    }

    NodeIterator leafIterator() {
        return new LeafIterator(root);
    }

    NodeIterator preorderIterator() {
        return new PreorderIterator(root);
    }

    NodeIterator postorderIterator() {
        return new PostorderIterator(root);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString("root", root, sb, 0);
        return sb.toString();
    }

    private void toString(String key, ConditionNode node,
            StringBuilder sb, int depth) {
        for (int i = depth; i-- > 0; ) {
            sb.append("  ");
        }
        sb.append(key);
        sb.append(": ");
        sb.append(node.toString());
        sb.append(Handler.LINE_SEP);

        int count = node.children.size();
        Iterator iterator = node.children.keySet().iterator();
        for (int i = count; i-- > 0; ) {
            String childKey = (String) iterator.next();
            ConditionNode child =
                (ConditionNode) node.children.get(childKey);
            toString(childKey, child, sb, depth + 1);
        }
    }

    void serialize(DataOutputStream stream) throws IOException {
        stream.writeInt(size);
        serializeNode("", root, stream);
    }

    void serializeNode(String key, ConditionNode node,
            DataOutputStream stream) throws IOException {
        stream.writeUTF(key);
        stream.writeInt(node.type());
        stream.writeInt(node.rank);
        stream.writeBoolean(node.inclusion);
        stream.writeInt(node.maxChildRank);
        if (node instanceof ImpliedNode) {
            ImpliedNode impNode = (ImpliedNode) node;
            if (impNode.rankingChildKey != null) {
                stream.writeByte(1);
                stream.writeUTF(impNode.rankingChildKey);
            }
            else {
                stream.writeByte(0);
            }
        }

        int count = node.children.size();
        Iterator iterator = node.children.keySet().iterator();
        stream.writeInt(count);
        for (int i = count; i-- > 0; ) {
            String childKey = (String) iterator.next();
            serializeNode(childKey,
                (ConditionNode) node.children.get(childKey), stream);
        }
    }

    static ConditionTree deserialize(DataInputStream stream)
            throws IOException {
        int size = stream.readInt();
        RootNode root = null;
        try {
            stream.readUTF(); // Read the unused root key
            root = (RootNode) deserializeNode(stream);
        }
        catch (ClassCastException e) {
            throw new BadFileFormatException("Malformed condition tree, " +
                "could not read root node", e);
        }

        ConditionTree tree = new ConditionTree(root);
        tree.size = size;

//      System.out.println(tree.toString());

        return tree;
    }

    static ConditionNode deserializeNode(DataInputStream stream)
            throws IOException {
        ConditionNode node = null;

        int type = stream.readInt();
        switch (type) {
        case RootNode.TYPE:
            node = new RootNode();
            break;
        case ImpliedNode.TYPE:
            node = new ImpliedNode();
            break;
        case InNode.TYPE:
            node = new InNode();
            break;
        case NotNode.TYPE:
            node = new NotNode();
            break;
        default:
            throw new AssertionError("Unknown condition node type");
        }

        node.rank = stream.readInt();
        node.inclusion = stream.readBoolean();
        node.maxChildRank = stream.readInt();

        String rankingChildKey = null;
        if (node instanceof ImpliedNode) {
            if (stream.readByte() == 1) {
                rankingChildKey = stream.readUTF();
            }
        }

        int count = stream.readInt();
//      System.out.println("type: " + type);
//      System.out.println("rank: " + node.rank);
//      System.out.println("inclusion: " + node.inclusion);
//      System.out.println("rankingChildKey: " + rankingChildKey);
//      System.out.println("count: " + count);
        for (int i = count; i > 0; i--) {
            String childKey = stream.readUTF();
            ConditionNode child = deserializeNode(stream);
            if (childKey.equals(rankingChildKey)) {
                ImpliedNode impNode = (ImpliedNode) node;
                impNode.rankingChildKey = rankingChildKey;
                impNode.rankingChild = child;
            }
            node.children.put(childKey, child);
        }

        return node;
    }

    final static class Condition {
        public final boolean inclusion;
        public final int rank;
        public final Object extra;

        public static final Condition DEFAULT_INCLUDE =
            new Condition(true, -1);
        public static final Condition DEFAULT_EXCLUDE =
            new Condition(false, -1);

        private Condition() {
            throw new AssertionError("Illegal constructor");
        }

        Condition(boolean inclusion, int rank) {
            this(inclusion, rank, null);
        }
        
        Condition(boolean inclusion, int rank, Object extra) {
            this.inclusion = inclusion;
            this.rank = rank;
            this.extra = extra;
        }
    }

    @SuppressWarnings("unchecked")
    abstract static class ConditionNode {
        protected int rank;
        protected boolean inclusion;
        
        final Map<Object, Object> children = new THashMap();
        protected int maxChildRank;

        private ConditionNode() {
        }

        protected ConditionNode(boolean inclusion, int rank) {
            this.inclusion = inclusion;
            this.rank = rank;
        }

        protected int rank() {
            return rank;
        }

        protected boolean inclusion() {
            return inclusion;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("{ ");
            sb.append(typeString());
            sb.append(", ");
            sb.append(rank);
            sb.append(", ");
            sb.append(inclusion);
            sb.append(", ");
            sb.append(maxChildRank);
            sb.append(" }");
            return sb.toString();
        }

        protected abstract Condition condition();

        protected abstract int type();

        protected abstract String typeString();

        protected abstract ConditionNode copy();
    }

    static class ImpliedNode extends ConditionNode {
        public static final byte TYPE = 1;

        public String rankingChildKey = null;
        public ConditionNode rankingChild = null;

        ImpliedNode() {
            super();
        }

        ImpliedNode(boolean inclusion, int rank) {
            super(inclusion, rank);
        }

        ImpliedNode(ConditionNode node) {
            super(node.inclusion, node.rank);
        }

        protected Condition condition() {
            switch (rankingChild.type()) {
            case ImpliedNode.TYPE:
                return rankingChild.condition();
            case InNode.TYPE:
                return new Condition(!rankingChild.inclusion,
                    rankingChild.rank, ((InNode) rankingChild).extra);
            case NotNode.TYPE:
                return new Condition(rankingChild.inclusion,
                    rankingChild.rank, ((NotNode) rankingChild).extra);
            default:
                throw new AssertionError("Unknown condition node type: " +
                    rankingChild.type());
            }
        }

        protected int type() {
            return TYPE;
        }

        protected String typeString() {
            return "IMPLIED";
        }

        protected ConditionNode copy() {
            ImpliedNode copy = new ImpliedNode(inclusion, rank);
            copy.rankingChildKey = rankingChildKey;
            return copy;
        }

        public String toString() {
            return super.toString() + ", ( " + rankingChildKey + " )";
                //": " + rankingChild + " )";
        }
    }

    final static class RootNode extends ImpliedNode {
        public static final byte TYPE = 0;

        RootNode() {
        }

        RootNode(RootNode node) {
            super(node.inclusion, node.rank);
        }

        RootNode(boolean inclusion, int rank) {
            super(inclusion, rank);
        }

        protected Condition condition() {
            if (children.size() == 0) {
                return new Condition(inclusion, rank);
            }
            return super.condition();
        }

        protected int type() {
            return TYPE;
        }

        protected String typeString() {
            return "ROOT";
        }

        protected ConditionNode copy() {
            RootNode copy = new RootNode(inclusion, rank);
            copy.rankingChildKey = rankingChildKey;
            return copy;
        }
    }

    final static class InNode extends ConditionNode {
        public static final byte TYPE = 2;
        
        protected Object extra;

        InNode() {
        }

        InNode(boolean inclusion, int rank) {
            super(inclusion, rank);
        }
        
        InNode(boolean inclusion, int rank, Object extra) {
            super(inclusion, rank);
            this.extra = extra;
        }

        protected Condition condition() {
            return new Condition(inclusion, rank, extra);
        }

        protected int type() {
            return TYPE;
        }

        protected String typeString() {
            return "IN";
        }

        protected ConditionNode copy() {
            return new InNode(inclusion, rank, extra);
        }
    }

    final static class NotNode extends ConditionNode {
        public static final byte TYPE = 3;
        
        protected Object extra;

        NotNode() {
        }

        NotNode(boolean inclusion, int rank) {
            super(inclusion, rank);
        }
        
        NotNode(boolean inclusion, int rank, Object extra) {
            super(inclusion, rank);
            this.extra = extra;
        }

        protected Condition condition() {
            return new Condition(!inclusion, rank, extra);
        }

        protected int type() {
            return TYPE;
        }

        protected String typeString() {
            return "NOT";
        }

        protected ConditionNode copy() {
            return new NotNode(inclusion, rank, extra);
        }
    }

    public static interface NodeIterator {
        void advance();

        boolean hasNext();

        String key();

        String keyPrefix();

        String fullKey();

        ConditionNode value();

        ConditionNode parent();

        int depth();
    }

    private static abstract class AbstractNodeIterator
            implements NodeIterator {
        protected ConditionNode curNode;
        protected Iterator curChildren;

        protected String fullKeyPrefix;
        protected String key;
        protected String fullKey;
        protected ConditionNode value;
        protected int depth;
        protected ConditionNode parent;

        protected List<Object> keyStack = new ArrayList<Object>(5);
        protected List<Object> iterStack = new ArrayList<Object>(5);
        protected List<Object> nodeStack = new ArrayList<Object>(5);

        protected StringBuilder keyBuilder = new StringBuilder();

        private AbstractNodeIterator() {
            throw new AssertionError("Illegal constructor");
        }

        protected AbstractNodeIterator(RootNode root) {
            moveInto(root);
        }

        protected abstract void moveInto(ConditionNode node);

        protected abstract void moveUp();

        private final void moveToNext() {
            //System.out.println("moveToNext::" + curNode);
            if (curChildren.hasNext()) {
                String childKey = (String) curChildren.next();
                ConditionNode child =
                    (ConditionNode) curNode.children.get(childKey);
                keyStack.add(childKey);
                iterStack.add(curChildren);
                nodeStack.add(curNode);
                moveInto(child);
            }
            else {
                moveUp();
            }
        }

        public boolean hasNext() {
            return (curNode != null);
        }

        public void advance() {
            //System.out.println("advance::" + curNode);
            if (curNode == null) {
                throw new NoSuchElementException();
            }

            // Store current values for retrieval
            int stackSize = keyStack.size();
            int stopIndex = stackSize - 1;
            key = (String) keyStack.get(stackSize - 1);
            if (stackSize > 1) {
                keyBuilder.append(keyStack.get(0));
                for (int i = 1; i < stopIndex; i++) {
                    keyBuilder.append(".");
                    keyBuilder.append(keyStack.get(i));
                }
                fullKeyPrefix = keyBuilder.toString();
                keyBuilder.append(".");
                keyBuilder.append(key);
                fullKey = keyBuilder.toString();
            }
            else {
                fullKeyPrefix = "";
                fullKey = key;
            }
            keyBuilder.setLength(0);
            value = curNode;
            depth = stackSize;
            parent = (ConditionNode) nodeStack.get(stackSize - 1);

            // Advance in tree
            moveToNext();
        }

        public String key() {
            //System.out.println("-->key");
            if (key == null) {
                throw new IllegalStateException();
            }
            return key;
        }

        public String keyPrefix() {
            if (fullKeyPrefix == null) {
                throw new IllegalStateException();
            }
            return fullKeyPrefix;
        }

        public String fullKey() {
            return fullKey;
        }

        public ConditionNode value() {
            if (value == null) {
                throw new IllegalStateException();
            }
            return value;
        }

        public ConditionNode parent() {
            return parent;
        }

        public int depth() {
            return depth;
        }
    }

    private final static class PostorderIterator extends AbstractNodeIterator {

        PostorderIterator(RootNode root) {
            super(root);
        }

        protected final void moveInto(ConditionNode node) {
            //System.out.println("moveInto::" + node);
            curNode = node;

            Map children = node.children;
            int childCount = children.size();
            curChildren = children.keySet().iterator();

            if (childCount > 0) {
                String childKey = (String) curChildren.next();
                ConditionNode child =
                    (ConditionNode) children.get(childKey);
                keyStack.add(childKey);
                iterStack.add(curChildren);
                nodeStack.add(curNode);
                moveInto(child);
            }
            else if (curNode.type() == RootNode.TYPE) {
                moveUp();
            }
        }

        // Return from path terminus
        protected final void moveUp() {
            //System.out.println("moveUp::" + curNode);
            int stackSize = iterStack.size();
            if (stackSize > 0) {
                int topIndex = stackSize - 1;
                curChildren = (Iterator) iterStack.get(topIndex);
                curNode = (ConditionNode) nodeStack.get(topIndex);
                if (curChildren.hasNext()) {
                    String childKey = (String) curChildren.next();
                    ConditionNode child =
                        (ConditionNode) curNode.children.get(childKey);
                    keyStack.set(keyStack.size() - 1, childKey);
                    moveInto(child);
                    return;
                }
                keyStack.remove(topIndex);
                iterStack.remove(topIndex);
                nodeStack.remove(topIndex);
                stackSize--;

                if (curNode.type() != RootNode.TYPE) {
                    return;
                }
            }

            assert curNode.type() == RootNode.TYPE;
            assert keyStack.size() == 0;
            assert iterStack.size() == 0;
            assert nodeStack.size() == 0;

            curNode = null;
            keyStack = null;
            iterStack = null;
            nodeStack = null;
        }
    }

    private final static class PreorderIterator extends AbstractNodeIterator {

        PreorderIterator(RootNode root) {
            super(root);
        }

        protected final void moveInto(ConditionNode node) {
            //System.out.println("moveInto::" + node);
            curNode = node;

            Map children = node.children;
            curChildren = children.keySet().iterator();

            switch (curNode.type()) {
            case RootNode.TYPE:
                if (children.size() > 0) {
                    String childKey = (String) curChildren.next();
                    ConditionNode child =
                        (ConditionNode) children.get(childKey);
                    keyStack.add(childKey);
                    iterStack.add(curChildren);
                    nodeStack.add(curNode);
                    moveInto(child);
                }
                else {
                    moveUp();
                }
                break;
            case ImpliedNode.TYPE:
            case InNode.TYPE:
            case NotNode.TYPE:
                break;
            default:
                throw new AssertionError();
            }
        }

        // Return from path terminus
        protected final void moveUp() {
            //System.out.println("moveUp::" + curNode);
            int stackSize = iterStack.size();
            while (stackSize > 0) {
                int topIndex = stackSize - 1;
                curChildren = (Iterator) iterStack.get(topIndex);
                curNode = (ConditionNode) nodeStack.get(topIndex);
                if (curChildren.hasNext()) {
                    String childKey = (String) curChildren.next();
                    ConditionNode child =
                        (ConditionNode) curNode.children.get(childKey);
                    keyStack.set(keyStack.size() - 1, childKey);
                    //iterStack.add(curChildren);
                    //nodeStack.add(curNode);
                    moveInto(child);
                    return;
                }
                keyStack.remove(topIndex);
                iterStack.remove(topIndex);
                nodeStack.remove(topIndex);
                stackSize--;
            }

            //assert curNode.type() == RootNode.TYPE;
            assert keyStack.size() == 0;
            assert iterStack.size() == 0;
            assert nodeStack.size() == 0;

            curNode = null;
            keyStack = null;
            iterStack = null;
            nodeStack = null;
        }
    }

    private final static class LeafIterator extends AbstractNodeIterator {

        LeafIterator(RootNode root) {
            super(root);
        }

        // Only previously unvisited nodes
        protected void moveInto(ConditionNode node) {
            //System.out.println("moveInto::" + node);
            curNode = node;

            Map children = node.children;
            int childCount = children.size();
            curChildren = children.keySet().iterator();

            switch (curNode.type()) {
            case RootNode.TYPE:
            case ImpliedNode.TYPE:
                if (childCount > 0) {
                    String childKey = (String) curChildren.next();
                    ConditionNode child =
                        (ConditionNode) children.get(childKey);
                    keyStack.add(childKey);
                    iterStack.add(curChildren);
                    nodeStack.add(curNode);
                    moveInto(child);
                }
                else {
                    moveUp();
                }
                break;
            case InNode.TYPE:
            case NotNode.TYPE:
                break;
            default:
                throw new AssertionError();
            }
        }

        // Return from path terminus
        protected void moveUp() {
            //System.out.println("moveUp::" + curNode);
            int stackSize = iterStack.size();
            while (stackSize > 0) {
                int topIndex = stackSize - 1;
                curChildren = (Iterator) iterStack.get(topIndex);
                curNode = (ConditionNode) nodeStack.get(topIndex);
                if (curChildren.hasNext()) {
                    String childKey = (String) curChildren.next();
                    ConditionNode child =
                        (ConditionNode) curNode.children.get(childKey);
                    keyStack.set(keyStack.size() - 1, childKey);
                    //iterStack.add(curChildren);
                    //nodeStack.add(curNode);
                    moveInto(child);
                    return;
                }
                keyStack.remove(topIndex);
                iterStack.remove(topIndex);
                nodeStack.remove(topIndex);
                stackSize--;
            }

            //assert curNode.type() == RootNode.TYPE;
            assert keyStack.size() == 0;
            assert iterStack.size() == 0;
            assert nodeStack.size() == 0;

            curNode = null;
            keyStack = null;
            iterStack = null;
            nodeStack = null;
        }
    }

    /* Test driver code for condition tree. */
    public static void main(String[] argv) throws Exception {
        ConditionTree ct = new ConditionTree(true, 0);
        ct.addNode("x.y.z", new InNode(true, 2));
        ct.addNode("x.y.z.A", new NotNode(true, 3));
        ct.addNode("x.y.z.B", new NotNode(true, 0), true);
        ct.addNode("x.y.f", new NotNode(true, 0), true);
        ct.addNode("x.y.g", new NotNode(true, 0), true);
        //ct.addNode("x.y.z", new NotNode(true, 5));
        //ct.addNode("x.y.z", new NotNode(true, 0), true);
        ct.addNode("x.y.z.A.i", new InNode(true, 0), true);
        ct.addNode("x.y.z.A.j", new InNode(true, 0), true);
        //ct.addNode("x.y.z.A", new InNode(true, 0), true);
        //ct.addNode("x.y.g", new InNode(true, 0), true);
        ct.addNode("x.y.f", new InNode(true, 0), true);
        ct.addNode("x.y.z.A.i.alpha", new InNode(true, 0), true);
        ct.addNode("x.y.z.A.i.alpha.beta", new InNode(true, 0), true);
        System.out.println(ct.toString());

        System.out.println();
        System.out.println("Leaf iterator:");
        NodeIterator iter = ct.leafIterator();
        while (iter.hasNext()) {
            iter.advance();
            System.out.println("[ " + iter.keyPrefix() + ", " + iter.key() +
                ", " + iter.value() + ", " + iter.depth() + ", " +
                iter.parent() + " ]");
        }
        System.out.println();

        System.out.println("Postorder iterator:");
        iter = ct.postorderIterator();
        while (iter.hasNext()) {
            iter.advance();
            System.out.println("[ " + iter.keyPrefix() + ", " + iter.key() +
                ", " + iter.value() + ", " + iter.depth() + ", " +
                iter.parent() + " ]");
        }
        System.out.println();

        System.out.println("Preorder iterator:");
        iter = ct.preorderIterator();
        while (iter.hasNext()) {
            iter.advance();
            System.out.println("[ " + iter.keyPrefix() + ", " + iter.key() +
                ", " + iter.value() + ", " + iter.depth() + ", " +
                iter.parent() + " ]");
        }
        System.out.println();

        List<Object> keys = new ArrayList<Object>();
        List<Object> nodes = new ArrayList<Object>();
        keys.add("x.y.z.A.i.alpha");
        nodes.add(new NotNode(false, 0));
        ct.merge(null, keys, nodes, true);
        System.out.println(ct.toString());

        keys.add("x.y.z.A.i.alpha");
        ((ConditionNode) nodes.get(0)).rank = 2;
        ct.merge(new RootNode(false, 6), keys, nodes, true);
        System.out.println(ct.toString());

        iter = ct.postorderIterator();
        while (iter.hasNext()) {
            iter.advance();
            System.out.println("[ " + iter.keyPrefix() + ", " + iter.key() +
                ", " + iter.value() + ", " + iter.depth() + ", " +
                iter.parent() + " ]");
        }
        System.out.println();
    }
}
