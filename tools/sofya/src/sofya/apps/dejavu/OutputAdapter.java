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

package sofya.apps.dejavu;

import java.io.IOException;

import sofya.viewers.TestSelectionViewer;

/**
 * Adapter class which is responsible for the display of test selection
 * results and the writing of those results to a database file.
 *
 * @author Rogan Creswick
 * @author Alex Kinneer
 * @version 09/22/2004
 */
public class OutputAdapter {
    /** Name of the output file to be written. */
    private String fileName;
    /** Database tag associated with the output file to be written. */
    private String tag;
    /** Viewer used for immediate display to the console. */
    private TestSelectionViewer tsViewer;
    /** Handler for writing the test selection file to the database. */
    private TestSelectionHandler tsHandler;
    
    private OutputAdapter() { }
    
    /**
     * Standard constructor, initializes basic information required by the
     * output adapter.
     *
     * @param fileName Name of the output file to be written.
     * @param tag Database tag to be associated with the output file (can
     * be <code>null</code>).
     * @param totalTests Total number of tests from which test selection
     * may occur.
     */
    public OutputAdapter(String fileName, String tag, int totalTests) {
        this.fileName = fileName;
        this.tag = tag;
        tsHandler = new TestSelectionHandler(totalTests);
        tsViewer = new TestSelectionViewer(tsHandler);
    }
    
    /**
     * Adds test selection information about a method.
     *
     * @param methodName Name of the method for which test selection
     * data is being recorded for output.
     * @param selected Selection data object containing the test selection
     * data being recorded.
     */
    public void addSelected(String methodName, SelectionData selected) {
        tsHandler.setSelectedTests(methodName, selected.tests);
    }
    
    /**
     * Writes the test selection information to a database file.
     *
     * @throws IOException For any IO error which prevents successful
     * creation of the test selection file.
     */
    public void writeTestSelectionFile()
                throws IOException {
        tsHandler.writeTestSelectionFile(fileName, tag);
    }

    /**
     * Prints the output to standard output.
     *
     * @param format Display format to be used for the output.
     */
    public void print(int format) throws IOException {
        tsViewer.setOutputFormat(format);
        tsViewer.print();
    }
}

