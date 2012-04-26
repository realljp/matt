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

/**
 * Records the result of applying verification to a class or method.
 * If the verification failed, additional information can be obtained
 * about the cause of failure.
 *
 * @author Alex Kinneer
 * @version 10/19/2005
 */
public interface VerificationResult {
    /**
     * Gets the result of verification.
     *
     * @return A value indicating the result of verification. Zero indicates
     * that verification succeeded, one indicates that verification failed
     * but no specific cause is available, and values two or larger indicate
     * specific verification failures.
     */
    int getResult();
    
    /**
     * Gets a message associated with the verification result, which may
     * be an explanation of why verification failed.
     *
     * @return A message describing the verification result. If the
     * verification failed, this message should explain the cause.
     */
    String getMessage();
    
    /**
     * Reports the verification pass that was reached during verification.
     * Sometimes the verifier may fail on an earlier pass before reaching
     * the requested verification pass.
     *
     * @return The verification pass reached by the verifier. If verification
     * succeeded, this will be the pass requested. Otherwise, this will
     * be the verification pass at which the verification failed.
     */
    Verifier.Pass getLevel();
}
