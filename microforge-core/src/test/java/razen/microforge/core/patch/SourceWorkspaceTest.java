package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static razen.microforge.core.patch.PatchTestSupport.cleanup;
import static razen.microforge.core.patch.PatchTestSupport.require;
import static razen.microforge.core.patch.PatchTestSupport.write;

public final class SourceWorkspaceTest {
    private SourceWorkspaceTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("SourceWorkspaceTest passed");
    }

    public static void run() throws Exception {
        preserveCp1252AndUtf8Boundaries();
        rejectZipTraversal();
    }

    private static void preserveCp1252AndUtf8Boundaries() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-encoding-");
        var cp1252 = Charset.forName("windows-1252");
        try {
            var pristine = temp.resolve("pristine");
            var cp1252Working = temp.resolve("cp1252-working");
            var utf8Working = temp.resolve("utf8-working");
            var relative = "test/Encoded.java";
            var original = "package test;\nclass Encoded { String value = \"é\"; }\n";
            var cp1252Edit = "package test;\nclass Encoded { String value = \"é!\"; }\n";
            var utf8Edit = "package test;\nclass Encoded { String value = \"é ✓\"; }\n";
            write(pristine, relative, original, cp1252);
            FileOperations.copyTree(pristine, cp1252Working);
            require(SourceDiffer.createDiff(pristine, cp1252Working).isEmpty(),
                    "unchanged CP1252 bytes were misread as UTF-8");

            write(cp1252Working, relative, cp1252Edit, cp1252);
            var cp1252Patch = SourceDiffer.createDiff(pristine, cp1252Working);
            require(cp1252Patch.serialize().contains("é!"), "CP1252 edit was converted to mojibake");
            PatchApplier.apply(pristine, PatchMerger.merge(pristine,
                    java.util.List.of(new PatchContribution("cp1252", cp1252Patch))));
            require(Files.readString(pristine.resolveSibling("patched").resolve(relative),
                    StandardCharsets.UTF_8).equals(cp1252Edit),
                    "patched CP1252 source was not emitted as UTF-8");

            cleanup(pristine);
            write(pristine, relative, original, cp1252);
            FileOperations.copyTree(pristine, utf8Working);
            write(utf8Working, relative, utf8Edit, StandardCharsets.UTF_8);
            require(SourceDiffer.createDiff(pristine, utf8Working).serialize().contains("✓"),
                    "UTF-8 working-tree edit was decoded as CP1252");

            SourceWorkspace.normalizePreparedTreeToUtf8(pristine, Set.of());
            require(Files.readString(pristine.resolve(relative), StandardCharsets.UTF_8).equals(original),
                    "prepared source tree was not normalized from CP1252 to UTF-8");
        } finally {
            cleanup(temp);
        }
    }

    private static void rejectZipTraversal() throws Exception {
        var temp = Files.createTempDirectory("microforge-patch-zip-");
        try {
            var zip = temp.resolve("bad.zip");
            try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
                output.putNextEntry(new ZipEntry("../escaped.java"));
                output.write("class Escaped {}".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            try {
                SourceWorkspace.unpack(zip, temp.resolve("output"));
                throw new AssertionError("expected archive traversal to be rejected");
            } catch (IOException expected) {
                require(expected.getMessage().contains("escapes target"),
                        "unexpected traversal error: " + expected.getMessage());
            }
        } finally {
            cleanup(temp);
        }
    }
}
