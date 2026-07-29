package razen.microforge.cli;

import razen.microforge.compiler.CompileJobBuilder;
import razen.microforge.compiler.Compiler;
import razen.microforge.compiler.CompilerException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class BuildCommand {
    private final Path root;
    private final Path ecjJar;
    private final String gameClasspath;

    private BuildCommand(Path root, Path ecjJar, String gameClasspath) {
        this.root = root;
        this.ecjJar = ecjJar;
        this.gameClasspath = gameClasspath;
    }

    static BuildCommand fromEnvironment() throws BuildException, IOException {
        var root = Path.of("").toAbsolutePath();
        var gameLib = requiredPathProperty("razen.microforge.cli.path.game");
        var ecjJar = requiredPathProperty("razen.microforge.cli.path.ecj");

        if (!Files.isDirectory(gameLib)) {
            throw new BuildException("game lib directory not found at " + gameLib);
        }

        if (!Files.isRegularFile(ecjJar)) {
            throw new BuildException("ECJ jar not found at " + ecjJar);
        }

        var classpath = findJars(gameLib).stream()
                .map(Path::toString)
                .reduce((a, b) -> a + java.io.File.pathSeparator + b)
                .orElse("");

        return new BuildCommand(root, ecjJar, classpath);
    }

    private static Path requiredPathProperty(String name) throws BuildException {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new BuildException("-D" + name + " was not provided");
        }
        return Path.of(value);
    }

    void run() throws IOException, CompilerException {
        var outDir = root.resolve("out");
        var jarsDir = root.resolve("jars");
        var microforgeOut = outDir.resolve("production/microforge");

        deleteRecursively(microforgeOut);
        Files.createDirectories(microforgeOut);
        Files.createDirectories(jarsDir);

        var classpath = gameClasspath + File.pathSeparator + ecjJar;
        var job = new CompileJobBuilder()
                .name("microforge")
                .sourceDirs(List.of(root.resolve("microforge/src/main/java"),
                        root.resolve("microforge-compiler/src/main/java")))
                .outputDir(microforgeOut)
                .classpath(classpath)
                .jarOutput(jarsDir.resolve("microforge.jar"))
                .manifestAttributes(Map.of("Premain-Class", "razen.microforge.MicroforgeAgent"))
                .build();
        Compiler.compile(job);
    }

    private static List<Path> findJars(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(x -> x.getFileName().toString()))
                    .toList();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
