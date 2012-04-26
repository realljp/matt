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

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.BitSet;
import java.util.zip.*;
import java.util.jar.*;
import java.util.Enumeration;
import java.lang.ref.SoftReference;

import sofya.base.exceptions.*;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Utility;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.generic.Type;

/**
 * Abstract superclass for all handlers.
 *
 * <p>All of the file creation and access methods return handles to file
 * streams. This provides the greatest flexibility to subclasses and
 * external applications code, including the ability to write binary streams
 * to file if the need should arise. For character data files the typical
 * usage is to wrap the stream with a reader or writer, such as a
 * <code>BufferedReader</code> or <code>PrintWriter</code>, a technique that
 * is demonstrated by the current handlers.</p>
 *
 * <p>It is the responsibility of the object receiving the stream to close the
 * stream. If you release all references to the stream without closing it,
 * its resources typically will not be released until the garbage collector
 * runs finalization on the stream object.</p>
 *
 * @author Alex Kinneer
 * @version 12/12/2006
 */
public abstract class Handler {
    /** Convenience string containing the system dependent newline
        character sequence. */
    public static final String LINE_SEP = System.getProperty("line.separator");
    /** Convenience string containing the path to the Sofya
        database directory. */
    private static final String dbDirPath = ProjectDescription.dbDir +
                                            File.separatorChar;

    /** Base prefix of cache directories created in the database directory
        to support disk-backed caches. */
    private static final String CACHE_DIR_PREFIX = dbDirPath + ".cached";
    /** Next available cache ID, used to allow multiple caches without
        contention. */
    private static int nextCacheID = 1;

    /** Compile flag controlling whether certain handler extensions are
        enabled. At this time, controls whether method signatures are
        written to certain database files. */
    protected static final boolean HANDLER_EXTENSIONS = true;
    
    /** Reference to the shared copy buffer used for copying streams;
        reclaimable when memory is low. */
    private static SoftReference<byte[]> copyBuffer =
        new SoftReference<byte[]>(null);
    /** Size of the buffer used when copying streams, in bytes. */
    public static final int COPY_BUFFER_SIZE = 4096;
        
