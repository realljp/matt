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

package sofya.mutator.verifier;

import org.apache.bcel.classfile.JavaClass;

/**
 * A verifier performs verification of Java classes and methods.
 *
 * @author Alex Kinneer
 * @version 10/19/2005
 */
public interface Verifier {
    /**
     * Type-safe enumeration used for specifying levels of verification.
     */
    static final class Pass {
        /** Constant for verification pass 1. */
        public static final int IONE     = 1;
        /** Constant for verification pass 2. */
        public static final int ITWO     = 2;
        /** Constant for verification pass 3a. */
        public static final int ITHREE_A = 3;
        /** Constant for verification pass 3b. */
        public static final int ITHREE_B = 4;
        
        /** Type-safe constant for verification pass 1. */
        public static final Pass ONE =     new Pass(IONE);
        /** Type-safe constant for verification pass 2. */
        public static final Pass TWO =     new Pass(ITWO);
        /** Type-safe constant for verification pass 3a. */
        public static final Pass THREE_A = new Pass(ITHREE_A);
        /** Type-safe constant for verification pass 3b. */
        public static final Pass THREE_B = new Pass(ITHREE_B);
        
        private final int code;
        
        private Pass() {
            code = -1;
        }
        
        private Pass(int code) {
            this.code = code;
        }
        
        /**
         * Gets the integer constant associated with this verification pass.
         *
         * @return The integer constant associated with this verification
         * pass.
         */
        public int toInt() {
            return code;
        }
    }
    
    /**
     * Loads a class, overriding any existing definition of the class.
     *
     * <p>This method is used to instruct the verifier to load the mutated
     * bytecode of the class to be verified. The verifier should therefore
     * ensure that no subsequent requests to verify the given class will
     * operate on any cached version of the class.</p>
     *
     * @param clazz BCEL representation of the class to be loaded.
     *
     * @throws VerifierException If an error prevents the class from being
     * loaded.
     */
    void loadClass(JavaClass clazz)
            throws VerifierException;
    
    /**
     * Loads a class, overriding any existing definition of the class.
     *
     * <p>This method is used to instruct the verifier to load the mutated
     * bytecode of the class to be verified. The verifier should therefore
     * ensure that no subsequent requests to verify the given class will
     * operate on any cached version of the class.</p>
     *
     * @param className Name of the class to be loaded.
     * @param classBytes The bytes comprising the class to be loaded.
     *
     * @throws VerifierException If an error prevents the class from being
     * loaded.
     */
    void loadClass(String className, byte[] classBytes)
            throws VerifierException;
    
    /**
     * Verifies a method.
     *
     * @param className Name of the class implementing the method to be
     * verified.
     * @param methodName Name of the method to be verified.
     * @param signature Signature of the method to be verified.
     * @param level Verification pass to be applied. Only passes 3a
     * and 3b will actually verify the specific method.
     *
     * @return A verification result, containing information about the
     * cause of failure if the method did not verify.
     *
     * @throws VerifierException If the verifier is unable to execute
     * on the method for any reason, such as if requested method cannot
     * be found in the given class.
     */
    VerificationResult verify(String className, String methodName,
            String signature, Pass level)
            throws VerifierException;
    
    /**
     * Verifies a class.
     *
     * @param className Name of the class to be verified.
     * @param level Verification pass to be applied. When chosen,
     * passes 3a and 3b will be applied to every method in the class.
     *
     * @return A verification result, containing information about the
     * cause of failure if the class did not verify.
     *
     * @throws VerifierException If the verifier is unable to execute
     * on the class for any reason.
     */
    VerificationResult verify(String className, Pass level)
            throws VerifierException;
}
