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

package sofya.base;

import java.util.List;
import java.util.ArrayList;

/**
 * Encapsulates information about a set of classes constituting a portion
 * of a program. Typically used to identify classes contained in jar files
 * and/or specified in &apos;prog&apos; files.
 *
 * @author Alex Kinneer
 * @version 06/01/2005
 */
public class ProgramUnit {
    /** The location where class files associated with this program unit
        can be found. */
    public final String location;
    /** The classes associated with this program unit. */
    public final List<String> classes;
    
    /** Flag specifying whether the location field should be considered
        valid. If <code>false</code>, tools should assume that entries
        in the class list may already include path information. */
    public final boolean useLocation;
    /** Flag specifying whether the location field points to a jar file. */
    public final boolean isJar;
    
    /**
     * Creates a default program unit.
     *
     * <p>The <code>location</code> field will be <code>null</code>,
     * the <code>useLocation</code> field set to <code>false</code>,
     * and the <code>isJar</code> field set to <code>false</code>.</p>
     *
     * <p>This constructor is typically used to create a &quot;default&quot;
     * program unit which contains the class names specified as arguments to
     * a tool. Some tools prefer to process such entries directly and should
     * be able to accommodate entries which already have associated path
     * information.</p> 
     */
    public ProgramUnit() {
        this.location = null;
        this.useLocation = false;
        this.isJar = false;
        this.classes = new ArrayList<String>();
    }
    
    /**
     * Creates a program unit to specify classes at a specific location.
     *
     * <p>The <code>isJar</code> field is automatically set by parsing
     * the provided location field. The <code>location</code> field is
     * always set to <code>true</code> by this constructor.</p>
     *
     * @param location Name of the directory or jar file which constitutes
     * this program unit.
     *
     */
    public ProgramUnit(String location) {
        this.isJar = location.endsWith(".jar");
        this.useLocation = true;
        
        if (!isJar && !location.endsWith("/")) {
            location += "/";
        }
        
        this.location = location;
        this.classes = new ArrayList<String>();
    }
    
    /**
     * Creates a program unit to specify classes at a specific location
     * with particular characteristics.
     *
     * <p>This constructor is primarily intended to be used for
     * deserialization.</p>
     *
     * @param location Name of the directory or jar file which constitutes
     * this program unit.
     * @param useLocation Flag specifying whether the location should be
     * considered valid.
     * @param isJar Flag specifying whether the location points to a
     * jar file.
     * @param classes List of classes constituting the program unit.
     */
    public ProgramUnit(String location, boolean useLocation, boolean isJar,
            List<String> classes) {
        this.location = location;
        this.useLocation = useLocation;
        this.isJar = isJar;
        this.classes = classes;
    }
    
    /**
     * Adds a class to this program unit.
     *
     * @param className Name of the class to be added to this program unit.
     */
    public void addClass(String className) {
        classes.add(className);
    }
}
