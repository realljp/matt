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

import java.util.Map;
import java.util.TreeMap;

import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;

/**
 * Observable event filter which directs events to separate listeners for
 * each object in the monitored system. Events which are not specific
 * to individual object instances are discarded.
 *
 * @author Alex Kinneer
 * @version 12/04/2006
 */
public class ObjectBasedSplitter extends EventSelectionFilter
        implements ChainedEventListener, ErrorRecorder {
    /** Factory used to generate a new listener for the event stream associated
        with each object that gets created in the observed system. */
    private ChainedEventListenerFactory listenerFactory;

    /** Map which actually links each individual object with its associated
        event listener. */
    private TLongObjectHashMap objListeners = new TLongObjectHashMap();

    /** Parent event listener from which this filter is receiving
        events, if any. */
    private ChainedEventListener parent;
    /** ID assigned to this event stream, if any (this value should be set
        only if this filter was created by a factory). */
    private long streamId = -1;
    /** Name assigned to this event stream, if any (should be set
        only by a factory). */
    private String streamName = null;

    /** Flag which is set if an error occurs while creating a
        trace file. */
    private boolean errState = false;
    /** Stores any exception raised while creating a trace file. */
    private Exception err = null;

    /**
     * Factory class used to produce instances of this class on demand,
     * which are to be used to generate independent streams for subsets of
     * the events raised by the observed system.
     */
    private static class Factory implements ChainedEventListenerFactory {
        private ChainedEventListenerFactory myFactory;

        Factory(ChainedEventListenerFactory celf) {
            myFactory = celf;
        }

        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            return new ObjectBasedSplitter(myFactory, parent,
                streamId, streamName);
        }
    }

    protected ObjectBasedSplitter() { }

    /**
     * Creates a new object instance filter.
     *
     * @param celf Listener factory used to create the listeners associated
     * with observed objects.
     */
    public ObjectBasedSplitter(ChainedEventListenerFactory celf) {
        listenerFactory = celf;
    }

    /**
     * Creates a new object instance filter attached to an existing
     * filter chain.
     *
     * @param celf Listener factory used to create the listeners associated
     * with observed objects.
     * @param parent Event listener from which this object filter will
     * receive events.
     * @param streamId Identifier assigned to this event stream by the
     * factory creating this filter.
     * @param streamName Informational name assigned to this event stream
     * by the factory creating this filter.
     */
    protected ObjectBasedSplitter(ChainedEventListenerFactory celf,
            ChainedEventListener parent, long streamId, String streamName) {
        this(celf);
        this.parent = parent;
        this.streamId = streamId;
        this.streamName = streamName;
    }

    /**
     * Gets a factory for producing instances of this class on demand.
     *
     * @param celf Factory which the produced filters will use to generate
     * listeners for their own filter streams.
     *
     * @return A listener factory which produces instances of this class
     * to filter by object instance subsets of the events dispatched from the
     * observed system.
     */
    public static ChainedEventListenerFactory getFactory(
            ChainedEventListenerFactory celf) {
        return new Factory(celf);
    }

    public ChainedEventListener getParent() {
        return parent;
    }

    public long getStreamID() {
        return streamId;
    }

    public String getStreamName() {
        return streamName;
    }

    /**
     * Reports whether the filter is in an error state.
     *
     * <p>Note that errors raised while attempting to write to
     * individual filter streams for each object will be stored for each
     * such object if the listener associated with that object
     * implements the {@link ErrorRecorder} interface. Those errors
     * can be retrieved via {@link #getTraceErrors()}. This method
     * only reports whether the filter failed to instantiate a
     * listener for a given object.
     *
     * @return <code>true</code> if an error has occurred while trying
     * to create a listener.
     */
    public boolean inError() { return errState; }

    /**
     * Rethrows the originating exception if this filter is
     * in an error state; does nothing if in a normal state.
     *
     * @throws Exception If an error has been raised while trying to
     * create a listener for an object event stream.
     */
    public void rethrowError() throws Exception {
        if (errState) {
            throw err;
        }
    }

    /**
     * Gets the exception that put the filter in an error state.
     *
     * @return The first unrecoverable exception that put the filter
     * in an error state.
     */
    public Exception getError() {
        return err;
    }

    /**
     * Returns any exceptions that were raised by listeners associated
     * with individual objects.
     *
     * @return A map which relates objects to any exceptions raised while
     * attempting to write events to their associated listeners. Map keys will
     * be object IDs (as Longs), and values will be exception objects. No
     * mapping will exist for an object for which the listener does not
     * report any errors or does not implement the {@link ErrorRecorder}
     * interface. Thus under ideal circumstances, if all attached listeners
     * support error recording, the size of the returned map should be zero.
     */
    public Map<Long, Throwable> getTraceErrors() {
        Map<Long, Throwable> errs = new TreeMap<Long, Throwable>();

        TLongObjectIterator iterator = objListeners.iterator();
        for (int i = objListeners.size(); i-- > 0; ) {
            iterator.advance();
            Object listener = iterator.value();

            if (!(listener instanceof ErrorRecorder)) continue;

            ErrorRecorder l = (ErrorRecorder) listener;
            if (l.inError()) {
                errs.put(new Long(iterator.key()), l.getError());
            }
        }

        return errs;
    }

    private EventListener createListener(long objectId) {
        EventListener newListener = null;
        try {
            newListener = listenerFactory.createEventListener(
                this, objectId, "object");
        }
        catch (FactoryException e) {
            errState = true;
            err = e;
            return null;
        }

        objListeners.put(objectId, newListener);

        return newListener;
    }
    
    /**
     * Releases a listener attached to this splitter.
     * 
     * <p>A listener implementation may find it advisable to periodically
     * call {@link EventListener.ObjectData#isCollected()} to check
     * whether its mirrored object has been garbage collected. If an
     * object has been collected, it is not possible for any futher
     * events to be directed to that listener, so it should be released
     * to free memory. This is not done automatically so as to avoid
     * the performance overhead.</p>
     * 
     * <p><strong>Note:</strong> If the mirrored object associated with
     * a listener has not been garbage collected, and subsequent monitored
     * events occur on that object, the splitter will request a new event
     * listener for the object from the factory.</p>
     * 
     * @param objectId ID assigned to the object for which the listener
     * receives events; this will be the same identifier passed as
     * <code>streamId</code> to the factory used to create the listener.
     * 
     * @return The event listener associated with the given object ID,
     * or <code>null</code> if no matching listener was found.
     */
    public EventListener releaseListener(long objectId) {
        return (EventListener) objListeners.remove(objectId);
    }
    
    /**
     * Releases a listener attached to this splitter.
     * 
     * <p>This method serves the same purpose as
     * {@link #releaseListener(long)}, except that it accepts the actual
     * listener to be released. This method uses object identity,
     * <em>not</em> <code>equals</code>, to match the listener to be
     * removed. Note also that this method requires O(n) time to
     * find and remove a listener, so the constant time
     * {@link #releaseListener(long)} should be preferred whenever
     * possible.</p>
     * 
     * @param listener Event listener to be released from
     * association with this splitter.
     * 
     * @return <code>true</code> if the event listener was found and
     * removed, <code>false</code> otherwise.
     */
    public boolean releaseListener(EventListener listener) {
        int size = objListeners.size();
        TLongObjectIterator iterator = objListeners.iterator();
        for (int i = size; i-- > 0; ) {
            iterator.advance();
            if (iterator.value() == listener) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public void monitorContendEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.monitorContendEvent(td, od, md);
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.monitorAcquireEvent(td, od, md);
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.monitorPreReleaseEvent(td, od, md);
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.monitorReleaseEvent(td, od, md);
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener newListener = createListener(objectId);
        if (errState) {
            return;
        }

        newListener.constructorEnterEvent(td, od, md);
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        if (errState) return;

        long objectId = od.getId();

        EventListener newListener = createListener(objectId);
        if (errState) {
            return;
        }

        newListener.constructorExitEvent(td, od, md, false);
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.instanceFieldAccessEvent(td, od, fd);
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.instanceFieldWriteEvent(td, od, fd);
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.virtualMethodEnterEvent(td, od, md);
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        if (errState) return;

        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);

        if (listener == null) {
            listener = createListener(objectId);
            if (errState) {
                return;
            }
        }

        listener.virtualMethodExitEvent(td, od, md, false);
    }

    // We dispatch this event so that the end-of-file markers will be written
    // to the trace files
    public void systemExited() {
        TLongObjectIterator iterator = objListeners.iterator();
        for (int i = objListeners.size(); i-- > 0; ) {
            iterator.advance();
            ((EventListener) iterator.value()).systemExited();
        }
    }
}
