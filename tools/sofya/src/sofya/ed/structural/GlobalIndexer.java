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

package sofya.ed.structural;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.IOException;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectIntIterator;

/**
 * The global indexer maintains mappings between blocks (branches) and
 * a globally unique index (rather than the usual per-method index).
 * This is used to support trace hashing.
 *
 * @see sofya.ed.structural.HashingProbe
 * @see sofya.ed.structural.StructuralInstrumentor
 *
 * @author Alex Kinneer
 * @version 02/28/2007
 */
final class GlobalIndexer {
    /** Maps global indexes to method/local indexes. */
    private final TIntObjectHashMap globalToLocal = new TIntObjectHashMap();
    /** Maps method/local indexes to global indexes. */
    private final TObjectIntHashMap localToGlobal = new TObjectIntHashMap();
    /** Counter to assign new global indexes. */
    private int nextGlobalIndex = 1;
    
    /** For constructing the method/local index keys. */
    private final StringBuilder keyBuilder = new StringBuilder();
    
    /**
     * Create a new indexer.
     */
    GlobalIndexer() {
    }
    
    /**
     * Get the global index for a particular block (branch).
     * 
     * @param methodSig Signature of the method containing the block (branch).
     * @param localIndex Local index of the block (branch).
     * 
     * @return The global index for the specified block (branch), which
     * may be newly assigned if it has not been encountered before.
     */
    int indexFor(String methodSig, int localIndex) {
        String key = keyBuilder.append(methodSig).append(":")
            .append(localIndex).toString();
        keyBuilder.setLength(0);
        
        if (localToGlobal.containsKey(key)) {
            //System.out.println("existing index for: " + key);
            return localToGlobal.get(key);
        }
        else {
            int newIndex = nextGlobalIndex++;
            //System.out.println("new index " + newIndex + " for: " + key);
            localToGlobal.put(key, newIndex);
            globalToLocal.put(newIndex, key);
            return newIndex;
        }
    }
    
    /**
     * Generates a file recording the index mappings. This is for
     * debugging/informational purposes only.
     * 
     * @param name Name of the mapping file to generate.
     * @param indexOnGlobal If <code>true</code>, generate the mapping
     * from global indexes to method/local indexes, if <code>false</code>,
     * generate the mapping from method/local indexes to global indexes.
     * 
     * @throws IOException On any error that prevents the file from being
     * created or written.
     */
    void writeMappingFile(String name, boolean indexOnGlobal)
            throws IOException {
        FileWriter fw = new FileWriter(name);
        try {
            PrintWriter outFile = new PrintWriter(new BufferedWriter(fw));
            
            if (indexOnGlobal) {
                TIntObjectIterator iter = globalToLocal.iterator();
                int size = globalToLocal.size();
                for (int i = size; i-- > 0; ) {
                    iter.advance();
                    outFile.println(iter.key() + " -> " + iter.value());
                }
            }
            else {
                // This could definitely be improved by a pre-processing
                // pass to group methods together
                TObjectIntIterator iter = localToGlobal.iterator();
                int size = localToGlobal.size();
                for (int i = size; i-- > 0; ) {
                    iter.advance();
                    outFile.println(iter.key() + " -> " + iter.value());
                }
            }
            
            outFile.flush();
        }
        finally {
            try {
                fw.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("Exception closing mapping file");
            }
        }
    }
}
