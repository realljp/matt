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

/**
 * Data structure which correlates a class name to its location in the
 * two versions of the program.
 *
 * @author Alex Kinneer
 * @version 05/03/2005
 */
public final class ClassPair {
    /** The name of the class. */
    public final String name;
    /** Location of the class in the old version of the program. */
    public final String oldLocation;
    /** Location of the class in the new version of the program. */
    public final String newLocation;
    
    /** Stored hashcode which is computed once for efficiency, since
        this is an immutable object. */
    private int hashCode;
    
    private ClassPair() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Constructs a new class pair object.
     *
     * @param name Name of the class.
     * @param oldLocation Location of the class in the old version.
     * @param newLocation Location of the class in the new version.
     */
    ClassPair(String name,
              String oldLocation, String newLocation) {
        this.name = name;
        this.oldLocation = oldLocation;
        this.newLocation = newLocation;
        
        computeHashCode();
    }
    
    /**
     * Computes the stored hashcode.
     */
    private void computeHashCode() {
        hashCode = 13;
        hashCode = (31 * hashCode) + name.hashCode();
        hashCode = (31 * hashCode) + oldLocation.hashCode();
        hashCode = (31 * hashCode) + newLocation.hashCode();
    }
    
    public boolean equals(Object obj) {
        if (this == obj) return true;
        
        if (!(obj instanceof ClassPair)) {
            return false;
        }
        
        ClassPair cpObj = (ClassPair) obj;
        if ((cpObj.name != this.name) ||
                (cpObj.oldLocation != this.oldLocation) ||
                (cpObj.newLocation != this.newLocation)) {
            return false;
        }
        
        return true;
    }
    
    public int hashCode() {
        return hashCode;
    }
}
