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

package sofya.ed.semantic;

import org.apache.bcel.generic.Type;

public interface AdaptiveEventSpecification extends EventSpecification {
    // TODO: Implement support for constrained EDL ('in'-conditions only)
    
    void removeAllEvents(String className);

    void addConstructorEntry(String className, Type[] argTypes);

    void removeConstructorEntry(String className, Type[] argTypes);

    void addConstructorExit(String className, Type[] argTypes);

    void removeConstructorExit(String className, Type[] argTypes);

    void addMethodEvent(String className, String methodName,
            Type[] argTypes, MethodAction mAct);

    void removeMethodEvent(String className, String methodName,
            Type[] argTypes, MethodAction mAct);

    void addFieldEvent(String fieldName, FieldType fType);

    void removeFieldEvent(String fieldName, FieldType fType);
}
