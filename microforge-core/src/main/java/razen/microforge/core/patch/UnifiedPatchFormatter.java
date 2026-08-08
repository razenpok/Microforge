package razen.microforge.core.patch;

final class UnifiedPatchFormatter {
    private UnifiedPatchFormatter() {
    }

    static String format(UnifiedPatch patch) {
        var result = new StringBuilder();
        for (var file : patch.files()) {
            result.append("diff --git a/").append(file.path()).append(" b/").append(file.path()).append('\n');
            if (file.added()) {
                result.append("new file mode 100644\n");
                result.append("--- /dev/null\n");
            } else {
                result.append("--- a/").append(file.path()).append('\n');
            }
            if (file.deleted()) {
                result.append("+++ /dev/null\n");
            } else {
                result.append("+++ b/").append(file.path()).append('\n');
            }

            for (var hunk : file.hunks()) {
                appendHunk(result, hunk);
            }
        }
        return result.toString();
    }

    private static void appendHunk(StringBuilder output, UnifiedPatch.Hunk hunk) {
        var oldCount = 0;
        var newCount = 0;
        for (var line : hunk.lines()) {
            if (line.kind() != UnifiedPatch.Kind.ADD) {
                oldCount++;
            }
            if (line.kind() != UnifiedPatch.Kind.DELETE) {
                newCount++;
            }
        }
        output.append("@@ -").append(hunk.oldStart()).append(',').append(oldCount)
                .append(" +").append(hunk.newStart()).append(',').append(newCount).append(" @@\n");
        for (var line : hunk.lines()) {
            var prefix = switch (line.kind()) {
                case CONTEXT -> ' ';
                case DELETE -> '-';
                case ADD -> '+';
            };
            output.append(prefix).append(line.text()).append('\n');
        }
    }
}
