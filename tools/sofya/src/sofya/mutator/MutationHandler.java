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

package sofya.mutator;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import sofya.base.Handler;
import sofya.base.exceptions.*;

import gnu.trove.THashSet;
import gnu.trove.THashMap;

/************************************************************************
 * Provides file handling routines for mutation related files.
 *
 * @author Alex Kinneer
 * @version 08/16/2007
 */
@SuppressWarnings("unchecked")
public class MutationHandler extends Handler {
    /** Map used to cache the reflective method objects for invoking the
        deserialize method of mutation implementations. */
    private static Map<Object, Object> mutationMap = new THashMap();
    
    /** Location of the default directory for mutation operators. */
    public static final String DEFAULT_OP_RESOURCE_PATH = "sofya" +
    	File.separatorChar + "mutator" + File.separatorChar + "operators";

    /** Location of the default mutation operator list file, which is the
        file that lists the operators included by default. */
    public static final String DEFAULT_OP_LIST =
    	DEFAULT_OP_RESOURCE_PATH + File.separatorChar + "operators.txt";
    
    /** Counter for assigning mutation IDs when writing a new mutation table. */
    private static int nextMutationID = 1;
    
    private MutationHandler() {
    }
    
    /**
     * Reads a configuration file for the mutation table generator.
     *
     * @param configFile Name of the configuration file.
     *
     * @return A {@link MutatorConfiguration} providing access to the
     * configuration data.
     *
     * @throws BadFileFormatException If the specified file is not a
     * configuration file or does not conform to the configuration file format.
     * @throws IOException For any other I/O error that prevents successful
     * reading of the configuration file.
     */
    @SuppressWarnings("unchecked")
    public static MutatorConfiguration readConfiguration(String configFile)
            throws BadFileFormatException, IOException {
        File file = new File(configFile);
        if (!file.exists()) {
            throw new FileNotFoundException("Configuration file not found");
        }
        
        BufferedReader br = new BufferedReader(
                            new FileReader(configFile));
        MutatorConfiguration mc = null;
        boolean defaultEnabled = true;
        Set<Object> configuredOps = new THashSet();
        int numEnabled = 0;
        
        try {
            StreamTokenizer stok = new StreamTokenizer(br);
            
            prepareTokenizer(stok);
            disableParseNumbers(stok);
            stok.eolIsSignificant(false);
            stok.ordinaryChar('{');
            stok.ordinaryChar('}');
            
            if ((stok.nextToken() != StreamTokenizer.TT_WORD) ||
                    !stok.sval.equals("global")) {
                throw new BadFileFormatException("Global properties " +
                    "section not declared");
            }
                
            if (stok.nextToken() != '{') {
                throw new LocatableFileException("Expected '{', " +
                    configFile, stok.lineno());
            }
            
            Map<Object, Object> properties = new THashMap();
            
            readGlobalKeyValues:
            while (true) {
                switch (stok.nextToken()) {
                case StreamTokenizer.TT_WORD:
                    parseKeyValue(stok.lineno(), stok.sval,
                        properties);
                    break;
                case '}':
                    break readGlobalKeyValues;
                case StreamTokenizer.TT_EOF:
                    throw new DatabaseEOFException("Expected '}', " +
                        configFile);
                default:
                    throw new DataTypeException(stok.lineno());
                }
            }
            
            if (!properties.containsKey("operatorResourcePath")) {
                properties.put("operatorResourcePath",
		    DEFAULT_OP_RESOURCE_PATH);
            }
            
            if (!properties.containsKey("operatorList")) {
                properties.put("operatorList", DEFAULT_OP_LIST);
            }
            
            mc = new MutatorConfiguration(properties);
            
            if (properties.containsKey("defaultEnabled")) {
                String value =
                    ((String) properties.get("defaultEnabled")).toLowerCase();
                if (value.equals("1") || value.equals("true")
                        || value.equals("t")) {
                    defaultEnabled = true;
                }
                else if (value.equals("0") || value.equals("false") ||
                        value.equals("f")) {
                    defaultEnabled = false;
                }
            }
            
            String operator;
            while (stok.nextToken() != StreamTokenizer.TT_EOF) {
                if (stok.ttype == StreamTokenizer.TT_WORD) {
                    operator = stok.sval;
                }
                else {
                    throw new LocatableFileException("Expected mutation " +
                        "operator, " + configFile, stok.lineno());
                }
                
                if (stok.nextToken() != '{') {
                    throw new LocatableFileException("Expected '{', " +
                        configFile, stok.lineno());
                }
                
                boolean enabled = true;
                int pos = operator.lastIndexOf(':');
                if (pos != -1) {
                    String val = operator.substring(pos + 1).toLowerCase();
                    if ("off".equals(val)) {
                        enabled = false;
                    }
                    else {
                        operator = operator.substring(0, pos);
                    }
                }
                
                Map<Object, Object> settings = null;
                properties = new THashMap();
                
                readOpKeyValues:
                while (true) {
                    switch (stok.nextToken()) {
                    case StreamTokenizer.TT_WORD:
                        if (stok.sval.equals("properties:")) {
                            settings = properties;
                            properties = new THashMap();
                            continue;
                        }
                        parseKeyValue(stok.lineno(), stok.sval,
                            properties);
                        break;
                    case '}':
                        break readOpKeyValues;
                    case StreamTokenizer.TT_EOF:
                        throw new DatabaseEOFException("Expected '}', " +
                            configFile);
                    default:
                        throw new DataTypeException(stok.lineno());
                    }
                }
                
                configuredOps.add(operator);
                
                if (enabled) {
                    mc.addOperator(operator, settings, properties);
                    numEnabled += 1;
                }
            }
        }
        finally {
            br.close();
        }
        
        if (defaultEnabled) {
            String[] operatorList = readOperatorList();
            for (int i = 0; i < operatorList.length; i++) {
                if (!configuredOps.contains(operatorList[i])) {
                    mc.addOperator(operatorList[i], new THashMap(),
                        new THashMap());
                    numEnabled += 1;
                }
            }
        }
        
        if (numEnabled == 0) {
            throw new BadFileFormatException("No operators enabled");
        }
        
        return mc;
    }
    
