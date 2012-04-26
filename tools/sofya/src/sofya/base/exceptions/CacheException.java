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

package sofya.base.exceptions;

/**
 * Defines an exception that indicates that something prevented a
 * cache or cache-related operation from being serviced.
 *
 * @author Alex Kinneer
 * @version 09/08/2004
 */
public class CacheException extends RuntimeException  {

    private static final long serialVersionUID = 7545886258333516416L;
    
    /** Wrapped exception that is the original cause, if applicable. */
    private Throwable cause = null;

    /** Create an instance with no message. */
    public CacheException() { super(); }

    /** Create an instance with the given message. */
    public CacheException(String s) { super(s); }

    /** Creates an instance with the given message wrapping another
        exception. */
    public CacheException(String s, Throwable cause) {
        super(s);
        this.cause = cause;
    }

    /** Gets the exception that is the original source of the problem
        (may be <code>null</code>). */
    public Throwable getCause() {
        return cause;
    }
}
