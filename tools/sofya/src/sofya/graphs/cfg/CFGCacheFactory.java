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

import sofya.graphs.GraphCache;

/**
 * Factory to obtain graph caches for control flow graphs.
 * 
 * <p>This class enables components to obtain a fully functional graph
 * cache for control flow graphs with a default serialization strategy,
 * while protecting the serializer. Graph caching is an internal
 * implementation strategy to manage memory consumption, therefore
 * the default CFG serializer is not intended for public consumption.
 * Clients of Sofya should not attempt to use the serializer for their
 * own storage of CFGs, or make any assumptions about the format or
 * contents of cache files.</p>
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 * 
 * @see sofya.graphs.GraphCache
 * @see sofya.graphs.cfg.CFG
 */
public final class CFGCacheFactory {
    /**
     * Creates a new graph cache for control flow graphs.
     * 
     * @return The new graph cache, parameterized to manage
     * control flow graphs.
     */
    public static final GraphCache<CFG> createCache() {
        return new GraphCache<CFG>(new CFGSerializer());
    }
}
