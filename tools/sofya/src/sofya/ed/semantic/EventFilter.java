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
 * Abstract base class for all classes that wish to filter observable events
 * in some way. It is intended that these filters can be registered with
 * the {@link sofya.ed.SemanticEventTracer} as well as chained together to
 * filter and extract relevant events from the event stream online.
 *
 * @author Alex Kinneer
 * @version 06/08/2005
 */
public abstract class EventFilter implements EventListener {
    /** Initial number of slots in the array of listeners to which
        events can be dispatched. */
    private static final int INIT_LISTENER_STORAGE = 20;
    /** Increment by which the array of listeners is extended if the number
        of listeners which need to be registered with the filter exceeds
        the number of slots currently available in the array. */
    private static final int STORAGE_INCREMENT = 20;
    
    /** Registered event listeners. An array is used because events are
        dispatched to all listeners, and this tool will normally observe
        a large number of events. */
    protected EventListener[] listeners =
        new EventListener[INIT_LISTENER_STORAGE];
    /** Number of listeners currently registered. */
    protected int listenerCount = 0;
    
    /**
     * Registers a listener for observable events.
     *
     * @param listener Object which wishes to receive notifications of
     * events related to observables in the system.
     */
    public void addEventListener(EventListener listener) {
        if (listenerCount == listeners.length) {
            EventListener[] temp =
                new EventListener[listeners.length + STORAGE_INCREMENT];
            System.arraycopy(listeners, 0, temp, 0, listeners.length);
            listeners = temp;
        }
        listeners[listenerCount++] = listener;
    }
    
    /**
     * Unregisters a listener for observable events.
     *
     * @param listener Object which no longer wishes to receive notifications
     * of events related to observables in the system.
     */
    public void removeEventListener(EventListener listener) {
        listenerCount -= 1;
        if (listeners[listenerCount] == listener) {
            return;
        }
        
        for (int i = listenerCount - 1; i >= 0; i--) {
            if (listeners[listenerCount] == listener) {
                System.arraycopy(listeners, i + 1, listeners, i,
                                 listeners.length - 1 - i);
                return;
            }
        }
    }
    
    /**
     * Ensures that the array of listeners will be large enough to store
     * a listener at a given index.
     *
     * @param index Index which must be within the bounds of the array of
     * listeners. If the array is not currently of sufficient size for the
     * given index to be legal, the array will be extended such that it is.
     * Otherwise this method does nothing. Negative values will be ignored.
     */
    protected void ensureCapacity(int index) {
        if (listeners.length <= index) {
            EventListener[] temp = new EventListener[index + 1];
            System.arraycopy(listeners, 0, temp, 0, listeners.length);
            listeners = temp;
        }
    }
}