package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.zip.ZipFile;

public final class SourceWorkspace {
    private SourceWorkspace() {
    }

    public static void unpack(Path sourceZip, Path targetDir) throws IOException {
        if (!Files.isRegularFile(sourceZip)) {
            throw new IOException("source archive does not exist: " + sourceZip);
        }

        var normalizedTarget = targetDir.toAbsolutePath().normalize();
        FileOperations.deleteRecursively(normalizedTarget);
        Files.createDirectories(normalizedTarget);

        try (var zip = new ZipFile(sourceZip.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var output = normalizedTarget.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedTarget)) {
                    throw new IOException("archive entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                var parent = output.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (var input = zip.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            FileOperations.deleteRecursively(normalizedTarget);
            throw e;
        }
    }

    public static boolean unpackIfAbsent(Path sourceZip, Path targetDir) throws IOException {
        if (Files.isDirectory(targetDir)) {
            return false;
        }
        if (Files.exists(targetDir)) {
            throw new IOException("source cache path exists but is not a directory: " + targetDir);
        }

        var staging = targetDir.resolveSibling("." + targetDir.getFileName() + ".unpack.tmp");
        FileOperations.deleteRecursively(staging);
        unpack(sourceZip, staging);
        try {
            FileOperations.moveDirectory(staging, targetDir);
        } catch (IOException e) {
            FileOperations.deleteRecursively(staging);
            throw e;
        }
        return true;
    }

    public static void normalizePreparedTreeToUtf8(Path root, Set<Path> alreadyUtf8) throws IOException {
        var normalizedRoot = root.toAbsolutePath().normalize();
        var normalizedUtf8 = alreadyUtf8.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toSet());
        try (var paths = Files.walk(normalizedRoot)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .filter(file -> !normalizedUtf8.contains(file.toAbsolutePath().normalize()))
                    .toList()) {
                SourceText.writeUtf8(path, SourceText.readPristineFile(path));
            }
        }
    }
}
