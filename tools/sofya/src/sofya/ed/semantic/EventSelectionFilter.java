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
 * Event filter which provides null implementations of all of the
 * {@link EventListener} methods, thus discarding the events. This
 * is intended as a convenience for subclasses, which can then
 * selectively override to respond to the events of interest.
 *
 * @author Alex Kinneer
 * @version 08/15/2006
 */
public abstract class EventSelectionFilter extends EventFilter {

    // Null implementation of the interface

    public void systemStarted() {
    }

    public void executionStarted() {
    }

    public void threadStartEvent(ThreadData td) {
    }

    public void threadDeathEvent(ThreadData td) {
    }

    public void classPrepareEvent(ThreadData td, String className) {
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
    }

    public void newAllocationEvent(ThreadData td, NewAllocationData nad) {
    }

    public void constructorCallEvent(ThreadData td, CallData cd) {
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
    }

    public void staticCallEvent(ThreadData td, CallData cd) {
    }

    public void virtualCallEvent(ThreadData td, CallData cd) {
    }

    public void interfaceCallEvent(ThreadData td, CallData cd) {
    }

    public void callReturnEvent(ThreadData td, CallData cd,
            boolean exceptional) {
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
    }

    public void staticMethodEnterEvent(ThreadData td, MethodData md) {
    }

    public void staticMethodExitEvent(ThreadData td, MethodData md, boolean exceptional) {
    }

    public void exceptionThrowEvent(ThreadData td, ExceptionData ed) {
    }

    public void exceptionCatchEvent(ThreadData td, ExceptionData ed) {
    }

    public void staticInitializerEnterEvent(ThreadData td, MethodData md) {
    }

    public void systemExited() {
    }
}
