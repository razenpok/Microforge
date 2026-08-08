package razen.microforge.core.patch;

import java.io.IOException;
import java.io.Serial;

public final class PatchContributionException extends IOException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String modName;

    PatchContributionException(String modName, String message) {
        super(message);
        this.modName = modName;
    }

    public String modName() {
        return modName;
    }
}
