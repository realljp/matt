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

import sofya.graphs.Graph;

/**
 * Data structure which maintains information about the class
 * and methods currently being processed by DejaVu.
 *
 * @author CS562 dev team
 * @author Alex Kinneer
 * @version 05/13/2005
 */
public class MethodPair {
    private MethodPair() {
        throw new UnsupportedOperationException();
    }
    
    public MethodPair(ClassPair clazz, String name,
            Graph oldGraph, Graph newGraph) {
        this.class_ = clazz;
        this.name = name;
        this.oldGraph = oldGraph;
        this.newGraph = newGraph;
    }
    
    /** Information about the class which implements the method in the
        respective versions of the program. */
    public final ClassPair class_;

    /** Name of the method. */
    public final String name;

    /** Graph for the old version of the method. */
    public final Graph oldGraph;

    /** Graph for the new version of the method. */
    public final Graph newGraph;
}
