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

import java.util.BitSet;

/**
 * A coverage traces records the coverage of structural program entities (basic
 * blocks or branches) observed through execution of an instrumented program by
 * a {@link ProgramEventDispatcher}.
 *
 * @author Alex Kinneer
 * @version 03/03/2006
 *
 * @see sofya.ed.Instrumentor
 * @see sofya.ed.structural.TraceHandler
 * @see sofya.ed.structural.ProgramEventDispatcher
 * @see sofya.viewers.TraceViewer
 */
public abstract class CoverageTrace {
    /** Bit vector which records program entities hit during execution. */
    protected BitSet traceVector;
    /** Highest structural entity number found in the method. */
    protected int highestId;

    /**************************************************************************
     * Default constructor is not useful, as it would not provide the number
     * of structural entities present in the method.
     */
    private CoverageTrace() { }

    /**************************************************************************
     * Default constructor, creates a trace with the specified number of
     * structural entities.
     *
     * @param highestId Highest possible entity number in the method;
     * cannot be changed after instantiation.
     */
    protected CoverageTrace(int highestId) {
        this.highestId = highestId;
        this.traceVector = new BitSet(highestId);
    }

    /**************************************************************************
     * Gets the highest entity ID present in the method.
     *
     * @return The highest ID of any entity found in the method.
     */
    public int getHighestId() {
        return highestId;
    }

    /**************************************************************************
     * Marks that an entity was covered during execution.
     *
     * @param id ID of the entity that was covered.
     */
    public void set(int id) {
        if ((id - 1 > highestId) || (id < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                    "block or branch " + id);
        }
        traceVector.set(id - 1);
    }

    /**************************************************************************
     * Queries whether an entity was covered during execution.
     *
     * @param id ID of the method entity to be queried.
     *
     * @return <code>true</code> if the entity was covered during execution,
     * <code>false</code> otherwise.
     */
    public boolean query(int id) {
        if ((id - 1 > highestId) || (id < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                    "block or branch " + id);
        }
        return traceVector.get(id - 1);
    }

    /**************************************************************************
     * Marks that an entity was not covered during execution.
     *
     * @param id ID of the entity for which covered status is being
     * cleared.
     */
    public void unset(int id) {
        if ((id - 1 > highestId) || (id < 1)) {
            throw new IndexOutOfBoundsException("Method does not contain " +
                    "block or branch " + id);
        }
        traceVector.clear(id - 1);
    }

    /**************************************************************************
     * Clears the covered status for every entity in the method.
     */
    public void clear() {
        BitSet zeroMask = new BitSet(traceVector.size());
        traceVector.and(zeroMask);
    }

    /**************************************************************************
     * Sets the trace vector for for this trace to the specified bit vector,
     * represented by a hexadecimal string.
     *
     * <p>The hexadecimal string should be a contiguous string of hexadecimal
     * digits with no special formatting. Any existing trace information
     * associated with the block will be replaced.</p>
     *
     * <p>This method will <i>not</i> reset the highest entity ID for the
     * trace object. If the specified bit vector is larger than the number
     * of entities set for this trace object, excess entities will be
     * inaccessible (entity-ID-keyed methods will return an
     * IndexOutOfBoundsException for those indices) and will be ignored when
     * the trace file is written. This is in part to mask the padding
     * required internally for entity counts which are not multiples of four.</p>
     *
     * @param hexString New trace vector, in hexadecimal form.
     */
    void setTraceVector(String hexString) {
        traceVector = TraceHandler.toBinary(hexString);
    }

    /**************************************************************************
     * Gets the trace vector from this trace, represented as a hexadecimal
     * string.
     *
     * <p>The bit vector will be end-padded to the nearest multiple of four
     * and converted to a contiguous string of hexadecimal digits.</p>
     *
     * @return The trace vector stored by this trace object.
     */
    String getTraceVector() {
        return TraceHandler.toHex(traceVector, highestId);
    }

    public abstract CoverageTrace copy();

