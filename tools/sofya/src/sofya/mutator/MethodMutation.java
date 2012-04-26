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

/**
 * A mutation that applies a transformation to the bytecode of a method.
 *
 * @author Alex Kinneer
 * @version 09/25/2005
 */
public abstract class MethodMutation extends MutationImpl {
    /** Name of the class containing the mutated method. */
    protected String className;
    /** Name of the mutated method. */
    protected String methodName;
    /** Signature of the mutated method. */
    protected String signature;

    /**
     * Gets the name of the class containing the method to which the
     * mutation applies.
     *
     * @return The name of the class implementing the mutated method.
     */
    public String getClassName() {
        return className;
    }
    
    /**
     * Gets the name of the method to which the mutation applies.
     *
     * @return The name of the mutated method.
     */
    public String getMethodName() {
        return methodName;
    }
    
    /**
     * Gets the signature of the method to which the mutation applies.
     *
     * @return The signature of the mutated method.
     */
    public String getSignature() {
        return signature;
    }
}
