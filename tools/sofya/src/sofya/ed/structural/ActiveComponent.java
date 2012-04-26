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

import java.util.List;

/**
 * <p>An active component can be any component used by or attached to an event
 * listener that maintains a configurable internal state that may be sensitive
 * to the lifecycle of the event dispatcher, and that actively performs work
 * during delivery of the event stream.</p>
 *
 * <p>By implementing this interface, a component provides hooks that are used
 * by event dispatchers to supply system global resources and opportunities
 * for the component to configure itself and report whether it is ready for
 * the initiation of the event stream.</p>
 *
 * @author Alex Kinneer
 * @version 03/13/2006
 */
public interface ActiveComponent {
    /**
     * <p>Registers this component with the event dispatcher.</p>
     *
     * <p>This method is called each time an event dispatcher prepares to
     * initiate a new event stream, caused by a call to
     * {@link ProgramEventDispatcher#startDispatcher}.</p>
     *
     * @param edConfig The current configuration of system global resources
     * and settings that the component may (and should) use as appropriate.
     */
    void register(EventDispatcherConfiguration edConfig);

    /**
     * <p>Configures this component from command line parameters.</p>
     *
     * <p>This method provides a component the opportunity to define and process
     * command line parameters required to configure itself. Components
     * participate in a processing chain, initiated in Sofya-provided event
     * dispatchers by calling this method on the processing strategy. The
     * processing strategies provided by Sofya in turn invoke this method on
     * any listeners implementing this interface.</p>
     *
     * @param parameters A list of command line tokens that have not yet
     * been processed by any previous components.
     *
     * @return The list of parameters, with any recognized parameters and
     * associated values removed. This enables the chaining of parameter
     * processing.
     *
     * @throws ParameterValueAbsentException If a required value for a
     * recognized parameter is not provided.
     * @throws BadParameterValueException If a value for a recognized
     * parameter is invalid.
     */
    List<String> configure(List<String> parameters);

    /**
     * <p>Notifies this component to clear its configuration and reset any
     * internal state.</p>
     *
     * <p>Calls to this method are chained similary to the {@link #configure}
     * method. This method is invoked by the event dispatcher prior to
     * reconfiguration. Normally it should be used to clear any configuration
     * state to avoid using stale values.</p>
     *
     * @throws IllegalStateException If it is impossible for this component
     * to reset its configuration or internal state at the time of the
     * request.
     */
    void reset() throws IllegalStateException;
    
    /**
     * <p>Reports whether this component is ready for the event dispatcher
     * to begin dispatching events.</p>
     *
     * <p>Calls to this method are chained similarly to the {@link #configure}
     * method. The event dispatcher will fail with an error if a client
     * invokes {@link ProgramEventDispatcher#startDispatcher} when any
     * attached component returns <code>false</code> from this method.</p>
     *
     * @return <code>true</code> if this component is ready for the event
     * dispatcher to begin dispatching events, <code>false</code> otherwise.
     */
    boolean isReady();

    /**
     * <p>Notifies this component that its current lifecycle has expired and
     * that it should commit any stored state and release resources.</p>
     *
     * <p>Calls to this method are chained similary to the {@link #configure}
     * method. This method is invoked directly by a client of the event
     * dispatcher. Normally, it should be used to release any resources that
     * were required to persist over multiple runs of the event dispatcher.</p>
     */
    void release();
}
