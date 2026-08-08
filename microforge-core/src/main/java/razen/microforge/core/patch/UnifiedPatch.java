package razen.microforge.core.patch;

import java.io.IOException;
import java.util.List;

public record UnifiedPatch(List<FilePatch> files) {
    public UnifiedPatch {
        files = List.copyOf(files);
    }

    public static UnifiedPatch empty() {
        return new UnifiedPatch(List.of());
    }

    public static UnifiedPatch parse(String text, String sourceName) throws IOException {
        return UnifiedPatchParser.parse(text == null ? "" : text, sourceName);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public String serialize() {
        return UnifiedPatchFormatter.format(this);
    }

    public enum Kind {
        CONTEXT,
        DELETE,
        ADD
    }

    public record PatchLine(Kind kind, String text) {
    }

    public record Hunk(int oldStart, int newStart, List<PatchLine> lines) {
        public Hunk {
            lines = List.copyOf(lines);
        }
    }

    public record FilePatch(String path, boolean added, boolean deleted, List<Hunk> hunks) {
        public FilePatch {
            hunks = List.copyOf(hunks);
        }
    }

    public record Edit(int start, List<String> oldLines, List<String> newLines) {
        public Edit {
            oldLines = List.copyOf(oldLines);
            newLines = List.copyOf(newLines);
        }
    }
}