    /**
     * Parses a key-value pair in the format &quot;key=value&quot; from
     * a configuration file.
     *
     * @param lineno Line number at which the key-value pair is being parsed
     * (used for error reporting).
     * @param configLine Line from the configuration file containing the
     * key-value pair.
     * @param properties Map into which the key-value pair is to be stored.
     *
     * @throws BadFileFormatException If the file does not contain a
     * key-value pair on the given line.
     */
    private static void parseKeyValue(int lineno, String configLine,
            Map<Object, Object> properties) throws BadFileFormatException {
        StringTokenizer stok = new StringTokenizer(configLine, "=");
        String key = stok.nextToken();
        
        if (!stok.hasMoreTokens()) {
            throw new LocatableFileException("Error in configuration " +
                "setting: no value specified", lineno);
        }
        
        String value = stok.nextToken();
        properties.put(key, value);
    }
    
    /**
     * Reads the default operator list file.
     *
     * @return An array of strings listing all the names of the implementing
     * classes for the default operators.
     *
     * @throws IOException For any I/O error that prevents successful reading
     * of the operator list file.
     */
    public static String[] readOperatorList()
            throws IOException {
        List<String> operators = new ArrayList<String>();

	ClassLoader loader = MutationHandler.class.getClassLoader();
	BufferedReader in = new BufferedReader(new InputStreamReader(
	    loader.getResourceAsStream(DEFAULT_OP_LIST)));

        try {
            String line = null;
            while ((line = in.readLine()) != null) {
                operators.add(line);
            }
        }
        finally {
            in.close();
        }
        
        return (String[]) operators.toArray(new String[operators.size()]);
    }
    
    /**
     * Returns an iterator that enables progressive reading of a mutation
     * table from a file.
     *
     * <p>Reading a mutation table file using this approach is more efficient
     * if mutations only need to be processed serially. This is especially
     * true of large mutation tables.</p>
     *
     * @param fileName Name of the mutation file to be read.
     *
     * @return A {@link MutationIterator} over the mutations in the mutation
     * table stored in the file.
     *
     * @throws IOException If the specified file does not exist or does not
     * contain a mutation table.
     */
    public static MutationIterator readMutationFile(String fileName)
            throws IOException {
        return new MutationIterator(new MutationFileReader(fileName));
    }
    
    /**
     * Returns a handle to a disk-backed mutation table that progressively
     * writes mutations to a file as they are added.
     *
     * <p>Writing a mutation table file using this approach is more efficient
     * if access to previously written mutations is not required during
     * generation of the table. This is especially true of large mutation
     * tables.</p>
     *
     * @param fileName Name of the mutation file to be written. Any existing
     * file of the same name will be overwritten.
     *
     * @return A {@link FileWriterMutationTable} that provides transparent
     * serialization of mutations to file as they are added.
     *
     * @throws IOException If the requested mutation file cannot be opened
     * for writing.
     */
    public static FileWriterMutationTable writeMutationFile(String fileName)
            throws IOException {
        nextMutationID = 1;
        return new FileWriterMutationTable(fileName);
    }
    
    /**
     * Reads a mutation table from file into memory.
     *
     * <p>Large mutation tables may cause significant memory consumption.</p>
     *
     * @param fileName Name of the mutation file to be read.
     *
     * @return A {@link MutationTable} providing access to the mutations read
     * from the mutation table.
     *
     * @throws IOException If the specified file does not exist or does not
     * contain a mutation table.
     */
    public static MutationTable readMutationTable(String fileName)
            throws IOException {
        MutationFileReader in = new MutationFileReader(fileName);
        StringTable stringTable = in.getStringTable();
        MutationTable mt = null;
        
        try {
            mt = new StandardMutationTable(stringTable);
            
            int count = in.getMutationCount();
            for (int i = 0; i < count; i++) {
                Mutation m = readMutation(in);
                mt.addMutation(m);
            }
        }
        finally {
            in.close();
        }
        
        return mt;
    }
    
