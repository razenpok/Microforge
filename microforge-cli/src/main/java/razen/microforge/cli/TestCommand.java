package razen.microforge.cli;

import razen.microforge.core.compile.ClasspathBuilder;
import razen.microforge.core.compile.CompileJobBuilder;
import razen.microforge.core.compile.Compiler;
import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class TestCommand {
    private static final List<String> TEST_CLASSES = List.of(
            "razen.microforge.core.patch.UnifiedPatchTest",
            "razen.microforge.core.patch.PatchMergerTest",
            "razen.microforge.core.patch.SourceWorkspaceTest",
            "razen.microforge.core.patch.VersionedPatchTest",
            "razen.microforge.core.mods.ModManagerTest",
            "razen.microforge.core.mods.MicroforgeVersionCompatibilityTest",
            "razen.microforge.core.modbuild.ModBuildPlannerTest",
            "razen.microforge.cli.PatchCommandTest");

    private final Path root;
    private final Path ecjJar;
    private final Path gameLib;
    private final ClasspathBuilder classpath;

    private TestCommand(Path root, Path ecjJar, Path gameLib, ClasspathBuilder classpath) {
        this.root = root;
        this.ecjJar = ecjJar;
        this.gameLib = gameLib;
        this.classpath = classpath;
    }

    static TestCommand fromEnvironment() throws BuildException, IOException {
        var root = Path.of("").toAbsolutePath();
        var gameLib = CliPaths.requiredProperty("razen.microforge.cli.path.game");
        var ecjJar = CliPaths.requiredProperty("razen.microforge.cli.path.ecj");

        if (!Files.isDirectory(gameLib)) {
            throw new BuildException("game lib directory not found at " + gameLib);
        }
        if (!Files.isRegularFile(ecjJar)) {
            throw new BuildException("ECJ jar not found at " + ecjJar);
        }

        var classpath = new ClasspathBuilder().add(ecjJar).addJars(gameLib);
        return new TestCommand(root, ecjJar, gameLib, classpath);
    }

    void run() throws Exception {
        System.err.println("building tests...");
        var output = root.resolve("out/microforge-tests/classes");
        FileOperations.deleteRecursively(output.getParent());

        var job = new CompileJobBuilder()
                .name("microforge-tests")
                .sourceDirs(List.of(
                        root.resolve("microforge-cli/src/main/java"),
                        root.resolve("microforge-cli/src/test/java"),
                        root.resolve("microforge-core/src/main/java"),
                        root.resolve("microforge-core/src/test/java"),
                        root.resolve("microforge-plugin/src/main/java"),
                        root.resolve("microforge/src/main/java"),
                        root.resolve("microforge/src/test/java")))
                .outputDir(output)
                .classpath(classpath.build())
                .build();
        Compiler.compile(job);

        System.setProperty("java.awt.headless", "true");
        System.setProperty("log4j.defaultInitOverride", "true");
        runTests(output);
        System.out.println("All Microforge tests passed");
    }

    private void runTests(Path output) throws Exception {
        var urls = new ArrayList<URL>();
        urls.add(output.toUri().toURL());
        for (var entry : classpath.entries()) {
            urls.add(entry.toUri().toURL());
        }

        var testClasspath = urls.toArray(URL[]::new);
        for (var testClass : TEST_CLASSES) {
            runTest(testClasspath, testClass);
        }
        runTest(testClasspath, "razen.microforge.GameApiPatcherTest", ecjJar.toString(), gameLib.toString());
    }

    private static void runTest(URL[] classpath, String className, String... args) throws Exception {
        try (var loader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            var thread = Thread.currentThread();
            var previousLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                invokeMain(loader, className, args);
            } finally {
                thread.setContextClassLoader(previousLoader);
            }
        }
    }

    private static void invokeMain(ClassLoader loader, String className, String... args) throws Exception {
        var main = Class.forName(className, true, loader).getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }
}
