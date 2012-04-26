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

import gnu.trove.TLongObjectHashMap;

/**
 *
 *
 * @author Alex Kinneer
 * @version 08/09/2006
 */
public class ObjectBasedRouter extends EventSelectionFilter
        implements ChainedEventListener {
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

    /**
     * Factory class used to produce instances of this class on demand,
     * which are to be used to generate independent streams for subsets of
     * the events raised by the observed system.
     */
    private static class Factory implements ChainedEventListenerFactory {
        Factory() {
        }

        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            return new ObjectBasedRouter(parent, streamId, streamName);
        }
    }

    public ObjectBasedRouter() {
    }

    protected ObjectBasedRouter(ChainedEventListener parent,
            long streamId, String streamName) {
        this();
        this.parent = parent;
        this.streamId = streamId;
        this.streamName = streamName;
    }

    /**
     * Gets a factory for producing instances of this class on demand.
     *
     * @return A listener factory which produces instances of this class.
     */
    public static ChainedEventListenerFactory getFactory() {
        return new Factory();
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

    public void connectStream(long objId, EventListener listener) {
        objListeners.put(objId, listener);
    }

    public void releaseStream(long objId) {
        objListeners.remove(objId);
    }

    public void monitorContendEvent(ThreadData td, ObjectData od, MonitorData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.monitorContendEvent(td, od, md);
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od, MonitorData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.monitorAcquireEvent(td, od, md);
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.monitorPreReleaseEvent(td, od, md);
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.monitorReleaseEvent(td, od, md);
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.constructorEnterEvent(td, od, md);
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.constructorExitEvent(td, od, md, false);
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.instanceFieldAccessEvent(td, od, fd);
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.instanceFieldWriteEvent(td, od, fd);
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.virtualMethodEnterEvent(td, od, md);
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        long objectId = od.getId();

        EventListener listener = (EventListener) objListeners.get(objectId);
        if (listener == null) {
            return;
        }

        listener.virtualMethodExitEvent(td, od, md, false);
    }
}
