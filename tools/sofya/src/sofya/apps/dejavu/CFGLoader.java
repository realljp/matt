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

import sofya.base.exceptions.*;
import sofya.graphs.Graph;
import sofya.graphs.cfg.CFHandler;

/**
 * Loader class for control flow graphs.
 *
 * @author CS562 2003 dev team
 * @author Alex Kinneer
 * @version 09/20/2004
 */
public class CFGLoader implements GraphLoader {
    /** Galileo handler for reading control flow graphs. */
    private CFHandler cfHandler;
    /** Name of the class from which control flow graphs can currently
        be loaded. */
    private String className;

    /**
     * Standard constructor, initializes the loader.
     */
    public CFGLoader() {
        cfHandler = new CFHandler();
    }
    
    /**
     * Sets the current class from which control flow graphs are
     * to be retrieved.
     *
     * @param className Name of the class from which to load
     * control flow graphs.
     * @param tag Database tag associated with the class file's
     * control flow data.
     *
     * @throws FileNotFoundException If no control flow file can be found for
     * the specified class.
     * @throws EmptyFileException If the control flow file for the specified
     * class contains no data.
     * @throws BadFileFormatException If the control flow file for the
     * specified class is corrupted.
     * @throws IOException For any other type of IO error that prevents the
     * control flow file from being read successfully.
     */
    public void setClass(String className, String tag)
                throws FileNotFoundException, EmptyFileException,
                       BadFileFormatException, IOException {
        this.className = className;
        cfHandler.readCFFile(className, tag);
    }

    /**
     * Returns the list of methods for which control flow graphs
     * can be retrieved.
     *
     * <strong>Note:</strong> {@link CFGLoader#setClass} must be called
     * before this method or an exception will be thrown.
     *
     * @return The list of methods for which control flow graphs are available.
     *
     * @throws IllegalStateException If <code>setClass</code> has not been
     * called prior to calling this method.
     */
    public String[] getMethodList() throws IllegalStateException {
        if (className == null) {
            throw new IllegalStateException("No class is loaded");
        }
        return cfHandler.getMethodList();
    }

    /**
     * Returns the control flow graph for the specified method.
     *
     * <strong>Note:</strong> {@link CFGLoader#setClass} must be called
     * before this method or an exception will be thrown.
     *
     * @param method Name of the method for which a control flow graph
     * is to be retrieved.
     *
     * @return The control flow graph for that method.
     *
     * @throws MethodNotFoundException If a method by the given name does not
     * exist in the control flow file.
     * @throws IllegalStateException If <code>setClass</code> has not been
     * called prior to calling this method.
     */
    public Graph getGraph(String method) throws MethodNotFoundException,
                                                IllegalStateException{
        if (className == null) {
            throw new IllegalStateException("No class is loaded");
        }
        return cfHandler.getCFG(method);
    }
}
