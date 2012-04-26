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

package sofya.graphs.cfg;

import java.util.*;
import java.io.*;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.ConfigurationError;
import sofya.graphs.GraphCache;
import sofya.graphs.irg.IRG;
import sofya.graphs.cfg.TypeInferenceAlgorithm.*;

import org.apache.bcel.generic.*;

import gnu.trove.THashSet;
import gnu.trove.THashMap;

/**
 * This class is the type inferring module used by the CFG builder. It is
 * intended for use in performing static type inference on thrown exceptions.
 *
 * @author Alex Kinneer
 * @version 03/09/2005
 */
class TypeInferrer {
    /** Type inference algorithm used for <strong>intra</strong>procedural
        type inference. */
    private TypeInferenceAlgorithm intraprocedural;
    /** Type inference algorithm used for <strong>inter</strong>procedural
        type inference. */
    private TypeInferenceAlgorithm interprocedural;
    /** Reference to the disk-backed CFG cache currently in use. */
    private GraphCache<CFG> cache;
    
    /** Interclass relation graph for the program under analysis, required
        for interprocedural analyses. */
    private IRG irg = null;
    /** List of classes under analysis, generated from a &apos;.prog&apos;
        file. */
    private List<String> classList = null;
    
    /** Records number of imprecise type inferences when performing
        combined type inference, may be useful for statistical
        purposes. */
    private int numImprecise = 0;

    /** Constant flag specifying that no special type inference is to be
        performed. */
    public static final int CONSERVATIVE = 1;
    /** Constant flag indicating that only flow-sensitive intraprocedural
        type inference is to be performed. */
    public static final int FLOW_SENSITIVE = 2;
    /** Constant flag indicating that only flow-insensitive interprocedural
        type inference is to be performed. */
    public static final int FLOW_INSENSITIVE = 3;
    /** Constant flag indicating that both intra and interprocedural
        type inference is to be performed. */
    public static final int COMBINED = 4;
    
    /** Flag indicating which level of type inference algorithm is set
        to be applied. */
    public static final int LEVEL = 2; //FLOW_SENSITIVE;
    
    /**************************************************************************
     * Creates a new type inferrer, which will only be usable if the
     * type inference level is set to conservative or flow-sensitive
     * intraprocedural.
     *
     * @param graphs Reference to a cache to be used for storing CFGs if
     * necessary.
     *
     * @throws IllegalStateException If the type inferrer is set to an
     * inference level which performs interprocedural analysis.
     */
    protected TypeInferrer(GraphCache<CFG> cache)
            throws IllegalStateException {
        if ((LEVEL == FLOW_INSENSITIVE) || (LEVEL == COMBINED)) {
            throw new IllegalStateException("Interprocedural " +
                "analysis requires use of '.prog' file");
        }
        if (cache == null) {
            throw new NullPointerException();
        }
        
        setup(null, cache);
    }
    
    /**************************************************************************
     * Creates a new type inferrer.
     *
     * @param progFile Name of the &apos;<code>.prog</code>&apos; file to be
     * used by this type inferrer.
     * @param tag Database tag associated with the
     * &apos;<code>.prog</code>&apos; file, if any.
     * @param builder Reference to the CFG builder currently in use.
     * @param cache Reference to the cache used to store CFGs.
     *
     * @throws FileNotFoundException If the specified &apos;.prog&apos; file
     * cannot be found.
     * @throws IOException If there is an error reading the specified
     * &apos;.prog&apos; file or the given tag does not exist.
     * @throws ClassFormatError If a class loaded for analysis during
     * interclass relation graph construction is invalid.
     */
    protected TypeInferrer(String progFile, String tag,
                           CFGBuilder builder, GraphCache<CFG> cache)
              throws FileNotFoundException, IOException,
                     ClassFormatError {
        if ((progFile == null) || (builder == null) || (cache == null)) {
            throw new NullPointerException();
        }
        
        if ((LEVEL == FLOW_INSENSITIVE) || (LEVEL == COMBINED)) {
            List<ProgramUnit> programUnits = new ArrayList<ProgramUnit>();
            Handler.readProgFile(progFile, tag, programUnits);
            
            classList = new ArrayList<String>();
            int size = programUnits.size();
            Iterator iterator = programUnits.iterator();
            for (int i = size; i-- > 0; ) {
                ProgramUnit pUnit = (ProgramUnit) iterator.next();
                classList.addAll(pUnit.classes);
            }
            
            irg = new IRG(classList);
        }
        setup(builder, cache);
    }
    
