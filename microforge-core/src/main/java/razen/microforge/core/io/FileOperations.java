package razen.microforge.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class FileOperations {
    private FileOperations() {
    }

    public static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IOException("source directory does not exist: " + source);
        }
        var normalizedSource = source.toAbsolutePath().normalize();
        var normalizedTarget = target.toAbsolutePath().normalize();
        try (var paths = Files.walk(normalizedSource)) {
            for (var path : paths.sorted().toList()) {
                var output = normalizedTarget.resolve(normalizedSource.relativize(path)).normalize();
                if (!output.startsWith(normalizedTarget)) {
                    throw new IOException("copy target escapes destination: " + output);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(output);
                    continue;
                }
                var parent = output.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(path, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void writeAtomically(Path path, String content) throws IOException {
        var parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (var entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    public static Path resolveInside(Path root, String relative) throws IOException {
        var normalizedRoot = root.toAbsolutePath().normalize();
        final Path result;
        try {
            result = normalizedRoot.resolve(relative).normalize();
        } catch (RuntimeException e) {
            throw new IOException("invalid relative path: " + relative, e);
        }
        if (!result.startsWith(normalizedRoot)) {
            throw new IOException("path escapes root: " + relative);
        }
        return result;
    }
}
