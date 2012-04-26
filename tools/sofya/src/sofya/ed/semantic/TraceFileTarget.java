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

import java.io.*;

import sofya.base.Handler;
import static sofya.ed.semantic.SemanticConstants.*;

import gnu.trove.TObjectIntHashMap;

/**
 * Generates a trace file of observed events.
 *
 * @author Alex Kinneer
 * @version 08/15/2006
 */
public class TraceFileTarget implements ChainedEventListener, ErrorRecorder {
    /** Output stream attached to the trace file. */
    private DataOutputStream out;

    /** Parent event listener from which this trace file is receiving
        events, if any. */
    private ChainedEventListener parent;
    /** ID assigned to this event stream, if any (this value should be set
        only if this trace file was created by a factory). */
    private long streamId = -1;
    /** Name assigned to this event stream, if any (should be set
        only by a factory). */
    private String streamName = null;

    /** Flag which is set if an error occurs while writing to the
        trace file. */
    private boolean errState = false;
    /** Stores any exception raised during writing of the trace file. */
    private Exception err = null;

    /** Reverse string table, used to convert strings to indices for those
        events for which this is possible. */
    private TObjectIntHashMap revStringTable = new TObjectIntHashMap();

    /** Marker which is written to indicate the end of the file. */
    public static final int EOF = 0xffffffff;

    /** Singleton factory for producing instances of this class on demand. */
    private static TraceFactory factory = null;

    /**
     * Factory class used to produce instances of this class on demand,
     * which are to be used to generate independent trace files for subsets of
     * the events raised by the observed system.
     */
    private static class TraceFactory implements ChainedEventListenerFactory {
        private final SemanticEventData egd;

        private TraceFactory() {
            throw new AssertionError("Illegal constructor");
        }

        private TraceFactory(SemanticEventData egd) {
            this.egd = egd;
        }

        public ChainedEventListener createEventListener(
                ChainedEventListener parent, long streamId,
                String streamName) throws FactoryException {
            if (parent == null) {
                throw new FactoryException("Parent is null");
            }


            StringBuilder traceName = new StringBuilder();
            traceName.append(streamName);
            traceName.append(streamId);
            traceName.append(".eg.tr");

            ChainedEventListener nParent = parent;
            while (nParent != null) {
                String pStreamName = nParent.getStreamName();
                if (pStreamName == null) {
                    break;
                }

                traceName.insert(0, ".");
                traceName.insert(0, nParent.getStreamID());
                traceName.insert(0, nParent.getStreamName());

                nParent = nParent.getParent();
            }

            TraceFileTarget trace = null;
            try {
                trace = new TraceFileTarget(traceName.toString(), egd);
            }
            catch (IOException e) {
                throw new FactoryException("Trace creation failed", e);
            }

            trace.parent = parent;
            trace.streamId = streamId;
            trace.streamName = streamName;

            return trace;
        }
    }

    private TraceFileTarget() {
    }

    /**
     * Creates a new trace file.
     *
     * @param fileName Name of the trace file that will be generated.
     * @param egd Event dispatch data file produced by the instrumentor.
     *
     * @throws IOException If an error occurs when attempting to open
     * the trace file for writing.
     */
    public TraceFileTarget(String fileName, SemanticEventData egd) throws IOException {
        out = new DataOutputStream(
              new BufferedOutputStream(
                  Handler.openOutputFile(fileName, false)));
        writeStringTable(egd);
    }

    /**
     * Creates a new trace file.
     *
     * @param f Handle to the trace file that will be generated.
     * @param egd Event dispatch data file produced by the instrumentor.
     *
     * @throws IOException If an error occurs when attempting to open
     * the trace file for writing.
     */
    public TraceFileTarget(File f, SemanticEventData egd) throws IOException {
        out = new DataOutputStream(
              new BufferedOutputStream(
              new FileOutputStream(f)));
        writeStringTable(egd);
    }

