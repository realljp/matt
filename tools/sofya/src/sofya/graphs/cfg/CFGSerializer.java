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

import java.io.*;
import java.util.List;
import java.util.Iterator;

import sofya.base.SConstants.*;
import sofya.base.CacheHandle;
import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProjectDescription;
import sofya.base.exceptions.CacheException;
import sofya.graphs.GraphSerializer;
import sofya.graphs.Edge;
import sofya.graphs.Node;
import sofya.graphs.cfg.CFEdge.BranchID;

import org.apache.bcel.generic.Type;

/**
 * <p>Implements a strategy for serializing control flow graphs
 * (see {@link CFG}) to binary flat files on disk.</p>
 *
 * @author Alex Kinneer
 * @version 09/06/2006
 */
class CFGSerializer implements GraphSerializer<CFG> {
    /** Handle to the cache directory in the database. */
    private CacheHandle cHandle = Handler.newCache(true);
    // Deleted on exit
    
    CFGSerializer() {
    }
    
    /**
     * Reads a CFG from a cache file.
     *
     * @param method Signature of the method for which to retrieve the
     * CFG from the cache file.
     *
     * @return The CFG for the specified method.
     *
     * @throws CacheException If reading of the cache file fails, which
     * may occur if no cache file exists for the requested method or
     * if the cache file is unreadable or corrupted in some way.
     */
    public CFG readFromDisk(MethodSignature method)
                    throws CacheException {
        DataInputStream in = null;
        CFG cfg = new CFG();
        List<Node> nodes = cfg.blockList();
        List<Edge> edges = cfg.edgeList();
    
        // The name of the cache file is simply the URL encoded UTF-8 encoding
        // of the method signature. Is this completely portable? Probably not,
        // but it seems to work fine on Solaris, Linux, and Windows, which
        // is pretty much all we care about anyway.
        try {
            in = new DataInputStream(new BufferedInputStream(
                Handler.openCacheFile(cHandle, java.net.URLEncoder.encode(
                method.toString(), "UTF-8"))));
        }
        catch (IOException e) {
            throw new CacheException("Method not found in cache", e);
        }
        
        cfg.methodSignature = method;
        try {
            cfg.displayString = in.readUTF();
            int nodeCount = in.readInt();
            boolean branchExtensions = (in.readByte() == 1);
            
            if (branchExtensions) {
                cfg.setNumberOfBranches(in.readInt());
                cfg.setSummaryBranchID(in.readInt());
            }
            
            for (int i = 0; i < nodeCount; i++) {
                Block block = new Block();
                block.setID(in.readInt());
                block.setLabel(BlockLabel.fromChar(in.readChar()));
                block.setType(BlockType.fromInt(in.readInt()));
                block.setSubType(BlockSubType.fromInt(in.readInt()));
                block.setStartOffset(in.readInt());
                block.setEndOffset(in.readInt());
                nodes.add(block);
            }
            
            for (int i = 0; i < nodeCount; i++) {
                Block block = (Block) nodes.get(i);
                List<Node> successors = block.successorList();
                int nCount = in.readInt();  // Successor count
                for (int j = 0; j < nCount; j++) {
                    successors.add(nodes.get(in.readInt() - 1));
                }
                List<Node> predecessors = block.predecessorList();
                nCount = in.readInt();  // Predecessor count
                for (int j = 0; j < nCount; j++) {
                    predecessors.add(nodes.get(in.readInt() - 1));
                }
            }
            
            int edgeCount = in.readInt();
            for (int i = 0; i < edgeCount; i++) {
                CFEdge e = new CFEdge();
                e.setID(in.readInt());
                e.setPredNodeID(in.readInt());
                e.setSuccNodeID(in.readInt());
                e.setSpecialNodeID(in.readInt());
                if (branchExtensions) {
                    int branchCount = in.readInt();
                    for (int j = 0; j < branchCount; j++) {
                        int bid = in.readInt();
                        BranchType type = BranchType.fromInt(in.readInt());
                        e.addBranchID(bid, type);
                    }
                }
                if (in.readByte() == 1) {
                    e.setLabel(in.readUTF());
                }
                if (in.readByte() == 1) {
                    e.auxLabel = in.readUTF();
                }
                switch (in.readByte()) {
                case 0:
                    break;  // No type signature
                case 1:
                    e.labelType = Type.getType(in.readUTF());
                    break;
                case 2:
                    e.labelType = Type.UNKNOWN;
                    break;
                default:
                    throw new CacheException("Invalid cache file: edge type " +
                        "code unknown");
                }
                edges.add(e);
            }
        }
        catch (IOException e) {
            throw new CacheException("Error reading cache file", e);
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) {
                throw new CacheException("Error closing cache file", e);
            }
        }
        
