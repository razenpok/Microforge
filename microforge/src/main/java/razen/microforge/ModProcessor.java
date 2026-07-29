package razen.microforge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModDependencyAPI;
import com.fs.starfarer.api.ModSpecAPI;
import org.apache.log4j.Logger;
import razen.microforge.compiler.CompileJobBuilder;
import razen.microforge.compiler.IsolatedCompiler;
import razen.microforge.compiler.JarPackager;
import razen.microforge.compiler.SourceHasher;
import razen.microforge.compiler.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ModProcessor {
    private static final Logger LOG = Logger.getLogger(ModProcessor.class);
    private static final Path BUILD_OUTPUT_DIR = Path.of("out", "build", "classes");

    private final Path ecjJar;
    private final List<Path> gameClasspathJars;
    private final List<ModSpecAPI> enabledMods;
    private final Map<String, ModSpecAPI> enabledModsById;

    private ModProcessor(Path ecjJar, List<Path> gameClasspathJars, List<ModSpecAPI> enabledMods) {
        this.ecjJar = ecjJar;
        this.gameClasspathJars = gameClasspathJars;
        this.enabledMods = enabledMods;
        this.enabledModsById = indexMods(enabledMods);
    }

    static ModProcessor fromRuntime() throws IOException {
        var modManager = Global.getSettings().getModManager();
        var enabledMods = modManager.getEnabledModsCopy();
        return new ModProcessor(Paths.getEcjJar(), jarsIn(Paths.getGameJarsDir()), enabledMods);
    }

    List<ModBuildTarget> findTargets() throws IOException {
        var targets = new ArrayList<ModBuildTarget>();
        for (var mod : this.enabledMods) {
            var modRoot = Path.of(mod.getPath()).normalize();
            var maybeConfig = MicroforgeConfig.read(modRoot);
            if (maybeConfig.isEmpty()) {
                continue;
            }

            var config = maybeConfig.get();
            if (!config.isBuildEnabled()) {
                continue;
            }

            var sourceDir = config.sourceDir();
            if (!Files.isDirectory(sourceDir)) {
                throw new IllegalStateException("source directory for " + mod.getId() + " does not exist: " + sourceDir);
            }

            var outputDir = Paths.getModPath()
                    .resolve(BUILD_OUTPUT_DIR)
                    .resolve(safePathSegment(mod.getId()))
                    .normalize();
            targets.add(new ModBuildTarget(mod.getId(), mod.getName(), modRoot, sourceDir, outputDir,
                    config.jarOutput(mod), config.sourceEncoding()));
        }

        return targets;
    }

    void compileAll(List<ModBuildTarget> targets, ProgressListener progress) throws Exception {
        var targetsByModId = indexTargets(targets);
        var orderedTargets = orderByDependencies(targets, targetsByModId);

        try (var compiler = new IsolatedCompiler(this.ecjJar)) {
            var completed = 0;
            for (var target : orderedTargets) {
                var sourceHash = SourceHasher.hash(target.sourceDir(), target.sourceEncoding());
                if (isUpToDate(target, sourceHash)) {
                    LOG.info("Skipping " + target.displayName() + "; sources unchanged.");
                    completed++;
                    progress.onProgress(null, completed, orderedTargets.size());
                    continue;
                }

                progress.onProgress(target, completed, orderedTargets.size());
                compile(target, compiler, targetsByModId, sourceHash);
                completed++;
                progress.onProgress(null, completed, orderedTargets.size());
            }
        } finally {
            deleteRecursively(buildOutputRoot());
        }
    }

    private void compile(ModBuildTarget target, IsolatedCompiler compiler, Map<String, ModBuildTarget> targetsByModId,
                         String sourceHash) {
        deleteRecursively(target.outputDir());

        var classpath = buildClasspath(target, targetsByModId);
        var job = new CompileJobBuilder()
                .name(target.modId())
                .sourceDir(target.sourceDir())
                .outputDir(target.outputDir())
                .classpath(classpath)
                .jarOutput(target.jarOutput())
                .sourceEncoding(target.sourceEncoding())
                .sourceHash(sourceHash)
                .build();
        try {
            logCompilerOutput(target, compiler.run(job), false);
        } catch (InvocationTargetException e) {
            if (e instanceof IsolatedCompiler.InvocationFailure failure) {
                logCompilerOutput(target, failure.output(), true);
            }
            var cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("failed to compile " + target.displayName(), cause);
        } finally {
            deleteRecursively(target.outputDir());
        }
    }

    private static boolean isUpToDate(ModBuildTarget target, String sourceHash) {
        var jarOutput = target.jarOutput();
        if (!Files.isRegularFile(jarOutput)) {
            return false;
        }

        try {
            var jarSourceHash = JarPackager.readJarHash(target.jarOutput());
            return sourceHash.equals(jarSourceHash);
        } catch (IOException e) {
            LOG.warn("Could not read Microforge source hash from " + target.jarOutput() + "; rebuilding.", e);
            return false;
        }
    }

    private static void logCompilerOutput(ModBuildTarget target, IsolatedCompiler.Output output, boolean failure) {
        if (output == null) {
            return;
        }

        logCompilerStream("stdout", output.stdout(), target, failure ? LogLevel.ERROR : LogLevel.INFO);
        logCompilerStream("stderr", output.stderr(), target, failure ? LogLevel.ERROR : LogLevel.WARN);
    }

    private static void logCompilerStream(String streamName, String text, ModBuildTarget target, LogLevel level) {
        if (text == null || text.isBlank()) {
            return;
        }

        logLine("Microforge compiler " + streamName + " for " + target.displayName() + ":", level);
        for (var line : text.stripTrailing().split("\\R")) {
            logLine("  " + line, level);
        }
    }

    private static void logLine(String line, LogLevel level) {
        switch (level) {
            case ERROR -> LOG.error(line);
            case WARN -> LOG.warn(line);
            case INFO -> LOG.info(line);
        }
    }

    private enum LogLevel {
        ERROR,
        WARN,
        INFO
    }

    private String buildClasspath(ModBuildTarget target, Map<String, ModBuildTarget> targetsByModId) {
        var jars = new LinkedHashSet<>(this.gameClasspathJars);

        var targetMod = requireEnabledMod(target.modId(), target.modId());
        addModJars(jars, targetMod, target.jarOutput());

        for (var dependency : dependenciesOf(targetMod)) {
            var dependencyId = StringUtils.cleanString(dependency.getId());
            if (dependencyId == null || dependencyId.equals(target.modId())) {
                continue;
            }

            var dependencyMod = requireEnabledMod(dependencyId, target.modId());
            var dependencyTarget = targetsByModId.get(dependencyId);
            if (dependencyTarget != null) {
                addModJars(jars, dependencyMod, dependencyTarget.jarOutput());
                jars.add(normalizePath(dependencyTarget.jarOutput()));
            } else {
                addModJars(jars, dependencyMod, null);
            }
        }

        return jars.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private void addModJars(Set<Path> jars, ModSpecAPI mod, Path excludedJar) {
        var modJars = mod.getJars();
        if (modJars == null) {
            return;
        }

        var modRoot = Path.of(mod.getPath()).normalize();
        var normalizedExcludedJar = excludedJar == null ? null : normalizePath(excludedJar);
        for (var jarPath : modJars) {
            var cleanJarPath = StringUtils.cleanString(jarPath);
            if (cleanJarPath == null) {
                continue;
            }

            var jar = normalizePath(MicroforgeConfig.resolveJarPath(modRoot, cleanJarPath));
            if (jar.equals(normalizedExcludedJar)) {
                continue;
            }
            if (Files.isRegularFile(jar)) {
                jars.add(jar);
            }
        }
    }

    private ModSpecAPI requireEnabledMod(String dependencyId, String targetId) {
        var mod = this.enabledModsById.get(dependencyId);
        if (mod == null) {
            throw new IllegalStateException("enabled mod " + targetId + " depends on disabled or missing mod " + dependencyId);
        }
        return mod;
    }

    private List<ModBuildTarget> orderByDependencies(List<ModBuildTarget> targets,
                                                     Map<String, ModBuildTarget> targetsByModId) {
        var ordered = new ArrayList<ModBuildTarget>();
        var visiting = new LinkedHashSet<String>();
        var visited = new LinkedHashSet<String>();

        for (var target : targets) {
            visit(target, targetsByModId, visiting, visited, ordered);
        }

        return ordered;
    }

    private void visit(ModBuildTarget target, Map<String, ModBuildTarget> targetsByModId, Set<String> visiting,
                       Set<String> visited, List<ModBuildTarget> ordered) {
        var modId = target.modId();
        if (visited.contains(modId)) {
            return;
        }
        if (!visiting.add(modId)) {
            throw new IllegalStateException("cyclic Microforge build dependency involving " + modId);
        }

        for (var dependency : dependenciesOf(requireEnabledMod(modId, modId))) {
            var dependencyId = StringUtils.cleanString(dependency.getId());
            var dependencyTarget = dependencyId == null ? null : targetsByModId.get(dependencyId);
            if (dependencyTarget != null) {
                visit(dependencyTarget, targetsByModId, visiting, visited, ordered);
            }
        }

        visiting.remove(modId);
        visited.add(modId);
        ordered.add(target);
    }

    private static List<Path> jarsIn(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static Map<String, ModSpecAPI> indexMods(List<ModSpecAPI> mods) {
        var modsById = new LinkedHashMap<String, ModSpecAPI>();
        for (var mod : mods) {
            var modId = StringUtils.cleanString(mod.getId());
            if (modId != null) {
                modsById.put(modId, mod);
            }
        }
        return modsById;
    }

    private static Map<String, ModBuildTarget> indexTargets(List<ModBuildTarget> targets) {
        var targetsByModId = new LinkedHashMap<String, ModBuildTarget>();
        for (var target : targets) {
            targetsByModId.put(target.modId(), target);
        }
        return targetsByModId;
    }

    private static List<ModDependencyAPI> dependenciesOf(ModSpecAPI mod) {
        var dependencies = mod.getAllDependencies();
        if (dependencies == null) {
            dependencies = mod.getDependencies();
        }
        return dependencies == null ? List.of() : dependencies;
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String safePathSegment(String value) {
        var safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    private static Path buildOutputRoot() {
        return Paths.getModPath().resolve(BUILD_OUTPUT_DIR).normalize();
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }

        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    interface ProgressListener {
        void onProgress(ModBuildTarget target, int completed, int total);
    }
}
