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

import gnu.trove.THashMap;

/************************************************************************
 * A configuration for the mutation table generator.
 *
 * <p>The main function of a mutator configuration is to specify the
 * active mutation operators. It also specifies configuration parameters
 * to the mutation table generator. Global parameters include the
 * default directory to search for mutation operator implementing classes
 * and the location of the default mutation operators list file. A
 * rudimentary framework is also present for possible future support of
 * per-operator configurations, though this functionality is not currently
 * implemented.</p>
 *
 * @author Alex Kinneer
 * @version 08/16/2007
 *
 * @see MutationGenerator
 */
@SuppressWarnings("unchecked")
public final class MutatorConfiguration {
    /** Stores the global properties. */
    private Map<Object, Object> globalProperties;
    /** Stores the operator configurations. */
    private Map<Object, Object> operators = new THashMap();
    
    private MutatorConfiguration() {
    }
    
    /**
     * Creates a new mutator configuration.
     *
     * @param globalProperties Map used to store global configuration
     * properties.
     */
    MutatorConfiguration(Map<Object, Object> globalProperties) {
        this.globalProperties = globalProperties;
    }
    
    /**
     * Adds an active operator to the configuration.
     *
     * @param operatorName Name of the class implementing the mutation
     * operator to be activated in this configuration.
     * @param settings Map containing configuration settings for the mutation
     * operator [unused].
     * @param properties Map containing values to be assigned to properties
     * (fields) of the operator [unused].
     */
    void addOperator(String operatorName, Map<Object, Object> settings,
            Map<Object, Object> properties) {
        operators.put(operatorName,
            new OperatorConfiguration(operatorName, settings, properties));
    }
    
    /**
     * Gets the active mutation operators in this configuration.
     *
     * @return An array of mutation operators enabled in this configuration.
     */
    public OperatorConfiguration[] getOperators() {
        return (OperatorConfiguration[]) operators.values().toArray(
            new OperatorConfiguration[operators.size()]);
    }
    
    /**
     * Gets the default resource path for mutation operator implementations.
     *
     * @return The path to the default directory that is searched to load
     * the classes implementing mutation operators.
     */
    public String getDefaultOperatorResourcePath() {
        return (String) globalProperties.get("operatorResourcePath");
    }
    
    /**
     * Gets the path to the resource listing mutation operators that are
     * considered enabled by default.
     *
     * @return The path to the file that lists the names of classes
     * implementing mutation operators to be included in the configuration
     * by default.
     */
    public String getOperatorListResource() {
        return (String) globalProperties.get("operatorList");
    }
}
