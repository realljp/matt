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

import java.util.*;
import java.io.*;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.*;

import gnu.trove.THashMap;

/**
 * This class performs basic validation of the input files
 * supplied to DejaVu. It is also responsible for correlating
 * classes and methods between the two versions (P and P')
 * for comparison, based on class and method names.
 *
 * @author cs562-03 dev team.
 * @author Alex Kinneer
 * @version 05/13/2005
 */
public final class InputParser {
    /** 'Prog' file for the old version of the subject program. */
    private String oldProgFile;
    /** 'Prog' file for the new version of the subject program. */
    private String newProgFile;

    /** Loader to retrieve graphs for the old version. */
    private GraphLoader graphSourceOld;
    /** Loader to retrieve graphs for the new version. */
    private GraphLoader graphSourceNew;

    /** List of classes to be analyzed in the old version. */
    private List<Object> oldClasses;
    /** List of classes to be analyzed in the new version. */
    private List<Object> newClasses;

    /** List of method names in the old version. */
    private String[] oldMethods;
    /** List of method names in the new version. */
    private String[] newMethods;

    /** Database tag for the old version. */
    private String oldTag;
    /** Database tag for the new version. */
    private String newTag;
    
    /** List of matching classes to traverse. */
    private ClassPair[] matchingClasses;
    
    /** List of methods which do not exist in both prog files. */
	@SuppressWarnings("unused")
	private String[] unmatchedMethods = null;

    /**
     * Default constructor is useless and this is a final class.
     */
    private InputParser() { }

