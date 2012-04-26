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

package sofya.apps.dejavu;

import java.io.IOException;

import sofya.graphs.Node;
import sofya.base.exceptions.MethodNotFoundException;

/**
 * This abstract class defines the contract for classes that provide the
 * service of comparing nodes from different Java classes for
 * equality. The actual means of comparison is abstracted behind
 * this interface, and is (of course) up to the class providing
 * a concrete implementation of the interface.
 *
 * @author Alex Kinneer
 * @version 05/03/2005
 */
public abstract class NodeComparer {
    /** Information about the class which is currently loaded for
        node comparisons. */
    protected ClassPair class_;
    
    /**
     * No-argument constructor for subclasses only.
     */
    protected NodeComparer() { }
    
    /**
     * Gets the name of the class from which the nodes to be compared
     * are obtained (as set by {@link NodeComparer#setComparisonClass}.
     *
     * @return The name of the class from which the nodes have been obtained.
     */
    public ClassPair getComparisonClass() {
        return class_;
    }

    /**
     * Sets the name of the class from which the nodes to be compared
     * are obtained.
     *
     * @param clazz Information about the class from which nodes are
     * to be compared.
     *
     * @throws IOException If the class or class-related file cannot
     * be loaded (most comparers are expected to need to access files
     * based on this information.)
     */
    public void setComparisonClass(ClassPair clazz) throws IOException {
        this.class_ = clazz;
    }

    /**
     * Retrives the name of the method that is invoked by a call node.
     *
     * @param methodName Name of the method which contains the call node
     * for which the called method name is being requested.
     * @param oldCallNode Call node in the original graph.
     * @param newCallNode Call node in the modified graph.
     *
     * @return The name of the called method contained within the node,
     * if it can be obtained from either the original or modified node.
     *
     * @throws MethodNotFoundException If no method matching the specified
     * name can be found within which to locate the call nodes.
     */
    public abstract String getCallMethodName(String methodName,
            Node oldCallNode, Node newCallNode)
            throws MethodNotFoundException;

    /**
     * Compares two nodes from the same method in two different graphs
     * (versions of the program).
     *
     * @param methodName Name of the method from which the nodes for
     * comparison have been obtained.
     * @param oldNode Node for comparison from the original graph.
     * @param newNode Node for comparison from the modified graph.
     *
     * @return <code>true</code> if the two nodes are equivalent,
     * <code>false</code> otherwise.
     *
     * @throws MethodNotFoundException If no method by the given name exists
     * in one or both of the graphs.
     * @throws NullPointerException If a valid node for comparison is
     * not provided.
     */
    public abstract boolean compareNodes(String methodName,
            Node oldNode, Node newNode)
            throws MethodNotFoundException, NullPointerException;
}

