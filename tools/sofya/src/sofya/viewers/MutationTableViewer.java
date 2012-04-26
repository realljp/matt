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

package sofya.viewers;

import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.IOException;
import java.util.StringTokenizer;

import sofya.mutator.*;
import sofya.base.exceptions.BadFileFormatException;

import org.apache.bcel.generic.Type;

/**
 * The MutationTableViewer is used to display the a mutation table in a
 * human readable format, or as a list for processing.
 *
 * <p>Usage:<br>
 * <code>java sofya.viewers.MutationTableViewer &lt;mutation_table_file&gt;
 * [-t <i>l|d|i</i>] [-m <i>method_desc</i>] [OutputFile]<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-t <i>l|d|i</i> : Format of the output.<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-m <i>method_desc</i> : Print mutations
 * only for the given method, using the diff-table method signature format.<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;OutputFile : Redirect output of viewer
 * to <i>OutputFile</i>
 * </code></p>
 *
 * @author Alex Kinneer
 * @version 08/11/2006
 */
public class MutationTableViewer extends Viewer {
    /** Output format specifier. */
    private int format = LIST;

    private String forMethod;

    /** Constant flag for list format. */
    public static final int LIST = 0;
    /** Constant flag for descriptive format. */
    public static final int DESCRIPTIVE = 1;
    /** Constant flag for ID-only format. */
    public static final int IDS = 2;

    /**
     * Mutation visitor that instructs visited mutations to print themselves.
     */
    private static class PrintVisitor implements MutationVisitor {
        protected PrintWriter pw;
        protected int format = LIST;

        private PrintVisitor() {
        }

        public PrintVisitor(int format, PrintWriter pw) {
            this.pw = pw;
            this.format = format;
        }

        public void visit(Mutation m) {
            print(m);
        }

        public void visit(MutationGroup m, boolean start) {
            if (start) {
                if (format != IDS) {
                    print(m);
                }
                m.visitMembers(true);
            }
        }

        public void visit(GroupableMutation m) {
            print(m);
        }

        public void visit(ClassMutation m) {
            print(m);
        }

        public void visit(MethodMutation m) {
            print(m);
        }

        private final void print(Mutation m) {
            switch (format) {
            case LIST:
                pw.println(m.print());
                break;
            case DESCRIPTIVE:
                pw.println(m);
                break;
            case IDS:
                int mutationId = m.getID().asInt();
                StringBuilder sb = new StringBuilder();
                sb.append(mutationId);
                sb.append(",");
                if (m instanceof GroupableMutation) {
                    GroupableMutation gm = (GroupableMutation) m;
                    MutationGroup group = gm.getParent();
                    if (group != null) {
                        sb.append(group.getID().asInt());
                    }
                    else {
                        sb.append(mutationId);
                    }
                }
                else {
                    sb.append(mutationId);
                }
                pw.println(sb.toString());
                break;
            default:
                throw new AssertionError("Illegal format");
            }
        }
    }

    private static class MethodPrintVisitor extends PrintVisitor {
        private String forMethod;

        private MethodPrintVisitor() {
        }

        public MethodPrintVisitor(int format, PrintWriter pw,
                String forMethod) {
            super(format, pw);
            if (forMethod == null) {
                throw new NullPointerException();
            }
            this.forMethod = forMethod;
        }

        public void visit(Mutation m) {
            return;
        }

        public void visit(MutationGroup m, boolean start) {
            super.visit(m, start);
        }

        public void visit(GroupableMutation m) {
            if (m instanceof MethodMutation) {
                if (!isMatchingMethod((MethodMutation) m, forMethod)) {
                    return;
                }
            }
            super.print(m);
        }

        public void visit(ClassMutation m) {
            return;
        }

        public void visit(MethodMutation m) {
            if (!isMatchingMethod(m, forMethod)) {
                return;
            }
            super.print(m);
        }

        private final boolean isMatchingMethod(MethodMutation mm,
                String methodDesc) {
            // No spaces are allowed in class or method names, so seek the
            // first underscore (space substitute), then seek backwards
            // to the last dot to find the end of the class name and
            // extract it
            int classEnd = methodDesc.indexOf('_');
            String descClassName = methodDesc.substring(0, classEnd);
            classEnd = descClassName.lastIndexOf('.');
            descClassName = descClassName.substring(0, classEnd);

            // Check whether the class names match
            if (!mm.getClassName().equals(descClassName)) {
                return false;
            }

            // Parse the argument types using the locations of the parentheses
            int argStart = methodDesc.indexOf('(');
            int argEnd = methodDesc.indexOf(')');
            String descArgs = methodDesc.substring(argStart + 1, argEnd);

            // Because the method name cannot contain spaces, seek the last
            // underscore before the start position of the arguments to extract
            // the method name (this prevents the access qualifiers and return
            // type from being erroneously included)
            String attrName = methodDesc.substring(classEnd + 1, argStart);
            int nameStart = attrName.lastIndexOf('_');
            String descMethodName = attrName.substring(nameStart + 1);

            // Check whether the method names match
            if (!mm.getMethodName().equals(descMethodName)) {
                return false;
            }

            // Seek the last underscore before the start position of the method
            // name to extract the return type (this prevents the access
            // qualifiers from being erroneously included)
            String descAttr = attrName.substring(0, nameStart);
            int retStart = descAttr.lastIndexOf('_');
            String descRet = descAttr.substring(retStart + 1);

            // System.out.println("class name: " + descClassName);
            // System.out.println("method name: " + descMethodName);
            // System.out.println("return type: " + descRet);
            // System.out.println("arg types: " + descArgs);

            String mutSignature = mm.getSignature();
            Type mutReturnType = Type.getReturnType(mutSignature);
            Type[] mutArgTypes = Type.getArgumentTypes(mutSignature);

            // Check whether the return type matches
            if (!mutReturnType.toString().endsWith(descRet)) {
                return false;
            }

            // Check whether the argument types and quantity match
            int i = 0;
            StringTokenizer argTypes = new StringTokenizer(descArgs, "_");
            for ( ; argTypes.hasMoreTokens(); i++) {
                String argType = argTypes.nextToken();
                // System.out.println(descArgType);
                // System.out.println(mutArgTypes[i].toString());

                if (i >= mutArgTypes.length) {
                    return false;
                }

                if (!mutArgTypes[i].toString().endsWith(argType)) {
                    return false;
                }
            }

            if (i < mutArgTypes.length) {
                return false;
            }

            return true;
        }
    }

