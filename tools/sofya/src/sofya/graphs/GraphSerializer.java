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

import sofya.base.MethodSignature;

/**
 * A graph serializer is responsible for implementing a custom mechanism
 * for storing and loading graphs to and from persistent storage for
 * a {@link GraphCache}.
 * 
 * <p>It is generally a priority for the serialization and deserialization
 * implementation to be as efficient as possible.</p>
 *
 * @param <T> Type of graph handled by this serializer.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
public interface GraphSerializer<T extends Graph> {
    /**
     * Reads a graph from persistent storage.
     * 
     * @param method Specifies the method for which an associated graph
     * is to be deserialized.
     * 
     * @return The graph for the specified method.
     */
    T readFromDisk(MethodSignature method);
    
    /**
     * Writes a graph to persistent storage.
     * 
     * @param method Signature of the method for which a graph is
     * being serialized.
     * @param graph The graph to be serialized.
     */
    void writeToDisk(MethodSignature method, T graph);
}
