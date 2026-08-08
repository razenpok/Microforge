package razen.microforge.core.compile;

import java.io.Serial;

public final class CompilerException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public CompilerException(String message) {
        super(message);
    }
}
