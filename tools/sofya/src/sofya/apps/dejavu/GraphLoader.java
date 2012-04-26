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
import java.io.FileNotFoundException;

import sofya.graphs.Graph;
import sofya.base.exceptions.*;

/**
 * This interface defines the contract for classes that
 * provide the service of loading graphs built from
 * Java classes.
 *
 * <p>Classes implementing this interface should provide some
 * means of storing what class is being operated on, so that
 * object instantiations can be kept to a minimum.</p>
 *
 * @author CS562 2003 dev team.
 * @author Alex Kinneer
 * @version 09/20/2004
 */
public interface GraphLoader {
    /**
     * Gets the list of methods available in the current class.
     *
     * @return The list of available method names.
     */
    public String[] getMethodList();

    /**
     * Gets the graph for a given method.
     *
     * @param method Name of the method for which to retrieve a graph.
     *
     * @return The graph for that method.
     *
     * @throws MethodNotFoundException If no method by the given
     * name exists in the loaded class.
     */
    public Graph getGraph(String method) throws MethodNotFoundException;

    /**
     * Sets the class for which graphs are to be retrieved.
     *
     * @param className The class from which graphs will be loaded.
     * @param tag Database tag associated with the class's graph data.
     *
     * @throws FileNotFoundException If the graph data file for the specified
     * class cannot be found.
     * @throws EmptyFileException If the graph data file for the class
     * is empty.
     * @throws BadFileFormatException If the graph data file for the class
     * is corrupted.
     * @throws IOException For any other IO error that prevents the graph
     * data file from being read successfully.
     */
    public void setClass(String className, String tag)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException;
}
