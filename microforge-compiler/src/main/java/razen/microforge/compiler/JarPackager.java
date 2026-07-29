package razen.microforge.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.*;

public final class JarPackager {
    public static final String SOURCE_HASH_ATTRIBUTE = "Microforge-Source-Hash";
    private static final long STABLE_ENTRY_TIME = 315532800000L;

    private JarPackager() {
    }

    public static void pack(Path jarPath, Path classesDir, Map<String, String> manifestAttributes) throws IOException {
        if (!Files.isDirectory(classesDir)) {
            throw new IOException("classes directory does not exist: " + classesDir);
        }

        var parent = jarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        var tempJar = jarPath.resolveSibling(jarPath.getFileName() + ".tmp");
        Files.deleteIfExists(tempJar);

        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Created-By"), "microforge-compiler");
        for (var attribute : manifestAttributes.entrySet()) {
            attributes.put(new Attributes.Name(attribute.getKey()), attribute.getValue());
        }

        try (var fileOut = Files.newOutputStream(tempJar);
             var jarOut = new JarOutputStream(fileOut, manifest)) {
            for (var classFile : classFiles(classesDir)) {
                var entryName = classesDir.relativize(classFile).toString().replace('\\', '/');
                var entry = new JarEntry(entryName);
                entry.setTime(STABLE_ENTRY_TIME);
                jarOut.putNextEntry(entry);
                try (var input = Files.newInputStream(classFile)) {
                    input.transferTo(jarOut);
                }
                jarOut.closeEntry();
            }
        }

        Files.move(tempJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String readJarHash(Path path) throws IOException {
        try (var jarFile = new JarFile(path.toFile())) {
            var manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            return StringUtils.cleanString(manifest.getMainAttributes().getValue(SOURCE_HASH_ATTRIBUTE));
        }
    }

    private static List<Path> classFiles(Path classesDir) throws IOException {
        try (var files = Files.walk(classesDir)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> classesDir.relativize(path).toString()))
                    .toList();
        }
    }
}
