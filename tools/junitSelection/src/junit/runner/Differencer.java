package junit.runner;

/**
 * Abstract template for file differencing modules, used by
 * {@link junit.textui.SelectionResultPrinter}. Allows dynamic
 * selection of the differencing method.
 *
 * @author Alex Kinneer
 */
public abstract class Differencer {
    /**
     * Diff two files and return <code>true</code> if they
     * are different.
     *
     * @param fName Name of the file to be diffed.
     * @param compDir Path to directory containing the other file
     * of the same name against which <code>fName</code> will be
     * diffed.
     *
     * @return <code>true</code> if the files are different,
     * <code>false</code> otherwise.
     */
    public abstract boolean diff(String fName, String compDir);
    
    /**
     * Diff two files and return <code>true</code> if they
     * are different, using internal information to determine
     * the location of the file to be diffed against.
     *
     * <p>Generally speaking, it is expected that the target path of
     * files to be diffed against will be stored internally and
     * set by a constructor. This is a convenience implementation for
     * situations were the target of the differencing is expected
     * to remain constant.</p>
     *
     * <p>Subclasses not wishing to provide an implementation are
     * not required to override this method. The default implementation
     * throws an UnsupportedOperationException.</p>
     *
     * @param fName Name of the file to be diffed.
     *
     * @return <code>true</code> if the files are different,
     * <code>false</code> otherwise.
     *
     * @throws UnsupportedOperationException If this differencer does
     * not implement this method.
     */
    public boolean diff(String fName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
}

