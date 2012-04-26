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

/**
 * A CFG transformer is used to apply a transformation (or extension) to
 * a control flow graph during the last phase of CFG construction.
 *
 * <p>Objects implementing this interface can be registered with the
 * {@link sofya.graphs.cfg.CFGBuilder}. The transformations will then
 * be executed, in order of addition, by the <code>CFGBuilder</code>
 * as the final phase of CFG construction.</p>
 *
 * @author Alex Kinneer
 * @version 09/29/2004
 */
public interface CFGTransformer {
    /**
     * Applies a transformation to a control flow graph.
     *
     * @param cfg Control flow graph to which the transformation will
     * be applied.
     *
     * @throws TransformationException If the transformation fails for
     * any reason.
     */
    public void transformCFG(CFG cfg) throws TransformationException;
}
