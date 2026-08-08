package razen.microforge.core.modbuild;

import razen.microforge.core.MicroforgeConfig;
import razen.microforge.core.compile.ClasspathBuilder;
import razen.microforge.core.mods.ModJars;
import razen.microforge.core.mods.ModSpec;
import razen.microforge.core.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModBuildPlanner {
    private final Path buildOutputRoot;
    private final List<Path> gameClasspathJars;
    private final List<ModSpec> enabledMods;
    private final Map<String, ModSpec> enabledModsById;

    ModBuildPlanner(Path buildOutputRoot, List<Path> gameClasspathJars, List<ModSpec> enabledMods) {
        this.buildOutputRoot = buildOutputRoot.toAbsolutePath().normalize();
        this.gameClasspathJars = List.copyOf(gameClasspathJars);
        this.enabledMods = List.copyOf(enabledMods);
        this.enabledModsById = indexMods(enabledMods);
    }

    List<ModBuildTarget> findTargets() throws IOException {
        var targets = new ArrayList<ModBuildTarget>();
        for (var mod : enabledMods) {
            var modRoot = mod.root();
            var maybeConfig = MicroforgeConfig.read(modRoot);
            if (maybeConfig.isEmpty() || !maybeConfig.get().isBuildEnabled()) {
                continue;
            }
            var config = maybeConfig.get();
            var sourceDir = config.sourceDir();
            if (!Files.isDirectory(sourceDir)) {
                throw new IllegalStateException("source directory for " + mod.id() + " does not exist: "
                        + sourceDir);
            }
            targets.add(new ModBuildTarget(mod.id(), mod.name(), sourceDir,
                    buildOutputRoot.resolve(safePathSegment(mod.id())), config.jarOutput(mod),
                    config.sourceEncoding()));
        }
        return List.copyOf(targets);
    }

    Plan plan(List<ModBuildTarget> targets) {
        var byId = indexTargets(targets);
        var ordered = new ArrayList<ModBuildTarget>();
        var visiting = new LinkedHashSet<String>();
        var visited = new LinkedHashSet<String>();
        for (var target : targets) {
            visit(target, byId, visiting, visited, ordered);
        }
        return new Plan(List.copyOf(ordered), byId);
    }

    List<Path> classpathFor(ModBuildTarget target, Map<String, ModBuildTarget> targetsById) {
        var classpath = new ClasspathBuilder().addAll(gameClasspathJars);
        var targetMod = requireEnabledMod(target.modId(), target.modId());
        classpath.addAll(ModJars.existing(targetMod, target.jarOutput()));
        for (var dependencyId : allDependencies(targetMod)) {
            if (dependencyId.equals(target.modId())) {
                continue;
            }
            var dependency = requireEnabledMod(dependencyId, target.modId());
            var dependencyTarget = targetsById.get(dependencyId);
            classpath.addAll(ModJars.existing(dependency,
                    dependencyTarget == null ? null : dependencyTarget.jarOutput()));
            if (dependencyTarget != null) {
                classpath.add(dependencyTarget.jarOutput());
            }
        }
        return classpath.entries();
    }

    private void visit(ModBuildTarget target, Map<String, ModBuildTarget> byId, Set<String> visiting,
                       Set<String> visited, List<ModBuildTarget> ordered) {
        if (visited.contains(target.modId())) {
            return;
        }
        if (!visiting.add(target.modId())) {
            throw new IllegalStateException("cyclic Microforge build dependency involving " + target.modId());
        }
        for (var dependencyId : requireEnabledMod(target.modId(), target.modId()).dependencies()) {
            var dependency = byId.get(dependencyId);
            if (dependency != null) {
                visit(dependency, byId, visiting, visited, ordered);
            }
        }
        visiting.remove(target.modId());
        visited.add(target.modId());
        ordered.add(target);
    }

    private ModSpec requireEnabledMod(String id, String targetId) {
        var mod = enabledModsById.get(id);
        if (mod == null) {
            throw new IllegalStateException("enabled mod " + targetId
                    + " depends on disabled or missing mod " + id);
        }
        return mod;
    }

    private List<String> allDependencies(ModSpec mod) {
        var result = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        seen.add(mod.id());
        collectDependencies(mod.dependencies(), seen, result);
        return List.copyOf(result);
    }

    private void collectDependencies(List<String> dependencies, Set<String> seen, List<String> result) {
        for (var dependencyId : dependencies) {
            if (!seen.add(dependencyId)) {
                continue;
            }
            result.add(dependencyId);
            var dependency = enabledModsById.get(dependencyId);
            if (dependency != null) {
                collectDependencies(dependency.dependencies(), seen, result);
            }
        }
    }

    private static Map<String, ModSpec> indexMods(List<ModSpec> mods) {
        var indexed = new LinkedHashMap<String, ModSpec>();
        for (var mod : mods) {
            var id = StringUtils.cleanString(mod.id());
            if (id != null) {
                indexed.put(id, mod);
            }
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, ModBuildTarget> indexTargets(List<ModBuildTarget> targets) {
        var indexed = new LinkedHashMap<String, ModBuildTarget>();
        for (var target : targets) {
            var existing = indexed.putIfAbsent(target.modId(), target);
            if (existing != null) {
                throw new IllegalArgumentException("duplicate build target for mod " + target.modId());
            }
        }
        return Map.copyOf(indexed);
    }

    private static String safePathSegment(String value) {
        var safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }

    record Plan(List<ModBuildTarget> orderedTargets, Map<String, ModBuildTarget> targetsById) {
    }
}
