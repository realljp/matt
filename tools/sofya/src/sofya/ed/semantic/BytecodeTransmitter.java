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

import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import sofya.ed.semantic.InstrumentationManager;

/**
 * Provides a connection to the {@link SemanticEventDispatcher} used to
 * transmit the bytecode for classes to be redefined.
 * 
 * <p>A class is only loaded and transmitted to the event dispatcher
 * once; the event dispatcher maintains the subsequent state of the
 * class bytecode in a cache. This is because there is no way in a
 * classloader to request the current in-memory representation of the
 * bytecodes for a class -- it can only load the bytecode from the
 * disk file, which will always contain the static instrumentation.</p>
 * 
 * <p>This class is also responsible for receiving the launch arguments
 * to the object program, which is a more robust solution to overcome
 * the limitations of the command line launcher provided by the JDI.</p>
 * 
 * @author Alex Kinneer
 * @version 09/06/2006
 */
final class BytecodeTransmitter implements Runnable {
    /*  The usage sequence for this class is as follows:
     *    - The event dispatcher sends the link port as the argument
     *      to main in EDProbe
     *    - We connect to the event dispatcher on that port
     *    - We receive the launch arguments
     *    - The signal field is set that enables the event dispatcher
     *      to create and publish the instrumentation manager
     *    - The object program is actually launched using the given
     *      arguments
     *    - We listen for requests to load and transmit class bytecodes
     */
    
    /** Socket used to communicate with the event dispatcher. */
    private Socket socket;
    /** Input stream attached to the communication socket. */
    private DataInputStream in;
    /** Output stream attached to the communication socket. */
    private BufferedOutputStream out;

    /** Flag indicating whether the object program has been launched. */
    private boolean launched = false;

    BytecodeTransmitter() {
    }

    /**
     * Connects the transmitter to the event dispatcher.
     * 
     * @param port Port number on which to attempt to connect to the
     * event dispatcher.
     * 
     * @throws IOException On failure to connect to the event dispatcher
     * or obtain the communication streams.
     */
    void connect(int port) throws IOException {
        socket = new Socket("localhost", port);
        in = new DataInputStream(
            new BufferedInputStream(socket.getInputStream()));
        out = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * Receives the command-line launch arguments for the object program.
     * 
     * @return An array of strings containing the launch arguments to be
     * used to invoke the object program.
     * 
     * @throws IOException On failure to read the launch arguments from
     * the socket.
     */
    String[] getLaunchArguments() throws IOException {
        try {
            short size = in.readShort();
            String[] args = new String[size];
            for (short i = 0; i < size; i++) {
                args[i] = in.readUTF();
            }
            return args;
        }
        catch (IOException e) {
            e.printStackTrace();
            try { in.close(); } catch (IOException e2) { }
            try { socket.close(); } catch (IOException e2) { }
            throw e;
        }
    }

    /**
     * Launches the thread to handle requests to load and transmit
     * class bytecodes.
     */
    void processRequests() {
        if (!launched) {
            Thread commThread = new Thread(
                Thread.currentThread().getThreadGroup().getParent(),
                this, InstrumentationManager.THREAD_NAME);
            commThread.setDaemon(true);
            commThread.start();
            launched = true;
        }
    }

    /**
     * Processes requests to transmit class bytecodes to the
     * event dispatcher.
     */
    public void run() {
        //System.out.println("BytecodeTransmitter thread started");
        //System.out.println("  " + socket.isClosed());
        byte cmd = 0;
        while (true) {
            try {
                cmd = in.readByte();
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error reading command");
                continue;
                //return;
            }

            try {
                switch (cmd) {
                case 1:
                    break;
                case 2: {
//                     System.out.print("Request class bytes: ");
//                     System.out.flush();
                    String clName = in.readUTF();
//                     System.out.println(clName);
//                     System.out.flush();
                    boolean useLoader = in.readBoolean();
                    ClassLoader clLoader;
                    if (useLoader) {
//                         System.out.println("Using identified classloader");
//                         System.out.flush();
                        clLoader = EDProbe._inst$$zk183_$cld$;
                    }
                    else {
                        clLoader = ClassLoader.getSystemClassLoader();
                    }
                    if (clLoader == null) {
                                if (useLoader) System.err.println("WARN");
                        clLoader = ClassLoader.getSystemClassLoader();
                    }
                    try {
                        BufferedInputStream bis = new BufferedInputStream(
                            clLoader.getResourceAsStream(
                            clName.replace('.', '/') + ".class"));
                        byte[] buffer = new byte[4096];
                        int cnt = 0;
                        while ((cnt = bis.read(buffer, 0, 4096)) != -1) {
                            out.write(buffer, 0, cnt);
                        }
                        out.flush();
//                         System.out.println("Bytes sent");
//                         System.out.flush();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("Could not read bytes for '" +
                            clName + "' (I/O Exception):");
                        System.err.println("\t" + e.getMessage());
                    }
                    break;
                  }
                default:
                    System.err.println("Unrecognized command");
                    break;
                }
            }
            catch (IOException e) {
                System.err.println("Error processing command");
                continue;
            }
        }
    }
}