        return cfg;
    }
    
    /**
     * Writes a CFG to a cache file.
     *
     * @param method Signature of the method being written to file.
     * @param g CFG being written to the cache file.
     *
     * @throws CacheException If the cache file cannot be created or
     * written.
     */
    public void writeToDisk(MethodSignature method, CFG g)
            throws CacheException {
        DataOutputStream out = null;
        CFG cfg = null;
        
        try {
            cfg = (CFG) g;
        }
        catch (ClassCastException e) {
            throw new CacheException("Graph is not a CFG", e);
        }
        
        List nodes = cfg.blockList();
        List edges = cfg.edgeList();
        int nodeCount = cfg.getNumberOfNodes();
        
        try {
            out = new DataOutputStream(new BufferedOutputStream(
                Handler.createCacheFile(cHandle, java.net.URLEncoder.encode(
                method.toString(), "UTF-8"), true)));
        }
        catch (IOException e) {
            throw new CacheException("Could not create cache file", e);
        }
        
        try {
            out.writeUTF(cfg.displayString);
            out.writeInt(nodeCount);
            if (ProjectDescription.ENABLE_BRANCH_EXTENSIONS) {
                out.writeByte(1);
                out.writeInt(cfg.getNumberOfBranches());
                out.writeInt(cfg.getSummaryBranchID());
            }
            else {
                out.writeByte(0);
            }
            
            // Write node data
            for (int i = 0; i < nodeCount; i++) {
                Block block = (Block) nodes.get(i);
                out.writeInt(block.getID());
                out.writeChar(block.getLabel().toChar());
                out.writeInt(block.getType().toInt());
                out.writeInt(block.getSubType().toInt());
                out.writeInt(block.getStartOffset());
                out.writeInt(block.getEndOffset());
            }
            
            // Predecessors and successors are stored as direct node
            // references. To be able to set those references correctly
            // on the read side, the node objects must already exist.
            // Therefore we write the predecessor/successor information
            // at a point in the stream at which the node objects should
            // have already been created.
            for (int i = 0; i < nodeCount; i++) {
                Block block = (Block) nodes.get(i);
                List successors = block.successorList();
                out.writeInt(successors.size());
                for (int j = 0; j < successors.size(); j++) {
                    out.writeInt(((Block) successors.get(j)).getID());
                }
                List predecessors = block.predecessorList();
                out.writeInt(predecessors.size());
                for (int j = 0; j < predecessors.size(); j++) {
                    out.writeInt(((Block) predecessors.get(j)).getID());
                }
            }
            
            // Write edge data
            out.writeInt(edges.size());
            for (Iterator i = edges.iterator(); i.hasNext(); ) {
                CFEdge e = (CFEdge) i.next();
                out.writeInt(e.getID());
                out.writeInt(e.getPredNodeID());
                out.writeInt(e.getSuccNodeID());
                out.writeInt(e.getSpecialNodeID());
                if (ProjectDescription.ENABLE_BRANCH_EXTENSIONS) {
                    out.writeInt(e.branchIDs.size());
                    Iterator j = e.branchIDs.iterator();
                    while (j.hasNext()) {
                        BranchID bid = (BranchID) j.next();
                        out.writeInt(bid.getID());
                        out.writeInt(bid.getType().toInt());
                    }
                }
                if (e.getLabel() == null) {
                    out.writeByte(0);
                }
                else {
                    out.writeByte(1);
                    out.writeUTF(e.getLabel());
                }
                if (e.auxLabel == null) {
                    out.writeByte(0);
                }
                else {
                    out.writeByte(1);
                    out.writeUTF(e.getAuxLabel());
                }
                if (e.labelType == null) {
                    out.writeByte(0);
                }
                else {
                    if (e.labelType.equals(Type.UNKNOWN)) {
                        out.writeByte(2);
                    }
                    else {
                        out.writeByte(1);
                        out.writeUTF(e.labelType.getSignature());
                    }
                }
            }
        }
        catch (IOException e) {
            throw new CacheException("Error writing cache file", e);
        }
        finally {
            try {
                out.close();
            }
            catch (IOException e) {
                throw new CacheException("Error closing cache file", e);
            }
        }
    }
    
    /** Simple test driver. */
    public static void main(String[] argv) {
        MethodSignature ms = new MethodSignature("AClass", "AMethod",
            Type.VOID, new Type[]{Type.INT, Type.INT});
        CFGSerializer testCache = new CFGSerializer();
        testCache.writeToDisk(ms, new CFG());
    }
}
