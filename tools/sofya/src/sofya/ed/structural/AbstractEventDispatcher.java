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

package sofya.ed.structural;

/**
 * <p>This is the abstract base class of all event dispatchers that produce
 * structural event streams.</p>
 *
 * @author Alex Kinneer
 * @version 03/16/2006
 */
public abstract class AbstractEventDispatcher {
    /** Flag indicating whether a dispatcher's own configuration is set and
        the dispatcher is ready to invoke the subject program. */
    protected static boolean dispatcherReady = false;

    /** Flag indicating whether subject is instrumented. Set to true once a
        connection has been made from the subject. */
    protected static volatile boolean isInstrumented = false;

    protected AbstractEventDispatcher() {
    }

    /*************************************************************************
     * <p>Performs setup and runs the subject; this is the main driving
     * method of a structural event dispatcher.</p>
     *
     * @throws SetupException If there is an error attempting to set up the
     * event dispatcher to run the subject.
     * @throws ExecException If an error occurs executing the subject. This
     * does not include exceptions thrown by the subject itself, only errors
     * in the event dispatcher while attempting to interface with the subject.
     */
    public abstract void startDispatcher();

    /** Exception that indicates an error occurred during instantiation of an
        event dispatcher. */
    public static class CreateException extends RuntimeException {
        private static final long serialVersionUID = 4374857291284587149L;
        
        /** Wrapped exception that is the original cause, if applicable. */
        private Throwable cause = null;

        /** Creates an instance with the given message. */
        public CreateException(String s) { super(s); }

        /** Creates an instance with the given message wrapping another
            exception. */
        public CreateException(String s, Throwable e) {
            super(s);
            cause = e;
        }

        /** Gets the exception that is the original source of the problem
            (may be <code>null</code>). */
        public Throwable getCause() {
            return cause;
        }
    }

    /** Exception that indicates an error occurred while preparing to execute
        a subject class. */
    public static class SetupException extends RuntimeException {
        private static final long serialVersionUID = 6595600175050774245L;

        /** Wrapped exception that is the original cause, if applicable. */
        private Throwable cause = null;

        /** Create an instance with the given message. */
        public SetupException(String s) { super(s); }

        /** Creates an instance with the given message wrapping another
            exception. */
        public SetupException(String s, Throwable e) {
            super(s);
            cause = e;
        }

        /** Gets the exception that is the original source of the problem
            (may be <code>null</code>). */
        public Throwable getCause() {
            return cause;
        }
    }

    /** Exception that indicates an error occurred in an event dispatcher while
        executing a subject class. */
    public static class ExecException extends RuntimeException  {
        private static final long serialVersionUID = 5672555773273220455L;

        /** Wrapped exception that is the original cause, if applicable. */
        private Throwable cause = null;

        /** Create an instance with the given message. */
        public ExecException(String s) { super(s); }

        /** Creates an instance with the given message wrapping another
            exception. */
        public ExecException(String s, Throwable e) {
            super(s);
            cause = e;
        }

        /** Gets the exception that is the original source of the problem
            (may be <code>null</code>). */
        public Throwable getCause() {
            return cause;
        }
    }

    /** Exception that indicates an error occurred while creating or writing
        a trace file. */
    public static class TraceFileException extends RuntimeException  {
        private static final long serialVersionUID = -7213132463763379719L;
        
        /** Wrapped exception that is the original cause, if applicable. */
        private Throwable cause = null;

        /** Create an instance with the given message. */
        public TraceFileException(String s) { super(s); }

        /** Creates an instance with the given message wrapping another
            exception. */
        public TraceFileException(String s, Throwable e) {
            super(s);
            cause = e;
        }

        /** Gets the exception that is the original source of the problem
            (may be <code>null</code>). */
        public Throwable getCause() {
            return cause;
        }
    }
}
