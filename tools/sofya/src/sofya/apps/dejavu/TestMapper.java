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

import sofya.base.exceptions.*;
import sofya.graphs.Edge;

/**
 * A concrete implementation of this class is responsible for performing
 * the mapping of dangerous edges to tests in the original test suite.
 *
 * @author CS562 2003 dev team.
 * @author Alex Kinneer
 * @version 09/23/2004
 */
public abstract class TestMapper {
    /**
     * Selects tests based on the dangerous edge list for a given
     * method.
     *
     * @param methodName Name of the method for which test selection is
     * occurring.
     * @param dangerousEdges List of dangerous edges found in the method,
     * to be mapped to corresponding tests.
     *
     * @return A selection data object containing information about
     * the selected tests, most importantly the list of selected test numbers.
     *
     * @throws MethodNotFoundException If the method test descriptor references
     * a method that cannot be found in the test history file.
     */
    public abstract SelectionData selectTests(String methodName,
                                              Edge[] dangerousEdges)
                                  throws MethodNotFoundException;
    
    /**
     * Returns the number of tests in the test history.
     *
     * @return The total number of tests.
     */
    public abstract int getTotalNumberOfTests();
}


