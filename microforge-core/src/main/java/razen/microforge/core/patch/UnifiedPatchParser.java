package razen.microforge.core.patch;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class UnifiedPatchParser {
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(?: .*)?$");
    private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file";

    private final String[] lines;
    private final String sourceName;
    private final boolean blankInput;
    private int index;

    private UnifiedPatchParser(String[] lines, String sourceName, boolean blankInput) {
        this.lines = lines;
        this.sourceName = sourceName;
        this.blankInput = blankInput;
    }

    static UnifiedPatch parse(String patch, String sourceName) throws IOException {
        return new UnifiedPatchParser(SourceText.normalizeNewlines(patch).split("\n", -1), sourceName,
                patch.isBlank()).parse();
    }

    private UnifiedPatch parse() throws IOException {
        var files = new ArrayList<UnifiedPatch.FilePatch>();
        while (index < lines.length) {
            if (lines[index].startsWith("--- ")) {
                files.add(parseFile());
            } else {
                index++;
            }
        }
        if (files.isEmpty() && !blankInput) {
            throw invalid("contains no file changes");
        }
        return new UnifiedPatch(List.copyOf(files));
    }

    private UnifiedPatch.FilePatch parseFile() throws IOException {
        var oldPath = headerPath(lines[index].substring(4));
        index++;
        if (index >= lines.length || !lines[index].startsWith("+++ ")) {
            throw invalid("missing +++ header");
        }
        var newPath = headerPath(lines[index].substring(4));
        index++;

        var added = oldPath.equals("/dev/null");
        var deleted = newPath.equals("/dev/null");
        if (deleted) {
            newPath = oldPath;
        }
        if (!added && !deleted && !oldPath.equals(newPath)) {
            throw new IOException("renaming Starsector source files is not supported: " + oldPath + " -> "
                    + newPath);
        }
        var path = added ? newPath : oldPath;
        validatePatchPath(path, sourceName);

        var hunks = new ArrayList<UnifiedPatch.Hunk>();
        while (index < lines.length && !startsNextFile()) {
            if (lines[index].startsWith("@@ ")) {
                hunks.add(parseHunk(path));
            } else if (hunks.isEmpty() || isTrailingEmptyLine()) {
                index++;
            } else {
                throw invalid("unexpected content after hunk for " + path + ": " + lines[index]);
            }
        }
        if (hunks.isEmpty()) {
            throw invalid("contains no hunks for " + path);
        }
        return new UnifiedPatch.FilePatch(path, added, deleted, List.copyOf(hunks));
    }

    private UnifiedPatch.Hunk parseHunk(String path) throws IOException {
        var header = lines[index];
        var matcher = HUNK_HEADER.matcher(header);
        if (!matcher.matches()) {
            throw invalid("invalid hunk header: " + header);
        }
        var oldStart = number(matcher.group(1), header);
        var oldCount = count(matcher.group(2), header);
        var newStart = number(matcher.group(3), header);
        var newCount = count(matcher.group(4), header);
        index++;

        var oldConsumed = 0;
        var newConsumed = 0;
        var hunkLines = new ArrayList<UnifiedPatch.PatchLine>();
        while (oldConsumed < oldCount || newConsumed < newCount) {
            if (index >= lines.length || isHunkBoundary()) {
                throw countMismatch(path, oldCount, newCount, oldConsumed, newConsumed);
            }
            var line = lines[index++];
            if (line.equals(NO_NEWLINE_MARKER)) {
                if (hunkLines.isEmpty()) {
                    throw invalid("newline marker precedes hunk content for " + path);
                }
                continue;
            }
            if (line.isEmpty()) {
                if (index == lines.length) {
                    throw countMismatch(path, oldCount, newCount, oldConsumed, newConsumed);
                }
                throw invalid("hunk line has no prefix for " + path);
            }

            var kind = switch (line.charAt(0)) {
                case ' ' -> UnifiedPatch.Kind.CONTEXT;
                case '-' -> UnifiedPatch.Kind.DELETE;
                case '+' -> UnifiedPatch.Kind.ADD;
                default -> throw invalid("invalid hunk line for " + path + ": " + line);
            };
            if (kind != UnifiedPatch.Kind.ADD) {
                oldConsumed++;
            }
            if (kind != UnifiedPatch.Kind.DELETE) {
                newConsumed++;
            }
            if (oldConsumed > oldCount || newConsumed > newCount) {
                throw countMismatch(path, oldCount, newCount, oldConsumed, newConsumed);
            }
            hunkLines.add(new UnifiedPatch.PatchLine(kind, line.substring(1)));
        }
        while (index < lines.length && lines[index].equals(NO_NEWLINE_MARKER)) {
            if (hunkLines.isEmpty()) {
                throw invalid("newline marker follows an empty hunk for " + path);
            }
            index++;
        }
        return new UnifiedPatch.Hunk(oldStart, newStart, List.copyOf(hunkLines));
    }

    private boolean startsNextFile() {
        return lines[index].startsWith("diff --git ")
                || (lines[index].startsWith("--- ") && index + 1 < lines.length
                && lines[index + 1].startsWith("+++ "));
    }

    private boolean isTrailingEmptyLine() {
        return lines[index].isEmpty() && index == lines.length - 1;
    }

    private boolean isHunkBoundary() {
        var line = lines[index];
        return line.startsWith("@@ ") || line.startsWith("diff --git ")
                || (line.startsWith("--- ") && index + 1 < lines.length
                && lines[index + 1].startsWith("+++ "));
    }

    private int count(String value, String header) throws IOException {
        return value == null ? 1 : number(value, header);
    }

    private int number(String value, String header) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("invalid hunk number from " + sourceName + ": " + header, e);
        }
    }

    private IOException countMismatch(String path, int expectedOld, int expectedNew,
                                      int actualOld, int actualNew) {
        return invalid("hunk line counts for " + path + " declare " + expectedOld + " old/"
                + expectedNew + " new lines but contain " + actualOld + " old/" + actualNew + " new lines");
    }

    private IOException invalid(String details) {
        return new IOException("invalid patch from " + sourceName + ": " + details);
    }

    private static String headerPath(String header) {
        var tab = header.indexOf('\t');
        var path = tab < 0 ? header.trim() : header.substring(0, tab).trim();
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    private static void validatePatchPath(String path, String sourceName) throws IOException {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("\u0000")) {
            throw new IOException("unsafe patch path from " + sourceName + ": " + path);
        }
        try {
            var normalized = Path.of(path).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..") || !path.endsWith(".java")) {
                throw new IOException("unsupported patch path from " + sourceName + ": " + path);
            }
        } catch (InvalidPathException e) {
            throw new IOException("unsafe patch path from " + sourceName + ": " + path, e);
        }
    }
}