    /**************************************************************************
     * Tests whether this trace object is equal to another trace.
     *
     * <p>Two trace objects are considered equal if their entity counts and
     * trace bit vectors are equivalent.</p>
     *
     * @param obj Trace to which this trace should be compared for equality.
     *
     * @return <code>true</code> if the specified trace is equal to
     * this trace, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (this == obj) return true;

        CoverageTrace trace = (CoverageTrace) obj;
        if ((this.highestId != trace.highestId) ||
            !this.traceVector.equals(trace.traceVector)) {
            return false;
        }

        return true;
    }

    /**************************************************************************
     * Creates a trace that is the union of this trace and another trace.
     *
     * <p>The specified trace must have the same number of method entities as
     * this trace. The method will then take the logical union of the
     * bit vectors in the two traces. The resulting trace will be an entirely
     * new object, neither the current trace or the specified trace will be
     * modified by this method.</p>
     *
     * @param tr Trace for which the union should be taken with
     * this trace.
     *
     * @return An entirely new trace object, representing the union
     * of this trace and the specified trace.
     */
    public CoverageTrace union(CoverageTrace tr) {
        if (this.highestId != tr.highestId) {
            throw new IllegalArgumentException("Methods do not have matching " +
                "number of blocks/branches");
        }

        CoverageTrace unionTrace = this.copy();
        unionTrace.traceVector.or(tr.traceVector);

        return unionTrace;
    }

    /**************************************************************************
     * Returns a string representation of this trace.
     *
     * <p>This method simply calls the <code>toString()</code> method of the
     * underlying BitSet and returns the result. Typically this is a list of
     * the bits in the BitSet using vector notation
     * (e.g. <code>[ 0 1 1 0 ... 1 ]</code>).</p>
     *
     * @return String representation of this trace object.
     */
    public String toString() {
        return traceVector.toString();
    }
}

/****************************************************************************/

/*
  $Log: CoverageTrace.java,v $
  Revision 1.4  2007/07/30 18:01:26  akinneer
  Updated year in copyright notice.

  Revision 1.3  2006/09/08 21:30:04  akinneer
  Updated copyright notice.

  Revision 1.2  2006/09/08 20:26:26  akinneer
  Cleaned up imports. Removed constant interface anti-pattern.

  Revision 1.1  2006/03/21 22:09:55  kinneer
  Moved from 'inst', or new.

  Revision 1.2  2005/06/06 18:47:47  kinneer
  Minor revisions and added copyright notices.

  Revision 1.1.1.1  2005/01/06 17:34:16  kinneer
  Sofya Java Bytecode Instrumentation and Analysis System

  Revision 1.12  2004/04/16 17:59:52  kinneer
  Added union method to support trace file merging.

  Revision 1.11  2004/04/14 18:48:55  kinneer
  Moved binary/hex conversion routines to Handler class to simplify
  maintenance.

  Revision 1.10  2003/08/27 18:44:06  kinneer
  New handlers architecture. Addition of test history related classes.
  Part of release 2.2.0.

  Revision 1.9  2003/08/13 18:28:37  kinneer
  Release 2.0, please refer to release notes for details.

  Revision 1.8  2003/08/01 17:10:46  kinneer
  All file handler implementations changed from HashMaps to TreeMaps.
  See release notes for additional details.  Version string for
  Galileo has been set.

  All classes cleaned for readability and JavaDoc'ed.

  Revision 1.7  2002/07/17 05:51:27  sharmahi
  Modified package name

  Revision 1.6  2002/07/08 05:46:53  sharmahi
  Modified package name

  Revision 1.5  2002/06/25 09:09:57  sharmahi
  Added Package name "handlers"

  Revision 1.3  2002/06/09 08:45:27  sharmahi
  After first glance and review of fomrat, code style and file layout

  Revision 1.2  2002/04/16 08:42:06  sharmahi
  galileo/src/handlers/

  Revision 1.1  2002/04/06 01:26:19  sharmahi
  After first Code reviews and implementing the changes suggested.


*/



