package razen.microforge;

import razen.microforge.compiler.CompileJob;

import java.nio.file.Path;

record ModBuildTarget(String modId, String modName, Path modRoot, Path sourceDir, Path outputDir, Path jarOutput,
                      String sourceEncoding) {
    ModBuildTarget {
        sourceEncoding = CompileJob.normalizeSourceEncoding(sourceEncoding);
    }

    String displayName() {
        if (modName != null && !modName.isBlank()) {
            return modName;
        }
        return modId;
    }
}
