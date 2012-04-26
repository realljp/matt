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

/**
 * Observable event filter which directs events to separate listeners for
 * each thread in the monitored system. Events which are not specific
 * to individual threads are discarded.
 *
 * @author Alex Kinneer
 * @version 12/04/2006
 */
public class ThreadBasedSplitter extends EventSelectionFilter
        implements ChainedEventListener, ErrorRecorder {
    // The listener array is used directly to send events to trace file targets
    // for each thread, where the index into the array corresponds to
    // the thread ID. This will not pose any storage problems, since thread
    // IDs are assigned internally by the event dispatcher in incrementing
    // order starting from 1.

    /** Factory used to generate a new listener for the event stream associated
        with each thread that gets created in the observed system. */
    protected ChainedEventListenerFactory listenerFactory;

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

    /** The starting value for thread IDs assigned by the tracer. */
    private static final int firstThreadId = 1;

    /**
     * Factory class used to produce instances of this class on demand,
     * which are to be used to generate independent streams for subsets of
     * the events raised by the observed system.
     */
    private static class FilterFactory implements ChainedEventListenerFactory {
        private ChainedEventListenerFactory myFactory;

        FilterFactory(ChainedEventListenerFactory celf) {
            myFactory = celf;
        }

        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            return new ThreadBasedSplitter(myFactory, parent,
                streamId, streamName);
        }
    }

    protected ThreadBasedSplitter() { }

    /**
     * Creates a new thread filter.
     *
     * @param celf Listener factory used to create the listeners associated
     * with observed threads.
     */
    public ThreadBasedSplitter(ChainedEventListenerFactory celf) {
        listenerFactory = celf;
    }

    /**
     * Creates a new thread filter attached to an existing filter chain.
     *
     * @param celf Listener factory used to create the listeners associated
     * with observed threads.
     * @param parent Event listener from which this thread filter will
     * receive events.
     * @param streamId Identifier assigned to this event stream by the
     * factory creating this filter.
     * @param streamName Informational name assigned to this event stream
     * by the factory creating this filter.
     */
    private ThreadBasedSplitter(ChainedEventListenerFactory celf,
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
     * to filter by thread ID subsets of the events dispatched from the
     * observed system.
     */
    public static ChainedEventListenerFactory getFactory(
            ChainedEventListenerFactory celf) {
        return new FilterFactory(celf);
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
     * individual filter streams for each thread will be stored for each
     * such thread if the listener associated with that thread
     * implements the {@link ErrorRecorder} interface. Those errors
     * can be retrieved via {@link #getTraceErrors()}. This method
     * only reports whether the filter failed to instantiate a
     * listener for a given thread.
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
     * create a listener for a thread event stream.
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
     * with individual threads.
     *
     * @return A map which relates threads to any exceptions raised while
     * attempting to write events to their associated listeners. Map keys will
     * be thread IDs (as Integers), and values will be exception objects. No
     * mapping will exist for a thread for which the listener does not
     * report any errors or does not implement the {@link ErrorRecorder}
     * interface. Thus under ideal circumstances, if all attached listeners
     * support error recording, the size of the returned map should be zero.
     */
    public Map<Integer, Throwable> getTraceErrors() {
        Map<Integer, Throwable> errs = new TreeMap<Integer, Throwable>();

        int stopIndex = firstThreadId + listenerCount;
        for (int i = firstThreadId; i < stopIndex; i++) {
            if (!(listeners[i] instanceof ErrorRecorder)) {
                continue;
            }

            ErrorRecorder l = (ErrorRecorder) listeners[i];
            if (l.inError()) {
                errs.put(new Integer(i), l.getError());
            }
        }

        return errs;
    }

    private void createListener(int threadId) {
        ensureCapacity(threadId);

        try {
            listeners[threadId] = listenerFactory.createEventListener(
                this, threadId, "thread");
            listenerCount++;
        }
        catch (FactoryException e) {
            errState = true;
            err = e;
        }
    }
    
    /**
     * Releases a listener attached to this splitter.
     * 
     * <p>A listener implementation may find it advisable to periodically
     * check whether a thread is still alive. If the thread is no
     * longer alive, it is not possible for any futher events to be
     * directed to that listener, so it should be released to free memory.
     * This is not done automatically so as to avoid the performance
     * overhead. To determine whether a thread is alive, one of the
     * following techniques can be used:
     * <ul>
     * <li>Call {@link EventListener.ThreadData#getStatus()} and check
     * whether the thread's status is
     * {@link EventListener.ThreadStatus#ZOMBIE}.</li>
     * <li>Call {@link EventListener.ThreadData#getObject()}.{@link EventListener.ObjectData#isCollected()}.
     * A thread can only be garbage collected if it has exited.</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> If the mirrored thread associated with
     * a listener is still runnable, and subsequent monitored
     * events occur on that thread, the splitter will request a new event
     * listener for the thread from the factory.</p>
     * 
     * @param threadId ID assigned to the thread for which the listener
     * receives events; this will be the same identifier passed as
     * <code>streamId</code> to the factory used to create the listener.
     * 
     * @return The event listener associated with the given object ID,
     * or <code>null</code> if no matching listener was found.
     */
    public EventListener releaseListener(int threadId) {
        EventListener listener = listeners[threadId];
        listeners[threadId] = null;
        return listener;
    }
    
    /**
     * Releases a listener attached to this splitter.
     * 
     * <p>This method serves the same purpose as
     * {@link #releaseListener(int)}, except that it accepts the actual
     * listener to be released. This method uses object identity,
     * <em>not</em> <code>equals</code>, to match the listener to be
     * removed. Note also that this method requires O(n) time to
     * find and remove a listener, so the constant time
     * {@link #releaseListener(int)} should be preferred whenever
     * possible.</p>
     * 
     * @param listener Event listener to be released from
     * association with this splitter.
     * 
     * @return <code>true</code> if the event listener was found and
     * removed, <code>false</code> otherwise.
     */
    public boolean releaseListener(EventListener listener) {
        int size = listeners.length;
        for (int i = size - 1; i >= 0; i--) {
            if (listeners[i] == listener) {
                listeners[i] = null;
                return true;
            }
        }
        return false;
    }

    // We won't actually dispatch this event, since the existence of
    // the trace file implies the starting of the thread (obviously)
    public void threadStartEvent(ThreadData td) {
        if (errState) return;

        int threadId = td.getId();

        ensureCapacity(threadId);
        if (listeners[threadId] == null) {
            createListener(threadId);
        }
    }

    public void threadDeathEvent(ThreadData td) {
    }

    public void classPrepareEvent(ThreadData td, String className) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].classPrepareEvent(td, className);
    }

    public void monitorContendEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].monitorContendEvent(td, od, md);
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].monitorAcquireEvent(td, od, md);
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].monitorPreReleaseEvent(td, od, md);
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od, MonitorData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].monitorReleaseEvent(td, od, md);
    }

    public void newAllocationEvent(ThreadData td, NewAllocationData nad) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].newAllocationEvent(td, nad);
    }

    public void constructorCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].constructorCallEvent(td, cd);
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].constructorEnterEvent(td, od, md);
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].constructorExitEvent(td, od, md, false);
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticFieldAccessEvent(td, fd);
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].instanceFieldAccessEvent(td, od, fd);
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticFieldWriteEvent(td, fd);
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].instanceFieldWriteEvent(td, od, fd);
    }

    public void staticCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticCallEvent(td, cd);
    }

    public void virtualCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].virtualCallEvent(td, cd);
    }

    public void interfaceCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].interfaceCallEvent(td, cd);
    }

    public void callReturnEvent(ThreadData td, CallData cd,
            boolean exceptional) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].callReturnEvent(td, cd, exceptional);
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].virtualMethodEnterEvent(td, od, md);
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].virtualMethodExitEvent(td, od, md, false);
    }

    public void staticMethodEnterEvent(ThreadData td, MethodData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticMethodEnterEvent(td, md);
    }

    public void staticMethodExitEvent(ThreadData td, MethodData md, boolean exceptional) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticMethodExitEvent(td, md, false);
    }

    public void exceptionThrowEvent(ThreadData td, ExceptionData ed) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].exceptionThrowEvent(td, ed);
    }

    public void exceptionCatchEvent(ThreadData td, ExceptionData ed) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].exceptionCatchEvent(td, ed);
    }

    public void staticInitializerEnterEvent(ThreadData td, MethodData md) {
        if (errState) return;

        int threadId = td.getId();

        if ((threadId >= listeners.length) || (listeners[threadId] == null)) {
            createListener(threadId);
            if (errState) {
                return;
            }
        }

        listeners[threadId].staticInitializerEnterEvent(td,  md);
    }

    // We dispatch this event so that the end-of-file markers will be written
    // to the trace files
    public void systemExited() {
        int stopIndex = firstThreadId + listenerCount;
        for (int i = firstThreadId; i < stopIndex; i++) {
            listeners[i].systemExited();
        }
    }
}