    /**********************************************************************
     * Creates a MutationTableViewer to display a mutation table.
     * 
     * @param inputFile Name of the mutation table file to be displayed.
     * @param format Output format to be used. Should be one of the
     * following:
     * <ul>
     * <li>MutationTableViewer.LIST</li>
     * <li>MutationTableViewer.DESCRIPTIVE</li>
     * </ul>
     */
    public MutationTableViewer(String inputFile, int format) {
        super(inputFile);
        setOutputFormat(format);
    }

    /**********************************************************************
     * Creates a MutationTableViewer to display a mutation table in the
     * specified format to the specified output file.
     * 
     * @param inputFile Name of the mutation table file to be displayed.
     * @param outputFile Name of the file to which the viewer output should
     * be written.
     * @param format Output format to be used. Should be one of the
     * following:
     * <ul>
     * <li>MutationTableViewer.LIST</li>
     * <li>MutationTableViewer.DESCRIPTIVE</li>
     * </ul>
     */
    public MutationTableViewer(String inputFile, String outputFile, int format)
                               throws SameFileNameException, IOException {
        super(inputFile, outputFile);
        setOutputFormat(format);
    }

    /**********************************************************************
     * Creates a MutationTableViewer to display a mutation table in the
     * specified format to the specified output stream.
     * 
     * @param inputFile Name of the mutation table file to be displayed.
     * @param stream Stream to which the viewer output should be written.
     * @param format Output format to be used. Should be one of the
     * following:
     * <ul>
     * <li>MutationTableViewer.LIST</li>
     * <li>MutationTableViewer.DESCRIPTIVE</li>
     * </ul>
     */
    public MutationTableViewer(String inputFile, OutputStream stream,
                               int format) {
        super(inputFile, stream);
        setOutputFormat(format);
    }

    /*************************************************************************
     * Sets the output format to be used.
     *
     * @param format Output format to be used. Should be one of the
     * following:
     * <ul>
     * <li>MutationTableViewer.LIST</li>
     * <li>MutationTableViewer.DESCRIPTIVE</li>
     * </ul>
     *
     * @throws IllegalArgumentException If the specified output format is
     * not recognized.
     */ 
    public void setOutputFormat(int format) {
        if ((format < 0) || (format > 2)) {
            throw new IllegalArgumentException("Invalid output format");
        }
        this.format = format;
    }

    /*************************************************************************
     * Gets the output format currently set to be used.
     *
     * @return An integer representing the currently specified output
     * format (see {@link MutationTableViewer#setOutputFormat}).
     */ 
    public int getOutputFormat() {
        return format;
    }

    public void print(PrintWriter pw) throws IOException {
        PrintVisitor printer;
        if (forMethod != null) {
            printer = new MethodPrintVisitor(format, pw, forMethod);
        }
        else {
            printer = new PrintVisitor(format, pw);
        }

        MutationIterator mutants = MutationHandler.readMutationFile(inputFile);
        int count = mutants.count();
        for (int i = count; i-- > 0; ) {
            Mutation m = mutants.next();
            if(m instanceof MutationGroup) ((MutationGroup) m).setRequestedVariant(-1);
            try {
                m.accept(printer);
            }
            catch (MutationException e) {
                IOException ioe = new IOException("Error printing mutation");
                ioe.initCause(e);
                throw ioe;
            }
        }
    }

    /*************************************************************************
     * Prints the usage message and exits.
     */ 
    private static void printUsage() {
        System.err.println("Usage: java sofya.viewers.MutationTableViewer " +
                           "<mutation_table_file> [-t <l|d|i>] " +
                           "[-m <method_desc>] [output_file]");
        System.exit(1);
    }

    public static void main(String[] argv) {
        int format = LIST;
        String outputFile = null;
        String forMethod = null;

        if (argv.length < 1) {
            printUsage();
        }

        int argIndex = 1;

        for ( ; argIndex < argv.length; argIndex++) {
            if (argv[argIndex].equals("-t")) {
                argIndex += 1;
                if (argIndex == argv.length) {
                    printUsage();
                }
                if (argv[argIndex].equals("l")) {
                    format = LIST;
                }
                else if (argv[argIndex].equals("d")) {
                    format = DESCRIPTIVE;
                }
                else if (argv[argIndex].equals("i")) {
                    format = IDS;
                }
                else {
                    System.out.println("Invalid output format");
                    printUsage();
                }
            }
            else if (argv[argIndex].equals("-m")) {
                argIndex += 1;
                if (argIndex == argv.length) {
                    printUsage();
                }
                forMethod = argv[argIndex];
            }
            else if (!argv[argIndex].startsWith("-")) {
                break;
            }
        }

        if (argIndex < argv.length) {
            outputFile = argv[argIndex];
        }

        try {
            MutationTableViewer mtv = new MutationTableViewer(argv[0], format);
            if (outputFile != null) {
                mtv.setOutputFile(outputFile);
            }
            mtv.forMethod = forMethod;
            mtv.print();
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (BadFileFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}

