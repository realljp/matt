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

package sofya.ed.semantic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import sofya.base.ProgramUnit;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

/**
 * Abstract class which provides various utility methods to classes
 * implementing an event specification.
 *
 * @author Alex Kinneer
 * @version 06/14/2005
 */
public abstract class AbstractEventSpecification implements EventSpecification {
    /**
     * Serializes a collection of strings to an output stream.
     *
     * @param out Stream to which strings will be serialized.
     * @param strings Collection of strings to be serialized.
     *
     * @throws IOException On any I/O error which prevents writing to the
     * stream.
     */
    protected static void serializeStrings(DataOutputStream out,
            Collection<String> strings) throws IOException {
        int size = strings.size();
        out.writeInt(size);
        
        Iterator<String> iterator = strings.iterator();
        for (int i = size; i-- > 0; ) {
            out.writeUTF(iterator.next());
        }
    }
    
    /**
     * Deserializes a collection of strings from an input stream.
     *
     * <p>The stream must be pointing to a leading integer indicating the
     * number of strings to be read.</p>
     *
     * @param in Stream from which to deserialize the strings.
     * @param strings <strong>[out]</strong> Collection which will receive the
     * deserialized strings.
     *
     * @return A reference to the collection of strings.
     *
     * @throws IOException On any I/O error which prevents reading from
     * the stream.
     */
    protected static Collection<String> deserializeStrings(DataInputStream in,
            Collection<String> strings) throws IOException {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            strings.add(in.readUTF());
        }
        
        return strings;
    }
    
    /**
     * Serializes an object-to-integer map to an output stream.
     *
     * @param out Stream to which the map will be serialized.
     * @param map Object/int map to be serialized.
     *
     * @throws IOException On any I/O error which prevents writing to the
     * stream.
     */
    protected static void serializeObjectIntMap(DataOutputStream out,
            TObjectIntHashMap map) throws IOException {
        out.writeInt(map.size());
        TObjectIntIterator iterator = map.iterator();
        for (int i = map.size(); i-- > 0; ) {
            iterator.advance();
            out.writeUTF((String) iterator.key());
            out.writeInt(iterator.value());
        }
    }
    
    /**
     * Deserializes an object-to-integer map from an input stream.
     *
     * @param in Stream from which the map will be deserialized.
     *
     * @return The deserialized map.
     *
     * @throws IOException On any I/O error which prevents reading from the
     * stream.
     */
    protected static TObjectIntHashMap deserializeObjectIntMap(
            DataInputStream in) throws IOException {
        TObjectIntHashMap map = new TObjectIntHashMap();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            int precedence = in.readInt();
            map.put(key, precedence);
        }
        
        return map;
    }
    
    /**
     * Serializes a {@link sofya.base.ProgramUnit} to an output stream.
     *
     * @param out Stream to which the <code>ProgramUnit</code> will be
     * serialized.
     * @param pUnit <code>ProgramUnit</code> to be serialized.
     *
     * @throws IOException On any I/O error which prevents writing to the
     * stream.
     */
    protected static void serializeProgramUnit(DataOutputStream out,
            ProgramUnit pUnit) throws IOException {
        out.writeUTF(pUnit.location);
        out.writeBoolean(pUnit.useLocation);
        out.writeBoolean(pUnit.isJar);
        serializeStrings(out, pUnit.classes);
    }
    
    /**
     * Deserializes a {@link sofya.base.ProgramUnit} from an output stream.
     *
     * @param in Stream from which the <code>ProgramUnit</code> will be
     * deserialized.
     *
     * @return The deserialized <code>ProgramUnit</code>.
     *
     * @throws IOException On any I/O error which prevents reading from the
     * stream.
     */
    protected static ProgramUnit deserializeProgramUnit(DataInputStream in)
            throws IOException {
        return new ProgramUnit(
                     in.readUTF(),
                     in.readBoolean(),
                     in.readBoolean(),
                     (List<String>) deserializeStrings(in, new ArrayList<String>()));
    }
}
