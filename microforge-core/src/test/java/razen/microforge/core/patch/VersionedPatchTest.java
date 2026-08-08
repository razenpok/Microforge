package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static razen.microforge.core.patch.PatchTestSupport.cleanup;
import static razen.microforge.core.patch.PatchTestSupport.require;
import static razen.microforge.core.patch.PatchTestSupport.source;
import static razen.microforge.core.patch.PatchTestSupport.write;

public final class VersionedPatchTest {
    private VersionedPatchTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("VersionedPatchTest passed");
    }

    public static void run() throws Exception {
        loadAndRemapVersionedPatch();
        gameVersionChangesPatchFingerprint();
    }

    private static void loadAndRemapVersionedPatch() throws Exception {
        var temp = Files.createTempDirectory("microforge-versioned-patch-");
        try {
            var pristine = temp.resolve("pristine");
            var modified = temp.resolve("modified");
            var shifted = temp.resolve("shifted");
            var patchRoot = temp.resolve("patches");
            write(pristine, "test/A.java", source("two", "eight"));
            FileOperations.copyTree(pristine, modified);
            write(modified, "test/A.java", source("TWO", "eight"));
            Files.createDirectories(patchRoot);
            Files.writeString(patchRoot.resolve("0.98a-RC8.patch"),
                    SourceDiffer.createDiff(pristine, modified).serialize(), StandardCharsets.UTF_8);

            var currentVersion = GameVersion.parse("0.98a-RC11");
            var selection = VersionedPatchLoader.select(patchRoot, currentVersion, "test").orElseThrow();
            require(!selection.isExact(currentVersion), "older patch was incorrectly selected as exact");
            var loaded = VersionedPatchLoader.load(pristine, selection, currentVersion, "test");
            require(!loaded.remapped(), "matching compatibility patch was unnecessarily remapped");

            write(shifted, "test/A.java", "// shifted\n" + source("two", "eight"));
            require(VersionedPatchLoader.load(shifted, selection, currentVersion, "test").remapped(),
                    "shifted compatibility patch was not remapped");
        } finally {
            cleanup(temp);
        }
    }

    private static void gameVersionChangesPatchFingerprint() {
        var patch = UnifiedPatch.empty();
        var first = PatchFingerprint.hash(GameVersion.parse("0.98a-RC8"), patch);
        var second = PatchFingerprint.hash(GameVersion.parse("0.98a-RC11"), patch);
        require(!first.equals(second), "game version did not change the patch fingerprint");
    }
}
