package junit.runner;

import java.io.File;
import java.io.IOException;

/**
 * An implementation of Differencer which uses the Unix command
 * 'cmp -s' to diff the two files. Naturally this implementation is
 * platform dependent.
 *
 * @author Alex Kinneer
 * @version 10/08/2003
 */
public class UnixShellDifferencer extends Differencer {
	
    /** Path to directory containing files to be diffed against. */
    private String compDir = null;
    
    /**
     * Constructs a UnixShellDifferencer.
     */
    public UnixShellDifferencer() { }
    
    /**
     * Constructs a UnixShellDifferencer, which uses the specified
     * directory as the target for differencing.
     *
     * @see UnixShellDifferencer#diff(String)
     */
    public UnixShellDifferencer(String compDir) {
        if (compDir.charAt(compDir.length() - 1) != File.separatorChar) {
            this.compDir = compDir + File.separatorChar;
        }
        else {
            this.compDir = compDir;
        }
    }

    /**
     * Diffs a file against a file of the same name in the given
     * directory and returns <code>true</code> if the files are
     * different.
     *
     * @param fName Name of the file to be differenced.
     * @param compDir Path to the directory containing the other version
     * of the file to be diffed against.
     *
     * @return <code>true</code> if the files are different,
     * <code>false</code> if they are the same.
     */
    public boolean diff(String fName, String compDir) {
        if (compDir.charAt(compDir.length() - 1) != File.separatorChar) {
            compDir += File.separatorChar;
        }
        return execDiff(fName, compDir);
    }
    
    /**
     * Diff two files and return <code>true</code> if they
     * are different, using the directory given in the constructor
     * to locate target files for diffing.
     *
     * @param fName Name of the file to be diffed.
     *
     * @return <code>true</code> if the files are different,
     * <code>false</code> otherwise.
     *
     * @throws UnsupportedOperationException If this differencer was
     * created using the no-argument constructor.
     */
    public boolean diff(String fName) throws UnsupportedOperationException {
        if (this.compDir == null) {
            throw new UnsupportedOperationException("Target directory for diffing " +
                "was not specified in constructor");
        }
        return execDiff(fName, this.compDir);
    }
    
    /**
     * Executes 'cmp -s' to diff the two files.
     *
     * @param fName Name of the file to be diffed.
     * @param compDir Directory where the target for diffing can be found.
     *
     * @return <code>true</code> if the files are different,
     * <code>false</code> otherwise.
     */
    private boolean execDiff(String fName, String compDir) {
        Process diffProc = null;
        
        String baseName;
        int pos;
        if ((pos = fName.lastIndexOf(File.separatorChar)) != -1) {
            baseName = fName.substring(pos + 1, fName.length());
        }
        else {
            baseName = fName;
        }
        
        // Note: 'cmp -s' shouldn't produce any console output. For a
        // command that does, we would need to read the output streams.
        try {
            diffProc = Runtime.getRuntime().exec("cmp -s " + fName + " " +
                                                 compDir + baseName);
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException("Error executing 'cmp'");
        }
        
        try {
            return (diffProc.waitFor() != 0);
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted waiting for 'cmp' to complete");
        }
    }
}