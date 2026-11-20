package ca.yukon.aem.core.directory;

/**
 * The directory has not been loaded, for example because the directory API was unreachable
 * since AEM started.
 */
public class DirectoryUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    public DirectoryUnavailableException(String message) {
        super(message);
    }
}
