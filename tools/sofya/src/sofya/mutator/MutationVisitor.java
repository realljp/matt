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

package sofya.mutator;

/************************************************************************
 * A visitor for mutations.
 *
 * <p>This is not an entirely traditional visitor; many of the visitable
 * classes have inheritance relationships with each other. The purpose
 * of this visitor is primarily to support specialization where it is
 * useful while still providing a generic visitation pattern otherwise.</p>
 *
 * @author Alex Kinneer
 * @version 10/04/2005
 */
public interface MutationVisitor {
    /**
     * Visit a mutation.
     *
     * @param m Mutation to visit.
     */
    void visit(Mutation m) throws MutationException;
    
    /**
     * Visit a mutation group.
     *
     * @param mg Mutation group to visit.
     * @param begin Used to indicate to this visitor whether this is the
     * beginning of the visitation. Since a mutation group encapsulates
     * other mutations, some visitors may want to execute setup actions
     * before visitation of member mutations begins. If the mutation
     * group visits its members (see
     * {@link MutationGroup#visitMembers(boolean)}), this method will
     * be called with this parameter set to <code>false</code> after
     * all the member mutations have been visited.
     */
    void visit(MutationGroup mg, boolean begin) throws MutationException;
    
    /**
     * Visit a groupable mutation.
     *
     * @param gm Groupable mutation to visit.
     */
    void visit(GroupableMutation gm) throws MutationException;
    
    /**
     * Visit a class scope mutation.
     *
     * @param cm Class mutation to visit.
     */
    void visit(ClassMutation cm) throws MutationException;
    
    /**
     * Visit a method scope mutation (method bytecode mutation).
     *
     * @param mm Method mutation to visit.
     */
    void visit(MethodMutation mm) throws MutationException;
}
