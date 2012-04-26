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

package sofya.ed.structural;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A constants class that records the names of all the Sofya interfaces,
 * classes, and their inner classes that comprise the implementation of
 * the structural instrumentation probe.
 *
 * <p>Various Sofya components, including the instrumentors and event
 * dispatcher, need to know and share information about the names of
 * probe interfaces and classes. A principle function of this class
 * is to record in a structured way the inner classes of probe
 * related interfaces and classes. This supports generation of the
 * bootstrap jar file, and patching of names to alternate names when
 * the probe implementation is itself to be traced.</p>
 *
 * @author Alex Kinneer
 * @version 08/01/2007
 */
final class ProbeMetadata {
    /** Path to the package containing structural probe interfaces
        and classes. Note that this is not used in the literal sense,
        but is part of the format of some Java language specification
        entities, such as JNI class literals and constant pool entries. */
    static final String PATH_PREFIX = "sofya/ed/structural/";
    /** Name of the package declaring probe interfaces and classes, with
        a trailing '.' for convenience. */
    static final String PACKAGE_PREFIX = "sofya.ed.structural.";
    
    /** Name of the structural probe interface. */
    static final String INTERFACE_NAME = "Sofya$$Probe$10_36_3676__";
    /** Name of the structural probe isolation class loader. */
    static final String LOADER_NAME = "Sofya$$Probe$ClassLoader";
    /** Name of the default probe implementation class. */
    static final String IMPL_NAME = "SocketProbeImpl";
    /** Name of the adapter class to interface with legacy instrumentation;
        primarily used to fail gracefully. */
    static final String ADAPTER_NAME = "SocketProbe";
    /** Name of the class implementing the probe shutdown hook. */
    static final String SHUTDOWN_HOOK_NAME = "ProbeShutdownHook";
    
    /** Set containing the fully qualified names of all interfaces and
        classes comprising the structural probe implementation. */
    static final Set<Object> ALL_CLASSES = new HashSet<Object>();
    /** Set containing the class data structures that record
        information about probe interfaces and classes. */
    static final Set<Object> CLASSES = new HashSet<Object>();
    /** Set containing the simple names of all "root" probe interfaces
        and classes (those that are not inner interfaces or classes). */
    static final Set<Object> ROOT_SHORT_NAMES = new HashSet<Object>();
    
    /**
     * Records information about a "root" class (one that is not an
     * inner class or interface).
     */
    static final class ClassMetadata {
        /** Class object for the class or interface. */
        final Class theClass;
        /** Flag indicating whether the class needs to be a bootstrap
            class (available to the bootstrap class loader). */
        final boolean bootstrap;
        /** Mapping from inner class short names (starting with the '$')
            to their class objects. */
        final Map<Object, Object> innerClasses;
        
        private ClassMetadata() {
            throw new AssertionError("Illegal constructor");
        }
        
        ClassMetadata(Class theClass, boolean bootstrap) {
            this.theClass = theClass;
            this.bootstrap = bootstrap;
            this.innerClasses = new HashMap<Object, Object>();
            
            Class[] declCl = theClass.getDeclaredClasses();
            int len = declCl.length;
            for (int i = 0; i < len; i++) {
                String simpleName = declCl[i].getSimpleName();
                innerClasses.put("$" + simpleName, declCl[i]);
            }
        }
        
        ClassMetadata(Class theClass, Map<Object, Object> innerClasses) {
            this.theClass = theClass;
            this.bootstrap = true;
            this.innerClasses = innerClasses;
        }
        
        /**
         * Returns a set of the fully qualified, complete names of all
         * inner classes of this class.
         */
        Set<Object> innerClassNames() {
            Set<Object> names = new HashSet<Object>();
            String clName = theClass.getName();
            Iterator<Object> keys = innerClasses.keySet().iterator();
            while (keys.hasNext()) {
                names.add(clName + keys.next());
            }
            return names;
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder("ClassMetadata {\n");
            sb.append("  name: ");
            sb.append(theClass.getName());
            sb.append("\n");
            sb.append("  isBootstrap: ");
            sb.append(bootstrap);
            sb.append("\n");
            sb.append("  inner_classes:\n");
            Iterator<Object> keys = innerClasses.keySet().iterator();
            while (keys.hasNext()) {
                sb.append("    ");
                sb.append(keys.next());
                sb.append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    // Simply initializes the various data structures
    static {
        ROOT_SHORT_NAMES.add(INTERFACE_NAME);
        ROOT_SHORT_NAMES.add(LOADER_NAME);
        ROOT_SHORT_NAMES.add(IMPL_NAME);
        ROOT_SHORT_NAMES.add(ADAPTER_NAME);
        ROOT_SHORT_NAMES.add(SHUTDOWN_HOOK_NAME);
        
        // Requesting the declared classes for the probe interface causes
        // the interface to initialize, which means it tries to use the
        // isolation class loader to load the probe implementation. We
        // only want this to happen during execution by an event dispatcher,
        // so we just add its inner classes manually. This should be a
        // pretty stable list anyway.
        Map<Object, Object> probeIfcInners = new HashMap<Object, Object>();
        probeIfcInners.put("$ImplLoader",
                Sofya$$Probe$10_36_3676__.ImplLoader.class);
        probeIfcInners.put("$SequenceProbe",
                Sofya$$Probe$10_36_3676__.SequenceProbe.class);
        
        ClassMetadata clData;
        
        clData = new ClassMetadata(Sofya$$Probe$10_36_3676__.class,
            probeIfcInners);
        CLASSES.add(clData);
        ALL_CLASSES.add(clData.theClass.getName());
        ALL_CLASSES.addAll(clData.innerClassNames());
        
        clData = new ClassMetadata(Sofya$$Probe$ClassLoader.class, true);
        CLASSES.add(clData);
        ALL_CLASSES.add(clData.theClass.getName());
        ALL_CLASSES.addAll(clData.innerClassNames());

        clData = new ClassMetadata(SocketProbe.class, true);
        CLASSES.add(clData);
        ALL_CLASSES.add(clData.theClass.getName());
        ALL_CLASSES.addAll(clData.innerClassNames());

        clData = new ClassMetadata(SocketProbeImpl.class, false);
        CLASSES.add(clData);
        ALL_CLASSES.add(clData.theClass.getName());
        ALL_CLASSES.addAll(clData.innerClassNames());
        
        clData = new ClassMetadata(ProbeShutdownHook.class, false);
        CLASSES.add(clData);
        ALL_CLASSES.add(clData.theClass.getName());
        ALL_CLASSES.addAll(clData.innerClassNames());
    }
    
    private ProbeMetadata() {
        throw new AssertionError("Illegal constructor");
    }
    
    public static void main(String[] argv) {
        System.out.println(CLASSES);
        System.out.println();
        System.out.println(ALL_CLASSES);
    }
}
