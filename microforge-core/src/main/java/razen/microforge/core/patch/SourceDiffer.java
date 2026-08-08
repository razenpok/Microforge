package razen.microforge.core.patch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.TreeSet;

public final class SourceDiffer {
    private SourceDiffer() {
    }

    public static UnifiedPatch createDiff(Path pristineRoot, Path modifiedRoot) throws IOException {
        var pristine = SourceText.readPristineJavaTree(pristineRoot);
        var modified = SourceText.readWorkingJavaTree(modifiedRoot, pristine);
        var paths = new TreeSet<String>();
        paths.addAll(pristine.keySet());
        paths.addAll(modified.keySet());

        var files = new ArrayList<UnifiedPatch.FilePatch>();
        for (var path : paths) {
            var before = pristine.get(path);
            var after = modified.get(path);
            if (before != null && after == null) {
                throw new IOException("removing Starsector source files is not supported: " + path);
            }
            if (before == null) {
                before = SourceText.TextFile.empty();
            }
            if (after == null || before.lines().equals(after.lines())) {
                continue;
            }
            files.add(UnifiedPatchGenerator.createFilePatch(
                    path, before.lines(), after.lines(), pristine.containsKey(path)));
        }
        return new UnifiedPatch(files);
    }
}