    /**
     * Gets the factory for producing instances of this class on demand.
     *
     * @param egd Event dispatch data file produced by the instrumentor.
     *
     * @return A listener factory which produces instances of this class
     * to generate independent trace files for subsets of the events
     * dispatched from the observed system.
     */
    public static ChainedEventListenerFactory getFactory(SemanticEventData egd) {
        if ((factory == null) || (factory.egd != egd)) {
            factory = new TraceFactory(egd);
        }

        return factory;
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
     * Writes the string table to the head of the file.
     */
    private void writeStringTable(SemanticEventData egd) throws IOException {
        String[] stringTable = egd.getStringTable();
        int tableSize = stringTable.length;

        out.writeInt(tableSize);
        for (int i = 0; i < tableSize; i++) {
            String str = stringTable[i];
            out.writeUTF(str);
        }

        this.revStringTable = egd.getReverseStringTable();
    }

    /**
     * Reports whether this trace file target is in an error state.
     *
     * @return <code>true</code> if an error has occurred while trying
     * to generate the trace file.
     */
    public boolean inError() { return errState; }

    /**
     * Rethrows the originating exception if this trace file target is
     * in an error state; does nothing if in a normal state.
     *
     * @throws Exception If an error has been raised during the trace
     * file generation.
     */
    public void rethrowError() throws Exception {
        if (errState) {
            throw err;
        }
    }

    /**
     * Gets the exception that put the trace file target in an error state.
     *
     * @return The first unrecoverable exception occurred while trying to
     * generate the trace file.
     */
    public Exception getError() {
        return err;
    }

    public void systemStarted() {
    }

    public void executionStarted() {
    }

    public void threadStartEvent(ThreadData td) {
        if (errState) return;

        try {
            out.writeInt(EVENT_THREAD_START);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void threadDeathEvent(ThreadData td) {
        if (errState) return;

        try {
            out.writeInt(EVENT_THREAD_DEATH);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void classPrepareEvent(ThreadData td, String className) {
        if (errState) return;

        try {
            // Not interested in this event
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
             MonitorData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_MONITOR_CONTEND);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
             MonitorData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_MONITOR_ACQUIRE);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
             MonitorData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_MONITOR_PRE_RELEASE);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
             MonitorData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_MONITOR_RELEASE);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void newAllocationEvent(ThreadData td, NewAllocationData nad) {
        if (errState) return;

        try {
            out.writeInt(EVENT_NEW_OBJ);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(nad.getNewAllocationClass()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void constructorCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_CONSTRUCTOR);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(cd.getRawCalledSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_CONSTRUCTOR_ENTER);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        if (errState) return;

        try {
            out.writeInt(EVENT_CONSTRUCTOR_EXIT);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_GETSTATIC);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeUTF(fd.getFullName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_GETFIELD);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeUTF(fd.getFullName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_PUTSTATIC);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeUTF(fd.getFullName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_PUTFIELD);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeUTF(fd.getFullName());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_STATIC_CALL);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(cd.getRawCalledSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void virtualCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_VIRTUAL_CALL);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(cd.getRawCalledSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void interfaceCallEvent(ThreadData td, CallData cd) {
        if (errState) return;

        try {
            out.writeInt(EVENT_INTERFACE_CALL);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(cd.getRawCalledSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void callReturnEvent(ThreadData td, CallData cd,
            boolean exceptional) {
        if (errState) return;

        try {
            out.writeInt(EVENT_CALL_RETURN);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(cd.getRawCalledSignature()));
            out.writeByte((byte) (exceptional ? 1 : 0));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_VMETHOD_ENTER);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        if (errState) return;

        try {
            out.writeInt(EVENT_VMETHOD_EXIT);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeLong(od.getId());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticMethodEnterEvent(ThreadData td, MethodData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_SMETHOD_ENTER);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticMethodExitEvent(ThreadData td, MethodData md, boolean exceptional) {
        if (errState) return;

        try {
            out.writeInt(EVENT_SMETHOD_EXIT);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void exceptionThrowEvent(ThreadData td, ExceptionData ed) {
        if (errState) return;

        try {
            out.writeInt(EVENT_THROW);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeUTF(ed.getType());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void exceptionCatchEvent(ThreadData td, ExceptionData ed) {
        if (errState) return;

        try {
            out.writeInt(EVENT_CATCH);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeUTF(ed.getType());
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void staticInitializerEnterEvent(ThreadData td, MethodData md) {
        if (errState) return;

        try {
            out.writeInt(EVENT_STATIC_INIT_ENTER);
            out.writeInt(td.getId());
            out.writeUTF(td.getName());
            out.writeInt(revStringTable.get(md.getRawSignature()));
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }
    }

    public void systemExited() {
        if (!errState) {
            try {
                out.writeInt(EOF);
            }
            catch (Exception e) {
                errState = true;
                err = e;
            }
        }

        try {
            out.flush();
            out.close();
        }
        catch (Exception e) {
            errState = true;
            err = e;
        }

        if (errState) {
            System.err.println("Error generating trace file:");
            err.printStackTrace();
        }
    }
}
