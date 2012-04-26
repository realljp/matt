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

package sofya.base;

import java.io.File;

/**
 * Handle to a cache directory in the Sofya database.
 *
 * <p>A cache handle is issued by {@link Handler#newCache}
 * and is used by other cache-related methods to uniquely identify the
 * cache on which an operation should be performed. The primary purpose
 * of this design is to reduce the probability that different clients will
 * inadvertently operate on the same cache.</p>
 *
 * @author Alex Kinneer
 * @version 11/29/2004
 */
public final class CacheHandle {
    /** File handle to the directory with which this cache handle
        is associated. */
    private File cacheDir = null;

    /** No argument constructor is invalid. */
    private CacheHandle() { }
    
    /**
     * Creates a new cache handle linked to a specified cache directory.
     *
     * @param cacheDir File handle to the cache directory with which
     * this cache handle is associated.
     */
    CacheHandle(File cacheDir) {
        if (cacheDir == null) {
            throw new NullPointerException();
        }
        this.cacheDir = cacheDir.getAbsoluteFile();
    }
    
    /**
     * Gets the file handle for the underlying cache directory.
     *
     * @return File handle to the cache directory with which this cache
     * handle is associated.
     */
    File getDirectory() {
        return cacheDir;
    }
    
    /**
     * Converts this cache handle to a string.
     *
     * @return A string containing the path to the underlying cache directory.
     */
    public String toString() {
        return cacheDir.toString() + File.separatorChar;
    }
}