    /**
     * Standard constructor, the validation of the inputs is
     * performed as part of object construction, after which
     * the object is effectively a data object from which
     * information can be retrieved.
     *
     * @param oldProgFile 'Prog' file for old version of program.
     * @param newProgFile 'Prog' file for new version of program.
     * @param oldTag Database tag for old version of program.
     * @param newTag Database tag for new version of program.
     * @param graphLoaderType Class of graph loader to be used
     * to retrieve graphs for the classes specified in the
     * 'prog' files.
     *
     * @throws BadFileFormatException If a database file is found
     * to be corrupted while attempting jnput validation.
     * @throws IOException For any other IO error reading files
     * required to perform validation.
     */
    public InputParser(String oldProgFile, String newProgFile,
                       String oldTag, String newTag,
                       Class graphLoaderType)
                       throws BadFileFormatException,
                              IOException {
        if ((!oldProgFile.endsWith(".prog"))
                || (!newProgFile.endsWith(".prog"))) {
            throw new IllegalArgumentException("Argument does not " +
                "refer to a .prog file");
        }
        this.oldProgFile = oldProgFile;
        this.newProgFile = newProgFile;
        this.oldTag = oldTag;
        this.newTag = newTag;
        try {
            this.graphSourceOld = (GraphLoader) graphLoaderType.newInstance();
            this.graphSourceNew = (GraphLoader) graphLoaderType.newInstance();
        }
        catch (InstantiationException e) {
            throw new IllegalArgumentException("Requested graph loader " +
                "class cannot be created: it is not a legal class for " +
                "object instantiation");
        }
        catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Requested graph loader " +
                "class cannot be created: its class definition " +
                "cannot be found");
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("Requested graph loader " +
                "class cannot be created: it does not implement the " +
                "GraphLoader interface");
        }

        matchClasses();
    }

    /**
     * Reads the contents of the 'prog' files for the two versions
     * and initializes various data members from the contents of
     * those files.
     *
     * @throws BadFileFormatException If an entry in a 'prog'
     * file is not a legal class name.
     * @throws IOException On any IO error that prevents a
     * 'prog' file from being read successfully.
     */
    @SuppressWarnings("unchecked")
    private void matchClasses() throws BadFileFormatException, IOException {
        // After calling this method, the following fields will be initialized:
        // oldClasses, newClasses, matchingClasses
        oldClasses = new ArrayList<Object>();
        newClasses = new ArrayList<Object>();
        
        List<ProgramUnit> unitList = new ArrayList<ProgramUnit>();
        Map<Object, Object> classes = new THashMap();
        
        Handler.readProgFile(oldProgFile, oldTag, unitList);
        int unitCount = unitList.size();
        Iterator unitIterator = unitList.iterator();
        for (int i = unitCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) unitIterator.next();
            
            int clCount = pUnit.classes.size();
            Iterator clIterator = pUnit.classes.iterator();
            for (int j = clCount; j-- > 0; ) {
                ClassInfo oldClass =
                    new ClassInfo((String) clIterator.next(), pUnit.location);
                oldClasses.add(oldClass);
                classes.put(oldClass.name, oldClass);
            }
        }
        
        List<Object> matches = new ArrayList<Object>(oldClasses.size());

        Handler.readProgFile(newProgFile, newTag, unitList);
        unitCount = unitList.size();
        unitIterator = unitList.iterator();
        for (int i = unitCount; i-- > 0; ) {
            ProgramUnit pUnit = (ProgramUnit) unitIterator.next();
            
            int clCount = pUnit.classes.size();
            Iterator clIterator = pUnit.classes.iterator();
            for (int j = clCount; j-- > 0; ) {
                ClassInfo newClass =
                    new ClassInfo((String) clIterator.next(), pUnit.location);
                newClasses.add(newClass);
                
                ClassInfo oldClass = (ClassInfo) classes.get(newClass.name);
                if (oldClass != null) {
                    matches.add(new ClassPair(
                        oldClass.name, oldClass.location, newClass.location));
                }
            }
        }
        
        matchingClasses =
            (ClassPair[]) matches.toArray(new ClassPair[matches.size()]);
    }
    
    /**
     * Matches methods in a class from the old program to
     * methods in the corresponding class in the new program.
     *
     * <p>Matching is performed using lexical comparison of
     * names. A list of unmatched methods is constructed
     * as a side-effect of this method.</p>
     *
     * @return A list of matching methods between the two
     * versions of the class, as an array.
     */
    private String[] matchMethods(){
        List<Object> unmatched = new ArrayList<Object>();
        List<Object> matched = new ArrayList<Object>(newMethods.length);
        
        for (int i = 0; i < newMethods.length; i++) {
            if (Arrays.binarySearch(oldMethods, newMethods[i]) >= 0) {
                matched.add(newMethods[i]);
            }
            else {
                unmatched.add(newMethods[i]);
            }
        }
        
        unmatchedMethods =
            (String[]) unmatched.toArray(new String[unmatched.size()]);
        return (String[]) matched.toArray(new String[matched.size()]);
    }

    /**
     * Gets the list of matched classes.
     *
     * @return The list of matching classes, as an array.
     */
    public ClassPair[] getClassPairs() {
        return matchingClasses;
    }

    /**
     * Builds the list of method pairings containing information
     * about the methods that must be traversed for the specified class.
     *
     * @param clazz Information about the class for which method
     * pairings should be returned.
     *
     * @return A list of {@link MethodPair} objects to be traversed
     * by the graph traverser.
     *
     * @throws FileNotFoundException If a database file required to
     * retrieve information about the methods cannot be found.
     * @throws EmptyFileException If a database file required to
     * retrieve information about the methods contains no data.
     * @throws BadFileFormatException If a database file required
     * to retrieve information about the methods is corrupted.
     * @throws MethodNotFoundException If an inconsistency between
     * database files for the two versions of the program prevent
     * information from being retrieved about a method.
     * @throws IOException For any IO error that prevents reading
     * of a database file required to retrieve information about
     * the methods.
     */
    public MethodPair[] getMethods(ClassPair clazz)
                        throws FileNotFoundException, EmptyFileException,
                               BadFileFormatException, MethodNotFoundException,
                               IOException {
        String className = clazz.name;

        graphSourceOld.setClass(className + ".java", oldTag);
        Arrays.sort(oldMethods = graphSourceOld.getMethodList());

        graphSourceNew.setClass(className + ".java", newTag);
        Arrays.sort(newMethods = graphSourceNew.getMethodList());

        String[] methods = matchMethods();
        
        // For each method in the method list, obtain the corresponding CFGs,
        // prepare the method pair object, and add it to the list.
        MethodPair[] methodSet = new MethodPair[methods.length];
        for (int i = 0; i < methods.length; i++) {
            methodSet[i] = new MethodPair(
                clazz,
                methods[i],
                graphSourceOld.getGraph(methods[i]),
                graphSourceNew.getGraph(methods[i])
              );
        }

        return methodSet;
    }
    
    private static class ClassInfo implements Comparable {
        public final String name;
        public final String location;
        
        private int hashCode;
        
        ClassInfo(String name, String location) {
            this.name = name;
            this.location = location;
            
            computeHashCode();
        }
        
        private void computeHashCode() {
            hashCode = 13;
            hashCode = (31 * hashCode) + name.hashCode();
        }
        
        public boolean equals(Object obj) {
            if (this == obj) return true;
            
            if (!(obj instanceof ClassInfo)) return false;
            
            ClassInfo ci = (ClassInfo) obj;
            return this.name.equals(ci.name);
        }
        
        public int hashCode() {
            return hashCode;
        }
        
        public int compareTo(Object obj) {
            if (this == obj) return 0;
            
            ClassInfo ci = (ClassInfo) obj;
            return this.name.compareTo(ci.name);
        }
    }
}


