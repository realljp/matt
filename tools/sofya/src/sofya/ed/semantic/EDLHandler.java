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

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import sofya.base.Handler;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.LocatableFileException;
import sofya.ed.semantic.EventSpecification.ArrayElementType;
import sofya.ed.semantic.EventSpecification.ArrayElementBounds;
import sofya.ed.semantic.EventSpecification.CallType;
import sofya.ed.semantic.EventSpecification.FieldType;
import sofya.ed.semantic.EventSpecification.MethodAction;
import sofya.ed.semantic.EventSpecification.MonitorType;
import sofya.ed.semantic.EDLSpecification.*;

import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ClassGenException;
import org.apache.bcel.classfile.ClassFormatException;

/**
 * Handler for module description and event dispatch data files used by
 * the semantic event dispatcher.
 *
 * @author Alex Kinneer
 * @version 10/09/2007
 */
public class EDLHandler extends Handler {
    // TODO: Implement assertion injection/general bytecode injection mechanism
    
    private static final boolean DEBUG = false;
    
    /**
     * Simple data structure used to store method signature information
     * parsed from an event record.
     */
    private static class MethodInfo {
        String className;
        String name;
        Type[] argTypes;
        boolean intercept;
    }

    /** Singleton instance of the structure for storing parsed method
        signature information. Since records are parsed one at a time,
        a singleton suffices to avoid inefficient allocations of
        temporary objects. Instantiated on demand. */
    private MethodInfo methodInfo = null;
    /** Maintains the current rank (or precedence) for observable rules
        read from module description files. */
    private int eventRank = 1;

    protected static void prepareTokenizer(StreamTokenizer stok) {
        Handler.prepareTokenizer(stok);
        
        // Prevent '-' from being treated as a number character,
        // since it is the exclusion token in EDL syntax. Note
        // that this will prevent parsing of negative numbers;
        // this is not currently needed, but if the need should
        // arise, this would need to be handled locally by temporarily
        // restoring the significance of '-' as a number character.
        stok.ordinaryChar('-');
        stok.wordChars('-', '-');
    }
    
    public final SemanticEventData readEDLFile(String fileName)
            throws BadFileFormatException, IOException {
        return readEDLFile(fileName, null, null);
    }
    
