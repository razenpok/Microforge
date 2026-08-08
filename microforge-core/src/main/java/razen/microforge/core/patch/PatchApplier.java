package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PatchApplier {
    private PatchApplier() {
    }

    public static Set<Path> apply(Path sourceRoot, UnifiedPatch patch) throws IOException {
        var normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();
        var patchedRoot = normalizedSourceRoot.resolveSibling("patched");
        FileOperations.deleteRecursively(patchedRoot);
        Files.createDirectories(patchedRoot);
        if (patch != null) {
            for (var filePatch : patch.files()) {
                if (filePatch.added()) {
                    continue;
                }
                var source = FileOperations.resolveInside(normalizedSourceRoot, filePatch.path());
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                var target = FileOperations.resolveInside(patchedRoot, filePatch.path());
                var parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return applyInPlace(patchedRoot, patch);
    }

    private static Set<Path> applyInPlace(Path sourceRoot, UnifiedPatch patch) throws IOException {
        var changed = new LinkedHashSet<Path>();
        if (patch == null || patch.isEmpty()) {
            return changed;
        }

        for (var filePatch : patch.files()) {
            if (filePatch.deleted()) {
                throw new IOException("removing Starsector source files is not supported: " + filePatch.path());
            }

            var source = FileOperations.resolveInside(sourceRoot, filePatch.path());
            var before = Files.isRegularFile(source)
                    ? SourceText.readPristineFile(source).lines()
                    : List.<String>of();
            if (!Files.isRegularFile(source) && !filePatch.added()) {
                throw new IOException("merged patch targets missing source " + filePatch.path());
            }
            PatchEdits.validateHunks(filePatch, before, "merged patch");
            var owned = PatchEdits.edits(filePatch).stream()
                    .map(edit -> new PatchEdits.OwnedEdit("merged patch", edit))
                    .toList();
            var after = PatchEdits.apply(before, owned);
            var parent = source.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(source, String.join("\n", after) + "\n", StandardCharsets.UTF_8);
            changed.add(source);
        }
        return changed;
    }
}
