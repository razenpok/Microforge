package razen.microforge.core.patch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PatchEdits {
    private PatchEdits() {
    }

    static void validateHunks(UnifiedPatch.FilePatch patch, List<String> base, String owner) throws IOException {
        for (var hunk : patch.hunks()) {
            var oldLines = hunk.lines().stream()
                    .filter(line -> line.kind() != UnifiedPatch.Kind.ADD)
                    .map(UnifiedPatch.PatchLine::text)
                    .toList();
            var start = hunk.oldStart() == 0 ? 0 : hunk.oldStart() - 1;
            if (start < 0 || start + oldLines.size() > base.size()
                    || !sourceLinesMatch(base.subList(start, start + oldLines.size()), oldLines)) {
                throw new PatchContributionException(owner, "patch from " + owner
                        + " does not match pristine source " + patch.path() + " near line "
                        + Math.max(1, hunk.oldStart()));
            }
        }
    }

    static List<UnifiedPatch.Edit> edits(UnifiedPatch.FilePatch patch) {
        var result = new ArrayList<UnifiedPatch.Edit>();
        for (var hunk : patch.hunks()) {
            var position = hunk.oldStart() == 0 ? 0 : hunk.oldStart() - 1;
            var index = 0;
            while (index < hunk.lines().size()) {
                var line = hunk.lines().get(index);
                if (line.kind() == UnifiedPatch.Kind.CONTEXT) {
                    position++;
                    index++;
                    continue;
                }

                var start = position;
                var oldLines = new ArrayList<String>();
                var newLines = new ArrayList<String>();
                while (index < hunk.lines().size()
                        && hunk.lines().get(index).kind() != UnifiedPatch.Kind.CONTEXT) {
                    line = hunk.lines().get(index++);
                    if (line.kind() == UnifiedPatch.Kind.DELETE) {
                        oldLines.add(line.text());
                        position++;
                    } else {
                        newLines.add(line.text());
                    }
                }
                result.add(new UnifiedPatch.Edit(start, List.copyOf(oldLines), List.copyOf(newLines)));
            }
        }
        return result;
    }

    static List<String> apply(List<String> before, List<OwnedEdit> ownedEdits) throws IOException {
        var result = new ArrayList<>(before);
        var edits = new ArrayList<>(ownedEdits);
        edits.sort(Comparator.<OwnedEdit>comparingInt(value -> value.edit().start()).reversed()
                .thenComparing(Comparator.comparingInt(
                        (OwnedEdit value) -> value.edit().oldLines().size()).reversed()));

        for (var owned : edits) {
            var edit = owned.edit();
            if (edit.start() < 0 || edit.start() + edit.oldLines().size() > result.size()) {
                throw new PatchContributionException(owned.owner(), "patch from " + owned.owner()
                        + " has an invalid edit position");
            }
            var actual = result.subList(edit.start(), edit.start() + edit.oldLines().size());
            if (!actual.equals(edit.oldLines())) {
                throw new PatchContributionException(owned.owner(), "patch from " + owned.owner()
                        + " no longer matches source near line " + (edit.start() + 1));
            }
            actual.clear();
            result.addAll(edit.start(), edit.newLines());
        }
        return List.copyOf(result);
    }

    static boolean sourceLinesMatch(List<String> actual, List<String> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (var index = 0; index < actual.size(); index++) {
            if (!actual.get(index).equals(expected.get(index))
                    && !(actual.get(index).isBlank() && expected.get(index).isBlank())) {
                return false;
            }
        }
        return true;
    }

    record OwnedEdit(String owner, UnifiedPatch.Edit edit) {
    }
}
