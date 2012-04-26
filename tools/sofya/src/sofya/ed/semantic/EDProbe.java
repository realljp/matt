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

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import sofya.base.ProjectDescription;

/**
 * This class hosts various fields and trigger methods that are referenced
 * by probes inserted by the instrumentor, to support communication with
 * the JDI.
 *
 * @author Alex Kinneer
 * @version 08/09/2007
 */
public final class EDProbe {
    // We re-assign the error stream to the standard output stream so that
    // outputs by the system are seen in their original order. (We have to
    // use separate threads to relay the outputs in the module tracer, and
    // there is no way to force those threads to honor the original order
    // of outputs, which can lead to very strange disordered output. A
    // failing of the Jave exec model, really).
    static {
        System.setErr(System.out);
    }

    /** Field used to transmit very limited commands back to the subject
        instrumentation. */
    public static byte _inst$$zk183_$flag$ = 0;
    /** Field used by the instrumentation manager to transfer back the
        class loader used to load a class when requesting class bytes
        for adaptive instrumentation. */
    public static ClassLoader _inst$$zk183_$cld$ = null;
    /** Field used to insert special signal events into the event
        queue of the event dispatcher. */
    public static int _inst$$zk183_$sig$ = 0;
    /** Flag used to ensure that only one user code start event
        (see {@link sofya.ed.semantic.EventListener#executionStarted()})
        is dispatched. */
    public static boolean _inst$$zk183_$user_start$ = false;
    
    // Trigger methods for array element events
    public static final void _inst$$trigger_jdi$array$load$(boolean[] arr,
            boolean val, int idx) {
    }
    
    public static final void _inst$$trigger_jdi$array$store$(boolean[] arr,
            boolean oldVal, int idx, boolean newVal) {
    }

    public static final void _inst$$trigger_jdi$array$load$(byte[] arr,
            byte val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(byte[] arr,
            byte oldVal, int idx, byte newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(char[] arr,
            char val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(char[] arr,
            char oldVal, int idx, char newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(double[] arr,
            double val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(double[] arr,
            double oldVal, int idx, double newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(float[] arr,
            float val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(float[] arr,
            float oldVal, int idx, float newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(int[] arr,
            int val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(int[] arr,
            int oldVal, int idx, int newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(long[] arr,
            long val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(long[] arr,
            long oldVal, int idx, long newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(short[] arr,
            short val, int idx) {
    }
    public static final void _inst$$trigger_jdi$array$store$(short[] arr,
            short oldVal, int idx, short newVal) {
    }
    
    public static final void _inst$$trigger_jdi$array$load$(Object[] arr,
            Object val, int idx) {
    }
    
    public static final void _inst$$trigger_jdi$array$store$(Object[] arr,
            Object oldVal, int idx, Object newVal) {
    }
    
    /**
     * Used to reflectively invoke the main class for the monitored
     * application, which forces the probe to be loaded before any
     * monitored code can be loaded.
     *
     * <p>This is an ugly hack to work around a bug in the JDI. Namely,
     * the JDI violates the declared contract of the ClassPrepareEvent
     * by permitting execution of code in the loaded class prior to
     * dispatching the event to the JDI event queue. This means that
     * during initialization, user code may write to fields we want
     * to monitor before we have had a chance to request monitoring
     * of those fields in the SemanticEventDispatcher, since we must have a
     * reference to a loaded class to add event requests for the
     * fields of the class.</p>
     */
    public static void main(String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println("Internal error: missing launch arguments");
            System.exit(1);
        }

        BytecodeTransmitter commLink = new BytecodeTransmitter();
        commLink.connect(Integer.parseInt(argv[0]));
        String[] args = commLink.getLaunchArguments();
        _inst$$zk183_$sig$ = SemanticConstants.SIGNAL_CONNECTED;
        commLink.processRequests();

        Runtime rt = Runtime.getRuntime();
        System.out.println("Launching subject...");
        System.out.println("    Processors: " + rt.availableProcessors());
        System.out.println("    Max memory: " + rt.maxMemory());

        Class cl = Class.forName(argv[1]);
        Method clMain = cl.getMethod("main",
            new Class[]{ (new String[]{}).getClass() });
        try {
            clMain.invoke(null, new Object[]{args});
        }
        catch (InvocationTargetException e) {
            // Filter the reflective invocation stuff out of the
            // stack trace
            Throwable cause = e.getCause();
            StackTraceElement[] callStack = cause.getStackTrace();
            int len = callStack.length;
            int stopLen = len - 1;
            for (int i = stopLen; i >= 0; i--) {
                if (!callStack[i].getClassName()
                        .equals("sofya.ed.semantic.EDProbe")
                        && callStack[i].getMethodName().equals("main")) {
                    stopLen = i + 1;
                    break;
                }
            }
            
            StackTraceElement[] trimmedStack = new StackTraceElement[stopLen];
            System.arraycopy(callStack, 0, trimmedStack, 0, stopLen);
            cause.setStackTrace(trimmedStack);
            
            cause.printStackTrace();
        }
    }

    // Defunct, retained for possible future use if desired. This will
    // be totally superceded by the new JDI transport to come online
    // sometime in the not-too-distant future...
    @SuppressWarnings("unused")
    private static final String[] getLaunchArguments(String dbFile) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(ProjectDescription.dbDir +
                    File.separatorChar + dbFile)));
            short num = in.readShort();
            String[] args = new String[num];
            for (short i = 0; i < num; i++) {
                args[i] = in.readUTF();
            }
            return args;
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not read launch arguments");
            System.exit(1);
        }
        finally {
            if (in != null) {
                try { in.close(); } catch (IOException e) { }
            }
        }
        throw new Error();
    }
}
