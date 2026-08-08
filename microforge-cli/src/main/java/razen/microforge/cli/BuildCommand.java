package razen.microforge.cli;

import razen.microforge.core.compile.ClasspathBuilder;
import razen.microforge.core.compile.CompileJobBuilder;
import razen.microforge.core.compile.Compiler;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.modbuild.ModProcessor;
import razen.microforge.core.mods.ModDescriptorReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class BuildCommand {
    private final Path root;
    private final Path ecjJar;
    private final List<Path> gameClasspathJars;

    private BuildCommand(Path root, Path ecjJar, List<Path> gameClasspathJars) {
        this.root = root;
        this.ecjJar = ecjJar;
        this.gameClasspathJars = List.copyOf(gameClasspathJars);
    }

    static BuildCommand fromEnvironment() throws BuildException, IOException {
        var root = Path.of("").toAbsolutePath();
        var gameLib = CliPaths.requiredProperty("razen.microforge.cli.path.game");
        var ecjJar = CliPaths.requiredProperty("razen.microforge.cli.path.ecj");

        if (!Files.isDirectory(gameLib)) {
            throw new BuildException("game lib directory not found at " + gameLib);
        }

        if (!Files.isRegularFile(ecjJar)) {
            throw new BuildException("ECJ jar not found at " + ecjJar);
        }

        var classpath = new ClasspathBuilder().addJars(gameLib).entries();

        return new BuildCommand(root, ecjJar, classpath);
    }

    void run() throws Exception {
        var outDir = root.resolve("out");
        var jarsDir = root.resolve("jars");
        var microforgeOut = outDir.resolve("production/microforge");
        var pluginOut = outDir.resolve("production/microforge-plugin");

        FileOperations.deleteRecursively(microforgeOut);
        FileOperations.deleteRecursively(pluginOut);
        Files.createDirectories(microforgeOut);
        Files.createDirectories(jarsDir);

        var classpath = new ClasspathBuilder().addAll(gameClasspathJars).add(ecjJar).build();
        var job = new CompileJobBuilder()
                .name("microforge")
                .sourceDirs(List.of(root.resolve("microforge/src/main/java"),
                        root.resolve("microforge-core/src/main/java")))
                .outputDir(microforgeOut)
                .classpath(classpath)
                .jarOutput(jarsDir.resolve("microforge.jar"))
                .manifestAttributes(Map.of("Premain-Class", "razen.microforge.MicroforgeAgent"))
                .build();
        Compiler.compile(job);

        var microforge = ModDescriptorReader.read(root);
        var processor = new ModProcessor(pluginOut, ecjJar, gameClasspathJars, List.of(microforge));
        processor.compileAll(processor.findTargets(), (target, completed, total) -> { });
    }
}
