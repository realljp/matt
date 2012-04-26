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

package sofya.mutator;

import java.util.Map;

/************************************************************************
 * A configuration for a mutation operator.
 *
 * <p>This class provides a framework for possible future support of
 * per-operator configuration settings and reflective assignment of
 * field values in operators. Currently only the name of the class
 * implementing the operator is used.</p>
 *
 * @author Alex Kinneer
 * @version 09/28/2005
 */
class OperatorConfiguration {
    /** The name of the class implementing the operator. */
    private final String name;
    /** Configuration settings for the operator. */
    private final Map settings;
    /** Values to be assigned to fields implemented by the operator. */
    private final Map properties;
    
    private OperatorConfiguration() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new mutation operator configuration.
     *
     * @param name Name of the class implementing the mutation operator.
     * @param settings Configuration parameters to be passed to the
     * mutation operator.
     * @param properties Values to be assigned to fields implemented by
     * the mutation operator.
     */
    OperatorConfiguration(String name, Map settings, Map properties) {
        this.name = name;
        this.settings = settings;
        this.properties = properties;
    }
    
    /**
     * Gets the name of the class implementing the mutation operator.
     *
     * @return The name of the class that implements the mutation operator.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the configuration settings to be passed to the mutation operator.
     *
     * @return A map containing the names of configuration parameters and
     * the values to be assigned to those parameters.
     */
    public Map getSettings() {
        return settings;
    }
    
    /**
     * Gets the property values to be assigned in the mutation operator.
     *
     * @return A map containing the names of fields (properties) implemented
     * by the mutation operator and the values to be assigned to those fields.
     */
    public Map getProperties() {
        return properties;
    }
}
