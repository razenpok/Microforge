package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PatchCompatibility {
    private PatchCompatibility() {
    }

    public static Result adapt(Path pristineRoot, UnifiedPatch patch, String modName,
                               GameVersion patchVersion, GameVersion currentVersion) throws IOException {
        var files = new ArrayList<UnifiedPatch.FilePatch>();
        var remapped = false;
        for (var filePatch : patch.files()) {
            if (filePatch.added()) {
                files.add(filePatch);
                continue;
            }

            var source = FileOperations.resolveInside(pristineRoot, filePatch.path());
            if (!Files.isRegularFile(source)) {
                throw incompatible(modName, patchVersion, currentVersion, filePatch.path(), 1,
                        "the source file no longer exists");
            }
            var pristine = SourceText.readPristineFile(source).lines();
            var hunks = new ArrayList<UnifiedPatch.Hunk>();
            for (var hunk : filePatch.hunks()) {
                var oldLines = oldLines(hunk);
                var originalIndex = hunk.oldStart() == 0 ? 0 : hunk.oldStart() - 1;
                if (matchesAt(pristine, oldLines, originalIndex)) {
                    hunks.add(hunk);
                    continue;
                }

                var match = closestMatch(pristine, oldLines, originalIndex);
                if (match < 0) {
                    throw incompatible(modName, patchVersion, currentVersion, filePatch.path(),
                            Math.max(1, hunk.oldStart()), "a patch hunk could not be mapped to the current source");
                }
                var remappedOldStart = hunk.oldStart() == 0 ? match : match + 1;
                var delta = remappedOldStart - hunk.oldStart();
                hunks.add(new UnifiedPatch.Hunk(remappedOldStart,
                        Math.max(0, hunk.newStart() + delta), hunk.lines()));
                remapped = true;
            }
            files.add(new UnifiedPatch.FilePatch(filePatch.path(), filePatch.added(), filePatch.deleted(), hunks));
        }
        return new Result(new UnifiedPatch(files), remapped);
    }

    public static PatchCompatibilityException failure(String modName, GameVersion patchVersion,
                                                       GameVersion currentVersion, Throwable cause) {
        var details = "Compatibility patch " + patchVersion + " from mod '" + modName
                + "' could not be applied to Starsector " + currentVersion + ".";
        return userFacingFailure(modName, currentVersion, details, cause);
    }

    public static PatchCompatibilityException failure(String modName, GameVersion currentVersion,
                                                       Throwable cause) {
        var details = "Starsector API patch from mod '" + modName + "' could not be applied to Starsector "
                + currentVersion + ".";
        return userFacingFailure(modName, currentVersion, details, cause);
    }

    public static PatchCompatibilityException preparedSourcesMismatch(String modName, GameVersion currentVersion,
                                                                       Path sources) {
        var details = "Prepared Starsector API sources from mod '" + modName + "' at " + sources
                + " do not match Starsector " + currentVersion + ".";
        return new PatchCompatibilityException(
                "Prepared Starsector API sources for mod '" + modName + "' do not match Starsector "
                        + currentVersion + ".\nRun Microforge CLI prepare again before launching the game.",
                details, null);
    }

    private static List<String> oldLines(UnifiedPatch.Hunk hunk) {
        return hunk.lines().stream()
                .filter(line -> line.kind() != UnifiedPatch.Kind.ADD)
                .map(UnifiedPatch.PatchLine::text)
                .toList();
    }

    private static boolean matchesAt(List<String> pristine, List<String> expected, int start) {
        return start >= 0 && start + expected.size() <= pristine.size()
                && PatchEdits.sourceLinesMatch(pristine.subList(start, start + expected.size()), expected);
    }

    private static int closestMatch(List<String> pristine, List<String> expected, int originalIndex) {
        if (expected.isEmpty()) {
            return -1;
        }
        return java.util.stream.IntStream.rangeClosed(0, pristine.size() - expected.size())
                .filter(start -> matchesAt(pristine, expected, start))
                .boxed()
                .min(Comparator.comparingInt(start -> Math.abs(start - originalIndex)))
                .orElse(-1);
    }

    private static PatchCompatibilityException incompatible(String modName, GameVersion patchVersion,
                                                              GameVersion currentVersion, String path, int line,
                                                              String reason) {
        var details = "Compatibility patch " + patchVersion + " from mod '" + modName
                + "' cannot be applied to Starsector " + currentVersion + ": " + path + " near line " + line
                + " (" + reason + ").";
        return userFacingFailure(modName, currentVersion, details, null);
    }

    static PatchCompatibilityException userFacingFailure(String modName, GameVersion currentVersion,
                                                          String details, Throwable cause) {
        return new PatchCompatibilityException("Mod '" + modName + "' is incompatible with Starsector "
                + currentVersion + ".\nUpdate the mod or disable it.\nCheck starsector.log for more info.",
                details, cause);
    }

    public record Result(UnifiedPatch patch, boolean remapped) {
    }
}
