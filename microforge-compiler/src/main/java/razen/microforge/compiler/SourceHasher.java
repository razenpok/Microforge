package razen.microforge.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

public final class SourceHasher {
    private SourceHasher() {
    }

    public static String hash(Path sourceDir, String sourceEncoding) throws IOException {
        return hash(List.of(sourceDir), sourceEncoding);
    }

    public static String hash(List<Path> sourceDirs, String sourceEncoding) throws IOException {
        Checksum checksum = new CRC32C();
        updateString(checksum, "source-encoding");
        updateString(checksum, CompileJob.normalizeSourceEncoding(sourceEncoding));
        var sourceDirIndex = 0;
        for (var sourceDir : sourceDirs) {
            updateString(checksum, "source-dir");
            updateString(checksum, Integer.toString(sourceDirIndex));
            for (var source : sourceFiles(sourceDir)) {
                updateString(checksum, sourceDir.relativize(source).toString().replace('\\', '/'));
                updateFile(checksum, source);
            }
            sourceDirIndex++;
        }
        return toHex(checksum.getValue());
    }

    private static List<Path> sourceFiles(Path sourceDir) throws IOException {
        try (var stream = Files.walk(sourceDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(SourceHasher::isSourceFile)
                    .sorted(Comparator.comparing(x -> sourceDir.relativize(x).toString()))
                    .toList();
        }
    }

    private static boolean isSourceFile(Path path) {
        return path.getFileName().toString().endsWith(".java");
    }

    private static void updateString(Checksum checksum, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        checksum.update(bytes, 0, bytes.length);
        checksum.update(0);
    }

    private static void updateFile(Checksum checksum, Path source) throws IOException {
        var buffer = new byte[8192];
        try (var input = Files.newInputStream(source)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                checksum.update(buffer, 0, read);
            }
            checksum.update(0);
        }
    }

    private static String toHex(long value) {
        var hex = new char[8];
        for (var i = hex.length - 1; i >= 0; i--) {
            hex[i] = Character.forDigit((int) (value & 0xF), 16);
            value >>>= 4;
        }
        return new String(hex);
    }
}