    /**************************************************************************
     * Creates a new type inferrer.
     *
     * @param classList List of classes to which type inference is to be
     * applied, conservative estimates are used where interactions with classes
     * not in this list occur.
     * @param builder Reference to the CFG builder currently in use.
     * @param cache Reference to the cache used to store CFGs.
     *
     * @throws IOException If there is an error constructing an IRG from
     * the class list (if necessary).
     */
    protected TypeInferrer(List<String> classList, CFGBuilder builder,
                GraphCache<CFG> cache) throws IOException {
        if ((classList == null) || (builder == null) || (cache == null)) {
            throw new NullPointerException();
        }
        if ((LEVEL == FLOW_INSENSITIVE) || (LEVEL == COMBINED)) {
            irg = new IRG(classList);
        }
        this.classList = classList;
        setup(builder, cache);
    }
    
    /**************************************************************************
     * Sets the graph list comprising the program on which type inference
     * is being performed; this is required for interprocedural analyses.
     *
     * @param classes List of classes constituting the program under analysis.
     * @param builder Reference to the control flow graph builder which is
     * creating the graphs on which type inference is being performed.
     */
    void setClassList(List<String> classes, CFGBuilder builder) {
        if (classList == null) {
            throw new NullPointerException();
        }
        this.classList = classes;
        setup(builder, cache);
    }

    /**************************************************************************
     * Prepares the type inferrer for use by initializing the type
     * inference algorithms as appropriate for the current type inference
     * level.
     */
    private void setup(CFGBuilder builder, GraphCache<CFG> cache) {
        switch (LEVEL) {
        case CONSERVATIVE:
            intraprocedural = new SimpleInference();
            break;
        case FLOW_SENSITIVE:
            intraprocedural = new FSIntraprocedural();
            break;
        case FLOW_INSENSITIVE:
            interprocedural = 
                new FIInterprocedural(irg, new THashSet(classList),
                                      builder);
            break;
        case COMBINED:
            intraprocedural = new FSIntraprocedural();
            interprocedural = 
                new FIInterprocedural(irg, new THashSet(classList),
                                      builder);
            break;
        default:
            throw new ConfigurationError("Invalid type inference level");
        }
        this.cache = cache;
    }

    /**************************************************************************
     * Infers the types of exceptions thrown by the instruction found at the
     * end of each of the given blocks.
     *
     * <p>The list of blocks passed to this method should only contain
     * <code>throws</code> and call blocks, or an exception will be thrown.</p>
     *
     * @param cfg Control flow graph on which the type inference is being
     * performed.
     * @param mg BCEL representation of the method on which type inference is
     * is being performed.
     * @param blocks List of basic blocks containing exception throwing
     * instructions upon which type inference is to be performed.
     * @param inferredTypeSets <strong>[out]</strong> Receives the results of
     * the type inference, where the keys are blocks for which the analysis
     * was peformed and the values are sets of exception types inferred for
     * those blocks.
     *
     * @throws TypeInferenceException If type inference on the method cannot
     * be completed for any reason.
     */
    @SuppressWarnings("unchecked")
    protected void inferTypes(CFG cfg, MethodGen mg, List<Object> blocks,
                             Map<Object, Object> inferredTypeSets)
                   throws TypeInferenceException {
        Map<Object, Object> precisionData = new THashMap();
        inferredTypeSets.clear();
        
        switch (LEVEL) {
        case CONSERVATIVE:
        case FLOW_SENSITIVE: {
            intraprocedural.inferTypes(mg, blocks,
                cfg.blockOffsetMap, inferredTypeSets, precisionData);
            Iterator throwers = blocks.iterator();
            while (throwers.hasNext()) {
                TypeResult tr = (TypeResult) precisionData.get(throwers.next());
                if (!tr.isPrecise) {
                    numImprecise++;
                }
            }
            break;
        }
        case FLOW_INSENSITIVE: {
            interprocedural.inferTypes(mg, blocks,
                cfg.blockOffsetMap, inferredTypeSets, precisionData);
            break;
        }
        case COMBINED: {
            intraprocedural.inferTypes(mg, blocks,
                cfg.blockOffsetMap, inferredTypeSets, precisionData);
            
            // We must collect all of the imprecise blocks together
            // so the interprocedural algorithm has all the information
            // it needs to operate correctly
            List<Object> imprecise = new ArrayList<Object>();
            Iterator throwers = blocks.iterator();
            while (throwers.hasNext()) {
                Block thrower = (Block) throwers.next();
                TypeResult result = (TypeResult) precisionData.get(thrower);
                if (!result.isPrecise) {
                    numImprecise++;
                    imprecise.add(thrower);
                    inferredTypeSets.remove(thrower);
                }
            }
            
            Map<Object, Object> newPrecisionData = new THashMap();
            interprocedural.inferTypes(mg, imprecise,
                cfg.blockOffsetMap,  inferredTypeSets, newPrecisionData);
            
            break;
        }
        default:
            throw new ConfigurationError("Invalid type inference level");
        }
    }
}