    final SemanticEventData readEDLFile(String fileName,
            SemanticEventData semEventData, GlobalConstraints priorGC)
            throws BadFileFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(
                openInputFile(fileName)));

        try {
            br.mark(1024);
            StringBuilder peekBuf = new StringBuilder();
            int ch = br.read();
            while (ch > ' ') {
                peekBuf.append((char) ch);
                ch = br.read();
            }
            try {
                br.reset();
            }
            catch (IOException e) {
                throw new BadFileFormatException("First line of EDL file " +
                    "cannot exceed 1024 chars (read-ahead buffer overflow)");
            }
            
            if (ch == -1) {
                // EOF after zero or one string -- can't be an EDL file
                throw new BadFileFormatException("Invalid EDL file");
            }

            String peekStr = peekBuf.toString();
            if (DEBUG) System.out.println("EDL format peekStr: " + peekStr);
            
            if (peekStr.endsWith(".prog") || peekStr.endsWith(":")) {
                StreamTokenizer stok = new StreamTokenizer(br);
                prepareTokenizer(stok);
                
                if (semEventData == null) {
                    semEventData = new SemanticEventData("default");
                }
                EDLSpecification edlSpec = readLegacyEDLFile(stok);
                semEventData.addEventSpecification(edlSpec, false);
            }
            else {
                EDLParser parser;
                
                try {
                    if (semEventData == null) {
                        parser = new EDLParser(fileName, br);
                        semEventData = parser.edlUnit();
                    }
                    else {
                        parser = new EDLParser(fileName, br, semEventData,
                            priorGC);
                        parser.importEdlUnit(true);
                    }
                }
                catch (ParseException e) {
                    if (DEBUG) {
                        e.printStackTrace();
                    }
                    throw new BadFileFormatException(e.getMessage() +
                        "\nEncountered errors during parse.", e);
                }
            }
        }
        finally {
            br.close();
        }
        
        return semEventData;
    }
    
    /**
     * Parses an EDL file into an existing event data object.
     * 
     * @param fileName Name of the EDL file to be parsed.
     * @param mainData Event data into which to parse the EDL.
     * 
     * @return The provided event data object, with the given EDL
     * imported (existing data is not cleared, but may be overwritten).
     * 
     * @throws BadFileFormatException If the specified file is malformed
     * or is not an EDL file.
     * @throws IOException For any other error that prevents successful
     * reading of the EDL file.
     */
    final SemanticEventData mergeEDLFile(String fileName,
            SemanticEventData semEventData)
            throws BadFileFormatException, IOException {
        GlobalConstraints globalConstraints;
        if (semEventData.getEventSpecificationCount() > 0) {
            EventSpecification spec =
                semEventData.getEventSpecifications().iterator().next();
            if (spec instanceof EDLSpecification) {
                // (It's shared by all of the EDL specifications in the
                // suite, so we can just grab it from the first)
                globalConstraints = ((EDLSpecification) spec).globals;
            }
            else {
                globalConstraints = new GlobalConstraints();
            }
        }
        else {
            globalConstraints = new GlobalConstraints();
        }
        
        return readEDLFile(fileName, semEventData, globalConstraints);
    }

    /**
     * Reads a legacy EDL (module description) file.
     *
     * @param fileName Path to the legacy EDL file.
     *
     * @return An ADT representing the module description contained in the
     * specified file.
     *
     * @throws BadFileFormatException If the specified module description
     * file is malformed or not a module description file.
     * @throws IOException For any other error that prevents successful
     * reading of the specified module description file.
     */
    public EDLSpecification readDescriptionFile(String fileName)
            throws BadFileFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(
                openInputFile(fileName)));
        StreamTokenizer stok = new StreamTokenizer(br);

        prepareTokenizer(stok);
        
        EDLSpecification edlSpec = null;
        try {
            edlSpec = readLegacyEDLFile(stok);
        }
        finally {
            br.close();
        }
        
        return edlSpec;
    }
    
    private final EDLSpecification readLegacyEDLFile(StreamTokenizer stok)
            throws BadFileFormatException, IOException {
        EDLSpecification edlSpec = null;
        String specName = null;

        List<ProgramUnit> systemClassList = new ArrayList<ProgramUnit>();
        List<ProgramUnit> moduleClassList = new ArrayList<ProgramUnit>();

        String systemProgFile;
        String headerTok = readString(stok);
        if (headerTok.equals("SpecName:")) {
            if (isStringAvailable(stok, false)) {
                specName = readString(stok);
            }
            else {
                throw new BadFileFormatException("EDL file is " +
                    "incomplete: specification name is missing");
            }
            if (!readToEOL(stok)) {
                throw new BadFileFormatException("EDL file is " +
                    "incomplete: system classes not specified");
            }
            systemProgFile = readString(stok);
        }
        else {
            systemProgFile = headerTok;
        }
        
        String sysTag = null;
        if (isStringAvailable(stok, false)) {
            sysTag = readString(stok);
        }
        if (!readToEOL(stok)) {
            throw new BadFileFormatException("EDL file " +
                "is incomplete: module classes not specified");
        }

        String moduleProgFile = readString(stok);
        String moduleTag = null;
        if (isStringAvailable(stok, false)) {
            moduleTag = readString(stok);
        }

        // Hitting EOF is fine here
        readToEOL(stok);

        Handler.readProgFile(systemProgFile, sysTag, systemClassList);
        Handler.readProgFile(moduleProgFile, moduleTag, moduleClassList);

        edlSpec = new EDLSpecification(systemClassList, moduleClassList);
        
        if (specName != null) {
            edlSpec.setSpecificationKey(specName);
        }

        eventRank = 1;

        int ttype;
        while ((ttype = stok.nextToken()) != StreamTokenizer.TT_EOF) {
             if (ttype == StreamTokenizer.TT_EOL) continue;

             if (ttype != StreamTokenizer.TT_WORD) {
                 throw new LocatableFileException("Record type missing " +
                     "(non-character token found)", stok.lineno());
             }

             String strToken = stok.sval;
             if (strToken.length() > 1) {
                 throw new LocatableFileException("Unknown record type " +
                     "(token contains more than one character)",
                     stok.lineno());
             }

             char lineCode = strToken.charAt(0);
             switch (lineCode) {
             case 'E':
                 parseEvent(stok, edlSpec);
                 break;
             case 'A':
                 parseAssert(stok, edlSpec);
                 break;
             default:
                 throw new LocatableFileException("Unknown record " +
                     "type: '" + lineCode + "'", stok.lineno());
             }
        }

        return edlSpec;
    }

    /**
     * Stub method in case it should eventually be useful to provide support
     * for generating these files, either automatically or with the
     * assistance of a GUI.
     */
    void writeDescriptionFile(EDLSpecification edlSpec) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parses an event record from a module description file.
     *
     * @param stok Tokenizer attached to module description file stream.
     * @param edlSpec EDL description object which is being constructed
     * from the description file.
     */
    private void parseEvent(StreamTokenizer stok, EDLSpecification edlSpec)
                 throws BadFileFormatException, IOException {
        boolean inclusion;

        String includeStr = readString(stok);
        if (includeStr.length() > 1) {
            throw new LocatableFileException("Record inclusion code " +
                "missing (must be '+' or '-')", stok.lineno());
        }

        switch (includeStr.charAt(0)) {
        case '+':
            inclusion = true;
            break;
        case '-':
            inclusion = false;
            break;
        default:
            throw new LocatableFileException("Illegal record inclusion " +
                "code '" + includeStr.charAt(0) + "'", stok.lineno());
        }

        String eventName = readString(stok).toLowerCase();
        if ("new_object".equals(eventName)) {
            String className = readString(stok);
            NewObjectRequest request =
                edlSpec.createNewObjectRequest(className, inclusion,
                        eventRank++);
            parseConditions(stok, request, inclusion);
            edlSpec.addNewObjectRequest(request);
        }
        else if ("construct_object".equals(eventName)) {
            String className = readString(stok);
            Type[] argTypes = parseArgumentTypes(stok, "{");
            edlSpec.addConstructorEntryRequest(className, argTypes,
                inclusion, eventRank++);
        }
        else if ("construct_finish".equals(eventName)) {
            String className = readString(stok);
            Type[] argTypes = parseArgumentTypes(stok, "{");
            edlSpec.addConstructorExitRequest(className, argTypes,
                inclusion, eventRank++);
        }
        else if ("get_static".equals(eventName)) {
            String fieldName = readString(stok);
            FieldRequest request = edlSpec.createFieldRequest(fieldName,
                inclusion, eventRank++, FieldType.GETSTATIC);
            parseConditions(stok, request, inclusion);
            edlSpec.addFieldRequest(request);
        }
        else if ("put_static".equals(eventName)) {
            String fieldName = readString(stok);
            FieldRequest request = edlSpec.createFieldRequest(fieldName,
                inclusion, eventRank++, FieldType.PUTSTATIC);
            parseConditions(stok, request, inclusion);
            edlSpec.addFieldRequest(request);
        }
        else if ("get_field".equals(eventName)) {
            String fieldName = readString(stok);
            FieldRequest request = edlSpec.createFieldRequest(fieldName,
                inclusion, eventRank++, FieldType.GETFIELD);
            parseConditions(stok, request, inclusion);
            edlSpec.addFieldRequest(request);
        }
        else if ("put_field".equals(eventName)) {
            String fieldName = readString(stok);
            FieldRequest request = edlSpec.createFieldRequest(fieldName,
                inclusion, eventRank++, FieldType.PUTFIELD);
            parseConditions(stok, request, inclusion);
            edlSpec.addFieldRequest(request);
        }
        else if ("constructor_call".equals(eventName)) {
            String className = readString(stok);
            Type[] argTypes = parseArgumentTypes(stok, "{");
            CallRequest request = edlSpec.createCallRequest(className,
                "<init>", argTypes, inclusion, eventRank++,
                CallType.CONSTRUCTOR, false);
            parseConditions(stok, request, inclusion);
            edlSpec.addCallRequest(request);
        }
        else if ("static_call".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            CallRequest request = edlSpec.createCallRequest(mi.className,
                mi.name, mi.argTypes, inclusion, eventRank++, CallType.STATIC,
                mi.intercept);
            parseConditions(stok, request, inclusion);
            edlSpec.addCallRequest(request);
        }
        else if ("virtual_call".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            CallRequest request = edlSpec.createCallRequest(mi.className,
                mi.name, mi.argTypes, inclusion, eventRank++, CallType.VIRTUAL,
                mi.intercept);
            parseConditions(stok, request, inclusion);
            edlSpec.addCallRequest(request);
        }
        else if ("interface_call".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            CallRequest request = edlSpec.createCallRequest(mi.className,
                mi.name, mi.argTypes, inclusion, eventRank++,
                CallType.INTERFACE, mi.intercept);
            parseConditions(stok, request, inclusion);
            edlSpec.addCallRequest(request);
        }
        else if ("vmethod_enter".equals(eventName)) {
            System.err.println("NOTE: \"vmethod_enter\" has been " +
                "deprecated, please use \"virtual_method_enter\" " +
                "\n      instead.");
            MethodInfo mi = parseMethodInfo(stok);
            edlSpec.addMethodChangeRequest(mi.className, mi.name, mi.argTypes,
                inclusion, eventRank++, MethodAction.VIRTUAL_ENTER);
        }
        else if ("virtual_method_enter".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            edlSpec.addMethodChangeRequest(mi.className, mi.name, mi.argTypes,
                inclusion, eventRank++, MethodAction.VIRTUAL_ENTER);
        }
        else if ("virtual_method_exit".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            edlSpec.addMethodChangeRequest(mi.className, mi.name, mi.argTypes,
                inclusion, eventRank++, MethodAction.VIRTUAL_EXIT);
        }
        else if ("static_method_enter".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            edlSpec.addMethodChangeRequest(mi.className, mi.name, mi.argTypes,
                inclusion, eventRank++, MethodAction.STATIC_ENTER);
        }
        else if ("static_method_exit".equals(eventName)) {
            MethodInfo mi = parseMethodInfo(stok);
            edlSpec.addMethodChangeRequest(mi.className, mi.name, mi.argTypes,
                inclusion, eventRank++, MethodAction.STATIC_EXIT);
        }
        else if ("monitor_contend".equals(eventName)) {
            String className = readString(stok);
            MonitorRequest request = edlSpec.createMonitorRequest(className,
                inclusion, eventRank++, MonitorType.CONTEND);
            parseConditions(stok, request, inclusion);
            edlSpec.addMonitorRequest(request);
        }
        else if ("monitor_acquire".equals(eventName)) {
            String className = readString(stok);
            MonitorRequest request = edlSpec.createMonitorRequest(className,
                inclusion, eventRank++, MonitorType.ACQUIRE);
            parseConditions(stok, request, inclusion);
            edlSpec.addMonitorRequest(request);
        }
        else if ("monitor_pre_release".equals(eventName)) {
            String className = readString(stok);
            MonitorRequest request = edlSpec.createMonitorRequest(className,
                inclusion, eventRank++, MonitorType.PRE_RELEASE);
            parseConditions(stok, request, inclusion);
            edlSpec.addMonitorRequest(request);
        }
        else if ("monitor_release".equals(eventName)) {
            String className = readString(stok);
            MonitorRequest request = edlSpec.createMonitorRequest(className,
                inclusion, eventRank++, MonitorType.RELEASE);
            parseConditions(stok, request, inclusion);
            edlSpec.addMonitorRequest(request);
        }
        else if ("throw".equals(eventName)) {
            String exceptionClass = readString(stok);
            boolean andSubclasses = false;
            if (isStringAvailable(stok, false)) {
                String token = readString(stok).toLowerCase();
                if (token.equals("+s")) {
                    andSubclasses = true;
                }
                else {
                    stok.pushBack();
                }
            }

            ThrowRequest request = edlSpec.createThrowRequest(exceptionClass,
                andSubclasses, inclusion, eventRank++);
            parseConditions(stok, request, inclusion);
            edlSpec.addThrowRequest(request);
        }
        else if ("catch".equals(eventName)) {
            String exceptionClass = readString(stok);
            boolean andSubclasses = false;
            if (isStringAvailable(stok, false)) {
                String token = readString(stok).toLowerCase();
                if (token.equals("+s")) {
                    andSubclasses = true;
                }
                else {
                    stok.pushBack();
                }
            }

            CatchRequest request = edlSpec.createCatchRequest(exceptionClass,
                andSubclasses, inclusion, eventRank++);
            parseConditions(stok, request, inclusion);
            edlSpec.addCatchRequest(request);
        }
        else if ("static_init_enter".equals(eventName)) {
            String className = readString(stok);
            edlSpec.addStaticInitializerEntryRequest(className, inclusion,
                eventRank++);
        }
        else if ("array_element_load".equals(eventName)) {
            String typeStr = readString(stok);
            Type javaType;
            if (typeStr.equals("*")) {
                javaType = SemanticConstants.TYPE_ANY;
            }
            else {
                javaType = parseType(typeStr, stok);
            }

            ArrayElementBounds bounds = parseArrayBounds(stok);
            bounds.javaType = javaType;
            
            if (DEBUG) {
                System.out.println("Read array element load event: ");
                System.out.println(  "type=" + typeStr);
                System.out.println(  "min=" +
                    ((bounds.min != ArrayElementBounds.NO_BOUND)
                    ? bounds.min : "none"));
                System.out.println(  "max=" +
                    ((bounds.max != ArrayElementBounds.NO_BOUND)
                    ? bounds.max : "none"));
            }
            
            ArrayElementRequest request = edlSpec.createArrayElementRequest(
                bounds, inclusion, eventRank++, ArrayElementType.LOAD);
            parseConditions(stok, request, inclusion);
            edlSpec.addArrayElementRequest(request);
        }
        else if ("array_element_store".equals(eventName)) {
            String typeStr = readString(stok);
            Type javaType;
            if (typeStr.equals("*")) {
                javaType = SemanticConstants.TYPE_ANY;
            }
            else {
                javaType = parseType(typeStr, stok);
            }

            ArrayElementBounds bounds = parseArrayBounds(stok);
            bounds.javaType = javaType;
            
            if (DEBUG) {
                System.out.println("Read array element store event: ");
                System.out.println(  "type=" + typeStr);
                System.out.println(  "min=" +
                    ((bounds.min != ArrayElementBounds.NO_BOUND)
                    ? bounds.min : "none"));
                System.out.println(  "max=" +
                    ((bounds.max != ArrayElementBounds.NO_BOUND)
                    ? bounds.max : "none"));
            }
            
            ArrayElementRequest request = edlSpec.createArrayElementRequest(
                bounds, inclusion, eventRank++, ArrayElementType.STORE);
            parseConditions(stok, request, inclusion);
            edlSpec.addArrayElementRequest(request);
        }
        else {
            throw new LocatableFileException("Unknown event \"" + eventName +
                "\"", stok.lineno());
        }
        
        if (stok.nextToken() != StreamTokenizer.TT_EOL) {
            if (stok.ttype != StreamTokenizer.TT_EOF) {
                throw new LocatableFileException("Failed to reach end of " +
                    "record", stok.lineno());
            }
        }
    }

    /**
     * Parses event condition information from a record.
     *
     * @param stok Tokenizer attached to module description file stream.
     * @param request Observable event request to which the conditions
     * will be added.
     * @param inclusion Flag indicating whether the record for which
     * conditions are being parsed is an inclusion or exclusion record.
     *
     * @throws BadFileFormatException If the condition information
     * in the event record is malformed.
     * @throws IOException On any I/O error that prevents successful
     * reading from the tokenizer.
     */
    private void parseConditions(StreamTokenizer stok, EGEventRequest request,
            boolean inclusion) throws BadFileFormatException, IOException {
        if (!readStringIgnoreEOL(stok).equals("{")) {
            throw new LocatableFileException("Expected \"{\"", stok.lineno());
        }

        EventConditions ecs = request.conditions();

        String token;
        while (!(token = readStringIgnoreEOL(stok)).equals("}")) {
            if (token.equals("in")) {
                MethodInfo mi = parseMethodInfo(stok);
                ecs.addInCondition(mi.className, mi.name, mi.argTypes,
                    inclusion, eventRank++);
            }
            else if (token.equals("not")) {
                MethodInfo mi = parseMethodInfo(stok);
                ecs.addNotCondition(mi.className, mi.name, mi.argTypes,
                    inclusion, eventRank++);
            }
            else {
                throw new LocatableFileException("Unexpected token \"" +
                    token + "\"", stok.lineno());
            }
        }
    }

    /**
     * Parses method signature information from a record. Extracts the class
     * name, method name, and argument types.
     *
     * @param stok Tokenizer attached to module description file stream.
     *
     * @return A data structure with appropriate fields set to their
     * corresponding values. Only the argument types field is permitted
     * to be <code>null</code> (representing a wildcard on the signature).
     *
     * @throws BadFileFormatException If the method signature information
     * in the event record is malformed.
     */
    private MethodInfo parseMethodInfo(StreamTokenizer stok)
            throws BadFileFormatException, IOException {
        if (methodInfo == null) {
            methodInfo = new MethodInfo();
        }

        methodInfo.intercept = false;
        if (isStringAvailable(stok, false)) {
            String buffer = readString(stok);
            if ("#INT".equals(buffer.toUpperCase())) {
                methodInfo.intercept = true;

                if (isStringAvailable(stok, false)) {
                    buffer = readString(stok);
                }
                else {
                    throw new LocatableFileException("Method identification " +
                        "information must be provided", stok.lineno());
                }
            }

            int length = buffer.length();
            int lastDot = buffer.lastIndexOf('.');

            if (lastDot > 0) {
                if (lastDot == (length - 1)) {
                    throw new LocatableFileException("Method class name " +
                        "missing", stok.lineno());
                }

                methodInfo.className = buffer.substring(0, lastDot);
                methodInfo.name = buffer.substring(lastDot + 1, length);
            }
            else {
                if ((length > 0) && (buffer.charAt(0) == '*')) {
                    methodInfo.className = "*";
                    methodInfo.name = "*";
                }
                else {
                    throw new LocatableFileException("Method name missing",
                        stok.lineno());
                }
            }
        }
        else {
            throw new LocatableFileException("Method identification " +
                "information must be provided", stok.lineno());
        }

        methodInfo.argTypes = null;
        if (!methodInfo.name.equals("*")) {
            methodInfo.argTypes = parseArgumentTypes(stok, "{");
        }

        return methodInfo;
    }

    /**
     * Parses the argument types from a call record.
     *
     * <p>Argument types must always be comma delimited. This method will
     * attempt to recognize the following:
     * <ul>
     * <li>JNI style single-character codes for primitive types</li>
     * <li>Simple names for primitive types, including the string type</li>
     * <li>JNI style class signatures</li>
     * <li>Regular, fully qualified names of classes</li>
     * </ul>
     * Arrays <strong>must</strong> be specified in the JNI style.</p>
     *
     * @param Tokenizer attached to module description file stream.
     *
     * @return The types of the arguments to the call.
     *
     * @throws BadFileFormatException If the tokenizer does not point
     * to a parsable set of argument types.
     * @throws IOException If any other I/O error prevents successful
     * parsing of the argument types.
     */
    private final Type[] parseArgumentTypes(StreamTokenizer stok,
            String stopToken) throws BadFileFormatException, IOException {
        short voidCount = 0;
        
        stok.whitespaceChars(',', ',');

        try {
            if (!isStringAvailable(stok, false)) {
                return null;
            }

            String argString = readString(stok);
            if (argString.equals("*")) {
                return null;
            }
            else {
                stok.pushBack();
                if (argString.equals(stopToken)) {
                    return null;
                }
            }

            List<Object> argTypes = new ArrayList<Object>();
            while (isStringAvailable(stok, false)) {
                argString = readString(stok);

                if (argString.equals(stopToken)) {
                    stok.pushBack();
                    break;
                }
                
                Type argType = parseType(argString, stok);
                if (argType == Type.VOID) {
                    voidCount++;
                }
                else {
                    argTypes.add(argType);
                }
            }
            
            if (voidCount > 0) {
                if ((voidCount > 1) || (argTypes.size() > 0)) {
                    throw new IOException("Illegal signature: void cannot " +
                        "be combined with any other argument types\n\tand " +
                        "may only appear once");
                }
            }
            
            //System.out.println(stok.lineno() + argTypes.toString());

            return (Type[]) argTypes.toArray(new Type[argTypes.size()]);
        }
        finally {
            stok.wordChars(',', ',');
        }
    }
    
    private final Type parseType(String typeToken,
            StreamTokenizer stok) throws BadFileFormatException, IOException {
        char firstChar = typeToken.charAt(0);
        
        // Check for signature-style primitive type codes
        if (typeToken.length() == 1) {
            switch (firstChar) {
            case 'B':
                return Type.BYTE;
            case 'C':
                return Type.CHAR;
            case 'D':
                return Type.DOUBLE;
            case 'F':
                return Type.FLOAT;
            case 'I':
                return Type.INT;
            case 'J':
                return Type.LONG;
            case 'Z':
                return Type.BOOLEAN;
            case 'S':
                return Type.SHORT;
            case 'V':
                return Type.VOID;
            default:
                // Well... it's *theoretically* possible for it to be a
                // single character class name with no package
                break;
            }
        }

        // Check for convenience string identifiers
        String lcString = typeToken.toLowerCase();
        if ("int".equals(lcString)) {
            return Type.INT;
        }
        else if ("string".equals(lcString)) {
            return Type.STRING;
        }
        else if ("boolean".equals(lcString)) {
            return Type.BOOLEAN;
        }
        else if ("float".equals(lcString)) {
            return Type.FLOAT;
        }
        else if ("double".equals(lcString)) {
            return Type.DOUBLE;
        }
        else if ("char".equals(lcString)) {
            return Type.CHAR;
        }
        else if ("byte".equals(lcString)) {
            return Type.BYTE;
        }
        else if ("short".equals(lcString)) {
            return Type.SHORT;
        }
        else if ("long".equals(lcString)) {
            return Type.LONG;
        }
        else if ("void".equals(lcString)) {
            return Type.VOID;
        }
        else {
            return resolveReferenceType(typeToken, firstChar, stok.lineno());
        }
    }
    
    // Try to parse a string as a type signature or literal class name
    private final ReferenceType resolveReferenceType(String typeStr,
            char firstChar, int lineno) throws LocatableFileException {
        ReferenceType rt = null;

        if ((firstChar == '[')
                || ((firstChar == 'L') && typeStr.endsWith(";"))) {
            try {
                rt = (ReferenceType) Type.getType(typeStr);
                // Sanity check
                if (!rt.isCastableTo(Type.OBJECT)) {
                    rt = null;
                }
                else {
                    return rt;
                }
            }
            catch (ClassGenException e) {
                throw new LocatableFileException(e.getMessage(), lineno);
            }
            catch (ClassFormatException e) {
                throw new LocatableFileException(e.getMessage(), lineno);
            }
            catch (ClassFormatError e) { }
            catch (ClassNotFoundException e) { }
        }

        if (rt == null) {
            rt = new ObjectType(typeStr);
        }

        try {
            if (!rt.isCastableTo(Type.OBJECT)) {
                throw new LocatableFileException("Argument type \"" +
                    typeStr + "\" is not valid", lineno);
            }
        }
        catch (ClassNotFoundException e) {
            throw new LocatableFileException("Argument type \"" +
                typeStr + "\" is not on classpath", lineno);
        }
        
        return rt;
    }
    
    private final ArrayElementBounds parseArrayBounds(
            StreamTokenizer stok) throws BadFileFormatException, IOException {
        ArrayElementBounds bounds = new ArrayElementBounds();
        String token;
        
        stok.ordinaryChar(':');
        for (int i = 0; i < 2; i++) {
            if (!isStringAvailable(stok, false)) {
                break;
            }
            
            token = readString(stok);
            if ("min".equals(token)) {
                stok.nextToken();
                bounds.min = readInt(stok);
            }
            else if ("max".equals(token)) {
                stok.nextToken();
                bounds.max = readInt(stok);
            }
            else if ("{".equals(token)) {
                stok.pushBack();
                break;
            }
            else {
                throw new LocatableFileException("Unexpected token \"" +
                    token + "\"", stok.lineno());
            }
        }
        stok.wordChars(':', ':');
        
        return bounds;
    }

    /**
     * For future implementation.
     */
    private void parseAssert(StreamTokenizer stok, EDLSpecification edlSpec)
                 throws BadFileFormatException, IOException {
        @SuppressWarnings("unused")
        int precedence = stok.lineno();
    }

    /**
     * Reads an event dispatch data file.
     *
     * @param fileName Path to the data file.
     *
     * @return An object which provides access to the event dispatch data.
     *
     * @throws BadFileFormatException If the specified event dispatch data
     * file is corrupted, invalid, or not an event dispatcn data file.
     * @throws IOException For any other I/O error that prevents reading
     * of the file.
     */
    public SemanticEventData readDataFile(String fileName)
            throws BadFileFormatException, IOException {
        DataInputStream in = new DataInputStream(
            new BufferedInputStream(openInputFile(fileName)));
        SemanticEventData data = null;

        try {
            data = SemanticEventData.deserialize(in);
        }
        finally {
            in.close();
        }

        return data;
    }

    /**
     * Writes an event dispatch data file.
     *
     * @param fileName Path and name of the data file to be written.
     * @param data Event dispatch data to be written to file.
     *
     * @throws IOException For any error that prevents the data file
     * from being successfully written.
     */
    void writeDataFile(String fileName, SemanticEventData data)
            throws IOException {
        DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(openOutputFile(fileName, false)));

        try {
            data.serialize(out);
        }
        finally {
            out.close();
        }
    }
     
    /** For testing. */
    static void main(String[] argv) throws Exception {
        EDLHandler sdh = new EDLHandler();
        sdh.readEDLFile(argv[0]);
    }
}