    /*************************************************************************
     * Opens a file in the Sofya database directory for reading.
     *
     * @param fileName Name of the file to be opened, without extension.
     * @param tag Database tag to be associated with the file.
     *
     * @return A FileInputStream which can be used to read from the file.
     *
     * @throws IOException If there is error opening the file (such as a
     * file not found exception).
     */
    public static final FileInputStream openInputFile(
            String fileName, String tag) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }

        String dbFileName;
        if (tag != null) {
            ensureTagExists(tag);
            dbFileName = dbDirPath + tag + File.separatorChar + fileName;
        }
        else {
            dbFileName = dbDirPath + fileName;
        }

        return new FileInputStream(dbFileName);
    }

    /*************************************************************************
     * Opens an abritrary file for reading.
     *
     * @param fileName Name of the file to be opened, including extension and
     * any relative or absolute path required to locate the file.
     *
     * @return A FileInputStream which can be used to read from the file.
     *
     * @throws IOException If there is error opening the file (such as a
     * file not found exception).
     */
    public static final FileInputStream openInputFile(String fileName)
                                        throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileInputStream(fileName);
    }

    /*************************************************************************
     * Opens a file in the Sofya database directory for writing. The
     * file will be created if necessary.
     *
     * @param fileName Name of the file to be opened, without extension.
     * @param tag Database tag to be associated with the file.
     * @param append If <code>true</code>, data will be appended to the end
     * of the file, otherwise the existing contents of the file will be
     * overwritten.
     *
     * @return A FileOutputStream which can be used to write to the file.
     *
     * @throws IOException If there is error creating the file.
     */
    public static final FileOutputStream openOutputFile(String fileName,
            String tag, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }

        String dbFileName;
        if (tag != null) {
            ensureTagExists(tag);
            dbFileName = dbDirPath + tag + File.separatorChar + fileName;
        }
        else {
            dbFileName = dbDirPath + fileName;
        }

        return new FileOutputStream(dbFileName, append);
    }

    /*************************************************************************
     * Opens an arbitrary file for writing. The file will be created if
     * necessary.
     *
     * @param fileName Name of the file to be opened, including extension and
     * any relative or absolute path required to locate the file.
     * @param append If <code>true</code>, data will be appended to the end
     * of the file, otherwise the existing contents of the file will be
     * overwritten.
     *
     * @return A FileOutputStream which can be used to write to the file.
     *
     * @throws IOException If there is error creating the file.
     */
    public static final FileOutputStream openOutputFile(
            String fileName, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileOutputStream(fileName, append);
    }

    /*************************************************************************
     * Creates a new disk cache.
     *
     * @param deleteOnExit Flag specifying whether the cache should be
     * deleted on program exit. This is generally not useful except for
     * debugging. Note also that the cache will not be removed if the
     * contents of the cache are not cleared first.
     *
     * @return A cache handle which permits access to the cache.
     *
     * @throws CacheException If the cache cannot be created.
     */
    public static final CacheHandle newCache(boolean deleteOnExit)
                        throws CacheException {
        File cacheDir = new File(CACHE_DIR_PREFIX + nextCacheID++);
        if (cacheDir.exists()) {
            System.err.println("WARNING: Cache directory already exists. " +
                "This is only a problem if it was not created by an earlier " +
                "session of Sofya.");
        }
        else {
            if (!cacheDir.mkdir()) {
                throw new CacheException("Unable to create cache directory: " +
                    cacheDir.toString());
            }
        }

        if (deleteOnExit) {
            cacheDir.deleteOnExit();
        }

        return new CacheHandle(cacheDir);
    }

    /*************************************************************************
     * Creates a new cache file in a specified cache.
     *
     * @param cHandle Cache handle, as returned by {@link Handler#newCache},
     * which identifies the cache in which the file is to be created.
     * @param fileName Name of the cache file to be created.
     * @param deleteOnExit Flag specifying whether the file should be
     * deleted on program exit.
     *
     * @return A FileOutputStream which can be used to write to the
     * new cache file.
     *
     * @throws IOException If the cache file cannot be created as requested.
     */
    public static final FileOutputStream createCacheFile(CacheHandle cHandle,
            String fileName, boolean deleteOnExit) throws IOException {
        if (cHandle == null) {
            throw new NullPointerException("Null cache handle");
        }
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }

        File cacheFile = new File(cHandle + fileName);
        if (deleteOnExit) {
            cacheFile.deleteOnExit();
        }

        return new FileOutputStream(cacheFile);
    }

    /*************************************************************************
     * Opens a cache file in a specified cache.
     *
     * @param cHandle Cache handle, as returned by {@link Handler#newCache},
     * which identifies the cache containing the file to be opened.
     * @param fileName Name of the cache file to be opened.
     *
     * @return A FileInputStream which can be used to read from the
     * cache file.
     *
     * @throws IOException If the cache file cannot be opened as requested.
     */
    public static final FileInputStream openCacheFile(CacheHandle cHandle,
            String fileName) throws IOException {
        if (cHandle == null) {
            throw new NullPointerException("Null cache handle");
        }
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }

        File cacheFile = new File(cHandle + fileName);
        if (!cacheFile.exists()) {
            throw new FileNotFoundException("Cache file not found");
        }

        return new FileInputStream(cacheFile);
    }

    /*************************************************************************
     * Reads the next non-blank, non-comment line from the stream wrapped
     * by the given reader. A comment line is defined to be one which starts
     * with the comment record character '0' (zero). A blank line is defined
     * to be one which contains only the newline character sequence for the
     * current platform.
     *
     * @param reader Reader from which the next line is to be read.
     *
     * @return The next non-blank, non-comment line available from
     * the reader stream.
     *
     * @throws EOFException If the end of the underlying stream is reached.
     */
    public static final String readNextLine(Reader reader) throws IOException {
        BufferedReader br = null;
        if (reader instanceof BufferedReader) {
            br = (BufferedReader) reader;
        }
        else {
            br = new BufferedReader(reader);
        }
        String curLine = null;
        while (true) {
            curLine = br.readLine();
            if (curLine == null) throw new EOFException();
            if (!(curLine.startsWith("0") || curLine.startsWith(LINE_SEP))) {
                return curLine;
            }
        }
    }

    /*************************************************************************
     * Reads a "<code>prog.lst</code>" file.
     *
     * @param fileName Name of the <code>prog</code> file to be loaded,
     * including the "<code>.prog</code>" portion of the extension.
     * @param tag Database tag associated with the <code>prog</code> file.
     * May be <code>null</code>, in which case the handler will attempt
     * to locate the file in the root database directory.
     * @param unitList <strong>[out]</strong> List into which the classes
     * specified by the <code>prog</code> file will be placed, as
     * sets of classes associated with a location. <b>Any
     * existing contents of the list will be destroyed!</b> The list
     * elements will be instances of {@link ProgramUnit}.
     *
     * @return The number of classes read from the &apos;.prog&apos;
     * file. (This is not the same as the number of elements added
     * to <code>classList</code>.)
     *
     * @throws EmptyFileException If the <code>prog</code> file is
     * empty.
     * @throws BadFileFormatException If there is a parse error reading the
     * <code>prog</code> file.
     * @throws FileNotFoundException If the handler cannot locate a
     * <code>prog</code> file by the given name.
     * @throws IOException On any other I/O error opening or reading
     * the file.
     */
    public static final int readProgFile(String fileName, String tag,
            List<ProgramUnit> unitList) throws EmptyFileException,
                BadFileFormatException, FileNotFoundException, IOException {
        unitList.clear();
        BufferedReader br = null;
        String location = null;
        ProgramUnit currentUnit = null;
        int numRead = 0;

        try {
            br = new BufferedReader(
                 new InputStreamReader(
                     openInputFile(fileName + ".lst", tag)));
            StreamTokenizer stok = new StreamTokenizer(br);

            prepareTokenizer(stok);
            disableParseNumbers(stok);
            stok.eolIsSignificant(false);
            stok.ordinaryChar('{');
            stok.ordinaryChar('}');

            while (stok.nextToken() != StreamTokenizer.TT_EOF) {
                if (stok.ttype == StreamTokenizer.TT_WORD) {
                    location = stok.sval;
                }
                else {
                    throw new LocatableFileException("Expected location " +
                        "string, " + fileName, stok.lineno());
                }

                currentUnit = new ProgramUnit(location);

                if (stok.nextToken() != '{') {
                    throw new LocatableFileException("Expected '{', " +
                        fileName, stok.lineno());
                }

                int inNum = numRead;

                readSet:
                while (true) {
                    switch (stok.nextToken()) {
                    case StreamTokenizer.TT_WORD:
                        currentUnit.addClass(stok.sval);
                        numRead += 1;
                        break;
                    case '}':
                        break readSet;
                    case StreamTokenizer.TT_EOF:
                        throw new DatabaseEOFException("Expected '}', " +
                            fileName);
                    default:
                        throw new DataTypeException(stok.lineno());
                    }
                }

                if (inNum == numRead) {
                    if (currentUnit.isJar) {
                        numRead += readJarClasses(
                            currentUnit.location, currentUnit.classes);
                        unitList.add(currentUnit);
                    }
                    else {
                        System.err.println("NOTE: No classes specified " +
                            "for path entry '" + location + "' (was " +
                            "this intended?)");
                    }
                }
                else {
                    unitList.add(currentUnit);
                }
            }
        }
        catch (FileNotFoundException e) {
            // Improve the diagnostic message
            FileNotFoundException fnfe = new FileNotFoundException(
                "Cannot find list file in database for " + fileName);
            fnfe.fillInStackTrace();
            throw fnfe;
        }
        catch (ZipException e) {
            if (e.getMessage().indexOf("No such file") < 0) {
                // Not sure what this exception is
                throw e;
            }
            // A file not found exception; Not sure why this is
            // reported as a zip exception (or why a file not
            // found exception is not packaged as the cause);
            // Forces us to resort to message parsing.
            FileNotFoundException fnfe = new FileNotFoundException(
                "Cannot find jar or zip file \"" +
                currentUnit.location + "\"");
            fnfe.fillInStackTrace();
            throw fnfe;
        }
        finally {
            try { if (br != null) br.close(); } catch (IOException e) { }
        }

        if (numRead == 0) {
            System.err.println("NOTE: No classes were specified in '" +
                fileName + "' (was this intended?)");
        }

        return numRead;
    }

    /*************************************************************************
     * Utility method extract a list of the class files contained in
     * a jar file.
     *
     * @param jarName Name of the jar file.
     * @param classList <strong>[out]</strong> List into which the classes
     * contained in the jar file will be placed. <em>Previous elements
     * of the list will be preserved.</em>
     *
     * @return The number of class files found in the jar file.
     *
     * @throws IOException On any I/O error opening or reading the specified
     * jar file.
     */
    public static int readJarClasses(String jarName, List<String> classList)
                        throws IOException {
        JarFile sourceJar = new JarFile(jarName);
        int numRead = 0;

        for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = (ZipEntry) e.nextElement();
            String entryName = ze.getName();
            if (ze.isDirectory() || !entryName.endsWith(".class")) continue;

            entryName = entryName.substring(0,
                entryName.lastIndexOf(".class"));
            classList.add(entryName.replace('/', '.'));
            numRead += 1;
        }

        return numRead;
    }
    
    /*************************************************************************
     * Utility method extract a list of the fully resolved paths of class
     * files contained in a jar file.
     *
     * @param jarName Name of the jar file.
     * @param classList <strong>[out]</strong> List into which the resolved
     * paths of classes contained in the jar file will be placed.
     * <em>Previous elements of the list will be preserved.</em>
     *
     * @return The number of class files found in the jar file.
     *
     * @throws IOException On any I/O error opening or reading the specified
     * jar file.
     */
    public static int readJarClassesPaths(String jarName,
            List<String> classList) throws IOException {
        JarFile sourceJar = new JarFile(jarName);
        int numRead = 0;

        for (Enumeration e = sourceJar.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = (ZipEntry) e.nextElement();
            String entryName = ze.getName();
            if (ze.isDirectory() || !entryName.endsWith(".class")) continue;

            classList.add(entryName);
            numRead += 1;
        }

        return numRead;
    }

    /*************************************************************************
     * Utility method to copy a file.
     *
     * <p>Copies the specified source file to the specified destination.
     * Paths may be specified.</p>
     *
     * @param source File to be copied, including path if required.
     * @param dest Name of copied file, including path if required.
     *
     * @return True if the copy was successful, false otherwise.
     * 
     * @deprecated Superceded by version that uses more efficient
     * java.nio solution.
     */
    static final boolean copyFileWithStreams(File source, File dest) {
        BufferedInputStream from = null;
        BufferedOutputStream to = null;
        boolean result = false;
        try {
            from = new BufferedInputStream(new FileInputStream(source));
            to = new BufferedOutputStream(new FileOutputStream(dest));
            byte[] buffer = new byte[4096];
            int bytes_read;
            while((bytes_read = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytes_read);
            }
            result = true;
        }
        catch (Exception e) {
            result = false;
        }
        finally {
            // Always close the streams, even if exceptions were thrown
            if (from != null) try { from.close(); } catch (IOException e) { }
            if (to != null) try { to.close(); } catch (IOException e) { }
        }
        return result;
    }
    
    /*************************************************************************
     * Utility method to copy a file.
     *
     * <p>Copies the specified source file to the specified destination.
     * Paths may be specified.</p>
     *
     * @param source File to be copied, including path if required.
     * @param dest Name of copied file, including path if required.
     *
     * @return True if the copy was successful, false otherwise.
     */
    public static final boolean copyFile(File source, File dest) {
        FileInputStream in = null;
        FileOutputStream out = null;
        
        try {
            if (!dest.exists()) {
                dest.createNewFile();
            }

            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            
            copyFileStream(in, out);
        }
        catch (IOException e) {
            return false;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    // What can the caller do with it?
                }
            }
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    // What can the caller do with it?
                }
            }
        }
        
        return true;
    }
    
    /*************************************************************************
     * Utility method to copy a file, by transferring the contents of a
     * file input stream to a file output stream.
     * 
     * @param in Input stream attached to the file to be copied.
     * @param out Output stream attached to the file to contain the
     * copied data
     * 
     * @throws IOException On any I/O error that prevents successful
     * copying of the input stream to the output stream.
     */
    public static final void copyFileStream(FileInputStream in,
            FileOutputStream out) throws IOException {
        FileChannel source = in.getChannel();
        FileChannel dest = out.getChannel();
        
        long count = 0;
        long size = source.size();
        try {
            while ((count += dest.transferFrom(
                    source, count, size - count)) < size);
        }
        catch (IOException e) {
            // Attempt to deal with flaky filesystems (see
            // http://designdecisions.blogspot.com/2005_12_01_archive.html)
            
            if (count > 0) {
                throw e;
            }
            
            // Use traditional byte-buffer copy technique
            copyStream(in, out, true, false);
        }
    }
    
    /*************************************************************************
     * Utility method to copy the contents of an input stream to an
     * output stream. This method will buffer the streams for efficiency.
     * 
     * <p><strong>Note:</strong> This method does not use a thread-safe
     * copy buffer.</p>
     * 
     * <p>This method provides a basic implementation of stream copying.
     * For more advanced capabilities, see
     * {@link sofya.base.StreamRedirector}.</p>
     * 
     * @param in Input stream to be copied.
     * @param out Target stream to which to copy the contents of the
     * input stream.
     * 
     * @throws IOException On any I/O error that prevents successful
     * copying of the input stream to the output stream.
     */
    public static final void copyStream(InputStream in, OutputStream out)
            throws IOException {
        copyStream(in, out, true, false);
    }
    
    /*************************************************************************
     * Utility method to copy the contents of an input stream to an
     * output stream.
     * 
     * <p>This method provides a basic implementation of stream copying.
     * For more advanced capabilities, see
     * {@link sofya.base.StreamRedirector}.</p>
     * 
     * @param in Input stream to be copied.
     * @param out Target stream to which to copy the contents of the
     * input stream.
     * @param bufferStreams Specifies whether the streams should be
     * buffered for the copy operation; it is desirable to pass
     * <code>false</code> to this argument if the streams are already
     * externally buffered.
     * @param useStackLocalBuffer Indicates whether to use a buffer
     * allocated on the stack. Setting to <code>true</code> effectively
     * renders this method thread-safe (with respect to the copy buffer).
     * By default, this method uses a global, cached buffer to reduce
     * memory thrashing resulting from buffer allocations.
     * 
     * @throws IOException On any I/O error that prevents successful
     * copying of the input stream to the output stream.
     */
    public static final void copyStream(InputStream in, OutputStream out,
            boolean bufferStreams, boolean useStackLocalBuffer)
            throws IOException {
        byte[] buffer;
        if (useStackLocalBuffer) {
            buffer = new byte[COPY_BUFFER_SIZE];
        }
        else {
            buffer = copyBuffer.get();
            if (buffer == null) {
                buffer = new byte[COPY_BUFFER_SIZE];
                copyBuffer = new SoftReference<byte[]>(buffer);
            }
        }
        
        if (bufferStreams) {
            in = new BufferedInputStream(in);
            out = new BufferedOutputStream(out);
        }

        int bytes_read;
        while((bytes_read = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytes_read);
        }
    }

    /*************************************************************************
     * Utility method to open a class file for handling in BCEL.
     *
     * @param className Name of the class to be parsed.
     *
     * @return The BCEL parsed representation of the class.
     *
     * @throws IOException If the class file does not exist or there is an
     * an error reading the file.
     * @throws ClassFormatException If the class file is malformed.
     */
    public static final JavaClass parseClass(String className)
            throws IOException, ClassFormatException {
        try {
            return Repository.lookupClass(className);
        }
        catch (ClassNotFoundException e) {
            // Maybe it's an absolute (or otherwise qualified) path

            // The folks working on BCEL (as of 5.2) seem to have forgotten
            // how to write correct file handling code, so we have to
            // make sure the file exists ourselves or we might get
            // a null pointer exception
            File f = new File(className);
            if (!f.exists()) {
                throw new FileNotFoundException("Cannot find class: " +
                    className);
            }
            return new ClassParser(className).parse();
        }
    }

    /*************************************************************************
     * Utility method to convert method signatures to a common format.
     *
     * <p>The class name is concatenated with the signature
     * of the method, separated by a provided delimiter character. All
     * spaces, tabs, and newlines in the resulting string are then
     * converted to underscores. Sequences of underscores are reduced to
     * a single underscore, except when an underscore is present in the
     * method name itself.</p>
     *
     * @param fullClassName Name of the class which will be inserted into
     * the formatted string. This should be the fully package qualified
     * name of the class to guarantee uniqueness.
     * @param mSignature Signature of the method to be inserted into the
     * formatted string, typically obtained using BCEL.
     * @param classDelimiter Delimiter character that will be inserted
     * between the class name and method signature.
     *
     * @return The concatenation of the class name, delimiter, and
     * method signature with whitespace converted to underscores. This
     * should be a universally unique identifier for the method, as long
     * as the constraints on the parameters are met.
     */
    public static final String formatSignature(String fullClassName,
            String mSignature, char classDelimiter) {
        StringBuilder sb =
            new StringBuilder(fullClassName + classDelimiter + mSignature);
        boolean replacedLast = false;
        int n = 0;
        while (n < sb.length()) {
            if ((sb.charAt(n) == ' ') || (sb.charAt(n) == '\n')
                    || (sb.charAt(n) == '\t')) {
                if (replacedLast) {
                    sb.delete(n, n + 1);
                    continue;
                }
                else {
                    sb.replace(n, n + 1, "_");
                    replacedLast = true;
                }
            }
            else {
                replacedLast = false;
            }
            n++;
        }
        return sb.toString();
    }
    
    /*************************************************************************
     * Packs a class name and method into a string to be used in
     * instrumentation.
     * 
     * <p>Note that the format used contains the information necessary to
     * reconstruct the legacy representation of method signatures used
     * in certain Sofya tools.</p>
     * 
     * @param fullClassName Fully qualified name of the class implementing
     * the method.
     * @param method BCEL representation of the method.
     * 
     * @return The formatted string identifying the method, to be
     * used by the instrumentor.
     */
    public static final String packSignature(String fullClassName,
            Method method) {
        StringBuilder sb = new StringBuilder(fullClassName);
        sb.append("@");
        sb.append(method.getName());
        sb.append("#");
        sb.append(method.getAccessFlags());
        sb.append("#");
        int n = sb.length();

        String declString = method.toString();
        int cutIndex = declString.indexOf('(');
        sb.append(declString.substring(cutIndex));
        
        boolean replacedLast = false;
        while (n < sb.length()) {
            if ((sb.charAt(n) == ' ') || (sb.charAt(n) == '\n')
                    || (sb.charAt(n) == '\t')) {
                if (replacedLast) {
                    sb.delete(n, n + 1);
                    continue;
                }
                else {
                    sb.replace(n, n + 1, "_");
                    replacedLast = true;
                }
            }
            else {
                replacedLast = false;
            }
            n++;
        }
        
        sb.append("#");
        sb.append(method.getReturnType().getSignature());
        
        return sb.toString();
    }

    /*************************************************************************
     * Unpacks an instrumentor method identifier string into the legacy
     * string representation used by Sofya.
     * 
     * @param signature Packed signature string used by the instrumentor
     * to identify a method.
     * 
     * @return The string representation of the method declaration, in
     * the traditional format used by Sofya.
     */
    public static final String unpackSignature(String signature) {
        String[] parts = signature.split("[#@]");
        
        StringBuilder sb = new StringBuilder(parts[0]);
        sb.append(".");
        int n = sb.length();
        
        // Add access flags
        String accString = Utility.accessToString(Integer.parseInt(parts[2]));
        if (accString.length() > 0) {
            sb.append(accString);
            // Convert whitespace characters to 'safe' characters
            boolean replacedLast = false;
            while (n < sb.length()) {
                if ((sb.charAt(n) == ' ') || (sb.charAt(n) == '\n')
                        || (sb.charAt(n) == '\t')) {
                    if (replacedLast) {
                        sb.delete(n, n + 1);
                        continue;
                    }
                    else {
                        sb.replace(n, n + 1, "_");
                        replacedLast = true;
                    }
                }
                else {
                    replacedLast = false;
                }
                n++;
            }
            sb.append("_");
        }
        
        // Return type
        sb.append(Utility.compactClassName(Type.getType(parts[4]).toString()));
        sb.append("_");
        
        // Name
        sb.append(parts[1]);
        
        // Slice of Method.toString()
        sb.append(parts[3]);
        
        return sb.toString();
    }
    
    /**************************************************************************
     * Converts a bit vector to a string containing its hexadecimal equivalent.
     *
     * <p>The bit vector will be end-padded to the nearest multiple of four
     * and converted to a contiguous string of hexadecimal digits.</p>
     *
     * @param bv Bit vector to be converted to a hexadecimal string.
     * @param size Number of bits in the vector to be included in the
     * conversion - any bits in the vector past this value are ignored.
     *
     * @return A string containing the hexadecimal equivalent of the given
     * bit vector.
     */
    protected static String toHex(BitSet bv, int size) {
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (bv.get(i)) {
                bits.append("1");
            }
            else {
                bits.append("0");
            }
        }
        // Pad the end with zeros to nearest length of four
        int padLength = (bits.length() % 4);
        if (padLength != 0) {
            for (int i = 0; i < (4 - padLength); i++) {
                bits.append("0");
            }
        }
        // Convert to hex - Integer.parseInt doesn't like extremely large
        // binary inputs so we do it ourselves four bits at a time
        String bitString = bits.toString();
        bits.delete(0, bits.length());
        for (int i = 0; i < bitString.length(); i += 4) {
            bits.append(Integer.toString(Integer.parseInt(
                bitString.substring(i, i + 4), 2), 16));
        }
        return bits.toString();
    }

    /**************************************************************************
     * Converts a hexadecimal string to a bit vector representing its binary
     * equivalent.
     *
     * <p>The hexadecimal string should be a contiguous string of hexadecimal
     * digits with no special formatting.</p>
     *
     * @param hexString String of hexadecimal characters encoding a bitset.
     *
     * @return A bit vector containing the binary equivalent of the given
     * hexadecimal string.
     */
    protected static BitSet toBinary(String hexString) {
        BitSet bv = new BitSet(hexString.length() * 4);
        int bitGroup;
        for (int i = 0; i < hexString.length(); i++) {
             bitGroup = Character.digit(hexString.charAt(i), 16);
             for (int j = 0; j < 4; j++) {
                 if ((bitGroup & 0x00000008) == 0x00000008) {
                     bv.set((i * 4) + j);
                 }
                 bitGroup = bitGroup << 1;
             }
        }
        return bv;
    }

    /*************************************************************************
     * Checks whether a given tag exists and creates it if necessary.
     *
     * @param tag Name of the tag to be checked for existence and
     * created if necessary.
     * 
     * @return <code>true</code> if the tag already existed,
     * <code>false</code> if the tag was created by this call.
     *
     * @throws IOException If there is an error creating the tag, when
     * necessary.
     */
    public static boolean ensureTagExists(String tag) throws IOException {
        File f = new File(ProjectDescription.dbDir + File.separatorChar +
             tag + File.separatorChar);
        if (!f.exists()) {
            if (!f.mkdir()) {
                throw new IOException("Unable to ensure " +
                    "existence of tag '" + tag + "'");
            }
            return false;
        }
        else {
            return true;
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Theoretically it might be better to subclass StreamTokenizer to
    // implement the following methods, but static method calls simply bind
    // faster and the conversion of some handlers to use a StreamTokenizer
    // was a performance consideration.

    /*************************************************************************
     * Sets the syntax table of a stream tokenizer to a standard
     * configuration useful for most handlers.
     *
     * <p>The tokenizer is configured as follows:
     * <ul>
     * <li>All characters between ASCII 0 (\0) and ASCII 32 (' ') are
     * considered whitespace (token delimiters).</li>
     * <li>All other characters are considered word constituents.</li>
     * <li>Numbers are parsed (numbers with no adjoining alpha characters
     * will not be parsed as strings).</li>
     * <li>Line terminators are returned as separate tokens (this assists
     * in tracking position within the stream).</li>
     * </ul></p>
     *
     * @param stok Tokenizer to be configured.
     */
    protected static void prepareTokenizer(StreamTokenizer stok) {
        stok.resetSyntax();
        stok.parseNumbers();
        stok.whitespaceChars(0, ' ');
        stok.wordChars(33, 255);
        stok.eolIsSignificant(true);
    }

    /*************************************************************************
     * Modifies the syntax table of a stream tokenizer to disable parsing
     * of numbers.
     *
     * <p>This method properly reverses the actions performed by
     * <code>StreamTokenizer.parseNumbers()</code> since that class does
     * not itself provide any method for accomplishing that task.</p>
     *
     * @param stok Tokenizer to be configured.
     */
    public static void disableParseNumbers(StreamTokenizer stok) {
        // StreamTokenizer is a very poorly documented class, so this
        // method is a result of some internal inspection. Setting
        // the digits to be word characters does not override their
        // classification as digit characters, thus the only way to
        // undo the effects of parseNumbers() is to make them
        // ordinary characters first.
        stok.ordinaryChars('0', '9');
        stok.ordinaryChar('.');
        stok.ordinaryChar('-');
        stok.wordChars('0', '9');
        stok.wordChars('.', '.');
        stok.wordChars('-', '-');
    }

    /*************************************************************************
     * Reads an integer off of a stream tokenizer.
     *
     * @param stok Tokenizer from which an integer is to be read.
     *
     * @return An integer value from the stream being tokenized, if there
     * is in fact a token available and it is a numeric token (floating
     * point values will be truncated).
     *
     * @throws EOLException If the tokenizer is at the end of a line.
     * @throws DatabaseEOFException If the tokenizer reaches the end of the
     * underlying database file.
     * @throws DataTypeException If the next token in the stream is not
     * a number.
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static int readInt(StreamTokenizer stok)
            throws EOLException, DatabaseEOFException, DataTypeException,
                   IOException {
        switch (stok.nextToken()) {
        case StreamTokenizer.TT_NUMBER:
            return (int) stok.nval;
        case StreamTokenizer.TT_EOL:
            throw new EOLException(stok.lineno() - 1);
        case StreamTokenizer.TT_EOF:
            throw new DatabaseEOFException();
        default:
            throw new DataTypeException("Invalid data in file: found \"" +
                ((stok.ttype == StreamTokenizer.TT_WORD) ? stok.sval :
                    (char) stok.ttype) + "\" where integer " +
                "was expected", stok.lineno());
        }
    }

    /*************************************************************************
     * Reads an integer off of a stream tokenizer, ignoring line endings.
     *
     * @param stok Tokenizer from which an integer is to be read.
     *
     * @return An integer value from the stream being tokenized, if there
     * is in fact a token available and it is a numeric token (floating
     * point values will be truncated).
     *
     * @throws DatabaseEOFException If the tokenizer reaches the end of the
     * underlying database file.
     * @throws DataTypeException If the next token in the stream is not
     * a number.
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static int readIntIgnoreEOL(StreamTokenizer stok)
            throws DatabaseEOFException, DataTypeException, IOException {
        while (true) {
            switch (stok.nextToken()) {
            case StreamTokenizer.TT_NUMBER:
                return (int) stok.nval;
            case StreamTokenizer.TT_EOL:
                continue;
            case StreamTokenizer.TT_EOF:
                throw new DatabaseEOFException();
            default:
                throw new DataTypeException("Invalid data in file: found \"" +
                    ((stok.ttype == StreamTokenizer.TT_WORD) ? stok.sval :
                        (char) stok.ttype) + "\" where integer " +
                    "was expected", stok.lineno());
            }
        }
    }

    /*************************************************************************
     * Reads a string off of a stream tokenizer.
     *
     * @param stok Tokenizer from which a string is to be read.
     *
     * @return A string from the stream being tokenized, if there
     * is in fact a token available and it is a word token.
     *
     * @throws EOLException If the tokenizer is at the end of a line.
     * @throws DatabaseEOFException If the tokenizer reaches the end of the
     * underlying database file.
     * @throws DataTypeException If the next token in the stream is not
     * a word/string.
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static String readString(StreamTokenizer stok)
            throws EOLException, DataTypeException, DatabaseEOFException,
                   IOException {
        switch (stok.nextToken()) {
        case StreamTokenizer.TT_WORD:
            return stok.sval;
        case StreamTokenizer.TT_EOL:
            throw new EOLException(stok.lineno() - 1);
        case StreamTokenizer.TT_EOF:
            throw new DatabaseEOFException();
        default:
            throw new DataTypeException("Invalid data in file: found " +
                ((stok.ttype == StreamTokenizer.TT_NUMBER) ? "numeric " +
                    "value \"" + stok.nval + "\"":
                    "\"" + (char) stok.ttype + "\"") +
                " where string was expected", stok.lineno());
        }
    }

    /**
     * Reads the next available string off a stream tokenizer,
     * ignoring line endings.
     * 
     * @param stok Tokenizer from which a string is to be read.
     * 
     * @return A string from the stream being tokenized, if there
     * is in fact a token available and it is a word token.
     * 
     * @throws DatabaseEOFException If the tokenizer reaches the end of the
     * underlying database file.
     * @throws DataTypeException If the next token in the stream is not
     * a word/string.
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static String readStringIgnoreEOL(StreamTokenizer stok)
            throws DatabaseEOFException, DataTypeException, IOException {
        while (true) {
            switch (stok.nextToken()) {
            case StreamTokenizer.TT_WORD:
                return stok.sval;
            case StreamTokenizer.TT_EOL:
                continue;
            case StreamTokenizer.TT_EOF:
                throw new DatabaseEOFException();
            default:
                throw new DataTypeException("Invalid data in file: found " +
                        ((stok.ttype == StreamTokenizer.TT_NUMBER) ? "numeric " +
                            "value \"" + stok.nval + "\"":
                            "\"" + (char) stok.ttype + "\"") +
                        " where string was expected", stok.lineno());
            }
        }
    }

    /*************************************************************************
     * Reports whether an integer is available to be read next from a
     * stream tokenizer.
     *
     * @param stok Tokenizer to check for the availability of an integer
     * to be read.
     * @param ignoreEOL Specifies whether the tokenizer should ignore
     * line endings in the process of determining whether an integer
     * is available to read.
     *
     * @return <code>true</code> if an integer is available as the next
     * token to be read from the stream, <code>false</code> otherwise.
     *
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static boolean isIntAvailable(StreamTokenizer stok,
            boolean ignoreEOL) throws IOException {
        boolean isAvailable = false;
        
        scanLoop:
        while (true) {
            switch (stok.nextToken()) {
            case StreamTokenizer.TT_NUMBER:
                isAvailable = true;
                break scanLoop;
            case StreamTokenizer.TT_EOL:
                if (ignoreEOL) {
                    continue;
                }
                else {
                    break scanLoop;
                }
            default:
                break scanLoop;
            }
        }
    
        stok.pushBack();
        return isAvailable;
    }

    /*************************************************************************
     * Reports whether a string is available to be read next from a
     * stream tokenizer.
     *
     * @param stok Tokenizer to check for the availability of a string
     * to be read.
     * @param ignoreEOL Specifies whether the tokenizer should ignore
     * line endings in the process of determining whether a string
     * is available to read.
     *
     * @return <code>true</code> if a string is available as the next
     * token to be read from the stream, <code>false</code> otherwise.
     *
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static boolean isStringAvailable(StreamTokenizer stok,
            boolean ignoreEOL) throws IOException {
        boolean isAvailable = false;
        
        scanLoop:
        while (true) {
            switch (stok.nextToken()) {
            case StreamTokenizer.TT_WORD:
                isAvailable = true;
                break scanLoop;
            case StreamTokenizer.TT_EOL:
                if (ignoreEOL) {
                    continue;
                }
                else {
                    break scanLoop;
                }
            default:
                break scanLoop;
            }
        }
    
        stok.pushBack();
        return isAvailable;
    }

    /*************************************************************************
     * Advances a stream tokenizer through the next end-of-line token in
     * the stream.
     *
     * <p>After calling this method, the token type of the tokenizer
     * will be <code>StreamTokenizer.TT_EOL</code>, and the next call to
     * <code>nextToken()</code> will return the first token on the next
     * line.</p>
     *
     * @param stok Tokenizer to be advanced to the end of the current line.
     *
     * @return <code>true</code> if the tokenizer was successfully advanced
     * to the end of the line, <code>false</code> if the end of the underlying
     * file was reached instead.
     *
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static boolean readToEOL(StreamTokenizer stok) throws IOException {
        int tokenType;
        do {
            tokenType = stok.nextToken();
            if (tokenType == StreamTokenizer.TT_EOF) {
                return false;
            }
        }
        while (tokenType != StreamTokenizer.TT_EOL);
        return true;
    }

    /*************************************************************************
     * Advances a stream tokenizer to point to the first token of the next
     * data line in a database file.
     *
     * <p>This method is the equivalent of {@link #readNextLine(Reader)}
     * for database files being read through a StreamTokenizer.</p>
     *
     * @param stok Tokenizer to be advanced to the next data line.
     *
     * @return <code>true</code> if the tokenizer was successfully advanced
     * to the next data line, <code>false</code> if the end of the underlying
     * file was reached instead.
     *
     * @throws BadFileFormatException If a line is encountered in the
     * underlying database file which does not begin with a line type
     * indicator code.
     * @throws IOException For any I/O error raised when attempting to
     * read the underlying stream.
     */
    public static boolean readToNextDataLine(StreamTokenizer stok)
                          throws BadFileFormatException, IOException {
        boolean startOfLine = (stok.ttype == StreamTokenizer.TT_EOL);
        tokenLoop: do {
            switch (stok.nextToken()) {
            case StreamTokenizer.TT_NUMBER:
                if (startOfLine) {
                    if (stok.nval == 0) {
                        if (!readToEOL(stok)) {
                            break tokenLoop;
                        }
                        continue;
                    }
                    else {
                        stok.pushBack();
                        return true;
                    }
                }
                break;
            case StreamTokenizer.TT_WORD:
                if (startOfLine) {
                    throw new BadFileFormatException("Missing line data " +
                        "type code at line " + stok.lineno());
                }
                break;
            case StreamTokenizer.TT_EOL:
                startOfLine = true;
                break;
            case StreamTokenizer.TT_EOF:
                break tokenLoop;
            default:
                continue;
            }
        }
        while (true);

        return false;
    }
}
