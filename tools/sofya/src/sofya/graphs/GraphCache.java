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

package sofya.graphs;

import java.util.*;

import sofya.base.MethodSignature;
import sofya.base.exceptions.CacheException;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

import org.apache.commons.collections.map.LRUMap;

/**
 * A disk backed soft memory cache for graphs produced by the Sofya system.
 *
 * <p>A least recently used (LRU) policy is used to move graphs from memory
 * to disk files when a minimum free memory threshold is crossed. It is
 * the responsibility of a provided serializer to implement the methods
 * responsible for actually serializing graphs of a particular type
 * to file.</p>
 * 
 * @param <T> The type of graph to be managed by this cache; the
 * serializer is required to be consistent with this type.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
@SuppressWarnings("unchecked")
public final class GraphCache<T extends Graph> {
    /** Default percentage of memory available to the virtual machine which the
        cache must leave free. */
    private static final float LOW_MEMORY_THRESHOLD = 0.08f; // (8%)
    /** In-memory cache of graphs. */
    private CacheMap cache = new CacheMap();
    /** Map which records the status of every graph in cache, most notably
        whether the disk is currently cached to persistent storage. This data
        structure is double-keyed -- first on class, then on signature --
        primarily for the benefit of handlers. */
    private Map<Object, Map<MethodSignature,Object>> statusCache =
        new THashMap();
    /** Number of graphs currently in the cache, including those cached to
        disk. */
    private int size = 0;
    /** The serializer used to store and load graphs from
        persistent storage. */
    private GraphSerializer<T> serializer;
    
    private GraphCache() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new graph cache with a default minimum memory threshold
     * and disk caching policy.
     * 
     * @param serializer Serializer to be used to store and load graphs
     * from persistent storage.
     */
    public GraphCache(GraphSerializer<T> serializer) {
        this.serializer = serializer;
    }
    
    /**
     * Creates a new graph cache with a default minimum memory threshold
     * which immediately caches a given number of graphs to disk when
     * the threshold is passed.
     *
     * @param serializer Serializer to be used to store and load graphs
     * from persistent storage.
     * @param initRemoveNum Number of graphs to immediately cache to disk
     * when available memory falls below the minimum threshold. Higher
     * values may improve performance by immediately clearing more memory
     * and thus reducing the frequency with which the cache must search
     * for and cache eligible graphs.
     */
    public GraphCache(GraphSerializer<T> serializer, int initRemoveNum) {
        this(serializer);
        cache = new CacheMap(initRemoveNum);
    }
    
    /**
     * Creates a new graph cache which immediately caches the specified number
     * of graphs when the given minimum memory threshold is passed.
     *
     * @param serializer Serializer to be used to store and load graphs
     * from persistent storage.
     * @param initRemoveNum Number of graphs to immediately cache to disk
     * when available memory falls below the minimum threshold. Higher
     * values may improve performance by immediately clearing more memory
     * and thus reducing the frequency with which the cache must search
     * for and cache eligible graphs.
     * @param lowMemThreshold Minimum free memory, as a percentage of total
     * memory, that the cache must attempt to keep free. Setting this value
     * too high may result in disk thrashing.
     */
    public GraphCache(GraphSerializer<T> serializer, int initRemoveNum,
            float lowMemThreshold) {
        this(serializer);
        cache = new CacheMap(initRemoveNum, lowMemThreshold);
    }
    
    /**
     * Adds a new graph to the cache.
     *
     * <p>This action may result in existing graphs being cached to disk, if
     * the addition of the new graph causes free memory to drop below the
     * minimum threshold.</p>
     *
     * @param method Signature of the method for which a graph is being
     * cached, used as the key to retrieve the graph later.
     * @param graph Graph being added to the cache.
     *
     * @return A {@link GraphCache.CachedGraph} which can be used to modify
     * the publicly visible status of the graph. The graph itself cannot be
     * accessed through this object ({@link GraphCache.CachedGraph#getGraph}
     * will return <code>null</code>) since the existence of a strong
     * reference would prevent it from being removed from memory. The
     * {@link GraphCache#get} method should be used instead to retrieve the
     * graph.
     *
     * @throws CacheException If the operation results in an attempt to
     * cache graphs to disk, and that operation fails for any reason.
     */
    @SuppressWarnings("unchecked")
    public CachedGraph<T> put(MethodSignature method, T graph)
            throws CacheException {
        // We don't want the value strongly referencing the key
        MethodSignature key = method.copy();
        
        String className = key.getClassName();
        GraphStatus status = new GraphStatus();
        
        if (cache.put(key, graph) == null) {
            size += 1;
        }
        Map<MethodSignature, Object> classMethods;
        if (statusCache.containsKey(className)) {
            classMethods = (THashMap) statusCache.get(className);
        }
        else {
            classMethods = new THashMap();
            statusCache.put(className, classMethods);
        }
        classMethods.put(key, status);
        
        // We leave the graph field null because we can't keep
        // a strong reference to the value laying around
        return new CachedGraph<T>(null, status);
    }
    
    /**
     * Gets a graph from the cache.
     *
     * <p>The graph will be read from disk, if necessary. This method may
     * also result in graphs being cached to disk, if the retrieval of the
     * current graph causes free memory to fall below the minimum
     * threshold.</p>
     *
     * @param method Signature of the method for which to retrieve a
     * graph.
     *
     * @return A {@link GraphCache.CachedGraph} which may be used to
     * access the graph and modify the publicly visible status of the graph.
     *
     * @throws CacheException If no graph exists in the cache for the
     * requested method, or if the graph must be read from a cache file
     * and that operation fails for any reason.
     */
    @SuppressWarnings("unchecked")
    public CachedGraph<T> get(MethodSignature method)
            throws CacheException {
        String className = method.getClassName();
        
        if (!statusCache.containsKey(className)) {
            return null;
        }
        
        Map classMethods = (THashMap) statusCache.get(className);
        if (!classMethods.containsKey(method)) {
            return null;
        }
        
        GraphStatus status = (GraphStatus) classMethods.get(method);
        T graph;
        if (status.isCachedToDisk()) {
            graph = serializer.readFromDisk(method);
            cache.put(method.copy(), graph);
            status.setCachedToDisk(false);
        }
        else {
            // Commons-collections not generified
            graph = (T) cache.get(method);
        }
        
        return new CachedGraph<T>(graph, status);
    }
    
    /**
     * Removes a graph from the cache.
     *
     * @param method Signature of the method for which the graph is
     * to be removed.
     *
     * @return <code>true</code> if a graph was found for the method
     * and successfully removed, <code>false</code> otherwise.
     */
    public boolean remove(MethodSignature method) {
        String className = method.getClassName();
        
        if (!statusCache.containsKey(className)) {
            return false;
        }
        
        Map classMethods = (THashMap) statusCache.get(className);
        if (!classMethods.containsKey(method)) {
            return false;
        }
        
        // We simply remove the key; the graph itself will be reclaimed
        // whenever the garbage collector deems it necessary
        classMethods.remove(method);
        cache.remove(method);
        size -= 1;
        
        return true;
    }
    
    /**
     * Clears the cache.
     */
    public void clear() {
        statusCache.clear();
        cache.clear();
        size = 0;
    }
    
    /**
     * Gets the number of graphs currently in the cache.
     *
     * @return The number of graphs currently cached.
     */
    public int size() {
        return size;
    }
    
    /**
     * Gets all of the graphs in the cache for a given class.
     *
     * @param className Name of the class for which to retrieve all of
     * its graphs.
     *
     * @return A collection containing all of the graphs found in the cache
     * which are associated with the specified class.
     *
     * @throws OutOfMemoryError If there is not enough space in memory to hold
     * all of the graphs. For this reason, it is recommended that graphs instead
     * be retrieved using either a direct iterator or by iterating over the
     * key set.
     */
    @SuppressWarnings("unchecked")
    public Collection<T> getForClass(String className) {
        if (!statusCache.containsKey(className)) {
            return null;
        }
        
        Map classMethods = (THashMap) statusCache.get(className);
        if (classMethods.size() == 0) {
            return null;
        }
        
        Set<T> classGraphs = new THashSet();
        Iterator graphKeys = classMethods.keySet().iterator();
        while (graphKeys.hasNext()) {
            MethodSignature key = (MethodSignature) graphKeys.next();
            GraphStatus status = (GraphStatus) classMethods.get(key);
            if (!status.isComplete()) {
                 throw new CacheException("Incomplete graph for method " +
                     "in class");
            }
            T graph;
            if (status.isCachedToDisk()) {
                graph = serializer.readFromDisk(key);
                cache.put(key, graph);
                status.setCachedToDisk(false);
            }
            else {
                // Commons-collections not generified
                graph = (T) cache.get(key);
            }
            classGraphs.add(graph);
        }
        
        return classGraphs;
    }
    
    /**
     * Gets the key set for the cache, in no particular order.
     *
     * @return The set of keys to graphs in the cache, in no guaranteed order.
     */
    public Set<MethodSignature> keySet() {
        return fullKeySet(false);
    }
    
    /**
     * Gets the key set consisting of the keys for all graphs in the
     * cache associated with a particular class, in no particular
     * order.
     *
     * @param className Name of the class for which the keys of
     * associated graphs are to be retrieved.
     *
     * @return The set of keys to graphs in the cache which are
     * associated with the specified class, in no guaranteed order.
     *
     * @throws CacheException If no graphs associated with the given
     * class are found in the cache.
     */
    public Set<MethodSignature> keySet(String className) throws CacheException {
        return classKeySet(className, false);
    }
    
    /**
     * Reports whether the cache contains a mapping for a given key.
     *
     * @param method Method signature which is the key for which to check
     * if a mapping exists in the cache.
     *
     * @return <code>true</code> if the cache contains a mapping for the
     * given key, <code>false</code> otherwise.
     */
    public boolean containsKey(MethodSignature method) {
        String className = method.getClassName();
        
        if (!statusCache.containsKey(className)) {
            return false;
        }
        
        Map classMethods = (THashMap) statusCache.get(className);
        if (!classMethods.containsKey(method)) {
            return false;
        }
        else {
            return true;
        }
    }
    
    /**
     * Gets an iterator over the graphs in the cache.
     *
     * @return An iterator over the graphs in the cache. The iterator makes
     * no guarantee regarding the order of iteration.
     */
    public Iterator<T> iterator() {
        return new ValueIterator(fullKeySet(false).iterator());
    }
    
    /**
     * Gets a sorted iterator over the graphs in the cache.
     *
     * <p>The order of iteration is consistent with the lexical ordering
     * of the names of the methods with which the graphs are associated.</p>
     *
     * @return An iterator over the graphs in the cache, which iterates
     * over the graphs in the order determined by a lexical sorting of
     * the names of the methods with which the graphs are associated.
     *
     * @see sofya.base.MethodSignature.NameComparator
     */
    public Iterator<T> sortedIterator() {
        return new ValueIterator(fullKeySet(true).iterator());
    }
    
    /**
     * Gets an iterator over the graphs in the cache which are associated
     * with a specified class.
     *
     * @param className Name of the class with which the graphs returned
     * by the iterator should be associated.
     *
     * @return An iterator over the graphs in the cache which are associated
     * with the specified class. The iterator makes no guarantee regarding
     * the order of iteration.
     */
    public Iterator<T> iterator(String className)
            throws CacheException {
        return new ValueIterator(classKeySet(className, false).iterator());
    }
    
    /**
     * Gets a sorted iterator over the graphs in the cache which are associated
     * with a specified class.
     *
     * <p>The order of iteration is consistent with the lexical ordering
     * of the names of the methods with which the graphs are associated.</p>
     *
     * @param className Name of the class with which the graphs returned by
     * the iterator should be associated.
     *
     * @return An iterator over the graphs in the cache which are associated
     * with the specified class, and which iterates over the graphs in the
     * order determined by a lexical sorting of the names of the methods with
     * which the graphs are associated.
     *
     * @see sofya.base.MethodSignature.NameComparator
     */
    public Iterator<T> sortedIterator(String className)
            throws CacheException {
        return new ValueIterator(classKeySet(className, true).iterator());
    }
    
    /**
     * Internal helper method which returns the full key set for all entries
     * in the cache.
     *
     * @param sorted Specifies whether the key set should be sorted. If
     * <code>true</code>, the keys will be sorted lexically using the method
     * names (see {@link sofya.handlers.MethodSignature.NameComparator}).
     *
     * @return A set containing all of the keys currently in the cache.
     */
    @SuppressWarnings("unchecked")
    private Set<MethodSignature> fullKeySet(boolean sorted) {
        Set<MethodSignature> sigKeys;
        if (sorted) {
            sigKeys = new TreeSet<MethodSignature>(
                new MethodSignature.NameComparator());
        }
        else {
            sigKeys = new THashSet();
        }
        
        Iterator<Map<MethodSignature, Object>> classes =
            statusCache.values().iterator();
        while (classes.hasNext()) {
            Map<MethodSignature, Object> clGraphs = classes.next();
            sigKeys.addAll(clGraphs.keySet());
        }
        return sigKeys;
    }
    
    /**
     * Internal helper method which returns all of the keys in the cache
     * associated with a particular class.
     *
     * @param className Name of the class for which associated keys are to be
     * retrieved.
     * @param sorted Specifies whether the key set should be sorted. If
     * <code>true</code>, the keys will be sorted lexically using the method
     * names (see {@link sofya.handlers.MethodSignature.NameComparator}).
     *
     * @return A set containing all of the keys currently in the cache which
     * are associated with the given class.
     */
    @SuppressWarnings("unchecked")
    private Set<MethodSignature> classKeySet(String className, boolean sorted)
                throws CacheException {
        if (className == null) {
            throw new NullPointerException();
        }
        
        Map<MethodSignature, Object> classGraphs = statusCache.get(className);
        if (classGraphs != null) {
            Set<MethodSignature> sigKeys;
            if (sorted) {
                sigKeys = new TreeSet<MethodSignature>(
                    new MethodSignature.NameComparator());
            }
            else {
                sigKeys = new THashSet();
            }

            sigKeys.addAll(classGraphs.keySet());
            return sigKeys;
        }
        else {
            throw new CacheException("Class not found in cache: " + className);
        }
    }

    /**
     * Special internal map class which actually maintains those graphs
     * which are still in memory, and is responsible for monitoring memory
     * usage and enforcing the LRU policy when transferring graphs to
     * disk to keep free memory above the minimum threshold.
     */
    @SuppressWarnings("serial") // Inherited under duress from LRUMap
    class CacheMap extends LRUMap {
        /** Reference the the runtime environment, used to access information
            about the memory state of the JVM. */
        private Runtime runtime = Runtime.getRuntime();
        /** Default number of graphs to remove immediately when free memory
            drops below the minimum threshold. */
        private int numOnRemove = 3;
        /** Constant value which represents the hard limit on memory available
            to the JVM. */
        private long maxMemory = runtime.maxMemory();
        /** Minimum free memory threshold, as a percentage of the total
            memory (maxMemory). */
        private float lowMemThreshold = LOW_MEMORY_THRESHOLD;
        
        /**
         * Creates a new memory sensitive map with default values.
         */
        CacheMap() { }
        
        /**
         * Creates a new memory senstive map which immediately removes a
         * specified number of graphs when free memory falls below the
         * minimum threshold.
         *
         * @param numOnRemove Number of graphs to immediately remove when
         * free memory falls below the minimum threshold.
         */
        CacheMap(int numOnRemove) {
            this.numOnRemove = numOnRemove;
        }
        
        /**
         * Creates a new memory senstive map which immediately removes a
         * specified number of graphs when free memory falls below the
         * a specified minimum threshold.
         *
         * @param numOnRemove Number of graphs to immediately remove when
         * free memory falls below the minimum threshold.
         * @param lowMemThreshold Minimum amount of memory the map should
         * attempt to keep free, as a percentage of total available memory.
         */
        CacheMap(int numOnRemove, float lowMemThreshold) {
            this.numOnRemove = numOnRemove;
            this.lowMemThreshold = lowMemThreshold;
        }
        
        /**
         * Returns <code>false</code> to disable size bounding.
         *
         * <p>Overriding this method to always return <code>false</code>
         * prevents the map from attempting to remove entries simply because
         * an arbitrary map entry count has been reached, while still allowing
         * the LRU bookeeping to be inherited from the superclass. Entries are
         * instead removed based on the free memory threshold.
         *
         * @return <code>false</code>, always.
         */
        public boolean isFull() {
            return false;
        }
        
        /**
         * Reports whether free memory has dropped below the specified
         * threshold.
         *
         * @return <code>true</code> if free memory is below the specified
         * percentage of total memory, <code>false</code> otherwise.
         */
        private boolean freeMemoryIsLow() {
            long freeMem = runtime.freeMemory();
            long totalMem = runtime.totalMemory();
            
            //System.err.println("free: " + freeMem);
            //System.err.println("total: " + totalMem);
            //System.err.println("max: " + runtime.maxMemory());
            
            // The total memory value reported by the Runtime object represents
            // the heap memory currently allocated for objects, and may
            // actually increase over time as the JVM grows the heap to meet
            // memory demand. Thus we only consider ourselves at risk for
            // violating the threshold if the memory allocated for heap use is
            // equal to the hard limit on memory available to the JVM as
            // a whole.
            if (totalMem == maxMemory) {
                // Do an initial test -- if it seems like we are low on
                // memory, hint for a garbage collection and then test
                // again and return that result.
                boolean seemsLow =
                    ((double) freeMem / (double) totalMem) < lowMemThreshold;
                if (seemsLow) {
                    runtime.gc();
                    runtime.gc();
                    return ((double) freeMem / (double) totalMem)
                        < lowMemThreshold;
                }
                else {
                    return false;
                }
            }
            else {
                return false;
            }
        }
        
        /**
         * Removes keys from the map while caching their values (graphs)
         * to disk, thus making the graphs available for garbage collection
         * to free up memory.
         *
         * @numKeys Number of keys (graphs) which the method should attempt
         * to remove from the map. A value of -1 instructs the method to
         * remove as many keys as necessary to push free memory back above
         * the minimum threshold, if that is possible. 
         */
        @SuppressWarnings("unchecked")
        private boolean removeKeys(int numKeys) {
            ArrayList<MethodSignature> forRemoval;
            if (numKeys > 0) {
                forRemoval = new ArrayList<MethodSignature>(numKeys);
            }
            else {
                forRemoval = new ArrayList<MethodSignature>(numOnRemove);
            }
            int removeCount = 0;
            
            // Commons-collections not generified
            Iterator<MethodSignature> keys = orderedMapIterator();
            while (keys.hasNext()) {
                MethodSignature ms = keys.next();
                String keyClass = ms.getClassName();
                
                if (!statusCache.containsKey(keyClass)) {
                    throw new CacheException("No cache status available for " +
                        "method " + ms);
                }
                Map keyClassMethods = (THashMap) statusCache.get(keyClass);
                
                if (((GraphStatus) keyClassMethods.get(ms)).isComplete()) {
                    forRemoval.add(ms);
                    removeCount++;
                    if (((numKeys <= 0) && !freeMemoryIsLow())
                            || ((numKeys > 0) && (removeCount == numKeys))) {
                        break;
                    }
                }
            }
            if (removeCount == 0) {
                return false;
            }
            
            for (int i = 0; i < forRemoval.size(); i++) {
                MethodSignature ms = (MethodSignature) forRemoval.get(i);
                T graph = (T) get(ms);
                serializer.writeToDisk(ms, graph);
                ((GraphStatus) ((Map) statusCache.get(ms.getClassName()))
                    .get(ms)).setCachedToDisk(true);
                remove(ms);
            }
            return true;
        }
        
        /**
         * Adds a new mapping.
         *
         * <p>This may cause existing mappings to be removed by caching their
         * values to disk, if the addition of the new mapping causes free
         * memory to fall below the minimum threshold.</p>
         *
         * @param key Key to add to the map.
         * @param value Value associated with the key.
         *
         * @return A reference to the key.
         */
        public Object put(Object key, T value) {
            if (freeMemoryIsLow()) {
                removeKeys(numOnRemove);
                
                if (freeMemoryIsLow()) {
                    if (!removeKeys(-1) && freeMemoryIsLow()) {
                        System.err.println("WARNING: Unable to keep " +
                            "free memory above minimum threshold");
                    }
                }
            }
            return super.put(key, value);
        }
        
        /**
         * Handles removal of a mapping when the hard size of the cache
         * is exceeded - <em>this should never happen</em>!
         *
         * @param entry Least recently used entry in the map selected
         * for removal.
         *
         * @throws CacheException <strong>Always.</strong>
         */
        protected boolean removeLRU(LinkEntry entry) {
            throw new CacheException("Cache size exceeded");
        }
    }
    
    /**
     * Container class used to encapsulate a cached graph and its associated
     * status for passing to external users of the cache.
     */
    public static class CachedGraph<T>  {
        /** The graph retrieved from the cache. */
        private T theGraph = null;
        /** Status associated with the graph. */
        private GraphStatus status = null;
        
        /**
         * Creates a new cached graph container.
         *
         * @param g Graph stored in the cache.
         * @param s Status associated with the graph.
         */
        CachedGraph(T g, GraphStatus s) {
            theGraph = g;
            status = s;
        }
        
        /**
         * Gets the graph retrieved from the cache.
         *
         * @return The graph which was retrieved from the cache.
         */
        public T getGraph() {
            return theGraph;
        }
        
        /**
         * Reports whether the graph has been marked as complete.
         *
         * <p><strong>Only graphs marked as complete are cached to
         * disk!</strong> This is a safety measure to avoid corrupting
         * algorithms which may depend on object identity to function
         * correctly. It may be relaxed in the future.</p>
         *
         * @return <code>true</code> if the graph is considered complete
         */
        public boolean isComplete() {
            return status.isComplete();
        }
        
        /**
         * Sets whether a graph is complete.
         *
         * <p><strong>Only graphs marked as complete are cached to
         * disk!</strong> This is a safety measure to avoid corrupting
         * algorithms which may depend on object identity to function
         * correctly. It may be relaxed in the future.</p>
         *
         * @param isComplete <code>true</code> to mark the graph as complete,
         * <code>false</code> otherwise.
         */
        public void setComplete(boolean isComplete) {
            status.setComplete(isComplete);
        }
        
        /**
         * Reports whether the graph has been marked as fresh.
         *
         * <p>This flag may be set internally to indicate that a graph was
         * constructed automatically by the type inference module. This
         * is used, for example, by the CFGBuilder to return the graph
         * from the cache the first time it is requested, even if by
         * a <code>buildCFG</code> method.</p>
         *
         * @return <code>true</code>If the graph has not been requested
         * externally since it was constructed.
         */
        public boolean isFresh() {
            return status.isFresh();
        }
        
        /**
         * Sets whether a graph is fresh.
         *
         * <p>A graph should be considered fresh only if it has not been
         * requested externally since it was constructed.</p>
         *
         * @param isFresh <code>true</code> to mark the graph as fresh,
         * <code>false</code> otherwise.
         */
        public void setFresh(boolean isFresh) {
            status.setFresh(isFresh);
        }
    }
    
    /**
     * Class used to associate status information with a graph stored in the
     * cache. Which aspects of the graph's status are publicly visible is
     * determined by exposing public methods through the
     * {@link GraphCache.CachedGraph} class.
     */
    static class GraphStatus {
        /** Bitmask used for storing boolean status flags. */
        private int status = 0x00000000;
        
        /** Bitmask to set the bit marking the graph as complete. */
        public static final int COMPLETE        = 0x00000001;
        /** Bitmask to set the bit marking the graph as cached to disk. */
        public static final int CACHED_TO_DISK  = 0x00000002;
        
        public static final int FRESH           = 0x00000004;
        
        /**
         * Creates a new graph status with no status fields set.
         */
        GraphStatus() { }
        
        /**
         * Reports whether the graph is marked as complete.
         */
        public boolean isComplete() {
            return (status & COMPLETE) != 0;
        }
        
        /**
         * Specifies whether the graph is complete.
         */
        public void setComplete(boolean isComplete) {
            status = (isComplete) ? status | COMPLETE : status & 0xFFFFFFFE;
        }
        
        /**
         * Reports whether the graph is currently cached to disk.
         */
        public boolean isCachedToDisk() {
            return (status & CACHED_TO_DISK) != 0;
        }
        
        /**
         * Specifies whether the graph is currently cached to disk.
         */
        public void setCachedToDisk(boolean onDisk) {
            status = (onDisk) ? status | CACHED_TO_DISK : status & 0xFFFFFFFD;
        }
        
        /**
         * Reports whether the graph is fresh.
         */
        public boolean isFresh() {
            return (status & FRESH) != 0;
        }
        
        /**
         * Specifies whether the graphs is fresh.
         */
        public void setFresh(boolean isFresh) {
            status = (isFresh) ? status | FRESH : status & 0xFFFFFFFB;
        }
    }
    
    /**
     * Iterator for cached graphs.
     *
     * <p>The iterator accepts an iterator over a key set and uses it to
     * automatically retrieve the associated graphs and return them
     * through this iteration. This includes automatically retrieving
     * graphs cached to disk.</p>
     */
    class ValueIterator implements Iterator<T> {
        /** Iterator over a set of keys from the cache. */
        private Iterator<MethodSignature> theIterator;
        
        /** No argument constructor is invalid. */
        private ValueIterator() { }
        
        /**
         * Creates an iterator over the values associated with the keys
         * returned by a given iterator.
         *
         * @param i Iterator over a key set from the cache.
         */
        ValueIterator(Iterator<MethodSignature> i) {
            theIterator = i;
        }
        
        /**
         * Reports whether there are any more elements in the iteration.
         */
        public boolean hasNext() {
            return theIterator.hasNext();
        }
        
        /**
         * Gets the next element from the iteration.
         */
        public T next() {
            CachedGraph<T> cachedGraph = get(theIterator.next());
            return cachedGraph.getGraph();
        }
        
        /**
         * <strong>Unsupported operation<strong> - this is a read only
         * iterator!
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
