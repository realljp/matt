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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import sofya.base.MethodSignature;
import sofya.base.Handler;
import sofya.ed.semantic.ConditionTree.*;

import gnu.trove.THashSet;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

/**
 *
 *
 * @author Alex Kinneer
 * @version 09/28/2006
 */
final class ProbeLocationTree {
    private final InternalNode root = new InternalNode();

    private static final boolean ASSERTS = true;

    ProbeLocationTree() {
    }

    void add(ProbeRecord probe) {
        MethodSignature location = probe.location;
        String className = location.getClassName();
        String methodName = location.getMethodName();
        String typeSig = location.getTypeSignature();
        InternalNode curNode = root;

        StringTokenizer stok = new StringTokenizer(className, ".");
        while (stok.hasMoreTokens()) {
            String nodeKey = stok.nextToken();

            InternalNode child = (InternalNode) curNode.children.get(nodeKey);
            if (child == null) {
                child = new InternalNode();
                curNode.children.put(nodeKey, child);
            }
            curNode = child;
        }

        InternalNode child = (InternalNode) curNode.children.get(methodName);
        if (child == null) {
            child = new InternalNode();
            curNode.children.put(methodName, child);
        }
        curNode = child;

        LeafNode locNode = (LeafNode) curNode.children.get(typeSig);
        if (locNode == null) {
            locNode = new LeafNode();
            curNode.children.put(typeSig, locNode);
        }
        if (ASSERTS) assert !locNode.probes.containsKey(probe.id);
        locNode.probes.put(probe.id, probe);
    }

    TIntObjectHashMap get(MethodSignature location) {
        LeafNode locNode = seekNode(location);
        if (locNode == null) {
            return null;
        }
        return locNode.probes;
    }

    boolean remove(MethodSignature location, int probeId) {
        LeafNode locNode = seekNode(location);
        if (locNode == null) {
            return false;
        }
        return (locNode.probes.remove(probeId) != null);
    }

    // Returns true if change count successfully decremented. When
    // decrement results in change count of zero, probe is removed.
    short decrementAndRemove(MethodSignature location, int probeId) {
        LeafNode locNode = seekNode(location);
        if (locNode == null) {
            return -1;
        }

        ProbeRecord probe = (ProbeRecord) locNode.probes.get(probeId);
        if (probe == null) {
            return -1;
        }

        probe.changeCount -= 1;

        if (probe.changeCount == 0) {
            boolean removed = (locNode.probes.remove(probeId) != null);
            if (ASSERTS) assert removed;
        }

        return probe.changeCount;
    }

    ProbeIterator iterator() {
        return new AllProbeIterator();
    }

    ProbeIterator filteredIterator(ConditionTree ct) {
        return new FilteredProbeIterator(ct);
    }

    private final LeafNode seekNode(MethodSignature location) {
        String className = location.getClassName();
        String methodName = location.getMethodName();
        String typeSig = location.getTypeSignature();
        InternalNode curNode = root;

        StringTokenizer stok = new StringTokenizer(className, ".");
        while (stok.hasMoreTokens()) {
            String nodeKey = stok.nextToken();

            InternalNode child = (InternalNode) curNode.children.get(nodeKey);
            if (child == null) {
                return null;
            }
            curNode = child;
        }

        InternalNode child = (InternalNode) curNode.children.get(methodName);
        if (child == null) {
            return null;
        }
        curNode = child;

        return (LeafNode) curNode.children.get(typeSig);
    }

    private final Node seekNode(String key) {
        Node curNode = root;

        StringTokenizer stok = new StringTokenizer(key, ".");
        while (stok.hasMoreTokens()) {
            String nodeKey = stok.nextToken();

            switch (curNode.type) {
            case InternalNode.TYPE:
                InternalNode iNode = (InternalNode) curNode;
                Node child = (Node) iNode.children.get(nodeKey);
                if (child == null) {
                    // Requested a location that's not in the tree
                    // (no matching path)
                    return null;
                }
                curNode = child;
                break;
            case LeafNode.TYPE:
                // Requested a location that's not in the tree
                // (exceeded the depth on the prefix)
                return null;
            default:
                throw new AssertionError();
            }
        }
        return curNode;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        root.toString("root", sb, 0);
        return sb.toString();
    }

    public void serialize(DataOutputStream out) throws IOException {
        root.serialize(out);
    }

    public static ProbeLocationTree deserialize(DataInputStream in)
            throws IOException {
        byte type = in.readByte();
        if (ASSERTS) assert type == InternalNode.TYPE;
        ProbeLocationTree newTree = new ProbeLocationTree();
        newTree.root.deserialize(in);
        return newTree;
    }

