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

import java.io.FileNotFoundException;
import java.io.IOException;

import sofya.base.exceptions.*;
import sofya.graphs.Edge;
import sofya.tools.th.*;

import gnu.trove.TIntArrayList;

/**
 * Test mapper which performs a mapping from basic blocks selected
 * by DejaVu to the tests that hit those blocks.
 *
 * @author Alex Kinneer
 * @version 11/30/2004
 */
public class BlockTestMapper extends TestMapper {
    /** Loader used to read the test history file for the program. */
    private TestHistoryHandler thHandler = new TestHistoryHandler();
    
    /**
     * Standard constructor, initializes the edge mapper with the
     * given test history file.
     *
     * @param histFile Path to the test history file for the
     * program, either absolute or relative to the current working
     * directory.
     *
     * @throws FileNotFoundException If the specified test history
     * file cannot be found.
     * @throws BadFileFormatException If the test history file is
     * corrupted.
     * @throws IOException For any other IO error which prevents
     * the test history file from being read successfully.
     */
    public BlockTestMapper(String histFile) throws FileNotFoundException,
                                                   BadFileFormatException,
                                                   IOException {
        thHandler.readTestHistoryFile(histFile);
    }
    
    public SelectionData selectTests(String methodName, Edge[] dangerousEdges)
                         throws MethodNotFoundException {
        TestHistory th = thHandler.getTestHistory(methodName);
        int maxTestID = th.getHighestTestID();
        TIntArrayList testList = new TIntArrayList();
        
        for (int i = 0; i < dangerousEdges.length; i++) {
            //int predBlockID = dangerousEdges[i].getPredNodeID();
            int succBlockID = dangerousEdges[i].getSuccNodeID();

            for (int j = 0; j < maxTestID; j++) {
                if (/*th.query(predBlockID, j) &&*/ th.query(succBlockID, j)) {
                    testList.add(j + 1);
                }
            }
        }

        SelectionData sd = new SelectionData();
        if (testList.size() > 0) {
            sd.tests = testList.toNativeArray();
        }
        else {
            sd.tests = new int[0];
        }
        
        return sd;
    }
                                  
    public int getTotalNumberOfTests() {
        try {
            return thHandler.getTestHistory(
                (thHandler.getMethodList())[0]).getHighestTestID();
        }
        catch (MethodNotFoundException shouldnotHappen) {
            // TestHistoryHandler should refuse to load an empty file
            throw new SofyaError("Unable to determine total number of tests");
        }
    }
}
