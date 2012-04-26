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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Creates instances of verifiers to be used in verifying mutations.
 *
 * @author Alex Kinneer
 * @version 10/20/2005
 */
public final class VerifierFactory {
    /** The default verifier. */
    private static final Verifier DEFAULT_VERIFIER = new PlatformJVMVerifier();
        
    private VerifierFactory() {
    }
    
    /**
     * Gets the default verifier implementation supplied by Sofya.
     *
     * @return The default verifier for verifying mutations.
     */
    public static Verifier getDefaultVerifier() {
        return DEFAULT_VERIFIER;
    }
    
    /**
     * Gets a verifier by name.
     *
     * @param name Fully qualified name of the verifier class to be
     * instantiated.
     *
     * @return An instance of the specified verifier.
     */
    public static Verifier getVerifier(String name) throws VerifierException {
        Class implClass = null;
        try {
            implClass = Class.forName(name);
        }
        catch (ClassNotFoundException e) {
            throw new VerifierException("Verifier not found");
        }
        
        Constructor cons = null;
        try {
            cons = implClass.getConstructor(new Class[]{});
        }
        catch (NoSuchMethodException e) {
            throw new VerifierException("Verifier cannot be instantiated " +
                "(no argument constructor missing)");
        }
        
        try {
            return (Verifier) cons.newInstance(new Object[]{});
        }
        catch (IllegalAccessException e) {
            throw new VerifierException("Verifier cannot be instantiated " +
                "(no argument constructor not accessible)");
        }
        catch (InstantiationException e) {
            throw new VerifierException("Verifier cannot be instantiated " +
                "(class is abstract)");
        }
        catch (InvocationTargetException e) {
            throw new VerifierException("Verifier constructor failed", e);
        }
    }
}
