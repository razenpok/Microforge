package razen.microforge.cli;

import java.io.Serial;

final class BuildException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    BuildException(String message) {
        super(message);
    }

    BuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
