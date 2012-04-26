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

import java.io.PrintStream;
import java.text.NumberFormat;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.ObjectType;

/**
 * Defines general utility methods that belong to no class in particular.
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
public final class Utility {
    /** The utility class need not be instantiated. */
    private Utility() {
        throw new AssertionError("Illegal constructor");
    }

    /**
     * Converts a BCEL <code>Type</code> object to its corresponding
     * <code>java.lang.Class</code>.
     *
     * @param t BCEL type object to be converted.
     *
     * @return The class object corresponding to the given BCEL type.
     *
     * @throws ClassNotFoundException If the corresponding class cannot be
     * found by the classloader (most often because it is not on the
     * classpath), or if the BCEL type is <code>Type.UNKNOWN</code>.
     */
    public static final Class typeToClass(Type t)
            throws ClassNotFoundException {
        switch (t.getType()) {
        case Constants.T_BOOLEAN:
            return Boolean.TYPE;
        case Constants.T_CHAR:
            return Character.TYPE;
        case Constants.T_FLOAT:
            return Float.TYPE;
        case Constants.T_DOUBLE:
            return Double.TYPE;
        case Constants.T_BYTE:
            return Byte.TYPE;
        case Constants.T_SHORT:
            return Short.TYPE;
        case Constants.T_INT:
            return Integer.TYPE;
        case Constants.T_LONG:
            return Long.TYPE;
        case Constants.T_VOID:
            return Void.TYPE;
        case Constants.T_ARRAY:
            return Class.forName(t.getSignature().replace('/', '.'));
        case Constants.T_OBJECT:
            return Class.forName(((ObjectType) t).getClassName());
        default:
            throw new ClassNotFoundException();
        }
    }

    /**
     * Converts an array of BCEL <code>Type</code> objects to their
     * corresponding <code>java.lang.Class</code> objects.
     *
     * @param ts BCEL type objects to be converted.
     *
     * @return The class objects corresponding to the given BCEL types.
     *
     * @throws ClassNotFoundException If a corresponding class cannot be
     * found by the classloader (most often because it is not on the
     * classpath), or if a BCEL type is <code>Type.UNKNOWN</code>.
     */
    public static final Class[] typesToClasses(Type[] ts)
            throws ClassNotFoundException {
        int size = ts.length;
        Class[] classes = new Class[size];
        for (int i = 0; i < size; i++) {
            classes[i] = typeToClass(ts[i]);
        }
        return classes;
    }
    
    /**
     * Formats an elapsed time expressed in milliseconds to the form
     * &quot;hh:mm:ss.dd&quot; (that is, seconds are expressed to
     * two decimal places).
     * 
     * @param elapsedMillis Length of elapsed time.
     * 
     * @return The length of time, in the format &quot;hh:mm:ss.dd&quot;.
     */
    public static final String formatElapsedTime(long elapsedMillis) {
        double seconds = elapsedMillis / 1000.0; 
        
        int minutes = (int) (seconds / 60);
        seconds -= (minutes * 60);
        
        int hours = minutes / 60;
        minutes -= (hours * 60);
        
        NumberFormat formatter = NumberFormat.getInstance();
        formatter.setGroupingUsed(false);
        formatter.setMaximumFractionDigits(2);
        
        return ((hours < 10) ? "0" : "") + hours + ":" +
            ((minutes < 10) ? "0" : "") + minutes + ":" +
            ((seconds < 10.0) ? "0" : "") + formatter.format(seconds);
    }
    
    /**
     * Prints the stack trace for the current thread.
     * 
     * @param out Stream to which to print the stack trace.
     * @param header User-supplied information that should be
     * written as a header to the stack trace (can be <code>null</code>).
     */
    public static final void printStackTrace(PrintStream out, String header) {
        Thread curThread = Thread.currentThread();
        out.println("====================");
        out.println(curThread);
        if (header != null) {
            out.println(header);
        }
        out.println("--------------------");
        StackTraceElement[] stack = curThread.getStackTrace();
        int size = stack.length;
        for (int i = 0; i < size; i++) {
            out.println("    " + stack[i].toString());
        }
    }

    /**
     * Tests whether the string argument can be parsed as a signed decimal
     * integer in radix 10, as described for
     * <code>java.lang.Integer.parseInt(String)</code>. If so, the
     * resulting integer value is returned as the <code>value</code> of
     * the integer pointer argument.
     *
     * @param s String to test for parsability as an <code>int</code>.
     * @param resultPtr Integer pointer object into which the parsed
     * result will be written, if the string is parsable as an
     * <code>int</code>.
     * 
     * @return <code>true</code> if the given string can be parsed as
     * a radix 10 <code>int</code>, <code>false</code> otherwise.
     */
    public static boolean isParsableInt(String s, IntegerPtr resultPtr) {
        return isParsableInt(s, 10, resultPtr);
    }
    
    /**
     * Tests whether the string argument can be parsed as a signed decimal
     * integer in the radix given by the second argument, as described for
     * <code>java.lang.Integer.parseInt(String, int)</code>. If so, the
     * resulting integer value is returned as the <code>value</code> of
     * the integer pointer argument.
     *
     * @param s String to test for parsability as an <code>int</code>.
     * @param resultPtr Integer pointer object into which the parsed
     * result will be written, if the string is parsable as an
     * <code>int</code>.
     * 
     * @return <code>true</code> if the given string can be parsed as
     * an <code>int</code> in the specified radix, <code>false</code>
     * otherwise.
     */
    // Adapted from GPL'ed JDK7. The terms of the GPL v2 with
    // the "classpath" exception apply to this method (but not to
    // any other), and act as an exception to the University of
    // Nebraska Open Academic License, for this method.
    public static boolean isParsableInt(String s, int radix,
            IntegerPtr resultPtr) {
        resultPtr.value = 0;
        if (s == null) {
            return false;
        }
        if (radix < Character.MIN_RADIX) {
            return false;
        }
        if (radix > Character.MAX_RADIX) {
            return false;
        }

        int result = 0;
        boolean negative = false;
        int i = 0, max = s.length();
        int limit;
        int multmin;
        int digit;

        if (max > 0) {
            char firstChar = s.charAt(0);
            if (firstChar == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
                i++;
            }
            else {
                if (firstChar == '+')
                    i++;
                limit = -Integer.MAX_VALUE;
            }
            multmin = limit / radix;
            if (i < max) {
                digit = Character.digit(s.charAt(i++), radix);
                if (digit < 0) {
                    return false;
                }
                else {
                    result = -digit;
                }
            }
            while (i < max) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                digit = Character.digit(s.charAt(i++), radix);
                if (digit < 0) {
                    return false;
                }
                if (result < multmin) {
                    return false;
                }
                result *= radix;
                if (result < limit + digit) {
                    return false;
                }
                result -= digit;
            }
        }
        else {
            return false;
        }
        if (negative) {
            if (i > 1) {
                resultPtr.value = result;
                return true;
            }
            else { // Only got "-"
                return false;
            }
        }
        else {
            resultPtr.value = -result;
            return true;
        }
    }
    
    /**
     * Helper class to serve as a &quot;pointer&quot; to an integer
     * value, for use in returning multiple values from functions.
     */
    public static final class IntegerPtr {
        /** The <code>int</code> value &quot;pointed&quot; to. */
        public int value;
    }
}
