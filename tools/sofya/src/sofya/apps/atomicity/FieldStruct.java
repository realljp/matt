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

/**
 * Structure used to identify instance fields uniquely. Once created,
 * this object is immutable.
 *
 * @author Alex Kinneer
 * @version 11/17/2005
 */
final class FieldStruct {
    /** Identifier for the object that owns the field. */
    public final long objectId;
    /** Name of the field. */
    public final String name;
    
    /** Precomputed hash code. */
    private final int hashCode;
    
    private FieldStruct() {
        throw new AssertionError("Illegal constructor");
    }
    
    FieldStruct(long id, String name) {
        this.objectId = id;
        this.name = name;
        
        // Compute the hash code
        int tmp = 13;
        tmp = (37 * tmp) + (int) objectId;
        tmp = (37 * tmp) + name.hashCode();
        hashCode = tmp;
    }
    
    public boolean equals(Object o) {
        // This is performance-critical, so handling the cast exception
        // is preferred over having to execute instanceof on every call
        // (We should NOT see the cast exception anyway)
        try {
            FieldStruct of = (FieldStruct) o;
            if (this.objectId != of.objectId) {
                return false;
            }
            if (!this.name.equals(of.name)) {
                return false;
            }
            
            return true;
        }
        catch (ClassCastException e) {
            return false;
        }
    }
    
    public int hashCode() {
        return hashCode;
    }
}
