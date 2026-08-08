package razen.microforge.core.patch;

import java.util.ArrayList;
import java.util.List;

final class UnifiedPatchGenerator {
    private static final int CONTEXT_LINES = 3;

    private UnifiedPatchGenerator() {
    }

    static UnifiedPatch.FilePatch createFilePatch(String path, List<String> before, List<String> after,
                                                  boolean existed) {
        var operations = diff(before, after);
        var hunks = hunkRanges(operations).stream()
                .map(range -> createHunk(operations, range))
                .toList();
        return new UnifiedPatch.FilePatch(path, !existed, false, hunks);
    }

    private static List<Operation> diff(List<String> before, List<String> after) {
        var n = before.size();
        var m = after.size();
        var max = n + m;
        var offset = max + 1;
        var vector = new int[2 * max + 3];
        var trace = new ArrayList<int[]>();

        for (var distance = 0; distance <= max; distance++) {
            trace.add(vector.clone());
            for (var diagonal = -distance; diagonal <= distance; diagonal += 2) {
                var index = offset + diagonal;
                int x;
                if (diagonal == -distance
                        || (diagonal != distance && vector[index - 1] < vector[index + 1])) {
                    x = vector[index + 1];
                } else {
                    x = vector[index - 1] + 1;
                }
                var y = x - diagonal;
                while (x < n && y < m && before.get(x).equals(after.get(y))) {
                    x++;
                    y++;
                }
                vector[index] = x;
                if (x >= n && y >= m) {
                    return backtrack(before, after, trace, distance, offset);
                }
            }
        }
        throw new IllegalStateException("could not calculate source diff");
    }

    private static List<Operation> backtrack(List<String> before, List<String> after, List<int[]> trace,
                                             int distance, int offset) {
        var reversed = new ArrayList<Operation>();
        var x = before.size();
        var y = after.size();
        for (var currentDistance = distance; currentDistance >= 0; currentDistance--) {
            var vector = trace.get(currentDistance);
            var diagonal = x - y;
            int previousDiagonal;
            if (diagonal == -currentDistance || (diagonal != currentDistance
                    && vector[offset + diagonal - 1] < vector[offset + diagonal + 1])) {
                previousDiagonal = diagonal + 1;
            } else {
                previousDiagonal = diagonal - 1;
            }
            var previousX = vector[offset + previousDiagonal];
            var previousY = previousX - previousDiagonal;
            while (x > previousX && y > previousY) {
                reversed.add(new Operation(UnifiedPatch.Kind.CONTEXT, before.get(x - 1)));
                x--;
                y--;
            }
            if (currentDistance == 0) {
                break;
            }
            if (x == previousX) {
                reversed.add(new Operation(UnifiedPatch.Kind.ADD, after.get(y - 1)));
                y--;
            } else {
                reversed.add(new Operation(UnifiedPatch.Kind.DELETE, before.get(x - 1)));
                x--;
            }
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static List<Range> hunkRanges(List<Operation> operations) {
        var changes = new ArrayList<Integer>();
        for (var i = 0; i < operations.size(); i++) {
            if (operations.get(i).kind() != UnifiedPatch.Kind.CONTEXT) {
                changes.add(i);
            }
        }
        if (changes.isEmpty()) {
            return List.of();
        }

        var ranges = new ArrayList<Range>();
        var first = changes.get(0);
        var last = first;
        for (var i = 1; i < changes.size(); i++) {
            var next = changes.get(i);
            var context = 0;
            for (var operation = last + 1; operation < next; operation++) {
                if (operations.get(operation).kind() == UnifiedPatch.Kind.CONTEXT) {
                    context++;
                }
            }
            if (context <= CONTEXT_LINES * 2) {
                last = next;
            } else {
                ranges.add(expandRange(operations, first, last));
                first = next;
                last = next;
            }
        }
        ranges.add(expandRange(operations, first, last));
        return ranges;
    }

    private static Range expandRange(List<Operation> operations, int firstChange, int lastChange) {
        var start = firstChange;
        var context = 0;
        while (start > 0 && context < CONTEXT_LINES) {
            start--;
            if (operations.get(start).kind() == UnifiedPatch.Kind.CONTEXT) {
                context++;
            }
        }

        var end = lastChange + 1;
        context = 0;
        while (end < operations.size() && context < CONTEXT_LINES) {
            if (operations.get(end).kind() == UnifiedPatch.Kind.CONTEXT) {
                context++;
            }
            end++;
        }
        return new Range(start, end);
    }

    private static UnifiedPatch.Hunk createHunk(List<Operation> operations, Range range) {
        var oldLine = 1;
        var newLine = 1;
        for (var i = 0; i < range.start(); i++) {
            var kind = operations.get(i).kind();
            if (kind != UnifiedPatch.Kind.ADD) {
                oldLine++;
            }
            if (kind != UnifiedPatch.Kind.DELETE) {
                newLine++;
            }
        }

        var oldCount = 0;
        var newCount = 0;
        var lines = new ArrayList<UnifiedPatch.PatchLine>();
        for (var i = range.start(); i < range.end(); i++) {
            var operation = operations.get(i);
            if (operation.kind() != UnifiedPatch.Kind.ADD) {
                oldCount++;
            }
            if (operation.kind() != UnifiedPatch.Kind.DELETE) {
                newCount++;
            }
            lines.add(new UnifiedPatch.PatchLine(operation.kind(), operation.text()));
        }
        var oldStart = oldCount == 0 ? Math.max(0, oldLine - 1) : oldLine;
        var newStart = newCount == 0 ? Math.max(0, newLine - 1) : newLine;
        return new UnifiedPatch.Hunk(oldStart, newStart, lines);
    }

    private record Operation(UnifiedPatch.Kind kind, String text) {
    }

    private record Range(int start, int end) {
    }
}
