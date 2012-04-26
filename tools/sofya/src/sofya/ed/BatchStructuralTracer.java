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

//import sofya.base.Handler;
//import sofya.base.ProjectDescription;

//import java.io.*;
//import java.lang.reflect.*;
//import java.util.List;
//import java.util.ListIterator;
import java.util.StringTokenizer;
import sofya.ed.structural.*;

/**
 * This class provides the ability to execute a
 * {@link sofya.ed.structural.ProgramEventDispatcher} multiple times with
 * different subjects and/or configurations within the same JVM.
 *
 * <p>A batch file must be provided which specifies on each line a
 * distinct set of configuration parameters to be passed to the
 * <code>ProgramEventDispatcher</code>, including the name of the subject
 * class.</p>
 *
 * <p>Usage:<br><code>java sofya.ed.BatchStructuralTracer &lt;batch_file&gt;
 * </code></p>
 * 
 * <p><em>This class is broken and cannot be used (running it will have
 * no effect). It can be fixed, but doing so currently remains a very low
 * priority.</em></p>
 *
 * @author Alex Kinneer
 * @version 07/01/2005
 */
public final class BatchStructuralTracer {
    /** Filter which will be used as a library object. */
    @SuppressWarnings("unused")
    private static ProgramEventDispatcher dispatcher;

    /** No reason to instantiate this class. */
    private BatchStructuralTracer() { }

    /**
     * Prints the BatchFilter usage message and exits.
     */
    @SuppressWarnings("unused")
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("java sofya.inst.BatchFilter <batch_file>");
        System.exit(1);
    }

    /**
     * Entry point for BatchFilter. Reads the batch file and
     * executes the subjects with their associated configuration
     * parameters.
     *
     * @param argv Command line parameters to BatchFilter. One
     * argument is expected, the path to the batch file.
     */
    public static void main(String[] argv) {
        /*if (argv.length < 1) {
            printUsage();
        }

        boolean mtsEcho = false;

        int argi = 0;
        if (argv[0].equals("-mts")) {
            mtsEcho = true;
            argi += 1;
        }

        if (argi == argv.length) {
            System.err.println("ERROR: Must specify file or '-stdin'");
            System.exit(1);
        }

        LineNumberReader batchData = null;  // (LineNumberReader is buffered)
        if (argv[argi].equals("-stdin")) {
            batchData = new LineNumberReader(new InputStreamReader(System.in));
        }
        else {
            try {
                batchData = new LineNumberReader(new InputStreamReader(
                    new FileInputStream(argv[argi])));
            }
            catch (FileNotFoundException e) {
                System.err.println("ERROR: file '" + argv[argi] + "' could " +
                    "not be found");
                System.exit(1);
            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println("ERROR: I/O problem opening batch file");
                System.exit(1);
            }
        }

        // Use reflection to instantiate a filter of the requested type. This
        // is ultimately cleaner and makes supporting new types of filters
        // trivial (on the off chance that need should arise).
        String filterType = null;
        try {
            filterType = batchData.readLine();
            if (filterType == null) {
                System.err.println("ERROR: batch file is empty");
                System.exit(1);
            }
            Constructor ct = (Class.forName(filterType)).getConstructor(
                new Class[]{Class.forName("[Ljava.lang.String;"),
                            Class.forName("java.io.OutputStream"),
                            Class.forName("java.io.OutputStream"),
                            Class.forName("java.io.OutputStream")});
            // Create a dummy instance (the arguments are valid but the
            // class name is not). We'll just reconfigure it first thing
            // in the loop - and this makes the loop simpler.
            dispatcher = (ProgramEventDispatcher) ct.newInstance(
                new Object[]{new String[]{"-B", "foo"},
                             System.out,
                             System.out,
                             System.err});
        }
        catch (NoSuchMethodException e) {
            // Couldn't find the constructor
            System.err.println("ERROR: " + filterType + " is not a filter " +
                "class (or does not implement the necessary constructor)");
            System.exit(1);
        }
        catch (ClassCastException e) {
            // It has a matching constructor, but it's no filter
            System.err.println("ERROR: " + filterType + " is not a filter " +
                "class");
            System.exit(1);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("ERROR: I/O problem reading filter type from " +
                "batch file");
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: Failed to instantiate filter");
            System.exit(1);
        }

        try {
            String configLine;

            int testNum = 1;
            while ((configLine = batchData.readLine()) != null) {
                try {
                    if (mtsEcho) {
                        System.out.println(">>>>>>>>running test " + testNum);
                        configLine = "-o ../outputs/t" + testNum + " "
                            + configLine;
                    }

                    List badParams = dispatcher.configureDispatcher(
                        splitParams(configLine));
                    if (badParams.size() > 0) {
                        StringBuffer sb = new StringBuffer();
                        for (ListIterator li = badParams.listIterator();
                                li.hasNext(); ) {
                            sb.append("'");
                            sb.append((String) li.next());
                            sb.append("' ");
                        }
                        System.err.println("ERROR: Line " +
                            batchData.getLineNumber() + ", " +
                            "Unrecognized parameter(s): " + sb.toString());
                        System.exit(1);
                    }
                }
                catch (IllegalArgumentException e) {
                    System.err.println("ERROR: Line " +
                        batchData.getLineNumber() + ", " + e.getMessage());
                    System.exit(1);
                }
                catch (IllegalStateException e) { // Shouldn't happen
                    System.err.println("ERROR: Line " +
                        batchData.getLineNumber() + ", Attempted to " +
                        "reconfigure running filter");
                    System.exit(1);
                }

                try {
                    dispatcher.startDispatcher();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("ERROR: Line " +
                        batchData.getLineNumber() + ", Execution of filter " +
                        "failed");
                    System.exit(1);
                }

                if (mtsEcho) {
                    boolean copied = Handler.copyFile(
                        new File(ProjectDescription.dbDir + File.separatorChar +
                            dispatcher.getTraceFileName() + ".tr"),
                        new File(".." + File.separatorChar + "traces" +
                            File.separatorChar + String.valueOf(testNum - 1) +
                            ".tr"));
                    if (!copied) {
                        System.err.println("ERROR: Line " +
                            batchData.getLineNumber() + ", Failed to copy " +
                            "trace file");
                        System.exit(1);
                    }
                    testNum += 1;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("ERROR: Line " + batchData.getLineNumber() +
                ", I/O problem reading batch file");
            System.exit(1);
        }
        finally {
            // For sequence filter, this ensures the relay socket is closed if
            // necessary
            dispatcher.destroy();
        }*/
    }

    /**
     * Utility method to split an input line from the batch file
     * into an array of strings.
     *
     * @param params String containing a configuration line read
     * from the batch file.
     *
     * @return The list of parameters as separate strings.
     */
    @SuppressWarnings("unused")
    private static String[] splitParams(String params) {
        StringTokenizer stok = new StringTokenizer(params);
        String[] configParams = new String[stok.countTokens()];
        for (int i = 0; stok.hasMoreTokens(); i++) {
            configParams[i] = stok.nextToken();
        }
        return configParams;
    }
}