    /**
     * Writes a mutation table to file from memory.
     *
     * <p>Retaining an entire mutation table in memory may be costly,
     * therefore it is generally preferable to avoid using this method.</p>
     *
     * @param fileName Name of the mutation file to be written. Any
     * existing file of the same name will be overwritten.
     * @param mt MutationTable to be written to the file.
     *
     * @throws IOException If the requested file cannot be opened for writing
     * or for any other I/O error that prevents successful serialization of
     * the mutation table to the file.
     */
    public static void writeMutationTable(String fileName, MutationTable mt)
            throws IOException {
        StringTable stringTable = mt.getStringTable();
        if (stringTable == null) {
            stringTable = new StringTable();
        }
        MutationFileWriter out = new MutationFileWriter(fileName, stringTable);
        nextMutationID = 1;
        
        try {
            Iterator iterator = mt.iterator();
            while (iterator.hasNext()) {
                Mutation mutation = (Mutation) iterator.next();
                writeMutation(out, mutation);
            }
        }
        finally {
            out.close(mt.size());
        }
    }
    
    /**
     * Utility method that deserializes a single mutation from a mutation table
     * supplied by a data input source.
     *
     * @param in Data input source from which the mutation will be
     * deserialized.
     *
     * @return The mutation deserialized from the data input source.
     *
     * @throws IOException If the data input source is not positioned at
     * a serialized mutation or for any I/O error that prevents successful
     * deserialization of the mutation.
     */
    static Mutation readMutation(DataInput in)
            throws IOException {
        MutationID id = new MutationID(in.readInt());
        String implName = in.readUTF();
        Method createMethod = null;
        
        // We maintain a cache of the Method objects needed to invoke the
        // deserialize method for each mutation type
        if (mutationMap.containsKey(implName)) {
            createMethod = (Method) mutationMap.get(implName);
        }
        else {
            Class mutationClass = null;
            try {
                mutationClass = Class.forName(implName);
            }
            catch (Exception e) {
                IOException ioe = new IOException("Could not deserialize " +
                    "mutation data");
                ioe.initCause(e);
                throw ioe;
            }
            
            try {
                createMethod = mutationClass.getMethod("deserialize",
                    new Class[]{DataInput.class});
            }
            catch (NoSuchMethodException e) {
                IOException ioe = new IOException("Mutation class does " +
                    "not implement static 'deserialize' method");
                ioe.initCause(e);
                throw ioe;
            }
            catch (Exception e) {
                IOException ioe = new IOException("Could not deserialize " +
                    "mutation data");
                ioe.initCause(e);
                throw ioe;
            }
            
            mutationMap.put(implName, createMethod);
        }
        
        Mutation mutation = null;
        try {
            mutation = (Mutation)
                createMethod.invoke(null, new Object[]{in});
        }
        catch (IllegalAccessException e) {
            IOException ioe = new IOException("Could not deserialize " +
                "mutation data: 'deserialize' method does not have " +
                "public access");
            ioe.initCause(e);
            throw ioe;
        }
        catch (InvocationTargetException e) {
            IOException ioe = new IOException("Mutation 'deserialize' " +
                "method threw an exception");
            ioe.initCause(e);
            throw ioe;
        }
        catch (Exception e) {
            IOException ioe = new IOException("Could not deserialize " +
                "mutation data");
            ioe.initCause(e);
            throw ioe;
        }
        
        mutation.setID(id);
        
        return mutation;
    }
    
    /**
     * Utility method that serializes a single mutation to a data output
     * sink.
     *
     * @param out Data output sink to which the mutation will be serialized.
     * @param mutation Mutation to be serialized.
     *
     * @throws IOException For any I/O error that prevents successful
     * serialization of the mutation to the data output sink.
     */
    static void writeMutation(DataOutput out, Mutation mutation)
            throws IOException {
        // Write the ID
        MutationID id = mutation.getID();
        if (id != null) {
            // This provides persistence if mutation files are read and
            // then re-saved.
            out.writeInt(id.asInt());
        }
        else {
            out.writeInt(nextMutationID++);
        }
        // Write the implementing class name
        out.writeUTF(mutation.getClass().getName());
        // Have it serialize itself
        mutation.serialize(out);
    }
    
    /**
     * Test driver to exercise various handler routines.
     */
    public static void main(String[] argv) throws Exception {
        MutatorConfiguration mc = MutationHandler.readConfiguration(argv[0]);
        System.out.println(mc.getDefaultOperatorResourcePath());
        System.out.println(mc.getOperatorListResource());
        OperatorConfiguration[] ops = mc.getOperators();
        for (int i = 0; i < ops.length; i++) {
            System.out.println(ops[i].getName());
        }
    }
}
