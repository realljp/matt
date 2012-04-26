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

package sofya.apps.atomicity;

import java.util.ArrayList;

import sofya.base.MethodSignature;
import sofya.base.exceptions.SofyaError;
import sofya.ed.semantic.EventSelectionFilter;
import sofya.ed.semantic.ChainedEventListener;
import sofya.ed.semantic.ChainedEventListenerFactory;
import sofya.ed.semantic.FactoryException;
import static sofya.apps.AtomicityChecker.USE_LEMMA_5_2;

/**
 * Starts and stops the automata which check whether a method is atomic
 * on a particular invocation. Each automata controller should be
 * associated with a single thread.
 *
 * @author Alex Kinneer
 * @version 01/17/2007
 */
public class AutomataController extends EventSelectionFilter
        implements ChainedEventListener {
    /** Reference to the global event classifier. */
    private EventClassifier classifier;
    /** Result collector which records whether the atomicity property is
        satisfied for invoked methods. */
    private ResultCollector results;
    
    /** Stack that stores the currently running automata for the thread. */
    private ArrayList<Object> running = new ArrayList<Object>(20);
    // private ArrayList callStack = new ArrayList(20);
    
    /** Parent in the filter chain, should be an event classifier
        operating as a ThreadFilter. */
    private ChainedEventListener parent;
    /** Numeric ID associated with this event stream. */
    private long streamId;
    /** Descriptive name associated with this event stream. */
    private String streamName;
    
    /**
     * Factory class to produce automata controllers on demand, such as
     * by a {@link sofya.ed.semantic.ThreadFilter}.
     */
    private static class AutomataFactory
            implements ChainedEventListenerFactory {
        private final EventClassifier classifier;
        private final ResultCollector results;
        
        private AutomataFactory() {
            throw new AssertionError("Illegal constructor");
        }
        
        private AutomataFactory(EventClassifier classifier,
                ResultCollector results) {
            this.classifier = classifier;
            this.results = results;
        }
        
        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            return new AutomataController(classifier, results,
                parent, streamId, streamName);
        }
    }
    
    private AutomataController() {
        throw new AssertionError("Illegal constructor");
    }
    
    /**
     * Creates a new automata controller.
     *
     * @param classifier Global classifier to which automata created by
     * this controller can delegate classification if desired.
     * @param results Result collector used to record results of atomicity
     * checking on invoked methods.
     */
    public AutomataController(EventClassifier classifier,
            ResultCollector results) {
        this.classifier = classifier;
        this.results = results;
    }
    
    /**
     * Creates a new automata controller attached to a filter chain.
     *
     * <p>This constructor is used by the factory for this class.</p>
     *
     * @param classifier Global classifier to which automata created by
     * this controller can delegate classification if desired.
     * @param results Result collector used to record results of atomicity
     * checking on invoked methods.
     * @param parent Parent of this controller in the filter chain.
     * @param streamId Numeric ID associated with the event stream from
     * which this controller is consuming.
     * @param streamName Descriptive name associated with the event stream
     * from which this controller is consuming.
     */
    private AutomataController(EventClassifier classifier,
            ResultCollector results, ChainedEventListener parent,
            long streamId, String streamName) {
        this(classifier, results);
        this.parent = parent;
        this.streamId = streamId;
        this.streamName = streamName;
    }
    
    /**
     * Gets a factory for producing instances of this class attached to
     * a filter stream.
     *
     * @param classifier Global classifier to which automata created by
     * the generated controllers can delegate classification if desired.
     * @param results Result collector used by the generated controllers
     * to record results of atomicity checking on invoked methods.
     *
     * @return A factory which produces automata controllers attached
     * to the current event stream.
     */
    public static ChainedEventListenerFactory getFactory(
            EventClassifier classifier, ResultCollector results) {
        return new AutomataFactory(classifier, results);
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
    
    protected void startAutomata(MethodSignature mSig) {
        //System.out.println("Starting automata: " + mSig);
        
        RBAutomata automata = (USE_LEMMA_5_2)
            ? new RBAutomataExt52(classifier, mSig)
            : new RBAutomata(classifier, mSig);
        running.add(automata);
        // callStack.add(mSig);
        
        addEventListener(automata);
    }
    
    protected void stopAutomata(MethodSignature mSig) {
        //System.out.println("Stopping automata: " + mSig);
        
        RBAutomata automata = (USE_LEMMA_5_2)
            ? (RBAutomataExt52) running.remove(running.size() - 1)
            : (RBAutomata) running.remove(running.size() - 1);
        try {
            results.add(mSig, automata.isAccepting());
        }
        catch (NullPointerException e) {
            throw new SofyaError("Automata not started for: " + mSig);
        }
        
        removeEventListener(automata);
    }
    
    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorContendEvent(td, od, md);
        }
    }
    
    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorAcquireEvent(td, od, md);
        }
    }
    
    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorPreReleaseEvent(td, od, md);
        }
    }
    
    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].monitorReleaseEvent(td, od, md);
        }
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].staticFieldAccessEvent(td, fd);
        }
    }
    
    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].instanceFieldAccessEvent(td, od, fd);
        }
    }
    
    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].staticFieldWriteEvent(td, fd);
        }
    }
    
    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        for (int i = 0; i < listenerCount; i++) {
            listeners[i].instanceFieldWriteEvent(td, od, fd);
        }
    }
    
    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        startAutomata(md.getSignature());
    }
    
    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        stopAutomata(md.getSignature());
    }
    
    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        startAutomata(md.getSignature());
    }
    
    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        stopAutomata(md.getSignature());
    }
    
    public void staticMethodEnterEvent(ThreadData td, MethodData md) {
        startAutomata(md.getSignature());
    }
    
    public void staticMethodExitEvent(ThreadData td, MethodData md, boolean exceptional) {
        stopAutomata(md.getSignature());
    }
}
