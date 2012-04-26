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

package sofya.apps;

import java.util.Arrays;
import java.io.IOException;

import sofya.apps.atomicity.*;
import sofya.base.MethodSignature;
import sofya.base.Utility;
import sofya.base.exceptions.BadFileFormatException;
import sofya.ed.semantic.SemanticEventDispatcher;
import sofya.ed.semantic.SemanticEventDispatcher.InternalException;
import sofya.ed.semantic.EDLHandler;
import sofya.ed.semantic.ThreadBasedSplitter;

/**
 * Runs a program and checks whether its methods satisfy the property of
 * atomicity.
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
public class AtomicityChecker {
    private static boolean objectSensitive = true;
    
    public static final boolean ENABLE_ESCAPE_DETECTION = true;
    public static final boolean ENABLE_HAPPENS_BEFORE = true;
    public static final boolean USE_LEMMA_5_2 = false;
    
    private static final boolean SIMPLE_OUTPUT = true;
    
    /**
     * Prints the usage message and exits.
     *
     * @param msg Targeted explanation of the usage error,
     * may be <code>null</code>.
     */
    private static void printUsage(String msg) {
        if (msg != null) System.err.println(msg);
        System.err.println("Usage:");
        System.err.println("java sofya.eg.atomicity.AtomicityChecker -md " +
            "<data_file> -main <main_class> [arg1 arg2 ...]");
        System.exit(1);
    }

    /**
     * Entry point for the atomicity checker.
     */
    public static void main(String[] argv) {
        if (argv.length < 4) {
            printUsage(null);
        }

        SemanticEventDispatcher ed = new SemanticEventDispatcher();
        String dataFile = null;
        long timeout = 0;

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
                else if ("-to".equals(curArg)) {
                    i += 1;
                    if (i < argv.length) {
                        try {
                            timeout = Long.parseLong(argv[i]);
                        }
                        catch (NumberFormatException e) {
                            printUsage("Timeout must be numeric value");
                        }
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

        DynamicEscapeDetector escapeDetector = (ENABLE_ESCAPE_DETECTION)
            ? new DynamicEscapeDetector()
            : null;
        HappenBeforeChecker hbChecker = (ENABLE_HAPPENS_BEFORE)
            ? new HappenBeforeChecker(escapeDetector)
            : null;
        MultiLocksetRaceDetector raceDetector = new MultiLocksetRaceDetector(
            escapeDetector, hbChecker, objectSensitive);
        EventClassifier classifier = new DefaultEventClassifier(
            objectSensitive, raceDetector, hbChecker);
        ResultCollector results = new ResultCollector();

        // The order is important here...
        if (ENABLE_ESCAPE_DETECTION) {
            ed.addEventListener(escapeDetector);
        }
        if (ENABLE_HAPPENS_BEFORE) {
            ed.addEventListener(hbChecker);
        }
        ed.addEventListener(raceDetector);
        ed.addEventListener(classifier);
        ed.addEventListener(new ThreadBasedSplitter(
            AutomataController.getFactory(classifier, results)));
        //ed.addEventListener(new sofya.ed.semantic.ConsoleTarget());

        Runtime runtime = Runtime.getRuntime();
        long startMem = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        try {
            ed.startDispatcher(timeout);
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
            System.err.println(e.getMessage() + ": " + cause.getMessage());
            System.exit(1);
        }
        long stopTime = System.currentTimeMillis();

        MethodSignature[] methods = results.getMethods();
        Arrays.sort(methods, new MethodSignature.NameComparator());
        System.out.println();
        System.out.println("== Results ==");
        for (int n = 0; n < methods.length; n++) {
            boolean isAtomic = results.get(methods[n]);
            if (!isAtomic) {
                System.out.println(methods[n]);
                if (!SIMPLE_OUTPUT) {
                    System.out.println("    [NOT ATOMIC]");
                }
            }
            else if (!SIMPLE_OUTPUT) {
                System.out.println(methods[n]);
                System.out.println("    [ATOMIC]");
            }
        }

        long elapsedTime = stopTime - startTime;
        long finishMem = runtime.totalMemory() - runtime.freeMemory();
        long maxMem = runtime.maxMemory();
        long usedMem = finishMem - startMem;
        
        System.out.println();
        System.out.println("  Time (hh:mm:ss): " + Utility.formatElapsedTime(
            elapsedTime) + " (" + elapsedTime + " ms)");
        System.out.println(" Available memory: " + maxMem);
        System.out.println(" Start memory use: " + startMem);
        System.out.println("Finish memory use: " + finishMem);
        System.out.println("  Memory consumed: " + usedMem +
            " (" + Math.round(((double) usedMem / maxMem) * 100) + "%)");
        System.out.println(" number of events: " + ed.getEventCount());
        System.out.println();
        
        //System.out.println(((ThreadBasedSplitter) mt.listeners[1])
        //    .getTraceErrors().size());
    }
}
