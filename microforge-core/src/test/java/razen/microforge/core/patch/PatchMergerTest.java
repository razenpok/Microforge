package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static razen.microforge.core.patch.PatchTestSupport.cleanup;
import static razen.microforge.core.patch.PatchTestSupport.require;
import static razen.microforge.core.patch.PatchTestSupport.source;
import static razen.microforge.core.patch.PatchTestSupport.write;

public final class PatchMergerTest {
    private PatchMergerTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("PatchMergerTest passed");
    }

    public static void run() throws Exception {
        mergeSeparateChangesInOneFile();
        mergedReadsOnlyReferencedSources();
        rejectConflictingChanges();
        roundTripAddedSource();
    }

    private static void mergeSeparateChangesInOneFile() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-merge-");
        try {
            var pristine = temp.resolve("pristine");
            var first = temp.resolve("first");
            var second = temp.resolve("second");
            write(pristine, "test/A.java", source("two", "eight"));
            write(pristine, "test/Unchanged.java", "package test;\nclass Unchanged {}\n");
            FileOperations.copyTree(pristine, first);
            FileOperations.copyTree(pristine, second);
            write(first, "test/A.java", source("TWO", "eight"));
            write(second, "test/A.java", source("two", "EIGHT"));

            var merged = PatchMerger.merge(pristine, List.of(
                    new PatchContribution("first", SourceDiffer.createDiff(pristine, first)),
                    new PatchContribution("second", SourceDiffer.createDiff(pristine, second))));
            var changed = PatchApplier.apply(pristine, merged);
            var patched = pristine.resolveSibling("patched");
            require(changed.size() == 1, "expected one changed source");
            require(Files.readString(patched.resolve("test/A.java")).equals(source("TWO", "EIGHT")),
                    "merged output was incorrect");
            require(!Files.exists(patched.resolve("test/Unchanged.java")),
                    "apply copied a source file not referenced by the patch");
            require(Files.readString(pristine.resolve("test/A.java")).equals(source("two", "eight")),
                    "apply modified the pristine source tree");
        } finally {
            cleanup(temp);
        }
    }

    private static void mergedReadsOnlyReferencedSources() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-lazy-read-");
        try {
            var pristine = temp.resolve("pristine");
            var modified = temp.resolve("modified");
            write(pristine, "test/A.java", source("two", "eight"));
            write(pristine, "test/Unrelated.java", "package test;\nclass Unrelated {}\n");
            FileOperations.copyTree(pristine, modified);
            write(modified, "test/A.java", source("TWO", "eight"));
            var patch = SourceDiffer.createDiff(pristine, modified);

            var unrelated = pristine.resolve("test/Unrelated.java");
            Set<PosixFilePermission> originalPermissions;
            try {
                originalPermissions = Files.getPosixFilePermissions(unrelated);
            } catch (UnsupportedOperationException ignored) {
                return;
            }

            Files.setPosixFilePermissions(unrelated, Set.of());
            try {
                var merged = PatchMerger.merge(pristine,
                        List.of(new PatchContribution("lazy", patch)));
                require(merged.serialize().contains("test/A.java"), "referenced source was not merged");
                require(!merged.serialize().contains("Unrelated.java"),
                        "unreferenced source leaked into merged patch");
            } finally {
                Files.setPosixFilePermissions(unrelated, originalPermissions);
            }
        } finally {
            cleanup(temp);
        }
    }

    private static void rejectConflictingChanges() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-conflict-");
        try {
            var pristine = temp.resolve("pristine");
            var first = temp.resolve("first");
            var second = temp.resolve("second");
            write(pristine, "test/A.java", source("two", "eight"));
            FileOperations.copyTree(pristine, first);
            FileOperations.copyTree(pristine, second);
            write(first, "test/A.java", source("TWO", "eight"));
            write(second, "test/A.java", source("different", "eight"));

            try {
                PatchMerger.merge(pristine, List.of(
                        new PatchContribution("first", SourceDiffer.createDiff(pristine, first)),
                        new PatchContribution("second", SourceDiffer.createDiff(pristine, second))));
                throw new AssertionError("expected a patch conflict");
            } catch (IOException expected) {
                require(expected.getMessage().contains("first") && expected.getMessage().contains("second"),
                        "conflict did not identify both mods: " + expected.getMessage());
            }
        } finally {
            cleanup(temp);
        }
    }

    private static void roundTripAddedSource() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-add-");
        try {
            var pristine = temp.resolve("pristine");
            var modified = temp.resolve("modified");
            Files.createDirectories(pristine);
            Files.createDirectories(modified);
            write(modified, "test/Added.java", "package test;\nclass Added {}\n");
            var patch = SourceDiffer.createDiff(pristine, modified);
            PatchApplier.apply(pristine, PatchMerger.merge(pristine,
                    List.of(new PatchContribution("added", patch))));
            require(Files.readString(pristine.resolveSibling("patched").resolve("test/Added.java"))
                    .equals("package test;\nclass Added {}\n"), "added source did not round-trip");
        } finally {
            cleanup(temp);
        }
    }
}
