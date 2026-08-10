package razen.microforge.core.patch;

import org.apache.log4j.Logger;
import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PatchMerger {
    private static final Logger LOG = Logger.getLogger(PatchMerger.class);

    private PatchMerger() {
    }

    public static UnifiedPatch merge(Path pristineRoot, List<PatchContribution> contributions) throws IOException {
        if (!Files.isDirectory(pristineRoot)) {
            throw new IOException("source directory does not exist: " + pristineRoot);
        }
        var normalizedPristineRoot = pristineRoot.toAbsolutePath().normalize();
        var pristine = new LinkedHashMap<String, Optional<SourceText.TextFile>>();
        var editsByPath = new LinkedHashMap<String, List<PatchEdits.OwnedEdit>>();

        for (var contribution : contributions) {
            if (contribution.patch().isEmpty()) {
                continue;
            }
            for (var filePatch : contribution.patch().files()) {
                if (filePatch.deleted()) {
                    throw new PatchContributionException(contribution.modName(), "mod " + contribution.modName()
                            + " removes a Starsector source file, which cannot be represented by a transformer: "
                            + filePatch.path());
                }

                var pristineFile = pristine.get(filePatch.path());
                if (pristineFile == null) {
                    var source = FileOperations.resolveInside(normalizedPristineRoot, filePatch.path());
                    pristineFile = Files.isRegularFile(source)
                            ? Optional.of(SourceText.readPristineFile(source))
                            : Optional.empty();
                    pristine.put(filePatch.path(), pristineFile);
                }

                var base = pristineFile.orElse(null);
                if (base == null && !filePatch.added()) {
                    throw new PatchContributionException(contribution.modName(), "mod "
                            + contribution.modName() + " patches missing source " + filePatch.path());
                }
                if (base == null) {
                    base = SourceText.TextFile.empty();
                }

                PatchEdits.validateHunks(filePatch, base.lines(), contribution.modName());
                var ownedEdits = editsByPath.computeIfAbsent(filePatch.path(), ignored -> new ArrayList<>());
                for (var edit : PatchEdits.edits(filePatch)) {
                    addMergedEdit(filePatch.path(), ownedEdits,
                            new PatchEdits.OwnedEdit(contribution.modName(), edit));
                }
            }
        }

        var files = new ArrayList<UnifiedPatch.FilePatch>();
        for (var entry : editsByPath.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var path = entry.getKey();
            var beforeFile = pristine.get(path).orElse(null);
            var before = beforeFile == null ? List.<String>of() : beforeFile.lines();
            var after = PatchEdits.apply(before, entry.getValue());
            if (!before.equals(after)) {
                files.add(UnifiedPatchGenerator.createFilePatch(path, before, after, beforeFile != null));
            }
        }
        return new UnifiedPatch(files);
    }

    private static void addMergedEdit(String path, List<PatchEdits.OwnedEdit> edits,
                                      PatchEdits.OwnedEdit candidate) throws IOException {
        for (var existing : edits) {
            if (existing.edit().equals(candidate.edit())) {
                if (!existing.owner().equals(candidate.owner())) {
                    LOG.info("Merging identical Starsector source changes from mods " + existing.owner() + " and "
                            + candidate.owner() + " in " + path + " near line " + (candidate.edit().start() + 1)
                            + ".");
                }
                return;
            }
            if (conflicts(existing.edit(), candidate.edit())) {
                throw new PatchContributionException(candidate.owner(),
                        "conflicting Starsector source patches from mods " + existing.owner() + " and "
                                + candidate.owner() + " in " + path + " near line "
                                + (Math.min(existing.edit().start(), candidate.edit().start()) + 1));
            }
        }
        edits.add(candidate);
    }

    private static boolean conflicts(UnifiedPatch.Edit left, UnifiedPatch.Edit right) {
        var leftEnd = left.start() + left.oldLines().size();
        var rightEnd = right.start() + right.oldLines().size();
        if (left.oldLines().isEmpty() && right.oldLines().isEmpty()) {
            return left.start() == right.start();
        }
        if (left.oldLines().isEmpty()) {
            return left.start() > right.start() && left.start() < rightEnd;
        }
        if (right.oldLines().isEmpty()) {
            return right.start() > left.start() && right.start() < leftEnd;
        }
        return left.start() < rightEnd && right.start() < leftEnd;
    }
}
