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

/**
 * Interface which indicates that an object is capable of acting as a factory
 * for producing event listeners which are aware of an association with
 * a filtered event stream produced by an {@link EventFilter}.
 *
 * <p>Classes which implement this factory interface are intended to be used
 * with event filters which split a single event stream into multiple event
 * streams based on some correlation of events with particular program
 * entities, such as threads or object instances. Such filters may need to be
 * able to create new listeners on demand, since it is often the case that
 * it is not possible to know a priori how many entities will be created
 * by the program.</p>
 *
 * @author Alex Kinneer
 * @version 06/10/2005
 */
public interface ChainedEventListenerFactory {
    /**
     * Creates a new event listener to be associated with a particular
     * program entity.
     *
     * @param parent Event listener from which the newly created chained
     * listener will receive events. It is left to implementors' discretion
     * whether to permit the passing of <code>null</code>, though it is
     * generally not recommended.
     * @param streamId Unique identifier associated with the program entity,
     * or in the more generic sense, the event stream, to which this listener
     * will be attached.
     * @param streamName Name of the entity or entity type associated with
     * the stream.
     */
    public ChainedEventListener createEventListener(ChainedEventListener parent,
            long streamId, String streamName)
            throws FactoryException;
}
