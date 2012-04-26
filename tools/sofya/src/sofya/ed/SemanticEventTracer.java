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

package sofya.ed;

import java.io.IOException;

import sofya.base.exceptions.BadFileFormatException;
import sofya.ed.semantic.EDLHandler;
import sofya.ed.semantic.SemanticEventDispatcher;
import sofya.ed.semantic.SemanticEventDispatcher.InternalException;
import sofya.ed.semantic.ConsoleTarget;
import sofya.ed.semantic.TraceFileTarget;

/**
 * <p>A semantic event tracer collects binary trace files recording the events
 * specified in a module description file that were witnessed during
 * execution of a program.</p>
 *
 * @author Alex Kinneer
 * @version 06/12/2005
 */
public class SemanticEventTracer {
    private SemanticEventTracer() {
    }

    /**
     * Prints the usage message and exits.
     *
     * @param msg Targeted explanation of the usage error,
     * may be <code>null</code>.
     */
    private static void printUsage(String msg) {
        if (msg != null) System.err.println(msg);
        System.err.println("Usage:");
        System.err.println("java sofya.eg.ModuleTracer -md " +
            "<data_file> -main <main_class> [arg1 arg2 ...]");
        System.exit(1);
    }

    /**
     * Entry point for the module tracer.
     */
    public static void main(String[] argv) {
        if (argv.length < 4) {
            printUsage(null);
        }

        SemanticEventDispatcher ed = new SemanticEventDispatcher();
        String dataFile = null;
        boolean toConsole = false;

        int i = 0;
        for ( ; i < argv.length; i++) {
            String curArg = argv[i];
            if (curArg.startsWith("-")) {
                if ("-md".equals(curArg)) {
                    i += 1;
                    if (i < argv.length) {
                        dataFile = argv[i];
                    }
                    else {
                        printUsage("Data file name not provided");
                    }
                }
                else if ("-main".equals(curArg)) {
                    i += 1;
                    if (i < argv.length) {
                        ed.setMainClass(argv[i]);
                    }
                    else {
                        printUsage("Main class not specified");
                    }
                    break;
                }
                else if ("-cout".equals(curArg)) {
                    toConsole = true;
                }
            }
            else {
                break;
            }
        }

        if (dataFile == null) {
            printUsage("You must supply a module data file");
        }
        if (ed.getMainClass() == null) {
            printUsage("You must specify a main class");
        }

        for (++i; i < argv.length; i++) {
            ed.addArgument(argv[i]);
        }

        EDLHandler sdh = new EDLHandler();
        try {
            ed.setEDData(sdh.readDataFile(dataFile));
        }
        catch (BadFileFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (toConsole) {
            ed.addEventListener(new ConsoleTarget());
        }

        /*ed.addEventListener(new ThreadFilter(
            ObjectFilter.getFactory(
            TraceFileTarget.getFactory(ed.getEGData()))));

        eg.addEventListener(new ThreadFilter(
            TraceFileTarget.getFactory(eg.getEGData())));

        eg.addEventListener(new ObjectFilter(
            TraceFileTarget.getFactory(eg.getEGData())));*/

        try {
            ed.addEventListener(
                new TraceFileTarget("eg.tr", ed.getEDData()));
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        try {
            ed.startDispatcher();
        }
        catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (InternalException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace();
            }
            System.err.println(e.getMessage());
            System.exit(1);
        }

        //System.out.println(((ThreadFilter) mt.listeners[1]).getTraceErrors().size());
    }
}
