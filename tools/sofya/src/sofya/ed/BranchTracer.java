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

import sofya.ed.structural.ProgramEventDispatcher;

/**
 * <p>A branch tracer collects branch coverage information and writes
 * it as a trace file.</p>
 *
 * @author Alex Kinneer
 * @version 11/16/2006
 */
public class BranchTracer {
    public BranchTracer() {
    }

    public static void main(String[] argv) {
        try {
            ProgramEventDispatcher ed =
                ProgramEventDispatcher.createBranchCoverageTracer(argv,
                    System.out, System.err, System.out);
            ed.startDispatcher();
            ed.release();
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(1);
        }
        catch (BadParameterValueException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (ParameterValueAbsentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (ProgramEventDispatcher.CreateException e) {
            Throwable source = e.getCause();
            if (source != null) source.printStackTrace();
            System.err.println("Error creating filter: " + e.getMessage());
            System.exit(1);
        }
        catch (ProgramEventDispatcher.SetupException e) {
            Throwable source = e.getCause();
            if (source != null) source.printStackTrace();
            System.err.println("Error during initialization: " +
                e.getMessage());
            System.exit(1);
        }
        catch (ProgramEventDispatcher.ExecException e) {
            Throwable source = e.getCause();
            if (source != null) source.printStackTrace();
            System.err.println("Error executing subject: " + e.getMessage());
            System.exit(1);
        }
        catch (ProgramEventDispatcher.TraceFileException e) {
            Throwable source = e.getCause();
            if (source != null) source.printStackTrace();
            System.err.println("Error writing trace file: " + e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unrecoverable exception was thrown!");
            System.exit(1);
        }
    }

    /*************************************************************************
     * Prints the branch coverage tracer usage message and exits.
     */
    private static void printUsage() {
        System.err.println("Usage:\njava sofya.ed.BranchTracer " +
            "[-port N] [-cp PATH] [-i] [-tl N] [-at]\n " +
            "[-trname <TraceName>] [-o OutputFile] [-ja <arg1> .. <__end>]\n" +
            " -<I|S|T|C|E|O> <classfileName> <arguments>");
        System.err.println("   -port <N> : Listen for subject trace " +
            "statements on port number N");
        System.err.println("   -cp <PATH> : Set CLASSPATH for subject to " +
            "PATH");
        System.err.println("   -i : Enable piping of stdin to the subject");
        System.err.println("   -tl <N> : Kill subject after N seconds");
        System.err.println("   -at : Append current trace to any existing " +
            "trace file");
        System.err.println("   -trname <TraceName> : Set trace file name " +
            "to <TraceName> (no extension)");
        System.err.println("   -o <OutputFile> : Redirect subject's output " +
            "to <OutputFile>");
        System.err.println("   -ja <arg1> .. <__end> : Pass arguments " +
            "<arg1> .. <argN> to Java (JVM)\n      invocation, use " +
            "\"__end\" token to indicate end of arguments");
        System.err.println("   -<I|S|T|C|E|O> : Any permutation of the " +
            "following : I-Ifs,S-Switches,T-Throws\n                        " +
            "C-Calls,E-Entries,O-Others");
    }
}
