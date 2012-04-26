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

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import static sofya.ed.semantic.SemanticConstants.*;

/**
 * Filters an object-correlated event stream based on user specified
 * criteria.
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 * 
 * @deprecated This is a prototype solution that will likely be superceded
 * by a better design at a future time.
 */
public class ObjectEventFilter extends EventSelectionFilter
        implements ChainedEventListener {
    // TODO: Consider alternative solutions to this problem. This solution
    // does not fit well into the design philosophy of Sofya.
    
    /** Filtering criteria used to filter the stream. */
    protected List<Object> criteria = new ArrayList<Object>();
    /** Number of criteria currently in the filter chain, used for
     *  optimization. */
    protected int criteriaCount = 0;

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
     * Factory class used to produce instances of this class on demand.
     */
    private static class Factory implements ChainedEventListenerFactory {
        Factory() {
        }

        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            return new ObjectEventFilter(parent, streamId, streamName);
        }
    }

    /**
     * Creates a new object based filter with no active filtering
     * criteria (all events are retained by default).
     */
    public ObjectEventFilter() {
    }

    /**
     * Creates a new object based filter attached to a listener chain.
     * 
     * @param parent Parent listener in the processing chain.
     * @param streamId Id associated with the stream transformed by this
     * filter.
     * @param streamName Name associated with the stream transformed by
     * this filter.
     */
    protected ObjectEventFilter(ChainedEventListener parent,
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

    /**
     * Adds a filtering criterion to filter events from the stream.
     * 
     * @param criterion Filtering criterion to be added to the filter
     * chain.
     */
    public void addFilterCriterion(ObjectEventCriterion criterion) {
        criteria.add(criterion);
        criteriaCount++;
    }

    /**
     * Removes a filtering criterion.
     * 
     * @param criterion Removes a filtering criterion from the filter
     * chain.
     * 
     * @return <code>true</code> if a matching criterion was found,
     * <code>false</code> otherwise.
     */
    public boolean removeFilterCriterion(ObjectEventCriterion criterion) {
        Iterator iterator = criteria.iterator();
        for (int i = criteriaCount; i-- > 0; ) {
            if (iterator.next().equals(criterion)) {
                iterator.remove();
                criteriaCount--;
                return true;
            }
        }
        return false;
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_MONITOR_CONTEND, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorContendEvent(td, od, md);
        }
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_MONITOR_ACQUIRE, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorAcquireEvent(td, od, md);
        }
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_MONITOR_PRE_RELEASE,
                        td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorPreReleaseEvent(td, od, md);
        }
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_MONITOR_RELEASE, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorReleaseEvent(td, od, md);
        }
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_CONSTRUCTOR_ENTER, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].constructorEnterEvent(td, od, md);
        }
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_CONSTRUCTOR_EXIT, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].constructorExitEvent(td, od, md, false);
        }
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_GETFIELD, td, od, fd)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].instanceFieldAccessEvent(td, od, fd);
        }
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_PUTFIELD, td, od, fd)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].instanceFieldWriteEvent(td, od, fd);
        }
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_VMETHOD_ENTER, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].virtualMethodEnterEvent(td, od, md);
        }
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        if (criteriaCount > 0) {
            Iterator iterator = criteria.iterator();
            for (int i = criteriaCount; i-- > 0; ) {
                ObjectEventCriterion criterion =
                    (ObjectEventCriterion) iterator.next();
                if (!criterion.isMatch(EVENT_VMETHOD_EXIT, td, od, md)) {
                    return;
                }
            }
        }

        for (int i = 0; i < listenerCount; i++) {
            listeners[i].virtualMethodExitEvent(td, od, md, false);
        }
    }

    public void systemExited() {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].systemExited();
        }
    }
}
