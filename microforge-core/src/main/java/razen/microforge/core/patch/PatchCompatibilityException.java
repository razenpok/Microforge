package razen.microforge.core.patch;

import java.io.IOException;
import java.io.Serial;

public final class PatchCompatibilityException extends IOException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String details;

    public PatchCompatibilityException(String message) {
        this(message, null, null);
    }

    public PatchCompatibilityException(String message, Throwable cause) {
        this(message, null, cause);
    }

    PatchCompatibilityException(String message, String details, Throwable cause) {
        super(message, cause);
        this.details = details;
    }

    public String details() {
        return details;
    }
}
