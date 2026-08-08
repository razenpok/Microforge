package razen.microforge.core.patch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

public final class VersionedPatchLoader {
    private static final String PATCH_EXTENSION = ".patch";

    private VersionedPatchLoader() {
    }

    public static Optional<Result> loadBest(Path patchRoot, Path pristineRoot, GameVersion currentVersion,
                                            String modName) throws IOException {
        var selection = select(patchRoot, currentVersion, modName);
        return selection.isEmpty()
                ? Optional.empty()
                : Optional.of(load(pristineRoot, selection.get(), currentVersion, modName));
    }

    public static Optional<Selection> select(Path patchRoot, GameVersion currentVersion, String modName)
            throws IOException {
        if (!Files.isDirectory(patchRoot)) {
            return Optional.empty();
        }

        var available = new ArrayList<Selection>();
        try (var paths = Files.list(patchRoot)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(PATCH_EXTENSION))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .toList()) {
                var fileName = path.getFileName().toString();
                var versionText = fileName.substring(0, fileName.length() - PATCH_EXTENSION.length());
                final GameVersion version;
                try {
                    version = GameVersion.parse(versionText);
                } catch (IllegalArgumentException e) {
                    throw new IOException("invalid versioned patch name in mod '" + modName + "': " + path, e);
                }
                available.add(new Selection(path.toAbsolutePath().normalize(), version));
            }
        }
        if (available.isEmpty()) {
            return Optional.empty();
        }

        var selected = available.stream()
                .filter(selection -> selection.version().compareTo(currentVersion) <= 0)
                .max(Comparator.comparing(Selection::version))
                .orElse(null);
        if (selected == null) {
            var details = "Mod '" + modName + "' has no Starsector API patch compatible with Starsector "
                    + currentVersion + ". Available patches: " + available.stream()
                    .map(selection -> selection.version().toString())
                    .collect(java.util.stream.Collectors.joining(", ")) + ".";
            throw PatchCompatibility.userFacingFailure(modName, currentVersion, details, null);
        }
        return Optional.of(selected);
    }

    public static Result load(Path pristineRoot, Selection selection,
                              GameVersion currentVersion, String modName) throws IOException {
        try {
            var patch = UnifiedPatch.parse(
                    Files.readString(selection.path(), StandardCharsets.UTF_8), modName);
            if (selection.version().equals(currentVersion)) {
                return new Result(selection, patch, false);
            }

            var compatibility = PatchCompatibility.adapt(pristineRoot, patch, modName,
                    selection.version(), currentVersion);
            return new Result(selection, compatibility.patch(), compatibility.remapped());
        } catch (PatchCompatibilityException e) {
            throw e;
        } catch (IOException e) {
            if (selection.version().equals(currentVersion)) {
                throw PatchCompatibility.failure(modName, currentVersion, e);
            }
            throw PatchCompatibility.failure(modName, selection.version(), currentVersion, e);
        }
    }

    public record Selection(Path path, GameVersion version) {
        public boolean isExact(GameVersion currentVersion) {
            return version.equals(currentVersion);
        }
    }

    public record Result(Selection selection, UnifiedPatch patch, boolean remapped) {
        public GameVersion version() {
            return selection.version();
        }

        public boolean isExact(GameVersion currentVersion) {
            return selection.isExact(currentVersion);
        }
    }
}
