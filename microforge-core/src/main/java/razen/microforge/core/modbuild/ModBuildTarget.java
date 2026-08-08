package razen.microforge.core.modbuild;

import razen.microforge.core.compile.CompileJob;

import java.nio.file.Path;

public record ModBuildTarget(String modId, String modName, Path sourceDir, Path outputDir, Path jarOutput,
                             String sourceEncoding) {
    public ModBuildTarget {
        sourceEncoding = CompileJob.normalizeSourceEncoding(sourceEncoding);
    }

    public String displayName() {
        return modName == null || modName.isBlank() ? modId : modName;
    }
}
