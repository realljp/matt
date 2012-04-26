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
 * Interface which indicates than an event listener can be made aware that
 * it is associated with a subset of the stream of events being produced
 * by the system under observation.
 *
 * <p>This interface facilitates the chaining of filters and targets
 * (listeners which are terminal points in the event processing stream).
 * In particular, it enables a listener to query recursively for information
 * about filters which precede it in the processing chain so that it
 * can report the nature of the filter chain in some meaningful way.</p>
 *
 * @author Alex Kinneer
 * @version 02/07/2005
 */
public interface ChainedEventListener extends EventListener {
    /**
     * Gets the parent of this listener in the listener chain.
     *
     * @return The event listener from which this listener receives events.
     */
    public ChainedEventListener getParent();
    
    /**
     * Gets the unique identifier associated with this event stream.
     *
     * @return An value which should uniquely identify this event stream,
     * typically derived from some characteristic of the filtering
     * criteria being applied by the parent listener.
     */
    public long getStreamID();
    
    /**
     * Gets an informational name associated with this event stream.
     *
     * @return A string which provides some description of the nature of the
     * events handled by this string.
     */
    public String getStreamName();
}
