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

package sofya.apps.atomicity;

import sofya.base.MethodSignature;

import gnu.trove.TObjectByteHashMap;
import gnu.trove.TObjectByteIterator;

/**
 * Records the results of atomicity checking on methods invoked in the
 * monitored program.
 *
 * @author Alex Kinneer
 * @version 06/14/2005
 */
public class ResultCollector {
    /** Map which stores the result of atomicity checking for each method. */
    private TObjectByteHashMap results = new TObjectByteHashMap();

    /** Constant indicating that the atomicity of the method has not
        been determined. */
    @SuppressWarnings("unused")
    private static final byte UNKNOWN     = 0;
    /** Constant indicating that the method is atomic. */
    private static final byte ATOMIC      = 1;
    /** Constant indicating that the method is not atomic. */
    private static final byte NON_ATOMIC  = 2;
    
    /**
     * Creates a new result collector.
     */
    public ResultCollector() {
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // The object/byte hashmap returns 0 if a key is not present in the map.
    // Thus by always applying a logical OR to the existing value in the map
    // it is possible for the following values to be stored in the map:
    //   0 - No information has been recorded about a method, a query about
    //       atomicity of the method will return false (this value is
    //       never explicitly stored).
    //   1 - The method has been observed as atomic in all invocations.
    //       (0 | 1 = 1; 1 | 1 = 1)
    //   2 - The method has been observed as non-atomic in all invocations.
    //       (0 | 2 = 2; 2 | 2 = 2)
    //   3 - The method was observed as atomic in some invocations and
    //       non-atomic in others.
    //       (1 | 2 = 3; 2 | 1 = 3)
    
    /**
     * Add the result of an atomicity check for an invocation of a given
     * method.
     *
     * @param mSig Signature of the invoked method.
     * @param atomic Boolean specifying whether the invocation was found
     * to be atomic or non-atomic.
     */
    public void add(MethodSignature mSig, boolean atomic) {
        byte current = results.get(mSig);
        results.put(mSig, (byte) (current | (atomic ? ATOMIC : NON_ATOMIC)));
    }
    
    /**
     * Checks whether a method was found to be atomic.
     *
     * @return <code>true</code> if the method was found to be atomic
     * on all invocations, <code>false</code> otherwise. This method will
     * also return <code>false</code> if the method was not invoked.
     */
    public boolean get(MethodSignature mSig) {
        return (results.get(mSig) == ATOMIC) ? true : false;
    }
    
    /**
     * Gets the list of methods for which atomicity results are available.
     *
     * @return An array of signatures of methods for which the atomicity
     * property has been checked and stored.
     */
    public MethodSignature[] getMethods() {
        int size = results.size();
        MethodSignature[] methods = new MethodSignature[size];
        TObjectByteIterator iterator = results.iterator();
        for (int i = 0; i < size; i++) {
            iterator.advance();
            methods[i] = (MethodSignature) iterator.key();
        }
        return methods;
    }
}
