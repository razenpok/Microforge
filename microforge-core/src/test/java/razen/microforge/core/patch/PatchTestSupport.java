package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class PatchTestSupport {
    private PatchTestSupport() {
    }

    static String source(String lineTwo, String lineEight) {
        return String.join("\n", "package test;", lineTwo, "three", "four", "five", "six", "seven", lineEight,
                "nine", "class A {}", "");
    }

    static void write(Path root, String relative, String content) throws IOException {
        write(root, relative, content, StandardCharsets.UTF_8);
    }

    static void write(Path root, String relative, String content, Charset charset) throws IOException {
        var path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, charset);
    }

    static void cleanup(Path path) throws IOException {
        FileOperations.deleteRecursively(path);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
