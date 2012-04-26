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

package sofya.ed.semantic;

import java.util.Map;

import gnu.trove.THashMap;

/**
 * Structure that records the changes made to a class file by the
 * {@link SemanticInstrumentor}.
 * 
 * @author Alex Kinneer
 * @version 01/18/2008
 */
final class ClassLog {
    /** Name of the class for which this log records changes. */
    public final String className;
    /** Logs of the changes to individual methods, keyed on signature. */
    public final Map<Object, Object> methodLogs;
    /** Records methods added to the class by the instrumentor; maps
        probe IDs to the methods created for the probe. */
    public final Map<Object, Object> addedMethods;

    private ClassLog() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Creates a new class change log.
     * 
     * @param className Name of the class for which the log records
     * changes.
     */
    @SuppressWarnings("unchecked")
    ClassLog(String className) {
        this.className = className;
        this.methodLogs = new THashMap();
        this.addedMethods = new THashMap();
    }
}
