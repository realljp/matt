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

/**
 * A shutdown hook that is only run from SocketProbeAlt (the synthetically
 * renamed copy of {@link SocketProbe} used to capture trace information
 * from an instrumented <code>SocketProbe</code> when the subject program is
 * Galileo or another Sofya). Its purpose is to ensure that the trace
 * information collected from the monitored <code>SocketProbe</code> is
 * transmitted to the {@link ProgramEventDispatcher} even in versions of
 * Galileo or Sofya that do not provide for this by having the instrumented
 * <code>SocketProbe</code> call <code>SocketProbeAlt.finish</code> at
 * the end of its own <code>finish</code> method.
 *
 * <p>The <code>finish</code> method itself is guarded by a synchronized
 * flag so that it can only execute once, thus avoiding any errant
 * behavior in versions of Sofya that do support the chaining of the
 * <code>SocketProbe.finish</code> call to the
 * <code>SocketProbeAlt.finish</code> call.</p>
 *
 * @author Alex Kinneer
 * @version 11/08/2006
 */
final class ProbeShutdownHook extends Thread {
    /** Reference to the probe implementation class; this should always be
        SocketProbeAlt. */
    private SocketProbeImpl probe;
    /** The thread that actually starts this shutdown hook, which may be
        VM-dependent. The reference is provided to the SocketProbe so that
        it won't attempt to wait on the thread, which may in turn wait
        on this thread (resulting in deadlock). */
    private Thread parent;

    private ProbeShutdownHook() {
        throw new AssertionError("Illegal constructor");
    }

    ProbeShutdownHook(SocketProbeImpl probe) {
        super("SocketProbeShutdownHook");
        this.probe = probe;
    }

    public void start() {
        parent = Thread.currentThread();
        super.start();
    }

    public void run() {
        System.out.println("Running shutdown hook");
        probe.finish(parent);
    }
}