    private static final void serializeProbeMap(TIntObjectHashMap probes,
            DataOutputStream out) throws IOException {
        int size = probes.size();
        out.writeInt(size);
        TIntObjectIterator iterator = probes.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            ProbeRecord probe = (ProbeRecord) iterator.value();
            out.writeInt(probe.id);

            MethodSignature location = probe.location;
            out.writeUTF(location.getClassName());
            out.writeUTF(location.getMethodName());
            out.writeUTF(location.getTypeSignature());

            Set liveKeys = probe.liveKeys;
            int keyCnt = liveKeys.size();
            out.writeInt(keyCnt);
            Iterator keyStrs = liveKeys.iterator();
            for (int j = keyCnt; j-- > 0; ) {
                out.writeUTF((String) keyStrs.next());
            }

            out.writeShort(probe.changeCount);
        }
    }

    @SuppressWarnings("unchecked")
    private static final void deserializeProbeMap(TIntObjectHashMap probes,
            DataInputStream in) throws IOException {
        int size = in.readInt();
        for (int i = size; i-- > 0; ) {
            int id = in.readInt();
            MethodSignature location = new MethodSignature(in.readUTF(),
                in.readUTF(), in.readUTF());

            Set<String> liveKeys = new THashSet();
            int keyCnt = in.readInt();
            for (int j = keyCnt; j-- > 0; ) {
                liveKeys.add(in.readUTF());
            }

            short changeCount = in.readShort();

            probes.put(id,
                new ProbeRecord(id, location, liveKeys, changeCount));
        }
    }

    private static abstract class Node {
        public final byte type;

        private Node() {
            throw new AssertionError("Illegal constructor");
        }

        Node(byte type) {
            this.type = type;
        }

        public abstract void toString(String key, StringBuilder sb, int depth);
        abstract void serialize(DataOutputStream out)
                throws IOException;
        abstract void deserialize(DataInputStream in)
                throws IOException;
    }

    @SuppressWarnings("unchecked")
    private static final class InternalNode extends Node {
        public static final byte TYPE = 1;
        final Map<Object, Object> children = new THashMap();

        InternalNode() {
            super(TYPE);
        }

        public void toString(String key, StringBuilder sb, int depth) {
            for (int i = depth; i-- > 0; ) {
                sb.append("  ");
            }
            sb.append(key);
            sb.append(":");
            sb.append(Handler.LINE_SEP);

            int count = children.size();
            Iterator iterator = children.keySet().iterator();
            for (int i = count; i-- > 0; ) {
                String childKey = (String) iterator.next();
                Node child = (Node) children.get(childKey);
                child.toString(childKey, sb, depth + 1);
            }
        }

        void serialize(DataOutputStream out) throws IOException {
            out.writeByte(TYPE);
            int childCnt = children.size();
            out.writeInt(childCnt);
            Iterator childKeys = children.keySet().iterator();
            for (int i = childCnt; i-- > 0; ) {
                String key = (String) childKeys.next();
                out.writeUTF(key);
                Node child = (Node) children.get(key);
                child.serialize(out);
            }
        }

        void deserialize(DataInputStream in) throws IOException {
            int childCnt = in.readInt();
            for (int i = childCnt; i-- > 0; ) {
                String childKey = in.readUTF();

                byte nodeType = in.readByte();
                Node childNode;
                switch (nodeType) {
                case InternalNode.TYPE:
                    childNode = new InternalNode();
                    break;
                case LeafNode.TYPE:
                    childNode = new LeafNode();
                    break;
                default:
                    throw new AssertionError();
                }

                childNode.deserialize(in);
                children.put(childKey, childNode);
            }
        }
    }

    private static final class LeafNode extends Node {
        public static final byte TYPE = 2;
        final TIntObjectHashMap probes = new TIntObjectHashMap();

        LeafNode() {
            super(TYPE);
        }

        public void toString(String key, StringBuilder sb, int depth) {
            for (int i = depth; i-- > 0; ) {
                sb.append("  ");
            }
            sb.append(key);
            sb.append(": ");
            sb.append(probes.size());
            sb.append(Handler.LINE_SEP);
        }

        void serialize(DataOutputStream out) throws IOException {
            out.writeByte(TYPE);
            serializeProbeMap(probes, out);
        }

        void deserialize(DataInputStream in) throws IOException {
            deserializeProbeMap(probes, in);
        }
    }

    public static interface ProbeIterator {
        boolean hasNext();

        ProbeRecord next();

        void remove();
    }

    private abstract class AbstractProbeIterator implements ProbeIterator {
        protected LeafNode curNode;
        protected LeafNode iterNode;
        protected TIntObjectIterator leafIter;
        protected int curId;

        protected List<Object> iterStack = new ArrayList<Object>(5);
        protected List<Object> nodeStack = new ArrayList<Object>(5);

        protected AbstractProbeIterator() {
        }

        protected AbstractProbeIterator(Node start) {
        }

        protected abstract void moveUp();

        protected final void moveToNext() {
            if (!leafIter.hasNext()) {
                moveUp();
            }
            else {
                leafIter.advance();
            }
        }

        public boolean hasNext() {
            return (leafIter != null);
        }

        public ProbeRecord next() {
            //System.out.println("next");
            if (leafIter == null) {
                throw new NoSuchElementException();
            }

            ProbeRecord probe = (ProbeRecord) leafIter.value();
            iterNode = curNode;
            curId = probe.id;
            moveToNext();
            return probe;
        }

        public void remove() {
            //System.out.println("remove");
            if (iterNode == null) {
                throw new IllegalStateException();
            }
            else {
                iterNode.probes.remove(curId);
                iterNode = null;
            }
        }
    }

    private final class AllProbeIterator extends AbstractProbeIterator {

        AllProbeIterator() {
            moveInto(root);
        }

        AllProbeIterator(Node start) {
            moveInto(start);
        }

        // Only previously unvisited nodes
        private final void moveInto(Node node) {
            //System.out.println("moveInto::" + node);

            switch (node.type) {
            case InternalNode.TYPE: {
                InternalNode iNode = (InternalNode) node;
                Map children = iNode.children;
                int childCount = children.size();
                Iterator curChildren = children.keySet().iterator();
                if (childCount > 0) {
                    // Add filter check here {
                    Node child = (Node) children.get(curChildren.next());
                    iterStack.add(curChildren);
                    nodeStack.add(node);
                    moveInto(child);
                    // }
                }
                else {
                    moveUp();
                }
                break;
              }
            case LeafNode.TYPE: {
                curNode = (LeafNode) node;
                leafIter = curNode.probes.iterator();
                moveToNext();
                break;
              }
            default:
                throw new AssertionError();
            }
        }

        // Return from path terminus
        protected final void moveUp() {
            //System.out.println("moveUp");
            int stackSize = iterStack.size();
            while (stackSize > 0) {
                int topIndex = stackSize - 1;
                Iterator curChildren = (Iterator) iterStack.get(topIndex);
                InternalNode iNode = (InternalNode) nodeStack.get(topIndex);
                if (curChildren.hasNext()) {
                    String childKey = (String) curChildren.next();
                    Node child = (Node) iNode.children.get(childKey);
                    moveInto(child);
                    return;
                }
                iterStack.remove(topIndex);
                nodeStack.remove(topIndex);
                stackSize--;
            }

            assert iterStack.size() == 0;
            assert nodeStack.size() == 0;

            curNode = null;
            leafIter = null;
            iterStack = null;
            nodeStack = null;
        }
    }

    @SuppressWarnings("unchecked")
    private final class FilteredProbeIterator extends AbstractProbeIterator {
        private Iterator seekIterator;
        private Set<Object> skipSet = new THashSet();

        private List<Object> keyStack = new ArrayList<Object>(5);

        private final StringBuilder keyBuilder = new StringBuilder();

        FilteredProbeIterator() {
            throw new AssertionError("Illegal constructor");
        }

        FilteredProbeIterator(ConditionTree condTree) {
            initialize(root, condTree);
        }

        private final void initialize(Node root, ConditionTree condTree) {
            final List<Object> seekList = new ArrayList<Object>(5);
            final ConditionNode initMaxChild = new ImpliedNode(true, -1);
            ConditionNode maxChild = initMaxChild;
            int curDepth = 0;

            final NodeIterator iterator = condTree.postorderIterator();
            while (iterator.hasNext()) {
                iterator.advance();
                ConditionNode condNode = iterator.value();
                int depth = iterator.depth();

                //System.out.println("condNode=" + condNode);
                if (depth < curDepth) {
                    curDepth = depth;

                    int stackSize = nodeStack.size();

                    switch (condNode.type()) {
                    case ImpliedNode.TYPE:
                        // There should never be a tree containing an
                        // implied node without a concrete node
                        if (ASSERTS) assert maxChild != null;

                        //System.out.println("[imp]maxChild=" + maxChild);

                        switch (maxChild.type()) {
                        case InNode.TYPE:
                            skipSet.add(iterator.fullKey());
                            break;
                        case NotNode.TYPE:
                            seekList.add(iterator.fullKey());
                            break;
                        default:
                            throw new AssertionError();
                        }

                        //System.out.println("nodeStack=" + nodeStack);
                        if (stackSize > 0) {
                            maxChild = (ConditionNode) nodeStack.remove(
                                nodeStack.size() - 1);
                        }

                        break;
                    case InNode.TYPE:
                        seekList.add(iterator.fullKey());
                        //System.out.println("nodeStack=" + nodeStack);
                        if (stackSize > 0) {
                            maxChild = (ConditionNode) nodeStack.remove(
                                nodeStack.size() - 1);
                        }
                        else {
                            maxChild = condNode;
                        }
                        //System.out.println("[in]maxChild=" + maxChild);
                        if (ASSERTS) assert condNode.rank >= maxChild.rank;
                        break;
                    case NotNode.TYPE:
                        skipSet.add(iterator.fullKey());
                        //System.out.println("nodeStack=" + nodeStack);
                        if (stackSize > 0) {
                            maxChild = (ConditionNode) nodeStack.remove(
                                nodeStack.size() - 1);
                        }
                        else {
                            maxChild = condNode;
                        }
                        //System.out.println("[not]maxChild=" + maxChild);
                        if (ASSERTS) assert condNode.rank >= maxChild.rank;
                        break;
                    default:
                        // Iterator is not supposed to return root
                        // (or any other type)
                        throw new AssertionError();
                    }

                }
                else {
                    if (depth > curDepth) {
                        if (maxChild != initMaxChild) {
                            nodeStack.add(maxChild);
                        }
                        maxChild = condNode;
                    }
                    else {
                        if (condNode.rank > maxChild.rank) {
                            maxChild = condNode;
                        }
                    }
                    //System.out.println("[1]maxChild=" + maxChild);

                    curDepth = depth;

                    switch (condNode.type()) {
                    case InNode.TYPE:
                        seekList.add(iterator.fullKey());
                        break;
                    case NotNode.TYPE:
                        skipSet.add(iterator.fullKey());
                        break;
                    default:
                        throw new AssertionError();
                    }
                }
            }

            if (ASSERTS) assert nodeStack.size() == 0;

            //System.out.println("seekList:");
            //System.out.println(seekList);
            //System.out.println();
            //System.out.println("skipSet:");
            //Iterator sIt = skipSet.iterator();
            //System.out.print("[ ");
            //while (sIt.hasNext()) {
            //    System.out.print(sIt.next() + " ");
            //}
            //System.out.println("]");
            //System.out.println();

            int seekCount = seekList.size();
            seekIterator = seekList.iterator();
            for (int i = seekCount; i-- > 0; ) {
                String seekKey = (String) seekIterator.next();
                //System.out.println("seekKey=" + seekKey);

                Node startNode = seekNode(seekKey);
                if (startNode != null) {
                    skipSet.add(seekKey);
                    moveInto(seekKey, startNode);
                    break;
                }
            }
        }

        private final void moveInto(String key, Node node) {
            //System.out.println("moveInto::" + node);
            //System.out.println("  " + keyStack);
            switch (node.type) {
            case InternalNode.TYPE: {
                InternalNode iNode = (InternalNode) node;
                Map children = iNode.children;
                int childCount = children.size();
                Iterator curChildren = children.keySet().iterator();
                if (childCount > 0) {
                    for (int i = childCount; i-- > 0; ) {
                        String childKey = (String) curChildren.next();

                        keyBuilder.append(key);
                        keyBuilder.append(".");
                        keyBuilder.append(childKey);
                        String fullChildKey = keyBuilder.toString();
                        keyBuilder.setLength(0);

                        if (!skipSet.contains(fullChildKey)) {
                            Node child = (Node) children.get(childKey);
                            iterStack.add(curChildren);
                            nodeStack.add(node);
                            keyStack.add(key);
                            moveInto(fullChildKey, child);
                            return;
                        }
                    }
                    moveUp();
                }
                else {
                    moveUp();
                }
                break;
              }
            case LeafNode.TYPE: {
                curNode = (LeafNode) node;
                leafIter = curNode.probes.iterator();
                moveToNext();
                break;
              }
            default:
                throw new AssertionError();
            }
        }

        protected final void moveUp() {
            //System.out.println("moveUp");
            int stackSize = iterStack.size();

            while (stackSize > 0) {
                int topIndex = stackSize - 1;
                Iterator curChildren = (Iterator) iterStack.get(topIndex);
                InternalNode iNode = (InternalNode) nodeStack.get(topIndex);
                String key = (String) keyStack.get(topIndex);
                while (curChildren.hasNext()) {
                    String childKey = (String) curChildren.next();

                    keyBuilder.append(key);
                    keyBuilder.append(".");
                    keyBuilder.append(childKey);
                    String fullChildKey = keyBuilder.toString();
                    keyBuilder.setLength(0);

                    if (!skipSet.contains(fullChildKey)) {
                        Node child = (Node) iNode.children.get(childKey);
                        moveInto(fullChildKey, child);
                        return;
                    }
                }
                iterStack.remove(topIndex);
                nodeStack.remove(topIndex);
                keyStack.remove(topIndex);
                stackSize--;
            }

            while (seekIterator.hasNext()) {
                String seekKey = (String) seekIterator.next();
                //System.out.println("seekKey=" + seekKey);

                Node startNode = seekNode(seekKey);
                if (startNode != null) {
                    skipSet.add(seekKey);
                    moveInto(seekKey, startNode);
                    return;
                }
            }

            assert iterStack.size() == 0;
            assert nodeStack.size() == 0;
            assert keyStack.size() == 0;

            seekIterator = null;
            skipSet = null;
            curNode = null;
            leafIter = null;
            iterStack = null;
            nodeStack = null;
            keyStack = null;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] argv) throws Exception {
        ProbeLocationTree tree = new ProbeLocationTree();

        ProbeIterator pIter = tree.iterator();
        while (pIter.hasNext()) {
            System.out.println(pIter.next());
        }

        tree.add(new ProbeRecord(1, new MethodSignature(
            "java.util.ArrayList", "add", "(Ljava/lang/Object;)V"),
            new THashSet()));
        tree.add(new ProbeRecord(2, new MethodSignature(
            "java.util.ArrayList", "add", "(Ljava/lang/Object;)Z"),
            new THashSet()));
        tree.add(new ProbeRecord(3, new MethodSignature(
            "java.util.LinkedList", "add", "(Ljava/lang/Object;)V"),
            new THashSet()));
        tree.add(new ProbeRecord(4, new MethodSignature(
            "java.lang.StringBuilder", "append", "(I)V"),
            new THashSet()));
        tree.add(new ProbeRecord(1, new MethodSignature(
            "java.util.ArrayList", "remove", "(Ljava/lang/Object;)Z"),
            new THashSet()));
        tree.add(new ProbeRecord(5, new MethodSignature(
            "java.util.HashSet", "retainAll", "(L/java/util/Collection;)V"),
            new THashSet()));
        //assert tree.remove(new MethodSignature(
        //    "java.util.ArrayList", "add", "(Ljava/lang/Object;)Z"), 2);
        pIter = tree.iterator();
        while (pIter.hasNext()) {
            ProbeRecord probe = (ProbeRecord) pIter.next();
            System.out.println(probe);
            if (probe.id == 3) {
                //pIter.remove();
            }
        }
        System.out.println(tree.toString());

        DataOutputStream fout = new DataOutputStream(
            new FileOutputStream("testfile.dat"));
        try {
            tree.serialize(fout);
        }
        finally {
            fout.close();
        }

        DataInputStream fin = new DataInputStream(
            new FileInputStream("testfile.dat"));
        try {
            tree = ProbeLocationTree.deserialize(fin);
        }
        finally {
            fin.close();
        }
        System.out.println(tree.toString());

        pIter = tree.iterator();
        while (pIter.hasNext()) {
            System.out.println(pIter.next());
        }

        System.out.println();
        ConditionTree ct = new ConditionTree(true, 0);
        ct.addNode("a.b.d.f", new InNode(true, 1));
        ct.addNode("a.b.d.g", new NotNode(true, 2));
        ct.addNode("a.b.e", new InNode(true, 3));
        ct.addNode("a.c", new NotNode(true, 4));
        System.out.println(ct.toString());

        pIter = tree.filteredIterator(ct);
        while (pIter.hasNext()) {
            ProbeRecord probe = (ProbeRecord) pIter.next();
            System.out.println(probe);
        }

        System.out.println();
        ct = new ConditionTree(true, 0);
        ct.addNode("java.util.LinkedList", new InNode(true, 1));
        ct.addNode("java.util.ArrayList", new NotNode(true, 2));
        //ct.addNode("java.util.ArrayList.add", new InNode(true, 3));
        System.out.println(ct.toString());

        pIter = tree.filteredIterator(ct);
        //System.out.println(fIt.next());
        while (pIter.hasNext()) {
            ProbeRecord probe = (ProbeRecord) pIter.next();
            System.out.println(probe);
        }
    }
}

